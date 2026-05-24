package com.antixray.detection;

import com.antixray.util.BlockPosition;
import org.bukkit.Material;
import org.bukkit.block.Biome;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class PlayerStatistics {

    private static final int MINED_POSITIONS_CAPACITY = 1000;
    private static final int Y_LEVEL_HISTOGRAM_SIZE = 384;
    private static final double SHORT_WINDOW_ALPHA = 1.0 / 1000.0;
    private static final double LONG_WINDOW_ALPHA = 1.0 / 10000.0;

    private static final Material[] STONE_TYPES = {
        Material.STONE,
        Material.DEEPSLATE,
        Material.NETHERRACK,
        Material.END_STONE
    };

    private long stoneMined;
    private final Map<Material, Long> oresMined = new EnumMap<>(Material.class);
    private long totalOresMined;
    private long playTimeMinutes;
    private long airAdjacentMined;
    private long totalMined;
    private long directionChanges;
    private long directionChangesTowardOre;

    private final BlockPosition[] minedPositionsRing = new BlockPosition[MINED_POSITIONS_CAPACITY];
    private int minedPositionsHead;
    private int minedPositionsSize;

    private final int[] yLevelHistogram = new int[Y_LEVEL_HISTOGRAM_SIZE];
    private final Map<Biome, Long> biomeHistogram = new EnumMap<>(Biome.class);

    private double shortWindowBlocks;
    private double shortWindowOres;
    private double longWindowBlocks;
    private double longWindowOres;

    private double oreToStoneRatio;
    private double diamondToStoneRatio;
    private double orePerHour;
    private double diamondPerHour;
    private double straightToOreRatio;
    private double valuableOreRatio;
    private double shortWindowOreRatio;
    private double longWindowOreRatio;

    public void onBlockBreak(Material material, int y, Biome biome, boolean adjacentToAir, BlockPosition position) {
        totalMined++;

        if (adjacentToAir) {
            airAdjacentMined++;
        }

        boolean isOre = isOreMaterial(material);
        if (isOre) {
            oresMined.merge(material, 1L, Long::sum);
            totalOresMined++;
            if (y >= 0 && y < Y_LEVEL_HISTOGRAM_SIZE) {
                yLevelHistogram[y]++;
            }
            if (biome != null) {
                biomeHistogram.merge(biome, 1L, Long::sum);
            }
        }

        if (isStoneType(material)) {
            stoneMined++;
        }

        addMinedPosition(position);

        shortWindowBlocks = shortWindowBlocks * (1.0 - SHORT_WINDOW_ALPHA) + SHORT_WINDOW_ALPHA;
        longWindowBlocks = longWindowBlocks * (1.0 - LONG_WINDOW_ALPHA) + LONG_WINDOW_ALPHA;

        if (isOre) {
            shortWindowOres = shortWindowOres * (1.0 - SHORT_WINDOW_ALPHA) + SHORT_WINDOW_ALPHA;
            longWindowOres = longWindowOres * (1.0 - LONG_WINDOW_ALPHA) + LONG_WINDOW_ALPHA;
        } else {
            shortWindowOres = shortWindowOres * (1.0 - SHORT_WINDOW_ALPHA);
            longWindowOres = longWindowOres * (1.0 - LONG_WINDOW_ALPHA);
        }

        recomputeRatios();
    }

    public void onDirectionChange(boolean towardOre) {
        directionChanges++;
        if (towardOre) {
            directionChangesTowardOre++;
        }
        recomputeRatios();
    }

    public void updatePlayTime(long minutes) {
        this.playTimeMinutes = minutes;
        recomputeRatios();
    }

    private void recomputeRatios() {
        oreToStoneRatio = (double) totalOresMined / Math.max(1, stoneMined);
        diamondToStoneRatio = (double) getDiamondsMined() / Math.max(1, stoneMined);

        double hours = playTimeMinutes / 60.0;
        orePerHour = totalOresMined / Math.max(1.0, hours);
        diamondPerHour = getDiamondsMined() / Math.max(1.0, hours);

        straightToOreRatio = (double) directionChangesTowardOre / Math.max(1, directionChanges);
        valuableOreRatio = (double) getValuableOresMined() / Math.max(1, totalOresMined);

        shortWindowOreRatio = shortWindowOres / Math.max(1e-9, shortWindowBlocks);
        longWindowOreRatio = longWindowOres / Math.max(1e-9, longWindowBlocks);
    }

    private void addMinedPosition(BlockPosition position) {
        if (position == null) return;
        minedPositionsRing[minedPositionsHead] = position;
        minedPositionsHead = (minedPositionsHead + 1) % MINED_POSITIONS_CAPACITY;
        if (minedPositionsSize < MINED_POSITIONS_CAPACITY) {
            minedPositionsSize++;
        }
    }

    public long getStoneMined() {
        return stoneMined;
    }

    public Map<Material, Long> getOresMined() {
        return Collections.unmodifiableMap(oresMined);
    }

    public long getTotalOresMined() {
        return totalOresMined;
    }

    public long getPlayTimeMinutes() {
        return playTimeMinutes;
    }

    public long getAirAdjacentMined() {
        return airAdjacentMined;
    }

    public long getTotalMined() {
        return totalMined;
    }

    public long getDirectionChanges() {
        return directionChanges;
    }

    public long getDirectionChangesTowardOre() {
        return directionChangesTowardOre;
    }

    public long getDiamondsMined() {
        Long d = oresMined.get(Material.DIAMOND_ORE);
        Long dd = oresMined.get(Material.DEEPSLATE_DIAMOND_ORE);
        return (d != null ? d : 0L) + (dd != null ? dd : 0L);
    }

    public long getEmeraldsMined() {
        Long e = oresMined.get(Material.EMERALD_ORE);
        Long ed = oresMined.get(Material.DEEPSLATE_EMERALD_ORE);
        return (e != null ? e : 0L) + (ed != null ? ed : 0L);
    }

    public long getAncientDebrisMined() {
        Long a = oresMined.get(Material.ANCIENT_DEBRIS);
        return a != null ? a : 0L;
    }

    public long getValuableOresMined() {
        long count = getDiamondsMined();
        count += getEmeraldsMined() + getAncientDebrisMined();
        return count;
    }

    public int[] getYLevelHistogram() {
        return yLevelHistogram.clone();
    }

    public int getYLevelCount(int y) {
        if (y < 0 || y >= Y_LEVEL_HISTOGRAM_SIZE) return 0;
        return yLevelHistogram[y];
    }

    public Map<Biome, Long> getBiomeHistogram() {
        return Collections.unmodifiableMap(biomeHistogram);
    }

    public double getShortWindowBlocks() {
        return shortWindowBlocks;
    }

    public double getShortWindowOres() {
        return shortWindowOres;
    }

    public double getLongWindowBlocks() {
        return longWindowBlocks;
    }

    public double getLongWindowOres() {
        return longWindowOres;
    }

    public double getOreToStoneRatio() {
        return oreToStoneRatio;
    }

    public double getDiamondToStoneRatio() {
        return diamondToStoneRatio;
    }

    public double getOrePerHour() {
        return orePerHour;
    }

    public double getDiamondPerHour() {
        return diamondPerHour;
    }

    public double getStraightToOreRatio() {
        return straightToOreRatio;
    }

    public double getValuableOreRatio() {
        return valuableOreRatio;
    }

    public double getShortWindowOreRatio() {
        return shortWindowOreRatio;
    }

    public double getLongWindowOreRatio() {
        return longWindowOreRatio;
    }

    public double getAirAdjacentRatio() {
        return (double) airAdjacentMined / Math.max(1, totalMined);
    }

    public BlockPosition[] getRecentMinedPositions() {
        BlockPosition[] result = new BlockPosition[minedPositionsSize];
        for (int i = 0; i < minedPositionsSize; i++) {
            int idx = (minedPositionsHead - minedPositionsSize + i + MINED_POSITIONS_CAPACITY) % MINED_POSITIONS_CAPACITY;
            result[i] = minedPositionsRing[idx];
        }
        return result;
    }

    public int getMinedPositionsCount() {
        return minedPositionsSize;
    }

    public void reset() {
        stoneMined = 0;
        oresMined.clear();
        totalOresMined = 0;
        playTimeMinutes = 0;
        airAdjacentMined = 0;
        totalMined = 0;
        directionChanges = 0;
        directionChangesTowardOre = 0;
        minedPositionsHead = 0;
        minedPositionsSize = 0;
        for (int i = 0; i < Y_LEVEL_HISTOGRAM_SIZE; i++) {
            yLevelHistogram[i] = 0;
        }
        biomeHistogram.clear();
        shortWindowBlocks = 0;
        shortWindowOres = 0;
        longWindowBlocks = 0;
        longWindowOres = 0;
        oreToStoneRatio = 0;
        diamondToStoneRatio = 0;
        orePerHour = 0;
        diamondPerHour = 0;
        straightToOreRatio = 0;
        valuableOreRatio = 0;
        shortWindowOreRatio = 0;
        longWindowOreRatio = 0;
    }

    public void setPersistedValues(long persistedStoneMined, long persistedTotalOresMined,
                                    long persistedDiamondsMined, long persistedEmeraldsMined,
                                    long persistedAncientDebrisMined, long persistedTotalMined,
                                    long persistedAirAdjacentMined, long persistedPlayTimeMinutes,
                                    double persistedShortWindowBlocks, double persistedShortWindowOres,
                                    double persistedLongWindowBlocks, double persistedLongWindowOres) {
        this.stoneMined = persistedStoneMined;
        this.totalOresMined = persistedTotalOresMined;
        this.totalMined = persistedTotalMined;
        this.airAdjacentMined = persistedAirAdjacentMined;
        this.playTimeMinutes = persistedPlayTimeMinutes;
        this.shortWindowBlocks = persistedShortWindowBlocks;
        this.shortWindowOres = persistedShortWindowOres;
        this.longWindowBlocks = persistedLongWindowBlocks;
        this.longWindowOres = persistedLongWindowOres;

        if (persistedDiamondsMined > 0) {
            oresMined.put(Material.DIAMOND_ORE, persistedDiamondsMined);
        }
        if (persistedEmeraldsMined > 0) {
            oresMined.put(Material.EMERALD_ORE, persistedEmeraldsMined);
        }
        if (persistedAncientDebrisMined > 0) {
            oresMined.put(Material.ANCIENT_DEBRIS, persistedAncientDebrisMined);
        }

        recomputeRatios();
    }

    private static boolean isOreMaterial(Material material) {
        if (material == null) return false;
        String name = material.name();
        return name.endsWith("_ORE")
            || name.startsWith("RAW_")
            || name.equals("ANCIENT_DEBRIS");
    }

    private static boolean isStoneType(Material material) {
        if (material == null) return false;
        for (Material stone : STONE_TYPES) {
            if (material == stone) return true;
        }
        return false;
    }
}
