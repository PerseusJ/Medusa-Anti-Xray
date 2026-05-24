package com.antixray.engine;

import com.antixray.nms.NmsAdapter;
import com.antixray.util.MaterialSet;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.World;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class BlockClassifierTest {

    private static final int STONE_ID = 1;
    private static final int DIAMOND_ORE_ID = 50;
    private static final int CHEST_ID = 60;
    private static final int AIR_ID = 0;

    private NmsAdapter adapter;
    private World world;
    private MaterialSet materialSet;
    private AirExposureChecker exposureChecker;
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
        exposureChecker = new AirExposureChecker(adapter, materialSet, true);
    }

    @AfterEach
    void tearDown() {
        if (bukkitMock != null) {
            bukkitMock.close();
        }
    }

    private long[] buildPacked(int bitsPerEntry) {
        int longsNeeded = PaletteManipulator.computePackedArraySize(bitsPerEntry);
        return new long[longsNeeded];
    }

    @Test
    void hiddenNotAirExposed_classifiedAsHidden() {
        List<Integer> palette = Arrays.asList(STONE_ID, DIAMOND_ORE_ID);
        long[] packed = buildPacked(4);
        PaletteManipulator.setIndex(packed, 0, 1, 4);
        when(adapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt())).thenReturn(STONE_ID);
        when(materialSet.isHidden(DIAMOND_ORE_ID)).thenReturn(true);
        when(materialSet.isTileEntity(DIAMOND_ORE_ID)).thenReturn(false);
        when(materialSet.isTransparent(STONE_ID)).thenReturn(false);
        BlockClassifier classifier = new BlockClassifier(materialSet, exposureChecker);
        BlockClassification[] result = classifier.classifySection(world, palette, packed, 4, 0, 0, 0);
        assertEquals(BlockClassification.HIDDEN, result[0]);
    }

    @Test
    void hiddenAirExposed_classifiedAsAirExposed() {
        List<Integer> palette = Arrays.asList(STONE_ID, DIAMOND_ORE_ID);
        long[] packed = buildPacked(4);
        PaletteManipulator.setIndex(packed, 0, 1, 4);
        when(adapter.getBlockStateAt(world, 1, 0, 0)).thenReturn(AIR_ID);
        when(adapter.getBlockStateAt(world, -1, 0, 0)).thenReturn(STONE_ID);
        when(adapter.getBlockStateAt(world, 0, 1, 0)).thenReturn(STONE_ID);
        when(adapter.getBlockStateAt(world, 0, -1, 0)).thenReturn(STONE_ID);
        when(adapter.getBlockStateAt(world, 0, 0, 1)).thenReturn(STONE_ID);
        when(adapter.getBlockStateAt(world, 0, 0, -1)).thenReturn(STONE_ID);
        when(materialSet.isHidden(DIAMOND_ORE_ID)).thenReturn(true);
        when(materialSet.isTileEntity(DIAMOND_ORE_ID)).thenReturn(false);
        when(materialSet.isTransparent(AIR_ID)).thenReturn(true);
        when(materialSet.isTransparent(STONE_ID)).thenReturn(false);
        BlockClassifier classifier = new BlockClassifier(materialSet, exposureChecker);
        BlockClassification[] result = classifier.classifySection(world, palette, packed, 4, 0, 0, 0);
        assertEquals(BlockClassification.AIR_EXPOSED, result[0]);
    }

    @Test
    void tileEntityAlwaysClassifiedAsHiddenTileEntity() {
        List<Integer> palette = Arrays.asList(STONE_ID, CHEST_ID);
        long[] packed = buildPacked(4);
        PaletteManipulator.setIndex(packed, 0, 1, 4);
        when(materialSet.isHidden(CHEST_ID)).thenReturn(true);
        when(materialSet.isTileEntity(CHEST_ID)).thenReturn(true);
        BlockClassifier classifier = new BlockClassifier(materialSet, exposureChecker);
        BlockClassification[] result = classifier.classifySection(world, palette, packed, 4, 0, 0, 0);
        assertEquals(BlockClassification.HIDDEN_TILE_ENTITY, result[0]);
    }

    @Test
    void normalBlock_classifiedAsNormal() {
        List<Integer> palette = Arrays.asList(STONE_ID);
        long[] packed = buildPacked(4);
        when(materialSet.isHidden(STONE_ID)).thenReturn(false);
        BlockClassifier classifier = new BlockClassifier(materialSet, exposureChecker);
        BlockClassification[] result = classifier.classifySection(world, palette, packed, 4, 0, 0, 0);
        assertEquals(BlockClassification.NORMAL, result[0]);
        assertEquals(BlockClassification.NORMAL, result[4095]);
    }

    @Test
    void allNormalBlockSection_allNormal() {
        List<Integer> palette = Arrays.asList(STONE_ID);
        long[] packed = buildPacked(4);
        when(materialSet.isHidden(STONE_ID)).thenReturn(false);
        BlockClassifier classifier = new BlockClassifier(materialSet, exposureChecker);
        BlockClassification[] result = classifier.classifySection(world, palette, packed, 4, 0, 0, 0);
        for (int i = 0; i < 4096; i++) {
            assertEquals(BlockClassification.NORMAL, result[i]);
        }
    }

    @Test
    void tileEntityNotAirExposed_stillHiddenTileEntity() {
        List<Integer> palette = Arrays.asList(STONE_ID, CHEST_ID);
        long[] packed = buildPacked(4);
        PaletteManipulator.setIndex(packed, 0, 1, 4);
        when(materialSet.isHidden(CHEST_ID)).thenReturn(true);
        when(materialSet.isTileEntity(CHEST_ID)).thenReturn(true);
        BlockClassifier classifier = new BlockClassifier(materialSet, exposureChecker);
        BlockClassification[] result = classifier.classifySection(world, palette, packed, 4, 0, 0, 0);
        assertEquals(BlockClassification.HIDDEN_TILE_ENTITY, result[0]);
        verify(adapter, never()).getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt());
    }

    @Test
    void borderChunkUnloadedNeighbor_notAirExposed() {
        List<Integer> palette = Arrays.asList(STONE_ID, DIAMOND_ORE_ID);
        long[] packed = buildPacked(4);
        PaletteManipulator.setIndex(packed, 0, 1, 4);
        when(adapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt())).thenReturn(-1);
        when(materialSet.isHidden(DIAMOND_ORE_ID)).thenReturn(true);
        when(materialSet.isTileEntity(DIAMOND_ORE_ID)).thenReturn(false);
        BlockClassifier classifier = new BlockClassifier(materialSet, exposureChecker);
        BlockClassification[] result = classifier.classifySection(world, palette, packed, 4, 0, 0, 0);
        assertEquals(BlockClassification.HIDDEN, result[0]);
    }

    @Test
    void resultArrayLengthIs4096() {
        List<Integer> palette = Arrays.asList(STONE_ID);
        long[] packed = buildPacked(4);
        when(materialSet.isHidden(STONE_ID)).thenReturn(false);
        BlockClassifier classifier = new BlockClassifier(materialSet, exposureChecker);
        BlockClassification[] result = classifier.classifySection(world, palette, packed, 4, 0, 0, 0);
        assertEquals(4096, result.length);
    }

    @Test
    void classifySingle_normalBlock() {
        when(materialSet.isHidden(STONE_ID)).thenReturn(false);
        BlockClassifier classifier = new BlockClassifier(materialSet, exposureChecker);
        assertEquals(BlockClassification.NORMAL, classifier.classifySingle(world, STONE_ID, 0, 0, 0));
    }

    @Test
    void classifySingle_hiddenNotAirExposed() {
        when(adapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt())).thenReturn(STONE_ID);
        when(materialSet.isHidden(DIAMOND_ORE_ID)).thenReturn(true);
        when(materialSet.isTileEntity(DIAMOND_ORE_ID)).thenReturn(false);
        when(materialSet.isTransparent(STONE_ID)).thenReturn(false);
        BlockClassifier classifier = new BlockClassifier(materialSet, exposureChecker);
        assertEquals(BlockClassification.HIDDEN, classifier.classifySingle(world, DIAMOND_ORE_ID, 0, 0, 0));
    }

    @Test
    void classifySingle_hiddenAirExposed() {
        when(adapter.getBlockStateAt(world, 1, 0, 0)).thenReturn(AIR_ID);
        when(adapter.getBlockStateAt(world, -1, 0, 0)).thenReturn(STONE_ID);
        when(adapter.getBlockStateAt(world, 0, 1, 0)).thenReturn(STONE_ID);
        when(adapter.getBlockStateAt(world, 0, -1, 0)).thenReturn(STONE_ID);
        when(adapter.getBlockStateAt(world, 0, 0, 1)).thenReturn(STONE_ID);
        when(adapter.getBlockStateAt(world, 0, 0, -1)).thenReturn(STONE_ID);
        when(materialSet.isHidden(DIAMOND_ORE_ID)).thenReturn(true);
        when(materialSet.isTileEntity(DIAMOND_ORE_ID)).thenReturn(false);
        when(materialSet.isTransparent(AIR_ID)).thenReturn(true);
        when(materialSet.isTransparent(STONE_ID)).thenReturn(false);
        BlockClassifier classifier = new BlockClassifier(materialSet, exposureChecker);
        assertEquals(BlockClassification.AIR_EXPOSED, classifier.classifySingle(world, DIAMOND_ORE_ID, 0, 0, 0));
    }

    @Test
    void classifySingle_tileEntity() {
        when(materialSet.isHidden(CHEST_ID)).thenReturn(true);
        when(materialSet.isTileEntity(CHEST_ID)).thenReturn(true);
        BlockClassifier classifier = new BlockClassifier(materialSet, exposureChecker);
        assertEquals(BlockClassification.HIDDEN_TILE_ENTITY, classifier.classifySingle(world, CHEST_ID, 0, 0, 0));
    }

    @Test
    void sectionBaseYAppliedCorrectly() {
        List<Integer> palette = Arrays.asList(STONE_ID, DIAMOND_ORE_ID);
        long[] packed = buildPacked(4);
        PaletteManipulator.setIndex(packed, 0, 1, 4);
        int sectionBaseY = -64;
        when(adapter.getBlockStateAt(world, 1, -64, 0)).thenReturn(AIR_ID);
        when(adapter.getBlockStateAt(world, -1, -64, 0)).thenReturn(STONE_ID);
        when(adapter.getBlockStateAt(world, 0, -63, 0)).thenReturn(STONE_ID);
        when(adapter.getBlockStateAt(world, 0, -65, 0)).thenReturn(STONE_ID);
        when(adapter.getBlockStateAt(world, 0, -64, 1)).thenReturn(STONE_ID);
        when(adapter.getBlockStateAt(world, 0, -64, -1)).thenReturn(STONE_ID);
        when(materialSet.isHidden(DIAMOND_ORE_ID)).thenReturn(true);
        when(materialSet.isTileEntity(DIAMOND_ORE_ID)).thenReturn(false);
        when(materialSet.isTransparent(AIR_ID)).thenReturn(true);
        when(materialSet.isTransparent(STONE_ID)).thenReturn(false);
        BlockClassifier classifier = new BlockClassifier(materialSet, exposureChecker);
        BlockClassification[] result = classifier.classifySection(world, palette, packed, 4, sectionBaseY, 0, 0);
        assertEquals(BlockClassification.AIR_EXPOSED, result[0]);
    }

    @Test
    void chunkOffsetAppliedCorrectly() {
        List<Integer> palette = Arrays.asList(STONE_ID, DIAMOND_ORE_ID);
        long[] packed = buildPacked(4);
        PaletteManipulator.setIndex(packed, 0, 1, 4);
        when(adapter.getBlockStateAt(world, 33, 0, 49)).thenReturn(AIR_ID);
        when(adapter.getBlockStateAt(world, 31, 0, 49)).thenReturn(STONE_ID);
        when(adapter.getBlockStateAt(world, 32, 1, 49)).thenReturn(STONE_ID);
        when(adapter.getBlockStateAt(world, 32, -1, 49)).thenReturn(STONE_ID);
        when(adapter.getBlockStateAt(world, 32, 0, 50)).thenReturn(STONE_ID);
        when(adapter.getBlockStateAt(world, 32, 0, 48)).thenReturn(STONE_ID);
        when(materialSet.isHidden(DIAMOND_ORE_ID)).thenReturn(true);
        when(materialSet.isTileEntity(DIAMOND_ORE_ID)).thenReturn(false);
        when(materialSet.isTransparent(AIR_ID)).thenReturn(true);
        when(materialSet.isTransparent(STONE_ID)).thenReturn(false);
        BlockClassifier classifier = new BlockClassifier(materialSet, exposureChecker);
        BlockClassification[] result = classifier.classifySection(world, palette, packed, 4, 0, 2, 3);
        assertEquals(BlockClassification.AIR_EXPOSED, result[0]);
    }

    @Test
    void tileEntityAirExposed_stillHiddenTileEntity() {
        List<Integer> palette = Arrays.asList(STONE_ID, CHEST_ID);
        long[] packed = buildPacked(4);
        PaletteManipulator.setIndex(packed, 0, 1, 4);
        when(adapter.getBlockStateAt(world, 1, 0, 0)).thenReturn(AIR_ID);
        when(adapter.getBlockStateAt(world, -1, 0, 0)).thenReturn(AIR_ID);
        when(adapter.getBlockStateAt(world, 0, 1, 0)).thenReturn(AIR_ID);
        when(adapter.getBlockStateAt(world, 0, -1, 0)).thenReturn(AIR_ID);
        when(adapter.getBlockStateAt(world, 0, 0, 1)).thenReturn(AIR_ID);
        when(adapter.getBlockStateAt(world, 0, 0, -1)).thenReturn(AIR_ID);
        when(materialSet.isHidden(CHEST_ID)).thenReturn(true);
        when(materialSet.isTileEntity(CHEST_ID)).thenReturn(true);
        when(materialSet.isTransparent(AIR_ID)).thenReturn(true);
        BlockClassifier classifier = new BlockClassifier(materialSet, exposureChecker);
        BlockClassification result = classifier.classifySingle(world, CHEST_ID, 0, 0, 0);
        assertEquals(BlockClassification.HIDDEN_TILE_ENTITY, result);
    }

    @Test
    void airInHiddenBlocks_airBlock_classifiedAsAir() {
        when(materialSet.isAirInHiddenBlocks()).thenReturn(true);
        when(materialSet.isHidden(AIR_ID)).thenReturn(false);
        when(materialSet.isAirBlock(AIR_ID)).thenReturn(true);
        BlockClassifier classifier = new BlockClassifier(materialSet, exposureChecker);
        assertEquals(BlockClassification.AIR, classifier.classifySingle(world, AIR_ID, 0, 0, 0));
    }

    @Test
    void airNotInHiddenBlocks_airBlock_classifiedAsNormal() {
        when(materialSet.isAirInHiddenBlocks()).thenReturn(false);
        when(materialSet.isHidden(AIR_ID)).thenReturn(false);
        BlockClassifier classifier = new BlockClassifier(materialSet, exposureChecker);
        assertEquals(BlockClassification.NORMAL, classifier.classifySingle(world, AIR_ID, 0, 0, 0));
    }

    @Test
    void airInHiddenBlocks_nonAirTransparentBlock_classifiedAsNormal() {
        int waterId = 10;
        when(materialSet.isAirInHiddenBlocks()).thenReturn(true);
        when(materialSet.isHidden(waterId)).thenReturn(false);
        when(materialSet.isAirBlock(waterId)).thenReturn(false);
        BlockClassifier classifier = new BlockClassifier(materialSet, exposureChecker);
        assertEquals(BlockClassification.NORMAL, classifier.classifySingle(world, waterId, 0, 0, 0));
    }

    @Test
    void airInHiddenBlocks_airBlockInSection_classifiedAsAir() {
        List<Integer> palette = Arrays.asList(AIR_ID, STONE_ID);
        long[] packed = buildPacked(4);
        PaletteManipulator.setIndex(packed, 0, 0, 4);
        when(materialSet.isAirInHiddenBlocks()).thenReturn(true);
        when(materialSet.isHidden(AIR_ID)).thenReturn(false);
        when(materialSet.isAirBlock(AIR_ID)).thenReturn(true);
        when(materialSet.isHidden(STONE_ID)).thenReturn(false);
        BlockClassifier classifier = new BlockClassifier(materialSet, exposureChecker);
        BlockClassification[] result = classifier.classifySection(world, palette, packed, 4, 0, 0, 0);
        assertEquals(BlockClassification.AIR, result[0]);
    }
}
