package com.antixray.async;

import com.antixray.cache.CacheEntry;
import com.antixray.cache.CacheKey;
import com.antixray.cache.ObfuscationCache;
import com.antixray.config.WorldConfig;
import com.antixray.engine.ObfuscationMode;

import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ChunkPreObfuscator {

    private static final Logger LOGGER = Logger.getLogger("AntiXray");

    private final AsyncProcessor asyncProcessor;
    private final ObfuscationCache cache;
    private final int configHash;

    private final AtomicLong tasksEnqueued = new AtomicLong(0);
    private final AtomicLong tasksSkippedCacheHit = new AtomicLong(0);
    private final AtomicLong tasksSkippedDisabled = new AtomicLong(0);

    public ChunkPreObfuscator(AsyncProcessor asyncProcessor, ObfuscationCache cache, int configHash) {
        this.asyncProcessor = asyncProcessor;
        this.cache = cache;
        this.configHash = configHash;
    }

    public boolean enqueuePreObfuscation(String worldName, int chunkX, int chunkZ, WorldConfig worldConfig) {
        if (asyncProcessor == null || cache == null) return false;

        if (!worldConfig.isEnabled()) {
            tasksSkippedDisabled.incrementAndGet();
            return false;
        }

        ObfuscationMode mode = worldConfig.getEngineMode();
        CacheKey key = new CacheKey(worldName, chunkX, chunkZ, mode, configHash);

        CacheEntry existing = cache.get(key);
        if (existing != null) {
            tasksSkippedCacheHit.incrementAndGet();
            return false;
        }

        ObfuscationTask task = new ObfuscationTask(key, ObfuscationTask.Priority.MEDIUM, null, asyncProcessor);
        asyncProcessor.enqueue(task);
        tasksEnqueued.incrementAndGet();

        LOGGER.log(Level.FINE,
                "Pre-obfuscation enqueued: {0}, chunk [{1}, {2}]",
                new Object[]{worldName, chunkX, chunkZ});

        return true;
    }

    public long getTasksEnqueued() {
        return tasksEnqueued.get();
    }

    public long getTasksSkippedCacheHit() {
        return tasksSkippedCacheHit.get();
    }

    public long getTasksSkippedDisabled() {
        return tasksSkippedDisabled.get();
    }

    public void resetStats() {
        tasksEnqueued.set(0);
        tasksSkippedCacheHit.set(0);
        tasksSkippedDisabled.set(0);
    }
}
