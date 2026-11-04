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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Drills 5-11: the failure modes of moving money through somebody else's system.
 * All red until {@link TransferService} and {@link TransferCallbackService} are implemented.
 */
class TransferServiceTest {

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

    private String partnerRefOf(Transfer transfer) {
        return transfer.partnerRef() != null ? transfer.partnerRef() : "P-" + transfer.id();
    }

    private Transfer current(String transferId) {
        return transfers.findById(transferId).orElseThrow();
    }

    @Test
    @DisplayName("drill 5: an expired quote cannot be executed")
    void drill05_expiredQuoteIsRefused() {
        Quote quote = newQuote();
        TransferService afterExpiry = new TransferService(
                quotes, transfers, ledger, partner, Clock.fixed(NOW.plus(Duration.ofMinutes(10)), ZoneOffset.UTC));

        assertThatThrownBy(() -> afterExpiry.execute("IDEM-EXPIRED", quote.id()))
                .isInstanceOf(QuoteExpiredException.class);

        assertThat(transfers.findAll()).isEmpty();
        assertThat(ledger.entriesOf(customerAccount.id())).isEmpty();
    }

    @Test
    @DisplayName("drill 6: the same idempotency key twice creates one transfer and one debit")
    void drill06_idempotencyKeyIsNotChargedTwice() {
        Quote quote = newQuote();

        Transfer first = service.execute("IDEM-1", quote.id());
        Transfer second = service.execute("IDEM-1", quote.id());

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(transfers.findAll()).hasSize(1);
        assertThat(ledger.entriesOf(customerAccount.id())).hasSize(1);
    }

    @Test
    @DisplayName("drill 7: a duplicated partner callback is applied once")
    void drill07_duplicateCallbackIsIgnored() {
        Transfer transfer = service.execute("IDEM-2", newQuote().id());
        String partnerRef = partnerRefOf(transfer);

        callbacks.handle(new PartnerCallback(partnerRef, transfer.id(), PartnerStatus.PAID, 1L, NOW.plusSeconds(60)));
        int afterFirstPaid = entries.findAll().size();

        callbacks.handle(new PartnerCallback(partnerRef, transfer.id(), PartnerStatus.PAID, 1L, NOW.plusSeconds(60)));

        assertThat(current(transfer.id()).status()).isEqualTo(TransferStatus.PAID);
        assertThat(entries.findAll()).hasSize(afterFirstPaid);
    }

    @Test
    @DisplayName("drill 8: an out-of-order callback never moves the transfer backwards")
    void drill08_outOfOrderCallbackKeepsFinalState() {
        Transfer transfer = service.execute("IDEM-3", newQuote().id());
        String partnerRef = partnerRefOf(transfer);

        callbacks.handle(new PartnerCallback(partnerRef, transfer.id(), PartnerStatus.PAID, 2L, NOW.plusSeconds(90)));
        int afterPaid = entries.findAll().size();

        // the ACCEPTED event arrives late, with an older sequence
        callbacks.handle(new PartnerCallback(partnerRef, transfer.id(), PartnerStatus.ACCEPTED, 1L, NOW.plusSeconds(30)));

        assertThat(current(transfer.id()).status()).isEqualTo(TransferStatus.PAID);
        assertThat(current(transfer.id()).lastPartnerSequence()).isEqualTo(2L);
        assertThat(entries.findAll()).hasSize(afterPaid);
    }

    @Test
    @DisplayName("drill 9: a partner timeout is resumable and debits the customer once")
    void drill09_partnerTimeoutResumesWithoutDoubleDebit() {
        partner.script(PartnerResult.timeout());
        Quote quote = newQuote();

        assertThatThrownBy(() -> service.execute("IDEM-4", quote.id()))
                .isInstanceOf(PartnerTimeoutException.class);

        assertThat(transfers.findAll()).hasSize(1);
        assertThat(transfers.findAll().get(0).status()).isEqualTo(TransferStatus.PENDING_PARTNER);

        Transfer resumed = service.execute("IDEM-4", quote.id());

        assertThat(resumed.status()).isEqualTo(TransferStatus.ACCEPTED);
        assertThat(transfers.findAll()).hasSize(1);
        assertThat(ledger.entriesOf(customerAccount.id())).hasSize(1);
    }

    @Test
    @DisplayName("drill 10: a permanent partner rejection refunds the customer")
    void drill10_permanentFailureIsRefunded() {
        partner.script(PartnerResult.rejected("BENEFICIARY_INVALID"));
        Quote quote = newQuote();

        Transfer transfer = service.execute("IDEM-5", quote.id());

        assertThat(transfer.status()).isEqualTo(TransferStatus.REFUNDED);
        assertThat(current(transfer.id()).status()).isEqualTo(TransferStatus.REFUNDED);
        assertThat(ledger.balanceOf(customerAccount.id())).isEqualTo(Money.zero(Currency.USD));
        assertThat(ledger.balanceOf(customerAccount.id())).isEqualTo(usd("0.00"));
    }

    @Test
    @DisplayName("drill 11: four concurrent executes with one key still produce one transfer")
    void drill11_concurrentExecuteCreatesOneTransfer() throws Exception {
        Quote quote = newQuote();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Transfer>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < 4; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return service.execute("IDEM-6", quote.id());
                }));
            }
            start.countDown();

            Set<String> ids = new HashSet<>();
            for (Future<Transfer> future : futures) {
                ids.add(future.get(5, TimeUnit.SECONDS).id());
            }

            assertThat(ids).hasSize(1);
            assertThat(transfers.findAll()).hasSize(1);
            assertThat(ledger.entriesOf(customerAccount.id())).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
    }
}
