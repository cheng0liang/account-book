package com.accountbook.user.api.event;

/** Published (in the registering transaction) when a user is created; {@code first} marks the very first user. */
public record UserRegisteredEvent(long userId, boolean first) {
}
