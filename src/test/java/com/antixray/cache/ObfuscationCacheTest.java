package com.antixray.cache;

import com.antixray.deobfuscation.RevealedBlocksSet;
import com.antixray.util.BlockPosition;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ObfuscationCacheTest {

    private ObfuscationCache oc;

    @BeforeEach
    void setUp() {
        oc = new ObfuscationCache(100, 300);
    }

    @Test
    void put_andGet_returnsEntry() {
        CacheKey key = new CacheKey("world", 1, 2, 3, 100);
        CacheEntry entry = new CacheEntry(new byte[]{1, 2, 3}, 1);
        oc.put(key, entry);
        CacheEntry retrieved = oc.get(key);
        assertNotNull(retrieved);
        assertArrayEquals(new byte[]{1, 2, 3}, retrieved.getObfuscatedData());
    }

    @Test
    void get_missingKey_returnsNull() {
        assertNull(oc.get(new CacheKey("world", 99, 99, 1, 0)));
    }

    @Test
    void invalidate_removesEntry() {
        CacheKey key = new CacheKey("world", 5, 10, 2, 500);
        oc.put(key, new CacheEntry(new byte[]{42}, 1));
        assertNotNull(oc.get(key));
        oc.invalidate(key);
        assertNull(oc.get(key));
    }

    @Test
    void invalidateChunk_removesMatchingEntry() {
        oc.put(new CacheKey("world", 5, 10, 2, 500), new CacheEntry(new byte[]{1}, 1));
        oc.invalidateChunk("world", 5, 10, 2, 500);
        assertNull(oc.get(new CacheKey("world", 5, 10, 2, 500)));
    }

    @Test
    void invalidateChunk_differentMode_notRemoved() {
        CacheKey key = new CacheKey("world", 5, 10, 2, 500);
        oc.put(key, new CacheEntry(new byte[]{1}, 1));
        oc.invalidateChunk("world", 5, 10, 3, 500);
        assertNotNull(oc.get(key));
    }

    @Test
    void invalidateChunk_withWorldObject_clearsL1AndQueuesL2() {
        L1MemoryCache customL1 = new L1MemoryCache(100, 300);
        ObfuscationCache cacheWithMock = new ObfuscationCache(customL1);

        CacheKey key1 = new CacheKey("world", 5, 10, 1, 0);
        CacheKey key2 = new CacheKey("world", 5, 10, 2, 100);
        cacheWithMock.put(key1, new CacheEntry(new byte[]{1}, 1));
        cacheWithMock.put(key2, new CacheEntry(new byte[]{2}, 1));
        cacheWithMock.get(key1);
        cacheWithMock.get(key2);

        World world = mock(World.class);
        when(world.getName()).thenReturn("world");

        cacheWithMock.invalidateChunk(world, 5, 10);

        assertEquals(0, cacheWithMock.size());
    }

    @Test
    void getHitRate_returnsL1HitRate() {
        CacheKey key = new CacheKey("world", 0, 0, 1, 0);
        CacheEntry entry = new CacheEntry(new byte[]{1}, 1);
        oc.put(key, entry);

        oc.get(key);
        oc.get(key);
        oc.get(new CacheKey("world", 99, 99, 1, 0));

        double hitRate = oc.getHitRate();
        assertTrue(hitRate >= 0.0 && hitRate <= 1.0,
                "Hit rate should be between 0.0 and 1.0, was " + hitRate);
    }

    @Test
    void getHitRate_delegatesToL1() {
        assertEquals(oc.hitRate(), oc.getHitRate(),
                "getHitRate() should return same value as hitRate()");
    }

    @Test
    void invalidateAll_removesAllEntries() {
        for (int i = 0; i < 5; i++) {
            oc.put(new CacheKey("world", i, 0, 1, 0), new CacheEntry(new byte[]{(byte) i}, 1));
        }
        assertTrue(oc.size() >= 5);
        oc.invalidateAll();
        assertEquals(0, oc.size());
    }

    @Test
    void getOverlayPositions_returnsPositionsForChunk() {
        RevealedBlocksSet revealed = new RevealedBlocksSet(100);
        revealed.add(new BlockPosition("world", 0, 64, 0), 1L);
        revealed.add(new BlockPosition("world", 16, 64, 0), 1L);
        List<BlockPosition> positions = oc.getOverlayPositions(revealed, 0, 0, "world");
        assertEquals(1, positions.size());
        assertEquals(0, positions.get(0).getX());
    }

    @Test
    void getL1Cache_returnsUnderlyingCache() {
        assertNotNull(oc.getL1Cache());
        assertTrue(oc.getL1Cache() instanceof L1MemoryCache);
    }

    @Test
    void customL1Cache_used() {
        L1MemoryCache custom = new L1MemoryCache(50, 60);
        ObfuscationCache customOc = new ObfuscationCache(custom);
        CacheKey key = new CacheKey("world", 0, 0, 1, 0);
        customOc.put(key, new CacheEntry(new byte[]{1}, 1));
        assertNotNull(customOc.get(key));
        assertEquals(50, customOc.getL1Cache().getMaxSize());
    }

    @Test
    void size_reflectsEntries() {
        assertEquals(0, oc.size());
        oc.put(new CacheKey("world", 0, 0, 1, 0), new CacheEntry(new byte[10], 1));
        oc.put(new CacheKey("world", 1, 1, 1, 0), new CacheEntry(new byte[10], 1));
        assertEquals(2, oc.size());
    }

    @Test
    void isL2Enabled_falseWhenNoL2() {
        assertFalse(oc.isL2Enabled());
        assertNull(oc.getL2Cache());
    }

    @Test
    void shutdown_withoutL2_noException() {
        assertDoesNotThrow(() -> oc.shutdown());
    }

    static class L2IntegrationTest {

        @TempDir
        File tempDir;

        private ObfuscationCache ocWithL2;

        @BeforeEach
        void setUp() {
            ocWithL2 = new ObfuscationCache(100, 300, tempDir, 500, 300);
        }

        @AfterEach
        void tearDown() {
            if (ocWithL2 != null) {
                ocWithL2.shutdown();
            }
        }

        @Test
        void isL2Enabled_trueWhenL2Configured() {
            assertTrue(ocWithL2.isL2Enabled());
            assertNotNull(ocWithL2.getL2Cache());
        }

        @Test
        void put_storesInL1AndQueuesL2Write() {
            CacheKey key = new CacheKey("world", 1, 2, 3, 100);
            CacheEntry entry = new CacheEntry(new byte[]{1, 2, 3}, 1);
            ocWithL2.put(key, entry);

            CacheEntry fromL1 = ocWithL2.getL1Cache().get(key);
            assertNotNull(fromL1, "Entry should be in L1");
            assertArrayEquals(new byte[]{1, 2, 3}, fromL1.getObfuscatedData());
        }

        @Test
        void get_l1Miss_l2Hit_promotesToL1() throws Exception {
            CacheKey key = new CacheKey("world", 1, 2, 3, 100);
            CacheEntry entry = new CacheEntry(new byte[]{1, 2, 3}, 1);
            ocWithL2.put(key, entry);

            waitForL2Writes();
            ocWithL2.getL1Cache().invalidate(key);
            assertNull(ocWithL2.getL1Cache().get(key), "L1 should be empty after invalidation");

            CacheEntry retrieved = ocWithL2.get(key);
            assertNotNull(retrieved, "Should find entry in L2 and promote to L1");
            assertArrayEquals(new byte[]{1, 2, 3}, retrieved.getObfuscatedData());
            assertNotNull(ocWithL2.getL1Cache().get(key), "Entry should now be in L1 after promotion");
        }

        @Test
        void invalidate_clearsBothL1AndL2() throws Exception {
            CacheKey key = new CacheKey("world", 5, 10, 2, 500);
            ocWithL2.put(key, new CacheEntry(new byte[]{42}, 1));
            waitForL2Writes();

            assertNotNull(ocWithL2.get(key));

        ocWithL2.invalidate(key);
        waitForL2Writes();
        assertNull(ocWithL2.get(key));

        assertNull(ocWithL2.getL2Cache().get(key), "L2 should also be invalidated");
        }

        @Test
        void invalidateAll_clearsBothLevels() {
            for (int i = 0; i < 5; i++) {
                ocWithL2.put(new CacheKey("world", i, 0, 1, 0), new CacheEntry(new byte[]{(byte) i}, 1));
            }
            assertTrue(ocWithL2.size() >= 5);

            ocWithL2.invalidateAll();
            assertEquals(0, ocWithL2.size());
        }

        @Test
        void invalidateWorld_clearsL2ForWorld() throws Exception {
            ocWithL2.put(new CacheKey("world", 0, 0, 1, 0), new CacheEntry(new byte[]{1}, 1));
            ocWithL2.put(new CacheKey("world_nether", 0, 0, 1, 0), new CacheEntry(new byte[]{2}, 1));
            waitForL2Writes();

            ocWithL2.invalidateWorld("world");
            waitForL2Writes();

            CacheEntry worldEntry = ocWithL2.getL2Cache().get(new CacheKey("world", 0, 0, 1, 0));
            assertNull(worldEntry, "world L2 entries should be cleared");
            CacheEntry netherEntry = ocWithL2.getL2Cache().get(new CacheKey("world_nether", 0, 0, 1, 0));
            assertNotNull(netherEntry, "world_nether L2 entries should remain");
        }

        @Test
        void shutdown_closesL2() {
            L2DiskCache l2 = ocWithL2.getL2Cache();
            ocWithL2.shutdown();
        }

        private void waitForL2Writes() throws InterruptedException {
            L2DiskCache l2 = ocWithL2.getL2Cache();
            if (l2 == null) return;
            int maxWait = 2000;
            int waited = 0;
            while (l2.getWriteQueueSize() > 0 && waited < maxWait) {
                Thread.sleep(50);
                waited += 50;
            }
            Thread.sleep(200);
        }
    }
}
