package com.accountbook.user.internal;

import com.accountbook.common.web.ApiException;
import com.accountbook.user.internal.oauth.OAuthProfile;
import java.time.Duration;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Phone + SMS login. Deliberately not transactional: a wrong-code attempt must stay counted even though the
 * request fails.
 */
@Service
class AuthService {

    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9]{6,15}$");

    private final UserRepository repo;
    private final AccountService accounts;
    private final SessionService sessions;
    private final SmsSender sender;
    private final AuthProperties props;

    AuthService(UserRepository repo, AccountService accounts, SessionService sessions, SmsSender sender,
                AuthProperties props) {
        this.repo = repo;
        this.accounts = accounts;
        this.sessions = sessions;
        this.sender = sender;
        this.props = props;
    }

    static String normalizePhone(String raw) {
        String p = raw == null ? "" : raw.replaceAll("[\\s\\-()]", "");
        if (!PHONE.matcher(p).matches()) {
            throw ApiException.invalidField("phone", "auth.phone.invalid");
        }
        return p;
    }

    record SmsSent(int resendSeconds, int expiresSeconds, String debugCode) {
    }

    SmsSent sendCode(String rawPhone) {
        String phone = normalizePhone(rawPhone);
        long now = System.currentTimeMillis();
        var sentAt = repo.smsSentAt(phone);
        if (sentAt.isPresent() && now - sentAt.get() < Duration.ofSeconds(props.sms().resendSeconds()).toMillis()) {
            throw ApiException.of(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS", "auth.sms.tooFrequent");
        }
        String code = String.format("%06d", Tokens.randomDigits(1_000_000));
        repo.upsertSms(phone, Tokens.sha256(phone + code), now,
                now + Duration.ofSeconds(props.sms().codeTtlSeconds()).toMillis());
        sender.send(phone, code);
        return new SmsSent(props.sms().resendSeconds(), props.sms().codeTtlSeconds(),
                props.devMode() ? code : null);
    }

    Views.Login loginWithSms(String rawPhone, String code, LocationInput location, ClientInfo client) {
        String phone = normalizePhone(rawPhone);
        sessions.requireLocationIfConfigured(location);
        verify(phone, code == null ? "" : code.strip());
        var profile = new OAuthProfile(phone, null, null, null, null, null);
        long userId = accounts.resolveOrRegister(AccountService.PHONE, profile, phone);
        return sessions.start(userId, AccountService.PHONE, location, client);
    }

    private void verify(String phone, String code) {
        var row = repo.takeSmsAttempt(phone, props.sms().maxAttempts());
        if (row.isEmpty()) {
            if (repo.smsExists(phone)) {
                throw ApiException.of(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS", "auth.sms.tooManyAttempts");
            }
            throw ApiException.badRequest("auth.sms.invalid");
        }
        boolean ok = row.get().expiresAt() > System.currentTimeMillis()
                && Tokens.constantTimeEquals(row.get().codeHash(), Tokens.sha256(phone + code));
        if (!ok) {
            throw ApiException.badRequest("auth.sms.invalid");
        }
        repo.deleteSms(phone);
    }
}
