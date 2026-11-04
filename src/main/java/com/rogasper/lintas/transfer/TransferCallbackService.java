package com.rogasper.lintas.transfer;

import com.rogasper.lintas.ledger.LedgerService;
import com.rogasper.lintas.partner.PartnerCallback;
import org.springframework.stereotype.Service;

import java.time.Clock;

/**
 * Applies partner callbacks to a transfer.
 *
 * <p>Partners retry callbacks, deliver them out of order, and sometimes deliver the same event
 * twice with a different timestamp. The contract:
 * <ul>
 *   <li>the same event applied twice changes nothing;</li>
 *   <li>an event older than {@code lastPartnerSequence} is ignored;</li>
 *   <li>the final status never goes backwards (PAID cannot become ACCEPTED);</li>
 *   <li>no ledger entry is posted twice.</li>
 * </ul>
 */
@Service
public class TransferCallbackService {

    private final TransferRepository transfers;
    private final LedgerService ledger;
    private final Clock clock;

    public TransferCallbackService(TransferRepository transfers, LedgerService ledger, Clock clock) {
        this.transfers = transfers;
        this.ledger = ledger;
        this.clock = clock;
    }

    public Transfer handle(PartnerCallback callback) {
        throw new UnsupportedOperationException(
                "TODO drill 7-8: implement TransferCallbackService.handle (dedupe + out-of-order + no double posting)");
    }
}
