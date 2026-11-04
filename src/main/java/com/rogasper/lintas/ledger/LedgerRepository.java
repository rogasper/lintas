package com.rogasper.lintas.ledger;

import java.time.Instant;
import java.util.List;

/**
 * Append-only journal storage. Note there is no {@code update} or {@code delete} on purpose.
 */
public interface LedgerRepository {

    Entry append(String transactionRef, Posting posting, Instant recordedAt);

    List<Entry> findByTransaction(String transactionRef);

    List<Entry> findByAccount(String accountId);

    List<Entry> findAll();

    boolean hasTransaction(String transactionRef);
}
