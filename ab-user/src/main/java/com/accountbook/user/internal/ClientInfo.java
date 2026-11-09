package com.accountbook.user.internal;

import jakarta.servlet.http.HttpServletRequest;

record ClientInfo(String ip, String userAgent) {

    static ClientInfo of(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        return new ClientInfo(request.getRemoteAddr(), ua == null ? null : ua.substring(0, Math.min(ua.length(), 300)));
    }

    static ClientInfo none() {
        return new ClientInfo(null, null);
    }
}
