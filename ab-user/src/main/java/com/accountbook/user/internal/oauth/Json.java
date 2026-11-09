package com.accountbook.user.internal.oauth;

import com.accountbook.common.web.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.http.HttpStatus;

final class Json {

    private Json() {
    }

    /** Parses a provider response; some (WeChat, QQ) label JSON as text/plain, so content type is ignored. */
    static JsonNode parse(ObjectMapper mapper, String body) {
        try {
            return mapper.readTree(body);
        } catch (JsonProcessingException | NullPointerException e) {
            throw ApiException.of(HttpStatus.BAD_GATEWAY, "BAD_GATEWAY", "auth.oauth.upstream");
        }
    }

    static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() || v.asText().isBlank() ? null : v.asText();
    }

    /**
     * Claims of an OIDC id_token. The token arrives straight from the provider's token endpoint over TLS, which
     * OIDC Core 3.1.3.7 accepts in place of verifying the signature; issuer, audience and expiry are still checked.
     */
    static JsonNode idTokenClaims(ObjectMapper mapper, String idToken) {
        String[] parts = idToken == null ? new String[0] : idToken.split("\\.");
        if (parts.length < 2) {
            throw ApiException.of(HttpStatus.BAD_GATEWAY, "BAD_GATEWAY", "auth.oauth.upstream");
        }
        return parse(mapper, new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));
    }

    static void requireClaims(JsonNode claims, java.util.Set<String> issuers, String audience, long nowSeconds) {
        String iss = text(claims, "iss");
        boolean audOk = claims.has("aud") && (claims.get("aud").isArray()
                ? java.util.stream.StreamSupport.stream(claims.get("aud").spliterator(), false)
                        .anyMatch(a -> audience.equals(a.asText()))
                : audience.equals(claims.get("aud").asText()));
        boolean fresh = claims.has("exp") && claims.get("exp").asLong() > nowSeconds;
        if (iss == null || !issuers.contains(iss) || !audOk || !fresh || text(claims, "sub") == null) {
            throw ApiException.of(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "auth.oauth.invalid");
        }
    }

    static ApiException invalid() {
        return ApiException.of(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "auth.oauth.invalid");
    }
}
