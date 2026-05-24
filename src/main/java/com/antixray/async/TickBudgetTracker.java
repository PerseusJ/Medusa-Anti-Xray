package com.antixray.async;

import java.util.concurrent.atomic.AtomicLong;

public class TickBudgetTracker {

    private final long perTickBudgetMs;
    private final AtomicLong cumulativeTimeNs;
    private volatile long tickStartNanos;

    public TickBudgetTracker(long perTickBudgetMs) {
        this.perTickBudgetMs = perTickBudgetMs;
        this.cumulativeTimeNs = new AtomicLong(0);
        this.tickStartNanos = System.nanoTime();
    }

    public void onTickStart() {
        cumulativeTimeNs.set(0);
        tickStartNanos = System.nanoTime();
    }

    public boolean canProcessMore() {
        if (perTickBudgetMs <= 0) return false;
        return cumulativeTimeNs.get() <= perTickBudgetMs * 1_000_000;
    }

    public void recordProcessingTime(long ms) {
        cumulativeTimeNs.addAndGet(ms * 1_000_000);
    }

    public void recordProcessingTimeNs(long ns) {
        cumulativeTimeNs.addAndGet(ns);
    }

    public long getCumulativeTimeMs() {
        return cumulativeTimeNs.get() / 1_000_000;
    }

    public long getPerTickBudgetMs() {
        return perTickBudgetMs;
    }

    public long getRemainingBudgetMs() {
        long used = cumulativeTimeNs.get() / 1_000_000;
        return Math.max(0, perTickBudgetMs - used);
    }

    public long getTickStartNanos() {
        return tickStartNanos;
    }
}
