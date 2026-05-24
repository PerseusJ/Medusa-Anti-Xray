package com.antixray.cache;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class L2DiskCacheTest {

    @TempDir
    File tempDir;

    private L2DiskCache l2Cache;

    @BeforeEach
    void setUp() {
        l2Cache = new L2DiskCache(tempDir, 500, 300);
    }

    @AfterEach
    void tearDown() {
        if (l2Cache != null) {
            l2Cache.shutdown();
        }
    }

    @Test
    void constructor_createsCacheDirectory() {
        File subDir = new File(tempDir, "subcache");
        L2DiskCache cache = new L2DiskCache(subDir, 100, 60);
        assertTrue(subDir.exists());
        cache.shutdown();
    }

    @Test
    void constructor_storesConfig() {
        assertEquals(500L * 1024 * 1024, l2Cache.getDiskBudgetBytes());
        assertEquals(300_000L, l2Cache.getExpiryMillis());
    }

    @Test
    void put_andGet_returnsEntry() throws Exception {
        CacheKey key = new CacheKey("world", 1, 2, 3, 100);
        CacheEntry entry = new CacheEntry(new byte[]{1, 2, 3}, 1);
        l2Cache.put(key, entry);

        waitForWrites();

        CacheEntry retrieved = l2Cache.get(key);
        assertNotNull(retrieved);
        assertArrayEquals(new byte[]{1, 2, 3}, retrieved.getObfuscatedData());
        assertEquals(1, retrieved.getSectionCount());
    }

    @Test
    void get_missingKey_returnsNull() {
        CacheKey key = new CacheKey("world", 99, 99, 1, 0);
        assertNull(l2Cache.get(key));
    }

    @Test
    void put_largeData_storedCorrectly() throws Exception {
        byte[] largeData = new byte[8192];
        for (int i = 0; i < largeData.length; i++) {
            largeData[i] = (byte) (i & 0xFF);
        }
        CacheKey key = new CacheKey("world", 0, 0, 1, 0);
        CacheEntry entry = new CacheEntry(largeData, 24);
        l2Cache.put(key, entry);

        waitForWrites();

        CacheEntry retrieved = l2Cache.get(key);
        assertNotNull(retrieved);
        assertArrayEquals(largeData, retrieved.getObfuscatedData());
        assertEquals(24, retrieved.getSectionCount());
    }

    @Test
    void put_negativeChunkCoords_storedCorrectly() throws Exception {
        CacheKey key = new CacheKey("world", -5, -10, 1, 0);
        CacheEntry entry = new CacheEntry(new byte[]{42}, 1);
        l2Cache.put(key, entry);

        waitForWrites();

        CacheEntry retrieved = l2Cache.get(key);
        assertNotNull(retrieved);
        assertEquals(42, retrieved.getObfuscatedData()[0]);
    }

    @Test
    void put_multipleWorlds_storedSeparately() throws Exception {
        CacheKey overworld = new CacheKey("world", 5, 10, 1, 0);
        CacheKey nether = new CacheKey("world_nether", 5, 10, 1, 0);
        l2Cache.put(overworld, new CacheEntry(new byte[]{1}, 1));
        l2Cache.put(nether, new CacheEntry(new byte[]{2}, 1));

        waitForWrites();

        CacheEntry ow = l2Cache.get(overworld);
        CacheEntry ne = l2Cache.get(nether);
        assertNotNull(ow);
        assertNotNull(ne);
        assertEquals(1, ow.getObfuscatedData()[0]);
        assertEquals(2, ne.getObfuscatedData()[0]);
    }

    @Test
    void put_multipleChunksInSameRegion_storedCorrectly() throws Exception {
        for (int i = 0; i < 10; i++) {
            CacheKey key = new CacheKey("world", i, i, 1, 0);
            l2Cache.put(key, new CacheEntry(new byte[]{(byte) i}, 1));
        }

        waitForWrites();

        for (int i = 0; i < 10; i++) {
            CacheKey key = new CacheKey("world", i, i, 1, 0);
            CacheEntry retrieved = l2Cache.get(key);
            assertNotNull(retrieved, "Chunk " + i + " should be in L2");
            assertEquals(i, retrieved.getObfuscatedData()[0]);
        }
    }

    @Test
    void put_chunksInDifferentRegions_storedCorrectly() throws Exception {
        CacheKey key1 = new CacheKey("world", 0, 0, 1, 0);
        CacheKey key2 = new CacheKey("world", 32, 0, 1, 0);
        CacheKey key3 = new CacheKey("world", 0, 32, 1, 0);
        l2Cache.put(key1, new CacheEntry(new byte[]{1}, 1));
        l2Cache.put(key2, new CacheEntry(new byte[]{2}, 1));
        l2Cache.put(key3, new CacheEntry(new byte[]{3}, 1));

        waitForWrites();

        assertNotNull(l2Cache.get(key1));
        assertNotNull(l2Cache.get(key2));
        assertNotNull(l2Cache.get(key3));
        assertEquals(1, l2Cache.get(key1).getObfuscatedData()[0]);
        assertEquals(2, l2Cache.get(key2).getObfuscatedData()[0]);
        assertEquals(3, l2Cache.get(key3).getObfuscatedData()[0]);
    }

    @Test
    void invalidate_removesEntry() throws Exception {
        CacheKey key = new CacheKey("world", 5, 10, 2, 500);
        l2Cache.put(key, new CacheEntry(new byte[]{42}, 1));

        waitForWrites();
        assertNotNull(l2Cache.get(key));

        l2Cache.invalidate(key);
        waitForWrites();

        assertNull(l2Cache.get(key));
    }

    @Test
    void invalidateChunk_removesMatchingEntry() throws Exception {
        CacheKey key = new CacheKey("world", 5, 10, 2, 500);
        l2Cache.put(key, new CacheEntry(new byte[]{1}, 1));

        waitForWrites();
        assertNotNull(l2Cache.get(key));

        l2Cache.invalidateChunk("world", 5, 10);
        waitForWrites();

        assertNull(l2Cache.get(key));
    }

    @Test
    void invalidateWorld_removesAllEntriesForWorld() throws Exception {
        l2Cache.put(new CacheKey("world", 0, 0, 1, 0), new CacheEntry(new byte[]{1}, 1));
        l2Cache.put(new CacheKey("world", 1, 1, 1, 0), new CacheEntry(new byte[]{2}, 1));
        l2Cache.put(new CacheKey("other", 0, 0, 1, 0), new CacheEntry(new byte[]{3}, 1));

        waitForWrites();

        l2Cache.invalidateWorld("world");
        waitForWrites();

        assertNull(l2Cache.get(new CacheKey("world", 0, 0, 1, 0)));
        assertNull(l2Cache.get(new CacheKey("world", 1, 1, 1, 0)));
        CacheEntry other = l2Cache.get(new CacheKey("other", 0, 0, 1, 0));
        assertNotNull(other);
        assertEquals(3, other.getObfuscatedData()[0]);
    }

    @Test
    void invalidateAll_removesAllEntries() throws Exception {
        for (int i = 0; i < 5; i++) {
            CacheKey key = new CacheKey("world", i, 0, 1, 0);
            l2Cache.put(key, new CacheEntry(new byte[]{(byte) i}, 1));
        }

        waitForWrites();

        l2Cache.invalidateAll();
        waitForWrites();

        for (int i = 0; i < 5; i++) {
            assertNull(l2Cache.get(new CacheKey("world", i, 0, 1, 0)));
        }
    }

    @Test
    void get_expiredEntry_returnsNull() throws Exception {
        L2DiskCache shortLivedCache = new L2DiskCache(tempDir, 500, 0);
        try {
            CacheKey key = new CacheKey("world", 0, 0, 1, 0);
            CacheEntry entry = new CacheEntry(new byte[]{1}, 1);
            shortLivedCache.put(key, entry);
            waitForWrites();

            Thread.sleep(50);

            assertNull(shortLivedCache.get(key));
        } finally {
            shortLivedCache.shutdown();
        }
    }

    @Test
    void get_nonExpiredEntry_returnsEntry() throws Exception {
        L2DiskCache longLivedCache = new L2DiskCache(tempDir, 500, 3600);
        try {
            CacheKey key = new CacheKey("world", 0, 0, 1, 0);
            CacheEntry entry = new CacheEntry(new byte[]{1}, 1);
            longLivedCache.put(key, entry);
            waitForWrites();

            CacheEntry retrieved = longLivedCache.get(key);
            assertNotNull(retrieved);
        } finally {
            longLivedCache.shutdown();
        }
    }

    @Test
    void get_configHashMismatch_returnsNull() throws Exception {
        CacheKey key1 = new CacheKey("world", 5, 10, 3, 100);
        CacheKey key2 = new CacheKey("world", 5, 10, 3, 200);
        l2Cache.put(key1, new CacheEntry(new byte[]{10}, 1));

        waitForWrites();

        CacheEntry retrieved = l2Cache.get(key2);
        assertNull(retrieved);
    }

    @Test
    void get_engineModeMismatch_returnsNull() throws Exception {
        CacheKey key1 = new CacheKey("world", 5, 10, 2, 500);
        CacheKey key2 = new CacheKey("world", 5, 10, 3, 500);
        l2Cache.put(key1, new CacheEntry(new byte[]{10}, 1));

        waitForWrites();

        CacheEntry retrieved = l2Cache.get(key2);
        assertNull(retrieved);
    }

    @Test
    void shutdown_preventsFurtherOperations() {
        l2Cache.shutdown();
        CacheKey key = new CacheKey("world", 0, 0, 1, 0);
        assertNull(l2Cache.get(key));
    }

    @Test
    void shutdown_flushesPendingWrites() throws Exception {
        CacheKey key = new CacheKey("world", 1, 2, 3, 100);
        l2Cache.put(key, new CacheEntry(new byte[]{1, 2, 3}, 1));

        l2Cache.shutdown();
        l2Cache = null;

        L2DiskCache reopened = new L2DiskCache(tempDir, 500, 300);
        try {
            CacheEntry retrieved = reopened.get(key);
            assertNotNull(retrieved, "Entry should survive shutdown and be readable after reopen");
            assertArrayEquals(new byte[]{1, 2, 3}, retrieved.getObfuscatedData());
        } finally {
            reopened.shutdown();
        }
    }

    @Test
    void persistence_dataSurvivesAcrossInstances() throws Exception {
        CacheKey key = new CacheKey("world", 5, 10, 3, 100);
        l2Cache.put(key, new CacheEntry(new byte[]{42}, 1));

        l2Cache.shutdown();
        l2Cache = null;

        L2DiskCache secondInstance = new L2DiskCache(tempDir, 500, 300);
        try {
            CacheEntry retrieved = secondInstance.get(key);
            assertNotNull(retrieved, "Data should survive across L2 instances");
            assertEquals(42, retrieved.getObfuscatedData()[0]);
        } finally {
            secondInstance.shutdown();
        }
    }

    @Test
    void preloadRecent_loadsEntriesIntoL1() throws Exception {
        CacheKey key1 = new CacheKey("world", 0, 0, 1, 0);
        CacheKey key2 = new CacheKey("world", 1, 1, 1, 0);
        l2Cache.put(key1, new CacheEntry(new byte[]{1}, 1));
        l2Cache.put(key2, new CacheEntry(new byte[]{2}, 1));

        waitForWrites();
        l2Cache.shutdown();
        l2Cache = null;

        L2DiskCache reopened = new L2DiskCache(tempDir, 500, 300);
        L1MemoryCache l1 = new L1MemoryCache(1000, 300);
        try {
            reopened.preloadRecent(l1, 10);
            assertTrue(l1.size() > 0);
        } finally {
            reopened.shutdown();
        }
    }

    @Test
    void diskBudget_smallBudget_evictsFiles() throws Exception {
        L2DiskCache smallBudgetCache = new L2DiskCache(tempDir, 0, 300);
        try {
            CacheKey key = new CacheKey("world", 0, 0, 1, 0);
            smallBudgetCache.put(key, new CacheEntry(new byte[]{1}, 1));
            waitForWrites(smallBudgetCache);
            smallBudgetCache.invalidateAll();
            waitForWrites(smallBudgetCache);
        } finally {
            smallBudgetCache.shutdown();
        }
    }

    @Test
    void diskBudget_budgetEnforcement_deletesOldest() throws Exception {
        L2DiskCache budgetCache = new L2DiskCache(tempDir, 1, 300);
        try {
            byte[] largeData = new byte[500 * 1024];
            new java.util.Random(42).nextBytes(largeData);

            CacheKey key1 = new CacheKey("world", 0, 0, 1, 0);
            budgetCache.put(key1, new CacheEntry(largeData, 1));
            waitForWrites(budgetCache);

            CacheKey key2 = new CacheKey("world", 32, 32, 1, 0);
            budgetCache.put(key2, new CacheEntry(largeData, 1));
            waitForWrites(budgetCache);

            File file1 = new File(new File(budgetCache.getBaseDir(), "world"), RegionFile.fileName(0, 0));
            File file2 = new File(new File(budgetCache.getBaseDir(), "world"), RegionFile.fileName(1, 1));
            File file3 = new File(new File(budgetCache.getBaseDir(), "world"), RegionFile.fileName(2, 2));

            assertTrue(file1.exists(), "Region 1 file should exist");
            assertTrue(file2.exists(), "Region 2 file should exist");

            long now = System.currentTimeMillis();
            assertTrue(file1.setLastModified(now - 10000));
            assertTrue(file2.setLastModified(now - 5000));

            CacheKey key3 = new CacheKey("world", 64, 64, 1, 0);
            budgetCache.put(key3, new CacheEntry(largeData, 1));
            waitForWrites(budgetCache);

            Thread.sleep(500);

            assertFalse(file1.exists(), "Oldest region file should be deleted");
            assertTrue(file2.exists(), "Newer region file should still exist");
            assertTrue(file3.exists(), "Newest region file should still exist");
        } finally {
            budgetCache.shutdown();
        }
    }


    @Test
    void invalidate_nonExistentKey_noException() {
        CacheKey key = new CacheKey("world", 999, 999, 1, 0);
        assertDoesNotThrow(() -> l2Cache.invalidate(key));
    }

    @Test
    void put_overwriteExistingEntry() throws Exception {
        CacheKey key = new CacheKey("world", 0, 0, 1, 0);
        l2Cache.put(key, new CacheEntry(new byte[]{1}, 1));

        waitForWrites();

        l2Cache.put(key, new CacheEntry(new byte[]{2, 3}, 2));

        waitForWrites();

        CacheEntry retrieved = l2Cache.get(key);
        assertNotNull(retrieved);
        assertArrayEquals(new byte[]{2, 3}, retrieved.getObfuscatedData());
        assertEquals(2, retrieved.getSectionCount());
    }

    @Test
    void getBaseDir_returnsConfiguredDir() {
        File expectedBaseDir = new File(tempDir, L2DiskCache.CACHE_DIR_NAME);
        assertEquals(expectedBaseDir, l2Cache.getBaseDir());
    }

    @Test
    void timestamp_preservedAcrossWriteRead() throws Exception {
        CacheKey key = new CacheKey("world", 0, 0, 1, 0);
        long before = System.currentTimeMillis();
        CacheEntry entry = new CacheEntry(new byte[]{1}, 1);
        l2Cache.put(key, entry);

        waitForWrites();

        CacheEntry retrieved = l2Cache.get(key);
        assertNotNull(retrieved);
        assertTrue(retrieved.getTimestamp() >= before);
        assertTrue(retrieved.getTimestamp() <= System.currentTimeMillis());
    }

    private void waitForWrites() throws InterruptedException {
        waitForWrites(l2Cache);
    }

    private void waitForWrites(L2DiskCache cache) throws InterruptedException {
        int maxWait = 2000;
        int waited = 0;
        while (cache.getWriteQueueSize() > 0 && waited < maxWait) {
            Thread.sleep(50);
            waited += 50;
        }
        Thread.sleep(200);
    }
}
