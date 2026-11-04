package com.rogasper.lintas.transfer;

/**
 * Thrown when the partner call timed out. The money may or may not have moved on their side —
 * that is exactly why a timeout is never "just retry from scratch".
 */
public class PartnerTimeoutException extends RuntimeException {

    public PartnerTimeoutException(String transferId) {
        super("partner call timed out for transfer " + transferId + " (outcome unknown)");
    }
}
