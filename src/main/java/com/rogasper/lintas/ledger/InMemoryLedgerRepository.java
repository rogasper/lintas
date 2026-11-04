package com.rogasper.lintas.ledger;

import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe in-memory journal. Good enough for drills; swap for JDBC later without touching
 * {@link LedgerService}.
 */
@Repository
public class InMemoryLedgerRepository implements LedgerRepository {

    private final AtomicLong sequence = new AtomicLong();
    private final List<Entry> entries = new CopyOnWriteArrayList<>();

    @Override
    public Entry append(String transactionRef, Posting posting, Instant recordedAt) {
        Entry entry = new Entry(sequence.incrementAndGet(), transactionRef, posting, recordedAt);
        entries.add(entry);
        return entry;
    }

    @Override
    public List<Entry> findByTransaction(String transactionRef) {
        return entries.stream()
                .filter(entry -> entry.transactionRef().equals(transactionRef))
                .toList();
    }

    @Override
    public List<Entry> findByAccount(String accountId) {
        return entries.stream()
                .filter(entry -> entry.accountId().equals(accountId))
                .toList();
    }

    @Override
    public List<Entry> findAll() {
        return List.copyOf(entries);
    }

    @Override
    public boolean hasTransaction(String transactionRef) {
        return entries.stream().anyMatch(entry -> entry.transactionRef().equals(transactionRef));
    }
}
