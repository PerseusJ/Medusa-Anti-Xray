package com.antixray.cache;

import com.antixray.deobfuscation.RevealedBlocksSet;
import com.antixray.util.BlockPosition;

import java.util.ArrayList;
import java.util.List;

public class ChunkOverlay {

    private ChunkOverlay() {}

    public static List<BlockPosition> getPositionsInChunkFast(RevealedBlocksSet revealed, int chunkX, int chunkZ, String worldName) {
        List<BlockPosition> allRevealed = revealed.getRevealedBeforeTickNoRemove(Long.MAX_VALUE, worldName);
        List<BlockPosition> result = new ArrayList<>();

        for (BlockPosition pos : allRevealed) {
            if (pos.getChunkX() == chunkX && pos.getChunkZ() == chunkZ) {
                result.add(pos.withWorld(worldName));
            }
        }

        return result;
    }
}
