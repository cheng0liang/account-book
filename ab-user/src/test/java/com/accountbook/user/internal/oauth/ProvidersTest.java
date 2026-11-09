package com.accountbook.user.internal.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.accountbook.common.web.ApiException;
import com.accountbook.user.internal.AuthProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Provider protocols against a scripted gateway: requests are recorded, responses keyed by URL prefix. */
class ProvidersTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static class FakeGateway implements HttpGateway {
        final Map<String, String> responses = new LinkedHashMap<>();
        final List<String> requests = new ArrayList<>();
        Map<String, String> lastForm;
        Map<String, String> lastHeaders;

        @Override
        public String get(String url, Map<String, String> headers) {
            requests.add(url);
            lastHeaders = headers;
            return respond(url);
        }

        @Override
        public String postForm(String url, Map<String, String> form) {
            requests.add(url);
            lastForm = form;
            return respond(url);
        }

        private String respond(String url) {
            return responses.entrySet().stream().filter(e -> url.startsWith(e.getKey())).map(Map.Entry::getValue)
                    .findFirst().orElseThrow(() -> new AssertionError("unexpected call " + url));
        }
    }

    private static AuthProperties props(String name, AuthProperties.Client client) {
        return new AuthProperties(false, true, 30, null, new AuthProperties.Sms(300, 60, 5), Map.of(name, client));
    }

    private static String b64(String s) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String idToken(String iss, String aud, long exp, String sub) {
        String payload = "{\"iss\":\"" + iss + "\",\"aud\":\"" + aud + "\",\"exp\":" + exp + ",\"sub\":\"" + sub
                + "\",\"name\":\"Alice\",\"email\":\"a@x.com\",\"picture\":\"http://p\"}";
        return b64("{\"alg\":\"none\"}") + "." + b64(payload) + ".sig";
    }

    private static long future() {
        return System.currentTimeMillis() / 1000 + 600;
    }

    @Test
    void googleChecksIssuerAudienceAndExpiry() {
        var http = new FakeGateway();
        var p = new GoogleProvider(props("google", new AuthProperties.Client("cid", "sec", null, null, null)), http,
                MAPPER);
        assertThat(p.configured()).isTrue();
        assertThat(p.authorizeUrl("st", "http://cb")).startsWith("https://accounts.google.com/")
                .contains("state=st").contains("client_id=cid");

        http.responses.put("https://oauth2.googleapis.com/token", "{\"id_token\":\""
                + idToken("https://accounts.google.com", "cid", future(), "g-1") + "\"}");
        var profile = p.exchange("the-code", "http://cb", Map.of());
        assertThat(profile.subject()).isEqualTo("g-1");
        assertThat(profile.nickname()).isEqualTo("Alice");
        assertThat(profile.email()).isEqualTo("a@x.com");
        assertThat(http.lastForm).containsEntry("code", "the-code").containsEntry("client_secret", "sec");

        for (String bad : List.of(idToken("https://evil.example", "cid", future(), "g-1"),
                idToken("https://accounts.google.com", "other-client", future(), "g-1"),
                idToken("https://accounts.google.com", "cid", 1, "g-1"))) {
            http.responses.put("https://oauth2.googleapis.com/token", "{\"id_token\":\"" + bad + "\"}");
            assertThatThrownBy(() -> p.exchange("c", "http://cb", Map.of())).isInstanceOf(ApiException.class);
        }
        http.responses.put("https://oauth2.googleapis.com/token", "not json");
        assertThatThrownBy(() -> p.exchange("c", "http://cb", Map.of())).isInstanceOf(ApiException.class);
    }

    @Test
    void lineFetchesTheProfileWithTheBearerToken() {
        var http = new FakeGateway();
        http.responses.put("https://api.line.me/oauth2/v2.1/token", "{\"access_token\":\"AT\"}");
        http.responses.put("https://api.line.me/v2/profile",
                "{\"userId\":\"U123\",\"displayName\":\"Taro\",\"pictureUrl\":\"http://pic\"}");
        var p = new LineProvider(props("line", new AuthProperties.Client("cid", "sec", null, null, null)), http, MAPPER);

        var profile = p.exchange("c", "http://cb", Map.of());
        assertThat(profile.subject()).isEqualTo("U123");
        assertThat(profile.nickname()).isEqualTo("Taro");
        assertThat(http.lastHeaders).containsEntry("Authorization", "Bearer AT");

        http.responses.put("https://api.line.me/oauth2/v2.1/token", "{\"error\":\"invalid_grant\"}");
        assertThatThrownBy(() -> p.exchange("c", "http://cb", Map.of())).isInstanceOf(ApiException.class);
    }

    @Test
    void wechatPrefersUnionIdAndFallsBackToOpenId() {
        var http = new FakeGateway();
        http.responses.put("https://api.weixin.qq.com/sns/oauth2/access_token",
                "{\"access_token\":\"AT\",\"openid\":\"OPEN1\"}");
        http.responses.put("https://api.weixin.qq.com/sns/userinfo",
                "{\"nickname\":\"小明\",\"headimgurl\":\"http://h\",\"unionid\":\"UNION1\"}");
        var p = new WechatProvider(props("wechat", new AuthProperties.Client("wx", "sec", null, null, null)), http,
                MAPPER);
        assertThat(p.authorizeUrl("st", "http://cb")).endsWith("#wechat_redirect").contains("appid=wx");

        var withUnion = p.exchange("c", "http://cb", Map.of());
        assertThat(withUnion.subject()).isEqualTo("UNION1");
        assertThat(withUnion.nickname()).isEqualTo("小明");

        http.responses.put("https://api.weixin.qq.com/sns/userinfo", "{\"nickname\":\"小明\"}");
        assertThat(p.exchange("c", "http://cb", Map.of()).subject()).isEqualTo("OPEN1");

        http.responses.put("https://api.weixin.qq.com/sns/userinfo", "{\"errcode\":40003}");
        assertThatThrownBy(() -> p.exchange("c", "http://cb", Map.of())).isInstanceOf(ApiException.class);
    }

    @Test
    void wechatNeedsASecretToBeConsideredConfigured() {
        var p = new WechatProvider(props("wechat", new AuthProperties.Client("wx", null, null, null, null)),
                new FakeGateway(), MAPPER);
        assertThat(p.configured()).isFalse();
    }

    @Test
    void qqResolvesOpenIdThenUserInfo() {
        var http = new FakeGateway();
        http.responses.put("https://graph.qq.com/oauth2.0/token", "{\"access_token\":\"AT\"}");
        http.responses.put("https://graph.qq.com/oauth2.0/me", "{\"openid\":\"QO\",\"unionid\":\"QU\"}");
        http.responses.put("https://graph.qq.com/user/get_user_info",
                "{\"nickname\":\"企鹅\",\"figureurl_qq_1\":\"http://q1\"}");
        var p = new QqProvider(props("qq", new AuthProperties.Client("101", "sec", null, null, null)), http, MAPPER);

        var profile = p.exchange("c", "http://cb", Map.of());
        assertThat(profile.subject()).isEqualTo("QO");
        assertThat(profile.unionId()).isEqualTo("QU");
        assertThat(profile.nickname()).isEqualTo("企鹅");
        assertThat(profile.avatarUrl()).isEqualTo("http://q1");

        http.responses.put("https://graph.qq.com/oauth2.0/me", "{\"error\":100016}");
        assertThatThrownBy(() -> p.exchange("c", "http://cb", Map.of())).isInstanceOf(ApiException.class);
    }

    @Test
    void appleClientSecretIsAnEs256JwtVerifiableWithThePublicKey() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(256);
        KeyPair pair = gen.generateKeyPair();
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(pair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----";
        var http = new FakeGateway();
        var p = new AppleProvider(props("apple", new AuthProperties.Client("com.example.web", null, "TEAM1", "KEY1",
                pem)), http, MAPPER);
        assertThat(p.configured()).isTrue();

        String jwt = p.clientSecret(1_000);
        String[] parts = jwt.split("\\.");
        assertThat(new String(Base64.getUrlDecoder().decode(parts[0]))).contains("ES256").contains("KEY1");
        var payload = MAPPER.readTree(Base64.getUrlDecoder().decode(parts[1]));
        assertThat(payload.get("iss").asText()).isEqualTo("TEAM1");
        assertThat(payload.get("sub").asText()).isEqualTo("com.example.web");
        assertThat(payload.get("aud").asText()).isEqualTo("https://appleid.apple.com");
        assertThat(payload.get("exp").asLong()).isEqualTo(1_300);

        Signature verify = Signature.getInstance("SHA256withECDSAinP1363Format");
        verify.initVerify(pair.getPublic());
        verify.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(verify.verify(Base64.getUrlDecoder().decode(parts[2]))).isTrue();

        http.responses.put("https://appleid.apple.com/auth/token", "{\"id_token\":\""
                + idToken("https://appleid.apple.com", "com.example.web", future(), "A-1") + "\"}");
        var profile = p.exchange("c", "http://cb",
                Map.of("user", "{\"name\":{\"firstName\":\"Taro\",\"lastName\":\"Yamada\"}}"));
        assertThat(profile.subject()).isEqualTo("A-1");
        assertThat(profile.nickname()).isEqualTo("YamadaTaro");
        assertThat(http.lastForm.get("client_secret").split("\\.")).hasSize(3);
        assertThat(p.exchange("c", "http://cb", Map.of()).nickname()).isNull();
    }

    @Test
    void appleWithABrokenKeyReportsMisconfiguration() {
        var p = new AppleProvider(props("apple", new AuthProperties.Client("id", null, "T", "K", "garbage")),
                new FakeGateway(), MAPPER);
        assertThatThrownBy(() -> p.clientSecret(1)).isInstanceOf(ApiException.class);
    }

    @Test
    void urlsEncodeQueryValues() {
        assertThat(Urls.build("https://x.example/a", Map.of("q", "a b&c"))).isEqualTo("https://x.example/a?q=a+b%26c");
    }
}
