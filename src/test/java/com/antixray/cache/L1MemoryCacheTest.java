package com.antixray.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class L1MemoryCacheTest {

    private L1MemoryCache cache;

    @BeforeEach
    void setUp() {
        cache = new L1MemoryCache(100, 300);
    }

    @Test
    void put_andGet_returnsEntry() {
        CacheKey key = new CacheKey("world", 1, 2, 3, 100);
        CacheEntry entry = new CacheEntry(new byte[]{1, 2, 3}, 1);
        cache.put(key, entry);
        CacheEntry retrieved = cache.get(key);
        assertNotNull(retrieved);
        assertArrayEquals(new byte[]{1, 2, 3}, retrieved.getObfuscatedData());
        assertEquals(1, retrieved.getSectionCount());
    }

    @Test
    void get_missingKey_returnsNull() {
        CacheKey key = new CacheKey("world", 99, 99, 1, 0);
        assertNull(cache.get(key));
    }

    @Test
    void invalidate_removesEntry() {
        CacheKey key = new CacheKey("world", 5, 10, 2, 500);
        cache.put(key, new CacheEntry(new byte[]{42}, 1));
        assertNotNull(cache.get(key));
        cache.invalidate(key);
        assertNull(cache.get(key));
    }

    @Test
    void invalidateAll_removesAllEntries() {
        for (int i = 0; i < 10; i++) {
            CacheKey key = new CacheKey("world", i, i, 1, 0);
            cache.put(key, new CacheEntry(new byte[]{(byte) i}, 1));
        }
        assertTrue(cache.size() >= 10);
        cache.invalidateAll();
        cache.cleanUp();
        assertEquals(0, cache.size());
    }

    @Test
    void size_reflectsNumberOfEntries() {
        assertEquals(0, cache.size());
        cache.put(new CacheKey("world", 0, 0, 1, 0), new CacheEntry(new byte[10], 1));
        cache.put(new CacheKey("world", 1, 1, 1, 0), new CacheEntry(new byte[10], 1));
        cache.cleanUp();
        assertEquals(2, cache.size());
    }

    @Test
    void lruEviction_evictsWhenExceedsMaxSize() {
        L1MemoryCache smallCache = new L1MemoryCache(5, 3600);
        for (int i = 0; i < 10; i++) {
            CacheKey key = new CacheKey("world", i, 0, 1, 0);
            smallCache.put(key, new CacheEntry(new byte[]{(byte) i}, 1));
        }
        smallCache.cleanUp();
        assertTrue(smallCache.size() <= 5);
    }

    @Test
    void timeBasedExpiry_entryExpiresAfterWriteTime() throws InterruptedException {
        L1MemoryCache shortLivedCache = new L1MemoryCache(100, 1);
        CacheKey key = new CacheKey("world", 0, 0, 1, 0);
        shortLivedCache.put(key, new CacheEntry(new byte[]{1}, 1));
        assertNotNull(shortLivedCache.get(key));
        TimeUnit.SECONDS.sleep(2);
        assertNull(shortLivedCache.get(key));
    }

    @Test
    void timeBasedExpiry_entryNotExpiredBeforeExpiry() throws InterruptedException {
        L1MemoryCache cache3s = new L1MemoryCache(100, 3);
        CacheKey key = new CacheKey("world", 0, 0, 1, 0);
        cache3s.put(key, new CacheEntry(new byte[]{1}, 1));
        TimeUnit.SECONDS.sleep(1);
        assertNotNull(cache3s.get(key));
    }

    @Test
    void put_overwritesExistingEntry() {
        CacheKey key = new CacheKey("world", 0, 0, 1, 0);
        cache.put(key, new CacheEntry(new byte[]{1}, 1));
        cache.put(key, new CacheEntry(new byte[]{2, 3}, 2));
        CacheEntry retrieved = cache.get(key);
        assertNotNull(retrieved);
        assertArrayEquals(new byte[]{2, 3}, retrieved.getObfuscatedData());
        assertEquals(2, retrieved.getSectionCount());
    }

    @Test
    void get_returnsDefensiveCopy() {
        CacheKey key = new CacheKey("world", 0, 0, 1, 0);
        byte[] data = {1, 2, 3};
        cache.put(key, new CacheEntry(data, 1));
        CacheEntry first = cache.get(key);
        CacheEntry second = cache.get(key);
        assertNotNull(first);
        assertNotNull(second);
        assertArrayEquals(first.getObfuscatedData(), second.getObfuscatedData());
        byte[] mutated = first.getObfuscatedData();
        mutated[0] = 99;
        CacheEntry third = cache.get(key);
        assertNotNull(third);
        assertEquals(1, third.getObfuscatedData()[0]);
    }

    @Test
    void differentKeys_storedSeparately() {
        CacheKey key1 = new CacheKey("world", 0, 0, 1, 0);
        CacheKey key2 = new CacheKey("world", 0, 0, 2, 0);
        cache.put(key1, new CacheEntry(new byte[]{1}, 1));
        cache.put(key2, new CacheEntry(new byte[]{2}, 1));
        CacheEntry e1 = cache.get(key1);
        CacheEntry e2 = cache.get(key2);
        assertNotNull(e1);
        assertNotNull(e2);
        assertEquals(1, e1.getObfuscatedData()[0]);
        assertEquals(2, e2.getObfuscatedData()[0]);
    }

    @Test
    void differentConfigHash_storedSeparately() {
        CacheKey key1 = new CacheKey("world", 5, 10, 3, 100);
        CacheKey key2 = new CacheKey("world", 5, 10, 3, 200);
        cache.put(key1, new CacheEntry(new byte[]{10}, 1));
        cache.put(key2, new CacheEntry(new byte[]{20}, 1));
        CacheEntry e1 = cache.get(key1);
        CacheEntry e2 = cache.get(key2);
        assertNotNull(e1);
        assertNotNull(e2);
        assertEquals(10, e1.getObfuscatedData()[0]);
        assertEquals(20, e2.getObfuscatedData()[0]);
    }

    @Test
    void constructor_storesConfig() {
        L1MemoryCache c = new L1MemoryCache(500, 600);
        assertEquals(500, c.getMaxSize());
        assertEquals(600, c.getExpirySeconds());
    }

    @Test
    void invalidate_nonExistentKey_noException() {
        CacheKey key = new CacheKey("world", 999, 999, 1, 0);
        assertDoesNotThrow(() -> cache.invalidate(key));
    }

    @Test
    void invalidateAll_onEmptyCache_noException() {
        assertDoesNotThrow(() -> cache.invalidateAll());
    }

    @Test
    void concurrentAccess_threadSafe() throws InterruptedException {
        L1MemoryCache concurrentCache = new L1MemoryCache(1000, 300);
        int threadCount = 8;
        int opsPerThread = 500;
        Thread[] threads = new Thread[threadCount];

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            threads[t] = new Thread(() -> {
                for (int i = 0; i < opsPerThread; i++) {
                    CacheKey key = new CacheKey("world", threadId, i, 1, 0);
                    concurrentCache.put(key, new CacheEntry(new byte[]{(byte) i}, 1));
                    concurrentCache.get(key);
                    if (i % 10 == 0) {
                        concurrentCache.invalidate(key);
                    }
                }
            });
        }

        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();

        concurrentCache.cleanUp();
        assertTrue(concurrentCache.size() <= 1000);
    }

    @Test
    void lruEviction_evictsEntriesWhenOverCapacity() {
        L1MemoryCache smallCache = new L1MemoryCache(3, 3600);
        CacheKey key0 = new CacheKey("world", 0, 0, 1, 0);
        CacheKey key1 = new CacheKey("world", 1, 0, 1, 0);
        CacheKey key2 = new CacheKey("world", 2, 0, 1, 0);
        CacheKey key3 = new CacheKey("world", 3, 0, 1, 0);
        CacheKey key4 = new CacheKey("world", 4, 0, 1, 0);

        smallCache.put(key0, new CacheEntry(new byte[]{0}, 1));
        smallCache.put(key1, new CacheEntry(new byte[]{1}, 1));
        smallCache.put(key2, new CacheEntry(new byte[]{2}, 1));
        smallCache.put(key3, new CacheEntry(new byte[]{3}, 1));
        smallCache.put(key4, new CacheEntry(new byte[]{4}, 1));
        smallCache.cleanUp();

        long size = smallCache.size();
        assertTrue(size <= 3, "Cache should not exceed max size of 3, but was " + size);
        int surviving = 0;
        for (int i = 0; i <= 4; i++) {
            if (smallCache.get(new CacheKey("world", i, 0, 1, 0)) != null) {
                surviving++;
            }
        }
        assertTrue(surviving <= 3);
    }

    @Test
    void largeDataEntry_storedCorrectly() {
        byte[] largeData = new byte[8192];
        for (int i = 0; i < largeData.length; i++) {
            largeData[i] = (byte) (i & 0xFF);
        }
        CacheKey key = new CacheKey("world", 0, 0, 1, 0);
        cache.put(key, new CacheEntry(largeData, 24));
        CacheEntry retrieved = cache.get(key);
        assertNotNull(retrieved);
        assertArrayEquals(largeData, retrieved.getObfuscatedData());
        assertEquals(24, retrieved.getSectionCount());
    }

    @Test
    void negativeChunkCoords_storedCorrectly() {
        CacheKey key = new CacheKey("world", -5, -10, 1, 0);
        cache.put(key, new CacheEntry(new byte[]{42}, 1));
        CacheEntry retrieved = cache.get(key);
        assertNotNull(retrieved);
        assertEquals(42, retrieved.getObfuscatedData()[0]);
    }

    @Test
    void multipleWorlds_storedSeparately() {
        CacheKey overworld = new CacheKey("world", 5, 10, 1, 0);
        CacheKey nether = new CacheKey("world_nether", 5, 10, 1, 0);
        cache.put(overworld, new CacheEntry(new byte[]{1}, 1));
        cache.put(nether, new CacheEntry(new byte[]{2}, 1));
        CacheEntry ow = cache.get(overworld);
        CacheEntry ne = cache.get(nether);
        assertNotNull(ow);
        assertNotNull(ne);
        assertEquals(1, ow.getObfuscatedData()[0]);
        assertEquals(2, ne.getObfuscatedData()[0]);
    }

    @Test
    void entryTimestamp_preserved() {
        CacheKey key = new CacheKey("world", 0, 0, 1, 0);
        long beforePut = System.currentTimeMillis();
        cache.put(key, new CacheEntry(new byte[]{1}, 1));
        long afterPut = System.currentTimeMillis();
        CacheEntry entry = cache.get(key);
        assertNotNull(entry);
        assertTrue(entry.getTimestamp() >= beforePut);
        assertTrue(entry.getTimestamp() <= afterPut);
    }

    @Test
    void hitRateTracking_tracksCorrectly() {
        CacheKey key1 = new CacheKey("world", 0, 0, 1, 0);
        CacheKey key2 = new CacheKey("world", 0, 1, 1, 0);
        cache.put(key1, new CacheEntry(new byte[]{1}, 1));

        assertNotNull(cache.get(key1));
        assertNull(cache.get(key2));

        double hitRate = cache.hitRate();
        assertEquals(0.5, hitRate, 0.001);
    }
}
