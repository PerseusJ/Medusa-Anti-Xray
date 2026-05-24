package com.antixray.engine;

import com.antixray.nms.NmsAdapter;
import com.antixray.util.MaterialSet;
import org.bukkit.World;

public class AirExposureChecker {

    public static final int NEIGHBOR_DX[] = {1, -1, 0, 0, 0, 0};
    public static final int NEIGHBOR_DY[] = {0, 0, 1, -1, 0, 0};
    public static final int NEIGHBOR_DZ[] = {0, 0, 0, 0, 1, -1};

    private final NmsAdapter adapter;
    private final MaterialSet materialSet;
    private final boolean lavaObscures;

    public AirExposureChecker(NmsAdapter adapter, MaterialSet materialSet, boolean lavaObscures) {
        this.adapter = adapter;
        this.materialSet = materialSet;
        this.lavaObscures = lavaObscures;
    }

    public boolean isAirExposed(World world, int x, int y, int z) {
        int centerChunkX = x >> 4;
        int centerChunkZ = z >> 4;

        for (int i = 0; i < 6; i++) {
            int nx = x + NEIGHBOR_DX[i];
            int ny = y + NEIGHBOR_DY[i];
            int nz = z + NEIGHBOR_DZ[i];

            int neighborChunkX = nx >> 4;
            int neighborChunkZ = nz >> 4;

        if (neighborChunkX != centerChunkX || neighborChunkZ != centerChunkZ) {
            if (!world.isChunkLoaded(neighborChunkX, neighborChunkZ)) {
                continue;
            }
        }

            int blockStateId = adapter.getBlockStateAt(world, nx, ny, nz);
            if (blockStateId == -1) {
                continue;
            }
            if (materialSet.isTransparent(blockStateId)) {
                if (materialSet.isAirInHiddenBlocks() && materialSet.isAirBlock(blockStateId)) {
                    continue;
                }
                return true;
            }
        }
        return false;
    }

    public boolean isLavaObscures() {
        return lavaObscures;
    }
}
