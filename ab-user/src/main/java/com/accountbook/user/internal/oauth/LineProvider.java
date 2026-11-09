package com.accountbook.user.internal.oauth;

import com.accountbook.user.internal.AuthProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
class LineProvider implements OAuthProvider {

    private final AuthProperties props;
    private final HttpGateway http;
    private final ObjectMapper mapper;

    LineProvider(AuthProperties props, HttpGateway http, ObjectMapper mapper) {
        this.props = props;
        this.http = http;
        this.mapper = mapper;
    }

    @Override
    public String id() {
        return "line";
    }

    @Override
    public boolean configured() {
        return props.client(id()).hasCredentials();
    }

    @Override
    public String authorizeUrl(String state, String redirectUri) {
        return Urls.build("https://access.line.me/oauth2/v2.1/authorize", Map.of(
                "response_type", "code", "client_id", props.client(id()).clientId(),
                "redirect_uri", redirectUri, "scope", "profile openid", "state", state));
    }

    @Override
    public OAuthProfile exchange(String code, String redirectUri, Map<String, String> extra) {
        var c = props.client(id());
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "authorization_code");
        form.put("code", code);
        form.put("redirect_uri", redirectUri);
        form.put("client_id", c.clientId());
        form.put("client_secret", c.clientSecret());
        JsonNode token = Json.parse(mapper, http.postForm("https://api.line.me/oauth2/v2.1/token", form));
        String accessToken = Json.text(token, "access_token");
        if (accessToken == null) {
            throw Json.invalid();
        }
        JsonNode me = Json.parse(mapper, http.get("https://api.line.me/v2/profile",
                Map.of("Authorization", "Bearer " + accessToken)));
        String userId = Json.text(me, "userId");
        if (userId == null) {
            throw Json.invalid();
        }
        return new OAuthProfile(userId, null, Json.text(me, "displayName"), null, Json.text(me, "pictureUrl"),
                me.toString());
    }
}
