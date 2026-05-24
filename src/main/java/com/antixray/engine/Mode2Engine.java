package com.antixray.engine;

import com.antixray.nms.NmsAdapter;
import com.antixray.util.MaterialSet;
import com.antixray.util.SeededRandom;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;

public class Mode2Engine {

    private final NmsAdapter adapter;
    private final BlockClassifier classifier;
    private final MaterialSet materialSet;
    private final AirExposureChecker exposureChecker;

    public Mode2Engine(NmsAdapter adapter, BlockClassifier classifier, MaterialSet materialSet,
                       AirExposureChecker exposureChecker) {
        this.adapter = adapter;
        this.classifier = classifier;
        this.materialSet = materialSet;
        this.exposureChecker = exposureChecker;
    }

    public void obfuscateSection(Object chunkSection, World world, int chunkX, int chunkZ,
                                 int sectionBaseY, int deepslateBelowY, int maxBlockHeight,
                                 long serverSalt, double fakeOreChance) {
        if (sectionBaseY > maxBlockHeight) return;
        if (adapter.getSectionNonEmptyCount(chunkSection) == 0) return;
        if (adapter.isDirectPalette(chunkSection)) return;

        int replacementBlockStateId;
        if (com.antixray.AntiXrayPlugin.getInstance() != null && com.antixray.AntiXrayPlugin.getInstance().getObfuscationEngine() != null) {
            replacementBlockStateId = com.antixray.AntiXrayPlugin.getInstance().getObfuscationEngine().getReplacementBlockStateId(world, sectionBaseY);
        } else {
            replacementBlockStateId = materialSet.getReplacement(sectionBaseY, deepslateBelowY, world.getEnvironment().name());
        }
        int[] hiddenPalette = materialSet.getHiddenBlockPaletteArray();

        if (hiddenPalette.length == 0) return;

        List<Integer> palette;
        long[] packed;
        int bitsPerEntry;

        if (adapter.isSingleValuePalette(chunkSection)) {
            int singleValue = adapter.getSingleValue(chunkSection);
            if (materialSet.isHidden(singleValue)) {
                PaletteManipulator.UpgradeResult upgraded = PaletteManipulator.upgradeSingleValue(singleValue, replacementBlockStateId);
                palette = upgraded.palette();
                packed = upgraded.packed();
                bitsPerEntry = upgraded.bitsPerEntry();
                if (bitsPerEntry == 0) {
                    adapter.upgradeToIndirectPalette(chunkSection, singleValue, replacementBlockStateId);
                    palette = new ArrayList<>(adapter.getPaletteEntries(chunkSection));
                    packed = adapter.getPackedIndices(chunkSection).clone();
                    bitsPerEntry = adapter.getPaletteBitsPerEntry(chunkSection);
                }
            } else {
                adapter.upgradeToIndirectPalette(chunkSection, singleValue, replacementBlockStateId);
                palette = new ArrayList<>(adapter.getPaletteEntries(chunkSection));
                packed = adapter.getPackedIndices(chunkSection).clone();
                bitsPerEntry = adapter.getPaletteBitsPerEntry(chunkSection);
            }
        } else {
            palette = new ArrayList<>(adapter.getPaletteEntries(chunkSection));
            packed = adapter.getPackedIndices(chunkSection).clone();
            bitsPerEntry = adapter.getPaletteBitsPerEntry(chunkSection);
        }

        if (bitsPerEntry <= 0 || packed.length == 0) return;

        BlockClassification[] classifications = classifier.classifySection(
                world, palette, packed, bitsPerEntry, sectionBaseY, chunkX, chunkZ);

        int replacementPaletteIdx = PaletteManipulator.ensureInPalette(palette, replacementBlockStateId);
        int newBitsPerEntry = PaletteManipulator.computeRequiredBits(palette.size());
        if (newBitsPerEntry > bitsPerEntry) {
            packed = PaletteManipulator.reencode(packed, bitsPerEntry, newBitsPerEntry);
            bitsPerEntry = newBitsPerEntry;
        }

        boolean modified = !materialSet.isAirInHiddenBlocks();

        for (int position = 0; position < PaletteManipulator.BLOCKS_PER_SECTION; position++) {
            BlockClassification cls = classifications[position];
            if (cls == BlockClassification.HIDDEN || cls == BlockClassification.HIDDEN_TILE_ENTITY) {
                PaletteManipulator.setIndex(packed, position, replacementPaletteIdx, bitsPerEntry);
                modified = true;
            }
        }

        SeededRandom rng = new SeededRandom(chunkX, chunkZ, sectionBaseY, serverSalt);

        for (int position = 0; position < PaletteManipulator.BLOCKS_PER_SECTION; position++) {
            BlockClassification cls = classifications[position];
            if (cls == BlockClassification.AIR) {
                int localX = position & 15;
                int localZ = (position >> 4) & 15;
                int localY = position >> 8;
                int worldX = chunkX * 16 + localX;
                int worldZ = chunkZ * 16 + localZ;
                int worldY = sectionBaseY + localY;
                boolean adjacentToOre = false;
                for (int i = 0; i < 6; i++) {
                    int nx = worldX + AirExposureChecker.NEIGHBOR_DX[i];
                    int ny = worldY + AirExposureChecker.NEIGHBOR_DY[i];
                    int nz = worldZ + AirExposureChecker.NEIGHBOR_DZ[i];
                    int nChunkX = nx >> 4;
                    int nChunkZ = nz >> 4;
                    if (nChunkX != (worldX >> 4) || nChunkZ != (worldZ >> 4)) {
                        if (!world.isChunkLoaded(nChunkX, nChunkZ)) continue;
                    }
                    int neighborId = adapter.getBlockStateAt(world, nx, ny, nz);
                    if (neighborId == -1) continue;
                    if (materialSet.isHidden(neighborId)) {
                        adjacentToOre = true;
                        break;
                    }
                }
                if (adjacentToOre) {
                    if (rng.nextDouble() < fakeOreChance) {
                        int oreBlockStateId = hiddenPalette[rng.nextInt(hiddenPalette.length)];
                        int orePaletteIdx = PaletteManipulator.ensureInPalette(palette, oreBlockStateId);
                        newBitsPerEntry = PaletteManipulator.computeRequiredBits(palette.size());
                        if (newBitsPerEntry > bitsPerEntry) {
                            packed = PaletteManipulator.reencode(packed, bitsPerEntry, newBitsPerEntry);
                            bitsPerEntry = newBitsPerEntry;
                        }
                        PaletteManipulator.setIndex(packed, position, orePaletteIdx, bitsPerEntry);
                    } else {
                        PaletteManipulator.setIndex(packed, position, replacementPaletteIdx, bitsPerEntry);
                    }
                    modified = true;
                }
            }
        }

        for (int position = 0; position < PaletteManipulator.BLOCKS_PER_SECTION; position++) {
            BlockClassification cls = classifications[position];
            if (cls != BlockClassification.NORMAL) continue;

            int currentIdx = PaletteManipulator.getIndex(packed, position, bitsPerEntry);
            int currentBlockStateId = palette.get(currentIdx);
            if (currentBlockStateId != replacementBlockStateId) continue;

            int localX = position & 15;
            int localZ = (position >> 4) & 15;
            int localY = position >> 8;
            int worldX = chunkX * 16 + localX;
            int worldZ = chunkZ * 16 + localZ;
            int worldY = sectionBaseY + localY;

            if (!exposureChecker.isAirExposed(world, worldX, worldY, worldZ)) {
                if (rng.nextDouble() < fakeOreChance) {
                    int oreBlockStateId = hiddenPalette[rng.nextInt(hiddenPalette.length)];
                    int orePaletteIdx = PaletteManipulator.ensureInPalette(palette, oreBlockStateId);
                    newBitsPerEntry = PaletteManipulator.computeRequiredBits(palette.size());
                    if (newBitsPerEntry > bitsPerEntry) {
                        packed = PaletteManipulator.reencode(packed, bitsPerEntry, newBitsPerEntry);
                        bitsPerEntry = newBitsPerEntry;
                    }
                    PaletteManipulator.setIndex(packed, position, orePaletteIdx, bitsPerEntry);
                    modified = true;
                }
            }
        }

        if (!modified) return;

        PaletteManipulator.CompactResult compacted = PaletteManipulator.compact(palette, packed, bitsPerEntry);
        writeBack(chunkSection, compacted);
    }

    private void writeBack(Object chunkSection, PaletteManipulator.CompactResult result) {
        adapter.setPaletteEntries(chunkSection, result.palette());
        if (result.bitsPerEntry() > 0) {
            adapter.setPackedIndices(chunkSection, result.packed(), result.bitsPerEntry());
        }
    }
}
