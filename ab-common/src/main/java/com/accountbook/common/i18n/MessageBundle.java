package com.accountbook.common.i18n;

/** A module registers its own message bundle (e.g. {@code i18n/merchant}) by exposing this as a bean. */
public record MessageBundle(String basename) {
}
