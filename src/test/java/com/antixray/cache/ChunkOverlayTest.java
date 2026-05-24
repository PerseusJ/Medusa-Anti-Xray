package com.antixray.cache;

import com.antixray.deobfuscation.RevealedBlocksSet;
import com.antixray.util.BlockPosition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChunkOverlayTest {

    @Test
    void getPositionsInChunkFast_noRevealedBlocks_returnsEmpty() {
        RevealedBlocksSet revealed = new RevealedBlocksSet(100);
        List<BlockPosition> result = ChunkOverlay.getPositionsInChunkFast(revealed, 0, 0, "world");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getPositionsInChunkFast_revealedInChunk_returnsPositions() {
        RevealedBlocksSet revealed = new RevealedBlocksSet(100);
        revealed.add(new BlockPosition("world", 0, 64, 0), 1L);
        revealed.add(new BlockPosition("world", 5, 64, 5), 1L);
        List<BlockPosition> result = ChunkOverlay.getPositionsInChunkFast(revealed, 0, 0, "world");
        assertEquals(2, result.size());
    }

    @Test
    void getPositionsInChunkFast_revealedInDifferentChunk_returnsEmpty() {
        RevealedBlocksSet revealed = new RevealedBlocksSet(100);
        revealed.add(new BlockPosition("world", 16, 64, 16), 1L);
        List<BlockPosition> result = ChunkOverlay.getPositionsInChunkFast(revealed, 0, 0, "world");
        assertTrue(result.isEmpty());
    }

    @Test
    void getPositionsInChunkFast_mixedChunks_returnsOnlyMatchingChunk() {
        RevealedBlocksSet revealed = new RevealedBlocksSet(100);
        revealed.add(new BlockPosition("world", 0, 64, 0), 1L);
        revealed.add(new BlockPosition("world", 16, 64, 0), 1L);
        revealed.add(new BlockPosition("world", 5, 50, 5), 1L);
        revealed.add(new BlockPosition("world", 32, 64, 32), 1L);
        List<BlockPosition> result = ChunkOverlay.getPositionsInChunkFast(revealed, 0, 0, "world");
        assertEquals(2, result.size());
        for (BlockPosition pos : result) {
            assertEquals(0, pos.getChunkX());
            assertEquals(0, pos.getChunkZ());
        }
    }

    @Test
    void getPositionsInChunkFast_negativeChunkCoords() {
        RevealedBlocksSet revealed = new RevealedBlocksSet(100);
        revealed.add(new BlockPosition("world", -1, 64, -1), 1L);
        List<BlockPosition> result = ChunkOverlay.getPositionsInChunkFast(revealed, -1, -1, "world");
        assertEquals(1, result.size());
        assertEquals(-1, result.get(0).getX());
    }

    @Test
    void getPositionsInChunkFast_differentChunkCoords_ignored() {
        RevealedBlocksSet revealed = new RevealedBlocksSet(100);
        revealed.add(new BlockPosition("world", 16, 64, 16), 1L);
        List<BlockPosition> result = ChunkOverlay.getPositionsInChunkFast(revealed, 0, 0, "world");
        assertTrue(result.isEmpty());
    }

    @Test
    void getPositionsInChunkFast_boundaryPositions() {
        RevealedBlocksSet revealed = new RevealedBlocksSet(100);
        revealed.add(new BlockPosition("world", 15, 64, 15), 1L);
        revealed.add(new BlockPosition("world", 16, 64, 0), 1L);
        List<BlockPosition> inChunk0 = ChunkOverlay.getPositionsInChunkFast(revealed, 0, 0, "world");
        assertEquals(1, inChunk0.size());
        assertEquals(15, inChunk0.get(0).getX());
        List<BlockPosition> inChunk1 = ChunkOverlay.getPositionsInChunkFast(revealed, 1, 0, "world");
        assertEquals(1, inChunk1.size());
        assertEquals(16, inChunk1.get(0).getX());
    }
}
