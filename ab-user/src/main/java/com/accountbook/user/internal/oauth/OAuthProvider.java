package com.accountbook.user.internal.oauth;

import java.util.Map;

public interface OAuthProvider {

    /** Lower-case id used in URLs, e.g. {@code apple}. */
    String id();

    boolean configured();

    String authorizeUrl(String state, String redirectUri);

    /** @param extra additional callback parameters (Apple posts the user's name only on first authorization) */
    OAuthProfile exchange(String code, String redirectUri, Map<String, String> extra);
}
