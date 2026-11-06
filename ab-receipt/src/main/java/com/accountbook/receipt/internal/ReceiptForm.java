package com.accountbook.receipt.internal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/** Request body for create/update and response body for detail/draft (see contracts/api.md §3.2). */
record ReceiptForm(
        Long id,
        @Valid MerchantRef merchant,
        String purchasedAt,
        String currency,
        BigDecimal totalAmount,
        BigDecimal taxAmount,
        BigDecimal discountAmount,
        String paymentMethod,
        @Size(max = 500, message = "{validation.size}") String note,
        String source,
        String imageToken,
        String imageUrl,
        @Valid List<ItemForm> items) {

    record MerchantRef(
            Long id,
            @Size(max = 100, message = "{validation.size}") String name,
            String country,
            @Size(max = 100, message = "{validation.size}") String city,
            @Size(max = 100, message = "{validation.size}") String district,
            String type,
            @Size(max = 300, message = "{validation.size}") String address,
            Double latitude,
            Double longitude) {
    }

    record ProductRef(
            Long id,
            @Size(max = 200, message = "{validation.size}") String name,
            @Size(max = 100, message = "{validation.size}") String brand,
            String category,
            @Size(max = 50, message = "{validation.size}") String specText,
            Double specValue,
            String specUnit,
            @Size(max = 32, message = "{validation.size}") String barcode,
            List<@Size(max = 30, message = "{validation.size}") String> tags) {
    }

    record ItemForm(
            Long id,
            @Valid ProductRef product,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal discount,
            BigDecimal amount,
            @Size(max = 200, message = "{validation.size}") String note) {
    }
}
