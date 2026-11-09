package com.accountbook.receipt.api;

import java.util.List;

/** Read-optimised view of a saved receipt: exactly what downstream modules (analytics) need, no presentation fields. */
public record ReceiptFact(
        long id,
        long userId,
        long merchantId,
        String purchasedAt,
        String purchasedDate,
        String currency,
        long totalMinor,
        long discountMinor,
        List<ReceiptItemFact> items) {
}
