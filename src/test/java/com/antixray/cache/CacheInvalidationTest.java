package com.antixray.cache;

import com.antixray.AntiXrayPlugin;
import com.antixray.config.ConfigurationManager;
import com.antixray.deobfuscation.DeobfuscationManager;
import com.antixray.engine.AirExposureChecker;
import com.antixray.engine.ObfuscationEngine;
import com.antixray.listener.BlockEventListener;
import com.antixray.listener.ExplosionEventListener;
import com.antixray.nms.NmsAdapter;
import com.antixray.util.MaterialSet;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CacheInvalidationTest {

    private AntiXrayPlugin plugin;
    private NmsAdapter adapter;
    private ObfuscationCache cache;
    private ObfuscationEngine engine;
    private DeobfuscationManager deobfuscationManager;
    private World world;
    private MaterialSet materialSet;
    private AirExposureChecker exposureChecker;

    @BeforeEach
    void setUp() {
        plugin = mock(AntiXrayPlugin.class);
        adapter = mock(NmsAdapter.class);
        cache = new ObfuscationCache(new L1MemoryCache(100, 300));
        engine = mock(ObfuscationEngine.class);
        deobfuscationManager = mock(DeobfuscationManager.class);
        world = mock(World.class);
        materialSet = mock(MaterialSet.class);
        exposureChecker = mock(AirExposureChecker.class);

        when(plugin.getNmsAdapter()).thenReturn(adapter);
        when(plugin.getObfuscationCache()).thenReturn(cache);
        when(plugin.getObfuscationEngine()).thenReturn(engine);
        when(plugin.getDeobfuscationManager()).thenReturn(deobfuscationManager);
        when(world.getName()).thenReturn("world");

        when(engine.getMaterialSet()).thenReturn(materialSet);
        when(engine.getExposureChecker()).thenReturn(exposureChecker);
    }

    @Test
    void blockBreak_invalidatesCorrectChunk() {
        CacheKey key1 = new CacheKey("world", 0, 0, 1, 100);
        CacheKey key2 = new CacheKey("world", 1, 1, 1, 100);
        cache.put(key1, new CacheEntry(new byte[]{1}, 1));
        cache.put(key2, new CacheEntry(new byte[]{2}, 1));

        assertEquals(2, cache.size());

        Block block = mock(Block.class);
        when(block.getX()).thenReturn(5);
        when(block.getY()).thenReturn(64);
        when(block.getZ()).thenReturn(5);
        when(block.getWorld()).thenReturn(world);
        when(block.getRelative(any(BlockFace.class))).thenReturn(mock(Block.class));

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());

        BlockBreakEvent event = mock(BlockBreakEvent.class);
        when(event.getBlock()).thenReturn(block);
        when(event.getPlayer()).thenReturn(player);

        BlockEventListener listener = new BlockEventListener(plugin);
        listener.onBlockBreak(event);

        assertEquals(0, cache.size());
    }

    @Test
    void explosion_invalidatesMultipleChunks() {
        L2DiskCache l2 = mock(L2DiskCache.class);
        ObfuscationCache cacheWithL2 = new ObfuscationCache(new L1MemoryCache(100, 300), l2);
        when(plugin.getObfuscationCache()).thenReturn(cacheWithL2);

        List<Block> destroyedBlocks = new ArrayList<>();

        Block b1 = mock(Block.class);
        when(b1.getX()).thenReturn(5);
        when(b1.getY()).thenReturn(64);
        when(b1.getZ()).thenReturn(5);
        when(b1.getWorld()).thenReturn(world);
        when(b1.getRelative(any(BlockFace.class))).thenReturn(mock(Block.class));
        destroyedBlocks.add(b1);

        Block b2 = mock(Block.class);
        when(b2.getX()).thenReturn(20);
        when(b2.getY()).thenReturn(64);
        when(b2.getZ()).thenReturn(20);
        when(b2.getWorld()).thenReturn(world);
        when(b2.getRelative(any(BlockFace.class))).thenReturn(mock(Block.class));
        destroyedBlocks.add(b2);

        EntityExplodeEvent event = mock(EntityExplodeEvent.class);
        when(event.blockList()).thenReturn(destroyedBlocks);
        when(event.getLocation()).thenReturn(new Location(world, 5, 64, 5));

        ExplosionEventListener listener = new ExplosionEventListener(plugin);
        listener.onEntityExplode(event);

        verify(l2).invalidateChunk("world", 0, 0);
        verify(l2).invalidateChunk("world", 1, 1);
    }

    @Test
    void configChange_clearsAll() {
        ConfigurationManager configMgr = mock(ConfigurationManager.class);
        when(plugin.getConfigurationManager()).thenReturn(configMgr);

        configMgr.addConfigChangeListener(() -> cache.invalidateAll());

        CacheKey key = new CacheKey("world", 0, 0, 1, 100);
        cache.put(key, new CacheEntry(new byte[]{1}, 1));
        assertNotNull(cache.get(key));

        cache.invalidateAll();

        assertNull(cache.get(key));
        assertEquals(0, cache.size());
    }

    @Test
    void configHashMismatch_autoInvalidates() {
        L2DiskCache l2 = mock(L2DiskCache.class);
        ObfuscationCache cacheWithL2 = new ObfuscationCache(new L1MemoryCache(100, 300), l2);

        CacheKey keyWithOldHash = new CacheKey("world", 0, 0, 1, 111);
        CacheKey keyWithNewHash = new CacheKey("world", 0, 0, 1, 222);

        cacheWithL2.put(keyWithOldHash, new CacheEntry(new byte[]{42}, 1));

        assertNotNull(cacheWithL2.get(keyWithOldHash));
        assertNull(cacheWithL2.get(keyWithNewHash));

        java.io.File tempDir = new java.io.File(System.getProperty("java.io.tmpdir"), "antixray-test-" + UUID.randomUUID());
        L2DiskCache realL2 = new L2DiskCache(tempDir, 10, 300);
        try {
            realL2.put(keyWithOldHash, new CacheEntry(new byte[]{99}, 1));

            int maxWait = 2000;
            int waited = 0;
            while (realL2.getWriteQueueSize() > 0 && waited < maxWait) {
                try { Thread.sleep(50); } catch (InterruptedException ignored) {}
                waited += 50;
            }
            try { Thread.sleep(200); } catch (InterruptedException ignored) {}

            CacheEntry entry1 = realL2.get(keyWithOldHash);
            assertNotNull(entry1);
            assertEquals(99, entry1.getObfuscatedData()[0]);

            CacheEntry entry2 = realL2.get(keyWithNewHash);
            assertNull(entry2);
        } finally {
            realL2.shutdown();
            deleteDirectory(tempDir);
        }
    }

    private void deleteDirectory(java.io.File dir) {
        java.io.File[] files = dir.listFiles();
        if (files != null) {
            for (java.io.File f : files) {
                if (f.isDirectory()) {
                    deleteDirectory(f);
                } else {
                    f.delete();
                }
            }
        }
        dir.delete();
    }
}
