package com.accountbook.merchant.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Input for creating/updating a merchant; also used by other modules to find-or-create one. */
public record MerchantDraft(
        @NotBlank(message = "{validation.required}") @Size(max = 100, message = "{validation.size}") String name,
        @Pattern(regexp = "^([A-Za-z]{2})?$", message = "{validation.range}") String country,
        @Size(max = 100, message = "{validation.size}") String city,
        @Size(max = 100, message = "{validation.size}") String district,
        String type,
        @Size(max = 300, message = "{validation.size}") String address,
        @DecimalMin(value = "-90", message = "{validation.range}") @DecimalMax(value = "90", message = "{validation.range}") Double latitude,
        @DecimalMin(value = "-180", message = "{validation.range}") @DecimalMax(value = "180", message = "{validation.range}") Double longitude) {
}
