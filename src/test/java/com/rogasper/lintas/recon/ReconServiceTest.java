package com.rogasper.lintas.recon;

import com.rogasper.lintas.partner.PartnerStatus;
import com.rogasper.lintas.shared.Currency;
import com.rogasper.lintas.shared.Money;
import com.rogasper.lintas.transfer.InMemoryTransferRepository;
import com.rogasper.lintas.transfer.Transfer;
import com.rogasper.lintas.transfer.TransferStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Drills 12-13: reconciliation as the control that catches everything above. */
class ReconServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-04T09:00:00Z");

    private InMemoryTransferRepository transfers;
    private ReconService recon;

    @BeforeEach
    void setUp() {
        transfers = new InMemoryTransferRepository();
        recon = new ReconService(transfers);
    }

    private static Money usd(String amount) {
        return Money.parse(amount, Currency.USD);
    }

    private static Money idr(String amount) {
        return Money.parse(amount, Currency.IDR);
    }

    private void paidTransfer(String id, String partnerRef, String targetAmount) {
        transfers.save(Transfer.pending(id, "Q-" + id, "IDEM-" + id, usd("100.00"), idr(targetAmount), NOW)
                .withStatus(TransferStatus.PAID)
                .withPartnerRef(partnerRef));
    }

    @Test
    @DisplayName("drill 12: recon reports what is missing on either side and what does not add up")
    void drill12_reconFlagsEveryDifference() {
        paidTransfer("TRF-1", "P-1", "1580000");
        paidTransfer("TRF-2", "P-2", "1580000");
        paidTransfer("TRF-3", "P-3", "1580000");

        List<PartnerStatementLine> statement = List.of(
                new PartnerStatementLine("P-1", "TRF-1", idr("1580000"), PartnerStatus.PAID),
                new PartnerStatementLine("P-2", "TRF-2", idr("1600000"), PartnerStatus.PAID),
                new PartnerStatementLine("P-4", "TRF-4", idr("1580000"), PartnerStatus.PAID));

        ReconResult result = recon.reconcile(statement);

        assertThat(result.isClean()).isFalse();
        assertThat(result.onlyInLedger()).containsExactly("TRF-3");
        assertThat(result.onlyAtPartner()).containsExactly("P-4");
        assertThat(result.amountMismatch()).containsExactly("TRF-2");
    }

    @Test
    @DisplayName("drill 13: recon is clean when both sides agree")
    void drill13_reconIsCleanWhenBothSidesAgree() {
        paidTransfer("TRF-1", "P-1", "1580000");
        paidTransfer("TRF-2", "P-2", "1580000");

        List<PartnerStatementLine> statement = List.of(
                new PartnerStatementLine("P-1", "TRF-1", idr("1580000"), PartnerStatus.PAID),
                new PartnerStatementLine("P-2", "TRF-2", idr("1580000"), PartnerStatus.PAID));

        ReconResult result = recon.reconcile(statement);

        assertThat(result.isClean()).isTrue();
    }
}
