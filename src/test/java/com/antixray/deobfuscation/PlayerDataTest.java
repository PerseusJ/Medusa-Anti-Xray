package com.antixray.deobfuscation;

import com.antixray.util.BlockPosition;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlayerDataTest {

    private static final int MAX_REVEALED = 10000;
    private static final String WORLD_NAME = "world";

    private PlayerData playerData;
    private MockedStatic<Bukkit> bukkitMock;
    private World world;

    @BeforeEach
    void setUp() {
        Server server = mock(Server.class);
        lenient().when(server.getName()).thenReturn("TestServer");
        lenient().when(server.getVersion()).thenReturn("1.21");
        lenient().when(server.getBukkitVersion()).thenReturn("1.21-R0.1-SNAPSHOT");
        bukkitMock = Mockito.mockStatic(Bukkit.class);
        bukkitMock.when(Bukkit::getServer).thenReturn(server);

        world = mock(World.class);
        lenient().when(world.getName()).thenReturn(WORLD_NAME);

        playerData = new PlayerData(MAX_REVEALED);
    }

    @AfterEach
    void tearDown() {
        if (bukkitMock != null) {
            bukkitMock.close();
        }
    }

    @Test
    void constructorInitializesDefaults() {
        assertNotNull(playerData.getRevealedBlocks());
        assertNull(playerData.getLastCheckedPosition());
        assertEquals(0L, playerData.getLastCheckTick());
    }

    @Test
    void constructorPassesMaxRevealedPerPlayer() {
        PlayerData custom = new PlayerData(500);
        assertEquals(500, custom.getRevealedBlocks().getMaxSize());
    }

    @Test
    void setAndGetLastCheckedPosition() {
        Location loc = new Location(world, 10.0, 64.0, -32.0);
        playerData.setLastCheckedPosition(loc);
        assertEquals(loc, playerData.getLastCheckedPosition());
        assertEquals(10.0, playerData.getLastCheckedPosition().getX(), 0.001);
        assertEquals(64.0, playerData.getLastCheckedPosition().getY(), 0.001);
        assertEquals(-32.0, playerData.getLastCheckedPosition().getZ(), 0.001);
    }

    @Test
    void setLastCheckedPositionOverwritesPrevious() {
        Location loc1 = new Location(world, 1.0, 2.0, 3.0);
        Location loc2 = new Location(world, 100.0, 200.0, 300.0);

        playerData.setLastCheckedPosition(loc1);
        playerData.setLastCheckedPosition(loc2);
        assertEquals(loc2, playerData.getLastCheckedPosition());
    }

    @Test
    void setAndGetLastCheckTick() {
        playerData.setLastCheckTick(12345L);
        assertEquals(12345L, playerData.getLastCheckTick());
    }

    @Test
    void setLastCheckTickOverwritesPrevious() {
        playerData.setLastCheckTick(100L);
        playerData.setLastCheckTick(200L);
        assertEquals(200L, playerData.getLastCheckTick());
    }

    @Test
    void lastCheckTickHandlesZero() {
        playerData.setLastCheckTick(0L);
        assertEquals(0L, playerData.getLastCheckTick());
    }

    @Test
    void lastCheckTickHandlesMaxLong() {
        playerData.setLastCheckTick(Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, playerData.getLastCheckTick());
    }

    @Test
    void revealedBlocksAddAndContains() {
        BlockPosition pos = new BlockPosition(WORLD_NAME, 10, 64, -32);
        playerData.getRevealedBlocks().add(pos, 100L);
        assertTrue(playerData.getRevealedBlocks().contains(pos));
    }

    @Test
    void revealedBlocksRemove() {
        BlockPosition pos = new BlockPosition(WORLD_NAME, 10, 64, -32);
        playerData.getRevealedBlocks().add(pos, 100L);
        playerData.getRevealedBlocks().remove(pos);
        assertFalse(playerData.getRevealedBlocks().contains(pos));
        assertEquals(0, playerData.getRevealedBlocks().size());
    }

    @Test
    void clearResetsRevealedBlocks() {
        BlockPosition pos1 = new BlockPosition(WORLD_NAME, 1, 2, 3);
        BlockPosition pos2 = new BlockPosition(WORLD_NAME, 4, 5, 6);
        playerData.getRevealedBlocks().add(pos1, 10L);
        playerData.getRevealedBlocks().add(pos2, 20L);
        assertEquals(2, playerData.getRevealedBlocks().size());

        playerData.clear();
        assertEquals(0, playerData.getRevealedBlocks().size());
        assertFalse(playerData.getRevealedBlocks().contains(pos1));
        assertFalse(playerData.getRevealedBlocks().contains(pos2));
    }

    @Test
    void clearResetsLastCheckedPosition() {
        Location loc = new Location(world, 50.0, 64.0, 50.0);
        playerData.setLastCheckedPosition(loc);
        assertNotNull(playerData.getLastCheckedPosition());

        playerData.clear();
        assertNull(playerData.getLastCheckedPosition());
    }

    @Test
    void clearResetsLastCheckTick() {
        playerData.setLastCheckTick(9999L);
        playerData.clear();
        assertEquals(0L, playerData.getLastCheckTick());
    }

    @Test
    void clearOnFreshPlayerDataIsHarmless() {
        PlayerData fresh = new PlayerData(MAX_REVEALED);
        fresh.clear();
        assertEquals(0, fresh.getRevealedBlocks().size());
        assertNull(fresh.getLastCheckedPosition());
        assertEquals(0L, fresh.getLastCheckTick());
    }

    @Test
    void clearMultipleTimesIsIdempotent() {
        BlockPosition pos = new BlockPosition(WORLD_NAME, 1, 2, 3);
        playerData.getRevealedBlocks().add(pos, 10L);
        playerData.setLastCheckTick(100L);
        playerData.setLastCheckedPosition(new Location(world, 10, 20, 30));

        playerData.clear();
        playerData.clear();

        assertEquals(0, playerData.getRevealedBlocks().size());
        assertNull(playerData.getLastCheckedPosition());
        assertEquals(0L, playerData.getLastCheckTick());
    }

    @Test
    void revealedBlocksGetRevealedBeforeTick() {
        BlockPosition pos1 = new BlockPosition(WORLD_NAME, 1, 2, 3);
        BlockPosition pos2 = new BlockPosition(WORLD_NAME, 4, 5, 6);
        playerData.getRevealedBlocks().add(pos1, 10L);
        playerData.getRevealedBlocks().add(pos2, 50L);

        var result = playerData.getRevealedBlocks().getRevealedBeforeTick(30L, WORLD_NAME);
        assertEquals(1, result.size());
        assertEquals(1, playerData.getRevealedBlocks().size());
        assertTrue(playerData.getRevealedBlocks().contains(pos2));
    }

    @Test
    void revealedBlocksEvictionAtCapacity() {
        PlayerData smallData = new PlayerData(2);
        RevealedBlocksSet smallSet = smallData.getRevealedBlocks();

        smallSet.add(new BlockPosition(WORLD_NAME, 1, 0, 0), 10L);
        smallSet.add(new BlockPosition(WORLD_NAME, 2, 0, 0), 20L);
        smallSet.add(new BlockPosition(WORLD_NAME, 3, 0, 0), 30L);

        assertEquals(2, smallSet.size());
        assertFalse(smallSet.contains(new BlockPosition(WORLD_NAME, 1, 0, 0)));
        assertTrue(smallSet.contains(new BlockPosition(WORLD_NAME, 2, 0, 0)));
        assertTrue(smallSet.contains(new BlockPosition(WORLD_NAME, 3, 0, 0)));
    }

    @Test
    void independentPlayerDataInstances() {
        PlayerData data1 = new PlayerData(MAX_REVEALED);
        PlayerData data2 = new PlayerData(MAX_REVEALED);

        BlockPosition pos = new BlockPosition(WORLD_NAME, 10, 64, -32);
        data1.getRevealedBlocks().add(pos, 100L);
        data1.setLastCheckTick(100L);

        assertFalse(data2.getRevealedBlocks().contains(pos));
        assertEquals(0L, data2.getLastCheckTick());

        data1.clear();
        assertTrue(data2.getRevealedBlocks().size() == 0);
    }

    @Test
    void setLastCheckedPositionToNull() {
        Location loc = new Location(world, 10.0, 64.0, -32.0);
        playerData.setLastCheckedPosition(loc);
        assertNotNull(playerData.getLastCheckedPosition());

        playerData.setLastCheckedPosition(null);
        assertNull(playerData.getLastCheckedPosition());
    }
}
