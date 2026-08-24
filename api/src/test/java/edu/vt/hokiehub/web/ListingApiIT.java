package edu.vt.hokiehub.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End to end against a real PostgreSQL, started for the test run and thrown away
 * afterwards. Flyway applies the same migrations production uses, so the schema,
 * the check constraints, the text[] column and the Hibernate mapping are all
 * exercised together — none of which an H2 or mocked test would catch.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ListingApiIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        // No Redis in the test run; the cache abstraction is exercised in-memory.
        registry.add("spring.cache.type", () -> "simple");
        registry.add("hokiehub.jwt.secret", () -> "test-secret-value-long-enough-for-hmac-sha256");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired ListingRepository listings;

    private static final String OWNER = "owner-subject";
    private static final String STRANGER = "stranger-subject";

    @BeforeEach
    void seedUsers() {
        listings.deleteAll();
        users.deleteAll();
        users.save(new User(OWNER, "owner@vt.edu", "Owner Student"));
        users.save(new User(STRANGER, "stranger@vt.edu", "Other Student"));
    }

    @Test
    @DisplayName("a valid token from a non-VT address cannot create a listing")
    void nonVtAccountIsRefused() throws Exception {
        String body = """
                {"categoryId": 1, "title": "Contraband", "description": "d",
                 "price": 1.00, "listingType": "item"}
                """;

        mvc.perform(post("/api/listings")
                        .with(jwt().jwt(j -> j.subject("outsider").claim("email", "someone@gmail.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    private String createListing(String subject, String title, String price) throws Exception {
        String body = """
                {
                  "categoryId": 1,
                  "title": "%s",
                  "description": "Test description",
                  "price": %s,
                  "condition": "good",
                  "listingType": "item",
                  "location": "Blacksburg"
                }
                """.formatted(title, price);

        String response = mvc.perform(post("/api/listings")
                        .with(jwt().jwt(j -> j.subject(subject)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return json.readTree(response).get("id").asText();
    }

    @Test
    @DisplayName("the seeded category tree has 12 top-level categories, each with children")
    void categoryTreeIsSeeded() throws Exception {
        String response = mvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode tree = json.readTree(response);
        assertThat(tree.size()).isEqualTo(12);
        assertThat(tree.findValuesAsText("name")).contains("Textbooks", "Electronics", "Services");
    }

    @Test
    @DisplayName("browsing is public but posting is not")
    void browsingIsPublicPostingIsNot() throws Exception {
        mvc.perform(get("/api/listings")).andExpect(status().isOk());
        mvc.perform(get("/api/categories")).andExpect(status().isOk());

        mvc.perform(post("/api/listings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a listing can be created and read back with its seller and category resolved")
    void createAndFetch() throws Exception {
        String id = createListing(OWNER, "Physics textbook", "45.00");

        mvc.perform(get("/api/listings/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Physics textbook"))
                .andExpect(jsonPath("$.status").value("available"))
                .andExpect(jsonPath("$.condition").value("good"))
                .andExpect(jsonPath("$.seller.email").value("owner@vt.edu"))
                .andExpect(jsonPath("$.category.name").isNotEmpty());
    }

    @Test
    @DisplayName("a stranger gets 403 updating someone else's listing, and it is unchanged")
    void strangerCannotUpdate() throws Exception {
        String id = createListing(OWNER, "Mini fridge", "80.00");

        mvc.perform(put("/api/listings/" + id)
                        .with(jwt().jwt(j -> j.subject(STRANGER)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Hijacked\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/listings/" + id))
                .andExpect(jsonPath("$.title").value("Mini fridge"));
    }

    @Test
    @DisplayName("a stranger gets 403 deleting someone else's listing, and it survives")
    void strangerCannotDelete() throws Exception {
        String id = createListing(OWNER, "Desk chair", "30.00");

        mvc.perform(delete("/api/listings/" + id)
                        .with(jwt().jwt(j -> j.subject(STRANGER))))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/listings/" + id)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("the owner can update and delete")
    void ownerCanUpdateAndDelete() throws Exception {
        String id = createListing(OWNER, "Bike", "120.00");

        mvc.perform(put("/api/listings/" + id)
                        .with(jwt().jwt(j -> j.subject(OWNER)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\":95.00,\"status\":\"sold\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(95.00))
                .andExpect(jsonPath("$.status").value("sold"));

        mvc.perform(delete("/api/listings/" + id)
                        .with(jwt().jwt(j -> j.subject(OWNER))))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/listings/" + id)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("invalid input is rejected per field before it reaches the database")
    void validationRejectsBadInput() throws Exception {
        String body = """
                {"categoryId": 1, "title": "", "description": "d",
                 "price": -5, "listingType": "item"}
                """;

        mvc.perform(post("/api/listings")
                        .with(jwt().jwt(j -> j.subject(OWNER)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").isNotEmpty())
                .andExpect(jsonPath("$.errors.price").isNotEmpty());
    }

    @Test
    @DisplayName("browse is paginated and does not return the whole table")
    void browseIsPaginated() throws Exception {
        for (int i = 0; i < 5; i++) {
            createListing(OWNER, "Item " + i, "10.00");
        }

        mvc.perform(get("/api/listings").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false));
    }

    @Test
    @DisplayName("a request for an absurd page size is capped rather than honoured")
    void pageSizeIsCapped() throws Exception {
        createListing(OWNER, "Only item", "10.00");

        mvc.perform(get("/api/listings").param("size", "100000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    @DisplayName("/mine returns only the caller's own listings")
    void mineReturnsOnlyOwnListings() throws Exception {
        createListing(OWNER, "Owner item", "10.00");
        createListing(STRANGER, "Stranger item", "20.00");

        mvc.perform(get("/api/listings/mine").with(jwt().jwt(j -> j.subject(OWNER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Owner item"));
    }

    @Test
    @DisplayName("reading a listing increments its view counter")
    void viewCounterIncrements() throws Exception {
        String id = createListing(OWNER, "Viewed item", "10.00");

        mvc.perform(get("/api/listings/" + id));
        mvc.perform(get("/api/listings/" + id));

        mvc.perform(get("/api/listings/" + id))
                .andExpect(jsonPath("$.viewsCount").value(2));
    }
}
