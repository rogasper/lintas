package com.rogasper.lintas.partner;

/**
 * Result of a partner submission. A result is not a final answer: {@code timedOut} means we do not
 * know whether the partner processed it, and the only safe follow-up is a status query or a retry
 * with the same partner-side reference.
 */
public record PartnerResult(
        boolean accepted,
        String partnerRef,
        PartnerStatus status,
        String errorCode,
        boolean timedOut) {

    public static PartnerResult accepted(String partnerRef) {
        return new PartnerResult(true, partnerRef, PartnerStatus.ACCEPTED, null, false);
    }

    public static PartnerResult accepted(String partnerRef, PartnerStatus status) {
        return new PartnerResult(true, partnerRef, status, null, false);
    }

    public static PartnerResult rejected(String errorCode) {
        return new PartnerResult(false, null, PartnerStatus.FAILED, errorCode, false);
    }

    public static PartnerResult timeout() {
        return new PartnerResult(false, null, PartnerStatus.PENDING, "TIMEOUT", true);
    }
}
