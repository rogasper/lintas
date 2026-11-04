package com.rogasper.lintas.ledger;

import com.rogasper.lintas.shared.Money;
import org.springframework.stereotype.Service;

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
        throw new UnsupportedOperationException(
                "TODO drill 1-4: implement LedgerService.post (balance check, currency check, idempotency)");
    }

    /** Credits minus debits for the account, in the account's currency. */
    public Money balanceOf(String accountId) {
        throw new UnsupportedOperationException(
                "TODO drill 1: implement LedgerService.balanceOf from the journal, never from a cached field");
    }

    public List<Entry> entriesOf(String accountId) {
        return entries.findByAccount(accountId);
    }
}
