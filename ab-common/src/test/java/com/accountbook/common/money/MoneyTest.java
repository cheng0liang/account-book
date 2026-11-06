package com.accountbook.common.money;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void jpyHasNoFraction() {
        assertThat(Money.toMinor(new BigDecimal("1280"), "JPY")).isEqualTo(1280);
        assertThat(Money.fromMinor(1280, "JPY")).isEqualByComparingTo("1280");
    }

    @Test
    void twoDigitCurrencyUsesCents() {
        assertThat(Money.toMinor(new BigDecimal("12.5"), "CNY")).isEqualTo(1250);
        assertThat(Money.fromMinor(1250, "USD")).isEqualByComparingTo("12.50");
    }

    @Test
    void averageKeepsTwoDecimalsForJpy() {
        assertThat(Money.fromMinorAvg(198.3333, "JPY")).isEqualByComparingTo("198.33");
    }

    @Test
    void rejectsUnknownCurrency() {
        assertThat(Money.isValidCurrency("XXX1")).isFalse();
        assertThat(Money.isValidCurrency(null)).isFalse();
        assertThat(Money.isValidCurrency("JPY")).isTrue();
    }

    @Test
    void rejectsPseudoCurrencies() {
        assertThat(Money.isValidCurrency("XXX")).isFalse();
        assertThat(Money.isValidCurrency("XAU")).isFalse();
    }
}
