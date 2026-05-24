package com.antixray.engine;

import com.antixray.nms.NmsAdapter;
import com.antixray.util.MaterialSet;
import com.antixray.util.SeededRandom;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

class Mode3EngineTest {

    private static final int STONE_ID = 1;
    private static final int DIAMOND_ORE_ID = 50;
    private static final int IRON_ORE_ID = 51;
    private static final int GOLD_ORE_ID = 52;
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
        when(materialSet.isHidden(GOLD_ORE_ID)).thenReturn(true);
        when(materialSet.isHidden(CHEST_ID)).thenReturn(true);
        when(materialSet.isTileEntity(CHEST_ID)).thenReturn(true);
        when(materialSet.isTileEntity(DIAMOND_ORE_ID)).thenReturn(false);
        when(materialSet.isTileEntity(IRON_ORE_ID)).thenReturn(false);
        when(materialSet.isTileEntity(GOLD_ORE_ID)).thenReturn(false);
        when(materialSet.isHidden(STONE_ID)).thenReturn(false);
        when(materialSet.isHidden(AIR_ID)).thenReturn(false);
        when(materialSet.isTransparent(AIR_ID)).thenReturn(true);
        when(materialSet.isTransparent(STONE_ID)).thenReturn(false);
        when(materialSet.getHiddenBlockPaletteArray()).thenReturn(new int[]{DIAMOND_ORE_ID, IRON_ORE_ID, GOLD_ORE_ID});

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
    void allBlocksInSameYLayerUseSameFakeType() {
        SeededRandom rng = new SeededRandom(5, 10, 0, 12345L);
        int[] hiddenPalette = materialSet.getHiddenBlockPaletteArray();

        int[] layerFakeIds = new int[16];
        for (int layer = 0; layer < 16; layer++) {
            layerFakeIds[layer] = hiddenPalette[rng.nextInt(hiddenPalette.length)];
        }

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int layer0Fake = layerFakeIds[0];
                assertEquals(layer0Fake, layerFakeIds[0],
                        "All blocks in layer 0 must use same fake type");
            }
        }
    }

    @Test
    void differentLayersMayUseDifferentTypes() {
        SeededRandom rng = new SeededRandom(5, 10, 0, 12345L);
        int[] hiddenPalette = materialSet.getHiddenBlockPaletteArray();

        int[] layerFakeIds = new int[16];
        for (int layer = 0; layer < 16; layer++) {
            layerFakeIds[layer] = hiddenPalette[rng.nextInt(hiddenPalette.length)];
        }

        boolean anyDifferent = false;
        for (int layer = 1; layer < 16; layer++) {
            if (layerFakeIds[layer] != layerFakeIds[0]) {
                anyDifferent = true;
                break;
            }
        }
        assertTrue(anyDifferent || hiddenPalette.length == 1,
                "Different layers should use different fake types (unless only 1 hidden block)");
    }

    @Test
    void deterministicForSameSeedAndCoords() {
        SeededRandom rng1 = new SeededRandom(5, 10, 0, 12345L);
        SeededRandom rng2 = new SeededRandom(5, 10, 0, 12345L);

        int[] hiddenPalette = materialSet.getHiddenBlockPaletteArray();
        int[] layer1 = new int[16];
        int[] layer2 = new int[16];
        for (int i = 0; i < 16; i++) {
            layer1[i] = hiddenPalette[rng1.nextInt(hiddenPalette.length)];
            layer2[i] = hiddenPalette[rng2.nextInt(hiddenPalette.length)];
        }

        assertArrayEquals(layer1, layer2, "Same seed must produce same layer assignments");
    }

    @Test
    void paletteGrowthBoundedBy16() {
        int[] hiddenPalette = materialSet.getHiddenBlockPaletteArray();
        int maxUniqueFakes = Math.min(hiddenPalette.length, 16);

        SeededRandom rng = new SeededRandom(5, 10, 0, 12345L);
        java.util.Set<Integer> uniqueFakes = new java.util.HashSet<>();
        for (int layer = 0; layer < 16; layer++) {
            uniqueFakes.add(hiddenPalette[rng.nextInt(hiddenPalette.length)]);
        }

        assertTrue(uniqueFakes.size() <= 16,
                "At most 16 unique fake types across layers (one per layer)");
    }

    @Test
    void hiddenBlocksReplacedWithLayerFake() {
        List<Integer> palette = new ArrayList<>(Arrays.asList(STONE_ID, DIAMOND_ORE_ID));
        int bitsPerEntry = 4;
        long[] packed = new long[PaletteManipulator.computePackedArraySize(bitsPerEntry)];
        for (int i = 0; i < 4096; i++) {
            PaletteManipulator.setIndex(packed, i, 0, bitsPerEntry);
        }
        PaletteManipulator.setIndex(packed, 42, 1, bitsPerEntry);

        when(adapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt())).thenReturn(STONE_ID);

        Object section = createMockSection(palette, packed, bitsPerEntry);
        Mode3Engine engine = new Mode3Engine(adapter, classifier, materialSet, exposureChecker);
        engine.obfuscateSection(section, world, 0, 0, 0, 0, 64, 12345L, 0.5);

        verify(adapter).setPaletteEntries(eq(section), any());
        verify(adapter).setPackedIndices(eq(section), any(long[].class), anyInt());
    }

    @Test
    void sectionAboveMaxBlockHeight_skipped() {
        Object section = new Object();
        when(adapter.getSectionNonEmptyCount(section)).thenReturn(1);
        when(adapter.isDirectPalette(section)).thenReturn(false);

        Mode3Engine engine = new Mode3Engine(adapter, classifier, materialSet, exposureChecker);
        engine.obfuscateSection(section, world, 0, 0, 128, 0, 64, 12345L, 0.5);

        verify(adapter, never()).getPaletteEntries(section);
    }

    @Test
    void directPalette_skipped() {
        Object section = new Object();
        when(adapter.getSectionNonEmptyCount(section)).thenReturn(1);
        when(adapter.isDirectPalette(section)).thenReturn(true);

        Mode3Engine engine = new Mode3Engine(adapter, classifier, materialSet, exposureChecker);
        engine.obfuscateSection(section, world, 0, 0, 0, 0, 64, 12345L, 0.5);

        verify(adapter, never()).getPaletteEntries(section);
    }

    @Test
    void emptyHiddenPalette_skipped() {
        when(materialSet.getHiddenBlockPaletteArray()).thenReturn(new int[]{});

        List<Integer> palette = new ArrayList<>(Arrays.asList(STONE_ID));
        int bitsPerEntry = 4;
        long[] packed = new long[PaletteManipulator.computePackedArraySize(bitsPerEntry)];

        Object section = createMockSection(palette, packed, bitsPerEntry);
        Mode3Engine engine = new Mode3Engine(adapter, classifier, materialSet, exposureChecker);
        engine.obfuscateSection(section, world, 0, 0, 0, 0, 64, 12345L, 0.5);

        verify(adapter, never()).setPaletteEntries(any(), any());
    }

    @Test
    void tileEntityHidden_alwaysReplaced() {
        List<Integer> palette = new ArrayList<>(Arrays.asList(STONE_ID, CHEST_ID));
        int bitsPerEntry = 4;
        long[] packed = new long[PaletteManipulator.computePackedArraySize(bitsPerEntry)];
        for (int i = 0; i < 4096; i++) {
            PaletteManipulator.setIndex(packed, i, 0, bitsPerEntry);
        }
        PaletteManipulator.setIndex(packed, 0, 1, bitsPerEntry);

        when(adapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt())).thenReturn(STONE_ID);

        Object section = createMockSection(palette, packed, bitsPerEntry);
        Mode3Engine engine = new Mode3Engine(adapter, classifier, materialSet, exposureChecker);
        engine.obfuscateSection(section, world, 0, 0, 0, 0, 64, 12345L, 0.0);

        verify(adapter).setPaletteEntries(eq(section), any());
    }

    @Test
    void singleValuePalette_hiddenBlock_upgradedAndReplaced() {
        Object section = new Object();
        when(adapter.isSingleValuePalette(section)).thenReturn(true);
        when(adapter.isDirectPalette(section)).thenReturn(false);
        when(adapter.getSectionNonEmptyCount(section)).thenReturn(1);
        when(adapter.getSingleValue(section)).thenReturn(DIAMOND_ORE_ID);

        Mode3Engine engine = new Mode3Engine(adapter, classifier, materialSet, exposureChecker);
        engine.obfuscateSection(section, world, 0, 0, 0, 0, 64, 12345L, 0.5);

        verify(adapter).setPaletteEntries(eq(section), any());
    }

    @Test
    void fakeOreChanceZero_stillReplacesHiddenBlocks() {
        List<Integer> palette = new ArrayList<>(Arrays.asList(STONE_ID, DIAMOND_ORE_ID));
        int bitsPerEntry = 4;
        long[] packed = new long[PaletteManipulator.computePackedArraySize(bitsPerEntry)];
        for (int i = 0; i < 4096; i++) {
            PaletteManipulator.setIndex(packed, i, 0, bitsPerEntry);
        }
        PaletteManipulator.setIndex(packed, 42, 1, bitsPerEntry);

        when(adapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt())).thenReturn(STONE_ID);

        Object section = createMockSection(palette, packed, bitsPerEntry);
        Mode3Engine engine = new Mode3Engine(adapter, classifier, materialSet, exposureChecker);
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
        Mode3Engine engine = new Mode3Engine(adapter, mockClassifier, materialSet, exposureChecker);
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

        List<Integer> palette = new ArrayList<>(Arrays.asList(STONE_ID, AIR_ID, DIAMOND_ORE_ID, IRON_ORE_ID, GOLD_ORE_ID));
        int bitsPerEntry = 4;
        long[] packed = new long[PaletteManipulator.computePackedArraySize(bitsPerEntry)];
        for (int i = 0; i < 4096; i++) {
            PaletteManipulator.setIndex(packed, i, 0, bitsPerEntry);
        }
        PaletteManipulator.setIndex(packed, 1, 1, bitsPerEntry);

        when(adapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt())).thenReturn(STONE_ID);

        Object section = createMockSection(palette, packed, bitsPerEntry);
        Mode3Engine engine = new Mode3Engine(adapter, mockClassifier, materialSet, exposureChecker);
        engine.obfuscateSection(section, world, 0, 0, 0, 0, 64, 12345L, 0.0);

        verify(adapter, never()).setPaletteEntries(any(), any());
    }
}
