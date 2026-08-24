package edu.vt.hokiehub.service;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The authenticated user, as the token describes them. Supabase is the identity
 * provider, so a user can present a perfectly valid token before this service has
 * ever seen them; carrying the claims through lets the account be provisioned on
 * first write instead of failing.
 */
public record Caller(String id, String email, String fullName) {

    public static Caller from(Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        return new Caller(jwt.getSubject(), email, displayName(jwt, email));
    }

    private static String displayName(Jwt jwt, String email) {
        Object metadata = jwt.getClaim("user_metadata");
        if (metadata instanceof java.util.Map<?, ?> map) {
            Object name = map.get("full_name");
            if (name == null) name = map.get("name");
            if (name != null && !name.toString().isBlank()) {
                return name.toString();
            }
        }
        // Falling back to the local part beats storing an empty name.
        return email != null && email.contains("@") ? email.substring(0, email.indexOf('@')) : "Hokie";
    }
}
