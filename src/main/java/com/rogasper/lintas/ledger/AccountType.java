package com.rogasper.lintas.ledger;

/**
 * The kind of ledger account. Lintas keeps customer funds separate from partner settlement and
 * fee accounts, so a bug in one bucket cannot silently eat another.
 */
public enum AccountType {
    /** Funds held for a customer. */
    CUSTOMER,
    /** Funds due to / from the payout partner. */
    PARTNER,
    /** Fee income. */
    FEE,
    /** FX position. */
    FX,
    /** Everything we could not classify — must always net to zero. */
    SUSPENSE
}
