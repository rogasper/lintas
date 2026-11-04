package com.rogasper.lintas.transfer;

import java.util.List;
import java.util.Optional;

public interface TransferRepository {

    Transfer save(Transfer transfer);

    Optional<Transfer> findById(String id);

    /** Used to make {@code execute} idempotent. Must be a real unique constraint in production. */
    Optional<Transfer> findByIdempotencyKey(String idempotencyKey);

    List<Transfer> findAll();
}
