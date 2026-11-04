package com.rogasper.lintas.partner;

import com.rogasper.lintas.shared.Money;

import java.util.Objects;

/** What we send to the payout partner. */
public record PartnerRequest(
        String transferId,
        Money sourceAmount,
        Money targetAmount,
        String beneficiaryRef) {

    public PartnerRequest {
        Objects.requireNonNull(transferId, "transferId is required");
        Objects.requireNonNull(sourceAmount, "sourceAmount is required");
        Objects.requireNonNull(targetAmount, "targetAmount is required");
        Objects.requireNonNull(beneficiaryRef, "beneficiaryRef is required");
    }
}
