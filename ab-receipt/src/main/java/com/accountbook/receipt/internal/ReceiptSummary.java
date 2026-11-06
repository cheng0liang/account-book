package com.accountbook.receipt.internal;

import java.math.BigDecimal;
import java.util.List;

record ReceiptSummary(
        long id,
        long merchantId,
        String merchantName,
        String purchasedAt,
        String currency,
        BigDecimal totalAmount,
        int itemCount,
        String paymentMethod,
        boolean hasImage,
        List<String> firstItems) {
}
