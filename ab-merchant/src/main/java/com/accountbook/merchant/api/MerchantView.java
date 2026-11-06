package com.accountbook.merchant.api;

public record MerchantView(
        long id,
        String name,
        String country,
        String city,
        String district,
        String type,
        String address,
        Double latitude,
        Double longitude,
        long createdAt) {
}
