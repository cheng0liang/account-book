package com.accountbook.user.internal.oauth;

import com.accountbook.user.internal.AuthProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
class QqProvider implements OAuthProvider {

    private final AuthProperties props;
    private final HttpGateway http;
    private final ObjectMapper mapper;

    QqProvider(AuthProperties props, HttpGateway http, ObjectMapper mapper) {
        this.props = props;
        this.http = http;
        this.mapper = mapper;
    }

    @Override
    public String id() {
        return "qq";
    }

    @Override
    public boolean configured() {
        var c = props.client(id());
        return c.hasCredentials() && c.clientSecret() != null;
    }

    @Override
    public String authorizeUrl(String state, String redirectUri) {
        return Urls.build("https://graph.qq.com/oauth2.0/authorize", Map.of(
                "response_type", "code", "client_id", props.client(id()).clientId(),
                "redirect_uri", redirectUri, "scope", "get_user_info", "state", state));
    }

    @Override
    public OAuthProfile exchange(String code, String redirectUri, Map<String, String> extra) {
        var c = props.client(id());
        JsonNode token = Json.parse(mapper, http.get(Urls.build("https://graph.qq.com/oauth2.0/token", Map.of(
                "grant_type", "authorization_code", "client_id", c.clientId(), "client_secret", c.clientSecret(),
                "code", code, "redirect_uri", redirectUri, "fmt", "json")), Map.of()));
        String accessToken = Json.text(token, "access_token");
        if (accessToken == null) {
            throw Json.invalid();
        }
        JsonNode me = Json.parse(mapper, http.get(Urls.build("https://graph.qq.com/oauth2.0/me",
                Map.of("access_token", accessToken, "unionid", "1", "fmt", "json")), Map.of()));
        String openId = Json.text(me, "openid");
        if (openId == null) {
            throw Json.invalid();
        }
        JsonNode info = Json.parse(mapper, http.get(Urls.build("https://graph.qq.com/user/get_user_info", Map.of(
                "access_token", accessToken, "oauth_consumer_key", c.clientId(), "openid", openId)), Map.of()));
        String avatar = Json.text(info, "figureurl_qq_2") != null ? Json.text(info, "figureurl_qq_2")
                : Json.text(info, "figureurl_qq_1");
        return new OAuthProfile(openId, Json.text(me, "unionid"), Json.text(info, "nickname"), null, avatar,
                info.toString());
    }
}
