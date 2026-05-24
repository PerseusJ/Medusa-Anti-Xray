package com.antixray.deobfuscation;

import com.antixray.AntiXrayPlugin;
import com.antixray.config.ConfigurationManager;
import com.antixray.config.WorldConfig;
import com.antixray.nms.NmsAdapter;
import com.antixray.util.BlockPosition;
import com.antixray.util.FoliaSchedulerAdapter;
import com.antixray.util.MaterialSet;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeobfuscationManagerTest {

	private static final String WORLD_NAME = "world";

	private AntiXrayPlugin plugin;
	private NmsAdapter nmsAdapter;
	private MaterialSet materialSet;
	private World world;
	private MockedStatic<Bukkit> bukkitMock;
	private BukkitScheduler scheduler;

	@BeforeEach
	void setUp() {
		Server server = mock(Server.class);
		lenient().when(server.getName()).thenReturn("TestServer");
		lenient().when(server.getVersion()).thenReturn("1.21");
		lenient().when(server.getBukkitVersion()).thenReturn("1.21-R0.1-SNAPSHOT");
		lenient().when(server.getPluginManager()).thenReturn(mock(org.bukkit.plugin.PluginManager.class));
		bukkitMock = Mockito.mockStatic(Bukkit.class);
		bukkitMock.when(Bukkit::getServer).thenReturn(server);

		scheduler = mock(BukkitScheduler.class);
		BukkitTask task = mock(BukkitTask.class);
		lenient().when(task.getTaskId()).thenReturn(1);
		lenient().when(scheduler.runTaskTimer(any(), any(Runnable.class), anyLong(), anyLong()))
			.thenReturn(task);
		lenient().when(scheduler.runTask(any(), any(Runnable.class))).thenReturn(task);
		doNothing().when(scheduler).cancelTask(anyInt());
		bukkitMock.when(Bukkit::getScheduler).thenReturn(scheduler);
		bukkitMock.when(Bukkit::getPluginManager).thenReturn(mock(org.bukkit.plugin.PluginManager.class));

		world = mock(World.class);
		lenient().when(world.getName()).thenReturn(WORLD_NAME);
		lenient().when(world.getMinHeight()).thenReturn(-64);
		lenient().when(world.getMaxHeight()).thenReturn(320);
		lenient().when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);

		nmsAdapter = mock(NmsAdapter.class);
		when(nmsAdapter.getBlockStateAt(any(World.class), anyInt(), anyInt(), anyInt()))
			.thenReturn(-1);
		lenient().when(nmsAdapter.createBlockUpdatePacket(any(Location.class), anyInt()))
			.thenReturn(new Object());
		lenient().when(nmsAdapter.createMultiBlockUpdatePacket(any(World.class), anyInt(), anyInt(), any(Map.class)))
			.thenReturn(new Object());
		lenient().when(nmsAdapter.createChunkDataPacket(any(World.class), anyInt(), anyInt()))
			.thenReturn(new Object());
        doNothing().when(nmsAdapter).sendPacket(any(Player.class), any());

        materialSet = Mockito.spy(new MaterialSet(1, 2, 3, 4));

        plugin = mock(AntiXrayPlugin.class);
        FoliaSchedulerAdapter schedulerAdapter = new FoliaSchedulerAdapter(plugin);
        when(plugin.getSchedulerAdapter()).thenReturn(schedulerAdapter);
		when(plugin.getAllPlayerData()).thenReturn(Collections.emptyMap());
		when(plugin.getLogger()).thenReturn(Logger.getLogger("AntiXray"));

		ConfigurationManager configManager = mock(ConfigurationManager.class);
		WorldConfig worldConfig = WorldConfig.builder()
			.deepslateBelowY(0)
			.maxRevealedPerPlayer(10000)
			.maxDeobfuscationUpdatesPerTick(64)
			.build();
		when(configManager.getWorldConfig(any(World.class))).thenReturn(worldConfig);
		when(plugin.getConfigurationManager()).thenReturn(configManager);
	}

	@AfterEach
	void tearDown() {
		if (bukkitMock != null) {
			bukkitMock.close();
		}
	}

	private DeobfuscationManager createManager() {
		return new DeobfuscationManager(plugin, nmsAdapter, materialSet);
	}

	private Player setupPlayer(UUID playerId, PlayerData data) {
		Map<UUID, PlayerData> playerDataMap = new HashMap<>();
		playerDataMap.put(playerId, data);
		when(plugin.getAllPlayerData()).thenReturn(Collections.unmodifiableMap(playerDataMap));
		when(plugin.getPlayerData(playerId)).thenReturn(data);

		Player player = mock(Player.class);
		when(player.isOnline()).thenReturn(true);
		when(player.getWorld()).thenReturn(world);
		when(player.getUniqueId()).thenReturn(playerId);
		bukkitMock.when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);

		return player;
	}

	@Test
	void queueDeobfuscationAddsToPendingBatch() {
		UUID playerId = UUID.randomUUID();
		PlayerData data = new PlayerData(10000);
		Player player = setupPlayer(playerId, data);

		when(nmsAdapter.getBlockStateAt(eq(world), eq(10), eq(64), eq(20)))
			.thenReturn(10);
		doReturn(true).when(materialSet).isHidden(10);

		DeobfuscationManager manager = createManager();
		List<BlockPosition> positions = List.of(new BlockPosition(WORLD_NAME, 10, 64, 20));
		manager.queueDeobfuscation(player, positions);

		assertEquals(1, manager.getPendingCount(playerId));
		assertTrue(data.getRevealedBlocks().contains(new BlockPosition(WORLD_NAME, 10, 64, 20)));
	}

	@Test
	void queueDeobfuscationSkipsAlreadyRevealed() {
		UUID playerId = UUID.randomUUID();
		PlayerData data = new PlayerData(10000);
		BlockPosition pos = new BlockPosition(WORLD_NAME, 10, 64, 20);
		data.getRevealedBlocks().add(pos, 100L);
		Player player = setupPlayer(playerId, data);

		when(nmsAdapter.getBlockStateAt(eq(world), eq(10), eq(64), eq(20)))
			.thenReturn(10);
		doReturn(true).when(materialSet).isHidden(10);

		DeobfuscationManager manager = createManager();
		manager.queueDeobfuscation(player, List.of(pos));

		assertEquals(0, manager.getPendingCount(playerId));
	}

	@Test
	void queueDeobfuscationSkipsNonHiddenBlocks() {
		UUID playerId = UUID.randomUUID();
		PlayerData data = new PlayerData(10000);
		Player player = setupPlayer(playerId, data);

		when(nmsAdapter.getBlockStateAt(eq(world), eq(10), eq(64), eq(20)))
			.thenReturn(5);
		doReturn(false).when(materialSet).isHidden(5);

		DeobfuscationManager manager = createManager();
		manager.queueDeobfuscation(player, List.of(new BlockPosition(WORLD_NAME, 10, 64, 20)));

		assertEquals(0, manager.getPendingCount(playerId));
	}

	@Test
	void queueDeobfuscationSkipsInvalidBlockState() {
		UUID playerId = UUID.randomUUID();
		PlayerData data = new PlayerData(10000);
		Player player = setupPlayer(playerId, data);

		when(nmsAdapter.getBlockStateAt(eq(world), eq(10), eq(64), eq(20)))
			.thenReturn(-1);

		DeobfuscationManager manager = createManager();
		manager.queueDeobfuscation(player, List.of(new BlockPosition(WORLD_NAME, 10, 64, 20)));

		assertEquals(0, manager.getPendingCount(playerId));
	}

	@Test
	void queueDeobfuscationSkipsWrongWorld() {
		UUID playerId = UUID.randomUUID();
		PlayerData data = new PlayerData(10000);
		Player player = setupPlayer(playerId, data);

		DeobfuscationManager manager = createManager();
		manager.queueDeobfuscation(player, List.of(new BlockPosition("other_world", 10, 64, 20)));

		assertEquals(0, manager.getPendingCount(playerId));
	}

	@Test
	void queueDeobfuscationEmptyListIsNoOp() {
		UUID playerId = UUID.randomUUID();
		PlayerData data = new PlayerData(10000);
		Player player = setupPlayer(playerId, data);

		DeobfuscationManager manager = createManager();
		manager.queueDeobfuscation(player, Collections.emptyList());

		assertEquals(0, manager.getPendingCount(playerId));
	}

	@Test
	void queueDeobfuscationNullPlayerDataIsNoOp() {
		UUID playerId = UUID.randomUUID();
		when(plugin.getPlayerData(playerId)).thenReturn(null);
		when(plugin.getAllPlayerData()).thenReturn(Collections.emptyMap());

		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(playerId);
		bukkitMock.when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);

		DeobfuscationManager manager = createManager();
		manager.queueDeobfuscation(player, List.of(new BlockPosition(WORLD_NAME, 10, 64, 20)));

		assertEquals(0, manager.getPendingCount(playerId));
	}

	@Test
	void sectionKeyRoundTrip() {
		long key = DeobfuscationManager.sectionKey(3, -4, 7);
		assertEquals(3, DeobfuscationManager.extractChunkXFromKey(key));
		assertEquals(-4, DeobfuscationManager.extractSectionYFromKey(key));
		assertEquals(7, DeobfuscationManager.extractChunkZFromKey(key));
	}

	@Test
	void sectionKeyNegativeCoordinates() {
		long key = DeobfuscationManager.sectionKey(-10, 5, -20);
		assertEquals(-10, DeobfuscationManager.extractChunkXFromKey(key));
		assertEquals(5, DeobfuscationManager.extractSectionYFromKey(key));
		assertEquals(-20, DeobfuscationManager.extractChunkZFromKey(key));
	}

    @Test
    void sectionKeyLargeCoordinates() {
        long key = DeobfuscationManager.sectionKey(100000, 20, -100000);
        assertEquals(100000, DeobfuscationManager.extractChunkXFromKey(key));
        assertEquals(20, DeobfuscationManager.extractSectionYFromKey(key));
        assertEquals(-100000, DeobfuscationManager.extractChunkZFromKey(key));
    }

    @Test
    void sectionKeyOverflowTruncates() {
        // chunkX uses 22-bit signed encoding; values beyond ±2,097,151 are truncated
        int overflowChunkX = 3000000;
        long key = DeobfuscationManager.sectionKey(overflowChunkX, 0, 0);
        int extracted = DeobfuscationManager.extractChunkXFromKey(key);
        assertNotEquals(overflowChunkX, extracted, "Overflow values should be truncated by 22-bit encoding");
    }

	@Test
	void sendBatchedUpdatesIndividualPacketsForSmallSection() {
		UUID playerId = UUID.randomUUID();
		Player player = mock(Player.class);
		when(player.isOnline()).thenReturn(true);
		when(player.getWorld()).thenReturn(world);

		when(nmsAdapter.getBlockStateAt(eq(world), eq(0), eq(64), eq(0)))
			.thenReturn(10);
		when(nmsAdapter.getBlockStateAt(eq(world), eq(1), eq(64), eq(0)))
			.thenReturn(11);
		doReturn(true).when(materialSet).isHidden(10);
		doReturn(true).when(materialSet).isHidden(11);

		DeobfuscationManager manager = createManager();
		List<BlockPosition> positions = List.of(
			new BlockPosition(WORLD_NAME, 0, 64, 0),
			new BlockPosition(WORLD_NAME, 1, 64, 0)
		);

		manager.sendBatchedUpdates(player, world, WORLD_NAME, positions);

		verify(nmsAdapter, times(2)).createBlockUpdatePacket(any(Location.class), anyInt());
		verify(nmsAdapter, never()).createMultiBlockUpdatePacket(any(), anyInt(), anyInt(), any());
		verify(nmsAdapter, never()).createChunkDataPacket(any(), anyInt(), anyInt());
	}

	@Test
	void sendBatchedUpdatesMultiBlockForMediumSection() {
		UUID playerId = UUID.randomUUID();
		Player player = mock(Player.class);
		when(player.isOnline()).thenReturn(true);
		when(player.getWorld()).thenReturn(world);

		for (int i = 0; i < 5; i++) {
			when(nmsAdapter.getBlockStateAt(eq(world), eq(i), eq(64), eq(0)))
				.thenReturn(10 + i);
			doReturn(true).when(materialSet).isHidden(10 + i);
		}

		DeobfuscationManager manager = createManager();
		List<BlockPosition> positions = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			positions.add(new BlockPosition(WORLD_NAME, i, 64, 0));
		}

		manager.sendBatchedUpdates(player, world, WORLD_NAME, positions);

		verify(nmsAdapter).createMultiBlockUpdatePacket(eq(world), anyInt(), anyInt(), any(Map.class));
		verify(nmsAdapter, never()).createChunkDataPacket(any(), anyInt(), anyInt());
	}

	@Test
	void sendBatchedUpdatesChunkDataForLargeSection() {
		UUID playerId = UUID.randomUUID();
		Player player = mock(Player.class);
		when(player.isOnline()).thenReturn(true);
		when(player.getWorld()).thenReturn(world);

		for (int i = 0; i < 70; i++) {
			when(nmsAdapter.getBlockStateAt(eq(world), eq(i & 15), eq(64 + ((i >> 4) & 15)), eq(0)))
				.thenReturn(10);
		}
		doReturn(true).when(materialSet).isHidden(10);

		DeobfuscationManager manager = createManager();
		List<BlockPosition> positions = new ArrayList<>();
		for (int i = 0; i < 70; i++) {
			positions.add(new BlockPosition(WORLD_NAME, i & 15, 64 + ((i >> 4) & 15), 0));
		}

		manager.sendBatchedUpdates(player, world, WORLD_NAME, positions);

		verify(nmsAdapter).createChunkDataPacket(eq(world), anyInt(), anyInt());
	}

	@Test
	void getPacketBuilderReturnsNonNull() {
		DeobfuscationManager manager = createManager();
		assertNotNull(manager.getPacketBuilder());
	}

	@Test
	void deobfuscateAroundQueuesPositions() {
		UUID playerId = UUID.randomUUID();
		PlayerData data = new PlayerData(10000);
		Player player = setupPlayer(playerId, data);

		when(nmsAdapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt()))
			.thenReturn(10);
		doReturn(true).when(materialSet).isHidden(10);

		DeobfuscationManager manager = createManager();
		Location center = new Location(world, 0, 64, 0);
		manager.deobfuscateAround(player, center);

		assertTrue(manager.getPendingCount(playerId) > 0);
	}

	@Test
	void rateLimitDefersExcess() {
		ConfigurationManager configManager = mock(ConfigurationManager.class);
		WorldConfig limitedConfig = WorldConfig.builder()
			.deepslateBelowY(0)
			.maxRevealedPerPlayer(10000)
			.maxDeobfuscationUpdatesPerTick(3)
			.build();
		when(configManager.getWorldConfig(any(World.class))).thenReturn(limitedConfig);
		when(plugin.getConfigurationManager()).thenReturn(configManager);

		UUID playerId = UUID.randomUUID();
		PlayerData data = new PlayerData(10000);
		Player player = setupPlayer(playerId, data);

		when(nmsAdapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt()))
			.thenReturn(10);
		doReturn(true).when(materialSet).isHidden(10);

		DeobfuscationManager manager = new DeobfuscationManager(plugin, nmsAdapter, materialSet);

		List<BlockPosition> positions = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			positions.add(new BlockPosition(WORLD_NAME, i, 64, 0));
		}
		manager.queueDeobfuscation(player, positions);

		assertTrue(manager.getPendingCount(playerId) > 0);
		assertTrue(manager.getDeferredCount(playerId) > 0);
	}

    @Test
    void startFlushTaskRegistersRecurringTask() {
        DeobfuscationManager manager = createManager();
        manager.startFlushTask();

        verify(scheduler).runTaskTimer(eq(plugin), any(Runnable.class), eq(1L), eq(1L));
    }

    @Test
    void stopFlushTaskCancelsTask() {
        DeobfuscationManager manager = createManager();
        manager.startFlushTask();
        manager.stopFlushTask();

        verify(scheduler).cancelTask(anyInt());
    }

	@Test
	void sendBatchedUpdatesExactlyThreeSendsIndividualPackets() {
		Player player = mock(Player.class);
		when(player.isOnline()).thenReturn(true);
		when(player.getWorld()).thenReturn(world);

		when(nmsAdapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt()))
			.thenReturn(10);
		doReturn(true).when(materialSet).isHidden(10);

		DeobfuscationManager manager = createManager();
		List<BlockPosition> positions = List.of(
			new BlockPosition(WORLD_NAME, 0, 64, 0),
			new BlockPosition(WORLD_NAME, 1, 64, 0),
			new BlockPosition(WORLD_NAME, 2, 64, 0)
		);

		manager.sendBatchedUpdates(player, world, WORLD_NAME, positions);

		verify(nmsAdapter, times(3)).createBlockUpdatePacket(any(Location.class), anyInt());
		verify(nmsAdapter, never()).createMultiBlockUpdatePacket(any(), anyInt(), anyInt(), any());
		verify(nmsAdapter, never()).createChunkDataPacket(any(), anyInt(), anyInt());
	}

	@Test
	void sendBatchedUpdatesExactlyFourSendsMultiBlockUpdate() {
		Player player = mock(Player.class);
		when(player.isOnline()).thenReturn(true);
		when(player.getWorld()).thenReturn(world);

		when(nmsAdapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt()))
			.thenReturn(10);
		doReturn(true).when(materialSet).isHidden(10);

		DeobfuscationManager manager = createManager();
		List<BlockPosition> positions = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			positions.add(new BlockPosition(WORLD_NAME, i, 64, 0));
		}

		manager.sendBatchedUpdates(player, world, WORLD_NAME, positions);

		verify(nmsAdapter).createMultiBlockUpdatePacket(eq(world), anyInt(), anyInt(), any(Map.class));
		verify(nmsAdapter, never()).createChunkDataPacket(any(), anyInt(), anyInt());
	}

	@Test
	void sendBatchedUpdatesExactlySixtyFourSendsMultiBlockUpdate() {
		Player player = mock(Player.class);
		when(player.isOnline()).thenReturn(true);
		when(player.getWorld()).thenReturn(world);

		when(nmsAdapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt()))
			.thenReturn(10);
		doReturn(true).when(materialSet).isHidden(10);

		DeobfuscationManager manager = createManager();
		List<BlockPosition> positions = new ArrayList<>();
		for (int i = 0; i < 64; i++) {
			positions.add(new BlockPosition(WORLD_NAME, i & 15, 64 + (i >> 4), 0));
		}

		manager.sendBatchedUpdates(player, world, WORLD_NAME, positions);

		verify(nmsAdapter).createMultiBlockUpdatePacket(eq(world), anyInt(), anyInt(), any(Map.class));
		verify(nmsAdapter, never()).createChunkDataPacket(any(), anyInt(), anyInt());
	}

	@Test
	void sendBatchedUpdatesExactlySixtyFiveSendsChunkDataPacket() {
		Player player = mock(Player.class);
		when(player.isOnline()).thenReturn(true);
		when(player.getWorld()).thenReturn(world);

		when(nmsAdapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt()))
			.thenReturn(10);
		doReturn(true).when(materialSet).isHidden(10);

		DeobfuscationManager manager = createManager();
		List<BlockPosition> positions = new ArrayList<>();
		for (int i = 0; i < 65; i++) {
			positions.add(new BlockPosition(WORLD_NAME, i & 15, 64 + (i >> 4), 0));
		}

		manager.sendBatchedUpdates(player, world, WORLD_NAME, positions);

		verify(nmsAdapter).createChunkDataPacket(eq(world), anyInt(), anyInt());
	}

	@Test
	void sendBatchedUpdatesSeparatesPositionsBySection() {
		Player player = mock(Player.class);
		when(player.isOnline()).thenReturn(true);
		when(player.getWorld()).thenReturn(world);

		when(nmsAdapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt()))
			.thenReturn(10);
		doReturn(true).when(materialSet).isHidden(10);

		DeobfuscationManager manager = createManager();
		List<BlockPosition> positions = List.of(
			new BlockPosition(WORLD_NAME, 0, 64, 0),
			new BlockPosition(WORLD_NAME, 1, 64, 0),
			new BlockPosition(WORLD_NAME, 2, 64, 0),
			new BlockPosition(WORLD_NAME, 3, 64, 0),
			new BlockPosition(WORLD_NAME, 16, 64, 0),
			new BlockPosition(WORLD_NAME, 17, 64, 0),
			new BlockPosition(WORLD_NAME, 18, 64, 0),
			new BlockPosition(WORLD_NAME, 19, 64, 0)
		);

		manager.sendBatchedUpdates(player, world, WORLD_NAME, positions);

		verify(nmsAdapter, times(2)).createMultiBlockUpdatePacket(eq(world), anyInt(), anyInt(), any(Map.class));
		verify(nmsAdapter, never()).createChunkDataPacket(any(), anyInt(), anyInt());
	}

	@Test
	void sendBatchedUpdatesFallsBackToIndividualWhenMultiBlockReturnsNull() {
		Player player = mock(Player.class);
		when(player.isOnline()).thenReturn(true);
		when(player.getWorld()).thenReturn(world);

		when(nmsAdapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt()))
			.thenReturn(10);
		doReturn(true).when(materialSet).isHidden(10);
		when(nmsAdapter.createMultiBlockUpdatePacket(any(), anyInt(), anyInt(), any()))
			.thenReturn(null);

		DeobfuscationManager manager = createManager();
		List<BlockPosition> positions = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			positions.add(new BlockPosition(WORLD_NAME, i, 64, 0));
		}

		manager.sendBatchedUpdates(player, world, WORLD_NAME, positions);

		verify(nmsAdapter, times(5)).createBlockUpdatePacket(any(Location.class), anyInt());
	}

	@Test
	void sendBatchedUpdatesFallsBackToMultiBlockWhenChunkDataReturnsNull() {
		Player player = mock(Player.class);
		when(player.isOnline()).thenReturn(true);
		when(player.getWorld()).thenReturn(world);

		when(nmsAdapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt()))
			.thenReturn(10);
		doReturn(true).when(materialSet).isHidden(10);
		when(nmsAdapter.createChunkDataPacket(any(), anyInt(), anyInt()))
			.thenReturn(null);

		DeobfuscationManager manager = createManager();
		List<BlockPosition> positions = new ArrayList<>();
		for (int i = 0; i < 65; i++) {
			positions.add(new BlockPosition(WORLD_NAME, i & 15, 64 + (i >> 4), 0));
		}

		manager.sendBatchedUpdates(player, world, WORLD_NAME, positions);

		verify(nmsAdapter).createMultiBlockUpdatePacket(eq(world), anyInt(), anyInt(), any(Map.class));
		verify(nmsAdapter, never()).createBlockUpdatePacket(any(), anyInt());
	}

	@Test
	void sendBatchedUpdatesFallsBackToIndividualThroughFullChain() {
		Player player = mock(Player.class);
		when(player.isOnline()).thenReturn(true);
		when(player.getWorld()).thenReturn(world);

		when(nmsAdapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt()))
			.thenReturn(10);
		doReturn(true).when(materialSet).isHidden(10);
		when(nmsAdapter.createChunkDataPacket(any(), anyInt(), anyInt()))
			.thenReturn(null);
		when(nmsAdapter.createMultiBlockUpdatePacket(any(), anyInt(), anyInt(), any()))
			.thenReturn(null);

		DeobfuscationManager manager = createManager();
		List<BlockPosition> positions = new ArrayList<>();
		for (int i = 0; i < 65; i++) {
			positions.add(new BlockPosition(WORLD_NAME, i & 15, 64 + (i >> 4), 0));
		}

		manager.sendBatchedUpdates(player, world, WORLD_NAME, positions);

		verify(nmsAdapter, times(65)).createBlockUpdatePacket(any(Location.class), anyInt());
	}

	@Test
	void sendBatchedUpdatesSkipsPositionsWithInvalidBlockState() {
		Player player = mock(Player.class);
		when(player.isOnline()).thenReturn(true);
		when(player.getWorld()).thenReturn(world);

		when(nmsAdapter.getBlockStateAt(eq(world), eq(0), eq(64), eq(0)))
			.thenReturn(-1);
		when(nmsAdapter.getBlockStateAt(eq(world), eq(1), eq(64), eq(0)))
			.thenReturn(10);
		doReturn(true).when(materialSet).isHidden(10);

		DeobfuscationManager manager = createManager();
		List<BlockPosition> positions = List.of(
			new BlockPosition(WORLD_NAME, 0, 64, 0),
			new BlockPosition(WORLD_NAME, 1, 64, 0)
		);

		manager.sendBatchedUpdates(player, world, WORLD_NAME, positions);

		verify(nmsAdapter, times(1)).createBlockUpdatePacket(any(Location.class), anyInt());
	}

	@Test
	void startFlushTaskIsIdempotent() {
		DeobfuscationManager manager = createManager();
		manager.startFlushTask();
		manager.startFlushTask();

		verify(scheduler, times(1)).runTaskTimer(eq(plugin), any(Runnable.class), eq(1L), eq(1L));
	}

	@Test
	void stopFlushTaskWithNoTaskDoesNotThrow() {
		DeobfuscationManager manager = createManager();
		manager.stopFlushTask();
	}

	@Test
	void deobfuscateAroundNullWorldDoesNotThrow() {
		Player player = mock(Player.class);
		Location center = new Location(null, 0, 64, 0);

		DeobfuscationManager manager = createManager();
		manager.deobfuscateAround(player, center);
	}

	@Test
	void queueDeobfuscationFiltersPositionsByWorldName() {
		UUID playerId = UUID.randomUUID();
		PlayerData data = new PlayerData(10000);
		Player player = setupPlayer(playerId, data);

		when(nmsAdapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt()))
			.thenReturn(10);
		doReturn(true).when(materialSet).isHidden(10);

		DeobfuscationManager manager = createManager();
		List<BlockPosition> positions = List.of(
			new BlockPosition(WORLD_NAME, 10, 64, 20),
			new BlockPosition("other_world", 10, 64, 20)
		);
		manager.queueDeobfuscation(player, positions);

		assertEquals(1, manager.getPendingCount(playerId));
	}

	@Test
	void getPacketBuilderReturnsExpectedBuilder() {
		DeobfuscationManager manager = createManager();
		assertSame(manager.getPacketBuilder(), manager.getPacketBuilder());
	}

	@Test
	void getPendingCountReturnsZeroForUnknownPlayer() {
		DeobfuscationManager manager = createManager();
		assertEquals(0, manager.getPendingCount(UUID.randomUUID()));
	}

    @Test
    void getDeferredCountReturnsZeroForUnknownPlayer() {
        DeobfuscationManager manager = createManager();
        assertEquals(0, manager.getDeferredCount(UUID.randomUUID()));
    }

    @Test
    void flushPendingBatchesClearsPendingMap() {
        UUID playerId = UUID.randomUUID();
        PlayerData data = new PlayerData(10000);
        Player player = setupPlayer(playerId, data);

        when(nmsAdapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt()))
            .thenReturn(10);
        doReturn(true).when(materialSet).isHidden(10);

        DeobfuscationManager manager = createManager();
        List<BlockPosition> positions = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            positions.add(new BlockPosition(WORLD_NAME, i, 64, 0));
        }
        manager.queueDeobfuscation(player, positions);
        assertEquals(5, manager.getPendingCount(playerId));

        manager.flushPendingBatches();
        assertEquals(0, manager.getPendingCount(playerId));
    }

    @Test
    void flushPendingBatchesHandlesMultiplePlayersIndependently() {
        UUID playerId1 = UUID.randomUUID();
        UUID playerId2 = UUID.randomUUID();
        PlayerData data1 = new PlayerData(10000);
        PlayerData data2 = new PlayerData(10000);
        Player player1 = setupPlayer(playerId1, data1);
        Player player2 = setupPlayer(playerId2, data2);

        when(nmsAdapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt()))
            .thenReturn(10);
        doReturn(true).when(materialSet).isHidden(10);

        DeobfuscationManager manager = createManager();
        manager.queueDeobfuscation(player1, List.of(new BlockPosition(WORLD_NAME, 0, 64, 0)));
        manager.queueDeobfuscation(player2, List.of(new BlockPosition(WORLD_NAME, 1, 64, 0)));

        manager.flushPendingBatches();

        verify(nmsAdapter, atLeastOnce()).sendPacket(eq(player1), any());
        verify(nmsAdapter, atLeastOnce()).sendPacket(eq(player2), any());
        assertEquals(0, manager.getPendingCount(playerId1));
        assertEquals(0, manager.getPendingCount(playerId2));
    }

    @Test
    void flushPendingBatchesSkipsOfflinePlayer() {
        UUID playerId = UUID.randomUUID();
        PlayerData data = new PlayerData(10000);
        Player player = setupPlayer(playerId, data);
        when(player.isOnline()).thenReturn(false);

        when(nmsAdapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt()))
            .thenReturn(10);
        doReturn(true).when(materialSet).isHidden(10);

        DeobfuscationManager manager = createManager();
        manager.queueDeobfuscation(player, List.of(new BlockPosition(WORLD_NAME, 0, 64, 0)));
        manager.flushPendingBatches();

        verify(nmsAdapter, never()).sendPacket(eq(player), any());
    }

    @Test
    void flushPendingBatchesSkipsNullPlayer() {
        UUID playerId = UUID.randomUUID();
        PlayerData data = new PlayerData(10000);
        setupPlayer(playerId, data);
        bukkitMock.when(() -> Bukkit.getPlayer(playerId)).thenReturn(null);

        when(nmsAdapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt()))
            .thenReturn(10);
        doReturn(true).when(materialSet).isHidden(10);

        DeobfuscationManager manager = createManager();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        manager.queueDeobfuscation(player, List.of(new BlockPosition(WORLD_NAME, 0, 64, 0)));
        manager.flushPendingBatches();

        verify(nmsAdapter, never()).sendPacket(any(Player.class), any());
    }

    @Test
    void flushPendingBatchesFiltersWrongWorldDuringFlush() {
        UUID playerId = UUID.randomUUID();
        PlayerData data = new PlayerData(10000);
        Player player = setupPlayer(playerId, data);

        when(nmsAdapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt()))
            .thenReturn(10);
        doReturn(true).when(materialSet).isHidden(10);

        DeobfuscationManager manager = createManager();
        List<BlockPosition> positions = List.of(
            new BlockPosition("other_world", 0, 64, 0),
            new BlockPosition(WORLD_NAME, 1, 64, 0)
        );
        manager.queueDeobfuscation(player, positions);

        assertEquals(1, manager.getPendingCount(playerId));
    }

    @Test
    void rateLimitExactlyAtMaxSendsAllNoDeferred() {
        ConfigurationManager configManager = mock(ConfigurationManager.class);
        WorldConfig config = WorldConfig.builder()
            .deepslateBelowY(0)
            .maxRevealedPerPlayer(10000)
            .maxDeobfuscationUpdatesPerTick(5)
            .build();
        when(configManager.getWorldConfig(any(World.class))).thenReturn(config);
        when(plugin.getConfigurationManager()).thenReturn(configManager);

        UUID playerId = UUID.randomUUID();
        PlayerData data = new PlayerData(10000);
        Player player = setupPlayer(playerId, data);

        when(nmsAdapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt()))
            .thenReturn(10);
        doReturn(true).when(materialSet).isHidden(10);

        DeobfuscationManager manager = new DeobfuscationManager(plugin, nmsAdapter, materialSet);
        List<BlockPosition> positions = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            positions.add(new BlockPosition(WORLD_NAME, i, 64, 0));
        }
        manager.queueDeobfuscation(player, positions);

        manager.flushPendingBatches();

        assertEquals(0, manager.getDeferredCount(playerId));
        verify(nmsAdapter, atLeastOnce()).sendPacket(eq(player), any());
    }

    @Test
    void rateLimitConfigOverrideApplies() {
        ConfigurationManager configManager = mock(ConfigurationManager.class);
        WorldConfig config = WorldConfig.builder()
            .deepslateBelowY(0)
            .maxRevealedPerPlayer(10000)
            .maxDeobfuscationUpdatesPerTick(2)
            .build();
        when(configManager.getWorldConfig(any(World.class))).thenReturn(config);
        when(plugin.getConfigurationManager()).thenReturn(configManager);

        UUID playerId = UUID.randomUUID();
        PlayerData data = new PlayerData(10000);
        Player player = setupPlayer(playerId, data);

        when(nmsAdapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt()))
            .thenReturn(10);
        doReturn(true).when(materialSet).isHidden(10);

        DeobfuscationManager manager = new DeobfuscationManager(plugin, nmsAdapter, materialSet);
        List<BlockPosition> positions = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            positions.add(new BlockPosition(WORLD_NAME, i, 64, 0));
        }
        manager.queueDeobfuscation(player, positions);

        assertTrue(manager.getDeferredCount(playerId) > 0);
    }

    @Test
    void deferredRequestsProcessedOnNextFlush() {
        ConfigurationManager configManager = mock(ConfigurationManager.class);
        WorldConfig config = WorldConfig.builder()
            .deepslateBelowY(0)
            .maxRevealedPerPlayer(10000)
            .maxDeobfuscationUpdatesPerTick(3)
            .build();
        when(configManager.getWorldConfig(any(World.class))).thenReturn(config);
        when(plugin.getConfigurationManager()).thenReturn(configManager);

        UUID playerId = UUID.randomUUID();
        PlayerData data = new PlayerData(10000);
        Player player = setupPlayer(playerId, data);

        when(nmsAdapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt()))
            .thenReturn(10);
        doReturn(true).when(materialSet).isHidden(10);

        DeobfuscationManager manager = new DeobfuscationManager(plugin, nmsAdapter, materialSet);
        List<BlockPosition> positions = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            positions.add(new BlockPosition(WORLD_NAME, i, 64, 0));
        }
        manager.queueDeobfuscation(player, positions);

        assertTrue(manager.getDeferredCount(playerId) > 0);

        // First flush: processes maxUpdates=3, defers the other 3
        manager.flushPendingBatches();

        // Second flush: processes remaining 3
        manager.flushPendingBatches();

        assertEquals(0, manager.getDeferredCount(playerId));
        assertEquals(0, manager.getPendingCount(playerId));
    }

    @Test
    void deferredPartialReDeferralWhenSlotsLimited() {
        ConfigurationManager configManager = mock(ConfigurationManager.class);
        WorldConfig config = WorldConfig.builder()
            .deepslateBelowY(0)
            .maxRevealedPerPlayer(10000)
            .maxDeobfuscationUpdatesPerTick(2)
            .build();
        when(configManager.getWorldConfig(any(World.class))).thenReturn(config);
        when(plugin.getConfigurationManager()).thenReturn(configManager);

        UUID playerId = UUID.randomUUID();
        PlayerData data = new PlayerData(10000);
        Player player = setupPlayer(playerId, data);

        when(nmsAdapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt()))
            .thenReturn(10);
        doReturn(true).when(materialSet).isHidden(10);

        DeobfuscationManager manager = new DeobfuscationManager(plugin, nmsAdapter, materialSet);

        List<BlockPosition> batch1 = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            batch1.add(new BlockPosition(WORLD_NAME, i, 64, 0));
        }
        manager.queueDeobfuscation(player, batch1);
        System.out.println("[DIAG-PARTIAL] After batch1 queue: pending=" + manager.getPendingCount(playerId) + " deferred=" + manager.getDeferredCount(playerId));

        assertTrue(manager.getDeferredCount(playerId) > 0);
        int firstDeferred = manager.getDeferredCount(playerId);

        List<BlockPosition> batch2 = new ArrayList<>();
        for (int i = 10; i < 14; i++) {
            batch2.add(new BlockPosition(WORLD_NAME, i, 64, 0));
        }
        manager.queueDeobfuscation(player, batch2);
        System.out.println("[DIAG-PARTIAL] After batch2 queue: pending=" + manager.getPendingCount(playerId) + " deferred=" + manager.getDeferredCount(playerId));

        manager.flushPendingBatches();
        System.out.println("[DIAG-PARTIAL] After flush: pending=" + manager.getPendingCount(playerId) + " deferred=" + manager.getDeferredCount(playerId));

        assertTrue(manager.getDeferredCount(playerId) > 0);
    }

    @Test
    void sectionKeyBoundaryZeroValues() {
        long key = DeobfuscationManager.sectionKey(0, 0, 0);
        assertEquals(0, DeobfuscationManager.extractChunkXFromKey(key));
        assertEquals(0, DeobfuscationManager.extractSectionYFromKey(key));
        assertEquals(0, DeobfuscationManager.extractChunkZFromKey(key));
    }

    @Test
    void sectionKeyMinSectionY() {
        long key = DeobfuscationManager.sectionKey(0, -4, 0);
        assertEquals(-4, DeobfuscationManager.extractSectionYFromKey(key));
    }

    @Test
    void mixedSectionSizesInOneFlushSendsCorrectPacketTypes() {
        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        when(player.getWorld()).thenReturn(world);

        when(nmsAdapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt()))
            .thenReturn(10);
        doReturn(true).when(materialSet).isHidden(10);

        DeobfuscationManager manager = createManager();

        List<BlockPosition> positions = new ArrayList<>();

        positions.add(new BlockPosition(WORLD_NAME, 0, 64, 0));
        positions.add(new BlockPosition(WORLD_NAME, 1, 64, 0));

        for (int i = 0; i < 5; i++) {
            positions.add(new BlockPosition(WORLD_NAME, 32 + i, 64, 0));
        }

        for (int i = 0; i < 70; i++) {
            positions.add(new BlockPosition(WORLD_NAME, i & 15, 128 + (i >> 4), 16));
        }

        manager.sendBatchedUpdates(player, world, WORLD_NAME, positions);

        verify(nmsAdapter, times(2)).createBlockUpdatePacket(any(Location.class), anyInt());
        verify(nmsAdapter, times(1)).createMultiBlockUpdatePacket(eq(world), anyInt(), anyInt(), any(Map.class));
        verify(nmsAdapter, times(1)).createChunkDataPacket(eq(world), anyInt(), anyInt());
    }

    @Test
    void sendBatchedUpdatesChunkDataFallbackOnException() {
        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        when(player.getWorld()).thenReturn(world);

        when(nmsAdapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt()))
            .thenReturn(10);
        doReturn(true).when(materialSet).isHidden(10);
        when(nmsAdapter.createChunkDataPacket(any(World.class), anyInt(), anyInt()))
            .thenThrow(new RuntimeException("chunk data error"));

        DeobfuscationManager manager = createManager();
        List<BlockPosition> positions = new ArrayList<>();
        for (int i = 0; i < 70; i++) {
            positions.add(new BlockPosition(WORLD_NAME, i & 15, 64 + (i >> 4), 0));
        }

        manager.sendBatchedUpdates(player, world, WORLD_NAME, positions);

        verify(nmsAdapter).createMultiBlockUpdatePacket(eq(world), anyInt(), anyInt(), any(Map.class));
    }

    @Test
    void sendBatchedUpdatesMultiBlockFallbackOnException() {
        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        when(player.getWorld()).thenReturn(world);

        when(nmsAdapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt()))
            .thenReturn(10);
        doReturn(true).when(materialSet).isHidden(10);
        when(nmsAdapter.createMultiBlockUpdatePacket(any(World.class), anyInt(), anyInt(), any(Map.class)))
            .thenThrow(new RuntimeException("multi-block error"));

        DeobfuscationManager manager = createManager();
        List<BlockPosition> positions = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            positions.add(new BlockPosition(WORLD_NAME, i, 64, 0));
        }

        manager.sendBatchedUpdates(player, world, WORLD_NAME, positions);

        verify(nmsAdapter, times(5)).createBlockUpdatePacket(any(Location.class), anyInt());
    }

    @Test
    void sendBatchedUpdatesIndividualExceptionContinuesRemaining() {
        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        when(player.getWorld()).thenReturn(world);

        when(nmsAdapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt()))
            .thenReturn(10);
        doReturn(true).when(materialSet).isHidden(10);

        AtomicInteger callCount = new AtomicInteger(0);
        when(nmsAdapter.createBlockUpdatePacket(any(Location.class), anyInt()))
            .thenAnswer(invocation -> {
                if (callCount.incrementAndGet() == 1) {
                    throw new RuntimeException("first block fails");
                }
                return new Object();
            });

        DeobfuscationManager manager = createManager();
        List<BlockPosition> positions = List.of(
            new BlockPosition(WORLD_NAME, 0, 64, 0),
            new BlockPosition(WORLD_NAME, 1, 64, 0)
        );

        manager.sendBatchedUpdates(player, world, WORLD_NAME, positions);

        verify(nmsAdapter, times(2)).createBlockUpdatePacket(any(Location.class), anyInt());
    }

    @Test
    void deobfuscateAroundRespectsUpdateRadius() {
        ConfigurationManager configManager = mock(ConfigurationManager.class);
        WorldConfig config = WorldConfig.builder()
            .deepslateBelowY(0)
            .maxRevealedPerPlayer(10000)
            .updateRadius(1)
            .maxDeobfuscationUpdatesPerTick(64)
            .build();
        when(configManager.getWorldConfig(any(World.class))).thenReturn(config);
        when(plugin.getConfigurationManager()).thenReturn(configManager);

        UUID playerId = UUID.randomUUID();
        PlayerData data = new PlayerData(10000);
        Player player = setupPlayer(playerId, data);

        when(nmsAdapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt()))
            .thenReturn(10);
        doReturn(true).when(materialSet).isHidden(10);

        DeobfuscationManager manager = new DeobfuscationManager(plugin, nmsAdapter, materialSet);
        Location center = new Location(world, 100, 64, 100);
        manager.deobfuscateAround(player, center);

        assertTrue(manager.getPendingCount(playerId) > 0);
        verify(nmsAdapter, atMost(27)).getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt());
    }

    @Test
    void deobfuscateAroundClampsToWorldHeightBounds() {
        ConfigurationManager configManager = mock(ConfigurationManager.class);
        WorldConfig config = WorldConfig.builder()
            .deepslateBelowY(0)
            .maxRevealedPerPlayer(10000)
            .updateRadius(4)
            .maxDeobfuscationUpdatesPerTick(64)
            .build();
        when(configManager.getWorldConfig(any(World.class))).thenReturn(config);
        when(plugin.getConfigurationManager()).thenReturn(configManager);

        UUID playerId = UUID.randomUUID();
        PlayerData data = new PlayerData(10000);
        Player player = setupPlayer(playerId, data);

        List<Integer> capturedY = new ArrayList<>();
        when(nmsAdapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt()))
            .thenAnswer(invocation -> {
                capturedY.add(invocation.getArgument(2));
                return 10;
            });
        doReturn(true).when(materialSet).isHidden(10);

        DeobfuscationManager manager = new DeobfuscationManager(plugin, nmsAdapter, materialSet);
        Location center = new Location(world, 0, -60, 0);
        manager.deobfuscateAround(player, center);

        for (int y : capturedY) {
            assertTrue(y >= -64 && y <= 319, "Y out of bounds: " + y);
        }
    }

    @Test
    void deobfuscateAroundNullPlayerDataIsNoOp() {
        UUID playerId = UUID.randomUUID();
        when(plugin.getPlayerData(playerId)).thenReturn(null);
        when(plugin.getAllPlayerData()).thenReturn(Collections.emptyMap());

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        bukkitMock.when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);

        DeobfuscationManager manager = createManager();
        Location center = new Location(world, 0, 64, 0);
        manager.deobfuscateAround(player, center);

        assertEquals(0, manager.getPendingCount(playerId));
    }

    @Test
    void queueDeobfuscationAccumulatesAcrossMultipleCalls() {
        UUID playerId = UUID.randomUUID();
        PlayerData data = new PlayerData(10000);
        Player player = setupPlayer(playerId, data);

        when(nmsAdapter.getBlockStateAt(eq(world), eq(10), eq(64), eq(20)))
            .thenReturn(10);
        when(nmsAdapter.getBlockStateAt(eq(world), eq(20), eq(64), eq(20)))
            .thenReturn(11);
        doReturn(true).when(materialSet).isHidden(10);
        doReturn(true).when(materialSet).isHidden(11);

        DeobfuscationManager manager = createManager();
        manager.queueDeobfuscation(player, List.of(new BlockPosition(WORLD_NAME, 10, 64, 20)));
        manager.queueDeobfuscation(player, List.of(new BlockPosition(WORLD_NAME, 20, 64, 20)));

        assertEquals(2, manager.getPendingCount(playerId));
    }

    @Test
    void flushPendingBatchesWithNoPendingStillProcessesDeferred() {
        ConfigurationManager configManager = mock(ConfigurationManager.class);
        WorldConfig config = WorldConfig.builder()
            .deepslateBelowY(0)
            .maxRevealedPerPlayer(10000)
            .maxDeobfuscationUpdatesPerTick(2)
            .build();
        when(configManager.getWorldConfig(any(World.class))).thenReturn(config);
        when(plugin.getConfigurationManager()).thenReturn(configManager);

        UUID playerId = UUID.randomUUID();
        PlayerData data = new PlayerData(10000);
        Player player = setupPlayer(playerId, data);

        when(nmsAdapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt()))
            .thenReturn(10);
        doReturn(true).when(materialSet).isHidden(10);

        DeobfuscationManager manager = new DeobfuscationManager(plugin, nmsAdapter, materialSet);

        List<BlockPosition> positions = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            positions.add(new BlockPosition(WORLD_NAME, i, 64, 0));
        }
        manager.queueDeobfuscation(player, positions);

        // With maxUpdates=2 and 5 items: flush1 processes 2, defers 3
        manager.flushPendingBatches();
        int deferredAfterFirst = manager.getDeferredCount(playerId);
        assertTrue(deferredAfterFirst > 0,
            "Should have deferred items after first flush, but had: " + deferredAfterFirst);

        // flush2 processes 2 more, defers 1
        manager.flushPendingBatches();
        int deferredAfterSecond = manager.getDeferredCount(playerId);
        assertTrue(deferredAfterSecond > 0,
            "Should still have 1 deferred after second flush, but had: " + deferredAfterSecond);

        // flush3 processes the final 1
        manager.flushPendingBatches();
        assertEquals(0, manager.getPendingCount(playerId));
        assertEquals(0, manager.getDeferredCount(playerId));
    }

    @Test
    void sendBatchedUpdatesEmptyPositionsIsNoOp() {
        Player player = mock(Player.class);
        DeobfuscationManager manager = createManager();
        manager.sendBatchedUpdates(player, world, WORLD_NAME, Collections.emptyList());

        verify(nmsAdapter, never()).createBlockUpdatePacket(any(), anyInt());
        verify(nmsAdapter, never()).createMultiBlockUpdatePacket(any(), anyInt(), anyInt(), any());
        verify(nmsAdapter, never()).createChunkDataPacket(any(), anyInt(), anyInt());
    }

    @Test
    void sendBatchedUpdatesAllInvalidBlockStatesIsNoOp() {
        Player player = mock(Player.class);
        when(player.getWorld()).thenReturn(world);

        when(nmsAdapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt()))
            .thenReturn(-1);

        DeobfuscationManager manager = createManager();
        List<BlockPosition> positions = List.of(
            new BlockPosition(WORLD_NAME, 0, 64, 0),
            new BlockPosition(WORLD_NAME, 1, 64, 0)
        );

        manager.sendBatchedUpdates(player, world, WORLD_NAME, positions);

        verify(nmsAdapter, never()).sendPacket(any(Player.class), any());
    }

    @Test
    void sectionKeyRoundTripAtBoundaries() {
        int[][] cases = {
            {0, 0, 0},
            {-1, -1, -1},
            {1, 1, 1},
            {100000, 20, -100000},
            {-100000, -4, 100000}
        };
        for (int[] c : cases) {
            long key = DeobfuscationManager.sectionKey(c[0], c[1], c[2]);
            assertEquals(c[0], DeobfuscationManager.extractChunkXFromKey(key),
                "chunkX mismatch for " + Arrays.toString(c));
            assertEquals(c[1], DeobfuscationManager.extractSectionYFromKey(key),
                "sectionY mismatch for " + Arrays.toString(c));
            assertEquals(c[2], DeobfuscationManager.extractChunkZFromKey(key),
                "chunkZ mismatch for " + Arrays.toString(c));
        }
    }

    @Test
    void sectionKeyRoundTripAtEncodingBoundary() {
        // chunkX/Z use 22-bit signed (±2,097,151), sectionY uses 20-bit signed (±524,287)
        long key = DeobfuscationManager.sectionKey(2097151, 524287, -2097152);
        assertEquals(2097151, DeobfuscationManager.extractChunkXFromKey(key));
        assertEquals(524287, DeobfuscationManager.extractSectionYFromKey(key));
        assertEquals(-2097152, DeobfuscationManager.extractChunkZFromKey(key));
    }

    @Test
    void sendBatchedUpdatesSinglePositionSendsIndividualPacket() {
        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        when(player.getWorld()).thenReturn(world);

        when(nmsAdapter.getBlockStateAt(eq(world), eq(5), eq(70), eq(10)))
            .thenReturn(42);
        doReturn(true).when(materialSet).isHidden(42);

        DeobfuscationManager manager = createManager();
        List<BlockPosition> positions = List.of(new BlockPosition(WORLD_NAME, 5, 70, 10));

        manager.sendBatchedUpdates(player, world, WORLD_NAME, positions);

        verify(nmsAdapter, times(1)).createBlockUpdatePacket(any(Location.class), eq(42));
        verify(nmsAdapter, never()).createMultiBlockUpdatePacket(any(), anyInt(), anyInt(), any());
        verify(nmsAdapter, never()).createChunkDataPacket(any(), anyInt(), anyInt());
    }

    @Test
    void queueDeobfuscationRevealedBlocksPreventDuplicateQueueing() {
        UUID playerId = UUID.randomUUID();
        PlayerData data = new PlayerData(10000);
        Player player = setupPlayer(playerId, data);

        when(nmsAdapter.getBlockStateAt(eq(world), eq(10), eq(64), eq(20)))
                .thenReturn(10);
        doReturn(true).when(materialSet).isHidden(10);

        DeobfuscationManager manager = createManager();
        BlockPosition pos = new BlockPosition(WORLD_NAME, 10, 64, 20);
        manager.queueDeobfuscation(player, List.of(pos));
        assertEquals(1, manager.getPendingCount(playerId));

        manager.queueDeobfuscation(player, List.of(pos));
        assertEquals(1, manager.getPendingCount(playerId));
    }

    @Test
    void batchGroupingBySection() {
        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        when(player.getWorld()).thenReturn(world);

        when(nmsAdapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt()))
                .thenReturn(10);
        doReturn(true).when(materialSet).isHidden(10);

        DeobfuscationManager manager = createManager();

        List<BlockPosition> positions = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            positions.add(new BlockPosition(WORLD_NAME, i, 64, 0));
        }
        for (int i = 0; i < 4; i++) {
            positions.add(new BlockPosition(WORLD_NAME, i, 80, 0));
        }
        for (int i = 0; i < 3; i++) {
            positions.add(new BlockPosition(WORLD_NAME, i + 16, 64, 16));
        }

        manager.sendBatchedUpdates(player, world, WORLD_NAME, positions);

        // Section y=4 (y=64) in chunk (0,0): 4 blocks → multi-block
        // Section y=5 (y=80) in chunk (0,0): 4 blocks → multi-block
        // Section y=4 (y=64) in chunk (1,1): 3 blocks (< threshold) → individual packets
        verify(nmsAdapter, times(2)).createMultiBlockUpdatePacket(eq(world), eq(0), eq(0), any(Map.class));
        verify(nmsAdapter, times(3)).createBlockUpdatePacket(any(Location.class), anyInt());
    }

    @Test
    void packetTypeSelectionThresholds() {
        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        when(player.getWorld()).thenReturn(world);

        when(nmsAdapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt()))
                .thenReturn(10);
        doReturn(true).when(materialSet).isHidden(10);

        DeobfuscationManager manager = createManager();

        List<BlockPosition> threePositions = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            threePositions.add(new BlockPosition(WORLD_NAME, i, 64, 0));
        }
        manager.sendBatchedUpdates(player, world, WORLD_NAME, threePositions);
        verify(nmsAdapter, times(3)).createBlockUpdatePacket(any(Location.class), anyInt());
        verify(nmsAdapter, never()).createMultiBlockUpdatePacket(any(), anyInt(), anyInt(), any());

        reset(nmsAdapter);
        lenient().when(nmsAdapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt())).thenReturn(10);
        lenient().when(nmsAdapter.createBlockUpdatePacket(any(Location.class), anyInt())).thenReturn(new Object());
        lenient().when(nmsAdapter.createMultiBlockUpdatePacket(any(World.class), anyInt(), anyInt(), any(Map.class))).thenReturn(new Object());
        lenient().when(nmsAdapter.createChunkDataPacket(any(World.class), anyInt(), anyInt())).thenReturn(new Object());
        doNothing().when(nmsAdapter).sendPacket(any(Player.class), any());

        List<BlockPosition> fourPositions = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            fourPositions.add(new BlockPosition(WORLD_NAME, i, 64, 0));
        }
        manager.sendBatchedUpdates(player, world, WORLD_NAME, fourPositions);
        verify(nmsAdapter).createMultiBlockUpdatePacket(eq(world), anyInt(), anyInt(), any(Map.class));
        verify(nmsAdapter, never()).createChunkDataPacket(any(), anyInt(), anyInt());

        reset(nmsAdapter);
        lenient().when(nmsAdapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt())).thenReturn(10);
        lenient().when(nmsAdapter.createBlockUpdatePacket(any(Location.class), anyInt())).thenReturn(new Object());
        lenient().when(nmsAdapter.createMultiBlockUpdatePacket(any(World.class), anyInt(), anyInt(), any(Map.class))).thenReturn(new Object());
        lenient().when(nmsAdapter.createChunkDataPacket(any(World.class), anyInt(), anyInt())).thenReturn(new Object());
        doNothing().when(nmsAdapter).sendPacket(any(Player.class), any());

        List<BlockPosition> sixtyFivePositions = new ArrayList<>();
        for (int i = 0; i < 65; i++) {
            sixtyFivePositions.add(new BlockPosition(WORLD_NAME, i & 15, 64 + (i >> 4), 0));
        }
        manager.sendBatchedUpdates(player, world, WORLD_NAME, sixtyFivePositions);
        verify(nmsAdapter).createChunkDataPacket(eq(world), anyInt(), anyInt());
    }

    @Test
    void rateLimitingDefersExcess() {
        ConfigurationManager configManager = mock(ConfigurationManager.class);
        WorldConfig limitedConfig = WorldConfig.builder()
                .deepslateBelowY(0)
                .maxRevealedPerPlayer(10000)
                .maxDeobfuscationUpdatesPerTick(3)
                .build();
        when(configManager.getWorldConfig(any(World.class))).thenReturn(limitedConfig);
        when(plugin.getConfigurationManager()).thenReturn(configManager);

        UUID playerId = UUID.randomUUID();
        PlayerData data = new PlayerData(10000);
        Player player = setupPlayer(playerId, data);

        when(nmsAdapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt()))
                .thenReturn(10);
        doReturn(true).when(materialSet).isHidden(10);

        DeobfuscationManager manager = new DeobfuscationManager(plugin, nmsAdapter, materialSet);

        List<BlockPosition> positions = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            positions.add(new BlockPosition(WORLD_NAME, i, 64, 0));
        }
        manager.queueDeobfuscation(player, positions);

        assertTrue(manager.getPendingCount(playerId) > 0,
                "Some positions should be in pending queue");
        assertTrue(manager.getDeferredCount(playerId) > 0,
                "Excess positions should be deferred to next tick");

        int total = manager.getPendingCount(playerId) + manager.getDeferredCount(playerId);
        assertEquals(10, total,
                "Total of pending + deferred should equal all queued positions");
    }

    @Test
    void reObfuscationAfterDelay() {
        UUID playerId = UUID.randomUUID();
        PlayerData data = new PlayerData(10000);
        Player player = setupPlayer(playerId, data);

        when(nmsAdapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt()))
                .thenReturn(10);
        doReturn(true).when(materialSet).isHidden(10);
        when(materialSet.getReplacement(anyInt(), anyInt(), anyString())).thenReturn(1);
        when(nmsAdapter.createBlockUpdatePacket(any(Location.class), anyInt()))
                .thenReturn(new Object());

        DeobfuscationManager manager = createManager();

        List<BlockPosition> positions = List.of(
                new BlockPosition(WORLD_NAME, 0, 64, 0),
                new BlockPosition(WORLD_NAME, 1, 64, 0)
        );

        manager.queueReObfuscation(player, positions);

        manager.flushPendingBatches();

        verify(nmsAdapter, atLeastOnce()).sendPacket(eq(player), any());
    }
}
