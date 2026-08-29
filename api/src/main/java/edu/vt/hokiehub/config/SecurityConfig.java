package edu.vt.hokiehub.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Authentication is delegated to Supabase: the Next.js client already holds a
 * Supabase session, and this service verifies that session's JWT rather than
 * running a second identity system.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * The Supabase project's base URL, e.g. https://abcdefg.supabase.co.
     *
     * Deliberately without a default. This is the only thing standing between an
     * anonymous request and a forged session, and application.yml used to carry a
     * development fallback for the old shared secret — which meant a deployment
     * that simply forgot to configure it came up looking healthy while accepting
     * tokens anyone could mint from a string committed to this repository. Missing
     * configuration must stop the service, not be quietly substituted.
     */
    @Value("${hokiehub.supabase.url:}")
    private String supabaseUrl;

    @Value("${hokiehub.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, RateLimitFilter rateLimit) throws Exception {
        http
            // No cookies, no server-side session: every request carries its own bearer
            // token, so CSRF has nothing to attack.
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Declared before the wildcard below, which would otherwise match it:
                // "/api/listings/*" covers a single path segment, and "mine" is one.
                // Left permitted, the controller received a null principal and
                // answered an anonymous request with a 500 instead of a 401.
                .requestMatchers(HttpMethod.GET, "/api/listings/mine").authenticated()
                // Browsing the marketplace does not require an account; posting does.
                .requestMatchers(HttpMethod.GET, "/api/listings", "/api/listings/*").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/categories").permitAll()
                .requestMatchers("/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**",
                                 "/swagger-ui.html").permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.decoder(jwtDecoder())))
            // After authentication, so an authenticated caller is counted as
            // themselves rather than as whatever address they share.
            .addFilterAfter(rateLimit, BearerTokenAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Any Filter bean is otherwise also registered with the servlet container,
     * which would run it a second time and outside the security chain, where
     * there is no authenticated principal to attribute the request to.
     */
    @Bean
    FilterRegistrationBean<RateLimitFilter> rateLimitNotRegisteredTwice(RateLimitFilter filter) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    JwtDecoder jwtDecoder() {
        if (supabaseUrl == null || supabaseUrl.isBlank()) {
            throw new IllegalStateException(
                    "SUPABASE_URL is not set. It is the Supabase project's base URL, "
                  + "e.g. https://your-ref.supabase.co, and without it this service "
                  + "cannot tell a real session from a forged one. Set it before starting.");
        }

        String issuer = supabaseUrl.replaceAll("/+$", "") + "/auth/v1";
        return decoderFor(issuer);
    }

    /**
     * Package-private so a test can point it at a JWKS it controls and assert on
     * real signature verification. The mock JWT used elsewhere in the suite
     * bypasses the decoder entirely, so nothing else covers this path.
     */
    static JwtDecoder decoderFor(String issuer) {

        // Supabase signs project JWTs with an asymmetric key (ES256) and publishes
        // the public half at the project's JWKS endpoint. Verifying against that is
        // both what actually works and strictly safer than the legacy HS256 shared
        // secret: there is no secret to store on the API host, and rotating the
        // signing key needs no redeploy here.
        // The algorithms have to be named. NimbusJwtDecoder defaults to expecting
        // RS256, and Supabase signs with ES256, which fails as "Another algorithm
        // expected, or no matching key(s) found" — a true statement that does not
        // say which algorithm. Both asymmetric families are accepted so a project
        // that rotates to an RSA signing key keeps working.
        //
        // HS256 is deliberately absent: accepting a symmetric algorithm alongside
        // a published public key is the algorithm-confusion attack, where the
        // public key everyone can read becomes the shared secret.
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withJwkSetUri(issuer + "/.well-known/jwks.json")
                .jwsAlgorithms(algorithms -> {
                    algorithms.add(SignatureAlgorithm.ES256);
                    algorithms.add(SignatureAlgorithm.RS256);
                })
                .build();

        // A signature check alone would accept a correctly signed token from a
        // different Supabase project. Pinning the issuer says which project.
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));

        return decoder;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
