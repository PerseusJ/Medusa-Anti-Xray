package com.antixray.deobfuscation;

import com.antixray.AntiXrayPlugin;
import com.antixray.config.ConfigurationManager;
import com.antixray.config.WorldConfig;
import com.antixray.nms.NmsAdapter;
import com.antixray.util.BlockPosition;
import com.antixray.util.FoliaSchedulerAdapter;
import com.antixray.util.MaterialSet;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class ProximityTracker {

    private final AntiXrayPlugin plugin;
    private final NmsAdapter nmsAdapter;
    private final MaterialSet materialSet;
    private final VisibilityResolver visibilityResolver;

    private final int updateRadius;
    private final int updateRadiusY;
    private final int checkIntervalTicks;
    private final double movementThreshold;
    private final boolean reObfuscateEnabled;
    private final long reObfuscateDelayTicks;
    private final double playerFov;

    private volatile int taskId = -1;
    private volatile boolean running = false;

    public ProximityTracker(AntiXrayPlugin plugin, NmsAdapter nmsAdapter,
                            MaterialSet materialSet,
                            boolean frustumCulling, boolean raycastLineOfSight,
                            int updateRadius, int updateRadiusY,
                            int checkIntervalTicks, double movementThreshold,
                            boolean reObfuscateEnabled, long reObfuscateDelayTicks,
                            double playerFov,
                            int raycastMaxSteps, int raycastPadding) {
        this.plugin = plugin;
        this.nmsAdapter = nmsAdapter;
        this.materialSet = materialSet;
        this.visibilityResolver = new VisibilityResolver(
                nmsAdapter, materialSet, frustumCulling, raycastLineOfSight,
                raycastMaxSteps, raycastPadding);
        this.updateRadius = updateRadius;
        this.updateRadiusY = updateRadiusY;
        this.checkIntervalTicks = checkIntervalTicks;
        this.movementThreshold = movementThreshold;
        this.reObfuscateEnabled = reObfuscateEnabled;
        this.reObfuscateDelayTicks = reObfuscateDelayTicks;
        this.playerFov = playerFov;
    }

    public void start() {
        if (running) return;
        running = true;
        taskId = plugin.getSchedulerAdapter().runAsyncTimer(
                this::asyncTick,
                checkIntervalTicks,
                checkIntervalTicks
        );
        plugin.getLogger().info("ProximityTracker started (interval=" + checkIntervalTicks
                + "t, radius=" + updateRadius + ", radiusY=" + updateRadiusY
                + ", threshold=" + movementThreshold + ")");
    }

    public void stop() {
        if (!running) return;
        running = false;
        if (taskId != -1) {
            plugin.getSchedulerAdapter().cancelTask(taskId);
            taskId = -1;
        }
    }

    public boolean isRunning() {
        return running;
    }

    public VisibilityResolver getVisibilityResolver() {
        return visibilityResolver;
    }

    private static long getCurrentTick() {
        try {
            Method method = Server.class.getMethod("getCurrentTick");
            return (long) method.invoke(Bukkit.getServer());
        } catch (Exception ignored) {
            return System.currentTimeMillis() / 50L;
        }
    }

    private void asyncTick() {
        if (!running) return;

        long currentTick = getCurrentTick();

        try {
            for (Map.Entry<UUID, PlayerData> entry : plugin.getAllPlayerData().entrySet()) {
                UUID playerId = entry.getKey();
                PlayerData data = entry.getValue();

                Player player = Bukkit.getPlayer(playerId);
                if (player == null || !player.isOnline()) continue;

                if (player.getGameMode() == GameMode.SPECTATOR) continue;

                World world = player.getWorld();
                String worldName = world.getName();

                Location current = player.getEyeLocation();
                Location last = data.getLastCheckedPosition();

                WorldConfig worldConfig = plugin.getConfigurationManager().getWorldConfig(world);

            org.bukkit.util.Vector velocity = player.getVelocity();
            if (velocity != null) {
                double speed = velocity.lengthSquared();
                double thresholdSq = worldConfig.getElytraVelocityThreshold();
                thresholdSq = thresholdSq * thresholdSq;
                data.updateElytraState(speed > thresholdSq);
            } else {
                data.updateElytraState(false);
            }

                int effectiveRadiusXZ = data.applyElytraRadius(worldConfig.getUpdateRadius());
                int effectiveRadiusY = data.applyElytraRadius(updateRadiusY);

                if (last != null && !hasMovedPastThreshold(current, last, movementThreshold)) {
                    continue;
                }

                data.setLastCheckedPosition(current.clone());
                data.setLastCheckTick(currentTick);

                visibilityResolver.updateFrustum(player, playerFov);

                scanAndReveal(player, data, world, worldName, current, currentTick, effectiveRadiusXZ, effectiveRadiusY);
                checkReObfuscation(player, data, world, worldName, current, currentTick, effectiveRadiusXZ, effectiveRadiusY);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error in ProximityTracker async tick", e);
        }
    }

    private void scanAndReveal(Player player, PlayerData data,
                               World world, String worldName,
                               Location eye, long currentTick,
                               int radiusXZ, int radiusY) {

        int cx = eye.getBlockX();
        int cy = (int) eye.getY();
        int cz = eye.getBlockZ();

        int minX = cx - radiusXZ;
        int maxX = cx + radiusXZ;
        int minY = Math.max(world.getMinHeight(), cy - radiusY);
        int maxY = Math.min(world.getMaxHeight() - 1, cy + radiusY);
        int minZ = cz - radiusXZ;
        int maxZ = cz + radiusXZ;

        int minChunkX = minX >> 4;
        int maxChunkX = maxX >> 4;
        int minSectionY = minY >> 4;
        int maxSectionY = maxY >> 4;
        int minChunkZ = minZ >> 4;
        int maxChunkZ = maxZ >> 4;

        RevealedBlocksSet revealed = data.getRevealedBlocks();
        List<BlockPosition> newRevealed = null;

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                for (int sectionY = minSectionY; sectionY <= maxSectionY; sectionY++) {

                    int sectionMinX = Math.max(minX, chunkX << 4);
                    int sectionMaxX = Math.min(maxX, (chunkX << 4) + 15);
                    int sectionMinY = Math.max(minY, sectionY << 4);
                    int sectionMaxY = Math.min(maxY, (sectionY << 4) + 15);
                    int sectionMinZ = Math.max(minZ, chunkZ << 4);
                    int sectionMaxZ = Math.min(maxZ, (chunkZ << 4) + 15);

                    for (int bx = sectionMinX; bx <= sectionMaxX; bx++) {
                        for (int by = sectionMinY; by <= sectionMaxY; by++) {
                            for (int bz = sectionMinZ; bz <= sectionMaxZ; bz++) {
                                int blockStateId = nmsAdapter.getBlockStateAt(world, bx, by, bz);
                                if (blockStateId == -1) continue;
                                if (!materialSet.isHidden(blockStateId)) continue;

                                BlockPosition pos = new BlockPosition(worldName, bx, by, bz);
                                if (revealed.contains(pos)) continue;

                                if (!visibilityResolver.isVisible(player, bx, by, bz)) continue;

                                if (newRevealed == null) {
                                    newRevealed = new ArrayList<>();
                                }
                                newRevealed.add(pos);
                            }
                        }
                    }
                }
            }
        }

        if (newRevealed == null) return;

        for (BlockPosition pos : newRevealed) {
            revealed.add(pos, currentTick);
        }

        dispatchRevealPackets(player, world, worldName, newRevealed);
    }

    private void checkReObfuscation(Player player, PlayerData data,
                                    World world, String worldName,
                                    Location eye, long currentTick,
                                    int radiusXZ, int radiusY) {

        if (!reObfuscateEnabled) return;

        RevealedBlocksSet revealed = data.getRevealedBlocks();
        long expireTick = currentTick - reObfuscateDelayTicks;

        List<BlockPosition> candidates = revealed.getRevealedBeforeTickNoRemove(expireTick, worldName);
        if (candidates.isEmpty()) return;

        int cx = eye.getBlockX();
        int cy = (int) eye.getY();
        int cz = eye.getBlockZ();

        List<BlockPosition> expired = new ArrayList<>();
        for (BlockPosition pos : candidates) {
            if (pos.getWorldName().equals(worldName)
                    && Math.abs(pos.getX() - cx) <= radiusXZ
                    && Math.abs(pos.getY() - cy) <= radiusY
                    && Math.abs(pos.getZ() - cz) <= radiusXZ) {
                continue;
            }
            expired.add(pos);
        }

        if (expired.isEmpty()) return;

        for (BlockPosition pos : expired) {
            revealed.remove(pos);
        }

        dispatchReObfuscatePackets(player, worldName, expired);
    }

    private void dispatchRevealPackets(Player player, World world,
                                        String worldName,
                                        List<BlockPosition> positions) {

        Map<Location, Integer> changes = new HashMap<>(positions.size());
        for (BlockPosition pos : positions) {
            int realStateId = nmsAdapter.getBlockStateAt(world, pos.getX(), pos.getY(), pos.getZ());
            if (realStateId == -1) continue;
            changes.put(new Location(world, pos.getX(), pos.getY(), pos.getZ()), realStateId);
        }

        if (changes.isEmpty()) return;

        plugin.getSchedulerAdapter().executeForEntity(player, () -> {
            Player p = Bukkit.getPlayer(player.getUniqueId());
            if (p == null || !p.isOnline()) return;
            if (!p.getWorld().getName().equals(worldName)) return;

            sendBlockUpdatesBatched(p, world, changes);
        });
    }

    private void dispatchReObfuscatePackets(Player player, String worldName,
                                            List<BlockPosition> positions) {

        DeobfuscationManager manager = plugin.getDeobfuscationManager();
        if (manager == null) return;

        manager.queueReObfuscation(player, positions);
    }

    private void sendBlockUpdatesBatched(Player player, World world, Map<Location, Integer> changes) {
        Map<Long, Map<Location, Integer>> byChunk = new HashMap<>();
        for (Map.Entry<Location, Integer> entry : changes.entrySet()) {
            Location loc = entry.getKey();
            long chunkKey = getChunkKey(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
            byChunk.computeIfAbsent(chunkKey, k -> new HashMap<>()).put(loc, entry.getValue());
        }

        for (Map.Entry<Long, Map<Location, Integer>> chunkEntry : byChunk.entrySet()) {
            Map<Location, Integer> chunkChanges = chunkEntry.getValue();
            if (chunkChanges.size() == 1) {
                Map.Entry<Location, Integer> single = chunkChanges.entrySet().iterator().next();
                Object packet = nmsAdapter.createBlockUpdatePacket(single.getKey(), single.getValue());
                if (packet != null) {
                    nmsAdapter.sendPacket(player, packet);
                }
            } else {
                int chunkX = extractChunkXFromKey(chunkEntry.getKey());
                int chunkZ = extractChunkZFromKey(chunkEntry.getKey());
                try {
                    Object packet = nmsAdapter.createMultiBlockUpdatePacket(world, chunkX, chunkZ, chunkChanges);
                    if (packet != null) {
                        nmsAdapter.sendPacket(player, packet);
                    }
                } catch (Exception e) {
                    plugin.getLogger().log(Level.FINE,
                            "Multi-block update failed, falling back to individual packets for player "
                                    + player.getName(), e);
                    for (Map.Entry<Location, Integer> entry : chunkChanges.entrySet()) {
                        try {
                            Object packet = nmsAdapter.createBlockUpdatePacket(entry.getKey(), entry.getValue());
                            if (packet != null) {
                                nmsAdapter.sendPacket(player, packet);
                            }
                        } catch (Exception ex) {
                            plugin.getLogger().log(Level.FINE,
                                    "Failed to send block update to " + player.getName(), ex);
                        }
                    }
                }
            }
        }
    }

    private static long getChunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX & 0xFFFFFFFFL) << 32 | ((long) chunkZ & 0xFFFFFFFFL);
    }

    private static int extractChunkXFromKey(long chunkKey) {
        return (int) (chunkKey >> 32);
    }

    private static int extractChunkZFromKey(long chunkKey) {
        return (int) chunkKey;
    }

    private boolean hasMovedPastThreshold(Location current, Location last) {
        if (!current.getWorld().getName().equals(last.getWorld().getName())) return true;
        double dx = current.getX() - last.getX();
        double dy = current.getY() - last.getY();
        double dz = current.getZ() - last.getZ();
        return (dx * dx + dy * dy + dz * dz) > (movementThreshold * movementThreshold);
    }

    private static boolean hasMovedPastThreshold(Location current, Location last, double threshold) {
        if (!current.getWorld().getName().equals(last.getWorld().getName())) return true;
        double dx = current.getX() - last.getX();
        double dy = current.getY() - last.getY();
        double dz = current.getZ() - last.getZ();
        return (dx * dx + dy * dy + dz * dz) > (threshold * threshold);
    }
}
