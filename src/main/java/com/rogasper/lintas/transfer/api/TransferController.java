package com.rogasper.lintas.transfer.api;

import com.rogasper.lintas.shared.Currency;
import com.rogasper.lintas.shared.Money;
import com.rogasper.lintas.transfer.Quote;
import com.rogasper.lintas.transfer.Transfer;
import com.rogasper.lintas.transfer.TransferService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Duration;

/** Thin HTTP layer. All interesting logic lives in {@link TransferService}. */
@RestController
@RequestMapping("/api")
public class TransferController {

    private final TransferService transfers;

    public TransferController(TransferService transfers) {
        this.transfers = transfers;
    }

    public record QuoteRequest(
            String customerRef,
            BigDecimal sourceAmount,
            String sourceCurrency,
            String targetCurrency,
            BigDecimal rate,
            BigDecimal fee,
            long ttlSeconds) {
    }

    public record TransferRequest(String quoteId) {
    }

    @PostMapping("/quotes")
    public Quote createQuote(@RequestBody QuoteRequest request) {
        Currency source = Currency.valueOf(request.sourceCurrency());
        Currency target = Currency.valueOf(request.targetCurrency());
        return transfers.createQuote(
                request.customerRef(),
                Money.ofMajor(request.sourceAmount(), source),
                target,
                request.rate(),
                Money.ofMajor(request.fee(), source),
                Duration.ofSeconds(request.ttlSeconds()));
    }

    /** Idempotency-Key is required, not optional: retries are normal, duplicates are not. */
    @PostMapping("/transfers")
    public Transfer execute(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody TransferRequest request) {
        return transfers.execute(idempotencyKey, request.quoteId());
    }

    @GetMapping("/transfers/{id}")
    public Transfer find(@PathVariable String id) {
        return transfers.find(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown transfer " + id));
    }
}
