package com.antixray.deobfuscation;

import com.antixray.AntiXrayPlugin;
import com.antixray.config.WorldConfig;
import com.antixray.nms.NmsAdapter;
import com.antixray.util.BlockPosition;
import com.antixray.util.FoliaSchedulerAdapter;
import com.antixray.util.MaterialSet;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class DeobfuscationManager {

	private static final int SECTION_BLOCK_COUNT = 4096;
	private static final int MULTI_BLOCK_THRESHOLD = 4;
    private static final int CHUNK_DATA_THRESHOLD = 64;

	private final AntiXrayPlugin plugin;
	private final NmsAdapter nmsAdapter;
	private final MaterialSet materialSet;
	private final UpdatePacketBuilder packetBuilder;

    private final Map<UUID, List<BlockPosition>> pendingRequests = new HashMap<>();
    private final Map<UUID, List<BlockPosition>> deferredRequests = new HashMap<>();
    private final Map<UUID, List<BlockPosition>> pendingReObfuscateRequests = new HashMap<>();
    private final Map<UUID, List<BlockPosition>> deferredReObfuscateRequests = new HashMap<>();
    private volatile int flushTaskId = -1;

	public DeobfuscationManager(AntiXrayPlugin plugin, NmsAdapter nmsAdapter, MaterialSet materialSet) {
		this.plugin = plugin;
		this.nmsAdapter = nmsAdapter;
		this.materialSet = materialSet;
		this.packetBuilder = new UpdatePacketBuilder(nmsAdapter);
	}

    public void startFlushTask() {
        if (flushTaskId != -1) return;
        flushTaskId = plugin.getSchedulerAdapter().runTaskTimer(this::flushPendingBatches, 1L, 1L);
    }

    public void stopFlushTask() {
        if (flushTaskId != -1) {
            plugin.getSchedulerAdapter().cancelTask(flushTaskId);
            flushTaskId = -1;
        }
    }

    public void queueDeobfuscation(Player player, List<BlockPosition> positions) {
        if (positions.isEmpty()) return;

        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        if (data == null) return;

        World world = player.getWorld();
        if (world == null) return;
        String worldName = world.getName();
        int maxUpdates = plugin.getConfigurationManager()
                .getWorldConfig(world).getMaxDeobfuscationUpdatesPerTick();
        long currentTick = getCurrentTick();
        RevealedBlocksSet revealed = data.getRevealedBlocks();
        List<BlockPosition> toReveal = new ArrayList<>();

        for (BlockPosition pos : positions) {
            if (!pos.getWorldName().equals(worldName)) continue;
            if (revealed.contains(pos)) continue;

            int blockStateId = nmsAdapter.getBlockStateAt(world, pos.getX(), pos.getY(), pos.getZ());
            if (blockStateId == -1) continue;
            if (!materialSet.isHidden(blockStateId)) continue;

            org.bukkit.block.data.BlockData blockData = nmsAdapter.getBlockDataFromId(blockStateId);
            org.bukkit.Material realMaterial = blockData != null ? blockData.getMaterial() : org.bukkit.Material.AIR;
            org.bukkit.Material obfuscatedMaterial = org.bukkit.Material.AIR;
            if (plugin.getObfuscationEngine() != null) {
                int replacementId = plugin.getObfuscationEngine().getReplacementBlockStateId(world, pos.getY());
                org.bukkit.block.data.BlockData repData = nmsAdapter.getBlockDataFromId(replacementId);
                if (repData != null) {
                    obfuscatedMaterial = repData.getMaterial();
                }
            }

            com.antixray.api.BlockVisibilityEvent event = new com.antixray.api.BlockVisibilityEvent(
                player, new org.bukkit.Location(world, pos.getX(), pos.getY(), pos.getZ()), realMaterial, obfuscatedMaterial
            );
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                continue;
            }

            toReveal.add(pos);
            revealed.add(pos, currentTick);
        }

        if (toReveal.isEmpty()) return;

        synchronized (pendingRequests) {
            List<BlockPosition> existing = pendingRequests.get(player.getUniqueId());
            int currentCount = existing != null ? existing.size() : 0;
            int availableSlots = Math.max(0, maxUpdates - currentCount);

            if (availableSlots >= toReveal.size()) {
                pendingRequests.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>()).addAll(toReveal);
            } else if (availableSlots > 0) {
                List<BlockPosition> fit = toReveal.subList(0, availableSlots);
                List<BlockPosition> excess = new ArrayList<>(toReveal.subList(availableSlots, toReveal.size()));
                pendingRequests.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>()).addAll(fit);
                synchronized (deferredRequests) {
                    deferredRequests.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>()).addAll(excess);
                }
            } else {
                synchronized (deferredRequests) {
                    deferredRequests.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>()).addAll(toReveal);
                }
            }
        }
    }

    public void deobfuscateAround(Player player, Location center) {
        if (center.getWorld() == null) return;

        if (player.getGameMode() == GameMode.SPECTATOR) return;

        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        if (data == null) return;

		World world = center.getWorld();
		String worldName = world.getName();
		int updateRadius = plugin.getConfigurationManager().getWorldConfig(world).getUpdateRadius();

		int cx = center.getBlockX();
		int cy = center.getBlockY();
		int cz = center.getBlockZ();

		int minX = cx - updateRadius;
		int maxX = cx + updateRadius;
		int minY = Math.max(world.getMinHeight(), cy - updateRadius);
		int maxY = Math.min(world.getMaxHeight() - 1, cy + updateRadius);
		int minZ = cz - updateRadius;
		int maxZ = cz + updateRadius;

		long currentTick = getCurrentTick();
		RevealedBlocksSet revealed = data.getRevealedBlocks();
		List<BlockPosition> toReveal = new ArrayList<>();

		for (int bx = minX; bx <= maxX; bx++) {
			for (int by = minY; by <= maxY; by++) {
				for (int bz = minZ; bz <= maxZ; bz++) {
					int blockStateId = nmsAdapter.getBlockStateAt(world, bx, by, bz);
					if (blockStateId == -1) continue;
					if (!materialSet.isHidden(blockStateId)) continue;

					BlockPosition pos = new BlockPosition(worldName, bx, by, bz);
					if (revealed.contains(pos)) continue;

					toReveal.add(pos);
					revealed.add(pos, currentTick);
				}
			}
		}

        if (toReveal.isEmpty()) return;

        synchronized (pendingRequests) {
            pendingRequests.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>()).addAll(toReveal);
        }
    }

    public void queueReObfuscation(Player player, List<BlockPosition> positions) {
        if (positions.isEmpty()) return;

        synchronized (pendingReObfuscateRequests) {
            pendingReObfuscateRequests.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>())
                    .addAll(positions);
        }
    }

    void flushPendingBatches() {
        flushDeobfuscateBatches();
        flushReObfuscateBatches();
    }

    private void flushDeobfuscateBatches() {
        // Move all deferred into pending first (rate limit enforced at queue time)
        mergeDeferredIntoPending();

        // Merge again until deferred is empty — deferred positions may have been
        // added during the first merge (e.g. offline player re-defer in flush).
        // Also handles the case where pending was empty before merge but deferred
        // was not, ensuring merged data is not skipped by the early-return check.
        while (true) {
            synchronized (pendingRequests) {
                if (pendingRequests.isEmpty()) {
                    synchronized (deferredRequests) {
                        if (deferredRequests.isEmpty()) {
                            return;
                        }
                    }
                    break;
                }
                break;
            }
        }

        mergeDeferredIntoPending();

        Map<UUID, List<BlockPosition>> batchToProcess;

        synchronized (pendingRequests) {
            if (pendingRequests.isEmpty()) {
                return;
            }
            batchToProcess = new HashMap<>(pendingRequests);
            pendingRequests.clear();
        }

        for (Map.Entry<UUID, List<BlockPosition>> entry : batchToProcess.entrySet()) {
            UUID playerId = entry.getKey();
            List<BlockPosition> positions = entry.getValue();

            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                synchronized (deferredRequests) {
                    deferredRequests.computeIfAbsent(playerId, k -> new ArrayList<>()).addAll(positions);
                }
                continue;
            }
            if (!player.isOnline()) continue;

            World world = player.getWorld();
            String worldName = world.getName();

            List<BlockPosition> filtered = new ArrayList<>();
            for (BlockPosition pos : positions) {
                if (pos.getWorldName().equals(worldName)) {
                    filtered.add(pos);
                }
            }

            int maxUpdates = plugin.getConfigurationManager()
                    .getWorldConfig(world).getMaxDeobfuscationUpdatesPerTick();
            List<BlockPosition> toProcessNow;
            List<BlockPosition> excess;
            if (filtered.size() > maxUpdates) {
                toProcessNow = filtered.subList(0, maxUpdates);
                excess = new ArrayList<>(filtered.subList(maxUpdates, filtered.size()));
            } else {
                toProcessNow = filtered;
                excess = null;
            }

            if (!toProcessNow.isEmpty()) {
                sendBatchedUpdates(player, world, worldName, toProcessNow);
            }

            if (excess != null && !excess.isEmpty()) {
                synchronized (deferredRequests) {
                    deferredRequests.computeIfAbsent(playerId, k -> new ArrayList<>()).addAll(excess);
                }
            }
        }
    }

    private void flushReObfuscateBatches() {
        mergeDeferredReObfuscateIntoPending();

        if (pendingReObfuscateRequests.isEmpty()) return;

        Map<UUID, List<BlockPosition>> batchToProcess;

        synchronized (pendingReObfuscateRequests) {
            if (pendingReObfuscateRequests.isEmpty()) return;
            batchToProcess = new HashMap<>(pendingReObfuscateRequests);
            pendingReObfuscateRequests.clear();
        }

        for (Map.Entry<UUID, List<BlockPosition>> entry : batchToProcess.entrySet()) {
            UUID playerId = entry.getKey();
            List<BlockPosition> positions = entry.getValue();

            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) continue;

            World world = player.getWorld();
            String worldName = world.getName();

            List<BlockPosition> filtered = new ArrayList<>();
            for (BlockPosition pos : positions) {
                if (pos.getWorldName().equals(worldName)) {
                    filtered.add(pos);
                }
            }

            if (filtered.isEmpty()) continue;

            int maxUpdates = plugin.getConfigurationManager()
                    .getWorldConfig(world).getMaxDeobfuscationUpdatesPerTick();
            List<BlockPosition> toProcessNow;
            List<BlockPosition> excess;
            if (filtered.size() > maxUpdates) {
                toProcessNow = filtered.subList(0, maxUpdates);
                excess = new ArrayList<>(filtered.subList(maxUpdates, filtered.size()));
            } else {
                toProcessNow = filtered;
                excess = null;
            }

            if (!toProcessNow.isEmpty()) {
                sendReObfuscateBatchedUpdates(player, world, toProcessNow);
            }

            if (excess != null && !excess.isEmpty()) {
                synchronized (deferredReObfuscateRequests) {
                    deferredReObfuscateRequests.computeIfAbsent(playerId, k -> new ArrayList<>()).addAll(excess);
                }
            }
        }
    }

    private void mergeDeferredReObfuscateIntoPending() {
        if (deferredReObfuscateRequests.isEmpty()) return;

        Map<UUID, List<BlockPosition>> toProcess;
        synchronized (deferredReObfuscateRequests) {
            if (deferredReObfuscateRequests.isEmpty()) return;
            toProcess = new HashMap<>(deferredReObfuscateRequests);
            deferredReObfuscateRequests.clear();
        }

        synchronized (pendingReObfuscateRequests) {
            for (Map.Entry<UUID, List<BlockPosition>> entry : toProcess.entrySet()) {
                pendingReObfuscateRequests.computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                        .addAll(entry.getValue());
            }
        }
    }

    void sendReObfuscateBatchedUpdates(Player player, World world, List<BlockPosition> positions) {
        int deepslateBelowY = plugin.getConfigurationManager()
                .getWorldConfig(world).getDeepslateBelowY();
        String environment = world.getEnvironment().name();

        Map<Long, Map<Location, Integer>> bySection = new HashMap<>();

        for (BlockPosition pos : positions) {
            int blockStateId = nmsAdapter.getBlockStateAt(world, pos.getX(), pos.getY(), pos.getZ());
            if (blockStateId == -1) continue;
            if (!materialSet.isHidden(blockStateId)) continue;

            int replacementId = materialSet.getReplacement(pos.getY(), deepslateBelowY, environment);
            long sectionKey = sectionKey(pos.getChunkX(), pos.getSectionY(), pos.getChunkZ());
            Location loc = new Location(world, pos.getX(), pos.getY(), pos.getZ());
            bySection.computeIfAbsent(sectionKey, k -> new HashMap<>()).put(loc, replacementId);
        }

        if (bySection.isEmpty()) return;

        for (Map.Entry<Long, Map<Location, Integer>> sectionEntry : bySection.entrySet()) {
            long sectionKey = sectionEntry.getKey();
            Map<Location, Integer> sectionChanges = sectionEntry.getValue();
            int changeCount = sectionChanges.size();

            int chunkX = extractChunkXFromKey(sectionKey);
            int chunkZ = extractChunkZFromKey(sectionKey);

            if (changeCount >= MULTI_BLOCK_THRESHOLD) {
                sendMultiBlockUpdate(player, world, chunkX, chunkZ, sectionChanges);
        } else {
            sendIndividualBlockUpdates(player, sectionChanges);
        }
        }
    }

    private void mergeDeferredIntoPending() {
        if (deferredRequests.isEmpty()) return;

        Map<UUID, List<BlockPosition>> toProcess;
        synchronized (deferredRequests) {
            if (deferredRequests.isEmpty()) return;
            toProcess = new HashMap<>(deferredRequests);
            deferredRequests.clear();
        }

        // Merge all deferred positions into pending unconditionally.
        // The online/offline check and rate limiting are handled by flushPendingBatches
        // when it processes the pending map.
        synchronized (pendingRequests) {
            for (Map.Entry<UUID, List<BlockPosition>> entry : toProcess.entrySet()) {
                pendingRequests.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).addAll(entry.getValue());
            }
        }
    }

	void sendBatchedUpdates(Player player, World world, String worldName, List<BlockPosition> positions) {
		Map<Long, Map<Location, Integer>> bySection = new HashMap<>();
		Map<Long, List<BlockPosition>> sectionPositions = new HashMap<>();

		for (BlockPosition pos : positions) {
			long sectionKey = sectionKey(pos.getChunkX(), pos.getSectionY(), pos.getChunkZ());

			int blockStateId = nmsAdapter.getBlockStateAt(world, pos.getX(), pos.getY(), pos.getZ());
			if (blockStateId == -1) continue;

			Location loc = new Location(world, pos.getX(), pos.getY(), pos.getZ());
			bySection.computeIfAbsent(sectionKey, k -> new HashMap<>()).put(loc, blockStateId);
			sectionPositions.computeIfAbsent(sectionKey, k -> new ArrayList<>()).add(pos);
		}

		if (bySection.isEmpty()) return;

		for (Map.Entry<Long, Map<Location, Integer>> sectionEntry : bySection.entrySet()) {
			long sectionKey = sectionEntry.getKey();
			Map<Location, Integer> sectionChanges = sectionEntry.getValue();
			int changeCount = sectionChanges.size();

			int chunkX = extractChunkXFromKey(sectionKey);
			int sectionY = extractSectionYFromKey(sectionKey);
			int chunkZ = extractChunkZFromKey(sectionKey);

			if (changeCount > CHUNK_DATA_THRESHOLD) {
				sendChunkDataFallback(player, world, chunkX, chunkZ, sectionChanges);
			} else if (changeCount >= MULTI_BLOCK_THRESHOLD) {
				sendMultiBlockUpdate(player, world, chunkX, chunkZ, sectionChanges);
			} else {
				sendIndividualBlockUpdates(player, sectionChanges);
			}
		}
	}

	private void sendChunkDataFallback(Player player, World world, int chunkX, int chunkZ,
									   Map<Location, Integer> sectionChanges) {
		try {
			Object packet = packetBuilder.buildChunkDataPacket(world, chunkX, chunkZ);
			if (packet != null) {
				nmsAdapter.sendPacket(player, packet);
			} else {
				sendMultiBlockUpdate(player, world, chunkX, chunkZ, sectionChanges);
			}
		} catch (Exception e) {
			plugin.getLogger().log(Level.FINE,
				"Chunk data packet failed, falling back to multi-block update for player "
					+ player.getName(), e);
			sendMultiBlockUpdate(player, world, chunkX, chunkZ, sectionChanges);
		}
	}

	private void sendMultiBlockUpdate(Player player, World world, int chunkX, int chunkZ,
									  Map<Location, Integer> changes) {
		try {
			Object packet = packetBuilder.buildMultiBlockUpdate(world, chunkX, chunkZ, changes);
			if (packet != null) {
				nmsAdapter.sendPacket(player, packet);
			} else {
				sendIndividualBlockUpdates(player, changes);
			}
		} catch (Exception e) {
			plugin.getLogger().log(Level.FINE,
				"Multi-block update failed, falling back to individual packets for player "
					+ player.getName(), e);
			sendIndividualBlockUpdates(player, changes);
		}
	}

	private void sendIndividualBlockUpdates(Player player, Map<Location, Integer> changes) {
		for (Map.Entry<Location, Integer> entry : changes.entrySet()) {
			try {
				Object packet = packetBuilder.buildBlockUpdate(entry.getKey(), entry.getValue());
				if (packet != null) {
					nmsAdapter.sendPacket(player, packet);
				}
			} catch (Exception e) {
				plugin.getLogger().log(Level.FINE,
					"Failed to send block update to " + player.getName(), e);
			}
		}
	}

	UpdatePacketBuilder getPacketBuilder() {
		return packetBuilder;
	}

	int getPendingCount(UUID playerId) {
		synchronized (pendingRequests) {
			List<BlockPosition> list = pendingRequests.get(playerId);
			return list != null ? list.size() : 0;
		}
	}

	int getDeferredCount(UUID playerId) {
		synchronized (deferredRequests) {
			List<BlockPosition> list = deferredRequests.get(playerId);
			return list != null ? list.size() : 0;
		}
	}

    static long sectionKey(int chunkX, int sectionY, int chunkZ) {
        return ((long) chunkX & 0x3FFFFFL) << 42
            | ((long) sectionY & 0xFFFFFL) << 22
            | ((long) chunkZ & 0x3FFFFFL);
    }

    static int extractChunkXFromKey(long sectionKey) {
        return (int) (sectionKey >> 42) << 10 >> 10;
    }

    static int extractSectionYFromKey(long sectionKey) {
        return (int) ((sectionKey >> 22) & 0xFFFFFL) << 12 >> 12;
    }

    static int extractChunkZFromKey(long sectionKey) {
        return (int) (sectionKey & 0x3FFFFFL) << 10 >> 10;
    }

	private static long getCurrentTick() {
		try {
			var method = org.bukkit.Server.class.getMethod("getCurrentTick");
			return (long) method.invoke(Bukkit.getServer());
		} catch (Exception ignored) {
			return System.currentTimeMillis() / 50L;
		}
	}
}
