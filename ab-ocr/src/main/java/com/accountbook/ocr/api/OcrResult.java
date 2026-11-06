package com.accountbook.ocr.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Raw recognition output. Fields are best-effort and unvalidated; the receipt module turns it into an editable draft. */
public record OcrResult(
        String provider,
        List<String> warnings,
        String merchantName,
        String country,
        String city,
        String district,
        String address,
        LocalDateTime purchasedAt,
        String currency,
        BigDecimal totalAmount,
        BigDecimal taxAmount,
        BigDecimal discountAmount,
        String paymentMethod,
        List<OcrItem> items) {

    public static OcrResult empty(String provider, List<String> warnings) {
        return new OcrResult(provider, warnings, null, null, null, null, null, null, null, null, null, null, null,
                List.of());
    }
}
