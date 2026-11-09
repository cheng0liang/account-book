package com.accountbook.user.internal.oauth;

import java.util.Map;

/** The only outbound HTTP the login flows need; tests replace it with a fake. */
public interface HttpGateway {

    String get(String url, Map<String, String> headers);

    String postForm(String url, Map<String, String> form);
}
