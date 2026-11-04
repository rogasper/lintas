package com.rogasper.lintas.ledger;

import com.rogasper.lintas.shared.Money;

import java.util.Objects;

/** One leg of a journal transaction, before it is recorded. */
public record Posting(String accountId, EntryDirection direction, Money amount) {

    public Posting {
        Objects.requireNonNull(accountId, "accountId is required");
        Objects.requireNonNull(direction, "direction is required");
        Objects.requireNonNull(amount, "amount is required");
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("posting amount must be positive: " + amount);
        }
    }
}
