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

/**
 * Drill 18 — a late ACCEPTED after REFUNDED resurrects a dead transfer.
 *
 * <p>This is the classic remittance horror, and it is a two-party race that no amount of retry logic
 * fixes: the partner is slow, we time it out, we reject and refund the customer, then the partner
 * comes back and says "accepted — I paid the beneficiary". Today the transfer happily becomes
 * ACCEPTED again, the customer's money is gone from the ledger <em>and</em> already refunded, and
 * nothing in the system records that we are now short one payout.
 *
 * <p>The mirror image is just as bad: a PAID after REFUNDED means we refunded a transfer that
 * actually settled. Both must be stopped at the state machine, not at the sequence filter — the
 * events genuinely are newer.
 *
 * <p>Contract:
 * <ul>
 *   <li>REFUNDED is terminal. Neither ACCEPTED nor PAID may change it;</li>
 *   <li>the journal does not move: the refund entry stays the last thing that happened;</li>
 *   <li>a contradicting event is an incident for reconciliation. Record what you can see (the
 *       sequence), do not manufacture a state, and do not throw at the webhook.</li>
 * </ul>
 */
class Drill18RefundedIsTerminalTest {

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

    @Test
    @DisplayName("drill 18: a late ACCEPTED, then PAID, cannot wake a refunded transfer")
    void drill18_lateAcceptedAfterRefundChangesNothing() {
        partner.script(PartnerResult.rejected("BENEFICIARY_INVALID"));
        Transfer refunded = service.execute("IDEM-18", newQuote().id());

        assertThat(refunded.status()).isEqualTo(TransferStatus.REFUNDED);
        assertThat(ledger.balanceOf(customerAccount.id())).isEqualTo(usd("0.00"));

        // the partner, having "rejected" the payout, now claims it accepted — then that it paid
        callbacks.handle(new PartnerCallback("P-late", refunded.id(), PartnerStatus.ACCEPTED, 10L, NOW.plusSeconds(300)));
        callbacks.handle(new PartnerCallback("P-late", refunded.id(), PartnerStatus.PAID, 11L, NOW.plusSeconds(600)));

        assertThat(current(refunded.id()).status())
                .as("a refunded transfer is finished: newer events describe a different reality, not a new status")
                .isEqualTo(TransferStatus.REFUNDED);
        assertThat(ledger.balanceOf(customerAccount.id()))
                .as("the customer already got the money back; the ledger must not move again")
                .isEqualTo(usd("0.00"));
        assertThat(ledger.balanceOf(partnerAccount.id()))
                .as("and the partner settlement account must net to zero as well")
                .isEqualTo(usd("0.00"));
        assertThat(entries.findAll())
                .as("two entries for the debit, two for the refund, and nothing after that")
                .hasSize(4);
    }
}
