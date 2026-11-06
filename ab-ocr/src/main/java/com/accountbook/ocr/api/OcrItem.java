package com.accountbook.ocr.api;

import java.math.BigDecimal;

/** One recognised line; amounts are decimals in the receipt currency's major unit. Everything but {@code name} may be null. */
public record OcrItem(
        String name,
        String brand,
        String specText,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal discount,
        BigDecimal amount,
        String category) {
}
