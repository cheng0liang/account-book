package com.accountbook.product.api;

import java.util.List;

/** {@code specDim} is WEIGHT/VOLUME/COUNT and {@code specBaseQty} the spec converted to g/ml/piece (both null if no numeric spec). */
public record ProductView(
        long id,
        String name,
        String brand,
        String category,
        String specText,
        Double specValue,
        String specUnit,
        String specDim,
        Double specBaseQty,
        String barcode,
        List<String> tags,
        long createdAt) {
}
