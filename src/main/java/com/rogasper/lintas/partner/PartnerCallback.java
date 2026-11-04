package com.rogasper.lintas.partner;

import java.time.Instant;
import java.util.Objects;

/**
 * A partner status event arriving over the callback endpoint.
 *
 * @param sequence partner-side ordering, monotonically increasing per transfer
 */
public record PartnerCallback(
        String partnerRef,
        String transferId,
        PartnerStatus status,
        long sequence,
        Instant occurredAt) {

    public PartnerCallback {
        Objects.requireNonNull(partnerRef, "partnerRef is required");
        Objects.requireNonNull(transferId, "transferId is required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(occurredAt, "occurredAt is required");
    }
}
