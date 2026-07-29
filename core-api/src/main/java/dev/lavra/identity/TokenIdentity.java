package dev.lavra.identity;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The bits of an OIDC token the local user record is built from.
 *
 * @param externalId stable identifier of the user at the IdP
 */
record TokenIdentity(String externalId, String email, String displayName) {

    static TokenIdentity from(Jwt jwt) {
        // Entra External ID issues `oid` (stable per tenant); `sub` is the
        // fallback and is what a plain OIDC provider gives.
        String externalId = firstNonBlank(jwt.getClaimAsString("oid"), jwt.getSubject());
        if (externalId == null) {
            throw new IllegalArgumentException("Token carries neither oid nor sub");
        }

        String email = firstNonBlank(
                jwt.getClaimAsString("email"),
                jwt.getClaimAsString("preferred_username"),
                jwt.getClaimAsString("upn"));
        if (email == null) {
            throw new IllegalArgumentException("Token carries no e-mail claim for " + externalId);
        }

        return new TokenIdentity(externalId, email, jwt.getClaimAsString("name"));
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
