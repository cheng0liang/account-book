package com.accountbook.user.internal.oauth;

import com.accountbook.common.web.ApiException;
import com.accountbook.user.internal.AuthProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Sign in with Apple, web flow: the client secret is a short-lived ES256 JWT signed with the team's private key. */
@Component
class AppleProvider implements OAuthProvider {

    private static final String ISSUER = "https://appleid.apple.com";

    private final AuthProperties props;
    private final HttpGateway http;
    private final ObjectMapper mapper;

    AppleProvider(AuthProperties props, HttpGateway http, ObjectMapper mapper) {
        this.props = props;
        this.http = http;
        this.mapper = mapper;
    }

    @Override
    public String id() {
        return "apple";
    }

    @Override
    public boolean configured() {
        var c = props.client(id());
        return c.hasCredentials() && c.teamId() != null && c.keyId() != null && c.privateKey() != null;
    }

    @Override
    public String authorizeUrl(String state, String redirectUri) {
        return Urls.build(ISSUER + "/auth/authorize", Map.of(
                "response_type", "code", "response_mode", "form_post", "client_id", props.client(id()).clientId(),
                "redirect_uri", redirectUri, "scope", "name email", "state", state));
    }

    @Override
    public OAuthProfile exchange(String code, String redirectUri, Map<String, String> extra) {
        var c = props.client(id());
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "authorization_code");
        form.put("code", code);
        form.put("client_id", c.clientId());
        form.put("client_secret", clientSecret(System.currentTimeMillis() / 1000));
        form.put("redirect_uri", redirectUri);
        JsonNode token = Json.parse(mapper, http.postForm(ISSUER + "/auth/token", form));
        JsonNode claims = Json.idTokenClaims(mapper, Json.text(token, "id_token"));
        Json.requireClaims(claims, Set.of(ISSUER), c.clientId(), System.currentTimeMillis() / 1000);
        return new OAuthProfile(Json.text(claims, "sub"), null, userName(extra.get("user")),
                Json.text(claims, "email"), null, claims.toString());
    }

    /** Apple only sends the name, as a JSON blob in the callback, the first time a user authorizes the app. */
    private String userName(String userJson) {
        if (userJson == null || userJson.isBlank()) {
            return null;
        }
        try {
            JsonNode name = mapper.readTree(userJson).path("name");
            String full = (Json.text(name, "lastName") == null ? "" : Json.text(name, "lastName"))
                    + (Json.text(name, "firstName") == null ? "" : Json.text(name, "firstName"));
            return full.isBlank() ? null : full;
        } catch (Exception e) {
            return null;
        }
    }

    String clientSecret(long nowSeconds) {
        var c = props.client(id());
        String header = "{\"alg\":\"ES256\",\"kid\":\"" + c.keyId() + "\"}";
        String payload = "{\"iss\":\"" + c.teamId() + "\",\"iat\":" + nowSeconds + ",\"exp\":" + (nowSeconds + 300)
                + ",\"aud\":\"" + ISSUER + "\",\"sub\":\"" + c.clientId() + "\"}";
        String signingInput = b64(header.getBytes(StandardCharsets.UTF_8)) + "."
                + b64(payload.getBytes(StandardCharsets.UTF_8));
        try {
            Signature sig = Signature.getInstance("SHA256withECDSAinP1363Format");
            sig.initSign(privateKey(c.privateKey()));
            sig.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            return signingInput + "." + b64(sig.sign());
        } catch (Exception e) {
            throw ApiException.of(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "auth.oauth.misconfigured");
        }
    }

    private static PrivateKey privateKey(String pem) throws Exception {
        String body = pem.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
        return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(body)));
    }

    private static String b64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
