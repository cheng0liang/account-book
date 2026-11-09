package com.accountbook.user.internal;

import com.accountbook.common.web.ApiException;
import com.accountbook.user.api.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Resolves the bearer token on every API request and rejects anonymous calls except to the public paths.
 * Identity is exposed to controllers through {@link CurrentUser}.
 */
@Component
class AuthInterceptor implements HandlerInterceptor {

    private final SessionService sessions;

    AuthInterceptor(SessionService sessions) {
        this.sessions = sessions;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        sessions.authenticate(bearer(request))
                .ifPresent(id -> request.setAttribute(CurrentUser.ATTRIBUTE, id));
        if (isPublic(request.getRequestURI()) || "OPTIONS".equals(request.getMethod())
                || CurrentUser.from(request).isPresent()) {
            return true;
        }
        throw ApiException.unauthorized("auth.required");
    }

    static boolean isPublic(String path) {
        return path.startsWith("/api/auth/") || path.equals("/api/meta");
    }

    static String bearer(HttpServletRequest request) {
        String h = request.getHeader(HttpHeaders.AUTHORIZATION);
        return h != null && h.regionMatches(true, 0, "Bearer ", 0, 7) ? h.substring(7).strip() : null;
    }
}
