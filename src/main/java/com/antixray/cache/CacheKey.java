package com.antixray.cache;

import com.antixray.engine.ObfuscationMode;

import java.util.Objects;

public final class CacheKey {

    private final String worldName;
    private final int chunkX;
    private final int chunkZ;
    private final int engineMode;
    private final int configHash;

    public CacheKey(String worldName, int chunkX, int chunkZ, int engineMode, int configHash) {
        this.worldName = Objects.requireNonNull(worldName);
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.engineMode = engineMode;
        this.configHash = configHash;
    }

    public CacheKey(String worldName, int chunkX, int chunkZ, ObfuscationMode mode, int configHash) {
        this(worldName, chunkX, chunkZ, mode.ordinal() + 1, configHash);
    }

    public String getWorldName() { return worldName; }

    public int getChunkX() { return chunkX; }

    public int getChunkZ() { return chunkZ; }

    public int getEngineMode() { return engineMode; }

    public int getConfigHash() { return configHash; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CacheKey that)) return false;
        return chunkX == that.chunkX
                && chunkZ == that.chunkZ
                && engineMode == that.engineMode
                && configHash == that.configHash
                && worldName.equals(that.worldName);
    }

    @Override
    public int hashCode() {
        int h = worldName.hashCode();
        h = 31 * h + chunkX;
        h = 31 * h + chunkZ;
        h = 31 * h + engineMode;
        h = 31 * h + configHash;
        return h;
    }

    @Override
    public String toString() {
        return "CacheKey{world=" + worldName
                + ", x=" + chunkX
                + ", z=" + chunkZ
                + ", mode=" + engineMode
                + ", configHash=" + configHash + '}';
    }
}
