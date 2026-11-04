package com.rogasper.lintas.transfer;

/** Thrown when an expired quote is used. Quotes have a shelf life for a reason: the rate moved. */
public class QuoteExpiredException extends RuntimeException {

    public QuoteExpiredException(String quoteId) {
        super("quote expired: " + quoteId);
    }
}
