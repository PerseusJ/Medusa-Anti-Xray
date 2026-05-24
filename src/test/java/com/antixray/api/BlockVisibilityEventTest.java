package com.antixray.api;

import com.antixray.AntiXrayPlugin;
import com.antixray.config.ConfigurationManager;
import com.antixray.config.WorldConfig;
import com.antixray.deobfuscation.DeobfuscationManager;
import com.antixray.deobfuscation.PlayerData;
import com.antixray.nms.NmsAdapter;
import com.antixray.util.BlockPosition;
import com.antixray.util.MaterialSet;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BlockVisibilityEventTest {

    private Server oldServer;
    private Server server;
    private PluginManager pluginManager;
    private AntiXrayPlugin plugin;
    private NmsAdapter nmsAdapter;
    private MaterialSet materialSet;
    private World world;

    @BeforeEach
    void setUp() throws Exception {
        java.lang.reflect.Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        oldServer = (Server) serverField.get(null);

        server = mock(Server.class);
        pluginManager = mock(PluginManager.class);
        when(server.getPluginManager()).thenReturn(pluginManager);
        serverField.set(null, server);

        plugin = mock(AntiXrayPlugin.class);
        nmsAdapter = mock(NmsAdapter.class);
        materialSet = mock(MaterialSet.class);
        world = mock(World.class);

        when(world.getName()).thenReturn("world");
        when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);

        ConfigurationManager configManager = mock(ConfigurationManager.class);
        WorldConfig worldConfig = WorldConfig.builder()
                .deepslateBelowY(0)
                .maxRevealedPerPlayer(10000)
                .maxDeobfuscationUpdatesPerTick(64)
                .build();
        when(configManager.getWorldConfig(any(World.class))).thenReturn(worldConfig);
        when(plugin.getConfigurationManager()).thenReturn(configManager);
        when(plugin.getNmsAdapter()).thenReturn(nmsAdapter);
    }

    @AfterEach
    void tearDown() throws Exception {
        java.lang.reflect.Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(null, oldServer);
    }

    @Test
    void testBlockVisibilityEventFieldsAccessible() {
        Player player = mock(Player.class);
        Location location = new Location(world, 10, 20, 30);
        BlockVisibilityEvent event = new BlockVisibilityEvent(player, location, Material.DIAMOND_ORE, Material.STONE);

        assertEquals(player, event.getPlayer());
        assertEquals(location, event.getLocation());
        assertEquals(Material.DIAMOND_ORE, event.getRealMaterial());
        assertEquals(Material.STONE, event.getObfuscatedMaterial());
        assertFalse(event.isCancelled());

        event.setCancelled(true);
        assertTrue(event.isCancelled());

        assertNotNull(event.getHandlers());
        assertNotNull(BlockVisibilityEvent.getHandlerList());
    }

    @Test
    void testBlockVisibilityEventFires() {
        UUID playerId = UUID.randomUUID();
        PlayerData playerData = new PlayerData(10000);
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getWorld()).thenReturn(world);
        when(plugin.getPlayerData(playerId)).thenReturn(playerData);
        when(plugin.getAllPlayerData()).thenReturn(Collections.singletonMap(playerId, playerData));

        when(nmsAdapter.getBlockStateAt(eq(world), eq(10), eq(64), eq(20))).thenReturn(100);
        when(materialSet.isHidden(100)).thenReturn(true);
        BlockData blockData = mock(BlockData.class);
        when(blockData.getMaterial()).thenReturn(Material.DIAMOND_ORE);
        when(nmsAdapter.getBlockDataFromId(100)).thenReturn(blockData);

        DeobfuscationManager manager = new DeobfuscationManager(plugin, nmsAdapter, materialSet);
        List<BlockPosition> positions = List.of(new BlockPosition("world", 10, 64, 20));

        manager.queueDeobfuscation(player, positions);

        // Verify event is fired
        ArgumentCaptor<BlockVisibilityEvent> eventCaptor = ArgumentCaptor.forClass(BlockVisibilityEvent.class);
        verify(pluginManager, times(1)).callEvent(eventCaptor.capture());

        BlockVisibilityEvent firedEvent = eventCaptor.getValue();
        assertEquals(player, firedEvent.getPlayer());
        assertEquals(new Location(world, 10, 64, 20), firedEvent.getLocation());
        assertEquals(Material.DIAMOND_ORE, firedEvent.getRealMaterial());
    }

    @Test
    void testBlockVisibilityEventCancellationPreventsDeobfuscation() {
        UUID playerId = UUID.randomUUID();
        PlayerData playerData = new PlayerData(10000);
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getWorld()).thenReturn(world);
        when(plugin.getPlayerData(playerId)).thenReturn(playerData);
        when(plugin.getAllPlayerData()).thenReturn(Collections.singletonMap(playerId, playerData));

        when(nmsAdapter.getBlockStateAt(eq(world), eq(10), eq(64), eq(20))).thenReturn(100);
        when(materialSet.isHidden(100)).thenReturn(true);
        BlockData blockData = mock(BlockData.class);
        when(blockData.getMaterial()).thenReturn(Material.DIAMOND_ORE);
        when(nmsAdapter.getBlockDataFromId(100)).thenReturn(blockData);

        // Cancel the event during callback
        doAnswer(invocation -> {
            BlockVisibilityEvent event = invocation.getArgument(0);
            event.setCancelled(true);
            return null;
        }).when(pluginManager).callEvent(any(BlockVisibilityEvent.class));

        DeobfuscationManager manager = new DeobfuscationManager(plugin, nmsAdapter, materialSet);
        List<BlockPosition> positions = List.of(new BlockPosition("world", 10, 64, 20));

        manager.queueDeobfuscation(player, positions);

        // Verify event was fired and cancelled
        verify(pluginManager, times(1)).callEvent(any(BlockVisibilityEvent.class));

        // Verify block was NOT added to pending queue or revealed blocks
        try {
            java.lang.reflect.Method getPendingCountMethod = DeobfuscationManager.class.getDeclaredMethod("getPendingCount", UUID.class);
            getPendingCountMethod.setAccessible(true);
            int pendingCount = (int) getPendingCountMethod.invoke(manager, playerId);
            assertEquals(0, pendingCount);
        } catch (Exception e) {
            fail("Failed to access getPendingCount: " + e.getMessage());
        }
        assertFalse(playerData.getRevealedBlocks().contains(new BlockPosition("world", 10, 64, 20)));
    }
}
