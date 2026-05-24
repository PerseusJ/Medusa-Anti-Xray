package com.antixray.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SeededRandomTest {

    @Test
    void sameSeedProducesSameSequence() {
        SeededRandom r1 = new SeededRandom(10, 20, 3, 12345L);
        SeededRandom r2 = new SeededRandom(10, 20, 3, 12345L);
        for (int i = 0; i < 100; i++) {
            assertEquals(r1.nextLong(), r2.nextLong());
        }
    }

    @Test
    void differentSeedsProduceDifferentSequences() {
        SeededRandom r1 = new SeededRandom(10, 20, 3, 12345L);
        SeededRandom r2 = new SeededRandom(10, 20, 3, 99999L);
        boolean differ = false;
        for (int i = 0; i < 10; i++) {
            if (r1.nextLong() != r2.nextLong()) {
                differ = true;
                break;
            }
        }
        assertTrue(differ, "Different server salts should produce different sequences");
    }

    @Test
    void differentChunkCoordsProduceDifferentSequences() {
        SeededRandom r1 = new SeededRandom(0, 0, 0, 100L);
        SeededRandom r2 = new SeededRandom(1, 0, 0, 100L);
        assertNotEquals(r1.nextLong(), r2.nextLong());
    }

    @Test
    void differentSectionYProducesDifferentSequences() {
        SeededRandom r1 = new SeededRandom(5, 10, 0, 100L);
        SeededRandom r2 = new SeededRandom(5, 10, 1, 100L);
        assertNotEquals(r1.nextLong(), r2.nextLong());
    }

    @Test
    void nextIntWithinBounds() {
        SeededRandom rng = new SeededRandom(0, 0, 0, 42L);
        int bound = 16;
        for (int i = 0; i < 10000; i++) {
            int val = rng.nextInt(bound);
            assertTrue(val >= 0 && val < bound, "nextInt(" + bound + ") returned " + val);
        }
    }

    @Test
    void nextIntBoundOneAlwaysZero() {
        SeededRandom rng = new SeededRandom(0, 0, 0, 42L);
        for (int i = 0; i < 100; i++) {
            assertEquals(0, rng.nextInt(1));
        }
    }

    @Test
    void nextIntThrowsOnNonPositive() {
        SeededRandom rng = new SeededRandom(0, 0, 0, 42L);
        assertThrows(IllegalArgumentException.class, () -> rng.nextInt(0));
        assertThrows(IllegalArgumentException.class, () -> rng.nextInt(-1));
    }

    @Test
    void nextDoubleInRange() {
        SeededRandom rng = new SeededRandom(0, 0, 0, 42L);
        for (int i = 0; i < 10000; i++) {
            double val = rng.nextDouble();
            assertTrue(val >= 0.0 && val < 1.0, "nextDouble() returned " + val);
        }
    }

    @Test
    void nextBooleanProbability() {
        SeededRandom rng = new SeededRandom(0, 0, 0, 42L);
        int trueCount = 0;
        int trials = 100000;
        double chance = 0.07;
        for (int i = 0; i < trials; i++) {
            if (rng.nextBoolean(chance)) trueCount++;
        }
        double observed = (double) trueCount / trials;
        assertTrue(observed > 0.04 && observed < 0.10,
                "Expected ~7% true rate, got " + (observed * 100) + "%");
    }

    @Test
    void determinismAcrossJvmInstances() {
        SeededRandom r1 = new SeededRandom(100, 200, 5, 999999L);
        long[] seq1 = new long[20];
        for (int i = 0; i < 20; i++) seq1[i] = r1.nextLong();

        SeededRandom r2 = new SeededRandom(100, 200, 5, 999999L);
        long[] seq2 = new long[20];
        for (int i = 0; i < 20; i++) seq2[i] = r2.nextLong();

        assertArrayEquals(seq1, seq2, "Same seed must produce identical sequences");
    }

    @Test
    void nextIntDistributionReasonable() {
        SeededRandom rng = new SeededRandom(42, 17, 3, 12345L);
        int bound = 5;
        int[] counts = new int[bound];
        for (int i = 0; i < 50000; i++) {
            counts[rng.nextInt(bound)]++;
        }
        for (int c : counts) {
            assertTrue(c > 8000, "Distribution too skewed: count=" + c);
        }
    }

    @Test
    void negativeChunkCoordsWork() {
        SeededRandom r1 = new SeededRandom(-10, -20, 3, 100L);
        SeededRandom r2 = new SeededRandom(-10, -20, 3, 100L);
        assertEquals(r1.nextLong(), r2.nextLong());
        assertEquals(r1.nextInt(16), r2.nextInt(16));
        assertEquals(r1.nextDouble(), r2.nextDouble());
    }
}
