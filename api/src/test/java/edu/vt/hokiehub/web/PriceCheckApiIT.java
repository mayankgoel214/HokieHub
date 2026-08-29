package edu.vt.hokiehub.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.vt.hokiehub.domain.User;
import edu.vt.hokiehub.pricing.GeminiPriceEstimator;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The rules around the price check. The estimator itself is stubbed — these
 * tests are about who may see the answer and what shape it is allowed to take,
 * and running them against a paid model would make the suite cost money and
 * depend on the weather.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PriceCheckApiIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.cache.type", () -> "simple");
        registry.add("hokiehub.rate-limit.enabled", () -> "false");
        registry.add("hokiehub.supabase.url", () -> "https://test-project.supabase.co");
        registry.add("hokiehub.gemini.api-key", () -> "test-key-present");
    }

    private static final String SELLER = "pc-seller";
    private static final String BUYER = "pc-buyer";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired ListingRepository listings;

    @MockitoBean GeminiPriceEstimator estimator;

    private String listingId;

    @BeforeEach
    void seed() throws Exception {
        listings.deleteAll();
        users.deleteAll();
        users.save(new User(SELLER, "seller@vt.edu", "Sam Seller"));
        users.save(new User(BUYER, "buyer@vt.edu", "Bailey Buyer"));
        when(estimator.isConfigured()).thenReturn(true);

        String body = """
                {"categoryId": 20, "title": "Sony WH-1000XM4", "description": "Barely used",
                 "price": 140.00, "condition": "good", "listingType": "item"}
                """;
        listingId = json.readTree(mvc.perform(post("/api/listings")
                        .with(jwt().jwt(j -> j.subject(SELLER)))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    private void estimatorReturns(BigDecimal low, BigDecimal typical, BigDecimal high,
                                  List<GeminiPriceEstimator.Estimate.Comparable> comps) {
        when(estimator.estimate(any(), any())).thenReturn(new GeminiPriceEstimator.Estimate(
                "Sony WH-1000XM4 wireless headphones", low, typical, high,
                "Widely resold; prices cluster in this range.", comps));
    }

    private static GeminiPriceEstimator.Estimate.Comparable comp(String title, String price) {
        return new GeminiPriceEstimator.Estimate.Comparable(
                title, "https://example.com/" + title.hashCode(), new BigDecimal(price), "used, good");
    }

    private void unlockAs(String subject) throws Exception {
        mvc.perform(post("/api/listings/" + listingId + "/price-check/unlock")
                        .with(jwt().jwt(j -> j.subject(subject).claim("email", subject + "@vt.edu"))))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("the status endpoint is public and says what it costs")
    void statusIsPublic() throws Exception {
        mvc.perform(get("/api/listings/" + listingId + "/price-check/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.priceCents").value(199))
                .andExpect(jsonPath("$.unlocked").value(false));
    }

    @Test
    @DisplayName("the analysis needs an unlock, not just an account")
    void lockedWithoutPurchase() throws Exception {
        mvc.perform(get("/api/listings/" + listingId + "/price-check")
                        .with(jwt().jwt(j -> j.subject(BUYER))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an anonymous visitor cannot see it at all")
    void anonymousRefused() throws Exception {
        mvc.perform(get("/api/listings/" + listingId + "/price-check"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("the seller cannot buy a price check on their own listing")
    void sellerRefused() throws Exception {
        mvc.perform(post("/api/listings/" + listingId + "/price-check/unlock")
                        .with(jwt().jwt(j -> j.subject(SELLER))))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/listings/" + listingId + "/price-check")
                        .with(jwt().jwt(j -> j.subject(SELLER))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a buyer who unlocks gets the estimate and the comparables behind it")
    void unlockedBuyerSeesEstimateWithSources() throws Exception {
        estimatorReturns(new BigDecimal("120"), new BigDecimal("150"), new BigDecimal("175"),
                List.of(comp("XM4 used - eBay", "148"), comp("XM4 refurb - Swappa", "155")));

        unlockAs(BUYER);

        mvc.perform(get("/api/listings/" + listingId + "/price-check")
                        .with(jwt().jwt(j -> j.subject(BUYER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ready"))
                .andExpect(jsonPath("$.grounded").value(true))
                .andExpect(jsonPath("$.estimatedTypical").value(150.00))
                .andExpect(jsonPath("$.sources.length()").value(2))
                .andExpect(jsonPath("$.sources[0].url").isNotEmpty())
                // Asking 140 against a 120-175 band is a fair price.
                .andExpect(jsonPath("$.verdict").value("fair"));
    }

    @Test
    @DisplayName("no comparables means no estimate, not a guess")
    void noComparablesMeansNoNumber() throws Exception {
        estimatorReturns(new BigDecimal("100"), new BigDecimal("130"), new BigDecimal("160"),
                List.of());

        unlockAs(BUYER);

        mvc.perform(get("/api/listings/" + listingId + "/price-check")
                        .with(jwt().jwt(j -> j.subject(BUYER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("no_comparables"))
                .andExpect(jsonPath("$.grounded").value(false))
                // The model offered numbers. Without sources they are not published.
                .andExpect(jsonPath("$.estimatedTypical").doesNotExist())
                .andExpect(jsonPath("$.verdict").doesNotExist())
                .andExpect(jsonPath("$.sources.length()").value(0));
    }

    @Test
    @DisplayName("the verdict is arithmetic on the asking price, not the model's opinion")
    void verdictReflectsAskingPrice() throws Exception {
        estimatorReturns(new BigDecimal("40"), new BigDecimal("55"), new BigDecimal("70"),
                List.of(comp("comparable", "55")));

        unlockAs(BUYER);

        // Asking 140 against a 40-70 band.
        mvc.perform(get("/api/listings/" + listingId + "/price-check")
                        .with(jwt().jwt(j -> j.subject(BUYER))))
                .andExpect(jsonPath("$.verdict").value("above_market"));
    }

    @Test
    @DisplayName("the analysis is computed once and reused, not re-run per viewer")
    void analysisIsCached() throws Exception {
        estimatorReturns(new BigDecimal("120"), new BigDecimal("150"), new BigDecimal("175"),
                List.of(comp("one", "150")));
        unlockAs(BUYER);

        mvc.perform(get("/api/listings/" + listingId + "/price-check")
                .with(jwt().jwt(j -> j.subject(BUYER)))).andExpect(status().isOk());
        mvc.perform(get("/api/listings/" + listingId + "/price-check")
                .with(jwt().jwt(j -> j.subject(BUYER)))).andExpect(status().isOk());

        // Two reads, one paid call.
        verify(estimator, times(1)).estimate(any(), any());
    }

    @Test
    @DisplayName("a deployment with no key says so rather than pretending")
    void unconfiguredIsHonest() throws Exception {
        when(estimator.isConfigured()).thenReturn(false);

        mvc.perform(get("/api/listings/" + listingId + "/price-check/status"))
                .andExpect(jsonPath("$.available").value(false));

        mvc.perform(post("/api/listings/" + listingId + "/price-check/unlock")
                        .with(jwt().jwt(j -> j.subject(BUYER).claim("email", "buyer@vt.edu"))))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    @DisplayName("retrying after a failure rewrites the row rather than colliding with it")
    void retryAfterFailureSucceeds() throws Exception {
        unlockAs(BUYER);

        // First attempt fails and is recorded.
        when(estimator.estimate(any(), any()))
                .thenThrow(new GeminiPriceEstimator.PriceCheckFailedException("upstream timed out"));
        mvc.perform(get("/api/listings/" + listingId + "/price-check")
                        .with(jwt().jwt(j -> j.subject(BUYER))))
                .andExpect(jsonPath("$.status").value("failed"));

        // Second attempt succeeds. There is one row per listing, so the retry has
        // to rewrite it — deleting and re-inserting in one flush let Hibernate
        // order the insert first and collide with the unique constraint, which
        // surfaced as a 500 rather than as a second try.
        reset(estimator);
        when(estimator.isConfigured()).thenReturn(true);
        estimatorReturns(new BigDecimal("120"), new BigDecimal("150"), new BigDecimal("175"),
                List.of(comp("XM4 used", "150")));

        mvc.perform(get("/api/listings/" + listingId + "/price-check")
                        .with(jwt().jwt(j -> j.subject(BUYER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ready"))
                .andExpect(jsonPath("$.estimatedTypical").value(150.00))
                .andExpect(jsonPath("$.failureReason").doesNotExist())
                .andExpect(jsonPath("$.sources.length()").value(1));
    }

    @Test
    @DisplayName("a model failure is recorded as a failure, not as a free estimate")
    void modelFailureIsNotAnEstimate() throws Exception {
        when(estimator.estimate(any(), any()))
                .thenThrow(new GeminiPriceEstimator.PriceCheckFailedException("upstream timed out"));

        unlockAs(BUYER);

        mvc.perform(get("/api/listings/" + listingId + "/price-check")
                        .with(jwt().jwt(j -> j.subject(BUYER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("failed"))
                .andExpect(jsonPath("$.estimatedTypical").doesNotExist())
                .andExpect(jsonPath("$.failureReason").isNotEmpty());
    }
}
