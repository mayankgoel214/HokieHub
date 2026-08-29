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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Uploading a photograph of the actual item, and who is allowed to. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ImageApiIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.cache.type", () -> "simple");
        registry.add("hokiehub.rate-limit.enabled", () -> "false");
        registry.add("hokiehub.supabase.url", () -> "https://test-project.supabase.co");
    }

    private static final String OWNER = "img-owner";
    private static final String STRANGER = "img-stranger";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired ListingRepository listings;

    private String listingId;

    // A one-pixel PNG: real bytes with a real header, small enough to inline.
    private static final byte[] PNG = java.util.Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    @BeforeEach
    void seed() throws Exception {
        listings.deleteAll();
        users.deleteAll();
        users.save(new User(OWNER, "owner@vt.edu", "Owner"));
        users.save(new User(STRANGER, "stranger@vt.edu", "Stranger"));

        String body = """
                {"categoryId": 1, "title": "Desk lamp", "description": "Works",
                 "price": 15.00, "listingType": "item"}
                """;
        listingId = json.readTree(mvc.perform(post("/api/listings")
                        .with(jwt().jwt(j -> j.subject(OWNER)))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    private MockMultipartFile png(String name) {
        return new MockMultipartFile("file", name, "image/png", PNG);
    }

    @Test
    @DisplayName("the owner uploads a photograph and it becomes the card image")
    void uploadBecomesPrimary() throws Exception {
        mvc.perform(multipart("/api/listings/" + listingId + "/images")
                        .file(png("lamp.png"))
                        .with(jwt().jwt(j -> j.subject(OWNER))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isPrimary").value(true))
                .andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.startsWith("/api/images/")));

        mvc.perform(get("/api/listings/" + listingId))
                .andExpect(jsonPath("$.primaryImageUrl")
                        .value(org.hamcrest.Matchers.startsWith("/api/images/")));
    }

    @Test
    @DisplayName("the stored photograph is served to anyone, with its content type")
    void servedPublicly() throws Exception {
        String created = mvc.perform(multipart("/api/listings/" + listingId + "/images")
                        .file(png("lamp.png"))
                        .with(jwt().jwt(j -> j.subject(OWNER))))
                .andReturn().getResponse().getContentAsString();
        int imageId = json.readTree(created).get("id").asInt();

        // No token at all: a listing's photograph is as public as the listing.
        mvc.perform(get("/api/images/" + imageId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(header().string("Cache-Control",
                        org.hamcrest.Matchers.containsString("max-age=31536000")));
    }

    @Test
    @DisplayName("a stranger cannot attach a photograph to someone else's listing")
    void strangerCannotUpload() throws Exception {
        mvc.perform(multipart("/api/listings/" + listingId + "/images")
                        .file(png("x.png"))
                        .with(jwt().jwt(j -> j.subject(STRANGER))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an anonymous upload is refused")
    void anonymousCannotUpload() throws Exception {
        mvc.perform(multipart("/api/listings/" + listingId + "/images").file(png("x.png")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a PDF wearing an image name is refused")
    void wrongContentTypeRefused() throws Exception {
        mvc.perform(multipart("/api/listings/" + listingId + "/images")
                        .file(new MockMultipartFile("file", "not-really.png",
                                "application/pdf", new byte[]{1,2,3}))
                        .with(jwt().jwt(j -> j.subject(OWNER))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("an oversized photograph is refused")
    void oversizedRefused() throws Exception {
        byte[] tooBig = new byte[2 * 1024 * 1024 + 1];
        mvc.perform(multipart("/api/listings/" + listingId + "/images")
                        .file(new MockMultipartFile("file", "big.png", "image/png", tooBig))
                        .with(jwt().jwt(j -> j.subject(OWNER))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a sixth photograph is refused")
    void tooManyRefused() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(multipart("/api/listings/" + listingId + "/images")
                            .file(png("p" + i + ".png"))
                            .with(jwt().jwt(j -> j.subject(OWNER))))
                    .andExpect(status().isCreated());
        }
        mvc.perform(multipart("/api/listings/" + listingId + "/images")
                        .file(png("sixth.png"))
                        .with(jwt().jwt(j -> j.subject(OWNER))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("deleting the card image promotes another rather than leaving none")
    void deletingPrimaryPromotesAnother() throws Exception {
        String first = mvc.perform(multipart("/api/listings/" + listingId + "/images")
                        .file(png("a.png")).with(jwt().jwt(j -> j.subject(OWNER))))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(multipart("/api/listings/" + listingId + "/images")
                        .file(png("b.png")).with(jwt().jwt(j -> j.subject(OWNER))))
                .andExpect(status().isCreated());
        int firstId = json.readTree(first).get("id").asInt();

        mvc.perform(delete("/api/listings/" + listingId + "/images/" + firstId)
                        .with(jwt().jwt(j -> j.subject(OWNER))))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/listings/" + listingId))
                .andExpect(jsonPath("$.images.length()").value(1))
                .andExpect(jsonPath("$.images[0].isPrimary").value(true))
                .andExpect(jsonPath("$.primaryImageUrl").isNotEmpty());
    }

    @Test
    @DisplayName("an unknown image id is a 404")
    void unknownImage() throws Exception {
        mvc.perform(get("/api/images/999999")).andExpect(status().isNotFound());
    }
}
