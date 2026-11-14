package com.rogasper.lintas.transfer;

import com.rogasper.lintas.ledger.Account;
import com.rogasper.lintas.ledger.AccountType;
import com.rogasper.lintas.ledger.InMemoryAccountRepository;
import com.rogasper.lintas.ledger.InMemoryLedgerRepository;
import com.rogasper.lintas.ledger.LedgerService;
import com.rogasper.lintas.partner.FakePartnerClient;
import com.rogasper.lintas.shared.Currency;
import com.rogasper.lintas.shared.Money;
import com.rogasper.lintas.support.RaceGate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drill 14 — the same idempotency key arriving at two application instances at the same time.
 *
 * <p>Drill 11 only proved that <em>one</em> instance can serialise itself. Production never runs one
 * instance: a rolling deploy, a second pod, a failover node — two JVMs, one database. A
 * {@code synchronized} method is a lock inside one heap; it protects nothing across nodes.
 *
 * <p>Contract:
 * <ul>
 *   <li>the guarantee must live where the data lives — one row per {@code idempotency_key}, enforced
 *       by the store, not by a JVM monitor;</li>
 *   <li>the writer that loses the race must recover by reading the winner's row and returning it,
 *       not by propagating a constraint violation to the caller — the caller sent a retry, it
 *       deserves the original transfer back;</li>
 *   <li>the customer is debited once, no matter how many nodes saw "not found".</li>
 * </ul>
 *
 * <p>The gate below is test scaffolding, not production code: it makes both nodes read
 * "not found" before either is allowed to write, which is exactly the interleaving that a
 * per-JVM lock cannot see. Do not delete it, and do not "fix" the drill with a static lock —
 * a second JVM does not share your statics either.
 */
class Drill14TwoNodesIdempotencyTest {

    private static final Instant NOW = Instant.parse("2026-11-04T09:00:00Z");
    private static final String KEY = "IDEM-TWO-NODES";

    private InMemoryQuoteRepository quotes;
    private GatedTransferRepository store;
    private InMemoryLedgerRepository entries;
    private LedgerService ledger;
    private FakePartnerClient partner;
    private Account customerAccount;
    private TransferService nodeA;
    private TransferService nodeB;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        InMemoryAccountRepository accounts = new InMemoryAccountRepository();
        entries = new InMemoryLedgerRepository();
        ledger = new LedgerService(accounts, entries);
        customerAccount = accounts.save(new Account("ACC-CUST-1", "CUST-1", AccountType.CUSTOMER, Currency.USD));
        accounts.save(new Account("ACC-PART-1", "PARTNER-1", AccountType.PARTNER, Currency.USD));

        quotes = new InMemoryQuoteRepository();
        store = new GatedTransferRepository();
        partner = new FakePartnerClient();

        // Two services, one store: two "pods" behind the same database.
        nodeA = new TransferService(quotes, store, ledger, partner, clock);
        nodeB = new TransferService(quotes, store, ledger, partner, clock);
    }

    private static Money usd(String amount) {
        return Money.parse(amount, Currency.USD);
    }

    private Quote newQuote() {
        return nodeA.createQuote("CUST-1", usd("100.00"), Currency.IDR,
                new BigDecimal("15800.00"), usd("1.50"), Duration.ofMinutes(5));
    }

    @Test
    @DisplayName("drill 14: two instances, one idempotency key, one transfer, one debit")
    void drill14_twoInstancesOneKey() throws Exception {
        Quote quote = newQuote();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Transfer> first = pool.submit(() -> {
                start.await();
                return nodeA.execute(KEY, quote.id());
            });
            Future<Transfer> second = pool.submit(() -> {
                start.await();
                return nodeB.execute(KEY, quote.id());
            });
            start.countDown();

            Transfer a = first.get(15, TimeUnit.SECONDS);
            Transfer b = second.get(15, TimeUnit.SECONDS);

            assertThat(b.id())
                    .as("both nodes must answer with the same transfer for the same idempotency key")
                    .isEqualTo(a.id());
            assertThat(store.findAll())
                    .as("one idempotency key must produce one transfer even when two JVMs race for it")
                    .hasSize(1);
            assertThat(ledger.entriesOf(customerAccount.id()))
                    .as("the customer must be debited exactly once")
                    .hasSize(1);
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * The store from drill 14's point of view — one shared "database" — with a hook that holds both
     * readers until each has seen the (empty) result. Extends the in-memory store so any method you
     * add to the repository keeps compiling here.
     */
    static final class GatedTransferRepository extends InMemoryTransferRepository {

        private final RaceGate gate = new RaceGate(2_000);

        @Override
        public Optional<Transfer> findByIdempotencyKey(String idempotencyKey) {
            Optional<Transfer> alreadyThere = super.findByIdempotencyKey(idempotencyKey);
            if (KEY.equals(idempotencyKey)) {
                gate.meet();
            }
            return alreadyThere;
        }
    }
}
