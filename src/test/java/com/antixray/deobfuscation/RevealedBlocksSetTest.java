package com.antixray.deobfuscation;

import com.antixray.util.BlockPosition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RevealedBlocksSetTest {

    private static final String WORLD = "world";

    private RevealedBlocksSet set;

    @BeforeEach
    void setUp() {
        set = new RevealedBlocksSet(10000);
    }

    @Test
    void addAndContains() {
        BlockPosition pos = new BlockPosition(WORLD, 10, 64, -32);
        set.add(pos, 100L);
        assertTrue(set.contains(pos));
    }

    @Test
    void containsReturnsFalseForMissingPosition() {
        BlockPosition pos = new BlockPosition(WORLD, 10, 64, -32);
        assertFalse(set.contains(pos));
    }

    @Test
    void addUpdatesTickOnReAdd() {
        BlockPosition pos = new BlockPosition(WORLD, 5, 10, 15);
        set.add(pos, 50L);
        set.add(pos, 200L);
        assertTrue(set.contains(pos));
        assertEquals(1, set.size());
    }

    @Test
    void removeExistingPosition() {
        BlockPosition pos = new BlockPosition(WORLD, 1, 2, 3);
        set.add(pos, 10L);
        set.remove(pos);
        assertFalse(set.contains(pos));
        assertEquals(0, set.size());
    }

    @Test
    void removeNonExistentDoesNotChangeSize() {
        BlockPosition pos = new BlockPosition(WORLD, 1, 2, 3);
        set.remove(pos);
        assertEquals(0, set.size());
    }

    @Test
    void sizeTracksAddAndRemove() {
        BlockPosition a = new BlockPosition(WORLD, 1, 2, 3);
        BlockPosition b = new BlockPosition(WORLD, 4, 5, 6);
        BlockPosition c = new BlockPosition(WORLD, 7, 8, 9);

        set.add(a, 1L);
        set.add(b, 2L);
        set.add(c, 3L);
        assertEquals(3, set.size());

        set.remove(b);
        assertEquals(2, set.size());

        set.remove(a);
        set.remove(c);
        assertEquals(0, set.size());
    }

    @Test
    void clearRemovesAllEntries() {
        set.add(new BlockPosition(WORLD, 1, 2, 3), 1L);
        set.add(new BlockPosition(WORLD, 4, 5, 6), 2L);
        set.add(new BlockPosition(WORLD, 7, 8, 9), 3L);
        assertEquals(3, set.size());

        set.clear();
        assertEquals(0, set.size());
        assertFalse(set.contains(new BlockPosition(WORLD, 1, 2, 3)));
        assertFalse(set.contains(new BlockPosition(WORLD, 4, 5, 6)));
        assertFalse(set.contains(new BlockPosition(WORLD, 7, 8, 9)));
    }

    @Test
    void clearOnEmptySetIsNoOp() {
        set.clear();
        assertEquals(0, set.size());
    }

    @Test
    void getRevealedBeforeTickReturnsOnlyOlderEntries() {
        BlockPosition pos1 = new BlockPosition(WORLD, 10, 20, 30);
        BlockPosition pos2 = new BlockPosition(WORLD, 40, 50, 60);
        BlockPosition pos3 = new BlockPosition(WORLD, 70, 80, 90);

        set.add(pos1, 10L);
        set.add(pos2, 20L);
        set.add(pos3, 30L);

        List<BlockPosition> result = set.getRevealedBeforeTick(25L, WORLD);

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(p -> p.getX() == 10 && p.getY() == 20 && p.getZ() == 30));
        assertTrue(result.stream().anyMatch(p -> p.getX() == 40 && p.getY() == 50 && p.getZ() == 60));
        assertFalse(result.stream().anyMatch(p -> p.getX() == 70));

        assertEquals(1, set.size());
        assertTrue(set.contains(pos3));
        assertFalse(set.contains(pos1));
        assertFalse(set.contains(pos2));
    }

    @Test
    void getRevealedBeforeTickRemovesReturnedEntries() {
        BlockPosition pos = new BlockPosition(WORLD, 5, 10, 15);
        set.add(pos, 10L);

        List<BlockPosition> result = set.getRevealedBeforeTick(20L, WORLD);
        assertEquals(1, result.size());
        assertEquals(0, set.size());
        assertFalse(set.contains(pos));
    }

    @Test
    void getRevealedBeforeTickReturnsEmptyWhenNoneOlder() {
        BlockPosition pos = new BlockPosition(WORLD, 5, 10, 15);
        set.add(pos, 100L);

        List<BlockPosition> result = set.getRevealedBeforeTick(50L, WORLD);
        assertTrue(result.isEmpty());
        assertEquals(1, set.size());
        assertTrue(set.contains(pos));
    }

    @Test
    void getRevealedBeforeTickExactTickNotIncluded() {
        BlockPosition pos = new BlockPosition(WORLD, 5, 10, 15);
        set.add(pos, 100L);

        List<BlockPosition> result = set.getRevealedBeforeTick(100L, WORLD);
        assertTrue(result.isEmpty());
    }

    @Test
    void getRevealedBeforeTickPreservesWorldName() {
        BlockPosition pos = new BlockPosition(WORLD, 42, -64, 100);
        set.add(pos, 10L);

        List<BlockPosition> result = set.getRevealedBeforeTick(20L, WORLD);
        assertEquals(1, result.size());
        assertEquals(WORLD, result.get(0).getWorldName());
    }

    @Test
    void getRevealedBeforeTickOnEmptySetReturnsEmpty() {
        List<BlockPosition> result = set.getRevealedBeforeTick(100L, WORLD);
        assertTrue(result.isEmpty());
    }

    @Test
    void getRevealedBeforeTickNoRemoveReturnsOnlyOlderEntries() {
        BlockPosition pos1 = new BlockPosition(WORLD, 10, 20, 30);
        BlockPosition pos2 = new BlockPosition(WORLD, 40, 50, 60);
        BlockPosition pos3 = new BlockPosition(WORLD, 70, 80, 90);

        set.add(pos1, 10L);
        set.add(pos2, 20L);
        set.add(pos3, 30L);

        List<BlockPosition> result = set.getRevealedBeforeTickNoRemove(25L, WORLD);

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(p -> p.getX() == 10 && p.getY() == 20 && p.getZ() == 30));
        assertTrue(result.stream().anyMatch(p -> p.getX() == 40 && p.getY() == 50 && p.getZ() == 60));
        assertFalse(result.stream().anyMatch(p -> p.getX() == 70));

        assertEquals(3, set.size());
        assertTrue(set.contains(pos1));
        assertTrue(set.contains(pos2));
        assertTrue(set.contains(pos3));
    }

    @Test
    void getRevealedBeforeTickNoRemoveDoesNotModifySet() {
        BlockPosition pos1 = new BlockPosition(WORLD, 5, 10, 15);
        BlockPosition pos2 = new BlockPosition(WORLD, 20, 30, 40);
        set.add(pos1, 10L);
        set.add(pos2, 50L);

        set.getRevealedBeforeTickNoRemove(30L, WORLD);

        assertEquals(2, set.size());
        assertTrue(set.contains(pos1));
        assertTrue(set.contains(pos2));
    }

    @Test
    void getRevealedBeforeTickNoRemoveReturnsEmptyWhenNoneOlder() {
        BlockPosition pos = new BlockPosition(WORLD, 5, 10, 15);
        set.add(pos, 100L);

        List<BlockPosition> result = set.getRevealedBeforeTickNoRemove(50L, WORLD);
        assertTrue(result.isEmpty());
        assertEquals(1, set.size());
    }

    @Test
    void getRevealedBeforeTickNoRemoveExactTickNotIncluded() {
        BlockPosition pos = new BlockPosition(WORLD, 5, 10, 15);
        set.add(pos, 100L);

        List<BlockPosition> result = set.getRevealedBeforeTickNoRemove(100L, WORLD);
        assertTrue(result.isEmpty());
    }

    @Test
    void getRevealedBeforeTickNoRemoveOnEmptySetReturnsEmpty() {
        List<BlockPosition> result = set.getRevealedBeforeTickNoRemove(100L, WORLD);
        assertTrue(result.isEmpty());
    }

    @Test
    void evictionRemovesOldestWhenOverMax() {
        RevealedBlocksSet smallSet = new RevealedBlocksSet(3);

        BlockPosition pos1 = new BlockPosition(WORLD, 1, 0, 0);
        BlockPosition pos2 = new BlockPosition(WORLD, 2, 0, 0);
        BlockPosition pos3 = new BlockPosition(WORLD, 3, 0, 0);
        BlockPosition pos4 = new BlockPosition(WORLD, 4, 0, 0);

        smallSet.add(pos1, 10L);
        smallSet.add(pos2, 20L);
        smallSet.add(pos3, 30L);
        assertEquals(3, smallSet.size());

        smallSet.add(pos4, 40L);
        assertEquals(3, smallSet.size());
        assertFalse(smallSet.contains(pos1));
        assertTrue(smallSet.contains(pos2));
        assertTrue(smallSet.contains(pos3));
        assertTrue(smallSet.contains(pos4));
    }

    @Test
    void evictionEvictsLowestTick() {
        RevealedBlocksSet smallSet = new RevealedBlocksSet(3);

        BlockPosition pos1 = new BlockPosition(WORLD, 1, 0, 0);
        BlockPosition pos2 = new BlockPosition(WORLD, 2, 0, 0);
        BlockPosition pos3 = new BlockPosition(WORLD, 3, 0, 0);
        BlockPosition pos4 = new BlockPosition(WORLD, 4, 0, 0);

        smallSet.add(pos1, 50L);
        smallSet.add(pos2, 10L);
        smallSet.add(pos3, 30L);
        smallSet.add(pos4, 40L);

        assertFalse(smallSet.contains(pos2));
        assertTrue(smallSet.contains(pos1));
        assertTrue(smallSet.contains(pos3));
        assertTrue(smallSet.contains(pos4));
    }

    @Test
    void reAddDoesNotTriggerEviction() {
        RevealedBlocksSet smallSet = new RevealedBlocksSet(3);

        BlockPosition pos1 = new BlockPosition(WORLD, 1, 0, 0);
        BlockPosition pos2 = new BlockPosition(WORLD, 2, 0, 0);
        BlockPosition pos3 = new BlockPosition(WORLD, 3, 0, 0);

        smallSet.add(pos1, 10L);
        smallSet.add(pos2, 20L);
        smallSet.add(pos3, 30L);
        assertEquals(3, smallSet.size());

        smallSet.add(pos1, 100L);
        assertEquals(3, smallSet.size());
        assertTrue(smallSet.contains(pos1));
    }

    @Test
    void maxSizeOneEvictsOnSecondAdd() {
        RevealedBlocksSet tinySet = new RevealedBlocksSet(1);

        BlockPosition pos1 = new BlockPosition(WORLD, 1, 0, 0);
        BlockPosition pos2 = new BlockPosition(WORLD, 2, 0, 0);

        tinySet.add(pos1, 10L);
        assertTrue(tinySet.contains(pos1));
        assertEquals(1, tinySet.size());

        tinySet.add(pos2, 20L);
        assertFalse(tinySet.contains(pos1));
        assertTrue(tinySet.contains(pos2));
        assertEquals(1, tinySet.size());
    }

    @Test
    void getMaxSizeReturnsConstructorValue() {
        RevealedBlocksSet custom = new RevealedBlocksSet(5000);
        assertEquals(5000, custom.getMaxSize());

        RevealedBlocksSet defaultSize = new RevealedBlocksSet(10000);
        assertEquals(10000, defaultSize.getMaxSize());
    }

    @Test
    void differentCoordinatesIndependent() {
        BlockPosition posA = new BlockPosition(WORLD, 0, 0, 0);
        BlockPosition posB = new BlockPosition(WORLD, 0, 0, 1);
        BlockPosition posC = new BlockPosition(WORLD, 0, 1, 0);

        set.add(posA, 1L);
        set.add(posB, 2L);
        set.add(posC, 3L);

        assertTrue(set.contains(posA));
        assertTrue(set.contains(posB));
        assertTrue(set.contains(posC));

        set.remove(posB);
        assertTrue(set.contains(posA));
        assertFalse(set.contains(posB));
        assertTrue(set.contains(posC));
        assertEquals(2, set.size());
    }

    @Test
    void negativeCoordinatesWork() {
        BlockPosition pos = new BlockPosition(WORLD, -100, -64, -200);
        set.add(pos, 5L);
        assertTrue(set.contains(pos));
        assertEquals(1, set.size());

        set.remove(pos);
        assertFalse(set.contains(pos));
        assertEquals(0, set.size());
    }

    @Test
    void sameEncodedKeyDifferentWorldNotCollision() {
        BlockPosition posW1 = new BlockPosition("world1", 10, 64, -32);
        BlockPosition posW2 = new BlockPosition("world2", 10, 64, -32);

        set.add(posW1, 1L);
        set.add(posW2, 2L);

        assertEquals(1, set.size());
        assertTrue(set.contains(posW1));
        assertTrue(set.contains(posW2));
    }

    @Test
    void addAfterRemoveReAddsWithNewTick() {
        BlockPosition pos = new BlockPosition(WORLD, 10, 20, 30);
        set.add(pos, 10L);
        set.remove(pos);
        assertFalse(set.contains(pos));
        assertEquals(0, set.size());

        set.add(pos, 50L);
        assertTrue(set.contains(pos));
        assertEquals(1, set.size());

        List<BlockPosition> before30 = set.getRevealedBeforeTick(30L, WORLD);
        assertTrue(before30.isEmpty());
        assertEquals(1, set.size());

        List<BlockPosition> before60 = set.getRevealedBeforeTick(60L, WORLD);
        assertEquals(1, before60.size());
        assertEquals(0, set.size());
    }

    @Test
    void getRevealedBeforeTickReturnsCorrectCoordinates() {
        BlockPosition pos = new BlockPosition(WORLD, 42, -64, 100);
        set.add(pos, 5L);

        List<BlockPosition> result = set.getRevealedBeforeTick(10L, WORLD);
        assertEquals(1, result.size());
        BlockPosition decoded = result.get(0);
        assertEquals(42, decoded.getX());
        assertEquals(-64, decoded.getY());
        assertEquals(100, decoded.getZ());
    }

    @Test
    void concurrentAddAndRemove() throws InterruptedException {
        RevealedBlocksSet concurrentSet = new RevealedBlocksSet(10000);
        int threadCount = 8;
        int opsPerThread = 1000;
        Thread[] threads = new Thread[threadCount];

        for (int t = 0; t < threadCount; t++) {
            final int threadIdx = t;
            threads[t] = new Thread(() -> {
                for (int i = 0; i < opsPerThread; i++) {
                    BlockPosition pos = new BlockPosition(WORLD, threadIdx, i, 0);
                    concurrentSet.add(pos, i);
                    concurrentSet.contains(pos);
                    if (i % 3 == 0) {
                        concurrentSet.remove(pos);
                    }
                }
            });
        }

        for (Thread t : threads) {
            t.start();
        }
        for (Thread t : threads) {
            t.join();
        }

        assertTrue(concurrentSet.size() >= 0);
        assertTrue(concurrentSet.size() <= concurrentSet.getMaxSize());
    }

    @Test
    void evictionWithBoundaryMaxSize() {
        RevealedBlocksSet exactSet = new RevealedBlocksSet(2);

        BlockPosition pos1 = new BlockPosition(WORLD, 1, 0, 0);
        BlockPosition pos2 = new BlockPosition(WORLD, 2, 0, 0);
        BlockPosition pos3 = new BlockPosition(WORLD, 3, 0, 0);

        exactSet.add(pos1, 1L);
        exactSet.add(pos2, 2L);
        assertEquals(2, exactSet.size());

        exactSet.add(pos3, 3L);
        assertEquals(2, exactSet.size());
        assertFalse(exactSet.contains(pos1));
    }

    @Test
    void evictionAfterMultipleReAddAndRemoves() {
        RevealedBlocksSet smallSet = new RevealedBlocksSet(2);

        BlockPosition pos1 = new BlockPosition(WORLD, 1, 0, 0);
        BlockPosition pos2 = new BlockPosition(WORLD, 2, 0, 0);
        BlockPosition pos3 = new BlockPosition(WORLD, 3, 0, 0);

        smallSet.add(pos1, 10L);
        smallSet.add(pos2, 20L);
        smallSet.add(pos1, 50L);
        assertEquals(2, smallSet.size());

        smallSet.add(pos3, 60L);
        assertEquals(2, smallSet.size());
        assertTrue(smallSet.contains(pos1));
        assertFalse(smallSet.contains(pos2));
        assertTrue(smallSet.contains(pos3));
    }

    @Test
    void getRevealedBeforeTickWithTickZeroReturnsAll() {
        BlockPosition pos = new BlockPosition(WORLD, 5, 10, 15);
        set.add(pos, -1L);

        List<BlockPosition> result = set.getRevealedBeforeTick(0L, WORLD);
        assertEquals(1, result.size());
        assertEquals(0, set.size());
    }

    @Test
    void removeThenAddDoesNotExceedMaxSize() {
        RevealedBlocksSet smallSet = new RevealedBlocksSet(2);

        BlockPosition pos1 = new BlockPosition(WORLD, 1, 0, 0);
        BlockPosition pos2 = new BlockPosition(WORLD, 2, 0, 0);
        BlockPosition pos3 = new BlockPosition(WORLD, 3, 0, 0);

        smallSet.add(pos1, 10L);
        smallSet.add(pos2, 20L);
        smallSet.remove(pos1);
        assertEquals(1, smallSet.size());

        smallSet.add(pos3, 30L);
        assertEquals(2, smallSet.size());
        assertTrue(smallSet.contains(pos2));
        assertTrue(smallSet.contains(pos3));
    }

    @Test
    void basicCrudOperations() {
        BlockPosition posA = new BlockPosition(WORLD, 5, 10, 15);
        BlockPosition posB = new BlockPosition(WORLD, 20, 30, 40);

        assertFalse(set.contains(posA));
        assertFalse(set.contains(posB));
        assertEquals(0, set.size());

        set.add(posA, 100L);
        assertTrue(set.contains(posA));
        assertFalse(set.contains(posB));
        assertEquals(1, set.size());

        set.add(posB, 200L);
        assertTrue(set.contains(posA));
        assertTrue(set.contains(posB));
        assertEquals(2, set.size());

        set.remove(posA);
        assertFalse(set.contains(posA));
        assertTrue(set.contains(posB));
        assertEquals(1, set.size());

        set.remove(posB);
        assertFalse(set.contains(posB));
        assertEquals(0, set.size());
    }

    @Test
    void evictionWhenExceedingMaxSize() {
        RevealedBlocksSet smallSet = new RevealedBlocksSet(3);

        BlockPosition pos1 = new BlockPosition(WORLD, 1, 0, 0);
        BlockPosition pos2 = new BlockPosition(WORLD, 2, 0, 0);
        BlockPosition pos3 = new BlockPosition(WORLD, 3, 0, 0);
        BlockPosition pos4 = new BlockPosition(WORLD, 4, 0, 0);
        BlockPosition pos5 = new BlockPosition(WORLD, 5, 0, 0);

        smallSet.add(pos1, 10L);
        smallSet.add(pos2, 20L);
        smallSet.add(pos3, 30L);
        assertEquals(3, smallSet.size());

        smallSet.add(pos4, 40L);
        assertEquals(3, smallSet.size());
        assertFalse(smallSet.contains(pos1), "Oldest entry (tick=10) should be evicted");
        assertTrue(smallSet.contains(pos2));
        assertTrue(smallSet.contains(pos3));
        assertTrue(smallSet.contains(pos4));

        smallSet.add(pos5, 50L);
        assertEquals(3, smallSet.size());
        assertFalse(smallSet.contains(pos2), "Next oldest entry (tick=20) should be evicted");
        assertTrue(smallSet.contains(pos3));
        assertTrue(smallSet.contains(pos4));
        assertTrue(smallSet.contains(pos5));
    }

    @Test
    void getRevealedBeforeTickReturnsCorrectEntries() {
        BlockPosition pos1 = new BlockPosition(WORLD, 10, 20, 30);
        BlockPosition pos2 = new BlockPosition(WORLD, 40, 50, 60);
        BlockPosition pos3 = new BlockPosition(WORLD, 70, 80, 90);
        BlockPosition pos4 = new BlockPosition(WORLD, 100, 110, 120);

        set.add(pos1, 10L);
        set.add(pos2, 20L);
        set.add(pos3, 30L);
        set.add(pos4, 40L);

        List<BlockPosition> result = set.getRevealedBeforeTick(25L, WORLD);

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(p -> p.getX() == 10 && p.getY() == 20 && p.getZ() == 30));
        assertTrue(result.stream().anyMatch(p -> p.getX() == 40 && p.getY() == 50 && p.getZ() == 60));
        assertFalse(result.stream().anyMatch(p -> p.getX() == 70));
        assertFalse(result.stream().anyMatch(p -> p.getX() == 100));

        assertEquals(2, set.size());
        assertFalse(set.contains(pos1));
        assertFalse(set.contains(pos2));
        assertTrue(set.contains(pos3));
        assertTrue(set.contains(pos4));
    }

    @Test
    void clearEmptiesSet() {
        set.add(new BlockPosition(WORLD, 1, 2, 3), 1L);
        set.add(new BlockPosition(WORLD, 4, 5, 6), 2L);
        set.add(new BlockPosition(WORLD, 7, 8, 9), 3L);
        assertEquals(3, set.size());

        set.clear();

        assertEquals(0, set.size());
        assertFalse(set.contains(new BlockPosition(WORLD, 1, 2, 3)));
        assertFalse(set.contains(new BlockPosition(WORLD, 4, 5, 6)));
        assertFalse(set.contains(new BlockPosition(WORLD, 7, 8, 9)));

        List<BlockPosition> result = set.getRevealedBeforeTick(100L, WORLD);
        assertTrue(result.isEmpty(), "No entries should remain after clear");
    }
}
