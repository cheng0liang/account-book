package com.accountbook.user.internal.oauth;

import com.accountbook.user.internal.AuthProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
class GoogleProvider implements OAuthProvider {

    private final AuthProperties props;
    private final HttpGateway http;
    private final ObjectMapper mapper;

    GoogleProvider(AuthProperties props, HttpGateway http, ObjectMapper mapper) {
        this.props = props;
        this.http = http;
        this.mapper = mapper;
    }

    @Override
    public String id() {
        return "google";
    }

    @Override
    public boolean configured() {
        return props.client(id()).hasCredentials();
    }

    @Override
    public String authorizeUrl(String state, String redirectUri) {
        return Urls.build("https://accounts.google.com/o/oauth2/v2/auth", Map.of(
                "response_type", "code", "client_id", props.client(id()).clientId(),
                "redirect_uri", redirectUri, "scope", "openid email profile", "state", state));
    }

    @Override
    public OAuthProfile exchange(String code, String redirectUri, Map<String, String> extra) {
        var c = props.client(id());
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "authorization_code");
        form.put("code", code);
        form.put("client_id", c.clientId());
        form.put("client_secret", c.clientSecret());
        form.put("redirect_uri", redirectUri);
        JsonNode token = Json.parse(mapper, http.postForm("https://oauth2.googleapis.com/token", form));
        JsonNode claims = Json.idTokenClaims(mapper, Json.text(token, "id_token"));
        Json.requireClaims(claims, Set.of("accounts.google.com", "https://accounts.google.com"), c.clientId(),
                System.currentTimeMillis() / 1000);
        return new OAuthProfile(Json.text(claims, "sub"), null, Json.text(claims, "name"),
                Json.text(claims, "email"), Json.text(claims, "picture"), claims.toString());
    }
}
