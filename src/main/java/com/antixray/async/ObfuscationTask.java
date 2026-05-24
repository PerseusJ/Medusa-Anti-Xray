package com.antixray.async;

import com.antixray.cache.CacheEntry;
import com.antixray.cache.CacheKey;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class ObfuscationTask implements Comparable<ObfuscationTask>, Runnable {

    private final AsyncProcessor processor;

    public enum Priority {
        CRITICAL(0),
        HIGH(1),
        MEDIUM(2),
        LOW(3);

        private final int level;

        Priority(int level) {
            this.level = level;
        }

        public int getLevel() {
            return level;
        }
    }

    private final CacheKey key;
    private final Priority priority;
    private final Consumer<CacheEntry> callback;
    private final long enqueueTime;
    private final AtomicBoolean cancelled;

    public ObfuscationTask(CacheKey key, Priority priority, Consumer<CacheEntry> callback, AsyncProcessor processor) {
        this.key = key;
        this.priority = priority;
        this.callback = callback;
        this.enqueueTime = System.nanoTime();
        this.cancelled = new AtomicBoolean(false);
        this.processor = processor;
    }

    public ObfuscationTask(CacheKey key, Priority priority, Consumer<CacheEntry> callback) {
        this(key, priority, callback, null);
    }

    @Override
    public void run() {
        if (processor != null) {
            processor.processTask(this);
        }
    }

    public CacheKey getKey() {
        return key;
    }

    public Priority getPriority() {
        return priority;
    }

    public Consumer<CacheEntry> getCallback() {
        return callback;
    }

    public long getEnqueueTime() {
        return enqueueTime;
    }

    public long getWaitTimeMs() {
        return (System.nanoTime() - enqueueTime) / 1_000_000;
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public boolean cancel() {
        return cancelled.compareAndSet(false, true);
    }

    @Override
    public int compareTo(ObfuscationTask other) {
        int cmp = Integer.compare(this.priority.level, other.priority.level);
        if (cmp != 0) return cmp;
        return Long.compare(this.enqueueTime, other.enqueueTime);
    }
}
