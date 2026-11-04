package com.rogasper.lintas.recon;

import com.rogasper.lintas.partner.PartnerStatus;
import com.rogasper.lintas.shared.Money;

import java.util.Objects;

/** One line of the partner's settlement statement. This is the source of truth for money that left. */
public record PartnerStatementLine(
        String partnerRef,
        String transferId,
        Money amount,
        PartnerStatus status) {

    public PartnerStatementLine {
        Objects.requireNonNull(partnerRef, "partnerRef is required");
        Objects.requireNonNull(transferId, "transferId is required");
        Objects.requireNonNull(amount, "amount is required");
        Objects.requireNonNull(status, "status is required");
    }
}
