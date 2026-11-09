package com.accountbook.user.api;

/** What other modules may know about a user: enough to label data with its owner. */
public record UserView(long id, String nickname, String avatarUrl) {
}
