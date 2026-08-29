package edu.vt.hokiehub.pricing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.vt.hokiehub.domain.Listing;
import edu.vt.hokiehub.domain.ListingImage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Asks Gemini what a second-hand item is worth, with Google Search grounding so
 * the answer is drawn from things that were actually listed rather than from
 * what the model remembers.
 *
 * The distinction matters more than it sounds. A model will happily produce
 * "$180" for anything; a price with no comparables behind it is a guess wearing
 * the costume of a market rate, and a marketplace that prints those is worse
 * than one that says nothing. So this returns {@code null} sources rather than
 * inventing them, and the caller refuses to publish an estimate without them.
 */
@Component
public class GeminiPriceEstimator {

    private static final Logger log = LoggerFactory.getLogger(GeminiPriceEstimator.class);

    /**
     * Multimodal, and cheap enough to run once per listing.
     *
     * Pinned rather than floating: Google retires these. gemini-2.0-flash was
     * removed and started answering 404 with a message about which model to use
     * instead, which is a better failure than most but still a failure.
     */
    static final String MODEL = "gemini-3.6-flash";

    private static final String ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/" + MODEL + ":generateContent";

    private final RestClient http;
    private final ObjectMapper json;
    private final String apiKey;

    public GeminiPriceEstimator(ObjectMapper json,
                                @Value("${hokiehub.gemini.api-key:}") String apiKey) {
        this.json = json;
        this.apiKey = apiKey;
        this.http = RestClient.builder()
                .requestFactory(timeouts())
                .build();
    }

    /**
     * A hung request would hold a thread and leave the buyer watching a spinner.
     * Grounded generation is slower than a plain call, so the read timeout is
     * generous, but it is not absent.
     */
    private static org.springframework.http.client.ClientHttpRequestFactory timeouts() {
        var f = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        f.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
        f.setReadTimeout((int) Duration.ofSeconds(60).toMillis());
        return f;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** What the model came back with, before anything is decided about it. */
    public record Estimate(
            String identifiedItem,
            BigDecimal low,
            BigDecimal typical,
            BigDecimal high,
            String summary,
            List<Comparable> comparables
    ) {
        public record Comparable(String title, String url, BigDecimal price, String note) {}
    }

    public Estimate estimate(Listing listing, List<ListingImage> photos) {
        if (!isConfigured()) {
            throw new PriceCheckUnavailableException(
                    "The price check is not configured on this deployment.");
        }

        List<Map<String, Object>> parts = new ArrayList<>();
        parts.add(Map.of("text", prompt(listing)));

        // Photographs first-class, not described: the whole point is that the model
        // looks at the item rather than trusting a seller's title.
        for (ListingImage photo : photos) {
            if (photo.getData() == null) continue;
            parts.add(Map.of("inline_data", Map.of(
                    "mime_type", photo.getContentType() == null ? "image/jpeg" : photo.getContentType(),
                    "data", Base64.getEncoder().encodeToString(photo.getData()))));
        }

        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("role", "user", "parts", parts)),
                // Grounding is the feature. Without it this is a language model
                // reciting plausible numbers.
                "tools", List.of(Map.of("google_search", Map.of())),
                "generationConfig", Map.of(
                        // Low, because this is an extraction task and not a creative one.
                        "temperature", 0.2,
                        "maxOutputTokens", 2048));

        JsonNode response;
        try {
            response = http.post()
                    .uri(ENDPOINT + "?key=" + apiKey)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RuntimeException e) {
            log.warn("Gemini call failed for listing {}: {}", listing.getId(), e.toString());
            throw new PriceCheckFailedException("The valuation service did not respond.");
        }

        String text = extractText(response);
        if (text == null || text.isBlank()) {
            throw new PriceCheckFailedException("The valuation service returned nothing.");
        }

        Estimate parsed = parse(text);

        // The model lists its comparables in the JSON, but the grounding metadata
        // is the API's own record of what search actually returned. If that record
        // is empty then nothing was retrieved, whatever the model wrote — so the
        // estimate has nothing behind it and must not be published as though it
        // has. This is the check that makes "grounded" mean something.
        if (groundingSources(response) == 0) {
            log.info("No grounding sources returned; treating as no comparables");
            return new Estimate(parsed.identifiedItem(), null, null, null,
                    parsed.summary(), List.of());
        }

        return parsed;
    }

    private String prompt(Listing listing) {
        return """
               You are valuing a second-hand item for a student marketplace at Virginia Tech.

               Use Google Search to find what comparable used examples of this item have
               recently sold or been listed for. Base the estimate on what you find.

               The seller says:
                 Title:       %s
                 Description: %s
                 Condition:   %s
                 Category:    %s
                 Asking:      $%s

               Treat the seller's words as a claim, not a fact. The photographs are the
               evidence — if they disagree with the title, say so in the summary.

               Reply with JSON only, no code fence, exactly this shape:
               {
                 "identified_item": "what the item actually is, make and model if visible",
                 "estimated_low": number,
                 "estimated_typical": number,
                 "estimated_high": number,
                 "summary": "two or three sentences a buyer would find useful, including \
               anything the photographs show that the description does not mention",
                 "comparables": [
                   {"title": "...", "url": "...", "price": number, "note": "condition or source"}
                 ]
               }

               Rules you must follow:
               - comparables must be real results you found. Never invent one.
               - If you cannot find comparable used prices, return "comparables": [] and
                 set the three estimate fields to null. An empty answer is correct and
                 useful; a guessed price is neither.
               - Prices are US dollars, numbers only, no currency symbols.
               """.formatted(
                listing.getTitle(),
                listing.getDescription(),
                listing.getCondition() == null ? "not stated" : listing.getCondition().value(),
                listing.getCategory().getName(),
                listing.getPrice());
    }

    /** How many results Google Search actually returned for this call. */
    private int groundingSources(JsonNode response) {
        if (response == null) return 0;
        JsonNode chunks = response.path("candidates").path(0)
                .path("groundingMetadata").path("groundingChunks");
        return chunks.isArray() ? chunks.size() : 0;
    }

    private String extractText(JsonNode response) {
        if (response == null) return null;
        JsonNode parts = response.path("candidates").path(0).path("content").path("parts");
        StringBuilder sb = new StringBuilder();
        for (JsonNode part : parts) {
            if (part.hasNonNull("text")) sb.append(part.get("text").asText());
        }
        return sb.toString();
    }

    private Estimate parse(String text) {
        // Models fence JSON even when told not to; strip it rather than fail.
        String cleaned = text.trim();
        if (cleaned.startsWith("```")) {
            int first = cleaned.indexOf('\n');
            int last = cleaned.lastIndexOf("```");
            if (first > 0 && last > first) cleaned = cleaned.substring(first + 1, last).trim();
        }

        JsonNode node;
        try {
            node = json.readTree(cleaned);
        } catch (Exception e) {
            log.warn("Gemini returned text that is not JSON: {}", cleaned.substring(0, Math.min(200, cleaned.length())));
            throw new PriceCheckFailedException("The valuation service returned an unreadable answer.");
        }

        List<Estimate.Comparable> comparables = new ArrayList<>();
        for (JsonNode c : node.path("comparables")) {
            String title = c.path("title").asText(null);
            if (title == null || title.isBlank()) continue;
            comparables.add(new Estimate.Comparable(
                    title,
                    c.path("url").asText(null),
                    money(c.path("price")),
                    c.path("note").asText(null)));
        }

        return new Estimate(
                node.path("identified_item").asText(null),
                money(node.path("estimated_low")),
                money(node.path("estimated_typical")),
                money(node.path("estimated_high")),
                node.path("summary").asText(null),
                comparables);
    }

    private static BigDecimal money(JsonNode n) {
        if (n == null || n.isNull() || n.isMissingNode()) return null;
        try {
            BigDecimal v = n.isNumber() ? n.decimalValue() : new BigDecimal(n.asText().replaceAll("[^0-9.]", ""));
            return v.signum() <= 0 ? null : v.setScale(2, java.math.RoundingMode.HALF_UP);
        } catch (Exception e) {
            return null;
        }
    }

    /** The deployment has no key. Distinct from a failure, because it is a setup problem. */
    public static class PriceCheckUnavailableException extends RuntimeException {
        public PriceCheckUnavailableException(String message) { super(message); }
    }

    /** The call happened and did not produce a usable answer. */
    public static class PriceCheckFailedException extends RuntimeException {
        public PriceCheckFailedException(String message) { super(message); }
    }
}
