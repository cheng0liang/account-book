package com.accountbook.analytics.internal;

import com.accountbook.common.web.ApiException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Parsed, validated request filter. Its toString is the cache key, so only known parameters ever reach it. */
record Filter(String from, String to, String currency, String country, String city, String district,
              String merchantType, Long merchantId, Long productId, String brand, String category, String tag) {

    static final Set<String> GRANULARITY = Set.of("day", "week", "month");
    static final Set<String> METRIC = Set.of("avg", "min", "max");
    static final Set<String> COMPARE = Set.of("merchant", "city", "district", "country", "merchantType", "brand", "product");
    static final Set<String> SPENDING = Set.of("day", "month", "merchant", "merchantType", "category", "brand", "city", "district");

    static Filter parse(Map<String, String> p, String defaultCurrency) {
        String currency = text(p, "currency");
        if (currency == null) {
            currency = defaultCurrency;
        }
        String country = text(p, "country");
        return new Filter(date(p, "from"), date(p, "to"), currency.toUpperCase(Locale.ROOT),
                country == null ? null : country.toUpperCase(Locale.ROOT), text(p, "city"), text(p, "district"),
                text(p, "merchantType"), number(p, "merchantId"), number(p, "productId"), text(p, "brand"),
                text(p, "category"), text(p, "tag"));
    }

    Filter withProduct(long id) {
        return new Filter(from, to, currency, country, city, district, merchantType, merchantId, id, brand, category, tag);
    }

    boolean hasProductScope() {
        return productId != null || brand != null || category != null || tag != null;
    }

    static String text(Map<String, String> p, String key) {
        String v = p.get(key);
        return v == null || v.isBlank() ? null : v.strip();
    }

    static Long number(Map<String, String> p, String key) {
        String v = text(p, key);
        if (v == null) {
            return null;
        }
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            throw ApiException.invalidField(key, "validation.range");
        }
    }

    static List<Long> numbers(Map<String, String> p, String key, int max) {
        String v = text(p, key);
        List<Long> out = new ArrayList<>();
        if (v != null) {
            for (String part : v.split(",")) {
                if (!part.isBlank()) {
                    try {
                        out.add(Long.parseLong(part.strip()));
                    } catch (NumberFormatException e) {
                        throw ApiException.invalidField(key, "validation.range");
                    }
                }
            }
        }
        if (out.size() > max) {
            throw ApiException.invalidField(key, "validation.range");
        }
        return out;
    }

    static String choice(Map<String, String> p, String key, Set<String> allowed, String fallback) {
        String v = text(p, key);
        if (v == null) {
            return fallback;
        }
        if (!allowed.contains(v)) {
            throw ApiException.invalidField(key, "validation.range");
        }
        return v;
    }

    private static String date(Map<String, String> p, String key) {
        String v = text(p, key);
        if (v == null) {
            return null;
        }
        try {
            return LocalDate.parse(v).toString();
        } catch (DateTimeParseException e) {
            throw ApiException.invalidField(key, "validation.range");
        }
    }
}
