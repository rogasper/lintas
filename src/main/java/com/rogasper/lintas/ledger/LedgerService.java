package com.rogasper.lintas.ledger;

import com.rogasper.lintas.shared.Money;
import org.springframework.stereotype.Service;
import com.rogasper.lintas.shared.Currency;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Double-entry posting rules.
 *
 * <p>Contract this service must satisfy (see {@code LedgerServiceTest}):
 * <ul>
 *   <li>a transaction is recorded only if it is balanced per currency;</li>
 *   <li>every involved account exists and matches the posting currency;</li>
 *   <li>a transaction reference is idempotent — replaying it records nothing new;</li>
 *   <li>balances are derived from entries: credits minus debits.</li>
 * </ul>
 */
@Service
public class LedgerService {

    private final AccountRepository accounts;
    private final LedgerRepository entries;

    public LedgerService(AccountRepository accounts, LedgerRepository entries) {
        this.accounts = accounts;
        this.entries = entries;
    }

    /**
     * Records one journal transaction atomically.
     *
     * @return the entries that belong to this transaction (also on replay, without re-posting)
     * @throws IllegalArgumentException when postings are unbalanced, mixed-currency, or refer to an
     *                                  unknown account
     */
    public List<Entry> post(String transactionRef, List<Posting> postings) {
        if(entries.hasTransaction(transactionRef)){
            return entries.findByTransaction(transactionRef);
        }
        if(postings == null || postings.isEmpty()){
            throw new IllegalArgumentException("Postings cannot be empty");
        }

        Currency txCurrency = null;
        long creditSum = 0L;
        long debitSum = 0L;

        for (Posting posting : postings){
            Account account = accounts.findById(posting.accountId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown account: " + posting.accountId()));

            Currency postingCurrency = posting.amount().currency();

            if (account.currency() != postingCurrency) {
                throw new IllegalArgumentException("Currency mismatch for account %s: expected %s but got %s".formatted(account.id(), account.currency(), postingCurrency));
            }

            if(txCurrency == null){
                txCurrency = postingCurrency;
            } else if (txCurrency != postingCurrency) {
                throw new IllegalArgumentException("Mixed currency in transaction: %s vs %s".formatted(txCurrency, postingCurrency));
            }

            if (posting.direction() == EntryDirection.CREDIT) {
                creditSum += posting.amount().minorUnits();
            } else if (posting.direction() == EntryDirection.DEBIT) {
                debitSum += posting.amount().minorUnits();
            }
        }

        if (creditSum != debitSum) {
            throw new IllegalArgumentException("Unbalanced posting: credit=" + creditSum + ", debit=" + debitSum);
        }

        Instant recordedAt = Instant.now();
        List<Entry> recorded = new ArrayList<>();
        for (Posting posting : postings) {
            recorded.add(entries.append(transactionRef, posting, recordedAt));
        }
        return recorded;
    }

    /** Credits minus debits for the account, in the account's currency. */
    public Money balanceOf(String accountId) {
        Account account = accounts.findById(accountId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown account: " + accountId));

        Money balance = Money.zero(account.currency());
        for (Entry entry : entries.findByAccount(accountId)){
            if(entry.direction() == EntryDirection.CREDIT){
                balance = balance.plus(entry.posting().amount());
            } else if(entry.direction() == EntryDirection.DEBIT){
                balance = balance.minus(entry.posting().amount());
            }
        }

        return balance;
    }

    public List<Entry> entriesOf(String accountId) {
        return entries.findByAccount(accountId);
    }
}
