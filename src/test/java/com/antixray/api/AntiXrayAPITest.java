package com.antixray.api;

import com.antixray.AntiXrayAPIImpl;
import com.antixray.AntiXrayPlugin;
import com.antixray.config.ConfigurationManager;
import com.antixray.config.WorldConfig;
import com.antixray.engine.AirExposureChecker;
import com.antixray.engine.ObfuscationEngine;
import com.antixray.engine.ObfuscationMode;
import com.antixray.nms.NmsAdapter;
import com.antixray.util.MaterialSet;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AntiXrayAPITest {

    private AntiXrayPlugin plugin;
    private NmsAdapter nmsAdapter;
    private ObfuscationEngine obfuscationEngine;
    private MaterialSet materialSet;
    private AirExposureChecker exposureChecker;
    private ConfigurationManager configurationManager;
    private Server oldServer;

    @BeforeEach
    void setUp() throws Exception {
        java.lang.reflect.Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        oldServer = (Server) serverField.get(null);

        Server server = mock(Server.class);
        serverField.set(null, server);

        plugin = mock(AntiXrayPlugin.class);
        nmsAdapter = mock(NmsAdapter.class);
        obfuscationEngine = mock(ObfuscationEngine.class);
        materialSet = mock(MaterialSet.class);
        exposureChecker = mock(AirExposureChecker.class);
        configurationManager = mock(ConfigurationManager.class);

        when(plugin.getNmsAdapter()).thenReturn(nmsAdapter);
        when(plugin.getObfuscationEngine()).thenReturn(obfuscationEngine);
        when(obfuscationEngine.getMaterialSet()).thenReturn(materialSet);
        when(obfuscationEngine.getExposureChecker()).thenReturn(exposureChecker);
        when(plugin.getConfigurationManager()).thenReturn(configurationManager);

        java.lang.reflect.Field instanceField = AntiXrayPlugin.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, plugin);
    }

    @AfterEach
    void tearDown() throws Exception {
        java.lang.reflect.Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(null, oldServer);

        java.lang.reflect.Field instanceField = AntiXrayPlugin.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);
    }

    @Test
    void testBlockStateProxy() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("test_world");
        BlockData blockData = mock(BlockData.class);

        BlockState proxy = BlockStateProxy.create(world, 10, 20, 30, Material.DIAMOND_ORE, blockData);

        assertEquals(Material.DIAMOND_ORE, proxy.getType());
        assertEquals(blockData, proxy.getBlockData());
        assertEquals(world, proxy.getWorld());
        assertEquals(10, proxy.getX());
        assertEquals(20, proxy.getY());
        assertEquals(30, proxy.getZ());
        assertTrue(proxy.isPlaced());

        Location location = proxy.getLocation();
        assertEquals(world, location.getWorld());
        assertEquals(10.0, location.getX());
        assertEquals(20.0, location.getY());
        assertEquals(30.0, location.getZ());

        Location targetLoc = new Location(null, 0, 0, 0);
        proxy.getLocation(targetLoc);
        assertEquals(world, targetLoc.getWorld());
        assertEquals(10.0, targetLoc.getX());
        assertEquals(20.0, targetLoc.getY());
        assertEquals(30.0, targetLoc.getZ());

        BlockState otherProxySame = BlockStateProxy.create(world, 10, 20, 30, Material.GOLD_ORE, blockData);
        assertEquals(proxy, otherProxySame);
        assertEquals(proxy.hashCode(), otherProxySame.hashCode());

        assertThrows(UnsupportedOperationException.class, proxy::update);
    }

    @Test
    void testBlockVisibilityEvent() {
        Player player = mock(Player.class);
        Location location = new Location(null, 1, 2, 3);
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
    void testPlayerXraySuspicionEvent() {
        Player player = mock(Player.class);
        Map<String, Double> metrics = new HashMap<>();
        metrics.put("orePerHour", 15.5);
        PlayerXraySuspicionEvent event = new PlayerXraySuspicionEvent(player, AlertLevel.WARNING, metrics);

        assertEquals(player, event.getPlayer());
        assertEquals(AlertLevel.WARNING, event.getAlertLevel());
        assertEquals(metrics, event.getTriggeringMetrics());
        assertFalse(event.isCancelled());

        event.setCancelled(true);
        assertTrue(event.isCancelled());

        assertNotNull(event.getHandlers());
        assertNotNull(PlayerXraySuspicionEvent.getHandlerList());
    }

    @Test
    void testDefaultObfuscationProvider() {
        DefaultObfuscationProvider provider = new DefaultObfuscationProvider();
        World world = mock(World.class);
        BlockState blockState = mock(BlockState.class);
        when(blockState.getType()).thenReturn(Material.DIAMOND_ORE);

        when(nmsAdapter.getBlockStateId(Material.DIAMOND_ORE)).thenReturn(100);
        when(materialSet.isHidden(100)).thenReturn(true);
        when(exposureChecker.isAirExposed(world, 1, 2, 3)).thenReturn(false);

        assertTrue(provider.shouldObfuscate(blockState, world, 1, 2, 3));

        World.Environment env = World.Environment.NORMAL;
        when(world.getEnvironment()).thenReturn(env);
        when(obfuscationEngine.getDeepslateBelowY()).thenReturn(0);
        when(materialSet.getReplacement(10, 0, "NORMAL")).thenReturn(200);

        BlockData blockData = mock(BlockData.class);
        when(blockData.getMaterial()).thenReturn(Material.STONE);
        when(nmsAdapter.getBlockDataFromId(200)).thenReturn(blockData);

        assertEquals(Material.STONE, provider.getReplacementBlock(world, 10));
    }

    @Test
    void testIsObfuscatedReturnsCorrectValue() {
        AntiXrayAPIImpl api = new AntiXrayAPIImpl(plugin);
        World world = mock(World.class);
        WorldConfig worldConfig = mock(WorldConfig.class);

        when(configurationManager.getWorldConfig(world)).thenReturn(worldConfig);
        when(worldConfig.isEnabled()).thenReturn(true);

        Location loc = new Location(world, 10, 20, 30);
        when(nmsAdapter.getBlockStateAt(world, 10, 20, 30)).thenReturn(100);
        when(materialSet.isHidden(100)).thenReturn(true);
        when(exposureChecker.isAirExposed(world, 10, 20, 30)).thenReturn(false);

        // Case 1: Enabled, Hidden, Not Air-exposed -> True
        assertTrue(api.isObfuscated(loc));

        // Case 2: World is null -> False
        Location locNullWorld = new Location(null, 10, 20, 30);
        assertFalse(api.isObfuscated(locNullWorld));

        // Case 3: World config not enabled -> False
        when(worldConfig.isEnabled()).thenReturn(false);
        assertFalse(api.isObfuscated(loc));
        when(worldConfig.isEnabled()).thenReturn(true);

        // Case 4: Block state is -1 -> False
        when(nmsAdapter.getBlockStateAt(world, 10, 20, 30)).thenReturn(-1);
        assertFalse(api.isObfuscated(loc));
        when(nmsAdapter.getBlockStateAt(world, 10, 20, 30)).thenReturn(100);

        // Case 5: Block is not hidden -> False
        when(materialSet.isHidden(100)).thenReturn(false);
        assertFalse(api.isObfuscated(loc));
        when(materialSet.isHidden(100)).thenReturn(true);

        // Case 6: Block is air-exposed -> False
        when(exposureChecker.isAirExposed(world, 10, 20, 30)).thenReturn(true);
        assertFalse(api.isObfuscated(loc));
    }

    @Test
    void testGetEngineModeReturnsWorldConfig() {
        AntiXrayAPIImpl api = new AntiXrayAPIImpl(plugin);
        World world = mock(World.class);
        WorldConfig worldConfig = mock(WorldConfig.class);

        when(configurationManager.getWorldConfig(world)).thenReturn(worldConfig);

        // Case 1: World config enabled, Mode 1 -> 1
        when(worldConfig.isEnabled()).thenReturn(true);
        when(worldConfig.getEngineMode()).thenReturn(ObfuscationMode.MODE_1);
        assertEquals(1, api.getEngineMode(world));

        // Case 2: World config enabled, Mode 2 -> 2
        when(worldConfig.getEngineMode()).thenReturn(ObfuscationMode.MODE_2);
        assertEquals(2, api.getEngineMode(world));

        // Case 3: World config enabled, Mode 3 -> 3
        when(worldConfig.getEngineMode()).thenReturn(ObfuscationMode.MODE_3);
        assertEquals(3, api.getEngineMode(world));

        // Case 4: World config is disabled -> -1
        when(worldConfig.isEnabled()).thenReturn(false);
        assertEquals(-1, api.getEngineMode(world));

        // Case 5: World config is null -> -1
        when(configurationManager.getWorldConfig(world)).thenReturn(null);
        assertEquals(-1, api.getEngineMode(world));

        // Case 6: World is null -> -1
        assertEquals(-1, api.getEngineMode(null));
    }

    @Test
    void testRegisterCustomHiddenBlockAddsToSet() {
        AntiXrayAPIImpl api = new AntiXrayAPIImpl(plugin);

        api.registerCustomHiddenBlock(Material.DIAMOND_ORE);
        verify(materialSet).registerCustomHiddenBlock(nmsAdapter, Material.DIAMOND_ORE);

        api.unregisterCustomHiddenBlock(Material.DIAMOND_ORE);
        verify(materialSet).unregisterCustomHiddenBlock(nmsAdapter, Material.DIAMOND_ORE);
    }

    @Test
    void testAntiXrayAPIImpl_customObfuscationProvider() {
        AntiXrayAPIImpl api = new AntiXrayAPIImpl(plugin);
        World world = mock(World.class);
        UUID worldUID = UUID.randomUUID();
        when(world.getUID()).thenReturn(worldUID);

        ObfuscationProvider customProvider = mock(ObfuscationProvider.class);

        assertTrue(api.getObfuscationProvider(world) instanceof DefaultObfuscationProvider);

        api.setObfuscationProvider(world, customProvider);
        assertEquals(customProvider, api.getObfuscationProvider(world));

        api.setObfuscationProvider(world, null);
        assertTrue(api.getObfuscationProvider(world) instanceof DefaultObfuscationProvider);
    }
}
