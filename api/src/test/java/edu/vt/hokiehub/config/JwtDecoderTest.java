package edu.vt.hokiehub.config;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The only test that exercises real signature verification.
 *
 * Everywhere else the suite uses Spring Security's mock `jwt()` post-processor,
 * which hands the controller a ready-made principal and never invokes the
 * decoder — so the entire signature path was untested, and shipped broken: the
 * decoder expected RS256 while Supabase signs with ES256, and every genuine
 * login came back 401 with "Another algorithm expected, or no matching key(s)
 * found".
 *
 * This serves a JWKS from a throwaway local HTTP server, exactly as Supabase
 * does, and checks what the decoder accepts and refuses.
 */
class JwtDecoderTest {

    private static HttpServer server;
    private static ECKey signingKey;
    private static String issuer;

    @BeforeAll
    static void startJwksServer() throws Exception {
        signingKey = new ECKeyGenerator(Curve.P_256)
                .keyID("test-key")
                .generate();

        String jwks = new JWKSet(signingKey.toPublicJWK()).toString();

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/auth/v1/.well-known/jwks.json", exchange -> {
            byte[] body = jwks.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();

        issuer = "http://127.0.0.1:" + server.getAddress().getPort() + "/auth/v1";
    }

    @AfterAll
    static void stopJwksServer() {
        server.stop(0);
    }

    private static JWTClaimsSet claims(String issuerClaim) {
        return new JWTClaimsSet.Builder()
                .subject("2f6c1f6e-0000-4000-8000-000000000001")
                .issuer(issuerClaim)
                .audience("authenticated")
                .claim("email", "student@vt.edu")
                .issueTime(Date.from(Instant.now()))
                .expirationTime(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .build();
    }

    private static String signedWithEs256(JWTClaimsSet claimsSet) throws Exception {
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.ES256)
                        .keyID(signingKey.getKeyID())
                        .type(JOSEObjectType.JWT)
                        .build(),
                claimsSet);
        jwt.sign(new ECDSASigner(signingKey));
        return jwt.serialize();
    }

    @Test
    @DisplayName("an ES256 token from the project's own JWKS is accepted")
    void acceptsEs256() throws Exception {
        Jwt decoded = SecurityConfig.decoderFor(issuer).decode(signedWithEs256(claims(issuer)));

        assertThat(decoded.getSubject()).isEqualTo("2f6c1f6e-0000-4000-8000-000000000001");
        assertThat(decoded.getClaimAsString("email")).isEqualTo("student@vt.edu");
    }

    @Test
    @DisplayName("a correctly signed token from a different project is refused")
    void refusesAnotherIssuer() throws Exception {
        // Signature alone proves the token was minted by *someone* with a key;
        // the issuer claim is what says it was this project.
        String foreign = signedWithEs256(claims("https://someone-elses-project.supabase.co/auth/v1"));

        assertThatThrownBy(() -> SecurityConfig.decoderFor(issuer).decode(foreign))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("an HMAC token is refused even when signed with the published public key")
    void refusesAlgorithmConfusion() throws Exception {
        // The classic attack on a JWKS setup: take the public key everyone can
        // read, use it as an HMAC secret, and hope the server treats a symmetric
        // algorithm as valid. It must not be in the accepted set at all.
        byte[] publicKeyAsSecret = signingKey.toPublicJWK().toJSONString()
                .getBytes(StandardCharsets.UTF_8);

        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.HS256).keyID(signingKey.getKeyID()).build(),
                claims(issuer));
        jwt.sign(new MACSigner(publicKeyAsSecret));

        assertThatThrownBy(() -> SecurityConfig.decoderFor(issuer).decode(jwt.serialize()))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("an expired token is refused")
    void refusesExpired() throws Exception {
        JWTClaimsSet expired = new JWTClaimsSet.Builder(claims(issuer))
                .expirationTime(Date.from(Instant.now().minus(1, ChronoUnit.HOURS)))
                .build();

        assertThatThrownBy(() -> SecurityConfig.decoderFor(issuer).decode(signedWithEs256(expired)))
                .isInstanceOf(JwtException.class);
    }
}
