package com.rogasper.lintas.transfer;

import com.rogasper.lintas.ledger.EntryDirection;
import com.rogasper.lintas.ledger.LedgerService;
import com.rogasper.lintas.ledger.Posting;
import com.rogasper.lintas.partner.PartnerClient;
import com.rogasper.lintas.partner.PartnerRequest;
import com.rogasper.lintas.partner.PartnerResult;
import com.rogasper.lintas.shared.Currency;
import com.rogasper.lintas.shared.Money;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Quote + transfer orchestration.
 *
 * <p>Already implemented: quote pricing. Left for you: everything that can fail halfway —
 * executing against a partner, and staying correct when the same request arrives twice.
 */
@Service
public class TransferService {

    private final QuoteRepository quotes;
    private final TransferRepository transfers;
    private final LedgerService ledger;
    private final PartnerClient partner;
    private final Clock clock;

    public TransferService(
            QuoteRepository quotes,
            TransferRepository transfers,
            LedgerService ledger,
            PartnerClient partner,
            Clock clock) {
        this.quotes = quotes;
        this.transfers = transfers;
        this.ledger = ledger;
        this.partner = partner;
        this.clock = clock;
    }

    /**
     * Prices a quote. {@code targetAmount = sourceAmount * rate}, rounded HALF_UP to the target
     * currency's scale (that rounding is a business decision — keep it, and keep it tested).
     */
    public Quote createQuote(
            String customerRef,
            Money sourceAmount,
            Currency targetCurrency,
            BigDecimal rate,
            Money fee,
            Duration ttl) {

        Instant now = clock.instant();
        if (!sourceAmount.isPositive()) {
            throw new IllegalArgumentException("source amount must be positive: " + sourceAmount);
        }
        if (fee.isNegative()) {
            throw new IllegalArgumentException("fee must not be negative: " + fee);
        }
        if (fee.currency() != sourceAmount.currency()) {
            throw new IllegalArgumentException("fee must be charged in the source currency");
        }
        if (rate == null || rate.signum() <= 0) {
            throw new IllegalArgumentException("rate must be positive");
        }
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("quote ttl must be positive");
        }

        Money targetAmount = Money.ofMajor(
                sourceAmount.toMajor().multiply(rate).setScale(targetCurrency.scale(), RoundingMode.HALF_UP),
                targetCurrency);

        Quote quote = new Quote(
                "Q-" + UUID.randomUUID().toString().substring(0, 8),
                customerRef,
                sourceAmount,
                targetAmount,
                rate,
                fee,
                now,
                now.plus(ttl));
        return quotes.save(quote);
    }

    /**
     * Starts (or resumes) a transfer for a quote.
     *
     * <p>Must satisfy, in this order:
     * <ol>
     *   <li>same {@code idempotencyKey} twice returns the same transfer and moves money once;</li>
     *   <li>an expired quote is refused with {@link QuoteExpiredException};</li>
     *   <li>a partner timeout raises {@link PartnerTimeoutException} and leaves the transfer
     *       {@code PENDING_PARTNER} — calling {@code execute} again with the same key resumes it
     *       instead of creating a second transfer;</li>
     *   <li>a permanent partner rejection ends in {@code REFUNDED} with the customer's ledger
     *       balance back to where it started.</li>
     * </ol>
     */
    public synchronized Transfer execute(String idempotencyKey, String quoteId) {
        Optional<Transfer> existing = transfers.findByIdempotencyKey(idempotencyKey);
        if(existing.isPresent()){
            Transfer transfer = existing.get();
            if (transfer.status() == TransferStatus.PENDING_PARTNER) {
                Quote quote = quotes.findById(transfer.quoteId())
                    .orElseThrow(() -> new IllegalArgumentException("Unknown quote: " + transfer.quoteId()));
                return sendToPartner(transfer, quote.customerRef(), quote.sourceAmount());
            }
            return transfer;
        }

        Quote quote = quotes.findById(quoteId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown quote: " + quoteId));

        if (quote.isExpired(clock.instant())){
            throw new QuoteExpiredException(quote.id());
        }

        String transferId = "TRF-" + UUID.randomUUID().toString().substring(0,8);
        Transfer transfer = Transfer.pending(
            transferId,
            quote.id(),
            idempotencyKey,
            quote.sourceAmount(),
            quote.targetAmount(),
            clock.instant()
        );
        transfers.save(transfer);

        ledger.post("TX-" + transferId, List.of(
            new Posting(customerAccountId(quote), EntryDirection.DEBIT, quote.sourceAmount()),
            new Posting(partnerAccountId(), EntryDirection.CREDIT, quote.sourceAmount())
        ));

        return sendToPartner(transfer, quote.customerRef(), quote.sourceAmount());
    }

    /** Cancels a transfer that has not reached the partner yet. */
    public Transfer cancel(String transferId) {
        throw new UnsupportedOperationException(
                "TODO: implement TransferService.cancel — only legal before the partner accepts");
    }

    public Optional<Transfer> find(String transferId) {
        return transfers.findById(transferId);
    }

    /** Exposed for callbacks and reconciliation; a real system would not hand out the ledger. */
    LedgerService ledger() {
        return ledger;
    }

    private Transfer sendToPartner(Transfer transfer, String customerRef, Money sourceAmount){
        Transfer attempt = transfers.save(transfer.withAttempts(transfer.attempts() + 1));
        PartnerResult result = partner.submit(new PartnerRequest(
            transfer.id(),
            transfer.sourceAmount(),
            transfer.targetAmount(),
            customerRef
        ));

        if (result.timedOut()) {
            throw new PartnerTimeoutException(attempt.id());
        }

        if(result.accepted()){
            return transfers.save(attempt
                                 .withStatus(TransferStatus.ACCEPTED)
                                 .withPartnerRef(result.partnerRef()));
        }

        ledger.post("TX-REFUND-"+ transfer.id(), List.of(
            new Posting(customerAccountId(customerRef), EntryDirection.CREDIT, sourceAmount),
            new Posting(partnerAccountId(), EntryDirection.DEBIT, sourceAmount)
        ));

        transfer = transfer.withStatus(TransferStatus.REFUNDED);
        return transfers.save(transfer);
    }

    private static String customerAccountId(Quote quote){
        return customerAccountId(quote.customerRef());
    }

    private static String customerAccountId(String customerRef){
        return "ACC-" + customerRef;
    }

    private static String partnerAccountId(){
        return "ACC-PART-1";
    }
}
