package edu.vt.hokiehub.web;

import edu.vt.hokiehub.domain.User;
import edu.vt.hokiehub.repository.ListingRepository;
import edu.vt.hokiehub.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The limiter counts in Redis rather than on the heap, so a limit that only held
 * within one process would not be a limit at all. This runs against a real Redis
 * for that reason.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class RateLimitIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static GenericContainer<?> redis =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.cache.type", () -> "simple");
        // The mock jwt() post-processor bypasses the decoder; this only has to
        // be present so the bean builds.
        registry.add("hokiehub.supabase.url", () -> "https://test-project.supabase.co");
    }

    private static final int WRITE_LIMIT = 30;

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired ListingRepository listings;
    @Autowired StringRedisTemplate redisTemplate;

    @BeforeEach
    void reset() {
        listings.deleteAll();
        users.deleteAll();
        users.save(new User("rate-limited", "seller@vt.edu", "Busy Seller"));
        // Each test starts from an empty window, or the previous one's counting
        // decides this one's outcome.
        var keys = redisTemplate.keys("ratelimit:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private String listingBody(int i) {
        return """
                {"categoryId": 1, "title": "Item %d", "description": "d",
                 "price": 10.00, "listingType": "item"}
                """.formatted(i);
    }

    @Test
    @DisplayName("the 31st write in an hour is refused with 429 and a Retry-After")
    void writesAreLimitedPerUser() throws Exception {
        for (int i = 0; i < WRITE_LIMIT; i++) {
            mvc.perform(post("/api/listings")
                            .with(jwt().jwt(j -> j.subject("rate-limited")
                                    .claim("email", "seller@vt.edu")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(listingBody(i)))
                    .andExpect(status().isCreated());
        }

        mvc.perform(post("/api/listings")
                        .with(jwt().jwt(j -> j.subject("rate-limited")
                                .claim("email", "seller@vt.edu")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(listingBody(999)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));

        // The refusal has to be a refusal, not a slow success.
        assertThat(listings.count()).isEqualTo(WRITE_LIMIT);
    }

    @Test
    @DisplayName("one user hitting the write limit does not lock out another")
    void limitIsPerCallerNotGlobal() throws Exception {
        users.save(new User("other-seller", "other@vt.edu", "Other Seller"));

        for (int i = 0; i < WRITE_LIMIT + 1; i++) {
            mvc.perform(post("/api/listings")
                    .with(jwt().jwt(j -> j.subject("rate-limited")
                            .claim("email", "seller@vt.edu")))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(listingBody(i)));
        }

        mvc.perform(post("/api/listings")
                        .with(jwt().jwt(j -> j.subject("other-seller")
                                .claim("email", "other@vt.edu")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(listingBody(1)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("public browsing carries its remaining budget in a header")
    void readsReportRemainingBudget() throws Exception {
        mvc.perform(get("/api/listings"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Limit", "300"))
                .andExpect(header().string("X-RateLimit-Remaining", "299"));
    }
}
