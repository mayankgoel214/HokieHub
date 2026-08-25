package edu.vt.hokiehub.repository;

import edu.vt.hokiehub.domain.Category;
import edu.vt.hokiehub.domain.Listing;
import edu.vt.hokiehub.domain.ListingType;
import edu.vt.hokiehub.domain.User;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Measures the N+1 problem instead of asserting it from the documentation.
 *
 * Hibernate's own statistics counter is the instrument. One test renders a page
 * of listings through the default lazy associations, the other renders the same
 * page fetched through the entity graph, and each records how many statements
 * actually reached PostgreSQL.
 */
@SpringBootTest
@Testcontainers
class ListingQueryCountIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.cache.type", () -> "simple");
        registry.add("hokiehub.jwt.secret", () -> "test-secret-value-long-enough-for-hmac-sha256");
        registry.add("spring.jpa.properties.hibernate.generate_statistics", () -> "true");
    }

    private static final int PAGE_SIZE = 20;

    @Autowired ListingRepository listings;
    @Autowired UserRepository users;
    @Autowired CategoryRepository categories;
    @Autowired EntityManager entityManager;

    @BeforeEach
    void seed() {
        listings.deleteAll();
        users.deleteAll();

        // A distinct seller and category per row. That is the shape of a real
        // browse page — twenty people's items across the taxonomy — and it is the
        // shape where the per-row loads cannot share a persistence-context hit.
        for (int i = 0; i < PAGE_SIZE; i++) {
            User seller = users.save(new User("seller-" + i, "seller" + i + "@vt.edu", "Seller " + i));
            Category category = categories.findById(i + 1).orElseThrow();
            listings.save(new Listing(seller, category, "Item " + i, "Description " + i,
                    new BigDecimal("10.00"), ListingType.ITEM));
        }
        entityManager.flush();
    }

    private long statementsToRender(Supplier<Iterable<Listing>> fetch) {
        entityManager.clear();
        Statistics stats = entityManager.getEntityManagerFactory()
                .unwrap(SessionFactory.class)
                .getStatistics();
        stats.clear();

        // Reading the seller and the category is what rendering a card does. A lazy
        // association costs nothing until something actually touches it.
        for (Listing listing : fetch.get()) {
            listing.getSeller().getFullName();
            listing.getCategory().getName();
        }

        return stats.getPrepareStatementCount();
    }

    /**
     * 62 = the page query, the count query pagination issues, and three loads for
     * every one of the twenty rows: the seller, the category, and — the one that
     * surprises — the service detail.
     *
     * That third load happens no matter what the mapping says. `Listing.serviceDetail`
     * is annotated `FetchType.LAZY`, but it is the inverse side of a one-to-one, so
     * Hibernate cannot build a proxy without first asking the database whether a row
     * exists at all. The annotation is not honoured and cannot be, absent bytecode
     * enhancement.
     */
    @Test
    @Transactional
    @DisplayName("rendering a 20-row page through the mapping's own fetch plan costs 62 statements")
    void defaultFetchPlanIsNPlusOne() {
        long statements = statementsToRender(() ->
                listings.findAll(PageRequest.of(0, PAGE_SIZE, Sort.by("createdAt"))));

        assertThat(statements).isEqualTo(62);
    }

    /**
     * 2 = one join for the page, plus the count query.
     *
     * A Spring Data `@EntityGraph` is a *fetch* graph, so every attribute it does not
     * name is treated as lazy — which is also what silences the service-detail load
     * above, an eagerness no annotation on the entity could switch off.
     */
    @Test
    @Transactional
    @DisplayName("the entity graph renders the same page in 2 statements")
    void entityGraphCollapsesTheFetch() {
        long statements = statementsToRender(() ->
                listings.search(null, null, null, null, null, null,
                        PageRequest.of(0, PAGE_SIZE, Sort.by("createdAt"))));

        assertThat(statements).isEqualTo(2);
    }
}
