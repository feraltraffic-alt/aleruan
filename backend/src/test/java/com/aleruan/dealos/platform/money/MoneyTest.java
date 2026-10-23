package com.aleruan.dealos.platform.money;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Currency;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void roundsToCents() {
        assertEquals("10.13 EUR", Money.of("10.125", "EUR").toString());
    }

    @Test
    void addsSameCurrency() {
        assertEquals(Money.of("15.50", "CZK"), Money.of("10", "CZK").plus(Money.of("5.5", "CZK")));
    }

    @Test
    void refusesMixedCurrencies() {
        assertThrows(IllegalArgumentException.class, () -> Money.of("1", "EUR").plus(Money.of("1", "PLN")));
    }

    @Test
    void appliesMarkup() {
        assertEquals(Money.of("6500.00", "EUR"), Money.of("5000", "EUR").withMarkupPercent(new BigDecimal("30")));
    }

    @Test
    void convertsWithExplicitRate() {
        // Invoice 02-01-2025/AL-1: 8 000 EUR at ČNB 25.185 = 201 480 CZK
        assertEquals(Money.of("201480.00", "CZK"),
                Money.of("8000", "EUR").convert(new BigDecimal("25.185"), Currency.getInstance("CZK")));
    }

    @Test
    void detectsNegativeMargin() {
        assertTrue(Money.of("100", "EUR").minus(Money.of("120", "EUR")).isNegative());
    }
}
