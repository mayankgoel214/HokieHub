package edu.vt.hokiehub.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.vt.hokiehub.domain.User;
import edu.vt.hokiehub.repository.BidRepository;
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
 * Bidding, against a real PostgreSQL so the unique constraint that makes "one
 * live offer per bidder" true is actually exercised rather than assumed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class BidApiIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.cache.type", () -> "simple");
        registry.add("hokiehub.rate-limit.enabled", () -> "false");
        registry.add("hokiehub.supabase.url", () -> "https://test-project.supabase.co");
    }

    private static final String SELLER = "seller-subject";
    private static final String BUYER = "buyer-subject";
    private static final String RIVAL = "rival-subject";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired ListingRepository listings;
    @Autowired BidRepository bids;

    private String listingId;

    @BeforeEach
    void seed() throws Exception {
        bids.deleteAll();
        listings.deleteAll();
        users.deleteAll();
        users.save(new User(SELLER, "seller@vt.edu", "Sam Seller"));
        users.save(new User(BUYER, "buyer@vt.edu", "Bailey Buyer"));
        users.save(new User(RIVAL, "rival@vt.edu", "Robin Rival"));

        String body = """
                {"categoryId": 1, "title": "Mountain bike", "description": "Rides well",
                 "price": 200.00, "listingType": "item"}
                """;
        String created = mvc.perform(post("/api/listings")
                        .with(jwt().jwt(j -> j.subject(SELLER)))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        listingId = json.readTree(created).get("id").asText();
    }

    private org.springframework.test.web.servlet.ResultActions bid(String subject, String amount)
            throws Exception {
        return mvc.perform(put("/api/listings/" + listingId + "/bids")
                .with(jwt().jwt(j -> j.subject(subject).claim("email", subject + "@vt.edu")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\": %s, \"message\": \"Can collect today\"}".formatted(amount)));
    }

    @Test
    @DisplayName("a buyer places an offer and the seller sees it")
    void placeAndList() throws Exception {
        bid(BUYER, "180.00").andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(180.00))
                .andExpect(jsonPath("$.status").value("active"))
                .andExpect(jsonPath("$.bidder.email").value("buyer@vt.edu"));

        mvc.perform(get("/api/listings/" + listingId + "/bids")
                        .with(jwt().jwt(j -> j.subject(SELLER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].amount").value(180.00));
    }

    @Test
    @DisplayName("raising an offer replaces it rather than stacking a second one")
    void raisingReplaces() throws Exception {
        bid(BUYER, "150.00").andExpect(status().isOk());
        bid(BUYER, "175.00").andExpect(status().isOk());

        assertThat(bids.count()).isEqualTo(1);
        mvc.perform(get("/api/listings/" + listingId + "/bids")
                        .with(jwt().jwt(j -> j.subject(SELLER))))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].amount").value(175.00));
    }

    @Test
    @DisplayName("a seller cannot bid on their own listing")
    void sellerCannotBidOnOwnListing() throws Exception {
        bid(SELLER, "10.00").andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("who is bidding is private; how many are is not")
    void summaryIsPublicButBiddersAreNot() throws Exception {
        bid(BUYER, "180.00");
        bid(RIVAL, "195.00");

        // Anyone, with no token at all.
        mvc.perform(get("/api/listings/" + listingId + "/bids/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.highest").value(195.00));

        // The list of who, without a token, and as somebody who is not the seller.
        mvc.perform(get("/api/listings/" + listingId + "/bids"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/listings/" + listingId + "/bids")
                        .with(jwt().jwt(j -> j.subject(RIVAL))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("withdrawing takes the offer out of the count")
    void withdrawing() throws Exception {
        bid(BUYER, "180.00");
        mvc.perform(delete("/api/listings/" + listingId + "/bids")
                        .with(jwt().jwt(j -> j.subject(BUYER))))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/listings/" + listingId + "/bids/summary"))
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    @DisplayName("accepting one offer declines the others and holds the listing")
    void acceptingDeclinesTheRest() throws Exception {
        bid(BUYER, "180.00");
        String rival = bid(RIVAL, "195.00").andReturn().getResponse().getContentAsString();
        String winningBid = json.readTree(rival).get("id").asText();

        mvc.perform(post("/api/listings/" + listingId + "/bids/" + winningBid + "/accept")
                        .with(jwt().jwt(j -> j.subject(SELLER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));

        // The listing is held, not sold: they still have to meet.
        mvc.perform(get("/api/listings/" + listingId))
                .andExpect(jsonPath("$.status").value("pending"));

        // Nobody is left thinking they are still in the running.
        mvc.perform(get("/api/listings/" + listingId + "/bids/summary"))
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    @DisplayName("a stranger cannot accept an offer on someone else's listing")
    void onlySellerAccepts() throws Exception {
        String placed = bid(BUYER, "180.00").andReturn().getResponse().getContentAsString();
        String bidId = json.readTree(placed).get("id").asText();

        mvc.perform(post("/api/listings/" + listingId + "/bids/" + bidId + "/accept")
                        .with(jwt().jwt(j -> j.subject(RIVAL))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a listing that is no longer available stops taking offers")
    void soldListingsTakeNoOffers() throws Exception {
        mvc.perform(put("/api/listings/" + listingId)
                .with(jwt().jwt(j -> j.subject(SELLER)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"sold\"}")).andExpect(status().isOk());

        bid(BUYER, "180.00").andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a bid of zero or less is rejected before it reaches the database")
    void nonPositiveBidsRejected() throws Exception {
        bid(BUYER, "0").andExpect(status().isBadRequest());
        bid(BUYER, "-5").andExpect(status().isBadRequest());
    }
}
