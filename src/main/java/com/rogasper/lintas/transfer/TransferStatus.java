package com.rogasper.lintas.transfer;

/**
 * Lifecycle of a transfer. Treat this as a state machine, not a free-text status:
 * <pre>
 * PENDING_PARTNER -> ACCEPTED -> PAID
 * PENDING_PARTNER -> FAILED -> REFUNDED
 * PENDING_PARTNER -> CANCELLED
 * </pre>
 * Any other transition is a bug, and callbacks are exactly where it happens.
 */
public enum TransferStatus {
    PENDING_PARTNER,
    ACCEPTED,
    PAID,
    FAILED,
    REFUNDED,
    CANCELLED
}
