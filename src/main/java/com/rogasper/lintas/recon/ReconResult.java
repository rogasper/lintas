package com.rogasper.lintas.recon;

import java.util.List;

/**
 * Differences found between our transfers and the partner statement. Everything is keyed by
 * partner reference or transfer id so an operator can act on it without opening a debugger.
 */
public record ReconResult(
        List<String> onlyInLedger,
        List<String> onlyAtPartner,
        List<String> amountMismatch,
        List<String> statusMismatch) {

    public ReconResult {
        onlyInLedger = List.copyOf(onlyInLedger);
        onlyAtPartner = List.copyOf(onlyAtPartner);
        amountMismatch = List.copyOf(amountMismatch);
        statusMismatch = List.copyOf(statusMismatch);
    }

    public boolean isClean() {
        return onlyInLedger.isEmpty()
                && onlyAtPartner.isEmpty()
                && amountMismatch.isEmpty()
                && statusMismatch.isEmpty();
    }
}
