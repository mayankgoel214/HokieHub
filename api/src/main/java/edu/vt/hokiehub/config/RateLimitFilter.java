package edu.vt.hokiehub.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * A fixed-window rate limiter over Redis.
 *
 * The marketplace is public and unauthenticated for reads, which means anyone
 * can page through every listing as fast as they can open sockets. Writes are
 * authenticated but nothing stopped one account from creating listings in a
 * loop. Neither is expensive per request; both are unbounded, and unbounded is
 * the problem.
 *
 * Redis was already here caching the category tree. Counting in it — rather than
 * in a map on the heap — is what makes the limit hold across more than one
 * instance, which is the only version of a limit worth having.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    /** Generous: a person browsing hard will not reach it, a scraper will. */
    private static final int READ_LIMIT = 300;
    private static final Duration READ_WINDOW = Duration.ofMinutes(1);

    /** Posting, editing or deleting listings. A real seller does not do 30 an hour. */
    private static final int WRITE_LIMIT = 30;
    private static final Duration WRITE_WINDOW = Duration.ofHours(1);

    private final StringRedisTemplate redis;
    private final boolean enabled;

    public RateLimitFilter(StringRedisTemplate redis,
                           @Value("${hokiehub.rate-limit.enabled:true}") boolean enabled) {
        this.redis = redis;
        this.enabled = enabled;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Health checks are what the platform uses to decide whether this
        // instance is alive; rate limiting them would be self-inflicted.
        String path = request.getRequestURI();
        // Photographs are immutable and cached for a year, and a single page asks
        // for a dozen. Counting them against a browsing budget meant for API calls
        // would throttle someone for scrolling.
        if (path.startsWith("/api/images/") && HttpMethod.GET.matches(request.getMethod())) {
            return true;
        }
        return !path.startsWith("/api/") || HttpMethod.OPTIONS.matches(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!enabled) {
            chain.doFilter(request, response);
            return;
        }

        boolean isWrite = !HttpMethod.GET.matches(request.getMethod());
        int limit = isWrite ? WRITE_LIMIT : READ_LIMIT;
        Duration window = isWrite ? WRITE_WINDOW : READ_WINDOW;
        String key = "ratelimit:" + (isWrite ? "w:" : "r:") + callerId(request);

        Long count;
        try {
            count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redis.expire(key, window);
            }
        } catch (RuntimeException e) {
            // Failing closed here would turn a cache outage into a total outage,
            // and Redis is not the system of record for anything. It is logged at
            // WARN rather than swallowed, because a limiter that is silently not
            // limiting is worse than no limiter at all.
            log.warn("Rate limiting is not being applied: Redis is unreachable ({})", e.toString());
            chain.doFilter(request, response);
            return;
        }

        if (count != null && count > limit) {
            Long ttl = redis.getExpire(key);
            long retryAfter = ttl != null && ttl > 0 ? ttl : window.toSeconds();

            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            response.getWriter().write("""
                    {"type":"about:blank","title":"Too Many Requests","status":429,\
                    "detail":"Rate limit of %d requests per %s exceeded. Retry in %d seconds.",\
                    "instance":"%s"}"""
                    .formatted(limit, humanWindow(window), retryAfter, request.getRequestURI()));
            return;
        }

        response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
        response.setHeader("X-RateLimit-Remaining",
                String.valueOf(Math.max(0, limit - (count == null ? 0 : count))));

        chain.doFilter(request, response);
    }

    /**
     * Authenticated callers are limited as themselves, so one person on a shared
     * campus NAT cannot exhaust everyone else's budget. Anonymous callers fall
     * back to the address.
     */
    private String callerId(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            return "user:" + jwt.getSubject();
        }
        return "ip:" + clientIp(request);
    }

    private String clientIp(HttpServletRequest request) {
        // Railway terminates TLS and proxies, so the socket address is the proxy.
        // Only the first hop of X-Forwarded-For is meaningful; the rest is
        // caller-supplied and trivially spoofed.
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static String humanWindow(Duration window) {
        return window.toHours() >= 1 ? "hour" : "minute";
    }
}
