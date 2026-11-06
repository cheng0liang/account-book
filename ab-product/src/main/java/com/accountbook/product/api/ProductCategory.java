package com.accountbook.product.api;

public enum ProductCategory {
    FOOD, BEVERAGE, FRESH, DAILY, HOUSEHOLD, MEDICINE, BEAUTY, CLOTHING, ELECTRONICS, OTHER;

    public static boolean isValid(String value) {
        for (ProductCategory c : values()) {
            if (c.name().equals(value)) {
                return true;
            }
        }
        return false;
    }
}
