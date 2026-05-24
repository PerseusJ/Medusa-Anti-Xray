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

import java.util.*;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProximityTrackerTest {

    private static final String WORLD_NAME = "world";

    private AntiXrayPlugin plugin;
    private NmsAdapter nmsAdapter;
    private MaterialSet materialSet;
    private World world;
    private MockedStatic<Bukkit> bukkitMock;
    private BukkitScheduler scheduler;
    private BukkitTask task;

    @BeforeEach
    void setUp() {
        Server server = mock(Server.class);
        lenient().when(server.getName()).thenReturn("TestServer");
        lenient().when(server.getVersion()).thenReturn("1.21");
        lenient().when(server.getBukkitVersion()).thenReturn("1.21-R0.1-SNAPSHOT");
        bukkitMock = Mockito.mockStatic(Bukkit.class);
        bukkitMock.when(Bukkit::getServer).thenReturn(server);

        scheduler = mock(BukkitScheduler.class);
        task = mock(BukkitTask.class);
        lenient().when(task.getTaskId()).thenReturn(1);
        lenient().when(scheduler.runTaskTimerAsynchronously(any(), any(Runnable.class), anyLong(), anyLong()))
                .thenReturn(task);
        lenient().when(scheduler.runTask(any(), any(Runnable.class))).thenReturn(task);
        doNothing().when(scheduler).cancelTask(anyInt());
        bukkitMock.when(Bukkit::getScheduler).thenReturn(scheduler);

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

    private ProximityTracker createTracker(int updateRadius, int updateRadiusY,
            int checkInterval, double threshold) {
        return createTracker(updateRadius, updateRadiusY, checkInterval, threshold, false, 200L);
    }

    private ProximityTracker createTracker(int updateRadius, int updateRadiusY,
            int checkInterval, double threshold,
            boolean reObfuscateEnabled, long reObfuscateDelayTicks) {
        return new ProximityTracker(
            plugin, nmsAdapter, materialSet,
            false, false,
            updateRadius, updateRadiusY,
            checkInterval, threshold,
            reObfuscateEnabled, reObfuscateDelayTicks,
            70.0,
            0, 0
        );
    }

    @Test
    void constructorCreatesVisibilityResolver() {
        ProximityTracker tracker = createTracker(5, 5, 5, 0.5);
        assertNotNull(tracker.getVisibilityResolver());
    }

    @Test
    void startRegistersAsyncTask() {
        ProximityTracker tracker = createTracker(5, 5, 5, 0.5);
        tracker.start();
        verify(scheduler).runTaskTimerAsynchronously(eq(plugin), any(Runnable.class), eq(5L), eq(5L));
        assertTrue(tracker.isRunning());
    }

    @Test
    void startWhenAlreadyRunningDoesNotDuplicate() {
        ProximityTracker tracker = createTracker(5, 5, 5, 0.5);
        tracker.start();
        tracker.start();
        verify(scheduler, times(1)).runTaskTimerAsynchronously(any(), any(Runnable.class), anyLong(), anyLong());
    }

    @Test
    void stopCancelsTask() {
        ProximityTracker tracker = createTracker(5, 5, 5, 0.5);
        tracker.start();
        tracker.stop();
        assertFalse(tracker.isRunning());
        verify(scheduler).cancelTask(1);
    }

    @Test
    void stopWhenNotRunningIsNoOp() {
        ProximityTracker tracker = createTracker(5, 5, 5, 0.5);
        tracker.stop();
        assertFalse(tracker.isRunning());
    }

    @Test
    void isRunningInitiallyFalse() {
        ProximityTracker tracker = createTracker(5, 5, 5, 0.5);
        assertFalse(tracker.isRunning());
    }

    @Test
    void movementThresholdSkipOnSamePosition() {
        UUID playerId = UUID.randomUUID();
        PlayerData data = new PlayerData(10000);
        Location samePos = new Location(world, 0.0, 64.0, 0.0);
        data.setLastCheckedPosition(samePos);

        Map<UUID, PlayerData> playerDataMap = new HashMap<>();
        playerDataMap.put(playerId, data);
        when(plugin.getAllPlayerData()).thenReturn(Collections.unmodifiableMap(playerDataMap));

        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        when(player.getWorld()).thenReturn(world);
        when(player.getEyeLocation()).thenReturn(new Location(world, 0.0, 64.0, 0.0));
        when(player.getUniqueId()).thenReturn(playerId);
        bukkitMock.when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);

        ProximityTracker tracker = createTracker(5, 5, 5, 0.5);

        when(nmsAdapter.getBlockStateAt(any(World.class), anyInt(), anyInt(), anyInt()))
                .thenReturn(-1);

        tracker.start();
        Runnable asyncTick = captureAsyncTickRunnable();
        asyncTick.run();

        verify(nmsAdapter, never()).getBlockStateAt(any(World.class), anyInt(), anyInt(), anyInt());
    }

    @Test
    void movementPastThresholdTriggersScan() {
        UUID playerId = UUID.randomUUID();
        PlayerData data = new PlayerData(10000);
        Location oldPos = new Location(world, 0.0, 64.0, 0.0);
        data.setLastCheckedPosition(oldPos);

        Map<UUID, PlayerData> playerDataMap = new HashMap<>();
        playerDataMap.put(playerId, data);
        when(plugin.getAllPlayerData()).thenReturn(Collections.unmodifiableMap(playerDataMap));

        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        when(player.getWorld()).thenReturn(world);
        Location newPos = new Location(world, 10.0, 64.0, 0.0);
        when(player.getEyeLocation()).thenReturn(newPos);
        when(player.getUniqueId()).thenReturn(playerId);
        bukkitMock.when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);

        ProximityTracker tracker = createTracker(2, 2, 5, 0.5);

        when(nmsAdapter.getBlockStateAt(any(World.class), anyInt(), anyInt(), anyInt()))
                .thenReturn(-1);

        tracker.start();
        Runnable asyncTick = captureAsyncTickRunnable();
        asyncTick.run();

        verify(nmsAdapter, atLeastOnce()).getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt());
    }

    @Test
    void offlinePlayerIsSkipped() {
        UUID playerId = UUID.randomUUID();
        PlayerData data = new PlayerData(10000);

        Map<UUID, PlayerData> playerDataMap = new HashMap<>();
        playerDataMap.put(playerId, data);
        when(plugin.getAllPlayerData()).thenReturn(Collections.unmodifiableMap(playerDataMap));

        bukkitMock.when(() -> Bukkit.getPlayer(playerId)).thenReturn(null);

        ProximityTracker tracker = createTracker(5, 5, 5, 0.5);
        tracker.start();
        Runnable asyncTick = captureAsyncTickRunnable();
        asyncTick.run();

        verify(nmsAdapter, never()).getBlockStateAt(any(World.class), anyInt(), anyInt(), anyInt());
    }

    @Test
    void hiddenBlockIsRevealed() {
        UUID playerId = UUID.randomUUID();
        PlayerData data = new PlayerData(10000);

        Map<UUID, PlayerData> playerDataMap = new HashMap<>();
        playerDataMap.put(playerId, data);
        when(plugin.getAllPlayerData()).thenReturn(Collections.unmodifiableMap(playerDataMap));

        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        when(player.getWorld()).thenReturn(world);
        Location eyeLoc = new Location(world, 0.0, 64.0, 0.0);
        when(player.getEyeLocation()).thenReturn(eyeLoc);
        when(player.getUniqueId()).thenReturn(playerId);
        bukkitMock.when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);

        when(nmsAdapter.getBlockStateAt(eq(world), eq(0), eq(64), eq(1)))
                .thenReturn(10);
        doReturn(true).when(materialSet).isHidden(10);
        when(nmsAdapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt()))
                .thenAnswer(inv -> {
                    int x = inv.getArgument(1, Integer.class);
                    int y = inv.getArgument(2, Integer.class);
                    int z = inv.getArgument(3, Integer.class);
                    if (x == 0 && y == 64 && z == 1) return 10;
                    return -1;
                });

        ProximityTracker tracker = createTracker(2, 2, 5, 0.5);
        tracker.start();
        Runnable asyncTick = captureAsyncTickRunnable();
        asyncTick.run();

        BlockPosition pos = new BlockPosition(WORLD_NAME, 0, 64, 1);
        assertTrue(data.getRevealedBlocks().contains(pos));
    }

    @Test
    void alreadyRevealedBlockNotReAdded() {
        UUID playerId = UUID.randomUUID();
        PlayerData data = new PlayerData(10000);
        BlockPosition pos = new BlockPosition(WORLD_NAME, 0, 64, 1);
        data.getRevealedBlocks().add(pos, 50L);

        Map<UUID, PlayerData> playerDataMap = new HashMap<>();
        playerDataMap.put(playerId, data);
        when(plugin.getAllPlayerData()).thenReturn(Collections.unmodifiableMap(playerDataMap));

        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        when(player.getWorld()).thenReturn(world);
        Location eyeLoc = new Location(world, 10.0, 64.0, 0.0);
        when(player.getEyeLocation()).thenReturn(eyeLoc);
        when(player.getUniqueId()).thenReturn(playerId);
        bukkitMock.when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);

        when(nmsAdapter.getBlockStateAt(eq(world), eq(0), eq(64), eq(1)))
                .thenReturn(10);
        doReturn(true).when(materialSet).isHidden(10);
        when(nmsAdapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt()))
                .thenAnswer(inv -> {
                    int x = inv.getArgument(1, Integer.class);
                    int y = inv.getArgument(2, Integer.class);
                    int z = inv.getArgument(3, Integer.class);
                    if (x == 0 && y == 64 && z == 1) return 10;
                    return -1;
                });

        ProximityTracker tracker = createTracker(2, 2, 5, 0.5);
        tracker.start();
        Runnable asyncTick = captureAsyncTickRunnable();
        asyncTick.run();

        assertEquals(1, data.getRevealedBlocks().size());
    }

    @Test
    void reObfuscateRemovesExpiredBlockOutsideUpdateRadius() {
        UUID playerId = UUID.randomUUID();
        PlayerData data = new PlayerData(10000);

        BlockPosition nearPos = new BlockPosition(WORLD_NAME, 1, 64, 0);
        BlockPosition farPos = new BlockPosition(WORLD_NAME, 20, 64, 0);
        data.getRevealedBlocks().add(nearPos, 100L);
        data.getRevealedBlocks().add(farPos, 100L);

        Map<UUID, PlayerData> playerDataMap = new HashMap<>();
        playerDataMap.put(playerId, data);
        when(plugin.getAllPlayerData()).thenReturn(Collections.unmodifiableMap(playerDataMap));

        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        when(player.getWorld()).thenReturn(world);
        Location eyeLoc = new Location(world, 0.0, 64.0, 0.0);
        when(player.getEyeLocation()).thenReturn(eyeLoc);
        when(player.getUniqueId()).thenReturn(playerId);
        bukkitMock.when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);

        when(nmsAdapter.getBlockStateAt(any(World.class), anyInt(), anyInt(), anyInt()))
                .thenReturn(-1);

        DeobfuscationManager manager = mock(DeobfuscationManager.class);
        when(plugin.getDeobfuscationManager()).thenReturn(manager);

        ProximityTracker tracker = createTracker(4, 4, 5, 0.5, true, 200L);
        tracker.start();
        Runnable asyncTick = captureAsyncTickRunnable();
        asyncTick.run();

        assertTrue(data.getRevealedBlocks().contains(nearPos));
        assertFalse(data.getRevealedBlocks().contains(farPos));
        verify(manager).queueReObfuscation(eq(player), anyList());
    }

    @Test
    void reObfuscateKeepsExpiredBlockInsideUpdateRadius() {
        UUID playerId = UUID.randomUUID();
        PlayerData data = new PlayerData(10000);

        BlockPosition nearPos = new BlockPosition(WORLD_NAME, 1, 64, 0);
        data.getRevealedBlocks().add(nearPos, 100L);

        Map<UUID, PlayerData> playerDataMap = new HashMap<>();
        playerDataMap.put(playerId, data);
        when(plugin.getAllPlayerData()).thenReturn(Collections.unmodifiableMap(playerDataMap));

        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        when(player.getWorld()).thenReturn(world);
        Location eyeLoc = new Location(world, 0.0, 64.0, 0.0);
        when(player.getEyeLocation()).thenReturn(eyeLoc);
        when(player.getUniqueId()).thenReturn(playerId);
        bukkitMock.when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);

        when(nmsAdapter.getBlockStateAt(any(World.class), anyInt(), anyInt(), anyInt()))
                .thenReturn(-1);

        DeobfuscationManager manager = mock(DeobfuscationManager.class);
        when(plugin.getDeobfuscationManager()).thenReturn(manager);

        ProximityTracker tracker = createTracker(4, 4, 5, 0.5, true, 200L);
        tracker.start();
        Runnable asyncTick = captureAsyncTickRunnable();
        asyncTick.run();

        assertTrue(data.getRevealedBlocks().contains(nearPos));
        verify(manager, never()).queueReObfuscation(any(), anyList());
    }

    @Test
    void reObfuscateDisabledDoesNotRemoveExpiredBlocks() {
        UUID playerId = UUID.randomUUID();
        PlayerData data = new PlayerData(10000);

        BlockPosition farPos = new BlockPosition(WORLD_NAME, 20, 64, 0);
        data.getRevealedBlocks().add(farPos, 100L);

        Map<UUID, PlayerData> playerDataMap = new HashMap<>();
        playerDataMap.put(playerId, data);
        when(plugin.getAllPlayerData()).thenReturn(Collections.unmodifiableMap(playerDataMap));

        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        when(player.getWorld()).thenReturn(world);
        Location eyeLoc = new Location(world, 0.0, 64.0, 0.0);
        when(player.getEyeLocation()).thenReturn(eyeLoc);
        when(player.getUniqueId()).thenReturn(playerId);
        bukkitMock.when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);

        when(nmsAdapter.getBlockStateAt(any(World.class), anyInt(), anyInt(), anyInt()))
                .thenReturn(-1);

        DeobfuscationManager manager = mock(DeobfuscationManager.class);
        when(plugin.getDeobfuscationManager()).thenReturn(manager);

        ProximityTracker tracker = createTracker(4, 4, 5, 0.5, false, 200L);
        tracker.start();
        Runnable asyncTick = captureAsyncTickRunnable();
        asyncTick.run();

        assertTrue(data.getRevealedBlocks().contains(farPos));
        verify(manager, never()).queueReObfuscation(any(), anyList());
    }

    @Test
    void reObfuscateChecksVerticalRadius() {
        UUID playerId = UUID.randomUUID();
        PlayerData data = new PlayerData(10000);

        BlockPosition sameYPos = new BlockPosition(WORLD_NAME, 0, 64, 0);
        BlockPosition farYPos = new BlockPosition(WORLD_NAME, 0, 80, 0);
        data.getRevealedBlocks().add(sameYPos, 100L);
        data.getRevealedBlocks().add(farYPos, 100L);

        Map<UUID, PlayerData> playerDataMap = new HashMap<>();
        playerDataMap.put(playerId, data);
        when(plugin.getAllPlayerData()).thenReturn(Collections.unmodifiableMap(playerDataMap));

        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        when(player.getWorld()).thenReturn(world);
        Location eyeLoc = new Location(world, 0.0, 64.0, 0.0);
        when(player.getEyeLocation()).thenReturn(eyeLoc);
        when(player.getUniqueId()).thenReturn(playerId);
        bukkitMock.when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);

        when(nmsAdapter.getBlockStateAt(any(World.class), anyInt(), anyInt(), anyInt()))
                .thenReturn(-1);

        DeobfuscationManager manager = mock(DeobfuscationManager.class);
        when(plugin.getDeobfuscationManager()).thenReturn(manager);

        ProximityTracker tracker = createTracker(4, 4, 5, 0.5, true, 200L);
        tracker.start();
        Runnable asyncTick = captureAsyncTickRunnable();
        asyncTick.run();

        assertTrue(data.getRevealedBlocks().contains(sameYPos));
        assertFalse(data.getRevealedBlocks().contains(farYPos));
    }

    @Test
    void radiusCheckIncludesCorrectBlocks() {
        UUID playerId = UUID.randomUUID();
        PlayerData data = new PlayerData(10000);

        Map<UUID, PlayerData> playerDataMap = new HashMap<>();
        playerDataMap.put(playerId, data);
        when(plugin.getAllPlayerData()).thenReturn(Collections.unmodifiableMap(playerDataMap));

        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        when(player.getWorld()).thenReturn(world);
        Location eyeLoc = new Location(world, 0.0, 64.0, 0.0);
        when(player.getEyeLocation()).thenReturn(eyeLoc);
        when(player.getUniqueId()).thenReturn(playerId);
        bukkitMock.when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);

        when(nmsAdapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt()))
                .thenAnswer(inv -> {
                    int x = inv.getArgument(1, Integer.class);
                    int y = inv.getArgument(2, Integer.class);
                    int z = inv.getArgument(3, Integer.class);
                    if (Math.abs(x) <= 2 && Math.abs(y - 64) <= 2 && Math.abs(z) <= 2) {
                        return 10;
                    }
                    return -1;
                });
        doReturn(true).when(materialSet).isHidden(10);

        ProximityTracker tracker = createTracker(2, 2, 5, 0.5);
        tracker.start();
        Runnable asyncTick = captureAsyncTickRunnable();
        asyncTick.run();

        BlockPosition insidePos = new BlockPosition(WORLD_NAME, 1, 65, 1);
        assertTrue(data.getRevealedBlocks().contains(insidePos),
                "Block within radius should be tracked");
        BlockPosition edgePos = new BlockPosition(WORLD_NAME, 2, 66, 2);
        assertTrue(data.getRevealedBlocks().contains(edgePos),
                "Block at radius boundary should be tracked");
    }

    @Test
    void radiusExcludesDistantBlocks() {
        UUID playerId = UUID.randomUUID();
        PlayerData data = new PlayerData(10000);

        Map<UUID, PlayerData> playerDataMap = new HashMap<>();
        playerDataMap.put(playerId, data);
        when(plugin.getAllPlayerData()).thenReturn(Collections.unmodifiableMap(playerDataMap));

        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        when(player.getWorld()).thenReturn(world);
        Location eyeLoc = new Location(world, 0.0, 64.0, 0.0);
        when(player.getEyeLocation()).thenReturn(eyeLoc);
        when(player.getUniqueId()).thenReturn(playerId);
        bukkitMock.when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);

        when(nmsAdapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt()))
                .thenReturn(10);
        doReturn(true).when(materialSet).isHidden(10);

        ProximityTracker tracker = createTracker(2, 2, 5, 0.5);
        tracker.start();
        Runnable asyncTick = captureAsyncTickRunnable();
        asyncTick.run();

        BlockPosition distantXZ = new BlockPosition(WORLD_NAME, 5, 64, 5);
        assertFalse(data.getRevealedBlocks().contains(distantXZ),
                "Block outside XZ radius should not be tracked");
        BlockPosition distantY = new BlockPosition(WORLD_NAME, 0, 70, 0);
        assertFalse(data.getRevealedBlocks().contains(distantY),
                "Block outside Y radius should not be tracked");
    }

    @Test
    void yRadiusSeparateFromXzRadius() {
        UUID playerId = UUID.randomUUID();
        PlayerData data = new PlayerData(10000);

        Map<UUID, PlayerData> playerDataMap = new HashMap<>();
        playerDataMap.put(playerId, data);
        when(plugin.getAllPlayerData()).thenReturn(Collections.unmodifiableMap(playerDataMap));

        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        when(player.getWorld()).thenReturn(world);
        Location eyeLoc = new Location(world, 0.0, 64.0, 0.0);
        when(player.getEyeLocation()).thenReturn(eyeLoc);
        when(player.getUniqueId()).thenReturn(playerId);
        bukkitMock.when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);

        when(nmsAdapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt()))
                .thenReturn(10);
        doReturn(true).when(materialSet).isHidden(10);

        ProximityTracker tracker = createTracker(2, 1, 5, 0.5);
        tracker.start();
        Runnable asyncTick = captureAsyncTickRunnable();
        asyncTick.run();

        BlockPosition withinXzBeyondY = new BlockPosition(WORLD_NAME, 1, 66, 0);
        assertFalse(data.getRevealedBlocks().contains(withinXzBeyondY),
                "Block within XZ radius but beyond Y radius should not be tracked");
        BlockPosition beyondXzWithinY = new BlockPosition(WORLD_NAME, 5, 64, 0);
        assertFalse(data.getRevealedBlocks().contains(beyondXzWithinY),
                "Block within Y radius but beyond XZ radius should not be tracked");
        BlockPosition withinBoth = new BlockPosition(WORLD_NAME, 1, 65, 1);
        assertTrue(data.getRevealedBlocks().contains(withinBoth),
                "Block within both XZ and Y radius should be tracked");
    }

    @Test
    void movementThresholdFiltering() {
        UUID playerId = UUID.randomUUID();
        PlayerData data = new PlayerData(10000);
        Location lastPos = new Location(world, 0.0, 64.0, 0.0);
        data.setLastCheckedPosition(lastPos);

        Map<UUID, PlayerData> playerDataMap = new HashMap<>();
        playerDataMap.put(playerId, data);
        when(plugin.getAllPlayerData()).thenReturn(Collections.unmodifiableMap(playerDataMap));

        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        when(player.getWorld()).thenReturn(world);
        Location slightMove = new Location(world, 0.1, 64.0, 0.0);
        when(player.getEyeLocation()).thenReturn(slightMove);
        when(player.getUniqueId()).thenReturn(playerId);
        bukkitMock.when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);

        when(nmsAdapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt()))
                .thenReturn(10);
        doReturn(true).when(materialSet).isHidden(10);

        ProximityTracker tracker = createTracker(4, 4, 5, 0.5);
        tracker.start();
        Runnable asyncTick = captureAsyncTickRunnable();
        asyncTick.run();

        assertTrue(data.getRevealedBlocks().size() == 0,
                "Minor movement below threshold should not trigger scan");

        Location significantMove = new Location(world, 10.0, 64.0, 0.0);
        when(player.getEyeLocation()).thenReturn(significantMove);
        asyncTick.run();

        assertTrue(data.getRevealedBlocks().size() > 0,
                "Movement past threshold should trigger scan");
    }

    private Runnable captureAsyncTickRunnable() {
        org.mockito.ArgumentCaptor<Runnable> captor = org.mockito.ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTaskTimerAsynchronously(eq(plugin), captor.capture(), anyLong(), anyLong());
        return captor.getValue();
    }
}
