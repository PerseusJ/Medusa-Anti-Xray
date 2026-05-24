package com.antixray.listener;

import com.antixray.AntiXrayPlugin;
import com.antixray.deobfuscation.DeobfuscationManager;
import com.antixray.util.BlockPosition;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import java.util.ArrayList;
import java.util.List;

public class WorldEventListener implements Listener {

    private static final int BORDER_FACES[][] = {
            {1, 0, 0},
            {-1, 0, 0},
            {0, 0, 1},
            {0, 0, -1}
    };

    private final AntiXrayPlugin plugin;

    public WorldEventListener(AntiXrayPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        plugin.initializeWorldState(event.getWorld().getName());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldUnload(WorldUnloadEvent event) {
        plugin.removeWorldState(event.getWorld().getName());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (event.isNewChunk()) return;

        World world = event.getWorld();
        int chunkX = event.getChunk().getX();
        int chunkZ = event.getChunk().getZ();
        String worldName = world.getName();

        DeobfuscationManager manager = plugin.getDeobfuscationManager();
        if (manager == null) return;

        int[][] neighbors = {
                {chunkX + 1, chunkZ},
                {chunkX - 1, chunkZ},
                {chunkX, chunkZ + 1},
                {chunkX, chunkZ - 1}
        };

        List<BlockPosition> borderPositions = new ArrayList<>();

        for (int[] neighbor : neighbors) {
            int nChunkX = neighbor[0];
            int nChunkZ = neighbor[1];
            if (!world.isChunkLoaded(nChunkX, nChunkZ)) continue;

            int borderX, startZ, endZ;

            if (nChunkX > chunkX) {
                borderX = (chunkX << 4) + 15;
                startZ = chunkZ << 4;
                endZ = startZ + 15;
            } else if (nChunkX < chunkX) {
                borderX = chunkX << 4;
                startZ = chunkZ << 4;
                endZ = startZ + 15;
            } else if (nChunkZ > chunkZ) {
                borderX = -1;
                int borderZ = (chunkZ << 4) + 15;
                int startX = chunkX << 4;
                int endX = startX + 15;
                int minH = world.getMinHeight();
                int maxH = world.getMaxHeight();
                for (int bx = startX; bx <= endX; bx++) {
                    for (int by = minH; by < maxH; by++) {
                        borderPositions.add(new BlockPosition(worldName, bx, by, borderZ));
                    }
                }
                continue;
            } else {
                borderX = -1;
                int borderZ = chunkZ << 4;
                int startX = chunkX << 4;
                int endX = startX + 15;
                int minH = world.getMinHeight();
                int maxH = world.getMaxHeight();
                for (int bx = startX; bx <= endX; bx++) {
                    for (int by = minH; by < maxH; by++) {
                        borderPositions.add(new BlockPosition(worldName, bx, by, borderZ));
                    }
                }
                continue;
            }

            int minH = world.getMinHeight();
            int maxH = world.getMaxHeight();
            for (int bz = startZ; bz <= endZ; bz++) {
                for (int by = minH; by < maxH; by++) {
                    borderPositions.add(new BlockPosition(worldName, borderX, by, bz));
                }
            }
        }

        if (borderPositions.isEmpty()) return;

        for (Player player : world.getPlayers()) {
            manager.queueDeobfuscation(player, borderPositions);
        }
    }
}
