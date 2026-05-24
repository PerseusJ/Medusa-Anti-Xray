package com.antixray.engine;

import com.antixray.nms.NmsAdapter;
import com.antixray.util.MaterialSet;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.World;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AirExposureCheckerTest {

    private NmsAdapter adapter;
    private World world;
    private MaterialSet materialSet;
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
    }

    @AfterEach
    void tearDown() {
        if (bukkitMock != null) {
            bukkitMock.close();
        }
    }

    @Test
    void allSolidNeighbors_notExposed() {
        when(adapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt())).thenReturn(100);
        when(materialSet.isTransparent(100)).thenReturn(false);
        AirExposureChecker checker = new AirExposureChecker(adapter, materialSet, true);
        assertFalse(checker.isAirExposed(world, 0, 0, 0));
    }

    @Test
    void oneTransparentNeighbor_isExposed() {
        when(adapter.getBlockStateAt(world, 1, 0, 0)).thenReturn(0);
        when(adapter.getBlockStateAt(world, -1, 0, 0)).thenReturn(100);
        when(adapter.getBlockStateAt(world, 0, 1, 0)).thenReturn(100);
        when(adapter.getBlockStateAt(world, 0, -1, 0)).thenReturn(100);
        when(adapter.getBlockStateAt(world, 0, 0, 1)).thenReturn(100);
        when(adapter.getBlockStateAt(world, 0, 0, -1)).thenReturn(100);
        when(materialSet.isTransparent(0)).thenReturn(true);
        when(materialSet.isTransparent(100)).thenReturn(false);
        AirExposureChecker checker = new AirExposureChecker(adapter, materialSet, true);
        assertTrue(checker.isAirExposed(world, 0, 0, 0));
    }

    @Test
    void aboveNeighborTransparent_isExposed() {
        when(adapter.getBlockStateAt(world, 1, 0, 0)).thenReturn(100);
        when(adapter.getBlockStateAt(world, -1, 0, 0)).thenReturn(100);
        when(adapter.getBlockStateAt(world, 0, 1, 0)).thenReturn(0);
        when(adapter.getBlockStateAt(world, 0, -1, 0)).thenReturn(100);
        when(adapter.getBlockStateAt(world, 0, 0, 1)).thenReturn(100);
        when(adapter.getBlockStateAt(world, 0, 0, -1)).thenReturn(100);
        when(materialSet.isTransparent(0)).thenReturn(true);
        when(materialSet.isTransparent(100)).thenReturn(false);
        AirExposureChecker checker = new AirExposureChecker(adapter, materialSet, true);
        assertTrue(checker.isAirExposed(world, 0, 0, 0));
    }

    @Test
    void belowNeighborTransparent_isExposed() {
        when(adapter.getBlockStateAt(world, 1, 0, 0)).thenReturn(100);
        when(adapter.getBlockStateAt(world, -1, 0, 0)).thenReturn(100);
        when(adapter.getBlockStateAt(world, 0, 1, 0)).thenReturn(100);
        when(adapter.getBlockStateAt(world, 0, -1, 0)).thenReturn(0);
        when(adapter.getBlockStateAt(world, 0, 0, 1)).thenReturn(100);
        when(adapter.getBlockStateAt(world, 0, 0, -1)).thenReturn(100);
        when(materialSet.isTransparent(0)).thenReturn(true);
        when(materialSet.isTransparent(100)).thenReturn(false);
        AirExposureChecker checker = new AirExposureChecker(adapter, materialSet, true);
        assertTrue(checker.isAirExposed(world, 0, 0, 0));
    }

    @Test
    void unloadedChunkNeighbor_notExposed() {
        when(adapter.getBlockStateAt(world, 1, 0, 0)).thenReturn(-1);
        when(adapter.getBlockStateAt(world, -1, 0, 0)).thenReturn(-1);
        when(adapter.getBlockStateAt(world, 0, 1, 0)).thenReturn(-1);
        when(adapter.getBlockStateAt(world, 0, -1, 0)).thenReturn(-1);
        when(adapter.getBlockStateAt(world, 0, 0, 1)).thenReturn(-1);
        when(adapter.getBlockStateAt(world, 0, 0, -1)).thenReturn(-1);
        AirExposureChecker checker = new AirExposureChecker(adapter, materialSet, true);
        assertFalse(checker.isAirExposed(world, 0, 0, 0));
    }

    @Test
    void lavaObscures_lavaNotTransparent() {
        int lavaId = 10;
        when(adapter.getBlockStateAt(world, 1, 0, 0)).thenReturn(lavaId);
        when(adapter.getBlockStateAt(world, -1, 0, 0)).thenReturn(100);
        when(adapter.getBlockStateAt(world, 0, 1, 0)).thenReturn(100);
        when(adapter.getBlockStateAt(world, 0, -1, 0)).thenReturn(100);
        when(adapter.getBlockStateAt(world, 0, 0, 1)).thenReturn(100);
        when(adapter.getBlockStateAt(world, 0, 0, -1)).thenReturn(100);
        when(materialSet.isTransparent(lavaId)).thenReturn(false);
        when(materialSet.isTransparent(100)).thenReturn(false);
        AirExposureChecker checker = new AirExposureChecker(adapter, materialSet, true);
        assertFalse(checker.isAirExposed(world, 0, 0, 0));
    }

    @Test
    void lavaNotObscures_lavaIsTransparent() {
        int lavaId = 10;
        when(adapter.getBlockStateAt(world, 1, 0, 0)).thenReturn(lavaId);
        when(adapter.getBlockStateAt(world, -1, 0, 0)).thenReturn(100);
        when(adapter.getBlockStateAt(world, 0, 1, 0)).thenReturn(100);
        when(adapter.getBlockStateAt(world, 0, -1, 0)).thenReturn(100);
        when(adapter.getBlockStateAt(world, 0, 0, 1)).thenReturn(100);
        when(adapter.getBlockStateAt(world, 0, 0, -1)).thenReturn(100);
        when(materialSet.isTransparent(lavaId)).thenReturn(true);
        when(materialSet.isTransparent(100)).thenReturn(false);
        AirExposureChecker checker = new AirExposureChecker(adapter, materialSet, false);
        assertTrue(checker.isAirExposed(world, 0, 0, 0));
    }

    @Test
    void onlySixFaceNeighborsChecked() {
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(adapter.getBlockStateAt(world, 1, 0, 0)).thenReturn(100);
        when(adapter.getBlockStateAt(world, -1, 0, 0)).thenReturn(100);
        when(adapter.getBlockStateAt(world, 0, 1, 0)).thenReturn(100);
        when(adapter.getBlockStateAt(world, 0, -1, 0)).thenReturn(100);
        when(adapter.getBlockStateAt(world, 0, 0, 1)).thenReturn(100);
        when(adapter.getBlockStateAt(world, 0, 0, -1)).thenReturn(100);
        when(materialSet.isTransparent(100)).thenReturn(false);
        AirExposureChecker checker = new AirExposureChecker(adapter, materialSet, true);
        assertFalse(checker.isAirExposed(world, 0, 0, 0));
        verify(adapter, times(6)).getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt());
    }

    @Test
    void diagonalNeighborNotChecked() {
        when(adapter.getBlockStateAt(world, 1, 0, 0)).thenReturn(100);
        when(adapter.getBlockStateAt(world, -1, 0, 0)).thenReturn(100);
        when(adapter.getBlockStateAt(world, 0, 1, 0)).thenReturn(100);
        when(adapter.getBlockStateAt(world, 0, -1, 0)).thenReturn(100);
        when(adapter.getBlockStateAt(world, 0, 0, 1)).thenReturn(100);
        when(adapter.getBlockStateAt(world, 0, 0, -1)).thenReturn(100);
        when(materialSet.isTransparent(100)).thenReturn(false);
        AirExposureChecker checker = new AirExposureChecker(adapter, materialSet, true);
        assertFalse(checker.isAirExposed(world, 0, 0, 0));
        verify(adapter, never()).getBlockStateAt(world, 1, 1, 1);
        verify(adapter, never()).getBlockStateAt(world, -1, -1, -1);
    }

    @Test
    void mixedUnloadedAndTransparent_exposed() {
        when(adapter.getBlockStateAt(world, 1, 0, 0)).thenReturn(-1);
        when(adapter.getBlockStateAt(world, -1, 0, 0)).thenReturn(100);
        when(adapter.getBlockStateAt(world, 0, 1, 0)).thenReturn(-1);
        when(adapter.getBlockStateAt(world, 0, -1, 0)).thenReturn(0);
        when(adapter.getBlockStateAt(world, 0, 0, 1)).thenReturn(100);
        when(adapter.getBlockStateAt(world, 0, 0, -1)).thenReturn(-1);
        when(materialSet.isTransparent(0)).thenReturn(true);
        when(materialSet.isTransparent(100)).thenReturn(false);
        AirExposureChecker checker = new AirExposureChecker(adapter, materialSet, true);
        assertTrue(checker.isAirExposed(world, 0, 0, 0));
    }

    @Test
    void isLavaObscuresReturnsConstructorValue() {
        AirExposureChecker with = new AirExposureChecker(adapter, materialSet, true);
        assertTrue(with.isLavaObscures());
        AirExposureChecker without = new AirExposureChecker(adapter, materialSet, false);
        assertFalse(without.isLavaObscures());
    }

    @Test
    void leavesTransparent_blocksExposedThroughLeaves() {
        int leavesId = 20;
        when(adapter.getBlockStateAt(world, 1, 0, 0)).thenReturn(leavesId);
        when(adapter.getBlockStateAt(world, -1, 0, 0)).thenReturn(100);
        when(adapter.getBlockStateAt(world, 0, 1, 0)).thenReturn(100);
        when(adapter.getBlockStateAt(world, 0, -1, 0)).thenReturn(100);
        when(adapter.getBlockStateAt(world, 0, 0, 1)).thenReturn(100);
        when(adapter.getBlockStateAt(world, 0, 0, -1)).thenReturn(100);
        when(materialSet.isTransparent(leavesId)).thenReturn(true);
        when(materialSet.isTransparent(100)).thenReturn(false);
        AirExposureChecker checker = new AirExposureChecker(adapter, materialSet, true);
        assertTrue(checker.isAirExposed(world, 0, 0, 0));
    }

    @Test
    void leavesNotTransparent_blocksNotExposedThroughLeaves() {
        int leavesId = 20;
        when(adapter.getBlockStateAt(world, 1, 0, 0)).thenReturn(leavesId);
        when(adapter.getBlockStateAt(world, -1, 0, 0)).thenReturn(100);
        when(adapter.getBlockStateAt(world, 0, 1, 0)).thenReturn(100);
        when(adapter.getBlockStateAt(world, 0, -1, 0)).thenReturn(100);
        when(adapter.getBlockStateAt(world, 0, 0, 1)).thenReturn(100);
        when(adapter.getBlockStateAt(world, 0, 0, -1)).thenReturn(100);
        when(materialSet.isTransparent(leavesId)).thenReturn(false);
        when(materialSet.isTransparent(100)).thenReturn(false);
        AirExposureChecker checker = new AirExposureChecker(adapter, materialSet, true);
        assertFalse(checker.isAirExposed(world, 0, 0, 0));
    }

    @Test
    void allNeighborsTransparent_exposed() {
        when(adapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt())).thenReturn(0);
        when(materialSet.isTransparent(0)).thenReturn(true);
        AirExposureChecker checker = new AirExposureChecker(adapter, materialSet, true);
        assertTrue(checker.isAirExposed(world, 0, 0, 0));
    }

    @Test
    void unloadedChunkSkipsIsTransparentCheck() {
        when(adapter.getBlockStateAt(world, 1, 0, 0)).thenReturn(-1);
        when(adapter.getBlockStateAt(world, -1, 0, 0)).thenReturn(-1);
        when(adapter.getBlockStateAt(world, 0, 1, 0)).thenReturn(-1);
        when(adapter.getBlockStateAt(world, 0, -1, 0)).thenReturn(-1);
        when(adapter.getBlockStateAt(world, 0, 0, 1)).thenReturn(-1);
        when(adapter.getBlockStateAt(world, 0, 0, -1)).thenReturn(-1);
        AirExposureChecker checker = new AirExposureChecker(adapter, materialSet, true);
        assertFalse(checker.isAirExposed(world, 0, 0, 0));
        verify(materialSet, never()).isTransparent(anyInt());
    }

    @Test
    void neighborDirectionArraysArePublic() {
        assertEquals(6, AirExposureChecker.NEIGHBOR_DX.length);
        assertEquals(6, AirExposureChecker.NEIGHBOR_DY.length);
        assertEquals(6, AirExposureChecker.NEIGHBOR_DZ.length);
        assertEquals(1, AirExposureChecker.NEIGHBOR_DX[0]);
        assertEquals(-1, AirExposureChecker.NEIGHBOR_DX[1]);
        assertEquals(1, AirExposureChecker.NEIGHBOR_DY[2]);
        assertEquals(-1, AirExposureChecker.NEIGHBOR_DY[3]);
        assertEquals(1, AirExposureChecker.NEIGHBOR_DZ[4]);
        assertEquals(-1, AirExposureChecker.NEIGHBOR_DZ[5]);
    }
}
