package com.antixray.cache;

import com.antixray.deobfuscation.RevealedBlocksSet;
import com.antixray.util.BlockPosition;
import org.bukkit.World;

import java.io.File;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ObfuscationCache {

    private static final Logger LOGGER = Logger.getLogger("AntiXray");

    private final L1MemoryCache l1Cache;
    private final L2DiskCache l2Cache;
    private final boolean l2Enabled;

    public ObfuscationCache(long maxL1Size, long l1ExpirySeconds) {
        this.l1Cache = new L1MemoryCache(maxL1Size, l1ExpirySeconds);
        this.l2Cache = null;
        this.l2Enabled = false;
    }

    public ObfuscationCache(L1MemoryCache l1Cache) {
        this.l1Cache = l1Cache;
        this.l2Cache = null;
        this.l2Enabled = false;
    }

    public ObfuscationCache(long maxL1Size, long l1ExpirySeconds, File cacheDir,
                            long diskBudgetMB, long l2ExpirySeconds) {
        this.l1Cache = new L1MemoryCache(maxL1Size, l1ExpirySeconds);
        this.l2Cache = new L2DiskCache(cacheDir, diskBudgetMB, l2ExpirySeconds);
        this.l2Enabled = true;
    }

    public ObfuscationCache(L1MemoryCache l1Cache, L2DiskCache l2Cache) {
        this.l1Cache = l1Cache;
        this.l2Cache = l2Cache;
        this.l2Enabled = l2Cache != null;
    }

    public CacheEntry get(CacheKey key) {
        CacheEntry entry = l1Cache.get(key);
        if (entry != null) {
            LOGGER.log(Level.FINE, "L1 cache hit for {0}", key);
            return entry;
        }

        if (l2Enabled && l2Cache != null) {
            entry = l2Cache.get(key);
            if (entry != null) {
                l1Cache.put(key, entry);
                LOGGER.log(Level.FINE, "L2 cache hit (promoted to L1) for {0}", key);
                return entry;
            }
        }

        return null;
    }

    public void put(CacheKey key, CacheEntry entry) {
        l1Cache.put(key, entry);
        LOGGER.log(Level.FINE, "L1 cache put for {0}", key);

        if (l2Enabled && l2Cache != null) {
            l2Cache.put(key, entry);
        }
    }

    public void invalidate(CacheKey key) {
        l1Cache.invalidate(key);
        if (l2Enabled && l2Cache != null) {
            l2Cache.invalidate(key);
        }
    }

    public void invalidateChunk(World world, int chunkX, int chunkZ) {
        String worldName = world.getName();
        l1Cache.invalidateAll();
        if (l2Enabled && l2Cache != null) {
            l2Cache.invalidateChunk(worldName, chunkX, chunkZ);
        }
    }

    public void invalidateChunk(String worldName, int chunkX, int chunkZ, int engineMode, int configHash) {
        CacheKey key = new CacheKey(worldName, chunkX, chunkZ, engineMode, configHash);
        l1Cache.invalidate(key);
        if (l2Enabled && l2Cache != null) {
            l2Cache.invalidateChunk(worldName, chunkX, chunkZ);
        }
    }

    public void invalidateAll() {
        l1Cache.invalidateAll();
        if (l2Enabled && l2Cache != null) {
            l2Cache.invalidateAll();
        }
        LOGGER.log(Level.INFO, "Cache fully invalidated (L1{0})", l2Enabled ? "+L2" : "");
    }

    public void invalidateWorld(String worldName) {
        if (l2Enabled && l2Cache != null) {
            l2Cache.invalidateWorld(worldName);
        }
    }

    public void preloadFromL2(int maxEntries) {
        if (l2Enabled && l2Cache != null) {
            l2Cache.preloadRecent(l1Cache, maxEntries);
        }
    }

    public void shutdown() {
        if (l2Enabled && l2Cache != null) {
            l2Cache.shutdown();
        }
    }

    public List<BlockPosition> getOverlayPositions(RevealedBlocksSet revealed, int chunkX, int chunkZ, String worldName) {
        return ChunkOverlay.getPositionsInChunkFast(revealed, chunkX, chunkZ, worldName);
    }

    public L1MemoryCache getL1Cache() {
        return l1Cache;
    }

    public L2DiskCache getL2Cache() {
        return l2Cache;
    }

    public boolean isL2Enabled() {
        return l2Enabled;
    }

    public long size() {
        return l1Cache.size();
    }

    public double hitRate() {
        return l1Cache.hitRate();
    }

    public double getHitRate() {
        return l1Cache.hitRate();
    }
}
