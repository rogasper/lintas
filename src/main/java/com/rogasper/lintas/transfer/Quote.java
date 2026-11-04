package com.rogasper.lintas.transfer;

import com.rogasper.lintas.shared.Money;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * A price promise: this much source money buys that much target money, until {@code expiresAt}.
 *
 * @param rate target units per one source unit
 * @param fee  charged in the source currency
 */
public record Quote(
        String id,
        String customerRef,
        Money sourceAmount,
        Money targetAmount,
        BigDecimal rate,
        Money fee,
        Instant createdAt,
        Instant expiresAt) {

    public Quote {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(customerRef, "customerRef is required");
        Objects.requireNonNull(sourceAmount, "sourceAmount is required");
        Objects.requireNonNull(targetAmount, "targetAmount is required");
        Objects.requireNonNull(rate, "rate is required");
        Objects.requireNonNull(fee, "fee is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(expiresAt, "expiresAt is required");
    }

    /** @return true when {@code now} is at or past the expiry instant */
    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }
}
