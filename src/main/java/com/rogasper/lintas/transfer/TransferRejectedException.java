package com.rogasper.lintas.transfer;

/** A request that cannot be honoured, with a machine-readable reason. */
public class TransferRejectedException extends RuntimeException {

    private final String reason;

    public TransferRejectedException(String reason, String message) {
        super(message);
        this.reason = reason;
    }

    public String reason() {
        return reason;
    }
}
