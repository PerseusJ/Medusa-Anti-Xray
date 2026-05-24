package com.antixray.util;

public class SeededRandom {

    private static final long MULTIPLIER = 6364136223846793005L;
    private static final long INCREMENT = 1442695040888963407L;

    private long state;

    public SeededRandom(int chunkX, int chunkZ, int sectionY, long serverSalt) {
        long seed = chunkX * 341873128712L + chunkZ * 132897987541L + sectionY * 97219323L + serverSalt;
        this.state = (seed ^ MULTIPLIER) + INCREMENT;
    }

    public long nextLong() {
        state = state * MULTIPLIER + INCREMENT;
        return state;
    }

    public int nextInt(int bound) {
        if (bound <= 0) throw new IllegalArgumentException("bound must be positive");
        long r = nextLong() >>> 1;
        return (int) (r % bound);
    }

    public double nextDouble() {
        return (nextLong() >>> 11) * 0x1.0p-53;
    }

    public boolean nextBoolean(double chance) {
        return nextDouble() < chance;
    }
}
