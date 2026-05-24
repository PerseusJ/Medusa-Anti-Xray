package com.antixray.listener;

import com.antixray.AntiXrayPlugin;
import com.antixray.cache.ObfuscationCache;
import com.antixray.deobfuscation.DeobfuscationManager;
import com.antixray.util.BlockPosition;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.event.block.BlockExplodeEvent;

public class ExplosionEventListener implements Listener {

    private static final BlockFace[] ADJACENT_FACES = {
        BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    private final AntiXrayPlugin plugin;

    public ExplosionEventListener(AntiXrayPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        List<Block> destroyed = event.blockList();
        if (destroyed.isEmpty()) return;

        World world = event.getLocation().getWorld();
        if (world == null) return;
        String worldName = world.getName();

        Set<Long> invalidatedChunks = new HashSet<>();
        for (Block destroyedBlock : destroyed) {
            int cx = destroyedBlock.getX() >> 4;
            int cz = destroyedBlock.getZ() >> 4;
            if (invalidatedChunks.add(encodeChunk(cx, cz))) {
                invalidateChunkCache(world, cx, cz);
            }
        }

        Set<Long> visited = new HashSet<>();
        List<BlockPosition> exposed = new ArrayList<>();

        for (Block destroyedBlock : destroyed) {
            for (BlockFace face : ADJACENT_FACES) {
                Block neighbor = destroyedBlock.getRelative(face);
                long key = BlockPosition.encodeToLong(neighbor.getX(), neighbor.getY(), neighbor.getZ());
                if (visited.add(key)) {
                    if (isHiddenAndAirExposed(neighbor, world)) {
                        exposed.add(new BlockPosition(worldName, neighbor.getX(), neighbor.getY(), neighbor.getZ()));
                    }
                }
            }
        }

        if (!exposed.isEmpty()) {
            DeobfuscationManager manager = plugin.getDeobfuscationManager();
            if (manager != null) {
                for (org.bukkit.entity.Player player : world.getPlayers()) {
                    manager.queueDeobfuscation(player, exposed);
                }
            }
        }
    }

    private boolean isHiddenAndAirExposed(Block block, World world) {
        int blockStateId = plugin.getNmsAdapter().getBlockStateAt(world, block.getX(), block.getY(), block.getZ());
        if (blockStateId == -1) return false;
        if (!plugin.getObfuscationEngine().getMaterialSet().isHidden(blockStateId)) return false;
        return plugin.getObfuscationEngine().getExposureChecker().isAirExposed(world, block.getX(), block.getY(), block.getZ());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        List<Block> destroyed = event.blockList();
        if (destroyed.isEmpty()) return;

        World world = event.getBlock().getWorld();
        String worldName = world.getName();

        Set<Long> invalidatedChunks = new HashSet<>();
        for (Block destroyedBlock : destroyed) {
            int cx = destroyedBlock.getX() >> 4;
            int cz = destroyedBlock.getZ() >> 4;
            if (invalidatedChunks.add(encodeChunk(cx, cz))) {
                invalidateChunkCache(world, cx, cz);
            }
        }

        Set<Long> visited = new HashSet<>();
        List<BlockPosition> exposed = new ArrayList<>();

        for (Block destroyedBlock : destroyed) {
            for (BlockFace face : ADJACENT_FACES) {
                Block neighbor = destroyedBlock.getRelative(face);
                long key = BlockPosition.encodeToLong(neighbor.getX(), neighbor.getY(), neighbor.getZ());
                if (visited.add(key)) {
                    if (isHiddenAndAirExposed(neighbor, world)) {
                        exposed.add(new BlockPosition(worldName, neighbor.getX(), neighbor.getY(), neighbor.getZ()));
                    }
                }
            }
        }

        if (!exposed.isEmpty()) {
            DeobfuscationManager manager = plugin.getDeobfuscationManager();
            if (manager != null) {
                for (org.bukkit.entity.Player player : world.getPlayers()) {
                    manager.queueDeobfuscation(player, exposed);
                }
            }
        }
    }

    private void invalidateChunkCache(World world, int chunkX, int chunkZ) {
        ObfuscationCache cache = plugin.getObfuscationCache();
        if (cache != null) {
            cache.invalidateChunk(world, chunkX, chunkZ);
        }
    }

    private static long encodeChunk(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }
}
