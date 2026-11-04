package com.rogasper.lintas.recon;

import com.rogasper.lintas.transfer.TransferRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Compares our side with the partner statement. Reconciliation is not reporting: it is the control
 * that catches every bug this repo's drills are about, because a duplicated debit or a missed
 * callback eventually shows up as a difference here.
 */
@Service
public class ReconService {

    private final TransferRepository transfers;

    public ReconService(TransferRepository transfers) {
        this.transfers = transfers;
    }

    /**
     * @param statement the partner's settlement lines for the same period
     * @return every difference, or a clean result
     */
    public ReconResult reconcile(List<PartnerStatementLine> statement) {
        throw new UnsupportedOperationException(
                "TODO drill 11: implement ReconService.reconcile (missing on either side + amount mismatch)");
    }
}
