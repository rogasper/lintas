package com.rogasper.lintas.partner;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Scriptable partner stub. Tests queue the outcomes they want; anything left over is accepted.
 *
 * <p>Not a mock framework on purpose: the interesting failures (timeout, rejection, late
 * duplicate) are exactly what you cannot express with "return this value once".
 */
@Component
public class FakePartnerClient implements PartnerClient {

    private final Queue<PartnerResult> scripted = new ConcurrentLinkedQueue<>();
    private final AtomicInteger submissions = new AtomicInteger();

    /** Queue outcomes for the next submissions, in order. */
    public FakePartnerClient script(PartnerResult... results) {
        scripted.addAll(List.of(results));
        return this;
    }

    public int submissions() {
        return submissions.get();
    }

    @Override
    public PartnerResult submit(PartnerRequest request) {
        submissions.incrementAndGet();
        PartnerResult next = scripted.poll();
        if (next != null) {
            return next;
        }
        return PartnerResult.accepted("P-" + request.transferId());
    }
}
