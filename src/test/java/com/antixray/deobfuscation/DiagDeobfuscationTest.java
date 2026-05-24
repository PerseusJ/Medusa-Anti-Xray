package com.antixray.deobfuscation;

import com.antixray.AntiXrayPlugin;
import com.antixray.config.ConfigurationManager;
import com.antixray.config.WorldConfig;
import com.antixray.nms.NmsAdapter;
import com.antixray.util.BlockPosition;
import com.antixray.util.MaterialSet;
import org.bukkit.Bukkit;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DiagDeobfuscationTest {

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
        doNothing().when(scheduler).cancelTask(anyInt());
        bukkitMock.when(Bukkit::getScheduler).thenReturn(scheduler);
        bukkitMock.when(Bukkit::getPluginManager).thenReturn(mock(org.bukkit.plugin.PluginManager.class));

        world = mock(World.class);
        lenient().when(world.getName()).thenReturn(WORLD_NAME);
        lenient().when(world.getMinHeight()).thenReturn(-64);
        lenient().when(world.getMaxHeight()).thenReturn(320);

        nmsAdapter = mock(NmsAdapter.class);
        when(nmsAdapter.getBlockStateAt(any(World.class), anyInt(), anyInt(), anyInt()))
                .thenReturn(-1);
        lenient().when(nmsAdapter.createBlockUpdatePacket(any(), anyInt()))
                .thenReturn(new Object());
        lenient().when(nmsAdapter.createMultiBlockUpdatePacket(any(World.class), anyInt(), anyInt(), any(Map.class)))
                .thenReturn(new Object());
        doNothing().when(nmsAdapter).sendPacket(any(Player.class), any());

        materialSet = Mockito.spy(new MaterialSet(1, 2, 3, 4));

        plugin = mock(AntiXrayPlugin.class);
        when(plugin.getAllPlayerData()).thenReturn(Collections.emptyMap());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("AntiXray"));
    }

    @AfterEach
    void tearDown() {
        if (bukkitMock != null) {
            bukkitMock.close();
        }
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
    void diagFlushDeferred() {
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
            "Should have deferred items after first flush, but deferred=" + deferredAfterFirst);

        // flush2 processes 2 more, defers 1
        manager.flushPendingBatches();
        int deferredAfterSecond = manager.getDeferredCount(playerId);
        assertTrue(deferredAfterSecond > 0,
            "Should still have 1 deferred after second flush, but deferred=" + deferredAfterSecond);

        // flush3 processes the final 1
        manager.flushPendingBatches();
        assertEquals(0, manager.getPendingCount(playerId), "Pending should be 0 after third flush");
        assertEquals(0, manager.getDeferredCount(playerId), "Deferred should be 0 after third flush");
    }
}
