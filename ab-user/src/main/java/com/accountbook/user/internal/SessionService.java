package com.accountbook.user.internal;

import com.accountbook.common.web.ApiException;
import java.time.Duration;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Completes a login (location + session) and resolves bearer tokens back to users. */
@Service
class SessionService {

    static final String PREFIX = "ab_";
    private static final long TOUCH_INTERVAL_MS = Duration.ofHours(1).toMillis();
    private static final long CLOCK_SKEW_MS = Duration.ofHours(24).toMillis();
    private static final long STATE_TTL_MS = Duration.ofMinutes(10).toMillis();

    private final UserRepository repo;
    private final AuthProperties props;

    SessionService(UserRepository repo, AuthProperties props) {
        this.repo = repo;
        this.props = props;
    }

    /** Fails fast, before anything single-use (SMS code, login ticket) is consumed. */
    void requireLocationIfConfigured(LocationInput location) {
        if (props.requireLocation() && (location == null || !location.valid())) {
            throw ApiException.invalidField("location", "auth.location.required");
        }
    }

    @Transactional
    public Views.Login start(long userId, String provider, LocationInput location, ClientInfo client) {
        requireLocationIfConfigured(location);
        var user = repo.findUser(userId).orElseThrow(() -> ApiException.unauthorized("auth.required"));
        if (!"ACTIVE".equals(user.status())) {
            throw ApiException.forbidden("auth.user.disabled");
        }
        long now = System.currentTimeMillis();
        repo.purgeExpired(now, now - STATE_TTL_MS);
        if (location != null && location.valid()) {
            long at = location.locatedAt() != null && Math.abs(location.locatedAt() - now) <= CLOCK_SKEW_MS
                    ? location.locatedAt() : now;
            repo.insertLocation(userId, provider, location, at, client, now);
        }
        repo.touchLogin(userId, now);
        String token = PREFIX + Tokens.random(32);
        long expiresAt = now + Duration.ofDays(props.sessionDays()).toMillis();
        repo.insertSession(Tokens.sha256(token), userId, provider, client.userAgent(), now, expiresAt);
        return new Views.Login(token, expiresAt, repo.findUser(userId).orElseThrow().toProfile());
    }

    public Optional<Long> authenticate(String token) {
        if (token == null || !token.startsWith(PREFIX)) {
            return Optional.empty();
        }
        String hash = Tokens.sha256(token);
        long now = System.currentTimeMillis();
        var session = repo.findSession(hash, now);
        session.filter(s -> now - s.lastSeenAt() > TOUCH_INTERVAL_MS).ifPresent(s -> repo.touchSession(hash, now));
        return session.map(UserRepository.SessionRow::userId);
    }

    public void logout(String token) {
        if (token != null) {
            repo.deleteSession(Tokens.sha256(token));
        }
    }
}
