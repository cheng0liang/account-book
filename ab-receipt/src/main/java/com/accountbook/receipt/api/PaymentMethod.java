package com.accountbook.receipt.api;

public enum PaymentMethod {
    CASH, CREDIT_CARD, DEBIT_CARD, E_MONEY, QR_PAY, OTHER;

    public static boolean isValid(String value) {
        for (PaymentMethod m : values()) {
            if (m.name().equals(value)) {
                return true;
            }
        }
        return false;
    }
}
