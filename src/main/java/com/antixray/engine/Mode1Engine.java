package com.antixray.engine;

import com.antixray.nms.NmsAdapter;
import com.antixray.util.MaterialSet;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;

public class Mode1Engine {

    private final NmsAdapter adapter;
    private final BlockClassifier classifier;
    private final MaterialSet materialSet;

    public Mode1Engine(NmsAdapter adapter, BlockClassifier classifier, MaterialSet materialSet) {
        this.adapter = adapter;
        this.classifier = classifier;
        this.materialSet = materialSet;
    }

    public void obfuscateSection(Object chunkSection, World world, int chunkX, int chunkZ,
                                 int sectionBaseY, int deepslateBelowY, int maxBlockHeight) {
        if (sectionBaseY > maxBlockHeight) return;
        if (adapter.getSectionNonEmptyCount(chunkSection) == 0) return;
        if (adapter.isDirectPalette(chunkSection)) return;

        int replacementBlockStateId;
        if (com.antixray.AntiXrayPlugin.getInstance() != null && com.antixray.AntiXrayPlugin.getInstance().getObfuscationEngine() != null) {
            replacementBlockStateId = com.antixray.AntiXrayPlugin.getInstance().getObfuscationEngine().getReplacementBlockStateId(world, sectionBaseY);
        } else {
            replacementBlockStateId = materialSet.getReplacement(sectionBaseY, deepslateBelowY, world.getEnvironment().name());
        }

        List<Integer> palette;
        long[] packed;
        int bitsPerEntry;

        if (adapter.isSingleValuePalette(chunkSection)) {
            int singleValue = adapter.getSingleValue(chunkSection);
            if (!materialSet.isHidden(singleValue)) return;
            PaletteManipulator.UpgradeResult upgraded = PaletteManipulator.upgradeSingleValue(singleValue, replacementBlockStateId);
            palette = upgraded.palette();
            packed = upgraded.packed();
            bitsPerEntry = upgraded.bitsPerEntry();
            if (bitsPerEntry == 0) {
                adapter.upgradeToIndirectPalette(chunkSection, singleValue, replacementBlockStateId);
                adapter.setPaletteEntries(chunkSection, List.of(replacementBlockStateId));
                return;
            }
            int replacementIdx = palette.indexOf(replacementBlockStateId);
            if (replacementIdx < 0) replacementIdx = PaletteManipulator.ensureInPalette(palette, replacementBlockStateId);
            for (int i = 0; i < PaletteManipulator.BLOCKS_PER_SECTION; i++) {
                PaletteManipulator.setIndex(packed, i, replacementIdx, bitsPerEntry);
            }
            PaletteManipulator.CompactResult compacted = PaletteManipulator.compact(palette, packed, bitsPerEntry);
            writeBack(chunkSection, compacted);
            return;
        }

        palette = new ArrayList<>(adapter.getPaletteEntries(chunkSection));
        packed = adapter.getPackedIndices(chunkSection).clone();
        bitsPerEntry = adapter.getPaletteBitsPerEntry(chunkSection);

        if (bitsPerEntry <= 0 || packed.length == 0) return;

        BlockClassification[] classifications = classifier.classifySection(
                world, palette, packed, bitsPerEntry, sectionBaseY, chunkX, chunkZ);

        int replacementPaletteIdx = PaletteManipulator.ensureInPalette(palette, replacementBlockStateId);
        int newBitsPerEntry = PaletteManipulator.computeRequiredBits(palette.size());
        if (newBitsPerEntry > bitsPerEntry) {
            packed = PaletteManipulator.reencode(packed, bitsPerEntry, newBitsPerEntry);
            bitsPerEntry = newBitsPerEntry;
        }

        boolean modified = false;
        for (int position = 0; position < PaletteManipulator.BLOCKS_PER_SECTION; position++) {
            BlockClassification cls = classifications[position];
            if (cls == BlockClassification.HIDDEN || cls == BlockClassification.HIDDEN_TILE_ENTITY) {
                PaletteManipulator.setIndex(packed, position, replacementPaletteIdx, bitsPerEntry);
                modified = true;
            }
        }

        if (!modified) return;

        PaletteManipulator.CompactResult compacted = PaletteManipulator.compact(palette, packed, bitsPerEntry);
        writeBack(chunkSection, compacted);
    }

    public int getReplacementBlockStateId(World world, int sectionBaseY, int deepslateBelowY) {
        return materialSet.getReplacement(sectionBaseY, deepslateBelowY, world.getEnvironment().name());
    }

    private void writeBack(Object chunkSection, PaletteManipulator.CompactResult result) {
        adapter.setPaletteEntries(chunkSection, result.palette());
        if (result.bitsPerEntry() > 0) {
            adapter.setPackedIndices(chunkSection, result.packed(), result.bitsPerEntry());
        }
    }
}
