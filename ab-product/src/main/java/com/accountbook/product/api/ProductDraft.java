package com.accountbook.product.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Input for creating/updating a product; also used by other modules to find-or-create one. */
public record ProductDraft(
        @NotBlank(message = "{validation.required}") @Size(max = 200, message = "{validation.size}") String name,
        @Size(max = 100, message = "{validation.size}") String brand,
        String category,
        @Size(max = 50, message = "{validation.size}") String specText,
        Double specValue,
        String specUnit,
        @Size(max = 32, message = "{validation.size}") String barcode,
        List<@Size(max = 30, message = "{validation.size}") String> tags) {
}
