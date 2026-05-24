package com.antixray.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.concurrent.TimeUnit;

public class L1MemoryCache {

    private final Cache<CacheKey, CacheEntry> cache;
    private final long maxSize;
    private final long expirySeconds;

    public L1MemoryCache(long maxSize, long expirySeconds) {
        this.maxSize = maxSize;
        this.expirySeconds = expirySeconds;
        this.cache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(expirySeconds, TimeUnit.SECONDS)
                .recordStats()
                .build();
    }

    public CacheEntry get(CacheKey key) {
        return cache.getIfPresent(key);
    }

    public void put(CacheKey key, CacheEntry entry) {
        cache.put(key, entry);
    }

    public void invalidate(CacheKey key) {
        cache.invalidate(key);
    }

    public void invalidateAll() {
        cache.invalidateAll();
    }

    public long size() {
        return cache.estimatedSize();
    }

    public double hitRate() {
        var stats = cache.stats();
        return stats.hitRate();
    }

    public long getMaxSize() {
        return maxSize;
    }

    public long getExpirySeconds() {
        return expirySeconds;
    }

    public void cleanUp() {
        cache.cleanUp();
    }
}
