package edu.vt.hokiehub.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
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
     * Deliberately without a default. This value is the only thing standing between
     * an anonymous request and a forged session, and application.yml used to carry a
     * development fallback — which meant a deployment that simply forgot to set the
     * variable came up looking healthy while accepting tokens anyone could mint from
     * a string committed to this repository. A missing secret must stop the service,
     * not be quietly substituted.
     */
    @Value("${hokiehub.jwt.secret:}")
    private String jwtSecret;

    @Value("${hokiehub.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
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
            .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.decoder(jwtDecoder())));

        return http.build();
    }

    @Bean
    JwtDecoder jwtDecoder() {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException(
                    "SUPABASE_JWT_SECRET is not set. It is the Supabase project's JWT "
                  + "secret, and without it this service cannot tell a real session "
                  + "from a forged one. Set it before starting.");
        }

        // Supabase signs project JWTs with HS256 using the project's JWT secret.
        SecretKeySpec key = new SecretKeySpec(
                jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
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
