package com.accountbook.user.internal;

import com.accountbook.common.web.ApiException;
import com.accountbook.user.api.event.UserRegisteredEvent;
import com.accountbook.user.internal.oauth.OAuthProfile;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Maps a verified external identity to a user, registering one on first sight. */
@Service
class AccountService {

    static final String PHONE = "PHONE";
    private static final int NICKNAME_MAX = 30;

    private final UserRepository repo;
    private final ApplicationEventPublisher events;

    AccountService(UserRepository repo, ApplicationEventPublisher events) {
        this.repo = repo;
        this.events = events;
    }

    /** @param phone set only for PHONE logins; stored on the new user's profile */
    @Transactional
    public long resolveOrRegister(String provider, OAuthProfile profile, String phone) {
        long now = System.currentTimeMillis();
        var existing = repo.findAccount(provider, profile.subject());
        if (existing.isPresent()) {
            repo.refreshAccount(provider, profile, now);
            return existing.get().userId();
        }
        boolean first = repo.countUsers() == 0;
        long id = repo.insertUser("User", phone, profile.avatarUrl(), now);
        repo.updateNickname(id, defaultNickname(id, profile, phone), now);
        repo.insertAccount(id, provider, profile, now);
        events.publishEvent(new UserRegisteredEvent(id, first));
        return id;
    }

    /** Adds a login method to an existing user; the identity must not belong to anyone else. */
    @Transactional
    public void bind(long userId, String provider, OAuthProfile profile) {
        var existing = repo.findAccount(provider, profile.subject());
        if (existing.isPresent()) {
            if (existing.get().userId() != userId) {
                throw ApiException.conflict("auth.account.boundToOther");
            }
            repo.refreshAccount(provider, profile, System.currentTimeMillis());
            return;
        }
        repo.insertAccount(userId, provider, profile, System.currentTimeMillis());
    }

    private static String defaultNickname(long id, OAuthProfile profile, String phone) {
        String name = profile.nickname();
        if (name != null && !name.isBlank()) {
            return name.strip().substring(0, Math.min(name.strip().length(), NICKNAME_MAX));
        }
        if (phone != null && phone.length() >= 4) {
            return "User" + phone.substring(phone.length() - 4);
        }
        return "User" + id;
    }
}
