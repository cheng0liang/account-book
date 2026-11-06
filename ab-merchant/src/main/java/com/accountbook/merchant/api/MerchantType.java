package com.accountbook.merchant.api;

public enum MerchantType {
    SUPERMARKET, CONVENIENCE, RESTAURANT, CAFE, DRUGSTORE, DEPARTMENT, ELECTRONICS, CLOTHING, ONLINE, GAS, OTHER;

    public static boolean isValid(String value) {
        for (MerchantType t : values()) {
            if (t.name().equals(value)) {
                return true;
            }
        }
        return false;
    }
}
