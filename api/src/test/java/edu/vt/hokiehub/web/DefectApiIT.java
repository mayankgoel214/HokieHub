package edu.vt.hokiehub.web;

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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** What the seller says is wrong with the item, and what happens to it on edit. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class DefectApiIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.cache.type", () -> "simple");
        registry.add("hokiehub.rate-limit.enabled", () -> "false");
        registry.add("hokiehub.supabase.url", () -> "https://test-project.supabase.co");
    }

    private static final String SELLER = "defect-seller";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired ListingRepository listings;

    private String id;

    @BeforeEach
    void seed() throws Exception {
        listings.deleteAll();
        users.deleteAll();
        users.save(new User(SELLER, "seller@vt.edu", "Sam Seller"));

        String body = """
                {"categoryId": 1, "title": "Desk lamp", "description": "Works",
                 "price": 15.00, "listingType": "item",
                 "defects": [
                   {"description": "Scratch on the base", "severity": "minor"},
                   {"description": "Switch sticks when cold", "severity": "moderate"}
                 ]}
                """;
        id = json.readTree(mvc.perform(post("/api/listings")
                        .with(jwt().jwt(j -> j.subject(SELLER)))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    @DisplayName("defects come back with the listing, in the order they were given")
    void defectsAreReturned() throws Exception {
        mvc.perform(get("/api/listings/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defects.length()").value(2))
                .andExpect(jsonPath("$.defects[0].description").value("Scratch on the base"))
                .andExpect(jsonPath("$.defects[0].severity").value("minor"))
                .andExpect(jsonPath("$.defects[1].severity").value("moderate"));
    }

    @Test
    @DisplayName("editing only the price leaves the disclosures alone")
    void updatingSomethingElseKeepsDefects() throws Exception {
        mvc.perform(put("/api/listings/" + id)
                        .with(jwt().jwt(j -> j.subject(SELLER)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\": 12.00}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/listings/" + id))
                .andExpect(jsonPath("$.defects.length()").value(2));
    }

    @Test
    @DisplayName("sending an empty list is how a seller says there are none any more")
    void emptyListClearsDefects() throws Exception {
        mvc.perform(put("/api/listings/" + id)
                        .with(jwt().jwt(j -> j.subject(SELLER)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"defects\": []}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/listings/" + id))
                .andExpect(jsonPath("$.defects.length()").value(0));
    }

    @Test
    @DisplayName("an unknown severity is rejected per field")
    void severityIsValidated() throws Exception {
        String body = """
                {"categoryId": 1, "title": "t", "description": "d", "price": 1.00,
                 "listingType": "item",
                 "defects": [{"description": "x", "severity": "catastrophic"}]}
                """;
        mvc.perform(post("/api/listings")
                        .with(jwt().jwt(j -> j.subject(SELLER)))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a defect needs a description")
    void descriptionIsRequired() throws Exception {
        String body = """
                {"categoryId": 1, "title": "t", "description": "d", "price": 1.00,
                 "listingType": "item", "defects": [{"description": "  "}]}
                """;
        mvc.perform(post("/api/listings")
                        .with(jwt().jwt(j -> j.subject(SELLER)))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }
}
