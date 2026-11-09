package com.accountbook.user.internal;

import com.accountbook.common.web.ApiException;
import com.accountbook.user.internal.oauth.OAuthProfile;
import com.accountbook.user.internal.oauth.OAuthProvider;
import com.accountbook.user.internal.oauth.Urls;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Server-side authorization-code flow for third-party logins, plus the one-time ticket hand-off to the SPA. */
@Service
class OAuthService {

    private static final Logger log = LoggerFactory.getLogger(OAuthService.class);
    private static final String MOCK_PREFIX = "mock-";
    private static final Duration STATE_TTL = Duration.ofMinutes(10);
    private static final Duration TICKET_TTL = Duration.ofMinutes(2);

    private final Map<String, OAuthProvider> providers = new LinkedHashMap<>();
    private final UserRepository repo;
    private final AccountService accounts;
    private final SessionService sessions;
    private final AuthProperties props;

    OAuthService(List<OAuthProvider> all, UserRepository repo, AccountService accounts, SessionService sessions,
                 AuthProperties props) {
        all.stream().sorted(java.util.Comparator.comparingInt(p -> order(p.id())))
                .forEach(p -> providers.put(p.id(), p));
        this.repo = repo;
        this.accounts = accounts;
        this.sessions = sessions;
        this.props = props;
    }

    private static int order(String id) {
        int i = List.of("apple", "google", "wechat", "qq", "line").indexOf(id);
        return i < 0 ? 99 : i;
    }

    record Available(String id, boolean mock) {
    }

    List<Available> available() {
        return providers.values().stream()
                .filter(p -> p.configured() || props.devMode())
                .map(p -> new Available(p.id(), !p.configured())).toList();
    }

    String authorizeUrl(String providerId, Long bindUserId, String mockUser, HttpServletRequest request) {
        OAuthProvider p = enabled(providerId);
        String state = Tokens.random(24);
        repo.insertState(state, p.id(), bindUserId, System.currentTimeMillis());
        String redirect = redirectUri(p, request);
        if (!p.configured()) {
            String who = mockUser == null || mockUser.isBlank() ? "demo" : mockUser.strip();
            return Urls.build(redirect, Map.of("code", MOCK_PREFIX + who, "state", state));
        }
        return p.authorizeUrl(state, redirect);
    }

    /** @return the SPA location to redirect the browser to */
    String callback(String providerId, Map<String, String> params, HttpServletRequest request) {
        OAuthProvider p = enabled(providerId);
        var state = params.get("state") == null ? java.util.Optional.<UserRepository.StateRow>empty()
                : repo.consumeState(params.get("state"));
        if (state.isEmpty() || !state.get().provider().equals(p.id())
                || System.currentTimeMillis() - state.get().createdAt() > STATE_TTL.toMillis()) {
            return "/#/login?error=state";
        }
        Long bindUserId = state.get().bindUserId();
        String page = bindUserId == null ? "/#/login" : "/#/profile";
        String code = params.get("code");
        if (params.get("error") != null || code == null || code.isBlank()) {
            return page + "?error=denied";
        }
        try {
            OAuthProfile profile = !p.configured() ? mockProfile(code) : p.exchange(code, redirectUri(p, request), params);
            String provider = p.id().toUpperCase(Locale.ROOT);
            if (bindUserId != null) {
                accounts.bind(bindUserId, provider, profile);
                return page + "?bound=" + p.id();
            }
            long userId = accounts.resolveOrRegister(provider, profile, null);
            String ticket = Tokens.random(32);
            repo.insertTicket(Tokens.sha256(ticket), userId, provider,
                    System.currentTimeMillis() + TICKET_TTL.toMillis());
            return page + "?ticket=" + ticket;
        } catch (ApiException e) {
            log.warn("OAuth {} callback failed: {}", p.id(), e.messageKey());
            return page + "?error=" + ("auth.account.boundToOther".equals(e.messageKey()) ? "bound_other" : "failed");
        }
    }

    Views.Login exchangeTicket(String ticket, LocationInput location, ClientInfo client) {
        sessions.requireLocationIfConfigured(location);
        var row = ticket == null ? java.util.Optional.<UserRepository.TicketRow>empty()
                : repo.consumeTicket(Tokens.sha256(ticket));
        if (row.isEmpty() || row.get().expiresAt() < System.currentTimeMillis()) {
            throw ApiException.badRequest("auth.ticket.invalid");
        }
        return sessions.start(row.get().userId(), row.get().provider(), location, client);
    }

    private OAuthProvider enabled(String id) {
        OAuthProvider p = providers.get(id == null ? "" : id.toLowerCase(Locale.ROOT));
        if (p == null || (!p.configured() && !props.devMode())) {
            throw ApiException.notFound("auth.provider.disabled", id);
        }
        return p;
    }

    private OAuthProfile mockProfile(String code) {
        if (!props.devMode() || !code.startsWith(MOCK_PREFIX)) {
            throw ApiException.badRequest("auth.oauth.invalid");
        }
        String who = code.substring(MOCK_PREFIX.length());
        return new OAuthProfile("mock:" + who, null, who, null, null, null);
    }

    private String redirectUri(OAuthProvider p, HttpServletRequest request) {
        String base = props.publicBaseUrl();
        if (base == null || base.isBlank()) {
            int port = request.getServerPort();
            boolean defaultPort = port == 80 && "http".equals(request.getScheme())
                    || port == 443 && "https".equals(request.getScheme());
            base = request.getScheme() + "://" + request.getServerName() + (defaultPort ? "" : ":" + port);
        }
        return base.replaceAll("/+$", "") + "/api/auth/oauth/" + p.id() + "/callback";
    }
}
