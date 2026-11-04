package com.rogasper.lintas.ledger;

import com.rogasper.lintas.shared.Currency;

import java.util.Objects;

/** A ledger account. Balances are never stored — they are derived from {@link Entry entries}. */
public record Account(String id, String ownerRef, AccountType type, Currency currency) {

    public Account {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(ownerRef, "ownerRef is required");
        Objects.requireNonNull(type, "type is required");
        Objects.requireNonNull(currency, "currency is required");
    }
}
