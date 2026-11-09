package com.accountbook.user.internal.oauth;

import com.accountbook.user.internal.AuthProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.stereotype.Component;

/** WeChat Open Platform "website application" QR login. */
@Component
class WechatProvider implements OAuthProvider {

    private final AuthProperties props;
    private final HttpGateway http;
    private final ObjectMapper mapper;

    WechatProvider(AuthProperties props, HttpGateway http, ObjectMapper mapper) {
        this.props = props;
        this.http = http;
        this.mapper = mapper;
    }

    @Override
    public String id() {
        return "wechat";
    }

    @Override
    public boolean configured() {
        var c = props.client(id());
        return c.hasCredentials() && c.clientSecret() != null;
    }

    @Override
    public String authorizeUrl(String state, String redirectUri) {
        return Urls.build("https://open.weixin.qq.com/connect/qrconnect", Map.of(
                "appid", props.client(id()).clientId(), "redirect_uri", redirectUri, "response_type", "code",
                "scope", "snsapi_login", "state", state)) + "#wechat_redirect";
    }

    @Override
    public OAuthProfile exchange(String code, String redirectUri, Map<String, String> extra) {
        var c = props.client(id());
        JsonNode token = Json.parse(mapper, http.get(Urls.build("https://api.weixin.qq.com/sns/oauth2/access_token",
                Map.of("appid", c.clientId(), "secret", c.clientSecret(), "code", code,
                        "grant_type", "authorization_code")), Map.of()));
        String accessToken = Json.text(token, "access_token");
        String openId = Json.text(token, "openid");
        if (accessToken == null || openId == null) {
            throw Json.invalid();
        }
        JsonNode info = Json.parse(mapper, http.get(Urls.build("https://api.weixin.qq.com/sns/userinfo",
                Map.of("access_token", accessToken, "openid", openId)), Map.of()));
        if (info.has("errcode")) {
            throw Json.invalid();
        }
        String union = Json.text(info, "unionid") != null ? Json.text(info, "unionid") : Json.text(token, "unionid");
        return new OAuthProfile(union != null ? union : openId, union, Json.text(info, "nickname"), null,
                Json.text(info, "headimgurl"), info.toString());
    }
}
