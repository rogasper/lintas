package com.rogasper.lintas.transfer;

import com.rogasper.lintas.ledger.Account;
import com.rogasper.lintas.ledger.AccountType;
import com.rogasper.lintas.ledger.InMemoryAccountRepository;
import com.rogasper.lintas.ledger.InMemoryLedgerRepository;
import com.rogasper.lintas.ledger.LedgerService;
import com.rogasper.lintas.partner.FakePartnerClient;
import com.rogasper.lintas.partner.PartnerResult;
import com.rogasper.lintas.shared.Currency;
import com.rogasper.lintas.shared.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Drill 16 — the attempt counter goes backwards on the failure path.
 *
 * <p>{@code sendToPartner} saves an incremented copy ({@code attempts + 1}), then, in the rejection
 * branch, saves a <em>different</em> copy derived from the transfer it was handed — the one where
 * {@code attempts} is still 0. The later write wins, so "we tried once and the partner said no"
 * is persisted as "we never tried". This is a lost update in miniature: two copies of one row,
 * last writer wins, and the loser is the field you need most when a customer asks what happened.
 *
 * <p>Why it matters beyond a counter: replace {@code attempts} with {@code status}, {@code fee},
 * or {@code rate} and the same bug becomes a money bug. The durable fix is optimistic locking —
 * a {@code version} column that makes the second writer fail loudly — not "remember to pass the
 * fresh copy around".
 *
 * <p>Contract: after one rejection, both the returned transfer and the stored transfer report
 * {@code attempts == 1}. A resumed timeout counts a second attempt and still debits once.
 */
class Drill16AttemptCounterTest {

    private static final Instant NOW = Instant.parse("2026-11-04T09:00:00Z");

    private InMemoryQuoteRepository quotes;
    private InMemoryTransferRepository transfers;
    private InMemoryLedgerRepository entries;
    private LedgerService ledger;
    private FakePartnerClient partner;
    private TransferService service;
    private Account customerAccount;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        InMemoryAccountRepository accounts = new InMemoryAccountRepository();
        entries = new InMemoryLedgerRepository();
        ledger = new LedgerService(accounts, entries);
        customerAccount = accounts.save(new Account("ACC-CUST-1", "CUST-1", AccountType.CUSTOMER, Currency.USD));
        accounts.save(new Account("ACC-PART-1", "PARTNER-1", AccountType.PARTNER, Currency.USD));

        quotes = new InMemoryQuoteRepository();
        transfers = new InMemoryTransferRepository();
        partner = new FakePartnerClient();
        service = new TransferService(quotes, transfers, ledger, partner, clock);
    }

    private static Money usd(String amount) {
        return Money.parse(amount, Currency.USD);
    }

    private Quote newQuote() {
        return service.createQuote("CUST-1", usd("100.00"), Currency.IDR,
                new BigDecimal("15800.00"), usd("1.50"), Duration.ofMinutes(5));
    }

    private Transfer current(String transferId) {
        return transfers.findById(transferId).orElseThrow();
    }

    @Test
    @DisplayName("drill 16: a rejected attempt does not erase the attempt counter")
    void drill16_rejectedAttemptKeepsItsCount() {
        partner.script(PartnerResult.rejected("BENEFICIARY_INVALID"));
        Quote quote = newQuote();

        Transfer refunded = service.execute("IDEM-16A", quote.id());

        assertThat(refunded.status()).isEqualTo(TransferStatus.REFUNDED);
        assertThat(refunded.attempts())
                .as("one submission reached the partner, so the returned transfer must say attempts=1")
                .isEqualTo(1);
        assertThat(current(refunded.id()).attempts())
                .as("the stored transfer must not disagree with what execute() returned")
                .isEqualTo(1);
        assertThat(ledger.entriesOf(customerAccount.id()))
                .as("the refund must still post exactly one compensating entry")
                .hasSize(2);
    }

    @Test
    @DisplayName("drill 16: a resumed timeout counts a second attempt and still debits once")
    void drill16_resumedTimeoutCountsSecondAttempt() {
        partner.script(PartnerResult.timeout());
        Quote quote = newQuote();

        assertThatThrownBy(() -> service.execute("IDEM-16B", quote.id()))
                .isInstanceOf(PartnerTimeoutException.class);
        String transferId = transfers.findAll().get(0).id();
        assertThat(current(transferId).attempts()).isEqualTo(1);

        Transfer resumed = service.execute("IDEM-16B", quote.id());

        assertThat(resumed.status()).isEqualTo(TransferStatus.ACCEPTED);
        assertThat(current(transferId).attempts())
                .as("the resume is a second submission, so the counter goes up, not down")
                .isEqualTo(2);
        assertThat(ledger.entriesOf(customerAccount.id()))
                .as("a retry is not a second charge")
                .hasSize(1);
    }
}
