package com.antixray.deobfuscation;

import com.antixray.nms.NmsAdapter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UpdatePacketBuilderTest {

	private static final String WORLD_NAME = "world";

	private NmsAdapter nmsAdapter;
	private UpdatePacketBuilder builder;
	private World world;
	private MockedStatic<Bukkit> bukkitMock;

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
		lenient().when(world.getMinHeight()).thenReturn(-64);
		lenient().when(world.getMaxHeight()).thenReturn(320);

		nmsAdapter = mock(NmsAdapter.class);
		lenient().when(nmsAdapter.createBlockUpdatePacket(any(Location.class), anyInt()))
			.thenReturn(new Object());
		lenient().when(nmsAdapter.createMultiBlockUpdatePacket(any(World.class), anyInt(), anyInt(), any(Map.class)))
			.thenReturn(new Object());
		lenient().when(nmsAdapter.createChunkDataPacket(any(World.class), anyInt(), anyInt()))
			.thenReturn(new Object());

		builder = new UpdatePacketBuilder(nmsAdapter);
	}

	@AfterEach
	void tearDown() {
		if (bukkitMock != null) {
			bukkitMock.close();
		}
	}

	@Test
	void buildBlockUpdateDelegatesToNmsAdapter() {
		Location loc = new Location(world, 10, 64, 20);
		Object result = builder.buildBlockUpdate(loc, 5);
		assertNotNull(result);
		verify(nmsAdapter).createBlockUpdatePacket(loc, 5);
	}

	@Test
	void buildBlockUpdateNullLocationReturnsNull() {
		assertNull(builder.buildBlockUpdate(null, 5));
		verify(nmsAdapter, never()).createBlockUpdatePacket(any(), anyInt());
	}

	@Test
	void buildBlockUpdateNullWorldReturnsNull() {
		Location loc = new Location(null, 10, 64, 20);
		assertNull(builder.buildBlockUpdate(loc, 5));
		verify(nmsAdapter, never()).createBlockUpdatePacket(any(), anyInt());
	}

	@Test
	void buildMultiBlockUpdateDelegatesToNmsAdapter() {
		Map<Location, Integer> changes = new HashMap<>();
		changes.put(new Location(world, 0, 64, 0), 10);
		changes.put(new Location(world, 1, 64, 0), 11);

		Object result = builder.buildMultiBlockUpdate(world, 0, 0, changes);
		assertNotNull(result);
		verify(nmsAdapter).createMultiBlockUpdatePacket(world, 0, 0, changes);
	}

	@Test
	void buildMultiBlockUpdateNullWorldReturnsNull() {
		Map<Location, Integer> changes = new HashMap<>();
		changes.put(new Location(world, 0, 64, 0), 10);

		assertNull(builder.buildMultiBlockUpdate(null, 0, 0, changes));
		verify(nmsAdapter, never()).createMultiBlockUpdatePacket(any(), anyInt(), anyInt(), any());
	}

	@Test
	void buildMultiBlockUpdateEmptyChangesReturnsNull() {
		Map<Location, Integer> changes = new HashMap<>();
		assertNull(builder.buildMultiBlockUpdate(world, 0, 0, changes));
		verify(nmsAdapter, never()).createMultiBlockUpdatePacket(any(), anyInt(), anyInt(), any());
	}

	@Test
	void buildMultiBlockUpdateNullChangesReturnsNull() {
		assertNull(builder.buildMultiBlockUpdate(world, 0, 0, null));
		verify(nmsAdapter, never()).createMultiBlockUpdatePacket(any(), anyInt(), anyInt(), any());
	}

	@Test
	void buildChunkDataPacketDelegatesToNmsAdapter() {
		Object result = builder.buildChunkDataPacket(world, 5, 10);
		assertNotNull(result);
		verify(nmsAdapter).createChunkDataPacket(world, 5, 10);
	}

	@Test
	void buildChunkDataPacketNullWorldReturnsNull() {
		assertNull(builder.buildChunkDataPacket(null, 5, 10));
		verify(nmsAdapter, never()).createChunkDataPacket(any(), anyInt(), anyInt());
	}

	@Test
	void buildBlockUpdateHandlesException() {
		Location loc = new Location(world, 10, 64, 20);
		when(nmsAdapter.createBlockUpdatePacket(any(), anyInt()))
			.thenThrow(new RuntimeException("test error"));

		assertNull(builder.buildBlockUpdate(loc, 5));
	}

	@Test
	void buildMultiBlockUpdateHandlesException() {
		Map<Location, Integer> changes = Map.of(new Location(world, 0, 64, 0), 10);
		when(nmsAdapter.createMultiBlockUpdatePacket(any(), anyInt(), anyInt(), any()))
			.thenThrow(new RuntimeException("test error"));

		assertNull(builder.buildMultiBlockUpdate(world, 0, 0, changes));
	}

    @Test
    void buildChunkDataPacketHandlesException() {
        when(nmsAdapter.createChunkDataPacket(any(), anyInt(), anyInt()))
            .thenThrow(new RuntimeException("test error"));

        assertNull(builder.buildChunkDataPacket(world, 0, 0));
    }

    @Test
    void buildBlockUpdatePassesCorrectBlockStateId() {
        Location loc = new Location(world, 10, 64, 20);
        Object expectedPacket = new Object();
        when(nmsAdapter.createBlockUpdatePacket(loc, 42)).thenReturn(expectedPacket);

        Object result = builder.buildBlockUpdate(loc, 42);

        assertSame(expectedPacket, result);
        verify(nmsAdapter).createBlockUpdatePacket(loc, 42);
    }

    @Test
    void buildBlockUpdateDifferentStateIdsProduceDifferentCalls() {
        Location loc = new Location(world, 10, 64, 20);
        Object packet1 = new Object();
        Object packet2 = new Object();
        when(nmsAdapter.createBlockUpdatePacket(loc, 1)).thenReturn(packet1);
        when(nmsAdapter.createBlockUpdatePacket(loc, 2)).thenReturn(packet2);

        Object result1 = builder.buildBlockUpdate(loc, 1);
        Object result2 = builder.buildBlockUpdate(loc, 2);

        assertSame(packet1, result1);
        assertSame(packet2, result2);
        verify(nmsAdapter, times(1)).createBlockUpdatePacket(loc, 1);
        verify(nmsAdapter, times(1)).createBlockUpdatePacket(loc, 2);
    }

    @Test
    void buildMultiBlockUpdatePassesCorrectChunkCoordinates() {
        Map<Location, Integer> changes = new HashMap<>();
        changes.put(new Location(world, 0, 64, 0), 10);

        Object expectedPacket = new Object();
        when(nmsAdapter.createMultiBlockUpdatePacket(world, 3, -7, changes))
            .thenReturn(expectedPacket);

        Object result = builder.buildMultiBlockUpdate(world, 3, -7, changes);

        assertSame(expectedPacket, result);
        verify(nmsAdapter).createMultiBlockUpdatePacket(world, 3, -7, changes);
    }

    @Test
    void buildMultiBlockUpdateMultipleEntriesAllIncluded() {
        Map<Location, Integer> changes = new HashMap<>();
        changes.put(new Location(world, 0, 64, 0), 10);
        changes.put(new Location(world, 1, 64, 0), 11);
        changes.put(new Location(world, 2, 64, 0), 12);

        Object expectedPacket = new Object();
        when(nmsAdapter.createMultiBlockUpdatePacket(world, 0, 0, changes))
            .thenReturn(expectedPacket);

        Object result = builder.buildMultiBlockUpdate(world, 0, 0, changes);

        assertSame(expectedPacket, result);
        verify(nmsAdapter).createMultiBlockUpdatePacket(world, 0, 0, changes);
    }

    @Test
    void buildChunkDataPacketPassesCorrectChunkCoordinates() {
        Object expectedPacket = new Object();
        when(nmsAdapter.createChunkDataPacket(world, 5, -3))
            .thenReturn(expectedPacket);

        Object result = builder.buildChunkDataPacket(world, 5, -3);

        assertSame(expectedPacket, result);
        verify(nmsAdapter).createChunkDataPacket(world, 5, -3);
    }

    @Test
    void buildChunkDataPacketNegativeChunkCoordinates() {
        Object expectedPacket = new Object();
        when(nmsAdapter.createChunkDataPacket(world, -100, -200))
            .thenReturn(expectedPacket);

        Object result = builder.buildChunkDataPacket(world, -100, -200);

        assertSame(expectedPacket, result);
        verify(nmsAdapter).createChunkDataPacket(world, -100, -200);
    }

    @Test
    void buildBlockUpdateZeroBlockStateId() {
        Location loc = new Location(world, 0, 0, 0);
        Object expectedPacket = new Object();
        when(nmsAdapter.createBlockUpdatePacket(loc, 0)).thenReturn(expectedPacket);

        Object result = builder.buildBlockUpdate(loc, 0);

        assertSame(expectedPacket, result);
    }

    @Test
    void buildMultiBlockUpdateSingleEntryMap() {
        Map<Location, Integer> changes = Map.of(new Location(world, 5, 10, 15), 99);

        Object expectedPacket = new Object();
        when(nmsAdapter.createMultiBlockUpdatePacket(world, 0, 0, changes))
            .thenReturn(expectedPacket);

        Object result = builder.buildMultiBlockUpdate(world, 0, 0, changes);

        assertSame(expectedPacket, result);
    }

    @Test
    void buildBlockUpdateReturnsAdapterResultDirectly() {
        Location loc = new Location(world, 10, 64, 20);
        Object customPacket = new Object();
        when(nmsAdapter.createBlockUpdatePacket(loc, 5)).thenReturn(customPacket);

        Object result = builder.buildBlockUpdate(loc, 5);

        assertNotNull(result);
        assertSame(customPacket, result);
    }

    @Test
    void buildMultiBlockUpdateReturnsAdapterResultDirectly() {
        Map<Location, Integer> changes = Map.of(new Location(world, 0, 64, 0), 10);
        Object customPacket = new Object();
        when(nmsAdapter.createMultiBlockUpdatePacket(world, 0, 0, changes))
            .thenReturn(customPacket);

        Object result = builder.buildMultiBlockUpdate(world, 0, 0, changes);

        assertNotNull(result);
        assertSame(customPacket, result);
    }

    @Test
    void buildChunkDataPacketReturnsAdapterResultDirectly() {
        Object customPacket = new Object();
        when(nmsAdapter.createChunkDataPacket(world, 5, 10))
            .thenReturn(customPacket);

        Object result = builder.buildChunkDataPacket(world, 5, 10);

        assertNotNull(result);
        assertSame(customPacket, result);
    }
}
