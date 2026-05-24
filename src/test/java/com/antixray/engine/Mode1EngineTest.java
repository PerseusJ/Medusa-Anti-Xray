package com.antixray.engine;

import com.antixray.nms.NmsAdapter;
import com.antixray.util.MaterialSet;
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

class Mode1EngineTest {

    private static final int STONE_ID = 1;
    private static final int DEEPSLATE_ID = 2;
    private static final int NETHERRACK_ID = 3;
    private static final int END_STONE_ID = 4;
    private static final int DIAMOND_ORE_ID = 50;
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
        when(materialSet.getReplacement(anyInt(), anyInt(), eq("NORMAL"))).thenReturn(STONE_ID);
        when(materialSet.getReplacement(anyInt(), anyInt(), eq("NETHER"))).thenReturn(NETHERRACK_ID);
        when(materialSet.getReplacement(anyInt(), anyInt(), eq("THE_END"))).thenReturn(END_STONE_ID);
        when(materialSet.isHidden(DIAMOND_ORE_ID)).thenReturn(true);
        when(materialSet.isHidden(CHEST_ID)).thenReturn(true);
        when(materialSet.isTileEntity(CHEST_ID)).thenReturn(true);
        when(materialSet.isTileEntity(DIAMOND_ORE_ID)).thenReturn(false);
        when(materialSet.isHidden(STONE_ID)).thenReturn(false);
        when(materialSet.isHidden(AIR_ID)).thenReturn(false);
        when(materialSet.isTransparent(AIR_ID)).thenReturn(true);
        when(materialSet.isTransparent(STONE_ID)).thenReturn(false);
        when(materialSet.isTransparent(DIAMOND_ORE_ID)).thenReturn(false);
        when(materialSet.getHiddenBlockPaletteArray()).thenReturn(new int[]{DIAMOND_ORE_ID});

        when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);
        when(world.getName()).thenReturn("world");

        when(adapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt())).thenReturn(STONE_ID);

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
    void hiddenDiamondOre_replacedWithStone() {
        List<Integer> palette = new ArrayList<>(Arrays.asList(STONE_ID, DIAMOND_ORE_ID));
        int bitsPerEntry = 4;
        long[] packed = new long[PaletteManipulator.computePackedArraySize(bitsPerEntry)];
        for (int i = 0; i < 4096; i++) {
            PaletteManipulator.setIndex(packed, i, 0, bitsPerEntry);
        }
        PaletteManipulator.setIndex(packed, 42, 1, bitsPerEntry);

        Object section = createMockSection(palette, packed, bitsPerEntry);
        Mode1Engine engine = new Mode1Engine(adapter, classifier, materialSet);
        engine.obfuscateSection(section, world, 0, 0, 0, 0, 64);

        verify(adapter, atLeastOnce()).setPaletteEntries(eq(section), any());
    }

    @Test
    void airExposedDiamondOre_leftAsIs() {
        List<Integer> palette = new ArrayList<>(Arrays.asList(STONE_ID, DIAMOND_ORE_ID));
        int bitsPerEntry = 4;
        long[] packed = new long[PaletteManipulator.computePackedArraySize(bitsPerEntry)];
        for (int i = 0; i < 4096; i++) {
            PaletteManipulator.setIndex(packed, i, 0, bitsPerEntry);
        }
        PaletteManipulator.setIndex(packed, 0, 1, bitsPerEntry);

        int worldX = 0, worldY = 0, worldZ = 0;
        when(adapter.getBlockStateAt(world, worldX + 1, worldY, worldZ)).thenReturn(AIR_ID);
        when(adapter.getBlockStateAt(world, worldX - 1, worldY, worldZ)).thenReturn(STONE_ID);
        when(adapter.getBlockStateAt(world, worldX, worldY + 1, worldZ)).thenReturn(STONE_ID);
        when(adapter.getBlockStateAt(world, worldX, worldY - 1, worldZ)).thenReturn(STONE_ID);
        when(adapter.getBlockStateAt(world, worldX, worldY, worldZ + 1)).thenReturn(STONE_ID);
        when(adapter.getBlockStateAt(world, worldX, worldY, worldZ - 1)).thenReturn(STONE_ID);

        Object section = createMockSection(palette, packed, bitsPerEntry);
        Mode1Engine engine = new Mode1Engine(adapter, classifier, materialSet);
        engine.obfuscateSection(section, world, 0, 0, 0, 0, 64);

        verify(adapter, never()).setPaletteEntries(any(), any());
    }

    @Test
    void tileEntityChest_alwaysReplaced() {
        List<Integer> palette = new ArrayList<>(Arrays.asList(STONE_ID, CHEST_ID));
        int bitsPerEntry = 4;
        long[] packed = new long[PaletteManipulator.computePackedArraySize(bitsPerEntry)];
        for (int i = 0; i < 4096; i++) {
            PaletteManipulator.setIndex(packed, i, 0, bitsPerEntry);
        }
        PaletteManipulator.setIndex(packed, 0, 1, bitsPerEntry);

        Object section = createMockSection(palette, packed, bitsPerEntry);
        Mode1Engine engine = new Mode1Engine(adapter, classifier, materialSet);
        engine.obfuscateSection(section, world, 0, 0, 0, 0, 64);

        verify(adapter, atLeastOnce()).setPaletteEntries(eq(section), any());
    }

    @Test
    void dimensionAware_overworldUsesStone() {
        when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);
        when(materialSet.getReplacement(anyInt(), anyInt(), eq("NORMAL"))).thenReturn(STONE_ID);

        int result = materialSet.getReplacement(10, 0, "NORMAL");
        assertEquals(STONE_ID, result);
    }

    @Test
    void dimensionAware_netherUsesNetherrack() {
        when(world.getEnvironment()).thenReturn(World.Environment.NETHER);
        when(materialSet.getReplacement(anyInt(), anyInt(), eq("NETHER"))).thenReturn(NETHERRACK_ID);

        int result = materialSet.getReplacement(10, 0, "NETHER");
        assertEquals(NETHERRACK_ID, result);
    }

    @Test
    void dimensionAware_endUsesEndStone() {
        when(world.getEnvironment()).thenReturn(World.Environment.THE_END);
        when(materialSet.getReplacement(anyInt(), anyInt(), eq("THE_END"))).thenReturn(END_STONE_ID);

        int result = materialSet.getReplacement(10, 0, "THE_END");
        assertEquals(END_STONE_ID, result);
    }

    @Test
    void deepslateYThreshold_belowThresholdUsesDeepslate() {
        when(materialSet.getReplacement(anyInt(), anyInt(), anyString())).thenAnswer(inv -> {
            int y = inv.getArgument(0);
            int deepslateBelowY = inv.getArgument(1);
            String env = inv.getArgument(2);
            if (env.equals("NETHER")) return NETHERRACK_ID;
            if (env.equals("THE_END")) return END_STONE_ID;
            return y < deepslateBelowY ? DEEPSLATE_ID : STONE_ID;
        });

        List<Integer> palette = new ArrayList<>(Arrays.asList(STONE_ID, DIAMOND_ORE_ID));
        int bitsPerEntry = 4;
        long[] packed = new long[PaletteManipulator.computePackedArraySize(bitsPerEntry)];
        for (int i = 0; i < 4096; i++) {
            PaletteManipulator.setIndex(packed, i, 0, bitsPerEntry);
        }
        PaletteManipulator.setIndex(packed, 0, 1, bitsPerEntry);

        Object section = createMockSection(palette, packed, bitsPerEntry);
        Mode1Engine engine = new Mode1Engine(adapter, classifier, materialSet);

        int deepslateBelowY = -10;
        int sectionBaseY = -48;
        engine.obfuscateSection(section, world, 0, 0, sectionBaseY, deepslateBelowY, 64);

        verify(adapter).setPaletteEntries(eq(section), any());
    }

    @Test
    void sectionAboveMaxBlockHeight_skipped() {
        Object section = new Object();
        when(adapter.getSectionNonEmptyCount(section)).thenReturn(1);
        when(adapter.isDirectPalette(section)).thenReturn(false);
        when(adapter.isSingleValuePalette(section)).thenReturn(false);

        Mode1Engine engine = new Mode1Engine(adapter, classifier, materialSet);
        engine.obfuscateSection(section, world, 0, 0, 128, 0, 64);

        verify(adapter, never()).getPaletteEntries(section);
    }

    @Test
    void emptySection_skipped() {
        Object section = new Object();
        when(adapter.getSectionNonEmptyCount(section)).thenReturn(0);

        Mode1Engine engine = new Mode1Engine(adapter, classifier, materialSet);
        engine.obfuscateSection(section, world, 0, 0, 0, 0, 64);

        verify(adapter, never()).getPaletteEntries(section);
    }

    @Test
    void directPalette_skipped() {
        Object section = new Object();
        when(adapter.getSectionNonEmptyCount(section)).thenReturn(1);
        when(adapter.isDirectPalette(section)).thenReturn(true);

        Mode1Engine engine = new Mode1Engine(adapter, classifier, materialSet);
        engine.obfuscateSection(section, world, 0, 0, 0, 0, 64);

        verify(adapter, never()).getPaletteEntries(section);
    }

    @Test
    void noHiddenBlocks_noModification() {
        List<Integer> palette = new ArrayList<>(Arrays.asList(STONE_ID));
        int bitsPerEntry = 4;
        long[] packed = new long[PaletteManipulator.computePackedArraySize(bitsPerEntry)];

        Object section = createMockSection(palette, packed, bitsPerEntry);
        Mode1Engine engine = new Mode1Engine(adapter, classifier, materialSet);
        engine.obfuscateSection(section, world, 0, 0, 0, 0, 64);

        verify(adapter, never()).setPaletteEntries(any(), any());
    }

    @Test
    void singleValuePalette_hiddenBlock_upgradedAndReplaced() {
        Object section = new Object();
        when(adapter.isSingleValuePalette(section)).thenReturn(true);
        when(adapter.isDirectPalette(section)).thenReturn(false);
        when(adapter.getSectionNonEmptyCount(section)).thenReturn(1);
        when(adapter.getSingleValue(section)).thenReturn(DIAMOND_ORE_ID);

        Mode1Engine engine = new Mode1Engine(adapter, classifier, materialSet);
        engine.obfuscateSection(section, world, 0, 0, 0, 0, 64);

        verify(adapter).setPaletteEntries(eq(section), any());
    }

    @Test
    void singleValuePalette_nonHiddenBlock_skipped() {
        Object section = new Object();
        when(adapter.isSingleValuePalette(section)).thenReturn(true);
        when(adapter.isDirectPalette(section)).thenReturn(false);
        when(adapter.getSectionNonEmptyCount(section)).thenReturn(1);
        when(adapter.getSingleValue(section)).thenReturn(STONE_ID);

        Mode1Engine engine = new Mode1Engine(adapter, classifier, materialSet);
        engine.obfuscateSection(section, world, 0, 0, 0, 0, 64);

        verify(adapter, never()).setPaletteEntries(any(), any());
    }

    @Test
    void getReplacementBlockStateId_overworld() {
        when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);
        when(materialSet.getReplacement(10, 0, "NORMAL")).thenReturn(STONE_ID);

        Mode1Engine engine = new Mode1Engine(adapter, classifier, materialSet);
        assertEquals(STONE_ID, engine.getReplacementBlockStateId(world, 10, 0));
    }

    @Test
    void mixedHiddenAndExposed_onlyHiddenReplaced() {
        List<Integer> palette = new ArrayList<>(Arrays.asList(STONE_ID, DIAMOND_ORE_ID));
        int bitsPerEntry = 4;
        long[] packed = new long[PaletteManipulator.computePackedArraySize(bitsPerEntry)];
        for (int i = 0; i < 4096; i++) {
            PaletteManipulator.setIndex(packed, i, 0, bitsPerEntry);
        }
        PaletteManipulator.setIndex(packed, 0, 1, bitsPerEntry);
        PaletteManipulator.setIndex(packed, 100, 1, bitsPerEntry);

        int worldX0 = 0, worldY0 = 0, worldZ0 = 0;
        when(adapter.getBlockStateAt(world, worldX0 + 1, worldY0, worldZ0)).thenReturn(AIR_ID);
        when(adapter.getBlockStateAt(world, worldX0 - 1, worldY0, worldZ0)).thenReturn(STONE_ID);
        when(adapter.getBlockStateAt(world, worldX0, worldY0 + 1, worldZ0)).thenReturn(STONE_ID);
        when(adapter.getBlockStateAt(world, worldX0, worldY0 - 1, worldZ0)).thenReturn(STONE_ID);
        when(adapter.getBlockStateAt(world, worldX0, worldY0, worldZ0 + 1)).thenReturn(STONE_ID);
        when(adapter.getBlockStateAt(world, worldX0, worldY0, worldZ0 - 1)).thenReturn(STONE_ID);

        Object section = createMockSection(palette, packed, bitsPerEntry);
        Mode1Engine engine = new Mode1Engine(adapter, classifier, materialSet);
        engine.obfuscateSection(section, world, 0, 0, 0, 0, 64);

        verify(adapter).setPaletteEntries(eq(section), any());
    }
}
