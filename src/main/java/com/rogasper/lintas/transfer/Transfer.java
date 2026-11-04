package com.rogasper.lintas.transfer;

import com.rogasper.lintas.shared.Money;

import java.time.Instant;
import java.util.Objects;

/**
 * One attempt to move money through a partner.
 *
 * <p>{@code idempotencyKey} is supplied by the caller and is what makes a retry safe.
 * {@code lastPartnerSequence} is the highest partner callback sequence already applied — the
 * callback endpoint will happily deliver older events after newer ones.
 */
public record Transfer(
        String id,
        String quoteId,
        String idempotencyKey,
        Money sourceAmount,
        Money targetAmount,
        TransferStatus status,
        String partnerRef,
        int attempts,
        long lastPartnerSequence,
        Instant createdAt) {

    public Transfer {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(quoteId, "quoteId is required");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey is required");
        Objects.requireNonNull(sourceAmount, "sourceAmount is required");
        Objects.requireNonNull(targetAmount, "targetAmount is required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
    }

    public static Transfer pending(
            String id,
            String quoteId,
            String idempotencyKey,
            Money sourceAmount,
            Money targetAmount,
            Instant createdAt) {
        return new Transfer(id, quoteId, idempotencyKey, sourceAmount, targetAmount,
                TransferStatus.PENDING_PARTNER, null, 0, 0L, createdAt);
    }

    public Transfer withStatus(TransferStatus newStatus) {
        return new Transfer(id, quoteId, idempotencyKey, sourceAmount, targetAmount,
                newStatus, partnerRef, attempts, lastPartnerSequence, createdAt);
    }

    public Transfer withPartnerRef(String newPartnerRef) {
        return new Transfer(id, quoteId, idempotencyKey, sourceAmount, targetAmount,
                status, newPartnerRef, attempts, lastPartnerSequence, createdAt);
    }

    public Transfer withAttempts(int newAttempts) {
        return new Transfer(id, quoteId, idempotencyKey, sourceAmount, targetAmount,
                status, partnerRef, newAttempts, lastPartnerSequence, createdAt);
    }

    public Transfer withLastPartnerSequence(long sequence) {
        return new Transfer(id, quoteId, idempotencyKey, sourceAmount, targetAmount,
                status, partnerRef, attempts, sequence, createdAt);
    }
}
