package com.rogasper.lintas.ledger;

import java.time.Instant;
import java.util.Objects;

/** An immutable, recorded ledger entry. Append-only: corrections are compensating entries. */
public record Entry(long sequence, String transactionRef, Posting posting, Instant recordedAt) {

    public Entry {
        Objects.requireNonNull(transactionRef, "transactionRef is required");
        Objects.requireNonNull(posting, "posting is required");
        Objects.requireNonNull(recordedAt, "recordedAt is required");
    }

    public String accountId() {
        return posting.accountId();
    }

    public EntryDirection direction() {
        return posting.direction();
    }
}
