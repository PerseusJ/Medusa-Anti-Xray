package com.antixray.async;

import com.antixray.cache.CacheEntry;
import com.antixray.cache.CacheKey;
import com.antixray.cache.ObfuscationCache;
import com.antixray.engine.ObfuscationEngine;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AsyncProcessor {

    private static final Logger LOGGER = Logger.getLogger("AntiXray");

    private final ThreadPoolManager threadPoolManager;
    private final TickBudgetTracker tickBudgetTracker;
    private final BackpressureHandler backpressureHandler;
    private final ObfuscationCache cache;
    private final long chunkTimeoutMs;

    private Function<CacheKey, CacheEntry> obfuscationFunction;

    private final AtomicLong tasksCompleted = new AtomicLong(0);
    private final AtomicLong tasksTimedOut = new AtomicLong(0);
    private final AtomicLong tasksCancelled = new AtomicLong(0);
    private final AtomicLong tasksCacheHit = new AtomicLong(0);
    private final AtomicLong totalWaitTimeNs = new AtomicLong(0);
    private final AtomicLong totalProcessTimeNs = new AtomicLong(0);

    public AsyncProcessor(ThreadPoolManager threadPoolManager,
                          TickBudgetTracker tickBudgetTracker,
                          BackpressureHandler backpressureHandler,
                          ObfuscationCache cache,
                          long chunkTimeoutMs) {
        this.threadPoolManager = threadPoolManager;
        this.tickBudgetTracker = tickBudgetTracker;
        this.backpressureHandler = backpressureHandler;
        this.cache = cache;
        this.chunkTimeoutMs = chunkTimeoutMs;
    }

    public void setObfuscationFunction(Function<CacheKey, CacheEntry> fn) {
        this.obfuscationFunction = fn;
    }

    public void enqueue(ObfuscationTask task) {
        if (backpressureHandler.isCircuitOpen() && !task.isCancelled()) {
            if (!backpressureHandler.shouldProcessTask(task.getPriority())) {
                task.cancel();
                tasksCancelled.incrementAndGet();
                return;
            }
        }

        if (backpressureHandler.shouldSendUnobfuscated()
                && task.getPriority() != ObfuscationTask.Priority.CRITICAL) {
            task.cancel();
            tasksCancelled.incrementAndGet();
            return;
        }

        if (backpressureHandler.shouldCancelMediumPriority()
                && task.getPriority() == ObfuscationTask.Priority.MEDIUM) {
            if (task.cancel()) {
                tasksCancelled.incrementAndGet();
                return;
            }
        }

        if (backpressureHandler.shouldCancelLowPriority()
                && task.getPriority() == ObfuscationTask.Priority.LOW) {
            if (task.cancel()) {
                tasksCancelled.incrementAndGet();
                return;
            }
        }

        threadPoolManager.execute(task);
    }

    public void processTask(ObfuscationTask task) {
        if (task.isCancelled()) {
            tasksCancelled.incrementAndGet();
            return;
        }

        if (task.getPriority() == ObfuscationTask.Priority.CRITICAL) {
            if (!tickBudgetTracker.canProcessMore()) {
                task.cancel();
                tasksCancelled.incrementAndGet();
                return;
            }
        }

        long waitTimeNs = System.nanoTime() - task.getEnqueueTime();
        if (task.getWaitTimeMs() > chunkTimeoutMs) {
            task.cancel();
            tasksTimedOut.incrementAndGet();
            LOGGER.log(Level.FINE, "Task timed out after {0}ms: {1}",
                    new Object[]{task.getWaitTimeMs(), task.getKey()});
            return;
        }

        CacheKey key = task.getKey();

        CacheEntry cached = cache.get(key);
        if (cached != null) {
            tasksCacheHit.incrementAndGet();
            totalWaitTimeNs.addAndGet(waitTimeNs);
            if (task.getCallback() != null) {
                task.getCallback().accept(cached);
            }
            return;
        }

        if (obfuscationFunction == null) {
            LOGGER.log(Level.WARNING, "No obfuscation function set, skipping task: {0}", key);
            return;
        }

        long processStart = System.nanoTime();
        CacheEntry result;
        try {
            result = obfuscationFunction.apply(key);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Obfuscation failed for " + key, e);
            return;
        }
        long processTimeNs = System.nanoTime() - processStart;

        if (task.getPriority() == ObfuscationTask.Priority.CRITICAL) {
            tickBudgetTracker.recordProcessingTimeNs(processTimeNs);
        }

        if (result != null) {
            cache.put(key, result);
        }

        totalWaitTimeNs.addAndGet(waitTimeNs);
        totalProcessTimeNs.addAndGet(processTimeNs);
        tasksCompleted.incrementAndGet();

        if (task.getCallback() != null) {
            task.getCallback().accept(result);
        }
    }

    public void onTickStart() {
        tickBudgetTracker.onTickStart();
        backpressureHandler.checkAndApply();
    }

    public void onTickEnd() {
        backpressureHandler.onTickEnd();
    }

    public ThreadPoolManager getThreadPoolManager() {
        return threadPoolManager;
    }

    public TickBudgetTracker getTickBudgetTracker() {
        return tickBudgetTracker;
    }

    public BackpressureHandler getBackpressureHandler() {
        return backpressureHandler;
    }

    public ObfuscationCache getCache() {
        return cache;
    }

    public long getTasksCompleted() {
        return tasksCompleted.get();
    }

    public long getTasksTimedOut() {
        return tasksTimedOut.get();
    }

    public long getTasksCancelled() {
        return tasksCancelled.get();
    }

    public long getTasksCacheHit() {
        return tasksCacheHit.get();
    }

    public double getAverageWaitTimeMs() {
        long completed = tasksCompleted.get() + tasksCacheHit.get();
        if (completed == 0) return 0.0;
        return (totalWaitTimeNs.get() / 1_000_000.0) / completed;
    }

    public double getAverageProcessTimeMs() {
        long completed = tasksCompleted.get();
        if (completed == 0) return 0.0;
        return (totalProcessTimeNs.get() / 1_000_000.0) / completed;
    }

    public void shutdown() {
        threadPoolManager.shutdown();
        cache.shutdown();
    }

    public boolean awaitTermination(long timeoutMs) throws InterruptedException {
        return threadPoolManager.awaitTermination(timeoutMs);
    }
}
