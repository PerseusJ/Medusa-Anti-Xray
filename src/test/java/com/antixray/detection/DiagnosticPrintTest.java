package com.antixray.detection;

import org.junit.jupiter.api.Test;

class DiagnosticPrintTest {

    @Test
    void printBranchMinerMetrics() {
        PlayerStatistics stats = new PlayerStatistics();
        for (int i = 0; i < 500; i++) {
            stats.onBlockBreak(org.bukkit.Material.STONE, 11,
                    org.bukkit.block.Biome.PLAINS, false, null);
        }
        stats.onBlockBreak(org.bukkit.Material.DIAMOND_ORE, 11,
                org.bukkit.block.Biome.PLAINS, false, null);
        stats.onBlockBreak(org.bukkit.Material.IRON_ORE, 11,
                org.bukkit.block.Biome.PLAINS, false, null);
        stats.updatePlayTime(60);

        System.out.println("=== BRANCH MINER ===");
        System.out.println("totalMined: " + stats.getTotalMined());
        System.out.println("stoneMined: " + stats.getStoneMined());
        System.out.println("totalOresMined: " + stats.getTotalOresMined());
        System.out.println("diamondsMined: " + stats.getDiamondsMined());
        System.out.println("valuableOresMined: " + stats.getValuableOresMined());
        System.out.println("playTimeMinutes: " + stats.getPlayTimeMinutes());
        System.out.println("airAdjacentRatio: " + stats.getAirAdjacentRatio());
        System.out.println("oreToStoneRatio: " + stats.getOreToStoneRatio());
        System.out.println("diamondToStoneRatio: " + stats.getDiamondToStoneRatio());
        System.out.println("orePerHour: " + stats.getOrePerHour());
        System.out.println("diamondPerHour: " + stats.getDiamondPerHour());
        System.out.println("shortWindowOreRatio: " + stats.getShortWindowOreRatio());
        System.out.println("longWindowOreRatio: " + stats.getLongWindowOreRatio());
        System.out.println("valuableOreRatio: " + stats.getValuableOreRatio());
        System.out.println("straightToOreRatio: " + stats.getStraightToOreRatio());
    }

    @Test
    void printXrayerMetrics() {
        PlayerStatistics stats = new PlayerStatistics();
        for (int i = 0; i < 100; i++) {
            stats.onBlockBreak(org.bukkit.Material.DIAMOND_ORE, -10,
                    org.bukkit.block.Biome.PLAINS, false, null);
        }
        stats.updatePlayTime(10);

        System.out.println("=== X-RAYER ===");
        System.out.println("totalMined: " + stats.getTotalMined());
        System.out.println("stoneMined: " + stats.getStoneMined());
        System.out.println("totalOresMined: " + stats.getTotalOresMined());
        System.out.println("diamondsMined: " + stats.getDiamondsMined());
        System.out.println("valuableOresMined: " + stats.getValuableOresMined());
        System.out.println("playTimeMinutes: " + stats.getPlayTimeMinutes());
        System.out.println("airAdjacentRatio: " + stats.getAirAdjacentRatio());
        System.out.println("oreToStoneRatio: " + stats.getOreToStoneRatio());
        System.out.println("diamondToStoneRatio: " + stats.getDiamondToStoneRatio());
        System.out.println("orePerHour: " + stats.getOrePerHour());
        System.out.println("diamondPerHour: " + stats.getDiamondPerHour());
        System.out.println("shortWindowOreRatio: " + stats.getShortWindowOreRatio());
        System.out.println("longWindowOreRatio: " + stats.getLongWindowOreRatio());
        System.out.println("valuableOreRatio: " + stats.getValuableOreRatio());
        System.out.println("straightToOreRatio: " + stats.getStraightToOreRatio());
    }

    @Test
    void printMixedMinerMetrics() {
        PlayerStatistics stats = new PlayerStatistics();
        for (int i = 0; i < 200; i++) {
            stats.onBlockBreak(org.bukkit.Material.STONE, 64,
                    org.bukkit.block.Biome.PLAINS, false, null);
        }
        for (int i = 0; i < 20; i++) {
            stats.onBlockBreak(org.bukkit.Material.DIAMOND_ORE, -10,
                    org.bukkit.block.Biome.PLAINS, true, null);
        }
        stats.updatePlayTime(120);

        System.out.println("=== MIXED (200 stone + 20 diamond) ===");
        System.out.println("totalMined: " + stats.getTotalMined());
        System.out.println("stoneMined: " + stats.getStoneMined());
        System.out.println("totalOresMined: " + stats.getTotalOresMined());
        System.out.println("diamondsMined: " + stats.getDiamondsMined());
        System.out.println("valuableOresMined: " + stats.getValuableOresMined());
        System.out.println("playTimeMinutes: " + stats.getPlayTimeMinutes());
        System.out.println("airAdjacentRatio: " + stats.getAirAdjacentRatio());
        System.out.println("oreToStoneRatio: " + stats.getOreToStoneRatio());
        System.out.println("diamondToStoneRatio: " + stats.getDiamondToStoneRatio());
        System.out.println("orePerHour: " + stats.getOrePerHour());
        System.out.println("diamondPerHour: " + stats.getDiamondPerHour());
        System.out.println("shortWindowOreRatio: " + stats.getShortWindowOreRatio());
        System.out.println("longWindowOreRatio: " + stats.getLongWindowOreRatio());
        System.out.println("valuableOreRatio: " + stats.getValuableOreRatio());
        System.out.println("straightToOreRatio: " + stats.getStraightToOreRatio());
    }

    @Test
    void printHeavyOreMinerMetrics() {
        PlayerStatistics stats = new PlayerStatistics();
        for (int i = 0; i < 200; i++) {
            stats.onBlockBreak(org.bukkit.Material.STONE, 64,
                    org.bukkit.block.Biome.PLAINS, false, null);
        }
        for (int i = 0; i < 50; i++) {
            stats.onBlockBreak(org.bukkit.Material.DIAMOND_ORE, -10,
                    org.bukkit.block.Biome.PLAINS, true, null);
        }
        stats.updatePlayTime(120);

        System.out.println("=== HEAVY (200 stone + 50 diamond) ===");
        System.out.println("totalMined: " + stats.getTotalMined());
        System.out.println("stoneMined: " + stats.getStoneMined());
        System.out.println("totalOresMined: " + stats.getTotalOresMined());
        System.out.println("diamondsMined: " + stats.getDiamondsMined());
        System.out.println("valuableOresMined: " + stats.getValuableOresMined());
        System.out.println("playTimeMinutes: " + stats.getPlayTimeMinutes());
        System.out.println("airAdjacentRatio: " + stats.getAirAdjacentRatio());
        System.out.println("oreToStoneRatio: " + stats.getOreToStoneRatio());
        System.out.println("diamondToStoneRatio: " + stats.getDiamondToStoneRatio());
        System.out.println("orePerHour: " + stats.getOrePerHour());
        System.out.println("diamondPerHour: " + stats.getDiamondPerHour());
        System.out.println("shortWindowOreRatio: " + stats.getShortWindowOreRatio());
        System.out.println("longWindowOreRatio: " + stats.getLongWindowOreRatio());
        System.out.println("valuableOreRatio: " + stats.getValuableOreRatio());
        System.out.println("straightToOreRatio: " + stats.getStraightToOreRatio());
    }
}
