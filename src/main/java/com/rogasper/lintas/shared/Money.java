package com.rogasper.lintas.shared;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * An amount in minor units (cents, sen) plus its currency.
 *
 * <p>Minor units are deliberate: a remittance engine that stores {@code double} or freely-scaled
 * {@link BigDecimal} loses money in rounding and reconciliation. Everything here is exact.
 *
 * <p>Arithmetic between different currencies is refused. If you need to convert, you need a rate,
 * and a rate means the {@code transfer} module — not this class.
 */
public record Money(long minorUnits, Currency currency) implements Comparable<Money> {

    public Money {
        Objects.requireNonNull(currency, "currency is required");
    }

    public static Money zero(Currency currency) {
        return new Money(0L, currency);
    }

    public static Money ofMinor(long minorUnits, Currency currency) {
        return new Money(minorUnits, currency);
    }

    /**
     * Builds money from a major-unit amount, e.g. {@code ofMajor(new BigDecimal("100.50"), USD)}.
     *
     * @throws IllegalArgumentException when the amount has more decimal places than the currency
     *                                  allows (e.g. 100.50 IDR)
     */
    public static Money ofMajor(BigDecimal amount, Currency currency) {
        Objects.requireNonNull(amount, "amount is required");
        Objects.requireNonNull(currency, "currency is required");
        try {
            long minor = amount.movePointRight(currency.scale())
                    .setScale(0, RoundingMode.UNNECESSARY)
                    .longValueExact();
            return new Money(minor, currency);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException(
                    "%s has more precision than %s allows (%d decimal places)"
                            .formatted(amount, currency, currency.scale()), ex);
        }
    }

    /** Convenience for tests and fixtures: {@code Money.parse("100.50", Currency.USD)}. */
    public static Money parse(String majorAmount, Currency currency) {
        return ofMajor(new BigDecimal(majorAmount), currency);
    }

    public BigDecimal toMajor() {
        return BigDecimal.valueOf(minorUnits, currency.scale());
    }

    public Money plus(Money other) {
        return new Money(Math.addExact(minorUnits, sameCurrency(other).minorUnits), currency);
    }

    public Money minus(Money other) {
        return new Money(Math.subtractExact(minorUnits, sameCurrency(other).minorUnits), currency);
    }

    public Money negate() {
        return new Money(Math.negateExact(minorUnits), currency);
    }

    public boolean isZero() {
        return minorUnits == 0L;
    }

    public boolean isPositive() {
        return minorUnits > 0L;
    }

    public boolean isNegative() {
        return minorUnits < 0L;
    }

    @Override
    public int compareTo(Money other) {
        return Long.compare(minorUnits, sameCurrency(other).minorUnits);
    }

    private Money sameCurrency(Money other) {
        Objects.requireNonNull(other, "other is required");
        if (currency != other.currency) {
            throw new IllegalArgumentException(
                    "currency mismatch: %s vs %s".formatted(currency, other.currency));
        }
        return other;
    }

    @Override
    public String toString() {
        return toMajor().toPlainString() + " " + currency;
    }
}
