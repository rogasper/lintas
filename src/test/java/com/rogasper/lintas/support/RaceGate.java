package com.rogasper.lintas.support;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Test-only rendezvous point that makes a check-then-act race reproducible.
 *
 * <p>Two threads meet here: each one waits for the other, but only up to {@code timeoutMillis}.
 * That timeout matters. If the production code serialises the two threads (a lock, a transaction,
 * a unique index), the first thread gives up waiting and carries on — so a correct implementation
 * goes green slowly instead of hanging the build forever.
 *
 * <p>Deliberately not a {@code CyclicBarrier}: a barrier would deadlock the moment somebody
 * "fixes" a race by putting a lock around it, and a deadlocked build teaches nothing.
 */
public final class RaceGate {

    private final CountDownLatch latch = new CountDownLatch(2);
    private final ThreadLocal<Boolean> alreadyMet = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private final long timeoutMillis;

    public RaceGate(long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    /** Waits (briefly) for the other thread to reach the same point. Safe to call more than once. */
    public void meet() {
        if (alreadyMet.get()) {
            return;
        }
        alreadyMet.set(Boolean.TRUE);
        latch.countDown();
        try {
            latch.await(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
