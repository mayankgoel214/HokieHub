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

    /**
     * Two calls, not one: retrieve, then structure.
     *
     * Asked to value an item and reply as JSON, the model simply does not search
     * — measured, repeatedly: zero searches run, zero grounding chunks returned,
     * and four confidently invented "comparables" in the reply. Asked instead to
     * go and look up what something is selling for, it runs a handful of searches
     * and comes back with real listings. The schema was suppressing the tool use.
     *
     * So the first call does the looking and is allowed to answer in prose, and
     * the second turns what it found into the shape this service stores. The
     * second call has no tools and is told to use nothing but the findings it is
     * handed, so it cannot quietly add a comparable of its own.
     */
    public Estimate estimate(Listing listing, List<ListingImage> photos) {
        if (!isConfigured()) {
            throw new PriceCheckUnavailableException(
                    "The price check is not configured on this deployment.");
        }

        JsonNode research = call(searchBody(listing, photos));
        String findings = extractText(research);
        int sources = groundingSources(research);

        if (sources == 0 || findings == null || findings.isBlank()) {
            // Nothing was actually retrieved. Whatever the model may be willing to
            // say about the price, there is nothing behind it.
            log.info("No grounding sources for listing {}; reporting no comparables",
                    listing.getId());
            return new Estimate(null, null, null, null, null, List.of());
        }

        JsonNode structured = call(structureBody(listing, findings));
        String text = extractText(structured);
        if (text == null || text.isBlank()) {
            throw new PriceCheckFailedException("The valuation service returned nothing.");
        }

        return parse(text);
    }

    private JsonNode call(Map<String, Object> body) {
        try {
            return http.post()
                    .uri(ENDPOINT + "?key=" + apiKey)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RuntimeException e) {
            log.warn("Gemini call failed: {}", e.toString());
            throw new PriceCheckFailedException("The valuation service did not respond.");
        }
    }

    /** Stage one: go and look, in the shape of question that makes it look. */
    private Map<String, Object> searchBody(Listing listing, List<ListingImage> photos) {
        List<Map<String, Object>> parts = new ArrayList<>();
        parts.add(Map.of("text", """
               Search eBay, Swappa, Facebook Marketplace and Google Shopping for what
               this used item is currently listed or recently sold for, and tell me
               what you find. Include the price and the link for each result.

               The item: %s
               Described by the seller as: %s
               Condition the seller claims: %s

               %sIf the photographs show something different from the description, say
               so. If you cannot find any comparable used listings, say that plainly
               rather than estimating.
               """.formatted(
                listing.getTitle(),
                listing.getDescription(),
                listing.getCondition() == null ? "not stated" : listing.getCondition().value(),
                photos.isEmpty()
                        ? "There are no photographs of this particular item. "
                        : "Photographs of the actual item are attached — identify it from those. ")));

        for (ListingImage photo : photos) {
            if (photo.getData() == null) continue;
            parts.add(Map.of("inline_data", Map.of(
                    "mime_type", photo.getContentType() == null ? "image/jpeg" : photo.getContentType(),
                    "data", Base64.getEncoder().encodeToString(photo.getData()))));
        }

        return Map.of(
                "contents", List.of(Map.of("role", "user", "parts", parts)),
                "tools", List.of(Map.of("google_search", Map.of())),
                "generationConfig", Map.of("temperature", 0.2, "maxOutputTokens", 8192));
    }

    /** Stage two: turn what was found into the stored shape. No tools, no additions. */
    private Map<String, Object> structureBody(Listing listing, String findings) {
        String text = """
               Here is research on what a used item is selling for:

               ---
               %s
               ---

               The seller is asking $%s for it.

               Turn that research into JSON. Reply with JSON only, no code fence:
               {
                 "identified_item": "what the item actually is, make and model",
                 "estimated_low": number,
                 "estimated_typical": number,
                 "estimated_high": number,
                 "summary": "two or three sentences a buyer would find useful",
                 "comparables": [
                   {"title": "...", "url": "...", "price": number, "note": "condition or source"}
                 ]
               }

               Use nothing but the research above. Every comparable must appear in it —
               do not add one from your own knowledge, and do not invent a URL. If the
               research found no comparable listings, return "comparables": [] and null
               for the three estimates.
               """.formatted(findings, listing.getPrice());

        return Map.of(
                "contents", List.of(Map.of("role", "user",
                        "parts", List.of(Map.of("text", text)))),
                "generationConfig", Map.of("temperature", 0.1, "maxOutputTokens", 8192));
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
            // The length is logged because truncation is the likely cause and it
            // is invisible in the first 200 characters.
            log.warn("Gemini returned {} characters that are not JSON: {}",
                    cleaned.length(), cleaned.substring(0, Math.min(300, cleaned.length())));
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
