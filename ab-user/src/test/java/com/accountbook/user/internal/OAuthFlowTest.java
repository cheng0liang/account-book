package com.accountbook.user.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.accountbook.common.testing.TestDb;
import com.accountbook.common.web.ApiException;
import com.accountbook.user.api.event.UserRegisteredEvent;
import com.accountbook.user.internal.oauth.OAuthProfile;
import com.accountbook.user.internal.oauth.OAuthProvider;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class OAuthFlowTest {

    private TestDb db;
    private UserRepository repo;
    private OAuthService oauth;
    private SessionService sessions;
    private AccountService accounts;
    private final List<Object> events = new ArrayList<>();
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/x");

    /** A configured provider whose "exchange" returns whatever the test put in {@code profiles}. */
    private final Map<String, OAuthProfile> profiles = new HashMap<>();
    private final OAuthProvider fake = new OAuthProvider() {
        @Override
        public String id() {
            return "google";
        }

        @Override
        public boolean configured() {
            return true;
        }

        @Override
        public String authorizeUrl(String state, String redirectUri) {
            return "https://idp.example/auth?state=" + state + "&redirect_uri=" + redirectUri;
        }

        @Override
        public OAuthProfile exchange(String code, String redirectUri, Map<String, String> extra) {
            OAuthProfile p = profiles.get(code);
            if (p == null) {
                throw ApiException.badRequest("auth.oauth.invalid");
            }
            return p;
        }
    };
    private final OAuthProvider unconfigured = new OAuthProvider() {
        @Override
        public String id() {
            return "line";
        }

        @Override
        public boolean configured() {
            return false;
        }

        @Override
        public String authorizeUrl(String state, String redirectUri) {
            throw new IllegalStateException("never called when unconfigured");
        }

        @Override
        public OAuthProfile exchange(String code, String redirectUri, Map<String, String> extra) {
            throw new IllegalStateException("never called when unconfigured");
        }
    };

    private void build(boolean devMode, String publicBase) {
        var props = new AuthProperties(devMode, true, 30, publicBase, new AuthProperties.Sms(300, 60, 5, ""), Map.of());
        accounts = new AccountService(repo, events::add);
        sessions = new SessionService(repo, props);
        oauth = new OAuthService(List.of(fake, unconfigured), repo, accounts, sessions, props);
    }

    @BeforeEach
    void setUp() {
        db = TestDb.create("user");
        repo = new UserRepository(db.jdbc());
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(8080);
        build(false, null);
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    private static String param(String url, String name) {
        var m = java.util.regex.Pattern.compile("[?&]" + name + "=([^&]+)").matcher(url);
        assertThat(m.find()).as(name + " in " + url).isTrue();
        return java.net.URLDecoder.decode(m.group(1), java.nio.charset.StandardCharsets.UTF_8);
    }

    private String stateOf(String url) {
        return param(url, "state");
    }

    @Test
    void fullLoginRoundTripGoesThroughStateThenSingleUseTicketThenLocationAndSession() {
        profiles.put("good", new OAuthProfile("sub-1", "u-1", "Alice", "a@x.com", "http://img", "{}"));

        String url = oauth.authorizeUrl("google", null, null, request);
        assertThat(param(url, "redirect_uri")).isEqualTo("http://localhost:8080/api/auth/oauth/google/callback");

        String location = oauth.callback("google", Map.of("code", "good", "state", stateOf(url)), request);
        assertThat(location).startsWith("/#/login?ticket=");
        String ticket = location.substring(location.indexOf("ticket=") + 7);

        var login = oauth.exchangeTicket(ticket, AuthFlowTest.TOKYO, ClientInfo.none());
        assertThat(login.user().nickname()).isEqualTo("Alice");
        assertThat(sessions.authenticate(login.token())).contains(login.user().id());
        assertThat(db.jdbc().sql("SELECT provider, provider_uid, union_id, email FROM usr_auth_account").query()
                .singleRow()).containsEntry("provider", "GOOGLE").containsEntry("provider_uid", "sub-1")
                .containsEntry("union_id", "u-1").containsEntry("email", "a@x.com");
        assertThat(db.jdbc().sql("SELECT provider FROM usr_login_location").query(String.class).single())
                .isEqualTo("GOOGLE");
        assertThat(events).containsExactly(new UserRegisteredEvent(login.user().id(), true));

        assertThatThrownBy(() -> oauth.exchangeTicket(ticket, AuthFlowTest.TOKYO, ClientInfo.none()))
                .as("ticket is single use").isInstanceOf(ApiException.class);
    }

    @Test
    void ticketIsNotConsumedWhenLocationIsMissing() {
        profiles.put("good", new OAuthProfile("sub-1", null, "Alice", null, null, null));
        String url = oauth.authorizeUrl("google", null, null, request);
        String location = oauth.callback("google", Map.of("code", "good", "state", stateOf(url)), request);
        String ticket = location.substring(location.indexOf("ticket=") + 7);

        assertThatThrownBy(() -> oauth.exchangeTicket(ticket, null, ClientInfo.none())).isInstanceOf(ApiException.class);
        assertThat(oauth.exchangeTicket(ticket, AuthFlowTest.TOKYO, ClientInfo.none()).token()).isNotBlank();
    }

    @Test
    void returningUserIsMatchedByProviderIdentityNotCreatedAgain() {
        profiles.put("good", new OAuthProfile("sub-1", null, "Alice", null, null, null));
        for (int i = 0; i < 2; i++) {
            String url = oauth.authorizeUrl("google", null, null, request);
            oauth.callback("google", Map.of("code", "good", "state", stateOf(url)), request);
        }
        assertThat(db.jdbc().sql("SELECT COUNT(*) FROM usr_user").query(Long.class).single()).isEqualTo(1);
        assertThat(events).hasSize(1);
    }

    @Test
    void badOrReusedStateIsRejected() {
        profiles.put("good", new OAuthProfile("sub-1", null, "Alice", null, null, null));
        assertThat(oauth.callback("google", Map.of("code", "good", "state", "nope"), request))
                .isEqualTo("/#/login?error=state");
        assertThat(oauth.callback("google", Map.of("code", "good"), request)).isEqualTo("/#/login?error=state");

        String url = oauth.authorizeUrl("google", null, null, request);
        String state = stateOf(url);
        oauth.callback("google", Map.of("code", "good", "state", state), request);
        assertThat(oauth.callback("google", Map.of("code", "good", "state", state), request))
                .as("state is single use").isEqualTo("/#/login?error=state");

        String expired = oauth.authorizeUrl("google", null, null, request);
        db.jdbc().sql("UPDATE usr_oauth_state SET created_at = 1").update();
        assertThat(oauth.callback("google", Map.of("code", "good", "state", stateOf(expired)), request))
                .isEqualTo("/#/login?error=state");
    }

    @Test
    void stateIsBoundToTheProviderItWasIssuedFor() {
        build(true, null);
        String url = oauth.authorizeUrl("line", null, "bob", request);
        assertThat(oauth.callback("google", Map.of("code", "mock-bob", "state", stateOf(url)), request))
                .isEqualTo("/#/login?error=state");
    }

    @Test
    void providerDenialAndExchangeFailureRedirectWithAnErrorCode() {
        String url = oauth.authorizeUrl("google", null, null, request);
        assertThat(oauth.callback("google", Map.of("error", "access_denied", "state", stateOf(url)), request))
                .isEqualTo("/#/login?error=denied");
        String url2 = oauth.authorizeUrl("google", null, null, request);
        assertThat(oauth.callback("google", Map.of("code", "unknown", "state", stateOf(url2)), request))
                .isEqualTo("/#/login?error=failed");
        assertThat(db.jdbc().sql("SELECT COUNT(*) FROM usr_user").query(Long.class).single()).isZero();
    }

    @Test
    void unconfiguredProviderIsHiddenUnlessDevModeThenRunsAMockFlow() {
        assertThat(oauth.available()).extracting(OAuthService.Available::id).containsExactly("google");
        assertThatThrownBy(() -> oauth.authorizeUrl("line", null, null, request)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> oauth.authorizeUrl("nosuch", null, null, request)).isInstanceOf(ApiException.class);

        build(true, "https://ab.example.com/");
        assertThat(oauth.available()).extracting(OAuthService.Available::id, OAuthService.Available::mock)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("google", false),
                        org.assertj.core.groups.Tuple.tuple("line", true));

        String url = oauth.authorizeUrl("line", null, "bob", request);
        assertThat(url).startsWith("https://ab.example.com/api/auth/oauth/line/callback?");
        String location = oauth.callback("line", Map.of("code", param(url, "code"), "state", stateOf(url)), request);
        var login = oauth.exchangeTicket(location.substring(location.indexOf("ticket=") + 7), AuthFlowTest.TOKYO,
                ClientInfo.none());
        assertThat(login.user().nickname()).isEqualTo("bob");
    }

    @Test
    void mockCodesAreRefusedOutsideDevMode() {
        build(true, null);
        String url = oauth.authorizeUrl("line", null, "bob", request);
        build(false, null);
        // state row survives the rebuild; with dev mode off the provider is not even enabled
        assertThatThrownBy(() -> oauth.callback("line", Map.of("code", "mock-bob", "state", stateOf(url)), request))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void bindingAddsALoginMethodToTheSignedInUserAndRefusesIdentitiesOwnedByOthers() {
        profiles.put("a", new OAuthProfile("sub-a", null, "Alice", null, null, null));
        profiles.put("b", new OAuthProfile("sub-b", null, "Bob", null, null, null));
        long alice = accounts.resolveOrRegister("PHONE", new OAuthProfile("0901", null, null, null, null, null), "0901");
        long bob = accounts.resolveOrRegister("GOOGLE", profiles.get("b"), null);

        String url = oauth.authorizeUrl("google", alice, null, request);
        assertThat(oauth.callback("google", Map.of("code", "a", "state", stateOf(url)), request))
                .isEqualTo("/#/profile?bound=google");
        assertThat(repo.accountsOf(alice)).extracting(Views.Account::provider).containsExactly("PHONE", "GOOGLE");

        String url2 = oauth.authorizeUrl("google", alice, null, request);
        assertThat(oauth.callback("google", Map.of("code", "b", "state", stateOf(url2)), request))
                .isEqualTo("/#/profile?error=bound_other");
        assertThat(repo.accountsOf(bob)).hasSize(1);
        assertThat(repo.accountsOf(alice)).hasSize(2);
    }

    @Test
    void profileUpdateValidatesEveryField() {
        var users = new UserService(repo, e -> { });
        long id = accounts.resolveOrRegister("PHONE", new OAuthProfile("0901", null, null, null, null, null), "0901");

        var saved = users.update(id, new UserService.ProfileForm(" Tom ", "male", "Shibuya", "1990-05-01", "jp"));
        assertThat(saved.nickname()).isEqualTo("Tom");
        assertThat(saved.gender()).isEqualTo("MALE");
        assertThat(saved.country()).isEqualTo("JP");
        assertThat(saved.birthDate()).isEqualTo("1990-05-01");

        var forms = List.of(
                new UserService.ProfileForm("  ", null, null, null, null),
                new UserService.ProfileForm("x".repeat(31), null, null, null, null),
                new UserService.ProfileForm("T", "robot", null, null, null),
                new UserService.ProfileForm("T", null, "a".repeat(201), null, null),
                new UserService.ProfileForm("T", null, null, "2999-01-01", null),
                new UserService.ProfileForm("T", null, null, "not-a-date", null),
                new UserService.ProfileForm("T", null, null, null, "ZZ"));
        for (var f : forms) {
            assertThatThrownBy(() -> users.update(id, f)).isInstanceOf(ApiException.class);
        }
        assertThat(users.profile(id).nickname()).isEqualTo("Tom");

        var cleared = users.update(id, new UserService.ProfileForm("Tom", "", "", "", ""));
        assertThat(cleared.gender()).isEqualTo("UNKNOWN");
        assertThat(cleared.address()).isNull();
        assertThat(cleared.country()).isNull();
    }
}
