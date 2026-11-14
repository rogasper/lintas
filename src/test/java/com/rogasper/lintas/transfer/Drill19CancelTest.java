package com.rogasper.lintas.transfer;

import com.rogasper.lintas.ledger.Account;
import com.rogasper.lintas.ledger.AccountType;
import com.rogasper.lintas.ledger.InMemoryAccountRepository;
import com.rogasper.lintas.ledger.InMemoryLedgerRepository;
import com.rogasper.lintas.ledger.LedgerService;
import com.rogasper.lintas.partner.FakePartnerClient;
import com.rogasper.lintas.partner.PartnerCallback;
import com.rogasper.lintas.partner.PartnerResult;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Drill 19 — {@code cancel()} is the last public method that still throws
 * {@code UnsupportedOperationException}, and it is the one customers actually use: "I changed my
 * mind" / "the beneficiary details were wrong" arrives while the partner is still thinking.
 *
 * <p>Cancelling is not "set status to CANCELLED". The money is already out of the customer's
 * balance in our books, so a cancellation is a refund with a deadline: legal only before the
 * partner accepted, and it must move money exactly once even if the customer clicks twice, the
 * button retries, or the API is called by two tabs at the same time.
 *
 * <p>Contract:
 * <ul>
 *   <li>cancel is legal while the transfer is {@code PENDING_PARTNER} — the state a timeout leaves
 *       behind (drill 9) — and illegal once the partner accepted (or anything later);</li>
 *   <li>an illegal cancel throws {@link IllegalStateException} and changes nothing;</li>
 *   <li>a legal cancel posts the compensating entry, leaves the customer at zero, and is
 *       idempotent: the second call returns the same CANCELLED transfer without posting again;</li>
 *   <li>CANCELLED is terminal, exactly like PAID (drill 17) and REFUNDED (drill 18) — a late
 *       ACCEPTED must not bring it back to life;</li>
 *   <li>unknown transfer id is a client error: {@link IllegalArgumentException}.</li>
 * </ul>
 */
class Drill19CancelTest {

    private static final Instant NOW = Instant.parse("2026-11-04T09:00:00Z");

    private InMemoryQuoteRepository quotes;
    private InMemoryTransferRepository transfers;
    private InMemoryLedgerRepository entries;
    private LedgerService ledger;
    private FakePartnerClient partner;
    private TransferService service;
    private TransferCallbackService callbacks;
    private Account customerAccount;
    private Account partnerAccount;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        InMemoryAccountRepository accounts = new InMemoryAccountRepository();
        entries = new InMemoryLedgerRepository();
        ledger = new LedgerService(accounts, entries);
        customerAccount = accounts.save(new Account("ACC-CUST-1", "CUST-1", AccountType.CUSTOMER, Currency.USD));
        partnerAccount = accounts.save(new Account("ACC-PART-1", "PARTNER-1", AccountType.PARTNER, Currency.USD));

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

    /** A transfer stuck at PENDING_PARTNER: the partner never answered, so nothing is final yet. */
    private String pendingTransfer(String idempotencyKey) {
        partner.script(PartnerResult.timeout());
        Quote quote = newQuote();
        assertThatThrownBy(() -> service.execute(idempotencyKey, quote.id()))
                .isInstanceOf(PartnerTimeoutException.class);
        return transfers.findAll().get(transfers.findAll().size() - 1).id();
    }

    @Test
    @DisplayName("drill 19: cancelling a pending transfer refunds the customer exactly once")
    void drill19_cancelRefundsOnce() {
        String transferId = pendingTransfer("IDEM-19A");
        assertThat(ledger.balanceOf(customerAccount.id())).isEqualTo(usd("-100.00"));

        Transfer cancelled = service.cancel(transferId);

        assertThat(cancelled.status()).isEqualTo(TransferStatus.CANCELLED);
        assertThat(current(transferId).status()).isEqualTo(TransferStatus.CANCELLED);
        assertThat(ledger.balanceOf(customerAccount.id()))
                .as("a cancellation gives the customer their money back")
                .isEqualTo(usd("0.00"));
        assertThat(ledger.balanceOf(partnerAccount.id()))
                .as("and takes it back off the partner settlement account")
                .isEqualTo(usd("0.00"));
        assertThat(entries.findAll())
                .as("two entries for the debit plus two for the compensating entry — a refund is a posting, never a deleted row")
                .hasSize(4);
    }

    @Test
    @DisplayName("drill 19: cancelling twice refunds once")
    void drill19_cancelIsIdempotent() {
        String transferId = pendingTransfer("IDEM-19B");

        Transfer first = service.cancel(transferId);
        Transfer second = service.cancel(transferId);

        assertThat(second.status()).isEqualTo(TransferStatus.CANCELLED);
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(ledger.balanceOf(customerAccount.id()))
                .as("a double click must not refund twice")
                .isEqualTo(usd("0.00"));
        assertThat(entries.findAll()).hasSize(4);
    }

    @Test
    @DisplayName("drill 19: once the partner accepted, cancel is refused and money stays put")
    void drill19_cancelAfterAcceptanceIsRefused() {
        Transfer accepted = service.execute("IDEM-19C", newQuote().id());
        assertThat(accepted.status()).isEqualTo(TransferStatus.ACCEPTED);

        assertThatThrownBy(() -> service.cancel(accepted.id()))
                .isInstanceOf(IllegalStateException.class);

        assertThat(current(accepted.id()).status()).isEqualTo(TransferStatus.ACCEPTED);
        assertThat(entries.findAll())
                .as("a refused cancellation must not post anything")
                .hasSize(2);
    }

    @Test
    @DisplayName("drill 19: a late ACCEPTED cannot resurrect a cancelled transfer")
    void drill19_lateAcceptedAfterCancelChangesNothing() {
        String transferId = pendingTransfer("IDEM-19D");
        service.cancel(transferId);

        callbacks.handle(new PartnerCallback("P-ghost", transferId, PartnerStatus.ACCEPTED, 42L, NOW.plusSeconds(900)));

        assertThat(current(transferId).status()).isEqualTo(TransferStatus.CANCELLED);
        assertThat(entries.findAll()).hasSize(4);
        assertThat(ledger.balanceOf(customerAccount.id())).isEqualTo(usd("0.00"));
    }

    @Test
    @DisplayName("drill 19: cancelling an unknown transfer is a client error")
    void drill19_cancelUnknownTransfer() {
        assertThatThrownBy(() -> service.cancel("TRF-DOES-NOT-EXIST"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
