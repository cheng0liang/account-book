package com.accountbook.user.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.accountbook.common.testing.TestDb;
import com.accountbook.common.web.ApiException;
import com.accountbook.user.api.event.UserRegisteredEvent;
import com.accountbook.user.internal.oauth.OAuthProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class AuthFlowTest {

    static final LocationInput TOKYO = new LocationInput(35.68, 139.76, 20.0, System.currentTimeMillis());

    private TestDb db;
    private UserRepository repo;
    private AuthService auth;
    private SessionService sessions;
    private final List<Object> events = new ArrayList<>();
    private final Map<String, String> sentCodes = new java.util.HashMap<>();

    @BeforeEach
    void setUp() {
        db = TestDb.create("user");
        repo = new UserRepository(db.jdbc());
        var props = new AuthProperties(true, true, 30, null, new AuthProperties.Sms(300, 60, 3, ""), Map.of());
        var accounts = new AccountService(repo, events::add);
        sessions = new SessionService(repo, props);
        auth = new AuthService(repo, accounts, sessions, sentCodes::put, props);
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    private long count(String sql) {
        return db.jdbc().sql(sql).query(Long.class).single();
    }

    private String sendCode(String phone) {
        return auth.sendCode(phone).debugCode();
    }

    @Test
    void smsLoginRegistersUserRecordsLocationAndIssuesWorkingSession() {
        String code = sendCode("090-1234 5678");
        assertThat(sentCodes).containsKey("09012345678");

        var login = auth.loginWithSms("09012345678", code, TOKYO, new ClientInfo("1.2.3.4", "UA"));

        assertThat(login.token()).startsWith("ab_");
        assertThat(login.user().phone()).isEqualTo("09012345678");
        assertThat(login.user().nickname()).isEqualTo("User5678");
        assertThat(count("SELECT COUNT(*) FROM usr_auth_account WHERE provider = 'PHONE'")).isEqualTo(1);
        var loc = db.jdbc().sql("SELECT * FROM usr_login_location").query().singleRow();
        assertThat(loc.get("provider")).isEqualTo("PHONE");
        assertThat(((Number) loc.get("latitude")).doubleValue()).isEqualTo(35.68);
        assertThat(loc.get("ip")).isEqualTo("1.2.3.4");
        assertThat(((Number) loc.get("located_at")).longValue()).isEqualTo(TOKYO.locatedAt());
        assertThat(events).containsExactly(new UserRegisteredEvent(login.user().id(), true));

        assertThat(sessions.authenticate(login.token())).contains(login.user().id());
        assertThat(db.jdbc().sql("SELECT COUNT(*) FROM usr_session WHERE token_hash = :t").param("t", login.token())
                .query(Long.class).single()).as("only the hash is stored").isZero();

        sessions.logout(login.token());
        assertThat(sessions.authenticate(login.token())).isEmpty();
    }

    @Test
    void secondLoginReusesTheUserAndAddsAnotherLocationRow() {
        long id = auth.loginWithSms("09011112222", sendCode("09011112222"), TOKYO, ClientInfo.none()).user().id();
        db.jdbc().sql("UPDATE usr_sms_code SET sent_at = 0").update();
        long again = auth.loginWithSms("09011112222", sendCode("09011112222"), TOKYO, ClientInfo.none()).user().id();

        assertThat(again).isEqualTo(id);
        assertThat(count("SELECT COUNT(*) FROM usr_user")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM usr_login_location")).isEqualTo(2);
        assertThat(events).hasSize(1);

        auth.loginWithSms("09033334444", sendCode("09033334444"), TOKYO, ClientInfo.none());
        assertThat(events.get(1)).isEqualTo(new UserRegisteredEvent(id + 1, false));
    }

    @Test
    void codeCannotBeRequestedAgainWithinTheResendWindow() {
        sendCode("09011112222");
        assertThatThrownBy(() -> sendCode("09011112222"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status().value()).isEqualTo(429));
    }

    @Test
    void wrongCodesAreCountedAndEventuallyLockTheCodeEvenWhenTheRightOneFollows() {
        String code = sendCode("09011112222");
        String wrong = code.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> auth.loginWithSms("09011112222", wrong, TOKYO, ClientInfo.none()))
                    .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status().value()).isEqualTo(400));
        }
        assertThatThrownBy(() -> auth.loginWithSms("09011112222", code, TOKYO, ClientInfo.none()))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status().value()).isEqualTo(429));
        assertThat(count("SELECT COUNT(*) FROM usr_user")).isZero();
    }

    @Test
    void codeIsSingleUseAndExpires() {
        String code = sendCode("09011112222");
        auth.loginWithSms("09011112222", code, TOKYO, ClientInfo.none());
        assertThatThrownBy(() -> auth.loginWithSms("09011112222", code, TOKYO, ClientInfo.none()))
                .isInstanceOf(ApiException.class);

        db.jdbc().sql("UPDATE usr_sms_code SET sent_at = 0").update();
        String second = sendCode("09011112222");
        db.jdbc().sql("UPDATE usr_sms_code SET expires_at = 1").update();
        assertThatThrownBy(() -> auth.loginWithSms("09011112222", second, TOKYO, ClientInfo.none()))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void missingOrOutOfRangeLocationIsRejectedWithoutBurningTheCode() {
        String code = sendCode("09011112222");
        assertThatThrownBy(() -> auth.loginWithSms("09011112222", code, null, ClientInfo.none()))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.details()).extracting("field").containsExactly("location"));
        assertThatThrownBy(() -> auth.loginWithSms("09011112222", code, new LocationInput(91.0, 0.0, null, null),
                ClientInfo.none())).isInstanceOf(ApiException.class);

        assertThat(auth.loginWithSms("09011112222", code, TOKYO, ClientInfo.none()).token()).isNotBlank();
    }

    @Test
    void staleClientTimestampFallsBackToServerTime() {
        long before = System.currentTimeMillis();
        auth.loginWithSms("09011112222", sendCode("09011112222"),
                new LocationInput(1.0, 2.0, null, 12345L), ClientInfo.none());
        long at = db.jdbc().sql("SELECT located_at FROM usr_login_location").query(Long.class).single();
        assertThat(at).isGreaterThanOrEqualTo(before);
    }

    @Test
    void invalidPhoneNumbersAreRejected() {
        for (String bad : new String[] {"", "abc", "12345", "+1234567890123456", null}) {
            assertThatThrownBy(() -> auth.sendCode(bad)).isInstanceOf(ApiException.class);
        }
    }

    @Test
    void disabledUsersCannotLogInOrUseOldSessions() {
        var login = auth.loginWithSms("09011112222", sendCode("09011112222"), TOKYO, ClientInfo.none());
        db.jdbc().sql("UPDATE usr_user SET status = 'DISABLED'").update();

        assertThat(sessions.authenticate(login.token())).isEmpty();
        assertThatThrownBy(() -> sessions.start(login.user().id(), "PHONE", TOKYO, ClientInfo.none()))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status().value()).isEqualTo(403));
    }

    @Test
    void expiredSessionsDoNotAuthenticate() {
        var login = auth.loginWithSms("09011112222", sendCode("09011112222"), TOKYO, ClientInfo.none());
        db.jdbc().sql("UPDATE usr_session SET expires_at = 1").update();
        assertThat(sessions.authenticate(login.token())).isEmpty();
        assertThat(sessions.authenticate("garbage")).isEmpty();
        assertThat(sessions.authenticate(null)).isEmpty();
    }

    @Test
    void userCreationRollsBackWhenARegistrationListenerFails() {
        var tx = new TransactionTemplate(new DataSourceTransactionManager(db.dataSource()));
        var accounts = new AccountService(repo, e -> {
            throw new IllegalStateException("listener failed");
        });
        assertThatThrownBy(() -> tx.executeWithoutResult(s ->
                accounts.resolveOrRegister("GOOGLE", new OAuthProfile("g1", null, "G", null, null, null), null)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(count("SELECT COUNT(*) FROM usr_user")).isZero();
        assertThat(count("SELECT COUNT(*) FROM usr_auth_account")).isZero();
    }

    private AuthService withFixedCode(boolean requireLocation) {
        var props = new AuthProperties(false, requireLocation, 30, null,
                new AuthProperties.Sms(300, 60, 3, "000000"), Map.of());
        return new AuthService(repo, new AccountService(repo, events::add), new SessionService(repo, props),
                sentCodes::put, props);
    }

    @Test
    void fixedCodeLogsInAnyPhoneWithoutRequestingACodeAndSendsNoSms() {
        var fixed = withFixedCode(false);
        var login = fixed.loginWithSms("+8613800138000", "000000", null, ClientInfo.none());
        assertThat(login.token()).startsWith("ab_");
        assertThat(sentCodes).isEmpty();
        assertThat(fixed.sendCode("+8613800138001").debugCode()).isNull();
        assertThat(sentCodes).isEmpty();
        assertThatThrownBy(() -> fixed.loginWithSms("+8613800138000", "123456", null, ClientInfo.none()))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void loginWithoutLocationSucceedsWhenLocationIsOptionalAndRecordsNoRow() {
        withFixedCode(false).loginWithSms("+8613800138000", "000000", null, ClientInfo.none());
        assertThat(count("SELECT COUNT(*) FROM usr_login_location")).isZero();
        withFixedCode(false).loginWithSms("+8613800138000", "000000", TOKYO, ClientInfo.none());
        assertThat(count("SELECT COUNT(*) FROM usr_login_location")).isEqualTo(1);
    }

    @Test
    void locationStillBlocksLoginWhenConfiguredAsRequired() {
        assertThatThrownBy(() -> withFixedCode(true).loginWithSms("+8613800138000", "000000", null, ClientInfo.none()))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void deletingTheAccountErasesTheUserSessionsLoginsAndNotifiesOtherModules() {
        var login = auth.loginWithSms("09011112222", sendCode("09011112222"), TOKYO, ClientInfo.none());
        auth.loginWithSms("09033334444", sendCode("09033334444"), TOKYO, ClientInfo.none());

        new UserService(repo, events::add).deleteAccount(login.user().id());

        assertThat(events).anyMatch(e -> e instanceof com.accountbook.user.api.event.UserDeletedEvent d
                && d.userId() == login.user().id());
        assertThat(sessions.authenticate(login.token())).isEmpty();
        assertThat(repo.findUser(login.user().id())).isEmpty();
        assertThat(count("SELECT COUNT(*) FROM usr_user")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM usr_auth_account")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM usr_login_location")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM usr_session")).isEqualTo(1);
        // the number can be registered again as a brand-new user
        var again = auth.loginWithSms("09011112222", sendCode("09011112222"), TOKYO, ClientInfo.none());
        assertThat(again.user().id()).isNotEqualTo(login.user().id());
    }
}
