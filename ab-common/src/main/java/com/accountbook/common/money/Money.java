package com.accountbook.common.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

/** Amounts are stored as integers in the currency's minor unit and exposed as decimals in major units. */
public final class Money {

    private static final int MIN_AVG_SCALE = 2;

    private Money() {
    }

    public static int digits(String currency) {
        return Math.max(Currency.getInstance(currency).getDefaultFractionDigits(), 0);
    }

    public static boolean isValidCurrency(String currency) {
        try {
            // pseudo-currencies (XXX, XAU, XTS ...) report -1 fraction digits and are not spendable money
            return Currency.getInstance(currency).getDefaultFractionDigits() >= 0;
        } catch (IllegalArgumentException | NullPointerException e) {
            return false;
        }
    }

    public static long toMinor(BigDecimal amount, String currency) {
        int digits = digits(currency);
        return amount.setScale(digits, RoundingMode.HALF_UP).movePointRight(digits).longValueExact();
    }

    public static BigDecimal fromMinor(long minor, String currency) {
        return BigDecimal.valueOf(minor, digits(currency));
    }

    /** For averages: keeps at least two decimals so cheap per-unit prices stay comparable. */
    public static BigDecimal fromMinorAvg(double minor, String currency) {
        int digits = digits(currency);
        return BigDecimal.valueOf(minor).movePointLeft(digits)
                .setScale(Math.max(digits, MIN_AVG_SCALE), RoundingMode.HALF_UP);
    }
}
