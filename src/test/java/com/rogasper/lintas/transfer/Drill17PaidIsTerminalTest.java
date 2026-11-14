package com.rogasper.lintas.transfer;

import com.rogasper.lintas.ledger.Account;
import com.rogasper.lintas.ledger.AccountType;
import com.rogasper.lintas.ledger.InMemoryAccountRepository;
import com.rogasper.lintas.ledger.InMemoryLedgerRepository;
import com.rogasper.lintas.ledger.LedgerService;
import com.rogasper.lintas.partner.FakePartnerClient;
import com.rogasper.lintas.partner.PartnerCallback;
import com.rogasper.lintas.partner.PartnerStatus;
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

/**
 * Drill 17 — a late FAILED after PAID must not undo the payment.
 *
 * <p>The guard in {@code TransferCallbackService} only stops one edge: {@code PAID -> ACCEPTED}.
 * Everything else is allowed, so a partner that confirms the payout and then sends a stale or
 * contradictory {@code FAILED} flips the transfer to "failed" — while the money is gone and the
 * journal still shows one debit and no refund. Ops reads "failed", retries the payout, and the
 * beneficiary is paid twice. This is how remittance systems lose real money.
 *
 * <p>Drill 8 does not catch it because there the late event carried an <em>older</em> sequence and
 * was dropped by the sequence filter. A genuinely newer contradicting event is a different animal:
 * no ordering rule can save you, only an explicit state machine.
 *
 * <p>Contract:
 * <ul>
 *   <li>PAID is terminal for status. A newer FAILED must leave the transfer PAID and the journal
 *       untouched;</li>
 *   <li>do not throw and do not 500: a partner webhook that errors gets retried forever. Return the
 *       current state and raise the contradiction as an incident (recon is where it surfaces);</li>
 *   <li>silently marking it FAILED is the one answer that is always wrong — it tells everybody "no
 *       money moved" when money moved.</li>
 * </ul>
 */
class Drill17PaidIsTerminalTest {

    private static final Instant NOW = Instant.parse("2026-11-04T09:00:00Z");

    private InMemoryQuoteRepository quotes;
    private InMemoryTransferRepository transfers;
    private InMemoryLedgerRepository entries;
    private LedgerService ledger;
    private FakePartnerClient partner;
    private TransferService service;
    private TransferCallbackService callbacks;
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
        callbacks = new TransferCallbackService(transfers, ledger, clock);
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
    @DisplayName("drill 17: a newer FAILED after PAID leaves the transfer PAID and the journal untouched")
    void drill17_lateFailureAfterPaidChangesNothing() {
        Transfer transfer = service.execute("IDEM-17", newQuote().id());
        String partnerRef = transfer.partnerRef();

        callbacks.handle(new PartnerCallback(partnerRef, transfer.id(), PartnerStatus.PAID, 2L, NOW.plusSeconds(60)));
        assertThat(current(transfer.id()).status()).isEqualTo(TransferStatus.PAID);
        int entriesAfterPaid = entries.findAll().size();

        // the partner now contradicts itself, with a strictly newer sequence
        callbacks.handle(new PartnerCallback(partnerRef, transfer.id(), PartnerStatus.FAILED, 3L, NOW.plusSeconds(120)));

        assertThat(current(transfer.id()).status())
                .as("PAID means the beneficiary was credited; a later FAILED must not rewrite history")
                .isEqualTo(TransferStatus.PAID);
        assertThat(entries.findAll())
                .as("no status change means no ledger movement — a FAILED here would need a reversal, not a relabel")
                .hasSize(entriesAfterPaid);
        assertThat(ledger.entriesOf(customerAccount.id()))
                .as("the customer's debit must still be there, and still be the only one")
                .hasSize(1);
    }
}
