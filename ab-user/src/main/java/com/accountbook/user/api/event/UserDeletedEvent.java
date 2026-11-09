package com.accountbook.user.api.event;

/**
 * Published synchronously, inside the account-deletion transaction, before the user row is removed. Listeners erase
 * the user's data in their own module; a listener that throws vetoes the deletion and rolls everything back.
 */
public record UserDeletedEvent(long userId) {
}
