package com.antixray.engine;

import com.antixray.util.MaterialSet;
import org.bukkit.World;

import java.util.List;

public class BlockClassifier {

    private final MaterialSet materialSet;
    private final AirExposureChecker exposureChecker;
    private final boolean airInHiddenBlocks;

    public BlockClassifier(MaterialSet materialSet, AirExposureChecker exposureChecker) {
        this.materialSet = materialSet;
        this.exposureChecker = exposureChecker;
        this.airInHiddenBlocks = materialSet.isAirInHiddenBlocks();
    }

    public BlockClassification[] classifySection(World world, List<Integer> palette, long[] packed, int bitsPerEntry, int sectionBaseY, int chunkX, int chunkZ) {
        BlockClassification[] result = new BlockClassification[PaletteManipulator.BLOCKS_PER_SECTION];
        com.antixray.api.ObfuscationProvider provider = null;
        com.antixray.nms.NmsAdapter adapter = null;
        if (com.antixray.AntiXrayPlugin.getInstance() != null) {
            provider = com.antixray.AntiXrayPlugin.getInstance().getAPI().getObfuscationProvider(world);
            adapter = com.antixray.AntiXrayPlugin.getInstance().getNmsAdapter();
        }

        for (int position = 0; position < PaletteManipulator.BLOCKS_PER_SECTION; position++) {
            int localX = position & 15;
            int localZ = (position >> 4) & 15;
            int localY = position >> 8;
            int worldX = chunkX * 16 + localX;
            int worldZ = chunkZ * 16 + localZ;
            int worldY = sectionBaseY + localY;
            int paletteIndex = PaletteManipulator.getIndex(packed, position, bitsPerEntry);
            int blockStateId = palette.get(paletteIndex);

            if (provider != null && !(provider instanceof com.antixray.api.DefaultObfuscationProvider) && adapter != null) {
                org.bukkit.block.BlockState stateProxy = com.antixray.api.BlockStateProxy.create(
                        world, worldX, worldY, worldZ, adapter.getTypeFromId(blockStateId), adapter.getBlockDataFromId(blockStateId)
                );
                try {
                    if (provider.shouldObfuscate(stateProxy, world, worldX, worldY, worldZ)) {
                        if (materialSet.isTileEntity(blockStateId)) {
                            result[position] = BlockClassification.HIDDEN_TILE_ENTITY;
                        } else {
                            result[position] = BlockClassification.HIDDEN;
                        }
                    } else {
                        if (airInHiddenBlocks && materialSet.isAirBlock(blockStateId)) {
                            result[position] = BlockClassification.AIR;
                        } else {
                            result[position] = BlockClassification.NORMAL;
                        }
                    }
                } catch (Exception e) {
                    result[position] = BlockClassification.NORMAL;
                }
            } else {
                if (airInHiddenBlocks && materialSet.isAirBlock(blockStateId)) {
                    result[position] = BlockClassification.AIR;
                } else if (materialSet.isHidden(blockStateId)) {
                    if (materialSet.isTileEntity(blockStateId)) {
                        result[position] = BlockClassification.HIDDEN_TILE_ENTITY;
                    } else if (exposureChecker.isAirExposed(world, worldX, worldY, worldZ)) {
                        result[position] = BlockClassification.AIR_EXPOSED;
                    } else {
                        result[position] = BlockClassification.HIDDEN;
                    }
                } else {
                    result[position] = BlockClassification.NORMAL;
                }
            }
        }
        return result;
    }

    public BlockClassification classifySingle(World world, int blockStateId, int worldX, int worldY, int worldZ) {
        com.antixray.api.ObfuscationProvider provider = null;
        com.antixray.nms.NmsAdapter adapter = null;
        if (com.antixray.AntiXrayPlugin.getInstance() != null) {
            provider = com.antixray.AntiXrayPlugin.getInstance().getAPI().getObfuscationProvider(world);
            adapter = com.antixray.AntiXrayPlugin.getInstance().getNmsAdapter();
        }

        if (provider != null && !(provider instanceof com.antixray.api.DefaultObfuscationProvider) && adapter != null) {
            org.bukkit.block.BlockState stateProxy = com.antixray.api.BlockStateProxy.create(
                    world, worldX, worldY, worldZ, adapter.getTypeFromId(blockStateId), adapter.getBlockDataFromId(blockStateId)
            );
            try {
                if (provider.shouldObfuscate(stateProxy, world, worldX, worldY, worldZ)) {
                    if (materialSet.isTileEntity(blockStateId)) {
                        return BlockClassification.HIDDEN_TILE_ENTITY;
                    }
                    return BlockClassification.HIDDEN;
                } else {
                    if (airInHiddenBlocks && materialSet.isAirBlock(blockStateId)) {
                        return BlockClassification.AIR;
                    }
                    return BlockClassification.NORMAL;
                }
            } catch (Exception e) {
                return BlockClassification.NORMAL;
            }
        }

        if (airInHiddenBlocks && materialSet.isAirBlock(blockStateId)) {
            return BlockClassification.AIR;
        }
        if (materialSet.isHidden(blockStateId)) {
            if (materialSet.isTileEntity(blockStateId)) {
                return BlockClassification.HIDDEN_TILE_ENTITY;
            }
            if (exposureChecker.isAirExposed(world, worldX, worldY, worldZ)) {
                return BlockClassification.AIR_EXPOSED;
            }
            return BlockClassification.HIDDEN;
        }
        return BlockClassification.NORMAL;
    }
}
