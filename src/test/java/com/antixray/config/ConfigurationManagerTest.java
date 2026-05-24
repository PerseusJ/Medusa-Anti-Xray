package com.antixray.config;

import com.antixray.engine.ObfuscationMode;
import com.antixray.nms.NmsAdapter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ConfigurationManagerTest {

    private static final int STONE_ID = 1;
    private static final int DEEPSLATE_ID = 2;
    private static final int NETHERRACK_ID = 3;
    private static final int END_STONE_ID = 4;
    private static final int DIAMOND_ORE_ID = 50;
    private static final int IRON_ORE_ID = 51;

    private JavaPlugin plugin;
    private NmsAdapter nmsAdapter;
    private Logger logger;
    private MockedStatic<Bukkit> bukkitMock;
    private MockedStatic<Material> materialMock;

    @BeforeEach
    void setUp() {
        logger = mock(Logger.class);
        plugin = mock(JavaPlugin.class);
        when(plugin.getLogger()).thenReturn(logger);

        nmsAdapter = mock(NmsAdapter.class);
        when(nmsAdapter.getBlockStateId(Material.STONE)).thenReturn(STONE_ID);
        when(nmsAdapter.getBlockStateId(Material.DEEPSLATE)).thenReturn(DEEPSLATE_ID);
        when(nmsAdapter.getBlockStateId(Material.NETHERRACK)).thenReturn(NETHERRACK_ID);
        when(nmsAdapter.getBlockStateId(Material.END_STONE)).thenReturn(END_STONE_ID);
        when(nmsAdapter.getBlockStateId(Material.DIAMOND_ORE)).thenReturn(DIAMOND_ORE_ID);
        when(nmsAdapter.getBlockStateId(Material.IRON_ORE)).thenReturn(IRON_ORE_ID);

        Server server = mock(Server.class);
        lenient().when(server.getName()).thenReturn("TestServer");
        lenient().when(server.getVersion()).thenReturn("1.21");
        lenient().when(server.getBukkitVersion()).thenReturn("1.21-R0.1-SNAPSHOT");
        bukkitMock = Mockito.mockStatic(Bukkit.class);
        bukkitMock.when(Bukkit::getServer).thenReturn(server);

        materialMock = Mockito.mockStatic(Material.class);
        Material stoneMock = mockMaterial("STONE", true);
        Material deepslateMock = mockMaterial("DEEPSLATE", true);
        Material netherrackMock = mockMaterial("NETHERRACK", true);
        Material endStoneMock = mockMaterial("END_STONE", true);
        Material diamondOreMock = mockMaterial("DIAMOND_ORE", true);
        Material ironOreMock = mockMaterial("IRON_ORE", true);

        materialMock.when(() -> Material.matchMaterial("stone")).thenReturn(stoneMock);
        materialMock.when(() -> Material.matchMaterial("STONE")).thenReturn(stoneMock);
        materialMock.when(() -> Material.matchMaterial("deepslate")).thenReturn(deepslateMock);
        materialMock.when(() -> Material.matchMaterial("DEEPSLATE")).thenReturn(deepslateMock);
        materialMock.when(() -> Material.matchMaterial("netherrack")).thenReturn(netherrackMock);
        materialMock.when(() -> Material.matchMaterial("NETHERRACK")).thenReturn(netherrackMock);
        materialMock.when(() -> Material.matchMaterial("end_stone")).thenReturn(endStoneMock);
        materialMock.when(() -> Material.matchMaterial("END_STONE")).thenReturn(endStoneMock);
        materialMock.when(() -> Material.matchMaterial("diamond_ore")).thenReturn(diamondOreMock);
        materialMock.when(() -> Material.matchMaterial("DIAMOND_ORE")).thenReturn(diamondOreMock);
        materialMock.when(() -> Material.matchMaterial("iron_ore")).thenReturn(ironOreMock);
        materialMock.when(() -> Material.matchMaterial("IRON_ORE")).thenReturn(ironOreMock);

        when(nmsAdapter.getBlockStateId(stoneMock)).thenReturn(STONE_ID);
        when(nmsAdapter.getBlockStateId(deepslateMock)).thenReturn(DEEPSLATE_ID);
        when(nmsAdapter.getBlockStateId(netherrackMock)).thenReturn(NETHERRACK_ID);
        when(nmsAdapter.getBlockStateId(endStoneMock)).thenReturn(END_STONE_ID);
        when(nmsAdapter.getBlockStateId(diamondOreMock)).thenReturn(DIAMOND_ORE_ID);
        when(nmsAdapter.getBlockStateId(ironOreMock)).thenReturn(IRON_ORE_ID);
    }

    private Material mockMaterial(String name, boolean isBlock) {
        Material mat = mock(Material.class);
        lenient().when(mat.name()).thenReturn(name);
        lenient().when(mat.isBlock()).thenReturn(isBlock);
        return mat;
    }

    @AfterEach
    void tearDown() {
        if (materialMock != null) {
            materialMock.close();
        }
        if (bukkitMock != null) {
            bukkitMock.close();
        }
    }

    private YamlConfiguration createDefaultConfig() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("enabled", true);
        config.set("engine-mode", 3);
        config.set("max-block-height", 64);
        config.set("fake-ore-chance", 0.07);
        config.set("lava-obscures", true);
        config.set("leaves-are-transparent", true);
        config.set("bypass-permission", "antixray.bypass");
        config.set("hidden-blocks", List.of("diamond_ore"));
        config.set("replacement-blocks.overworld.default", "stone");
        config.set("replacement-blocks.overworld.below-y", "deepslate");
        config.set("replacement-blocks.nether", "netherrack");
        config.set("replacement-blocks.end", "end_stone");
        config.set("deepslate-below-y", 0);
        return config;
    }

    @Test
    void globalConfigLoadsSuccessfully() {
        YamlConfiguration config = createDefaultConfig();
        when(plugin.getConfig()).thenReturn(config);

        ConfigurationManager manager = new ConfigurationManager(plugin, nmsAdapter);
        manager.load();

        WorldConfig global = manager.getGlobalConfig();
        assertNotNull(global);
        assertTrue(global.isEnabled());
        assertEquals(ObfuscationMode.MODE_3, global.getEngineMode());
        assertEquals(64, global.getMaxBlockHeight());
        assertEquals(0.07, global.getFakeOreChance(), 0.001);
    }

    @Test
    void globalConfig_correctReplacementIds() {
        YamlConfiguration config = createDefaultConfig();
        when(plugin.getConfig()).thenReturn(config);

        ConfigurationManager manager = new ConfigurationManager(plugin, nmsAdapter);
        manager.load();

        WorldConfig global = manager.getGlobalConfig();
        assertEquals(STONE_ID, global.getReplacementOverworld());
        assertEquals(DEEPSLATE_ID, global.getReplacementOverworldDeep());
        assertEquals(NETHERRACK_ID, global.getReplacementNether());
        assertEquals(END_STONE_ID, global.getReplacementEnd());
    }

    @Test
    void perWorldOverride_mergesWithGlobal() {
        YamlConfiguration config = createDefaultConfig();
        config.set("worlds.nether_world.engine-mode", 1);
        config.set("worlds.nether_world.max-block-height", 128);

        when(plugin.getConfig()).thenReturn(config);

        ConfigurationManager manager = new ConfigurationManager(plugin, nmsAdapter);
        manager.load();

        World netherWorld = mock(World.class);
        when(netherWorld.getName()).thenReturn("nether_world");

        WorldConfig netherConfig = manager.getWorldConfig(netherWorld);
        assertEquals(ObfuscationMode.MODE_1, netherConfig.getEngineMode());
        assertEquals(128, netherConfig.getMaxBlockHeight());
        assertEquals(0.07, netherConfig.getFakeOreChance(), 0.001);
        assertTrue(netherConfig.isLavaObscures());
    }

    @Test
    void perWorldOverride_caseInsensitive() {
        YamlConfiguration config = createDefaultConfig();
        config.set("worlds.MyWorld.engine-mode", 2);

        when(plugin.getConfig()).thenReturn(config);

        ConfigurationManager manager = new ConfigurationManager(plugin, nmsAdapter);
        manager.load();

        World myWorld = mock(World.class);
        when(myWorld.getName()).thenReturn("myworld");

        WorldConfig worldConfig = manager.getWorldConfig(myWorld);
        assertEquals(ObfuscationMode.MODE_2, worldConfig.getEngineMode());
    }

    @Test
    void missingWorldFallback_globalConfig() {
        YamlConfiguration config = createDefaultConfig();
        when(plugin.getConfig()).thenReturn(config);

        ConfigurationManager manager = new ConfigurationManager(plugin, nmsAdapter);
        manager.load();

        World unknownWorld = mock(World.class);
        when(unknownWorld.getName()).thenReturn("unknown_world");

        WorldConfig worldConfig = manager.getWorldConfig(unknownWorld);
        assertEquals(ObfuscationMode.MODE_3, worldConfig.getEngineMode());
        assertEquals(manager.getGlobalConfig().getMaxBlockHeight(), worldConfig.getMaxBlockHeight());
    }

    @Test
    void missingOptionalFields_useDefaults() {
        YamlConfiguration config = new YamlConfiguration();
        when(plugin.getConfig()).thenReturn(config);

        ConfigurationManager manager = new ConfigurationManager(plugin, nmsAdapter);
        manager.load();

        WorldConfig global = manager.getGlobalConfig();
        assertTrue(global.isEnabled());
        assertEquals(ObfuscationMode.MODE_3, global.getEngineMode());
        assertEquals(64, global.getMaxBlockHeight());
        assertEquals(0.07, global.getFakeOreChance(), 0.001);
        assertTrue(global.isLavaObscures());
        assertTrue(global.isLeavesAreTransparent());
        assertEquals("antixray.bypass", global.getBypassPermission());
    }

    @Test
    void configHash_computedOnLoad() {
        YamlConfiguration config = createDefaultConfig();
        when(plugin.getConfig()).thenReturn(config);

        ConfigurationManager manager = new ConfigurationManager(plugin, nmsAdapter);
        manager.load();

        int hash = manager.getConfigHash();
        assertNotEquals(0, hash);
    }

    @Test
    void configHash_consistentAcrossLoads() {
        YamlConfiguration config = createDefaultConfig();
        when(plugin.getConfig()).thenReturn(config);

        ConfigurationManager manager = new ConfigurationManager(plugin, nmsAdapter);
        manager.load();
        int hash1 = manager.getConfigHash();

        manager.load();
        int hash2 = manager.getConfigHash();

        assertEquals(hash1, hash2);
    }

    @Test
    void deepMerge_nestedKeys() {
        YamlConfiguration config = createDefaultConfig();
        config.set("worlds.custom_world.lava-obscures", false);
        config.set("worlds.custom_world.fake-ore-chance", 0.5);

        when(plugin.getConfig()).thenReturn(config);

        ConfigurationManager manager = new ConfigurationManager(plugin, nmsAdapter);
        manager.load();

        World customWorld = mock(World.class);
        when(customWorld.getName()).thenReturn("custom_world");

        WorldConfig worldConfig = manager.getWorldConfig(customWorld);
        assertFalse(worldConfig.isLavaObscures());
        assertEquals(0.5, worldConfig.getFakeOreChance(), 0.001);
        assertEquals(ObfuscationMode.MODE_3, worldConfig.getEngineMode());
        assertEquals(64, worldConfig.getMaxBlockHeight());
    }

    @Test
    void perWorldHiddenBlocks_overrideGlobal() {
        YamlConfiguration config = createDefaultConfig();
        config.set("worlds.special_world.hidden-blocks", List.of("diamond_ore", "iron_ore"));

        when(plugin.getConfig()).thenReturn(config);

        ConfigurationManager manager = new ConfigurationManager(plugin, nmsAdapter);
        manager.load();

        World specialWorld = mock(World.class);
        when(specialWorld.getName()).thenReturn("special_world");

        WorldConfig worldConfig = manager.getWorldConfig(specialWorld);
        assertTrue(worldConfig.getHiddenBlocks().contains(DIAMOND_ORE_ID));
        assertTrue(worldConfig.getHiddenBlocks().contains(IRON_ORE_ID));
    }

    @Test
    void getWorldConfig_nullWorldName_usesGlobal() {
        YamlConfiguration config = createDefaultConfig();
        when(plugin.getConfig()).thenReturn(config);

        ConfigurationManager manager = new ConfigurationManager(plugin, nmsAdapter);
        manager.load();

        World world = mock(World.class);
        when(world.getName()).thenReturn("nonexistent");

        WorldConfig result = manager.getWorldConfig(world);
        assertEquals(manager.getGlobalConfig(), result);
    }

    @Test
    void engineModeParsed_integer() {
        YamlConfiguration config = createDefaultConfig();
        config.set("engine-mode", 1);
        when(plugin.getConfig()).thenReturn(config);

        ConfigurationManager manager = new ConfigurationManager(plugin, nmsAdapter);
        manager.load();

        assertEquals(ObfuscationMode.MODE_1, manager.getGlobalConfig().getEngineMode());
    }

    @Test
    void engineModeParsed_string() {
        YamlConfiguration config = createDefaultConfig();
        config.set("engine-mode", "2");
        when(plugin.getConfig()).thenReturn(config);

        ConfigurationManager manager = new ConfigurationManager(plugin, nmsAdapter);
        manager.load();

        assertEquals(ObfuscationMode.MODE_2, manager.getGlobalConfig().getEngineMode());
    }

    @Test
    void reload_refreshesConfig() {
        YamlConfiguration config = createDefaultConfig();
        when(plugin.getConfig()).thenReturn(config);

        ConfigurationManager manager = new ConfigurationManager(plugin, nmsAdapter);
        manager.load();

        assertEquals(ObfuscationMode.MODE_3, manager.getGlobalConfig().getEngineMode());

        config.set("engine-mode", 1);
        manager.reload();

        assertEquals(ObfuscationMode.MODE_1, manager.getGlobalConfig().getEngineMode());
    }

    @Test
    void globalConfigHasCorrectDeepslateBelowY() {
        YamlConfiguration config = createDefaultConfig();
        config.set("deepslate-below-y", -10);
        when(plugin.getConfig()).thenReturn(config);

        ConfigurationManager manager = new ConfigurationManager(plugin, nmsAdapter);
        manager.load();

        assertEquals(-10, manager.getGlobalConfig().getDeepslateBelowY());
    }

    @Test
    void worldConfigDeepslateBelowY_defaultFromGlobal() {
        YamlConfiguration config = createDefaultConfig();
        config.set("deepslate-below-y", -20);
        when(plugin.getConfig()).thenReturn(config);

        ConfigurationManager manager = new ConfigurationManager(plugin, nmsAdapter);
        manager.load();

        World someWorld = mock(World.class);
        when(someWorld.getName()).thenReturn("some_world");

        WorldConfig worldConfig = manager.getWorldConfig(someWorld);
        assertEquals(-20, worldConfig.getDeepslateBelowY());
    }

    @Test
    void worldOverrideReplacementBlocks_mergeWithGlobal() {
        YamlConfiguration config = createDefaultConfig();
        config.set("worlds.nether_world.replacement-blocks.nether", "netherrack");

        when(plugin.getConfig()).thenReturn(config);

        ConfigurationManager manager = new ConfigurationManager(plugin, nmsAdapter);
        manager.load();

        World netherWorld = mock(World.class);
        when(netherWorld.getName()).thenReturn("nether_world");

        WorldConfig worldConfig = manager.getWorldConfig(netherWorld);
        assertEquals(NETHERRACK_ID, worldConfig.getReplacementNether());
        assertEquals(STONE_ID, worldConfig.getReplacementOverworld());
    }

    @Test
    void invalidMaterialInHiddenBlocks_skipped() {
        YamlConfiguration config = createDefaultConfig();
        config.set("hidden-blocks", List.of("diamond_ore", "invalid_material_xyz"));

        materialMock.when(() -> Material.matchMaterial(eq("invalid_material_xyz"))).thenReturn(null);
        materialMock.when(() -> Material.matchMaterial(eq("INVALID_MATERIAL_XYZ"))).thenReturn(null);

        when(plugin.getConfig()).thenReturn(config);

        ConfigurationManager manager = new ConfigurationManager(plugin, nmsAdapter);
        manager.load();

        WorldConfig global = manager.getGlobalConfig();
        assertTrue(global.getHiddenBlocks().contains(DIAMOND_ORE_ID));
        assertEquals(1, global.getHiddenBlocks().size());
    }

    @Test
    void reload_hiddenBlocksChange_firesConfigChangeListener() {
        YamlConfiguration config = createDefaultConfig();
        when(plugin.getConfig()).thenReturn(config);

        ConfigurationManager manager = new ConfigurationManager(plugin, nmsAdapter);
        manager.load();

        java.util.concurrent.atomic.AtomicBoolean listenerFired =
                new java.util.concurrent.atomic.AtomicBoolean(false);
        manager.addConfigChangeListener(() -> listenerFired.set(true));

        config.set("hidden-blocks", List.of("diamond_ore", "iron_ore"));
        manager.reload();

        assertTrue(listenerFired.get(),
                "ConfigChangeListener must fire when hidden-blocks change");
    }

    @Test
    void reload_noObfuscationChange_doesNotFireConfigChangeListener() {
        YamlConfiguration config = createDefaultConfig();
        when(plugin.getConfig()).thenReturn(config);

        ConfigurationManager manager = new ConfigurationManager(plugin, nmsAdapter);
        manager.load();

        java.util.concurrent.atomic.AtomicBoolean listenerFired =
                new java.util.concurrent.atomic.AtomicBoolean(false);
        manager.addConfigChangeListener(() -> listenerFired.set(true));

        config.set("fake-ore-chance", 0.99);
        manager.reload();

        assertFalse(listenerFired.get(),
                "ConfigChangeListener must NOT fire for non-obfuscation config changes");
    }

    @Test
    void reload_engineModeChange_firesConfigChangeListener() {
        YamlConfiguration config = createDefaultConfig();
        when(plugin.getConfig()).thenReturn(config);

        ConfigurationManager manager = new ConfigurationManager(plugin, nmsAdapter);
        manager.load();

        java.util.concurrent.atomic.AtomicBoolean listenerFired =
                new java.util.concurrent.atomic.AtomicBoolean(false);
        manager.addConfigChangeListener(() -> listenerFired.set(true));

        config.set("engine-mode", 1);
        manager.reload();

        assertTrue(listenerFired.get(),
                "ConfigChangeListener must fire when engine-mode changes");
    }

    @Test
    void statisticalDetectionConfigLoadsSuccessfully() {
        YamlConfiguration config = createDefaultConfig();
        config.set("detection.enabled", true);
        config.set("detection.minimum-sample-size", 150);
        config.set("detection.grace-period-minutes", 45);
        config.set("detection.thresholds.ore-to-stone-ratio.warning", 0.12);
        config.set("detection.thresholds.ore-to-stone-ratio.critical", 0.22);
        config.set("detection.thresholds.diamond-per-hour.warning", 15.0);
        config.set("detection.thresholds.diamond-per-hour.critical", 25.0);
        config.set("detection.actions.warning", List.of("log"));
        config.set("detection.actions.critical", List.of("log", "command:ban {player}"));
        config.set("detection.notifications.in-game", false);
        config.set("detection.notifications.console", true);

        when(plugin.getConfig()).thenReturn(config);

        ConfigurationManager manager = new ConfigurationManager(plugin, nmsAdapter);
        manager.load();

        assertTrue(manager.isDetectionEnabled());
        assertEquals(150, manager.getDetectionMinimumSampleSize());
        assertEquals(45, manager.getDetectionGracePeriodMinutes());
        assertEquals(0.12, manager.getDetectionThresholds().oreToStoneRatioWarning, 0.001);
        assertEquals(0.22, manager.getDetectionThresholds().oreToStoneRatioCritical, 0.001);
        assertEquals(15.0, manager.getDetectionThresholds().diamondPerHourWarning, 0.001);
        assertEquals(25.0, manager.getDetectionThresholds().diamondPerHourCritical, 0.001);
        assertEquals(List.of("log"), manager.getWarningActions());
        assertEquals(List.of("log", "command:ban {player}"), manager.getCriticalActions());
        assertFalse(manager.isNotifyInGame());
        assertTrue(manager.isNotifyConsole());
    }
}
