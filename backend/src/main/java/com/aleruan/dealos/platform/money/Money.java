package com.aleruan.dealos.platform.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * Amount + currency, always rounded to 2 decimals (CZK, PLN, EUR all use cents).
 * Arithmetic between different currencies is refused: convert first with an explicit rate.
 */
public record Money(BigDecimal amount, Currency currency) implements Comparable<Money> {

    public static final int SCALE = 2;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        amount = amount.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static Money of(String amount, String currencyCode) {
        return new Money(new BigDecimal(amount), Currency.getInstance(currencyCode));
    }

    public static Money zero(String currencyCode) {
        return of("0", currencyCode);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    public Money times(BigDecimal factor) {
        return new Money(amount.multiply(factor), currency);
    }

    /** Price from cost with a markup in percent: cost × (1 + markup/100). */
    public Money withMarkupPercent(BigDecimal markupPercent) {
        return times(BigDecimal.ONE.add(markupPercent.divide(HUNDRED, 10, RoundingMode.HALF_UP)));
    }

    /** Converts with "1 unit of this currency = rate units of target". */
    public Money convert(BigDecimal rate, Currency target) {
        return new Money(amount.multiply(rate), target);
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount);
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "Currency mismatch: " + currency.getCurrencyCode() + " vs " + other.currency.getCurrencyCode());
        }
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency.getCurrencyCode();
    }
}
