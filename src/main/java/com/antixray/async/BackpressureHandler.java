package com.antixray.async;

import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BackpressureHandler {

    private static final Logger LOGGER = Logger.getLogger("AntiXray");

    private final int maxQueueSize;
    private final ThreadPoolManager threadPoolManager;

    private final AtomicInteger consecutiveHighFillTicks = new AtomicInteger(0);
    private final AtomicBoolean circuitOpen = new AtomicBoolean(false);
    private final AtomicLong circuitOpenTime = new AtomicLong(0);
    private final AtomicInteger circuitTripCount = new AtomicInteger(0);
    private final AtomicLong firstTripInWindow = new AtomicLong(0);
    private final AtomicBoolean permanentlyOpen = new AtomicBoolean(false);
    private final ConcurrentHashMap<Long, Boolean> passDecisions = new ConcurrentHashMap<>();

    private static final double THRESHOLD_LOW_CANCEL = 0.75;
    private static final double THRESHOLD_MEDIUM_CANCEL = 0.90;
    private static final double THRESHOLD_SEND_UNOBFUSCATED = 0.95;
    private static final double THRESHOLD_SEVERE_WARNING = 0.98;
    private static final int SEVERE_WARNING_CONSECUTIVE_TICKS = 10;

    private static final long CIRCUIT_REOPEN_50_MS = 5_000;
    private static final long CIRCUIT_REOPEN_75_MS = 10_000;
    private static final long CIRCUIT_FULL_REOPEN_MS = 15_000;

    private static final int MAX_CIRCUIT_TRIPS = 3;
    private static final long TRIP_WINDOW_MS = 60_000;

    public BackpressureHandler(int maxQueueSize, ThreadPoolManager threadPoolManager) {
        this.maxQueueSize = maxQueueSize;
        this.threadPoolManager = threadPoolManager;
    }

    public double getQueueFillRatio() {
        if (maxQueueSize <= 0) return 1.0;
        return (double) threadPoolManager.getQueueSize() / maxQueueSize;
    }

    public boolean shouldCancelLowPriority() {
        return getQueueFillRatio() > THRESHOLD_LOW_CANCEL;
    }

    public boolean shouldCancelMediumPriority() {
        return getQueueFillRatio() > THRESHOLD_MEDIUM_CANCEL;
    }

    public boolean shouldSendUnobfuscated() {
        return getQueueFillRatio() > THRESHOLD_SEND_UNOBFUSCATED;
    }

    public boolean isCircuitOpen() {
        if (permanentlyOpen.get()) return true;
        return circuitOpen.get();
    }

    public boolean shouldProcessTask(ObfuscationTask.Priority priority) {
        if (permanentlyOpen.get()) return false;
        if (!circuitOpen.get()) return true;

        if (priority == ObfuscationTask.Priority.CRITICAL) return true;

        long elapsed = System.currentTimeMillis() - circuitOpenTime.get();

        if (elapsed >= CIRCUIT_FULL_REOPEN_MS) {
            closeCircuit();
            return true;
        }

        double passRate;
        if (elapsed < CIRCUIT_REOPEN_50_MS) {
            passRate = 0.0;
        } else if (elapsed < CIRCUIT_REOPEN_75_MS) {
            passRate = 0.50;
        } else {
            passRate = 0.75;
        }

        long threadId = Thread.currentThread().getId();
        Boolean previous = passDecisions.get(threadId);
        if (previous != null) return previous;

        boolean pass = Math.random() < passRate;
        passDecisions.put(threadId, pass);
        return pass;
    }

    public void checkAndApply() {
        double fillRatio = getQueueFillRatio();

        if (shouldCancelLowPriority()) {
            cancelTasksByPriority(ObfuscationTask.Priority.LOW);
        }

        if (shouldCancelMediumPriority()) {
            cancelTasksByPriority(ObfuscationTask.Priority.MEDIUM);
        }

        if (fillRatio > THRESHOLD_SEVERE_WARNING) {
            int consecutive = consecutiveHighFillTicks.incrementAndGet();
            if (consecutive >= SEVERE_WARNING_CONSECUTIVE_TICKS) {
                LOGGER.log(Level.SEVERE,
                        "AntiXray queue >98% full for {0} consecutive ticks! "
                        + "Consider reducing render distance. Queue: {1}/{2}",
                        new Object[]{consecutive, threadPoolManager.getQueueSize(), maxQueueSize});
            }
        } else {
            consecutiveHighFillTicks.set(0);
        }

        if (threadPoolManager.getQueueSize() >= maxQueueSize && maxQueueSize > 0) {
            openCircuit();
        }
    }

    public void onTickEnd() {
        passDecisions.clear();
    }

    private void cancelTasksByPriority(ObfuscationTask.Priority priority) {
        Iterator<Runnable> it = threadPoolManager.getQueue().iterator();
        int cancelled = 0;
        while (it.hasNext()) {
            Runnable r = it.next();
            if (r instanceof ObfuscationTask task) {
                if (task.getPriority() == priority && task.cancel()) {
                    threadPoolManager.getQueue().remove(r);
                    cancelled++;
                }
            }
        }
        if (cancelled > 0) {
            LOGGER.log(Level.FINE, "Backpressure: cancelled {0} {1} priority tasks",
                    new Object[]{cancelled, priority});
        }
    }

    private void openCircuit() {
        if (circuitOpen.compareAndSet(false, true)) {
            long now = System.currentTimeMillis();
            circuitOpenTime.set(now);
            passDecisions.clear();

            if (firstTripInWindow.get() == 0 || now - firstTripInWindow.get() > TRIP_WINDOW_MS) {
                firstTripInWindow.set(now);
                circuitTripCount.set(1);
            } else {
                int trips = circuitTripCount.incrementAndGet();
                if (trips >= MAX_CIRCUIT_TRIPS) {
                    permanentlyOpen.set(true);
                    LOGGER.log(Level.SEVERE,
                            "AntiXray circuit breaker tripped {0} times in 60 seconds! "
                            + "Obfuscation permanently disabled. Use /antixray toggle or restart to re-enable.",
                            trips);
                    return;
                }
            }

            LOGGER.log(Level.WARNING,
                    "AntiXray circuit breaker OPENED — queue saturated ({0}/{1}). "
                    + "Non-critical obfuscation paused for 5 seconds.",
                    new Object[]{threadPoolManager.getQueueSize(), maxQueueSize});
        }
    }

    private void closeCircuit() {
        if (circuitOpen.compareAndSet(true, false)) {
            passDecisions.clear();
            LOGGER.log(Level.INFO, "AntiXray circuit breaker CLOSED — obfuscation fully re-enabled");
        }
    }

    public void resetCircuitBreaker() {
        circuitOpen.set(false);
        permanentlyOpen.set(false);
        circuitTripCount.set(0);
        firstTripInWindow.set(0);
        circuitOpenTime.set(0);
        passDecisions.clear();
        consecutiveHighFillTicks.set(0);
        LOGGER.log(Level.INFO, "AntiXray circuit breaker manually reset");
    }

    public int getConsecutiveHighFillTicks() {
        return consecutiveHighFillTicks.get();
    }

    public int getCircuitTripCount() {
        return circuitTripCount.get();
    }

    public boolean isPermanentlyOpen() {
        return permanentlyOpen.get();
    }

    public int getMaxQueueSize() {
        return maxQueueSize;
    }
}
