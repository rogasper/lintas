package com.rogasper.lintas.transfer;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory transfer store. {@code findByIdempotencyKey} is a scan here; in a real database this
 * must be a unique index, otherwise two concurrent requests both see "not found" and both insert.
 */
@Repository
public class InMemoryTransferRepository implements TransferRepository {

    private final Map<String, Transfer> byId = new ConcurrentHashMap<>();

    @Override
    public Transfer save(Transfer transfer) {
        byId.put(transfer.id(), transfer);
        return transfer;
    }

    @Override
    public Optional<Transfer> findById(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public Optional<Transfer> findByIdempotencyKey(String idempotencyKey) {
        return byId.values().stream()
                .filter(transfer -> transfer.idempotencyKey().equals(idempotencyKey))
                .findFirst();
    }

    @Override
    public List<Transfer> findAll() {
        return List.copyOf(byId.values());
    }
}
