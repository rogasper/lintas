package com.rogasper.lintas.recon;

import com.rogasper.lintas.partner.PartnerStatus;
import com.rogasper.lintas.transfer.Transfer;
import com.rogasper.lintas.transfer.TransferRepository;
import com.rogasper.lintas.transfer.TransferStatus;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

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
        Map<String, Transfer> oursById = new TreeMap<>();
        for (Transfer transfer : transfers.findAll()) {
            oursById.put(transfer.id(), transfer);
        }

        Set<String> onlyInLedger = new TreeSet<>();
        Set<String> onlyAtPartner  = new TreeSet<>();
        Set<String> amountMismatch = new TreeSet<>();
        Set<String> statusMismatch = new TreeSet<>();
        Set<String> matchedIds = new HashSet<>();

        for (PartnerStatementLine line : statement) {
            Transfer ours = oursById.get(line.transferId());

            if (ours == null) {
                onlyAtPartner.add(line.partnerRef());
                continue;
            }

            matchedIds.add(ours.id());

            if (!ours.targetAmount().equals(line.amount())) {
                amountMismatch.add(ours.id());
            }
            if (ours.status() != toTransferStatus(line.status())) {
                statusMismatch.add(ours.id());
            }
        }

        for (Transfer transfer : oursById.values()) {
            if (!matchedIds.contains(transfer.id())) {
                onlyInLedger.add(transfer.id());
            }
        }

        return new ReconResult(
            new ArrayList<>(onlyInLedger),
            new ArrayList<>(onlyAtPartner),
            new ArrayList<>(amountMismatch),
            new ArrayList<>(statusMismatch)
        );
    }

    private static TransferStatus toTransferStatus(PartnerStatus status){
        return switch(status){
            case PENDING -> TransferStatus.PENDING_PARTNER;
            case ACCEPTED -> TransferStatus.ACCEPTED;
            case PAID -> TransferStatus.PAID;
            case FAILED -> TransferStatus.FAILED;
        };
    }
}
