package com.antixray.listener;

import com.antixray.AntiXrayPlugin;
import com.antixray.cache.ObfuscationCache;
import com.antixray.deobfuscation.DeobfuscationManager;
import com.antixray.util.BlockPosition;
import org.bukkit.World;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import com.antixray.detection.DetectionEngine;
import com.antixray.detection.DetectionResult;
import com.antixray.detection.PlayerStatistics;
import com.antixray.detection.StatisticsStorage;
import com.antixray.api.AlertLevel;
import com.antixray.detection.AlertManager;
import com.antixray.detection.ActionExecutor;
import com.antixray.deobfuscation.PlayerData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BlockEventListener implements Listener {

    private static final BlockFace[] ADJACENT_FACES = {
        BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    private final AntiXrayPlugin plugin;

    public BlockEventListener(AntiXrayPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block broken = event.getBlock();
        World world = broken.getWorld();
        String worldName = world.getName();

        invalidateChunkCache(world, broken.getX() >> 4, broken.getZ() >> 4);

        List<BlockPosition> exposed = new ArrayList<>();

        for (BlockFace face : ADJACENT_FACES) {
            Block neighbor = broken.getRelative(face);
            if (isHiddenAndAirExposed(neighbor, world)) {
                exposed.add(new BlockPosition(worldName, neighbor.getX(), neighbor.getY(), neighbor.getZ()));
            }
        }

        if (!exposed.isEmpty()) {
            DeobfuscationManager manager = plugin.getDeobfuscationManager();
            if (manager != null) {
                manager.queueDeobfuscation(event.getPlayer(), exposed);
            }
        }

        // Statistical Detection Integration
        if (plugin.getConfigurationManager() != null && plugin.getConfigurationManager().isDetectionEnabled()) {
            PlayerData data = plugin.getPlayerData(event.getPlayer().getUniqueId());
            if (data != null) {
                PlayerStatistics stats = data.getStatistics();
                if (stats != null) {
                    // Update play time
                    long sessionMinutes = (System.currentTimeMillis() - data.getJoinTimeMillis()) / 60000L;
                    long playTimeMinutes = sessionMinutes;
                    try {
                        long serverTicks = event.getPlayer().getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE);
                        playTimeMinutes = Math.max(sessionMinutes, serverTicks / 1200L);
                    } catch (Exception ignored) {
                    }
                    stats.updatePlayTime(playTimeMinutes);

                    // Determine if adjacent to air
                    boolean adjacentToAir = false;
                    for (BlockFace face : ADJACENT_FACES) {
                        if (broken.getRelative(face).getType().isAir()) {
                            adjacentToAir = true;
                            break;
                        }
                    }

                    Material material = broken.getType();
                    stats.onBlockBreak(
                        material,
                        broken.getY(),
                        broken.getBiome(),
                        adjacentToAir,
                        new BlockPosition(worldName, broken.getX(), broken.getY(), broken.getZ())
                    );

                    if (isOreMaterial(material)) {
                        StatisticsStorage storage = plugin.getStatisticsStorage();
                        if (storage != null) {
                            storage.recordOreBreak(
                                event.getPlayer().getUniqueId(),
                                material,
                                worldName,
                                broken.getX(),
                                broken.getY(),
                                broken.getZ(),
                                broken.getBiome()
                            );
                            storage.updateStatsInMemory(
                                event.getPlayer().getUniqueId(),
                                event.getPlayer().getName(),
                                stats
                            );
                        }
                    }

                    DetectionEngine engine = plugin.getDetectionEngine();
                    if (engine != null) {
                        DetectionResult result = engine.evaluate(event.getPlayer().getUniqueId(), stats);
                        if (result != null && result.isDetected()) {
                            plugin.triggerAlert(event.getPlayer(), result);
                        }
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block placed = event.getBlock();
        World world = placed.getWorld();
        String worldName = world.getName();

        invalidateChunkCache(world, placed.getX() >> 4, placed.getZ() >> 4);

        List<BlockPosition> exposed = new ArrayList<>();

        for (BlockFace face : ADJACENT_FACES) {
            Block neighbor = placed.getRelative(face);
            if (isHiddenAndAirExposed(neighbor, world)) {
                exposed.add(new BlockPosition(worldName, neighbor.getX(), neighbor.getY(), neighbor.getZ()));
            }
        }

        if (!exposed.isEmpty()) {
            DeobfuscationManager manager = plugin.getDeobfuscationManager();
            if (manager != null) {
                manager.queueDeobfuscation(event.getPlayer(), exposed);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) {
        Block toBlock = event.getToBlock();
        World world = toBlock.getWorld();
        String worldName = world.getName();

        Set<Long> invalidatedChunks = new HashSet<>();
        int cx = toBlock.getX() >> 4;
        int cz = toBlock.getZ() >> 4;
        if (invalidatedChunks.add(encodeChunk(cx, cz))) {
            invalidateChunkCache(world, cx, cz);
        }

        Block fromBlock = event.getBlock();
        int fromCx = fromBlock.getX() >> 4;
        int fromCz = fromBlock.getZ() >> 4;
        if (invalidatedChunks.add(encodeChunk(fromCx, fromCz))) {
            invalidateChunkCache(world, fromCx, fromCz);
        }

        List<BlockPosition> exposed = new ArrayList<>();

        for (BlockFace face : ADJACENT_FACES) {
            Block neighbor = toBlock.getRelative(face);
            if (isHiddenAndAirExposed(neighbor, world)) {
                exposed.add(new BlockPosition(worldName, neighbor.getX(), neighbor.getY(), neighbor.getZ()));
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPistonExtend(BlockPistonExtendEvent event) {
        Block piston = event.getBlock();
        World world = piston.getWorld();
        String worldName = world.getName();
        org.bukkit.block.BlockFace direction = event.getDirection();

        Set<Long> invalidatedChunks = new HashSet<>();

        int pistonCx = piston.getX() >> 4;
        int pistonCz = piston.getZ() >> 4;
        if (invalidatedChunks.add(encodeChunk(pistonCx, pistonCz))) {
            invalidateChunkCache(world, pistonCx, pistonCz);
        }

        List<BlockPosition> exposed = new ArrayList<>();

        for (Block moved : event.getBlocks()) {
            Block destination = moved.getRelative(direction);
            collectNewlyExposed(destination, world, worldName, exposed);

            int destCx = destination.getX() >> 4;
            int destCz = destination.getZ() >> 4;
            if (invalidatedChunks.add(encodeChunk(destCx, destCz))) {
                invalidateChunkCache(world, destCx, destCz);
            }

            int movedCx = moved.getX() >> 4;
            int movedCz = moved.getZ() >> 4;
            if (invalidatedChunks.add(encodeChunk(movedCx, movedCz))) {
                invalidateChunkCache(world, movedCx, movedCz);
            }
        }

        Block front = piston.getRelative(direction);
        collectNewlyExposed(front, world, worldName, exposed);

        int frontCx = front.getX() >> 4;
        int frontCz = front.getZ() >> 4;
        if (invalidatedChunks.add(encodeChunk(frontCx, frontCz))) {
            invalidateChunkCache(world, frontCx, frontCz);
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPistonRetract(BlockPistonRetractEvent event) {
        Block piston = event.getBlock();
        World world = piston.getWorld();
        String worldName = world.getName();
        org.bukkit.block.BlockFace direction = event.getDirection();

        Set<Long> invalidatedChunks = new HashSet<>();

        int pistonCx = piston.getX() >> 4;
        int pistonCz = piston.getZ() >> 4;
        if (invalidatedChunks.add(encodeChunk(pistonCx, pistonCz))) {
            invalidateChunkCache(world, pistonCx, pistonCz);
        }

        List<BlockPosition> exposed = new ArrayList<>();

        if (event.isSticky()) {
            for (Block moved : event.getBlocks()) {
                Block source = moved.getRelative(direction.getOppositeFace());
                collectNewlyExposed(source, world, worldName, exposed);

                int srcCx = source.getX() >> 4;
                int srcCz = source.getZ() >> 4;
                if (invalidatedChunks.add(encodeChunk(srcCx, srcCz))) {
                    invalidateChunkCache(world, srcCx, srcCz);
                }

                int movedCx = moved.getX() >> 4;
                int movedCz = moved.getZ() >> 4;
                if (invalidatedChunks.add(encodeChunk(movedCx, movedCz))) {
                    invalidateChunkCache(world, movedCx, movedCz);
                }
            }
        }

        Block pistonFront = piston.getRelative(direction);
        collectNewlyExposed(pistonFront, world, worldName, exposed);

        int frontCx = pistonFront.getX() >> 4;
        int frontCz = pistonFront.getZ() >> 4;
        if (invalidatedChunks.add(encodeChunk(frontCx, frontCz))) {
            invalidateChunkCache(world, frontCx, frontCz);
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

    private void collectNewlyExposed(Block block, World world, String worldName, List<BlockPosition> results) {
        for (BlockFace face : ADJACENT_FACES) {
            Block neighbor = block.getRelative(face);
            if (isHiddenAndAirExposed(neighbor, world)) {
                BlockPosition pos = new BlockPosition(worldName, neighbor.getX(), neighbor.getY(), neighbor.getZ());
                if (!results.contains(pos)) {
                    results.add(pos);
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

    private void invalidateChunkCache(World world, int chunkX, int chunkZ) {
        ObfuscationCache cache = plugin.getObfuscationCache();
        if (cache != null) {
            cache.invalidateChunk(world, chunkX, chunkZ);
        }
    }

    private static long encodeChunk(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    private boolean isOreMaterial(Material material) {
        if (material == null) return false;
        String name = material.name();
        return name.endsWith("_ORE")
            || name.startsWith("RAW_")
            || name.equals("ANCIENT_DEBRIS");
    }
}
