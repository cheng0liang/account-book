package com.accountbook.user.internal.oauth;

/** The identity a provider vouches for. Provider tokens are used once to read this and are never stored. */
public record OAuthProfile(String subject, String unionId, String nickname, String email, String avatarUrl,
                           String rawJson) {
}
