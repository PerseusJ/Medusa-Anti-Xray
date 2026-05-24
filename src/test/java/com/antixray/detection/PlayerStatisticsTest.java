package com.antixray.detection;

import com.antixray.util.BlockPosition;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayerStatisticsTest {

    private PlayerStatistics stats;

    @BeforeEach
    void setUp() {
        stats = new PlayerStatistics();
    }

    @Test
    void testRatioComputations() {
        // Initial state ratios should be zero or defaults
        assertEquals(0.0, stats.getOreToStoneRatio());
        assertEquals(0.0, stats.getDiamondToStoneRatio());
        assertEquals(0.0, stats.getOrePerHour());
        assertEquals(0.0, stats.getDiamondPerHour());
        assertEquals(0.0, stats.getStraightToOreRatio());
        assertEquals(0.0, stats.getValuableOreRatio());

        // Mine 10 stone and 2 diamond ore, at Y=12, Plains biome, not air adjacent
        BlockPosition pos = new BlockPosition("world", 0, 12, 0);
        for (int i = 0; i < 10; i++) {
            stats.onBlockBreak(Material.STONE, 12, Biome.PLAINS, false, pos);
        }
        for (int i = 0; i < 2; i++) {
            stats.onBlockBreak(Material.DIAMOND_ORE, 12, Biome.PLAINS, false, pos);
        }

        // Playtime: 120 minutes (2.0 hours)
        stats.updatePlayTime(120);

        // Ratios:
        // oreToStoneRatio = totalOresMined (2) / stoneMined (10) = 0.2
        assertEquals(0.2, stats.getOreToStoneRatio(), 1e-6);

        // diamondToStoneRatio = diamondsMined (2) / stoneMined (10) = 0.2
        assertEquals(0.2, stats.getDiamondToStoneRatio(), 1e-6);

        // orePerHour = totalOresMined (2) / 2.0 = 1.0
        assertEquals(1.0, stats.getOrePerHour(), 1e-6);

        // diamondPerHour = diamondsMined (2) / 2.0 = 1.0
        assertEquals(1.0, stats.getDiamondPerHour(), 1e-6);

        // valuableOreRatio = valuableOresMined (2) / totalOresMined (2) = 1.0
        assertEquals(1.0, stats.getValuableOreRatio(), 1e-6);

        // Add 2 direction changes: 1 toward ore, 1 not
        stats.onDirectionChange(true);
        stats.onDirectionChange(false);

        // straightToOreRatio = directionChangesTowardOre (1) / directionChanges (2) = 0.5
        assertEquals(0.5, stats.getStraightToOreRatio(), 1e-6);
    }

    @Test
    void testExponentialMovingAverageUpdate() {
        double shortAlpha = 1.0 / 1000.0;
        double longAlpha = 1.0 / 10000.0;

        // Verify initial state
        assertEquals(0.0, stats.getShortWindowBlocks());
        assertEquals(0.0, stats.getShortWindowOres());
        assertEquals(0.0, stats.getLongWindowBlocks());
        assertEquals(0.0, stats.getLongWindowOres());

        BlockPosition pos = new BlockPosition("world", 0, 10, 0);

        // Step 1: Mine a stone block (not ore)
        stats.onBlockBreak(Material.STONE, 10, Biome.PLAINS, false, pos);

        double expectedShortBlocks = shortAlpha;
        double expectedLongBlocks = longAlpha;
        double expectedShortOres = 0.0;
        double expectedLongOres = 0.0;

        assertEquals(expectedShortBlocks, stats.getShortWindowBlocks(), 1e-9);
        assertEquals(expectedLongBlocks, stats.getLongWindowBlocks(), 1e-9);
        assertEquals(expectedShortOres, stats.getShortWindowOres(), 1e-9);
        assertEquals(expectedLongOres, stats.getLongWindowOres(), 1e-9);

        // Step 2: Mine an ore block
        stats.onBlockBreak(Material.DIAMOND_ORE, 10, Biome.PLAINS, false, pos);

        expectedShortBlocks = expectedShortBlocks * (1.0 - shortAlpha) + shortAlpha;
        expectedLongBlocks = expectedLongBlocks * (1.0 - longAlpha) + longAlpha;
        expectedShortOres = expectedShortOres * (1.0 - shortAlpha) + shortAlpha;
        expectedLongOres = expectedLongOres * (1.0 - longAlpha) + longAlpha;

        assertEquals(expectedShortBlocks, stats.getShortWindowBlocks(), 1e-9);
        assertEquals(expectedLongBlocks, stats.getLongWindowBlocks(), 1e-9);
        assertEquals(expectedShortOres, stats.getShortWindowOres(), 1e-9);
        assertEquals(expectedLongOres, stats.getLongWindowOres(), 1e-9);

        // Check ratio computations for window ore ratios
        double expectedShortRatio = expectedShortOres / expectedShortBlocks;
        double expectedLongRatio = expectedLongOres / expectedLongBlocks;

        assertEquals(expectedShortRatio, stats.getShortWindowOreRatio(), 1e-9);
        assertEquals(expectedLongRatio, stats.getLongWindowOreRatio(), 1e-9);
    }

    @Test
    void testPlayStyleClassification() {
        // Under CAVING_THRESHOLD (0.60): should be BRANCH_MINING
        // Let's break 10 blocks, 5 air adjacent (ratio = 0.5)
        BlockPosition pos = new BlockPosition("world", 0, 10, 0);
        for (int i = 0; i < 5; i++) {
            stats.onBlockBreak(Material.STONE, 10, Biome.PLAINS, true, pos);
        }
        for (int i = 0; i < 5; i++) {
            stats.onBlockBreak(Material.STONE, 10, Biome.PLAINS, false, pos);
        }

        assertEquals(0.5, stats.getAirAdjacentRatio(), 1e-6);
        assertEquals(PlayStyleClassifier.BRANCH_MINING, PlayStyleClassifier.classify(stats));

        // Over CAVING_THRESHOLD: should be CAVING
        // Mine 2 more air-adjacent block (total 7/12 = 0.5833, still below 0.60)
        stats.onBlockBreak(Material.STONE, 10, Biome.PLAINS, true, pos);
        stats.onBlockBreak(Material.STONE, 10, Biome.PLAINS, true, pos);
        assertEquals(7.0 / 12.0, stats.getAirAdjacentRatio(), 1e-6);
        assertEquals(PlayStyleClassifier.BRANCH_MINING, PlayStyleClassifier.classify(stats));

        // Mine 1 more air-adjacent block (total 8/13 = 0.615, which is above 0.60)
        stats.onBlockBreak(Material.STONE, 10, Biome.PLAINS, true, pos);
        assertEquals(8.0 / 13.0, stats.getAirAdjacentRatio(), 1e-6);
        assertEquals(PlayStyleClassifier.CAVING, PlayStyleClassifier.classify(stats));

        // Test shouldReclassify interval
        assertTrue(PlayStyleClassifier.shouldReclassify(100, 0));
        assertFalse(PlayStyleClassifier.shouldReclassify(99, 0));
        assertTrue(PlayStyleClassifier.shouldReclassify(250, 150));
        assertFalse(PlayStyleClassifier.shouldReclassify(249, 150));
    }

    @Test
    void testDirectionChangeDetection() {
        // Initial values
        assertEquals(0, stats.getDirectionChanges());
        assertEquals(0, stats.getDirectionChangesTowardOre());

        // Direction change toward ore
        stats.onDirectionChange(true);
        assertEquals(1, stats.getDirectionChanges());
        assertEquals(1, stats.getDirectionChangesTowardOre());
        assertEquals(1.0, stats.getStraightToOreRatio(), 1e-6);

        // Direction change NOT toward ore
        stats.onDirectionChange(false);
        assertEquals(2, stats.getDirectionChanges());
        assertEquals(1, stats.getDirectionChangesTowardOre());
        assertEquals(0.5, stats.getStraightToOreRatio(), 1e-6);
    }
}
