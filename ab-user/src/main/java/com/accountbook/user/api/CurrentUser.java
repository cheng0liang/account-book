package com.accountbook.user.api;

import com.accountbook.common.web.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** The authenticated user of the current HTTP request, set by the auth interceptor. */
public final class CurrentUser {

    public static final String ATTRIBUTE = "ab.currentUserId";

    private CurrentUser() {
    }

    public static Optional<Long> find() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            return from(sra.getRequest());
        }
        return Optional.empty();
    }

    public static Optional<Long> from(HttpServletRequest request) {
        return request.getAttribute(ATTRIBUTE) instanceof Long id ? Optional.of(id) : Optional.empty();
    }

    public static long id() {
        return find().orElseThrow(() -> ApiException.unauthorized("auth.required"));
    }
}
