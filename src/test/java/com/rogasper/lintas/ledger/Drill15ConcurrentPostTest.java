package com.rogasper.lintas.ledger;

import com.rogasper.lintas.shared.Currency;
import com.rogasper.lintas.shared.Money;
import com.rogasper.lintas.support.RaceGate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drill 15 — the journal's idempotency check is a check-then-act.
 *
 * <p>{@code post()} asks "has this transaction been recorded?" and then writes. Those are two
 * separate steps with a gap in the middle, and in a database the gap is the whole problem: two
 * requests for the same transaction both ask, both hear "no", and both write. Today the only reason
 * this repo does not double-post is that every caller happens to go through a {@code synchronized}
 * method one layer up — exactly the kind of accidental protection that disappears the moment a
 * second instance (drill 14) or a second entry point shows up.
 *
 * <p>Contract:
 * <ul>
 *   <li>two concurrent posts of the same transaction reference produce one journal (two entries),
 *       not two journals;</li>
 *   <li>both callers get the recorded journal back — the loser reads, it does not fail;</li>
 *   <li>the check and the write have to be one atomic step <em>in the store</em>: a unique index on
 *       {@code (transaction_ref, account_id, direction)}, or an append-if-absent operation. The two
 *       callers below go through two different {@link LedgerService} instances on purpose — the
 *       journal is the shared thing, so that is where the guarantee has to live.</li>
 * </ul>
 */
class Drill15ConcurrentPostTest {

    private static final String TX = "TX-RACE-1";

    private GatedLedgerRepository entries;
    private LedgerService nodeA;
    private LedgerService nodeB;
    private Account debitAccount;
    private Account creditAccount;

    @BeforeEach
    void setUp() {
        InMemoryAccountRepository accounts = new InMemoryAccountRepository();
        entries = new GatedLedgerRepository();
        // Two services, one journal: two "pods" behind the same database.
        nodeA = new LedgerService(accounts, entries);
        nodeB = new LedgerService(accounts, entries);
        debitAccount = accounts.save(new Account("ACC-CUST-1", "CUST-1", AccountType.CUSTOMER, Currency.USD));
        creditAccount = accounts.save(new Account("ACC-PART-1", "PARTNER-1", AccountType.PARTNER, Currency.USD));
    }

    private static Money usd(String amount) {
        return Money.parse(amount, Currency.USD);
    }

    @Test
    @DisplayName("drill 15: two concurrent posts of one transaction reference write one journal")
    void drill15_concurrentPostWritesOneJournal() throws Exception {
        List<Posting> postings = List.of(
                new Posting(debitAccount.id(), EntryDirection.DEBIT, usd("100.00")),
                new Posting(creditAccount.id(), EntryDirection.CREDIT, usd("100.00")));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<List<Entry>> first = pool.submit(() -> {
                start.await();
                return nodeA.post(TX, postings);
            });
            Future<List<Entry>> second = pool.submit(() -> {
                start.await();
                return nodeB.post(TX, postings);
            });
            start.countDown();

            List<Entry> a = first.get(15, TimeUnit.SECONDS);
            List<Entry> b = second.get(15, TimeUnit.SECONDS);

            assertThat(entries.findAll())
                    .as("one transaction reference must be one journal, even when two callers race for it")
                    .hasSize(2);
            assertThat(a)
                    .as("the winning caller gets the journal it wrote")
                    .hasSize(2);
            assertThat(b)
                    .as("the losing caller must read the recorded journal instead of writing a second one")
                    .hasSize(2);
            assertThat(entries.findAll().stream().map(Entry::sequence).toList())
                    .as("the two callers must be looking at the same two rows")
                    .containsExactlyInAnyOrder(a.get(0).sequence(), a.get(1).sequence());
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * Shared journal with a hook that lets both readers see "no such transaction" before either one
     * appends. Extends the in-memory store so extra repository methods keep compiling.
     */
    static final class GatedLedgerRepository extends InMemoryLedgerRepository {

        private final RaceGate gate = new RaceGate(2_000);

        @Override
        public boolean hasTransaction(String transactionRef) {
            boolean alreadyThere = super.hasTransaction(transactionRef);
            if (TX.equals(transactionRef)) {
                gate.meet();
            }
            return alreadyThere;
        }
    }
}
