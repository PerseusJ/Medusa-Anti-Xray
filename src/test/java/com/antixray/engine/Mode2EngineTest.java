package com.antixray.engine;

import com.antixray.nms.NmsAdapter;
import com.antixray.util.MaterialSet;
import com.antixray.util.SeededRandom;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class Mode2EngineTest {

    private static final int STONE_ID = 1;
    private static final int DEEPSLATE_ID = 2;
    private static final int NETHERRACK_ID = 3;
    private static final int END_STONE_ID = 4;
    private static final int DIAMOND_ORE_ID = 50;
    private static final int IRON_ORE_ID = 51;
    private static final int CHEST_ID = 60;
    private static final int AIR_ID = 0;

    private NmsAdapter adapter;
    private World world;
    private MaterialSet materialSet;
    private AirExposureChecker exposureChecker;
    private BlockClassifier classifier;
    private MockedStatic<Bukkit> bukkitMock;

    @BeforeEach
    void setUp() {
        Server server = mock(Server.class);
        lenient().when(server.getName()).thenReturn("TestServer");
        lenient().when(server.getVersion()).thenReturn("1.21");
        lenient().when(server.getBukkitVersion()).thenReturn("1.21-R0.1-SNAPSHOT");
        bukkitMock = Mockito.mockStatic(Bukkit.class);
        bukkitMock.when(Bukkit::getServer).thenReturn(server);

        adapter = mock(NmsAdapter.class);
        world = mock(World.class);
        materialSet = mock(MaterialSet.class);

        when(materialSet.getReplacement(anyInt(), anyInt(), anyString())).thenReturn(STONE_ID);
        when(materialSet.isHidden(DIAMOND_ORE_ID)).thenReturn(true);
        when(materialSet.isHidden(IRON_ORE_ID)).thenReturn(true);
        when(materialSet.isHidden(CHEST_ID)).thenReturn(true);
        when(materialSet.isTileEntity(CHEST_ID)).thenReturn(true);
        when(materialSet.isTileEntity(DIAMOND_ORE_ID)).thenReturn(false);
        when(materialSet.isTileEntity(IRON_ORE_ID)).thenReturn(false);
        when(materialSet.isHidden(STONE_ID)).thenReturn(false);
        when(materialSet.isHidden(AIR_ID)).thenReturn(false);
        when(materialSet.isTransparent(AIR_ID)).thenReturn(true);
        when(materialSet.isTransparent(STONE_ID)).thenReturn(false);
        when(materialSet.getHiddenBlockPaletteArray()).thenReturn(new int[]{DIAMOND_ORE_ID, IRON_ORE_ID});

        when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);
        when(world.getName()).thenReturn("world");

        exposureChecker = new AirExposureChecker(adapter, materialSet, true);
        classifier = new BlockClassifier(materialSet, exposureChecker);
    }

    @AfterEach
    void tearDown() {
        if (bukkitMock != null) {
            bukkitMock.close();
        }
    }

    private Object createMockSection(List<Integer> palette, long[] packed, int bitsPerEntry) {
        Object section = new Object();
        when(adapter.isSingleValuePalette(section)).thenReturn(false);
        when(adapter.isDirectPalette(section)).thenReturn(false);
        when(adapter.getSectionNonEmptyCount(section)).thenReturn(1);
        when(adapter.getPaletteEntries(section)).thenReturn(new ArrayList<>(palette));
        when(adapter.getPackedIndices(section)).thenReturn(packed.clone());
        when(adapter.getPaletteBitsPerEntry(section)).thenReturn(bitsPerEntry);
        return section;
    }

    @Test
    void fakeOresInjectedAtExpectedProbability() {
        List<Integer> palette = new ArrayList<>(Arrays.asList(STONE_ID, DIAMOND_ORE_ID));
        int bitsPerEntry = 4;
        long[] packed = new long[PaletteManipulator.computePackedArraySize(bitsPerEntry)];
        for (int i = 0; i < 4096; i++) {
            PaletteManipulator.setIndex(packed, i, 0, bitsPerEntry);
        }
        PaletteManipulator.setIndex(packed, 42, 1, bitsPerEntry);

        when(adapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt())).thenReturn(STONE_ID);

        Object section = createMockSection(palette, packed, bitsPerEntry);
        Mode2Engine engine = new Mode2Engine(adapter, classifier, materialSet, exposureChecker);

        double fakeOreChance = 1.0;
        engine.obfuscateSection(section, world, 0, 0, 0, 0, 64, 12345L, fakeOreChance);

        verify(adapter).setPaletteEntries(eq(section), any());
        verify(adapter).setPackedIndices(eq(section), any(long[].class), anyInt());
    }

    @Test
    void deterministicOutputForSameSeed() {
        SeededRandom rng1 = new SeededRandom(5, 10, 0, 99999L);
        SeededRandom rng2 = new SeededRandom(5, 10, 0, 99999L);

        for (int i = 0; i < 100; i++) {
            assertEquals(rng1.nextDouble(), rng2.nextDouble());
        }
    }

    @Test
    void airExposedOres_notReplacedByFakeOre() {
        List<Integer> palette = new ArrayList<>(Arrays.asList(STONE_ID, DIAMOND_ORE_ID));
        int bitsPerEntry = 4;
        long[] packed = new long[PaletteManipulator.computePackedArraySize(bitsPerEntry)];
        for (int i = 0; i < 4096; i++) {
            PaletteManipulator.setIndex(packed, i, 0, bitsPerEntry);
        }
        PaletteManipulator.setIndex(packed, 0, 1, bitsPerEntry);

        when(adapter.getBlockStateAt(world, 1, 0, 0)).thenReturn(AIR_ID);
        when(adapter.getBlockStateAt(world, -1, 0, 0)).thenReturn(STONE_ID);
        when(adapter.getBlockStateAt(world, 0, 1, 0)).thenReturn(STONE_ID);
        when(adapter.getBlockStateAt(world, 0, -1, 0)).thenReturn(STONE_ID);
        when(adapter.getBlockStateAt(world, 0, 0, 1)).thenReturn(STONE_ID);
        when(adapter.getBlockStateAt(world, 0, 0, -1)).thenReturn(STONE_ID);

        Object section = createMockSection(palette, packed, bitsPerEntry);
        Mode2Engine engine = new Mode2Engine(adapter, classifier, materialSet, exposureChecker);
        engine.obfuscateSection(section, world, 0, 0, 0, 0, 64, 12345L, 1.0);

        verify(adapter).setPaletteEntries(eq(section), any());
    }

    @Test
    void paletteExpansion_whenNewOreAdded() {
        List<Integer> palette = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            palette.add(100 + i);
        }
        int bitsPerEntry = 4;
        long[] packed = new long[PaletteManipulator.computePackedArraySize(bitsPerEntry)];
        for (int i = 0; i < 4096; i++) {
            PaletteManipulator.setIndex(packed, i, 0, bitsPerEntry);
        }
        PaletteManipulator.setIndex(packed, 42, 1, bitsPerEntry);

        when(adapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt())).thenReturn(STONE_ID);

        Object section = createMockSection(palette, packed, bitsPerEntry);
        when(materialSet.isHidden(100)).thenReturn(false);
        when(materialSet.isHidden(101)).thenReturn(true);
        when(materialSet.isTileEntity(101)).thenReturn(false);

        Mode2Engine engine = new Mode2Engine(adapter, classifier, materialSet, exposureChecker);
        engine.obfuscateSection(section, world, 0, 0, 0, 0, 64, 12345L, 1.0);

        verify(adapter).setPaletteEntries(eq(section), any());
    }

    @Test
    void tileEntitiesNotUsedAsFakeBlocks() {
        when(materialSet.getHiddenBlockPaletteArray()).thenReturn(new int[]{DIAMOND_ORE_ID});
        assertFalse(materialSet.isTileEntity(DIAMOND_ORE_ID));

        List<Integer> palette = new ArrayList<>(Arrays.asList(STONE_ID, DIAMOND_ORE_ID));
        int bitsPerEntry = 4;
        long[] packed = new long[PaletteManipulator.computePackedArraySize(bitsPerEntry)];
        for (int i = 0; i < 4096; i++) {
            PaletteManipulator.setIndex(packed, i, 0, bitsPerEntry);
        }
        PaletteManipulator.setIndex(packed, 42, 1, bitsPerEntry);

        when(adapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt())).thenReturn(STONE_ID);

        Object section = createMockSection(palette, packed, bitsPerEntry);
        Mode2Engine engine = new Mode2Engine(adapter, classifier, materialSet, exposureChecker);
        engine.obfuscateSection(section, world, 0, 0, 0, 0, 64, 12345L, 0.5);

        verify(adapter).setPaletteEntries(eq(section), any());
    }

    @Test
    void emptyHiddenPalette_skipped() {
        when(materialSet.getHiddenBlockPaletteArray()).thenReturn(new int[]{});

        List<Integer> palette = new ArrayList<>(Arrays.asList(STONE_ID, DIAMOND_ORE_ID));
        int bitsPerEntry = 4;
        long[] packed = new long[PaletteManipulator.computePackedArraySize(bitsPerEntry)];
        for (int i = 0; i < 4096; i++) {
            PaletteManipulator.setIndex(packed, i, 0, bitsPerEntry);
        }

        Object section = createMockSection(palette, packed, bitsPerEntry);
        Mode2Engine engine = new Mode2Engine(adapter, classifier, materialSet, exposureChecker);
        engine.obfuscateSection(section, world, 0, 0, 0, 0, 64, 12345L, 0.5);

        verify(adapter, never()).setPaletteEntries(any(), any());
    }

    @Test
    void sectionAboveMaxBlockHeight_skipped() {
        Object section = new Object();
        when(adapter.getSectionNonEmptyCount(section)).thenReturn(1);
        when(adapter.isDirectPalette(section)).thenReturn(false);

        Mode2Engine engine = new Mode2Engine(adapter, classifier, materialSet, exposureChecker);
        engine.obfuscateSection(section, world, 0, 0, 128, 0, 64, 12345L, 0.5);

        verify(adapter, never()).getPaletteEntries(section);
    }

    @Test
    void directPalette_skipped() {
        Object section = new Object();
        when(adapter.getSectionNonEmptyCount(section)).thenReturn(1);
        when(adapter.isDirectPalette(section)).thenReturn(true);

        Mode2Engine engine = new Mode2Engine(adapter, classifier, materialSet, exposureChecker);
        engine.obfuscateSection(section, world, 0, 0, 0, 0, 64, 12345L, 0.5);

        verify(adapter, never()).getPaletteEntries(section);
    }

    @Test
    void hiddenBlocksReplacedWithReplacement() {
        List<Integer> palette = new ArrayList<>(Arrays.asList(STONE_ID, DIAMOND_ORE_ID));
        int bitsPerEntry = 4;
        long[] packed = new long[PaletteManipulator.computePackedArraySize(bitsPerEntry)];
        for (int i = 0; i < 4096; i++) {
            PaletteManipulator.setIndex(packed, i, 0, bitsPerEntry);
        }
        PaletteManipulator.setIndex(packed, 42, 1, bitsPerEntry);

        when(adapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt())).thenReturn(STONE_ID);

        Object section = createMockSection(palette, packed, bitsPerEntry);
        Mode2Engine engine = new Mode2Engine(adapter, classifier, materialSet, exposureChecker);
        engine.obfuscateSection(section, world, 0, 0, 0, 0, 64, 12345L, 0.0);

        verify(adapter).setPaletteEntries(eq(section), any());
    }

    @Test
    void singleValuePalette_nonHidden_upgradedWithReplacement() {
        Object section = new Object();
        when(adapter.isSingleValuePalette(section)).thenReturn(true);
        when(adapter.isDirectPalette(section)).thenReturn(false);
        when(adapter.getSectionNonEmptyCount(section)).thenReturn(1);
        when(adapter.getSingleValue(section)).thenReturn(STONE_ID);
        when(adapter.getPaletteEntries(section)).thenReturn(new ArrayList<>(Arrays.asList(STONE_ID, STONE_ID)));
        when(adapter.getPackedIndices(section)).thenReturn(new long[PaletteManipulator.computePackedArraySize(4)]);
        when(adapter.getPaletteBitsPerEntry(section)).thenReturn(4);

        Mode2Engine engine = new Mode2Engine(adapter, classifier, materialSet, exposureChecker);
        engine.obfuscateSection(section, world, 0, 0, 0, 0, 64, 12345L, 0.5);

        verify(adapter).upgradeToIndirectPalette(eq(section), eq(STONE_ID), eq(STONE_ID));
    }

    @Test
    void fakeOreChanceZero_noFakeOresInjected() {
        List<Integer> palette = new ArrayList<>(Arrays.asList(STONE_ID, DIAMOND_ORE_ID));
        int bitsPerEntry = 4;
        long[] packed = new long[PaletteManipulator.computePackedArraySize(bitsPerEntry)];
        for (int i = 0; i < 4096; i++) {
            PaletteManipulator.setIndex(packed, i, 0, bitsPerEntry);
        }
        PaletteManipulator.setIndex(packed, 42, 1, bitsPerEntry);

        when(adapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt())).thenReturn(STONE_ID);

        Object section = createMockSection(palette, packed, bitsPerEntry);
        Mode2Engine engine = new Mode2Engine(adapter, classifier, materialSet, exposureChecker);
        engine.obfuscateSection(section, world, 0, 0, 0, 0, 64, 12345L, 0.0);

        verify(adapter).setPaletteEntries(eq(section), any());
    }

    @Test
    void airInHiddenBlocks_airAdjacentToOre_replacedWithReplacement() {
        when(materialSet.isAirInHiddenBlocks()).thenReturn(true);
        BlockClassifier mockClassifier = mock(BlockClassifier.class);
        BlockClassification[] classifications = new BlockClassification[PaletteManipulator.BLOCKS_PER_SECTION];
        for (int i = 0; i < classifications.length; i++) {
            classifications[i] = BlockClassification.NORMAL;
        }
        classifications[1] = BlockClassification.AIR;
        when(mockClassifier.classifySection(eq(world), any(), any(), anyInt(), anyInt(), anyInt(), anyInt()))
            .thenReturn(classifications);

        List<Integer> palette = new ArrayList<>(Arrays.asList(AIR_ID, STONE_ID));
        int bitsPerEntry = 4;
        long[] packed = new long[PaletteManipulator.computePackedArraySize(bitsPerEntry)];
        for (int i = 0; i < 4096; i++) {
            PaletteManipulator.setIndex(packed, i, 0, bitsPerEntry);
        }
        PaletteManipulator.setIndex(packed, 1, 1, bitsPerEntry);

        when(adapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt())).thenReturn(STONE_ID);
        when(adapter.getBlockStateAt(world, 0, 0, 0)).thenReturn(DIAMOND_ORE_ID);

        Object section = createMockSection(palette, packed, bitsPerEntry);
        Mode2Engine engine = new Mode2Engine(adapter, mockClassifier, materialSet, exposureChecker);
        engine.obfuscateSection(section, world, 0, 0, 0, 0, 64, 12345L, 0.0);

        verify(adapter).setPaletteEntries(eq(section), any());
    }

    @Test
    void airInHiddenBlocks_airNotAdjacentToOre_notReplaced() {
        when(materialSet.isAirInHiddenBlocks()).thenReturn(true);
        BlockClassifier mockClassifier = mock(BlockClassifier.class);
        BlockClassification[] classifications = new BlockClassification[PaletteManipulator.BLOCKS_PER_SECTION];
        for (int i = 0; i < classifications.length; i++) {
            classifications[i] = BlockClassification.NORMAL;
        }
        classifications[1] = BlockClassification.AIR;
        when(mockClassifier.classifySection(eq(world), any(), any(), anyInt(), anyInt(), anyInt(), anyInt()))
            .thenReturn(classifications);

        List<Integer> palette = new ArrayList<>(Arrays.asList(STONE_ID, AIR_ID, DIAMOND_ORE_ID, IRON_ORE_ID));
        int bitsPerEntry = 4;
        long[] packed = new long[PaletteManipulator.computePackedArraySize(bitsPerEntry)];
        for (int i = 0; i < 4096; i++) {
            PaletteManipulator.setIndex(packed, i, 0, bitsPerEntry);
        }
        PaletteManipulator.setIndex(packed, 1, 1, bitsPerEntry);

        when(adapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt())).thenReturn(STONE_ID);

        Object section = createMockSection(palette, packed, bitsPerEntry);
        Mode2Engine engine = new Mode2Engine(adapter, mockClassifier, materialSet, exposureChecker);
        engine.obfuscateSection(section, world, 0, 0, 0, 0, 64, 12345L, 0.0);

        verify(adapter, never()).setPaletteEntries(any(), any());
    }
}
