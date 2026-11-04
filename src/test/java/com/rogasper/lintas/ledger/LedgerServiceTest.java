package com.rogasper.lintas.ledger;

import com.rogasper.lintas.shared.Currency;
import com.rogasper.lintas.shared.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Drills 1-4. All of these are red until {@link LedgerService} is implemented.
 * Do not change the assertions — the point is that the rules are fixed before the code exists.
 */
class LedgerServiceTest {

    private InMemoryAccountRepository accounts;
    private InMemoryLedgerRepository entries;
    private LedgerService ledger;

    private Account customer;
    private Account partner;
    private Account eurAccount;

    @BeforeEach
    void setUp() {
        accounts = new InMemoryAccountRepository();
        entries = new InMemoryLedgerRepository();
        ledger = new LedgerService(accounts, entries);
        customer = accounts.save(new Account("ACC-CUST-1", "CUST-1", AccountType.CUSTOMER, Currency.USD));
        partner = accounts.save(new Account("ACC-PART-1", "PARTNER-1", AccountType.PARTNER, Currency.USD));
        eurAccount = accounts.save(new Account("ACC-EUR-1", "CUST-1", AccountType.CUSTOMER, Currency.EUR));
    }

    private static Money usd(String amount) {
        return Money.parse(amount, Currency.USD);
    }

    @Test
    @DisplayName("drill 1: a balanced posting is recorded once and moves both balances")
    void drill01_balancedPostingUpdatesBalances() {
        List<Entry> posted = ledger.post("TX-1", List.of(
                new Posting(customer.id(), EntryDirection.CREDIT, usd("100.00")),
                new Posting(partner.id(), EntryDirection.DEBIT, usd("100.00"))));

        assertThat(posted).hasSize(2);
        assertThat(entries.findByTransaction("TX-1")).hasSize(2);
        assertThat(ledger.balanceOf(customer.id())).isEqualTo(usd("100.00"));
        assertThat(ledger.balanceOf(partner.id())).isEqualTo(usd("-100.00"));
    }

    @Test
    @DisplayName("drill 2: an unbalanced posting is refused and writes nothing")
    void drill02_unbalancedPostingIsRefused() {
        assertThatThrownBy(() -> ledger.post("TX-2", List.of(
                new Posting(customer.id(), EntryDirection.CREDIT, usd("100.00")),
                new Posting(partner.id(), EntryDirection.DEBIT, usd("90.00")))))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(entries.findAll()).isEmpty();
        assertThat(ledger.balanceOf(customer.id())).isEqualTo(usd("0.00"));
    }

    @Test
    @DisplayName("drill 3: a posting that mixes currencies is refused")
    void drill03_mixedCurrencyPostingIsRefused() {
        assertThatThrownBy(() -> ledger.post("TX-3", List.of(
                new Posting(customer.id(), EntryDirection.CREDIT, usd("100.00")),
                new Posting(eurAccount.id(), EntryDirection.DEBIT, Money.parse("100.00", Currency.EUR)))))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(entries.findAll()).isEmpty();
    }

    @Test
    @DisplayName("drill 4: replaying the same transaction reference does not post twice")
    void drill04_duplicateTransactionReferenceIsIdempotent() {
        List<Posting> postings = List.of(
                new Posting(customer.id(), EntryDirection.CREDIT, usd("100.00")),
                new Posting(partner.id(), EntryDirection.DEBIT, usd("100.00")));

        List<Entry> first = ledger.post("TX-4", postings);
        List<Entry> replay = ledger.post("TX-4", postings);

        assertThat(replay).isEqualTo(first);
        assertThat(entries.findByTransaction("TX-4")).hasSize(2);
        assertThat(entries.findAll()).hasSize(2);
        assertThat(ledger.balanceOf(customer.id())).isEqualTo(usd("100.00"));
    }
}
