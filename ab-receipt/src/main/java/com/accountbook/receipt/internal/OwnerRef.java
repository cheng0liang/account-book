package com.accountbook.receipt.internal;

/** Who a receipt belongs to; {@code mine} is relative to the caller. Response-only. */
record OwnerRef(long id, String nickname, String avatarUrl, boolean mine) {
}
