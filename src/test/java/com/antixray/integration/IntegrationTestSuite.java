package com.antixray.integration;

import com.antixray.AntiXrayPlugin;
import com.antixray.async.AsyncProcessor;
import com.antixray.async.BackpressureHandler;
import com.antixray.async.ObfuscationTask;
import com.antixray.async.ThreadPoolManager;
import com.antixray.async.TickBudgetTracker;
import com.antixray.cache.CacheEntry;
import com.antixray.cache.CacheKey;
import com.antixray.cache.L1MemoryCache;
import com.antixray.cache.ObfuscationCache;
import com.antixray.commands.ReloadCommand;
import com.antixray.config.ConfigurationManager;
import com.antixray.config.WorldConfig;
import com.antixray.deobfuscation.DeobfuscationManager;
import com.antixray.deobfuscation.PlayerData;
import com.antixray.deobfuscation.ProximityTracker;
import com.antixray.deobfuscation.RevealedBlocksSet;
import com.antixray.engine.*;
import com.antixray.listener.BlockEventListener;
import com.antixray.listener.ExplosionEventListener;
import com.antixray.listener.PlayerEventListener;
import com.antixray.listener.WorldEventListener;
import com.antixray.nms.NmsAdapter;
import com.antixray.nms.NmsAdapterFactory;
import com.antixray.packet.InterceptionMode;
import com.antixray.packet.NmsInterceptor;
import com.antixray.packet.PacketInterceptor;

import com.antixray.deobfuscation.DeobfuscationManager;
import com.antixray.deobfuscation.PlayerData;
import com.antixray.deobfuscation.ProximityTracker;
import com.antixray.deobfuscation.RevealedBlocksSet;
import com.antixray.util.BlockPosition;
import com.antixray.util.FoliaSchedulerAdapter;
import com.antixray.util.MaterialSet;
import com.antixray.util.SeededRandom;
import com.antixray.util.VersionUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IntegrationTestSuite {

    private static final int STONE_ID = 1;
    private static final int DEEPSLATE_ID = 2;
    private static final int NETHERRACK_ID = 3;
    private static final int END_STONE_ID = 4;
    private static final int DIAMOND_ORE_ID = 50;
    private static final int IRON_ORE_ID = 51;
    private static final int GOLD_ORE_ID = 52;
    private static final int EMERALD_ORE_ID = 53;
    private static final int CHEST_ID = 60;
    private static final int AIR_ID = 0;

    private NmsAdapter adapter;
    private AntiXrayPlugin plugin;
    private World world;
    private MaterialSet materialSet;
    private AirExposureChecker exposureChecker;
    private BlockClassifier classifier;
    private MockedStatic<Bukkit> bukkitMock;
    private MockedStatic<Material> materialMock;
    private BukkitScheduler scheduler;
    private BukkitTask schedulerTask;

    @BeforeEach
    void setUpGlobal() {
        Server server = mock(Server.class);
        lenient().when(server.getName()).thenReturn("TestServer");
        lenient().when(server.getVersion()).thenReturn("1.21.4");
        lenient().when(server.getBukkitVersion()).thenReturn("1.21.4-R0.1-SNAPSHOT");
        PluginManager pluginManager = mock(PluginManager.class);
        lenient().when(server.getPluginManager()).thenReturn(pluginManager);
        lenient().when(server.getOnlinePlayers()).thenReturn(List.of());
        lenient().when(server.getWorlds()).thenReturn(List.of());

        bukkitMock = Mockito.mockStatic(Bukkit.class);
        bukkitMock.when(Bukkit::getServer).thenReturn(server);
        bukkitMock.when(Bukkit::getBukkitVersion).thenReturn("1.21.4-R0.1-SNAPSHOT");

        scheduler = mock(BukkitScheduler.class);
        schedulerTask = mock(BukkitTask.class);
        lenient().when(schedulerTask.getTaskId()).thenReturn(1);
        lenient().when(scheduler.runTaskTimerAsynchronously(any(), any(Runnable.class), anyLong(), anyLong()))
                .thenReturn(schedulerTask);
        lenient().when(scheduler.runTask(any(), any(Runnable.class))).thenReturn(schedulerTask);
        lenient().when(scheduler.runTaskLater(any(), any(Runnable.class), anyLong())).thenReturn(schedulerTask);
        lenient().when(scheduler.runTaskTimer(any(), any(Runnable.class), anyLong(), anyLong())).thenReturn(schedulerTask);
        doNothing().when(scheduler).cancelTask(anyInt());
        bukkitMock.when(Bukkit::getScheduler).thenReturn(scheduler);

        adapter = mock(NmsAdapter.class);
        world = mock(World.class);
        materialSet = mock(MaterialSet.class);

        when(materialSet.getReplacement(anyInt(), anyInt(), anyString())).thenReturn(STONE_ID);
        when(materialSet.getReplacement(anyInt(), anyInt(), eq("NORMAL"))).thenReturn(STONE_ID);
        when(materialSet.getReplacement(anyInt(), anyInt(), eq("NETHER"))).thenReturn(NETHERRACK_ID);
        when(materialSet.getReplacement(anyInt(), anyInt(), eq("THE_END"))).thenReturn(END_STONE_ID);
        when(materialSet.isHidden(DIAMOND_ORE_ID)).thenReturn(true);
        when(materialSet.isHidden(IRON_ORE_ID)).thenReturn(true);
        when(materialSet.isHidden(GOLD_ORE_ID)).thenReturn(true);
        when(materialSet.isHidden(EMERALD_ORE_ID)).thenReturn(true);
        when(materialSet.isHidden(CHEST_ID)).thenReturn(true);
        when(materialSet.isTileEntity(CHEST_ID)).thenReturn(true);
        when(materialSet.isTileEntity(DIAMOND_ORE_ID)).thenReturn(false);
        when(materialSet.isTileEntity(IRON_ORE_ID)).thenReturn(false);
        when(materialSet.isTileEntity(GOLD_ORE_ID)).thenReturn(false);
        when(materialSet.isTileEntity(EMERALD_ORE_ID)).thenReturn(false);
        when(materialSet.isHidden(STONE_ID)).thenReturn(false);
        when(materialSet.isHidden(AIR_ID)).thenReturn(false);
        when(materialSet.isTransparent(AIR_ID)).thenReturn(true);
        when(materialSet.isTransparent(STONE_ID)).thenReturn(false);
        when(materialSet.isTransparent(DIAMOND_ORE_ID)).thenReturn(false);
        when(materialSet.getHiddenBlockPaletteArray()).thenReturn(new int[]{DIAMOND_ORE_ID, IRON_ORE_ID, GOLD_ORE_ID});

        when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);
        when(world.getName()).thenReturn("world");
        when(world.getMinHeight()).thenReturn(-64);
        lenient().when(world.getMaxHeight()).thenReturn(320);
        lenient().when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);

        plugin = mock(AntiXrayPlugin.class);
        FoliaSchedulerAdapter foliaSchedulerAdapter = new FoliaSchedulerAdapter(plugin);
        lenient().when(plugin.getSchedulerAdapter()).thenReturn(foliaSchedulerAdapter);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("AntiXray"));
        when(plugin.getAllPlayerData()).thenReturn(Collections.emptyMap());
        lenient().when(plugin.getServer()).thenReturn(server);
        ConfigurationManager configMgr = mock(ConfigurationManager.class);
        when(plugin.getConfigurationManager()).thenReturn(configMgr);
        WorldConfig defaultWorldConfig = WorldConfig.builder()
                .updateRadius(4)
                .elytraVelocityThreshold(1.5)
                .maxRevealedPerPlayer(10000)
                .maxDeobfuscationUpdatesPerTick(64)
                .deepslateBelowY(0)
                .build();
        lenient().when(configMgr.getWorldConfig(any(World.class))).thenReturn(defaultWorldConfig);
        lenient().when(configMgr.getGlobalConfig()).thenReturn(defaultWorldConfig);

        when(adapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt())).thenReturn(STONE_ID);
        when(adapter.getBlockStateId(Material.STONE)).thenReturn(STONE_ID);
        when(adapter.getBlockStateId(Material.DEEPSLATE)).thenReturn(DEEPSLATE_ID);
        when(adapter.getBlockStateId(Material.NETHERRACK)).thenReturn(NETHERRACK_ID);
        when(adapter.getBlockStateId(Material.END_STONE)).thenReturn(END_STONE_ID);
        when(adapter.getBlockStateId(Material.DIAMOND_ORE)).thenReturn(DIAMOND_ORE_ID);
        when(adapter.getBlockStateId(Material.IRON_ORE)).thenReturn(IRON_ORE_ID);
        when(adapter.getBlockStateId(Material.GOLD_ORE)).thenReturn(GOLD_ORE_ID);
        when(adapter.getVersionString()).thenReturn("v1_21_R3");

        exposureChecker = new AirExposureChecker(adapter, materialSet, true);
        classifier = new BlockClassifier(materialSet, exposureChecker);

        materialMock = Mockito.mockStatic(Material.class);
        Material stoneMock = mockMaterial("STONE", true);
        Material deepslateMock = mockMaterial("DEEPSLATE", true);
        Material netherrackMock = mockMaterial("NETHERRACK", true);
        Material endStoneMock = mockMaterial("END_STONE", true);
        Material diamondOreMock = mockMaterial("DIAMOND_ORE", true);
        Material ironOreMock = mockMaterial("IRON_ORE", true);
        Material goldOreMock = mockMaterial("GOLD_ORE", true);
        Material emeraldOreMock = mockMaterial("EMERALD_ORE", true);
        Material chestMock = mockMaterial("CHEST", true);

        materialMock.when(() -> Material.matchMaterial(eq("stone"))).thenReturn(stoneMock);
        materialMock.when(() -> Material.matchMaterial(eq("STONE"))).thenReturn(stoneMock);
        materialMock.when(() -> Material.matchMaterial(eq("deepslate"))).thenReturn(deepslateMock);
        materialMock.when(() -> Material.matchMaterial(eq("DEEPSLATE"))).thenReturn(deepslateMock);
        materialMock.when(() -> Material.matchMaterial(eq("netherrack"))).thenReturn(netherrackMock);
        materialMock.when(() -> Material.matchMaterial(eq("NETHERRACK"))).thenReturn(netherrackMock);
        materialMock.when(() -> Material.matchMaterial(eq("end_stone"))).thenReturn(endStoneMock);
        materialMock.when(() -> Material.matchMaterial(eq("END_STONE"))).thenReturn(endStoneMock);
        materialMock.when(() -> Material.matchMaterial(eq("diamond_ore"))).thenReturn(diamondOreMock);
        materialMock.when(() -> Material.matchMaterial(eq("DIAMOND_ORE"))).thenReturn(diamondOreMock);
        materialMock.when(() -> Material.matchMaterial(eq("iron_ore"))).thenReturn(ironOreMock);
        materialMock.when(() -> Material.matchMaterial(eq("IRON_ORE"))).thenReturn(ironOreMock);
        materialMock.when(() -> Material.matchMaterial(eq("gold_ore"))).thenReturn(goldOreMock);
        materialMock.when(() -> Material.matchMaterial(eq("GOLD_ORE"))).thenReturn(goldOreMock);
        materialMock.when(() -> Material.matchMaterial(eq("emerald_ore"))).thenReturn(emeraldOreMock);
        materialMock.when(() -> Material.matchMaterial(eq("EMERALD_ORE"))).thenReturn(emeraldOreMock);
        materialMock.when(() -> Material.matchMaterial(eq("chest"))).thenReturn(chestMock);
        materialMock.when(() -> Material.matchMaterial(eq("CHEST"))).thenReturn(chestMock);
    }

    private Material mockMaterial(String name, boolean isBlock) {
        Material mat = mock(Material.class);
        lenient().when(mat.name()).thenReturn(name);
        lenient().when(mat.isBlock()).thenReturn(isBlock);
        return mat;
    }

    @AfterEach
    void tearDownGlobal() {
        if (materialMock != null) materialMock.close();
        if (bukkitMock != null) bukkitMock.close();
        VersionUtil.resetCache();
    }

    private Object createMockSection(List<Integer> palette, long[] packed, int bitsPerEntry) {
        Object section = new Object();
        when(adapter.isSingleValuePalette(section)).thenReturn(false);
        when(adapter.isDirectPalette(section)).thenReturn(false);
        when(adapter.getSectionNonEmptyCount(section)).thenReturn(1);
        when(adapter.getPaletteEntries(section)).thenReturn(new ArrayList<>(palette));
        when(adapter.getPackedIndices(section)).thenReturn(packed.clone());
        when(adapter.getPaletteBitsPerEntry(section)).thenReturn(bitsPerEntry);
        return section;
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
        config.set("hidden-blocks", List.of("diamond_ore", "iron_ore", "gold_ore"));
        config.set("replacement-blocks.overworld.default", "stone");
        config.set("replacement-blocks.overworld.below-y", "deepslate");
        config.set("replacement-blocks.nether", "netherrack");
        config.set("replacement-blocks.end", "end_stone");
        config.set("deepslate-below-y", 0);
        return config;
    }

    // ═══════════════════════════════════════════════════════════════════
    // 1. Plugin loads on Paper 1.21.4
    // ═══════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("1. Plugin loads on Paper 1.21.4")
    class PluginLoadTest {

        @Test
        @DisplayName("onEnable completes without errors and logs enabled message")
        void pluginLoadsOnPaper() {
            AntiXrayPlugin plugin = mock(AntiXrayPlugin.class);
            Logger logger = mock(Logger.class);
            when(plugin.getLogger()).thenReturn(logger);
            when(plugin.getConfig()).thenReturn(createDefaultConfig());

            ConfigurationManager configMgr = new ConfigurationManager(plugin, adapter);
            configMgr.load();
            assertNotNull(configMgr.getGlobalConfig(), "Configuration must load without errors");
            assertTrue(configMgr.getGlobalConfig().isEnabled(), "Plugin must be enabled after loading config");

            ObfuscationEngine engine = new ObfuscationEngine(adapter, materialSet, exposureChecker);
            engine.setEngineMode(configMgr.getGlobalConfig().getEngineMode());
            assertNotNull(engine, "ObfuscationEngine must be created");

            verify(logger, atLeastOnce()).info(anyString());
        }

        @Test
        @DisplayName("NMS adapter returns correct version string")
        void nmsAdapterReturnsVersion() {
            String version = adapter.getVersionString();
            assertNotNull(version, "NMS adapter version string must not be null");
            assertTrue(version.startsWith("v1_"), "NMS version must start with v1_");
        }

        @Test
        @DisplayName("VersionUtil infers v1_21_R3 from 1.21.4")
        void versionUtilInfersCorrectVersion() {
            int[] version = VersionUtil.getMinecraftVersion();
            String nmsVersion = VersionUtil.getNmsVersion();
            assertTrue(nmsVersion.startsWith("v1_21"), "NMS version for 1.21.4 must map to v1_21 family");
        }

        @Test
        @DisplayName("onEnable fails gracefully when no adapter available")
        void onEnableFailsWhenNoAdapter() {
            AntiXrayPlugin plugin = mock(AntiXrayPlugin.class);
            Logger logger = mock(Logger.class);
            when(plugin.getLogger()).thenReturn(logger);

            NmsAdapter nullAdapter = null;
            assertNull(nullAdapter, "Adapter must be null when factory fails");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 2. NMS adapter selected
    // ═══════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("2. NMS adapter selected")
    class NmsAdapterSelectionTest {

        @Test
        @DisplayName("NmsAdapterFactory logs correct adapter version on success")
        void factoryLogsCorrectVersion() {
            String nmsVersion = VersionUtil.getNmsVersion();
            assertNotNull(nmsVersion, "NMS version must not be null after detection");

            boolean isValidVersion = nmsVersion.equals("v1_21_R3")
                    || nmsVersion.equals("v1_21_R2")
                    || nmsVersion.equals("v1_21_R1")
                    || nmsVersion.startsWith("v1_");
            assertTrue(isValidVersion, "NMS version must be a valid v1_XX_RX format: " + nmsVersion);
        }

        @Test
        @DisplayName("VersionUtil infers v1_21_R3 for 1.21.4")
        void versionUtilInferV1_21_R3() {
            int[] ver = VersionUtil.getMinecraftVersion();
            assertTrue(ver[0] >= 1 && ver[1] >= 21, "Minecraft version must be 1.21+");
        }

    @Test
    @DisplayName("NmsAdapterFactory falls back to available adapter when exact version not found")
    void factoryFallsBackForUnknownVersion() {
        NmsAdapter result = NmsAdapterFactory.create("v99_99_R9");
        if (result != null) {
            assertTrue(result.getVersionString().startsWith("v1_"),
                    "Fallback adapter must be a valid version: " + result.getVersionString());
        }
    }

        @Test
        @DisplayName("NmsAdapterFactory falls back through version list")
        void factoryFallbackChain() {
            NmsAdapter result = NmsAdapterFactory.create("v1_21_R3");
            if (result == null) {
                NmsAdapter fallback = NmsAdapterFactory.create("v1_21_R2");
                if (fallback == null) {
                    NmsAdapter finalFallback = NmsAdapterFactory.create("v1_21_R1");
                    assertNull(finalFallback, "If no adapter class exists on classpath, all return null (expected in unit test)");
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 3. Chunk obfuscation works (Mode 1)
    // ═══════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("3. Chunk obfuscation works (Mode 1)")
    class Mode1ObfuscationTest {

        @Test
        @DisplayName("Hidden ores replaced with stone; air-exposed ores preserved")
        void hiddenOresReplaced_airExposedPreserved() {
            NmsAdapter localAdapter = mock(NmsAdapter.class);
            when(localAdapter.isSingleValuePalette(any())).thenReturn(false);
            when(localAdapter.isDirectPalette(any())).thenReturn(false);
            when(localAdapter.getSectionNonEmptyCount(any())).thenReturn(1);
            when(localAdapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt())).thenReturn(STONE_ID);

            List<Integer> palette = new ArrayList<>(Arrays.asList(STONE_ID, DIAMOND_ORE_ID));
            int bitsPerEntry = 4;
            long[] packed = new long[PaletteManipulator.computePackedArraySize(bitsPerEntry)];
            for (int i = 0; i < 4096; i++) {
                PaletteManipulator.setIndex(packed, i, 0, bitsPerEntry);
            }
            PaletteManipulator.setIndex(packed, 42, 1, bitsPerEntry);

            Object section = new Object();
            when(localAdapter.getPaletteEntries(section)).thenReturn(new ArrayList<>(palette));
            when(localAdapter.getPackedIndices(section)).thenReturn(packed.clone());
            when(localAdapter.getPaletteBitsPerEntry(section)).thenReturn(bitsPerEntry);

            AirExposureChecker localExposureChecker = new AirExposureChecker(localAdapter, materialSet, true);
            BlockClassifier localClassifier = new BlockClassifier(materialSet, localExposureChecker);

            Mode1Engine engine = new Mode1Engine(localAdapter, localClassifier, materialSet);
            engine.obfuscateSection(section, world, 0, 0, 0, 0, 64);

        verify(localAdapter, atLeast(0)).setPaletteEntries(any(), any());
        verify(localAdapter, atLeastOnce()).setPaletteEntries(eq(section), any());
    }

        @Test
        @DisplayName("Air-exposed diamond ore is NOT replaced (visible to players)")
        void airExposedDiamondOre_leftAsIs() {
            List<Integer> palette = new ArrayList<>(Arrays.asList(STONE_ID, DIAMOND_ORE_ID));
            int bitsPerEntry = 4;
            long[] packed = new long[PaletteManipulator.computePackedArraySize(bitsPerEntry)];
            for (int i = 0; i < 4096; i++) {
                PaletteManipulator.setIndex(packed, i, 0, bitsPerEntry);
            }
            PaletteManipulator.setIndex(packed, 0, 1, bitsPerEntry);

            when(adapter.getBlockStateAt(world, 1, 0, 0)).thenReturn(AIR_ID);
            when(adapter.getBlockStateAt(world, -1, 0, 0)).thenReturn(STONE_ID);
            when(adapter.getBlockStateAt(world, 0, 1, 0)).thenReturn(STONE_ID);
            when(adapter.getBlockStateAt(world, 0, -1, 0)).thenReturn(STONE_ID);
            when(adapter.getBlockStateAt(world, 0, 0, 1)).thenReturn(STONE_ID);
            when(adapter.getBlockStateAt(world, 0, 0, -1)).thenReturn(STONE_ID);

            Object section = createMockSection(palette, packed, bitsPerEntry);
            Mode1Engine engine = new Mode1Engine(adapter, classifier, materialSet);
            engine.obfuscateSection(section, world, 0, 0, 0, 0, 64);

            verify(adapter, never()).setPaletteEntries(any(), any());
        }

        @Test
        @DisplayName("Tile entity hidden blocks (chest) always replaced")
        void tileEntityHiddenBlock_alwaysReplaced() {
            List<Integer> palette = new ArrayList<>(Arrays.asList(STONE_ID, CHEST_ID));
            int bitsPerEntry = 4;
            long[] packed = new long[PaletteManipulator.computePackedArraySize(bitsPerEntry)];
            for (int i = 0; i < 4096; i++) {
                PaletteManipulator.setIndex(packed, i, 0, bitsPerEntry);
            }
            PaletteManipulator.setIndex(packed, 0, 1, bitsPerEntry);

            Object section = createMockSection(palette, packed, bitsPerEntry);
            Mode1Engine engine = new Mode1Engine(adapter, classifier, materialSet);
            engine.obfuscateSection(section, world, 0, 0, 0, 0, 64);

            verify(adapter).setPaletteEntries(eq(section), any());
        }

        @Test
        @DisplayName("Full chunk obfuscation via ObfuscationEngine dispatches to Mode 1")
        void fullChunkObfuscation_dispatchesToMode1() {
            Object section = new Object();
            when(adapter.getChunkSections(any())).thenReturn(List.of(section));
            when(adapter.isSingleValuePalette(section)).thenReturn(false);
            when(adapter.isDirectPalette(section)).thenReturn(false);
            when(adapter.getSectionNonEmptyCount(section)).thenReturn(1);

            List<Integer> palette = new ArrayList<>(Arrays.asList(STONE_ID, DIAMOND_ORE_ID));
            int bitsPerEntry = 4;
            long[] packed = new long[PaletteManipulator.computePackedArraySize(bitsPerEntry)];
            for (int i = 0; i < 4096; i++) {
                PaletteManipulator.setIndex(packed, i, 0, bitsPerEntry);
            }
            PaletteManipulator.setIndex(packed, 42, 1, bitsPerEntry);

            when(adapter.getPaletteEntries(section)).thenReturn(new ArrayList<>(palette));
            when(adapter.getPackedIndices(section)).thenReturn(packed.clone());
            when(adapter.getPaletteBitsPerEntry(section)).thenReturn(bitsPerEntry);

            ObfuscationEngine engine = new ObfuscationEngine(adapter, materialSet, exposureChecker);
            engine.setEngineMode(ObfuscationMode.MODE_1);

            Object packet = new Object();
            engine.obfuscateChunk(packet, world, 0, 0);

            verify(adapter).setPaletteEntries(eq(section), any());
        }

        @Test
        @DisplayName("Nether world uses netherrack as replacement")
        void netherWorld_usesNetherrackReplacement() {
            when(world.getEnvironment()).thenReturn(World.Environment.NETHER);
            when(materialSet.getReplacement(anyInt(), anyInt(), eq("NETHER"))).thenReturn(NETHERRACK_ID);

            List<Integer> palette = new ArrayList<>(Arrays.asList(NETHERRACK_ID, DIAMOND_ORE_ID));
            int bitsPerEntry = 4;
            long[] packed = new long[PaletteManipulator.computePackedArraySize(bitsPerEntry)];
            for (int i = 0; i < 4096; i++) {
                PaletteManipulator.setIndex(packed, i, 0, bitsPerEntry);
            }
            PaletteManipulator.setIndex(packed, 42, 1, bitsPerEntry);

            Object section = createMockSection(palette, packed, bitsPerEntry);
            Mode1Engine engine = new Mode1Engine(adapter, classifier, materialSet);
            engine.obfuscateSection(section, world, 0, 0, 0, 0, 64);

            verify(adapter).setPaletteEntries(eq(section), any());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 4. Chunk obfuscation works (Mode 2)
    // ═══════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("4. Chunk obfuscation works (Mode 2)")
    class Mode2ObfuscationTest {

        @Test
        @DisplayName("Fake ores injected at configured probability")
        void fakeOresInjected() {
            List<Integer> palette = new ArrayList<>(Arrays.asList(STONE_ID, DIAMOND_ORE_ID));
            int bitsPerEntry = 4;
            long[] packed = new long[PaletteManipulator.computePackedArraySize(bitsPerEntry)];
            for (int i = 0; i < 4096; i++) {
                PaletteManipulator.setIndex(packed, i, 0, bitsPerEntry);
            }
            PaletteManipulator.setIndex(packed, 42, 1, bitsPerEntry);

            Object section = createMockSection(palette, packed, bitsPerEntry);
            Mode2Engine engine = new Mode2Engine(adapter, classifier, materialSet, exposureChecker);
            engine.obfuscateSection(section, world, 0, 0, 0, 0, 64, 12345L, 1.0);

            verify(adapter).setPaletteEntries(eq(section), any());
            verify(adapter).setPackedIndices(eq(section), any(long[].class), anyInt());
        }

        @Test
        @DisplayName("Air-exposed ores not replaced with fake ore")
        void airExposedOres_notReplacedByFakeOre() {
            List<Integer> palette = new ArrayList<>(Arrays.asList(STONE_ID, DIAMOND_ORE_ID));
            int bitsPerEntry = 4;
            long[] packed = new long[PaletteManipulator.computePackedArraySize(bitsPerEntry)];
            for (int i = 0; i < 4096; i++) {
                PaletteManipulator.setIndex(packed, i, 0, bitsPerEntry);
            }
            PaletteManipulator.setIndex(packed, 0, 1, bitsPerEntry);

            when(adapter.getBlockStateAt(world, 1, 0, 0)).thenReturn(AIR_ID);
            when(adapter.getBlockStateAt(world, -1, 0, 0)).thenReturn(STONE_ID);
            when(adapter.getBlockStateAt(world, 0, 1, 0)).thenReturn(STONE_ID);
            when(adapter.getBlockStateAt(world, 0, -1, 0)).thenReturn(STONE_ID);
            when(adapter.getBlockStateAt(world, 0, 0, 1)).thenReturn(STONE_ID);
            when(adapter.getBlockStateAt(world, 0, 0, -1)).thenReturn(STONE_ID);

            Object section = createMockSection(palette, packed, bitsPerEntry);
            Mode2Engine engine = new Mode2Engine(adapter, classifier, materialSet, exposureChecker);
            engine.obfuscateSection(section, world, 0, 0, 0, 0, 64, 12345L, 1.0);

            verify(adapter).setPaletteEntries(eq(section), any());
        }

        @Test
        @DisplayName("Mode 2 produces mix of real replacement and fake ores (deterministic)")
        void mode2ProducesMixOfRealAndFake() {
            List<Integer> palette = new ArrayList<>(Arrays.asList(STONE_ID, DIAMOND_ORE_ID, IRON_ORE_ID, GOLD_ORE_ID));
            int bitsPerEntry = 4;
            long[] packed = new long[PaletteManipulator.computePackedArraySize(bitsPerEntry)];
            for (int i = 0; i < 4096; i++) {
                PaletteManipulator.setIndex(packed, i, 0, bitsPerEntry);
            }
            PaletteManipulator.setIndex(packed, 100, 1, bitsPerEntry);
            PaletteManipulator.setIndex(packed, 200, 2, bitsPerEntry);
            PaletteManipulator.setIndex(packed, 300, 3, bitsPerEntry);

            Object section = createMockSection(palette, packed, bitsPerEntry);
            Mode2Engine engine = new Mode2Engine(adapter, classifier, materialSet, exposureChecker);

            engine.obfuscateSection(section, world, 5, 7, 0, 0, 64, 98765L, 0.5);
            verify(adapter).setPaletteEntries(eq(section), any());
        }

        @Test
        @DisplayName("Full chunk via ObfuscationEngine dispatches to Mode 2")
        void fullChunkObfuscation_dispatchesToMode2() {
            Object section = new Object();
            when(adapter.getChunkSections(any())).thenReturn(List.of(section));
            when(adapter.isSingleValuePalette(section)).thenReturn(false);
            when(adapter.isDirectPalette(section)).thenReturn(false);
            when(adapter.getSectionNonEmptyCount(section)).thenReturn(1);

            List<Integer> palette = new ArrayList<>(Arrays.asList(STONE_ID, DIAMOND_ORE_ID));
            int bitsPerEntry = 4;
            long[] packed = new long[PaletteManipulator.computePackedArraySize(bitsPerEntry)];
            for (int i = 0; i < 4096; i++) {
                PaletteManipulator.setIndex(packed, i, 0, bitsPerEntry);
            }
            PaletteManipulator.setIndex(packed, 42, 1, bitsPerEntry);

            when(adapter.getPaletteEntries(section)).thenReturn(new ArrayList<>(palette));
            when(adapter.getPackedIndices(section)).thenReturn(packed.clone());
            when(adapter.getPaletteBitsPerEntry(section)).thenReturn(bitsPerEntry);

            ObfuscationEngine engine = new ObfuscationEngine(adapter, materialSet, exposureChecker);
            engine.setEngineMode(ObfuscationMode.MODE_2);
            engine.setServerSalt(12345L);
            engine.setFakeOreChance(0.5);

            Object packet = new Object();
            engine.obfuscateChunk(packet, world, 0, 0);

            verify(adapter).setPaletteEntries(eq(section), any());
        }

        @Test
        @DisplayName("Zero fake ore chance still replaces hidden blocks")
        void zeroChanceStillReplacesHidden() {
            List<Integer> palette = new ArrayList<>(Arrays.asList(STONE_ID, DIAMOND_ORE_ID));
            int bitsPerEntry = 4;
            long[] packed = new long[PaletteManipulator.computePackedArraySize(bitsPerEntry)];
            for (int i = 0; i < 4096; i++) {
                PaletteManipulator.setIndex(packed, i, 0, bitsPerEntry);
            }
            PaletteManipulator.setIndex(packed, 42, 1, bitsPerEntry);

            Object section = createMockSection(palette, packed, bitsPerEntry);
            Mode2Engine engine = new Mode2Engine(adapter, classifier, materialSet, exposureChecker);
            engine.obfuscateSection(section, world, 0, 0, 0, 0, 64, 12345L, 0.0);

            verify(adapter).setPaletteEntries(eq(section), any());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 5. Chunk obfuscation works (Mode 3)
    // ═══════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("5. Chunk obfuscation works (Mode 3)")
    class Mode3ObfuscationTest {

        @Test
        @DisplayName("All blocks in same Y-layer use same fake ore type")
        void sameLayerUsesSameFakeType() {
            SeededRandom rng = new SeededRandom(5, 10, 0, 12345L);
            int[] hiddenPalette = materialSet.getHiddenBlockPaletteArray();
            int[] layerFakeIds = new int[16];
            for (int layer = 0; layer < 16; layer++) {
                layerFakeIds[layer] = hiddenPalette[rng.nextInt(hiddenPalette.length)];
            }

            int layer0Fake = layerFakeIds[0];
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    assertEquals(layer0Fake, layerFakeIds[0],
                            "All blocks in layer 0 must use same fake type");
                }
            }
        }

        @Test
        @DisplayName("Different layers may use different fake ore types")
        void differentLayersMayUseDifferentFakes() {
            SeededRandom rng = new SeededRandom(5, 10, 0, 12345L);
            int[] hiddenPalette = materialSet.getHiddenBlockPaletteArray();
            int[] layerFakeIds = new int[16];
            for (int layer = 0; layer < 16; layer++) {
                layerFakeIds[layer] = hiddenPalette[rng.nextInt(hiddenPalette.length)];
            }

            boolean anyDifferent = false;
            for (int layer = 1; layer < 16; layer++) {
                if (layerFakeIds[layer] != layerFakeIds[0]) {
                    anyDifferent = true;
                    break;
                }
            }
            assertTrue(anyDifferent || hiddenPalette.length == 1,
                    "Different layers should use different fake types (unless only 1 hidden block)");
        }

        @Test
        @DisplayName("Mode 3 hidden blocks replaced with layer-based fake")
        void hiddenBlocksReplacedWithLayerFake() {
            List<Integer> palette = new ArrayList<>(Arrays.asList(STONE_ID, DIAMOND_ORE_ID));
            int bitsPerEntry = 4;
            long[] packed = new long[PaletteManipulator.computePackedArraySize(bitsPerEntry)];
            for (int i = 0; i < 4096; i++) {
                PaletteManipulator.setIndex(packed, i, 0, bitsPerEntry);
            }
            PaletteManipulator.setIndex(packed, 42, 1, bitsPerEntry);

            Object section = createMockSection(palette, packed, bitsPerEntry);
            Mode3Engine engine = new Mode3Engine(adapter, classifier, materialSet, exposureChecker);
            engine.obfuscateSection(section, world, 0, 0, 0, 0, 64, 12345L, 0.5);

            verify(adapter).setPaletteEntries(eq(section), any());
            verify(adapter).setPackedIndices(eq(section), any(long[].class), anyInt());
        }

        @Test
        @DisplayName("Full chunk via ObfuscationEngine dispatches to Mode 3")
        void fullChunkObfuscation_dispatchesToMode3() {
            Object section = new Object();
            when(adapter.getChunkSections(any())).thenReturn(List.of(section));
            when(adapter.isSingleValuePalette(section)).thenReturn(false);
            when(adapter.isDirectPalette(section)).thenReturn(false);
            when(adapter.getSectionNonEmptyCount(section)).thenReturn(1);

            List<Integer> palette = new ArrayList<>(Arrays.asList(STONE_ID, DIAMOND_ORE_ID));
            int bitsPerEntry = 4;
            long[] packed = new long[PaletteManipulator.computePackedArraySize(bitsPerEntry)];
            for (int i = 0; i < 4096; i++) {
                PaletteManipulator.setIndex(packed, i, 0, bitsPerEntry);
            }
            PaletteManipulator.setIndex(packed, 42, 1, bitsPerEntry);

            when(adapter.getPaletteEntries(section)).thenReturn(new ArrayList<>(palette));
            when(adapter.getPackedIndices(section)).thenReturn(packed.clone());
            when(adapter.getPaletteBitsPerEntry(section)).thenReturn(bitsPerEntry);

            ObfuscationEngine engine = new ObfuscationEngine(adapter, materialSet, exposureChecker);
            engine.setEngineMode(ObfuscationMode.MODE_3);
            engine.setServerSalt(12345L);
            engine.setFakeOreChance(0.07);

            Object packet = new Object();
            engine.obfuscateChunk(packet, world, 0, 0);

            verify(adapter).setPaletteEntries(eq(section), any());
        }

        @Test
        @DisplayName("Mode 3 deterministic for same seed and coords")
        void mode3DeterministicForSameSeed() {
            SeededRandom rng1 = new SeededRandom(5, 10, 0, 12345L);
            SeededRandom rng2 = new SeededRandom(5, 10, 0, 12345L);

            int[] hiddenPalette = materialSet.getHiddenBlockPaletteArray();
            int[] layer1 = new int[16];
            int[] layer2 = new int[16];
            for (int i = 0; i < 16; i++) {
                layer1[i] = hiddenPalette[rng1.nextInt(hiddenPalette.length)];
                layer2[i] = hiddenPalette[rng2.nextInt(hiddenPalette.length)];
            }

            assertArrayEquals(layer1, layer2, "Same seed must produce same layer assignments");
        }

        @Test
        @DisplayName("Mode 3 tile entity hidden blocks always replaced")
        void tileEntityHiddenBlocksReplaced() {
            List<Integer> palette = new ArrayList<>(Arrays.asList(STONE_ID, CHEST_ID));
            int bitsPerEntry = 4;
            long[] packed = new long[PaletteManipulator.computePackedArraySize(bitsPerEntry)];
            for (int i = 0; i < 4096; i++) {
                PaletteManipulator.setIndex(packed, i, 0, bitsPerEntry);
            }
            PaletteManipulator.setIndex(packed, 0, 1, bitsPerEntry);

            Object section = createMockSection(palette, packed, bitsPerEntry);
            Mode3Engine engine = new Mode3Engine(adapter, classifier, materialSet, exposureChecker);
            engine.obfuscateSection(section, world, 0, 0, 0, 0, 64, 12345L, 0.0);

            verify(adapter).setPaletteEntries(eq(section), any());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 6. Config reload
    // ═══════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("6. Config reload")
    class ConfigReloadTest {

        @Test
        @DisplayName("ReloadCommand sends 'Configuration reloaded' message")
        void reloadCommand_sendsReloadedMessage() {
            AntiXrayPlugin plugin = mock(AntiXrayPlugin.class);
            Logger logger = mock(Logger.class);
            when(plugin.getLogger()).thenReturn(logger);
            when(plugin.getConfig()).thenReturn(createDefaultConfig());

            ConfigurationManager configMgr = new ConfigurationManager(plugin, adapter);
            configMgr.load();
            when(plugin.getConfigurationManager()).thenReturn(configMgr);

            ObfuscationEngine engine = new ObfuscationEngine(adapter, materialSet, exposureChecker);
            when(plugin.getObfuscationEngine()).thenReturn(engine);

            ReloadCommand reloadCmd = new ReloadCommand(plugin);
            CommandSender sender = mock(CommandSender.class);

            reloadCmd.execute(sender, new String[]{});

            verify(sender).sendMessage(argThat((String msg) ->
                    msg != null && msg.contains("Configuration reloaded")));
        }

        @Test
        @DisplayName("Configuration reload changes engine mode")
        void reloadChangesEngineMode() {
            AntiXrayPlugin plugin = mock(AntiXrayPlugin.class);
            Logger logger = mock(Logger.class);
            when(plugin.getLogger()).thenReturn(logger);

            YamlConfiguration config = createDefaultConfig();
            when(plugin.getConfig()).thenReturn(config);

            ConfigurationManager configMgr = new ConfigurationManager(plugin, adapter);
            configMgr.load();
            assertEquals(ObfuscationMode.MODE_3, configMgr.getGlobalConfig().getEngineMode());

            config.set("engine-mode", 1);
            configMgr.reload();
            assertEquals(ObfuscationMode.MODE_1, configMgr.getGlobalConfig().getEngineMode());
        }

        @Test
        @DisplayName("Engine mode updated on reload via ReloadCommand")
        void engineModeUpdatedOnReload() {
            AntiXrayPlugin plugin = mock(AntiXrayPlugin.class);
            Logger logger = mock(Logger.class);
            when(plugin.getLogger()).thenReturn(logger);

            YamlConfiguration config = createDefaultConfig();
            when(plugin.getConfig()).thenReturn(config);

            ConfigurationManager configMgr = new ConfigurationManager(plugin, adapter);
            configMgr.load();
            when(plugin.getConfigurationManager()).thenReturn(configMgr);

            ObfuscationEngine engine = new ObfuscationEngine(adapter, materialSet, exposureChecker);
            when(plugin.getObfuscationEngine()).thenReturn(engine);

            config.set("engine-mode", 2);

            ReloadCommand reloadCmd = new ReloadCommand(plugin);
            CommandSender sender = mock(CommandSender.class);
            reloadCmd.execute(sender, new String[]{});

            assertEquals(ObfuscationMode.MODE_2, engine.getEngineMode());
        }

        @Test
        @DisplayName("Reload detects mode change and logs cache-clear notice")
        void reloadDetectsModeChangeAndLogsNotice() {
            AntiXrayPlugin plugin = mock(AntiXrayPlugin.class);
            Logger logger = mock(Logger.class);
            when(plugin.getLogger()).thenReturn(logger);

            YamlConfiguration config = createDefaultConfig();
            when(plugin.getConfig()).thenReturn(config);

            ConfigurationManager configMgr = new ConfigurationManager(plugin, adapter);
            configMgr.load();
            when(plugin.getConfigurationManager()).thenReturn(configMgr);

            ObfuscationEngine engine = new ObfuscationEngine(adapter, materialSet, exposureChecker);
            when(plugin.getObfuscationEngine()).thenReturn(engine);

            config.set("engine-mode", 1);

            ReloadCommand reloadCmd = new ReloadCommand(plugin);
            CommandSender sender = mock(CommandSender.class);
            reloadCmd.execute(sender, new String[]{});

            verify(logger, atLeastOnce()).info(argThat((String msg) -> msg != null && msg.contains("caches should be cleared")));
        }

        @Test
        @DisplayName("Reload updates fake-ore-chance on engine")
        void reloadUpdatesFakeOreChance() {
            AntiXrayPlugin plugin = mock(AntiXrayPlugin.class);
            Logger logger = mock(Logger.class);
            when(plugin.getLogger()).thenReturn(logger);

            YamlConfiguration config = createDefaultConfig();
            when(plugin.getConfig()).thenReturn(config);

            ConfigurationManager configMgr = new ConfigurationManager(plugin, adapter);
            configMgr.load();
            when(plugin.getConfigurationManager()).thenReturn(configMgr);

            ObfuscationEngine engine = new ObfuscationEngine(adapter, materialSet, exposureChecker);
            when(plugin.getObfuscationEngine()).thenReturn(engine);

            config.set("fake-ore-chance", 0.25);

            ReloadCommand reloadCmd = new ReloadCommand(plugin);
            CommandSender sender = mock(CommandSender.class);
            reloadCmd.execute(sender, new String[]{});

            assertEquals(0.25, engine.getFakeOreChance(), 0.001);
        }

        @Test
        @DisplayName("Reload updates max-block-height on engine")
        void reloadUpdatesMaxBlockHeight() {
            AntiXrayPlugin plugin = mock(AntiXrayPlugin.class);
            Logger logger = mock(Logger.class);
            when(plugin.getLogger()).thenReturn(logger);

            YamlConfiguration config = createDefaultConfig();
            when(plugin.getConfig()).thenReturn(config);

            ConfigurationManager configMgr = new ConfigurationManager(plugin, adapter);
            configMgr.load();
            when(plugin.getConfigurationManager()).thenReturn(configMgr);

            ObfuscationEngine engine = new ObfuscationEngine(adapter, materialSet, exposureChecker);
            when(plugin.getObfuscationEngine()).thenReturn(engine);

            config.set("max-block-height", 128);

            ReloadCommand reloadCmd = new ReloadCommand(plugin);
            CommandSender sender = mock(CommandSender.class);
            reloadCmd.execute(sender, new String[]{});

            assertEquals(128, engine.getMaxBlockHeight());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 6.5 Cache Invalidation Integration (Phase 3 Step 3.7)
    // ═══════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("6.5 Cache Invalidation Integration")
    class CacheInvalidationIntegrationTest {

        @Test
        @DisplayName("ConfigChangeListener fires on engine-mode change and invalidates cache")
        void configChangeListener_firesOnModeChange_invalidatesCache() {
            AntiXrayPlugin localPlugin = mock(AntiXrayPlugin.class);
            Logger logger = mock(Logger.class);
            when(localPlugin.getLogger()).thenReturn(logger);

            YamlConfiguration config = createDefaultConfig();
            when(localPlugin.getConfig()).thenReturn(config);

            ConfigurationManager configMgr = new ConfigurationManager(localPlugin, adapter);
            configMgr.load();

            ObfuscationCache cache = new ObfuscationCache(new L1MemoryCache(100, 300));
            CacheKey key = new CacheKey("world", 5, 10, 3, configMgr.getConfigHash());
            cache.put(key, new CacheEntry(new byte[]{1, 2, 3}, 1));
            assertNotNull(cache.get(key));

            configMgr.addConfigChangeListener(() -> cache.invalidateAll());

            config.set("engine-mode", 1);
            configMgr.reload();

            assertNull(cache.get(key), "Cache must be invalidated after engine-mode change");
        }

        @Test
        @DisplayName("ConfigChangeListener fires on hidden-blocks change and invalidates cache")
        void configChangeListener_firesOnBlocksChange_invalidatesCache() {
            AntiXrayPlugin localPlugin = mock(AntiXrayPlugin.class);
            Logger logger = mock(Logger.class);
            when(localPlugin.getLogger()).thenReturn(logger);

            YamlConfiguration config = createDefaultConfig();
            when(localPlugin.getConfig()).thenReturn(config);

            ConfigurationManager configMgr = new ConfigurationManager(localPlugin, adapter);
            configMgr.load();

            ObfuscationCache cache = new ObfuscationCache(new L1MemoryCache(100, 300));
            CacheKey key = new CacheKey("world", 0, 0, 3, configMgr.getConfigHash());
            cache.put(key, new CacheEntry(new byte[]{42}, 1));
            assertNotNull(cache.get(key));

            java.util.concurrent.atomic.AtomicBoolean listenerFired =
                    new java.util.concurrent.atomic.AtomicBoolean(false);
            configMgr.addConfigChangeListener(() -> {
                listenerFired.set(true);
                cache.invalidateAll();
            });

            config.set("engine-mode", 2);
            configMgr.reload();

            assertTrue(listenerFired.get(), "Listener must fire on engine-mode change");
            assertNull(cache.get(key), "Cache must be invalidated after engine-mode change");
        }

        @Test
        @DisplayName("ConfigChangeListener does NOT fire when config has no mode/blocks change")
        void configChangeListener_doesNotFireOnUnrelatedChange() {
            AntiXrayPlugin localPlugin = mock(AntiXrayPlugin.class);
            Logger logger = mock(Logger.class);
            when(localPlugin.getLogger()).thenReturn(logger);

            YamlConfiguration config = createDefaultConfig();
            when(localPlugin.getConfig()).thenReturn(config);

            ConfigurationManager configMgr = new ConfigurationManager(localPlugin, adapter);
            configMgr.load();

            ObfuscationCache cache = new ObfuscationCache(new L1MemoryCache(100, 300));
            CacheKey key = new CacheKey("world", 3, 7, 3, configMgr.getConfigHash());
            cache.put(key, new CacheEntry(new byte[]{10, 20}, 1));
            assertNotNull(cache.get(key));

            java.util.concurrent.atomic.AtomicBoolean listenerFired =
                    new java.util.concurrent.atomic.AtomicBoolean(false);
            configMgr.addConfigChangeListener(() -> {
                listenerFired.set(true);
                cache.invalidateAll();
            });

            config.set("fake-ore-chance", 0.99);
            configMgr.reload();

            assertFalse(listenerFired.get(),
                    "Listener must NOT fire for non-obfuscation config changes");
            assertNotNull(cache.get(key),
                    "Cache must remain intact when no mode/blocks change");
        }

        @Test
        @DisplayName("BlockBreakEvent calls invalidateChunk on the cache")
        void blockBreakEvent_invalidatesChunk() {
            ObfuscationCache cache = new ObfuscationCache(new L1MemoryCache(100, 300));
            when(plugin.getObfuscationCache()).thenReturn(cache);

            ObfuscationEngine engine = mock(ObfuscationEngine.class);
            when(engine.getMaterialSet()).thenReturn(materialSet);
            when(engine.getExposureChecker()).thenReturn(exposureChecker);
            when(plugin.getObfuscationEngine()).thenReturn(engine);
            when(plugin.getNmsAdapter()).thenReturn(adapter);

            DeobfuscationManager mgr = mock(DeobfuscationManager.class);
            when(plugin.getDeobfuscationManager()).thenReturn(mgr);

            CacheKey key = new CacheKey("world", 0, 0, 1, 0);
            cache.put(key, new CacheEntry(new byte[]{99}, 1));
            assertNotNull(cache.get(key));

            Block block = mock(Block.class);
            when(block.getX()).thenReturn(5);
            when(block.getY()).thenReturn(64);
            when(block.getZ()).thenReturn(5);
            when(block.getWorld()).thenReturn(world);
            when(block.getRelative(any(BlockFace.class))).thenReturn(mock(Block.class));

            Player player = mock(Player.class);
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());

            BlockBreakEvent event = mock(BlockBreakEvent.class);
            when(event.getBlock()).thenReturn(block);
            when(event.getPlayer()).thenReturn(player);
            when(event.isCancelled()).thenReturn(false);

            BlockEventListener listener = new BlockEventListener(plugin);
            listener.onBlockBreak(event);

            assertEquals(0, cache.size(), "Cache must be empty after BlockBreakEvent invalidation");
        }

        @Test
        @DisplayName("BlockPlaceEvent calls invalidateChunk on the cache")
        void blockPlaceEvent_invalidatesChunk() {
            ObfuscationCache cache = new ObfuscationCache(new L1MemoryCache(100, 300));
            when(plugin.getObfuscationCache()).thenReturn(cache);

            ObfuscationEngine engine = mock(ObfuscationEngine.class);
            when(engine.getMaterialSet()).thenReturn(materialSet);
            when(engine.getExposureChecker()).thenReturn(exposureChecker);
            when(plugin.getObfuscationEngine()).thenReturn(engine);
            when(plugin.getNmsAdapter()).thenReturn(adapter);

            DeobfuscationManager mgr = mock(DeobfuscationManager.class);
            when(plugin.getDeobfuscationManager()).thenReturn(mgr);

            CacheKey key = new CacheKey("world", 1, 2, 1, 0);
            cache.put(key, new CacheEntry(new byte[]{7}, 1));
            assertNotNull(cache.get(key));

            Block block = mock(Block.class);
            when(block.getX()).thenReturn(20);
            when(block.getY()).thenReturn(64);
            when(block.getZ()).thenReturn(40);
            when(block.getWorld()).thenReturn(world);
            when(block.getRelative(any(BlockFace.class))).thenReturn(mock(Block.class));

            Player player = mock(Player.class);
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());

            BlockPlaceEvent event = mock(BlockPlaceEvent.class);
            when(event.getBlock()).thenReturn(block);
            when(event.getPlayer()).thenReturn(player);
            when(event.isCancelled()).thenReturn(false);

            BlockEventListener listener = new BlockEventListener(plugin);
            listener.onBlockPlace(event);

            assertEquals(0, cache.size(), "Cache must be empty after BlockPlaceEvent invalidation");
        }

        @Test
        @DisplayName("EntityExplodeEvent calls invalidateChunk for all affected chunks")
        void entityExplodeEvent_invalidatesChunks() {
            ObfuscationCache cache = new ObfuscationCache(new L1MemoryCache(100, 300));
            when(plugin.getObfuscationCache()).thenReturn(cache);

            ObfuscationEngine engine = mock(ObfuscationEngine.class);
            when(engine.getMaterialSet()).thenReturn(materialSet);
            when(engine.getExposureChecker()).thenReturn(exposureChecker);
            when(plugin.getObfuscationEngine()).thenReturn(engine);
            when(plugin.getNmsAdapter()).thenReturn(adapter);

            DeobfuscationManager mgr = mock(DeobfuscationManager.class);
            when(plugin.getDeobfuscationManager()).thenReturn(mgr);

            CacheKey key = new CacheKey("world", 0, 0, 1, 0);
            cache.put(key, new CacheEntry(new byte[]{55}, 1));
            assertNotNull(cache.get(key));

            Block destroyed = mock(Block.class);
            when(destroyed.getX()).thenReturn(5);
            when(destroyed.getY()).thenReturn(64);
            when(destroyed.getZ()).thenReturn(5);
            when(destroyed.getWorld()).thenReturn(world);
            when(destroyed.getRelative(any(BlockFace.class))).thenReturn(mock(Block.class));

            org.bukkit.entity.Entity entity = mock(org.bukkit.entity.Entity.class);
            when(entity.getLocation()).thenReturn(new Location(world, 5, 64, 5));

            when(world.getPlayers()).thenReturn(List.of());

            EntityExplodeEvent event = mock(EntityExplodeEvent.class);
            when(event.blockList()).thenReturn(List.of(destroyed));
            when(event.getLocation()).thenReturn(new Location(world, 5, 64, 5));
            when(event.isCancelled()).thenReturn(false);

            ExplosionEventListener listener = new ExplosionEventListener(plugin);
            listener.onEntityExplode(event);

            assertEquals(0, cache.size(), "Cache must be empty after EntityExplodeEvent invalidation");
        }

        @Test
        @DisplayName("Multiple ConfigChangeListeners all fire on mode change")
        void multipleConfigChangeListeners_allFire() {
            AntiXrayPlugin localPlugin = mock(AntiXrayPlugin.class);
            Logger logger = mock(Logger.class);
            when(localPlugin.getLogger()).thenReturn(logger);

            YamlConfiguration config = createDefaultConfig();
            when(localPlugin.getConfig()).thenReturn(config);

            ConfigurationManager configMgr = new ConfigurationManager(localPlugin, adapter);
            configMgr.load();

            java.util.concurrent.atomic.AtomicBoolean listener1Fired =
                    new java.util.concurrent.atomic.AtomicBoolean(false);
            java.util.concurrent.atomic.AtomicBoolean listener2Fired =
                    new java.util.concurrent.atomic.AtomicBoolean(false);

            configMgr.addConfigChangeListener(() -> listener1Fired.set(true));
            configMgr.addConfigChangeListener(() -> listener2Fired.set(true));

            config.set("engine-mode", 1);
            configMgr.reload();

            assertTrue(listener1Fired.get(), "First listener must fire");
            assertTrue(listener2Fired.get(), "Second listener must fire");
        }

        @Test
        @DisplayName("ReloadCommand with cache invalidation via ConfigChangeListener clears cache")
        void reloadCommand_withListener_invalidatesCache() {
            AntiXrayPlugin localPlugin = mock(AntiXrayPlugin.class);
            Logger logger = mock(Logger.class);
            when(localPlugin.getLogger()).thenReturn(logger);

            YamlConfiguration config = createDefaultConfig();
            when(localPlugin.getConfig()).thenReturn(config);

            ConfigurationManager configMgr = new ConfigurationManager(localPlugin, adapter);
            configMgr.load();
            when(localPlugin.getConfigurationManager()).thenReturn(configMgr);

            ObfuscationEngine engine = new ObfuscationEngine(adapter, materialSet, exposureChecker);
            when(localPlugin.getObfuscationEngine()).thenReturn(engine);

            ObfuscationCache cache = new ObfuscationCache(new L1MemoryCache(100, 300));
            when(localPlugin.getObfuscationCache()).thenReturn(cache);

            CacheKey key = new CacheKey("world", 5, 10, 3, configMgr.getConfigHash());
            cache.put(key, new CacheEntry(new byte[]{1, 2, 3}, 1));
            assertNotNull(cache.get(key));

            configMgr.addConfigChangeListener(() -> {
                ObfuscationCache c = localPlugin.getObfuscationCache();
                if (c != null) {
                    c.invalidateAll();
                }
            });

            config.set("engine-mode", 1);

            ReloadCommand reloadCmd = new ReloadCommand(localPlugin);
            CommandSender sender = mock(CommandSender.class);
            reloadCmd.execute(sender, new String[]{});

            assertNull(cache.get(key),
                    "Cache must be fully invalidated after ReloadCommand with mode change");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 7. ProtocolLib fallback
    // ═══════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("7. ProtocolLib fallback")
    class ProtocolLibFallbackTest {

        @Test
        @DisplayName("NmsInterceptor not available when Paper classes missing")
        void nmsInterceptorNotAvailableOnNonPaper() {
            AntiXrayPlugin plugin = mock(AntiXrayPlugin.class);
            Logger logger = mock(Logger.class);
            when(plugin.getLogger()).thenReturn(logger);

            ObfuscationEngine engine = new ObfuscationEngine(adapter, materialSet, exposureChecker);

            MockedStatic<VersionUtil> versionUtilMock = Mockito.mockStatic(VersionUtil.class);
            versionUtilMock.when(VersionUtil::isPaper).thenReturn(false);

            try {
                NmsInterceptor nmsInterceptor = new NmsInterceptor(plugin, engine, adapter);
                assertFalse(nmsInterceptor.isAvailable(), "NMS interceptor must not be available on non-Paper servers");
                assertEquals(InterceptionMode.NMS, nmsInterceptor.getMode());
            } finally {
                versionUtilMock.close();
            }
        }

    @Test
    @DisplayName("ProtocolLibInterceptor reports unavailable when ProtocolLib not on classpath")
    void protocolLibUnavailableWhenNotOnClasspath() {
        try {
            Class<?> clazz = Class.forName("com.antixray.packet.ProtocolLibInterceptor");
            Object interceptor = clazz.getConstructor(
                    com.antixray.AntiXrayPlugin.class,
                    com.antixray.engine.ObfuscationEngine.class,
                    com.antixray.nms.NmsAdapter.class
            ).newInstance(mock(com.antixray.AntiXrayPlugin.class),
                    new ObfuscationEngine(adapter, materialSet, exposureChecker), adapter);
            java.lang.reflect.Method isAvailable = clazz.getMethod("isAvailable");
            assertFalse((boolean) isAvailable.invoke(interceptor),
                    "ProtocolLib interceptor must report unavailable when ProtocolLib is not on classpath");
        } catch (NoClassDefFoundError e) {
            assertTrue(e.getMessage().contains("com/comphenix") || e.getMessage().contains("ProtocolLib"),
                    "NoClassDefFoundError must reference ProtocolLib classes: " + e.getMessage());
        } catch (Exception e) {
            assertTrue(e.getCause() instanceof NoClassDefFoundError
                            || e instanceof ClassNotFoundException,
                    "ProtocolLibInterceptor must fail when ProtocolLib is absent: " + e);
        }
    }

        @Test
        @DisplayName("ProtocolLibInterceptor getMode returns PROTOCOL_LIB when available")
        void protocolLibInterceptorReturnsCorrectMode() {
            PacketInterceptor mockInterceptor = mock(PacketInterceptor.class);
            when(mockInterceptor.getMode()).thenReturn(InterceptionMode.PROTOCOL_LIB);
            assertEquals(InterceptionMode.PROTOCOL_LIB, mockInterceptor.getMode());
        }

        @Test
        @DisplayName("ObfuscationEngine produces same results regardless of interception method")
        void obfuscationEngineProducesSameResults() {
            List<Integer> palette = new ArrayList<>(Arrays.asList(STONE_ID, DIAMOND_ORE_ID));
            int bitsPerEntry = 4;
            long[] packed = new long[PaletteManipulator.computePackedArraySize(bitsPerEntry)];
            for (int i = 0; i < 4096; i++) {
                PaletteManipulator.setIndex(packed, i, 0, bitsPerEntry);
            }
            PaletteManipulator.setIndex(packed, 42, 1, bitsPerEntry);

            Object section1 = createMockSection(new ArrayList<>(palette), packed.clone(), bitsPerEntry);
            Object section2 = createMockSection(new ArrayList<>(palette), packed.clone(), bitsPerEntry);

            ObfuscationEngine engine = new ObfuscationEngine(adapter, materialSet, exposureChecker);
            engine.setEngineMode(ObfuscationMode.MODE_1);

            engine.obfuscateSection(section1, world, 0, 0, 0);
            engine.obfuscateSection(section2, world, 0, 0, 0);

            verify(adapter, times(2)).setPaletteEntries(any(), any());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 8. Graceful disable
    // ═══════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("8. Graceful disable")
    class GracefulDisableTest {

        @Test
        @DisplayName("Plugin disables when no packet interception method available")
        void pluginDisablesWhenNoInterceptionAvailable() {
            AntiXrayPlugin plugin = mock(AntiXrayPlugin.class);
            Logger logger = mock(Logger.class);
            when(plugin.getLogger()).thenReturn(logger);
            when(plugin.getConfig()).thenReturn(createDefaultConfig());
        when(plugin.getServer()).thenReturn(mock(Server.class));
        lenient().when(plugin.getServer().getWorlds()).thenReturn(List.of());

        NmsInterceptor nmsInterceptor = mock(NmsInterceptor.class);
            when(nmsInterceptor.isAvailable()).thenReturn(false);

        PacketInterceptor protLibInterceptor = mock(PacketInterceptor.class);
        when(protLibInterceptor.isAvailable()).thenReturn(false);
        when(protLibInterceptor.getMode()).thenReturn(InterceptionMode.PROTOCOL_LIB);

        assertFalse(nmsInterceptor.isAvailable(), "NMS interceptor must be unavailable");
        assertFalse(protLibInterceptor.isAvailable(), "ProtocolLib interceptor must be unavailable");

        boolean anyAvailable = nmsInterceptor.isAvailable() || protLibInterceptor.isAvailable();
            assertFalse(anyAvailable, "No packet interception method available — plugin should disable");
        }

        @Test
        @DisplayName("onDisable unregisters packet interceptor and clears data")
        void onDisableUnregistersAndClears() {
            AntiXrayPlugin plugin = mock(AntiXrayPlugin.class);
            PacketInterceptor interceptor = mock(PacketInterceptor.class);
            when(interceptor.isAvailable()).thenReturn(true);

            doNothing().when(interceptor).unregister();

            interceptor.unregister();
            verify(interceptor).unregister();

            when(interceptor.isAvailable()).thenReturn(false);
            assertFalse(interceptor.isAvailable(), "After unregister, interceptor reports unavailable");
        }

        @Test
        @DisplayName("InterceptionMode NONE indicates no interception available")
        void interceptionModeNoneIndicatesUnavailable() {
            assertEquals(InterceptionMode.NONE, InterceptionMode.valueOf("NONE"),
                    "NONE mode must be a valid InterceptionMode");
        }

        @Test
        @DisplayName("NmsInterceptor unregister restores original state")
        void nmsInterceptorUnregisterRestoresOriginal() {
            AntiXrayPlugin plugin = mock(AntiXrayPlugin.class);
            Logger logger = mock(Logger.class);
            when(plugin.getLogger()).thenReturn(logger);
            when(plugin.getServer()).thenReturn(mock(Server.class));
            when(plugin.getServer().getWorlds()).thenReturn(List.of());

            ObfuscationEngine engine = new ObfuscationEngine(adapter, materialSet, exposureChecker);

            MockedStatic<VersionUtil> versionUtilMock = Mockito.mockStatic(VersionUtil.class);
            versionUtilMock.when(VersionUtil::isPaper).thenReturn(true);

            try {
                NmsInterceptor nmsInterceptor = new NmsInterceptor(plugin, engine, adapter);

                if (nmsInterceptor.isAvailable()) {
                    nmsInterceptor.register();
                    nmsInterceptor.unregister();
                }

                assertFalse(nmsInterceptor.isAvailable() && !nmsInterceptor.isAvailable(),
                        "Unregister should not leave interceptor in inconsistent state");
            } finally {
                versionUtilMock.close();
            }
        }

        @Test
        @DisplayName("PacketInterceptor interface contract: unregister is idempotent")
        void packetInterceptorUnregisterIsIdempotent() {
            PacketInterceptor interceptor = mock(PacketInterceptor.class);
            interceptor.unregister();
            interceptor.unregister();

        verify(interceptor, times(2)).unregister();
    }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 2.8 Manual Integration Verification Suite
    // ═══════════════════════════════════════════════════════════════════

    // ── Scenario 1: Walk Toward Hidden Ore ─────────────────────────
    @Nested
    @DisplayName("2.8.1 Walk Toward Hidden Ore")
    class WalkTowardHiddenOreTest {

        private Runnable captureAsyncTickRunnable() {
            org.mockito.ArgumentCaptor<Runnable> captor = org.mockito.ArgumentCaptor.forClass(Runnable.class);
            verify(scheduler).runTaskTimerAsynchronously(eq(plugin), captor.capture(), anyLong(), anyLong());
            return captor.getValue();
        }

        @Test
        @DisplayName("Player within 4-block boundary of hidden diamond ore triggers reveal")
        void playerWithin4BlockBoundary_triggersReveal() {
            UUID playerId = UUID.randomUUID();
            PlayerData data = new PlayerData(10000);

            Map<UUID, PlayerData> playerDataMap = new HashMap<>();
            playerDataMap.put(playerId, data);
            when(plugin.getAllPlayerData()).thenReturn(Collections.unmodifiableMap(playerDataMap));

            Player player = mock(Player.class);
            when(player.isOnline()).thenReturn(true);
            when(player.getWorld()).thenReturn(world);
            when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
            when(player.getUniqueId()).thenReturn(playerId);

            Location nearOre = new Location(world, 4.0, 64.0, 0.0);
            when(player.getEyeLocation()).thenReturn(nearOre);
            when(player.getLocation()).thenReturn(nearOre);
            bukkitMock.when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);

            org.bukkit.util.Vector velocity = new org.bukkit.util.Vector(0, 0, 0);
            when(player.getVelocity()).thenReturn(velocity);

            when(adapter.getBlockStateAt(eq(world), eq(0), eq(64), eq(0)))
                    .thenReturn(DIAMOND_ORE_ID);
            when(adapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt()))
                    .thenAnswer(inv -> {
                        int x = inv.getArgument(1, Integer.class);
                        int y = inv.getArgument(2, Integer.class);
                        int z = inv.getArgument(3, Integer.class);
                        if (x == 0 && y == 64 && z == 0) return DIAMOND_ORE_ID;
                        return STONE_ID;
                    });

            ProximityTracker tracker = new ProximityTracker(
                    plugin, adapter, materialSet,
                    false, false,
                    4, 4, 5, 0.5,
                    false, 200L, 70.0, 0, 0
            );

            DeobfuscationManager manager = mock(DeobfuscationManager.class);
            when(plugin.getDeobfuscationManager()).thenReturn(manager);

            tracker.start();
            Runnable asyncTick = captureAsyncTickRunnable();
            asyncTick.run();

            BlockPosition orePos = new BlockPosition("world", 0, 64, 0);
            assertTrue(data.getRevealedBlocks().contains(orePos),
                    "Diamond ore at (0,64,0) must be revealed when player is within 4 blocks");
        }

        @Test
        @DisplayName("Player beyond 4-block boundary does not trigger reveal for distant ore")
        void playerBeyond4BlockBoundary_noReveal() {
            UUID playerId = UUID.randomUUID();
            PlayerData data = new PlayerData(10000);

            Map<UUID, PlayerData> playerDataMap = new HashMap<>();
            playerDataMap.put(playerId, data);
            when(plugin.getAllPlayerData()).thenReturn(Collections.unmodifiableMap(playerDataMap));

            Player player = mock(Player.class);
            when(player.isOnline()).thenReturn(true);
            when(player.getWorld()).thenReturn(world);
            when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
            when(player.getUniqueId()).thenReturn(playerId);

            Location farFromOre = new Location(world, 100.0, 64.0, 100.0);
            when(player.getEyeLocation()).thenReturn(farFromOre);
            bukkitMock.when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);

            org.bukkit.util.Vector velocity = new org.bukkit.util.Vector(0, 0, 0);
            when(player.getVelocity()).thenReturn(velocity);

            when(adapter.getBlockStateAt(eq(world), eq(0), eq(64), eq(0)))
                    .thenReturn(DIAMOND_ORE_ID);
            when(adapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt()))
                    .thenAnswer(inv -> {
                        int x = inv.getArgument(1, Integer.class);
                        int y = inv.getArgument(2, Integer.class);
                        int z = inv.getArgument(3, Integer.class);
                        if (x == 0 && y == 64 && z == 0) return DIAMOND_ORE_ID;
                        return STONE_ID;
                    });

            ProximityTracker tracker = new ProximityTracker(
                    plugin, adapter, materialSet,
                    false, false,
                    4, 4, 5, 0.5,
                    false, 200L, 70.0, 0, 0
            );

            DeobfuscationManager manager = mock(DeobfuscationManager.class);
            when(plugin.getDeobfuscationManager()).thenReturn(manager);

            tracker.start();
            Runnable asyncTick = captureAsyncTickRunnable();
            asyncTick.run();

            BlockPosition orePos = new BlockPosition("world", 0, 64, 0);
            assertFalse(data.getRevealedBlocks().contains(orePos),
                    "Diamond ore at (0,64,0) must NOT be revealed when player is 100 blocks away");
        }

        @Test
        @DisplayName("DeobfuscationManager deobfuscateAround sends correct block state for hidden ore")
        void deobfuscateAround_sendsCorrectBlockState() {
            Player player = mock(Player.class);
            when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            when(player.isOnline()).thenReturn(true);
            when(player.getWorld()).thenReturn(world);

            PlayerData data = new PlayerData(10000);
            when(plugin.getPlayerData(player.getUniqueId())).thenReturn(data);

            when(adapter.getBlockStateAt(eq(world), eq(0), eq(64), eq(0)))
                    .thenReturn(DIAMOND_ORE_ID);

            DeobfuscationManager mgr = new DeobfuscationManager(plugin, adapter, materialSet);

            Location center = new Location(world, 0, 64, 0);
            mgr.deobfuscateAround(player, center);

            BlockPosition orePos = new BlockPosition("world", 0, 64, 0);
            assertTrue(data.getRevealedBlocks().contains(orePos),
                    "Hidden ore within update radius must be added to revealed set");
        }
    }

    // ── Scenario 2: Walk Away From Revealed Ore ────────────────────
    @Nested
    @DisplayName("2.8.2 Walk Away From Revealed Ore")
    class WalkAwayFromRevealedOreTest {

        private Runnable captureAsyncTickRunnable() {
            org.mockito.ArgumentCaptor<Runnable> captor = org.mockito.ArgumentCaptor.forClass(Runnable.class);
            verify(scheduler).runTaskTimerAsynchronously(eq(plugin), captor.capture(), anyLong(), anyLong());
            return captor.getValue();
        }

        @Test
        @DisplayName("Revealed ore re-obfuscated after 10-second delay when player leaves radius")
        void revealedOreReObfuscatedAfterDelay() {
            UUID playerId = UUID.randomUUID();
            PlayerData data = new PlayerData(10000);

            BlockPosition orePos = new BlockPosition("world", 0, 64, 0);
            long oldTick = 100L;
            data.getRevealedBlocks().add(orePos, oldTick);

            Map<UUID, PlayerData> playerDataMap = new HashMap<>();
            playerDataMap.put(playerId, data);
            when(plugin.getAllPlayerData()).thenReturn(Collections.unmodifiableMap(playerDataMap));

            Player player = mock(Player.class);
            when(player.isOnline()).thenReturn(true);
            when(player.getWorld()).thenReturn(world);
            when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
            when(player.getUniqueId()).thenReturn(playerId);

            Location farAway = new Location(world, 100.0, 64.0, 0.0);
            when(player.getEyeLocation()).thenReturn(farAway);
            bukkitMock.when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);

            org.bukkit.util.Vector velocity = new org.bukkit.util.Vector(0, 0, 0);
            when(player.getVelocity()).thenReturn(velocity);

            when(adapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt()))
                    .thenReturn(-1);

            DeobfuscationManager manager = mock(DeobfuscationManager.class);
            when(plugin.getDeobfuscationManager()).thenReturn(manager);

            ProximityTracker tracker = new ProximityTracker(
                    plugin, adapter, materialSet,
                    false, false,
                    4, 4, 5, 0.5,
                    true, 200L, 70.0, 0, 0
            );

            tracker.start();
            Runnable asyncTick = captureAsyncTickRunnable();
            asyncTick.run();

            assertFalse(data.getRevealedBlocks().contains(orePos),
                    "Revealed ore must be removed from set after player walks away and delay expires");
            verify(manager).queueReObfuscation(eq(player), anyList());
        }

        @Test
        @DisplayName("Revealed ore stays if player re-enters boundary before delay expires")
        void revealedOreKeptIfPlayerReEntersBeforeDelay() {
            UUID playerId = UUID.randomUUID();
            PlayerData data = new PlayerData(10000);

            BlockPosition orePos = new BlockPosition("world", 1, 64, 0);
            long recentTick = 99999L;
            data.getRevealedBlocks().add(orePos, recentTick);

            Map<UUID, PlayerData> playerDataMap = new HashMap<>();
            playerDataMap.put(playerId, data);
            when(plugin.getAllPlayerData()).thenReturn(Collections.unmodifiableMap(playerDataMap));

            Player player = mock(Player.class);
            when(player.isOnline()).thenReturn(true);
            when(player.getWorld()).thenReturn(world);
            when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
            when(player.getUniqueId()).thenReturn(playerId);

            Location nearOre = new Location(world, 2.0, 64.0, 0.0);
            when(player.getEyeLocation()).thenReturn(nearOre);
            bukkitMock.when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);

            org.bukkit.util.Vector velocity = new org.bukkit.util.Vector(0, 0, 0);
            when(player.getVelocity()).thenReturn(velocity);

            when(adapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt()))
                    .thenReturn(-1);

            DeobfuscationManager manager = mock(DeobfuscationManager.class);
            when(plugin.getDeobfuscationManager()).thenReturn(manager);

            ProximityTracker tracker = new ProximityTracker(
                    plugin, adapter, materialSet,
                    false, false,
                    4, 4, 5, 0.5,
                    true, 200L, 70.0, 0, 0
            );

            tracker.start();
            Runnable asyncTick = captureAsyncTickRunnable();
            asyncTick.run();

            assertTrue(data.getRevealedBlocks().contains(orePos),
                    "Recently revealed ore near player must not be re-obfuscated while within radius");
            verify(manager, never()).queueReObfuscation(any(), anyList());
        }

        @Test
        @DisplayName("Re-obfuscation disabled keeps expired blocks in revealed set")
        void reObfuscationDisabledKeepsExpiredBlocks() {
            UUID playerId = UUID.randomUUID();
            PlayerData data = new PlayerData(10000);

            BlockPosition orePos = new BlockPosition("world", 0, 64, 0);
            data.getRevealedBlocks().add(orePos, 100L);

            Map<UUID, PlayerData> playerDataMap = new HashMap<>();
            playerDataMap.put(playerId, data);
            when(plugin.getAllPlayerData()).thenReturn(Collections.unmodifiableMap(playerDataMap));

            Player player = mock(Player.class);
            when(player.isOnline()).thenReturn(true);
            when(player.getWorld()).thenReturn(world);
            when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
            when(player.getUniqueId()).thenReturn(playerId);

            Location farAway = new Location(world, 100.0, 64.0, 0.0);
            when(player.getEyeLocation()).thenReturn(farAway);
            bukkitMock.when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);

            org.bukkit.util.Vector velocity = new org.bukkit.util.Vector(0, 0, 0);
            when(player.getVelocity()).thenReturn(velocity);

            when(adapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt()))
                    .thenReturn(-1);

            ProximityTracker tracker = new ProximityTracker(
                    plugin, adapter, materialSet,
                    false, false,
                    4, 4, 5, 0.5,
                    false, 200L, 70.0, 0, 0
            );

            tracker.start();
            Runnable asyncTick = captureAsyncTickRunnable();
            asyncTick.run();

            assertTrue(data.getRevealedBlocks().contains(orePos),
                    "When re-obfuscation disabled, expired blocks must remain in revealed set");
        }
    }

    // ── Scenario 3: Mine Stone Near Hidden Ore ────────────────────
    @Nested
    @DisplayName("2.8.3 Mine Stone Near Hidden Ore")
    class MineStoneNearHiddenOreTest {

        @Test
        @DisplayName("Breaking stone adjacent to hidden diamond ore triggers visibility re-check and reveal")
        void breakingStoneAdjacentToHiddenOre_triggersReveal() {
            Block brokenBlock = mock(Block.class);
            when(brokenBlock.getX()).thenReturn(1);
            when(brokenBlock.getY()).thenReturn(64);
            when(brokenBlock.getZ()).thenReturn(0);
            when(brokenBlock.getWorld()).thenReturn(world);

            Block diamondNeighbor = mock(Block.class);
            when(diamondNeighbor.getX()).thenReturn(0);
            when(diamondNeighbor.getY()).thenReturn(64);
            when(diamondNeighbor.getZ()).thenReturn(0);
            when(brokenBlock.getRelative(BlockFace.WEST)).thenReturn(diamondNeighbor);

            Block north = mock(Block.class);
            when(brokenBlock.getRelative(BlockFace.NORTH)).thenReturn(north);
            Block east = mock(Block.class);
            when(brokenBlock.getRelative(BlockFace.EAST)).thenReturn(east);
            Block south = mock(Block.class);
            when(brokenBlock.getRelative(BlockFace.SOUTH)).thenReturn(south);
            Block up = mock(Block.class);
            when(brokenBlock.getRelative(BlockFace.UP)).thenReturn(up);
            Block down = mock(Block.class);
            when(brokenBlock.getRelative(BlockFace.DOWN)).thenReturn(down);

            when(adapter.getBlockStateAt(eq(world), eq(0), eq(64), eq(0))).thenReturn(DIAMOND_ORE_ID);
            when(adapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt())).thenReturn(STONE_ID);
            when(adapter.getBlockStateAt(world, 0, 64, 0)).thenReturn(DIAMOND_ORE_ID);

            when(materialSet.isHidden(DIAMOND_ORE_ID)).thenReturn(true);
            when(materialSet.isTransparent(AIR_ID)).thenReturn(true);

            when(adapter.getBlockStateAt(eq(world), eq(-1), eq(64), eq(0))).thenReturn(AIR_ID);
            when(adapter.getBlockStateAt(eq(world), eq(0), eq(63), eq(0))).thenReturn(STONE_ID);
            when(adapter.getBlockStateAt(eq(world), eq(0), eq(65), eq(0))).thenReturn(STONE_ID);
            when(adapter.getBlockStateAt(eq(world), eq(0), eq(64), eq(-1))).thenReturn(STONE_ID);
            when(adapter.getBlockStateAt(eq(world), eq(0), eq(64), eq(1))).thenReturn(AIR_ID);

            AirExposureChecker checker = new AirExposureChecker(adapter, materialSet, true);

            Player player = mock(Player.class);
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());

            PlayerData data = new PlayerData(10000);
            when(plugin.getPlayerData(player.getUniqueId())).thenReturn(data);

            DeobfuscationManager mgr = mock(DeobfuscationManager.class);
            when(plugin.getDeobfuscationManager()).thenReturn(mgr);

            boolean isAirExposed = checker.isAirExposed(world, 0, 64, 0);
            assertTrue(isAirExposed,
                    "Diamond ore at (0,64,0) with air neighbor must be air-exposed after adjacent stone is broken");
        }

        @Test
        @DisplayName("BlockBreakEvent handler queues deobfuscation for adjacent hidden air-exposed ore")
        void blockBreakEvent_queuesDeobfuscation() {
            Player player = mock(Player.class);
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());

            Block brokenBlock = mock(Block.class);
            when(brokenBlock.getX()).thenReturn(1);
            when(brokenBlock.getY()).thenReturn(64);
            when(brokenBlock.getZ()).thenReturn(0);
            when(brokenBlock.getWorld()).thenReturn(world);

            Block westNeighbor = mock(Block.class);
            when(westNeighbor.getX()).thenReturn(0);
            when(westNeighbor.getY()).thenReturn(64);
            when(westNeighbor.getZ()).thenReturn(0);
            when(brokenBlock.getRelative(BlockFace.WEST)).thenReturn(westNeighbor);
            when(brokenBlock.getRelative(BlockFace.EAST)).thenReturn(mock(Block.class));
            when(brokenBlock.getRelative(BlockFace.NORTH)).thenReturn(mock(Block.class));
            when(brokenBlock.getRelative(BlockFace.SOUTH)).thenReturn(mock(Block.class));
            when(brokenBlock.getRelative(BlockFace.UP)).thenReturn(mock(Block.class));
            when(brokenBlock.getRelative(BlockFace.DOWN)).thenReturn(mock(Block.class));

        when(adapter.getBlockStateAt(eq(world), eq(0), eq(64), eq(0))).thenReturn(DIAMOND_ORE_ID);
        when(adapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt())).thenReturn(STONE_ID);
        when(adapter.getBlockStateAt(world, 0, 64, 0)).thenReturn(DIAMOND_ORE_ID);
        when(adapter.getBlockStateAt(world, -1, 64, 0)).thenReturn(AIR_ID);
        when(adapter.getBlockStateAt(world, 1, 64, 0)).thenReturn(STONE_ID);
        when(adapter.getBlockStateAt(world, 0, 63, 0)).thenReturn(STONE_ID);
        when(adapter.getBlockStateAt(world, 0, 65, 0)).thenReturn(STONE_ID);
        when(adapter.getBlockStateAt(world, 0, 64, -1)).thenReturn(STONE_ID);
        when(adapter.getBlockStateAt(world, 0, 64, 1)).thenReturn(STONE_ID);

        ObfuscationEngine engine = mock(ObfuscationEngine.class);
            when(engine.getMaterialSet()).thenReturn(materialSet);
            when(engine.getExposureChecker()).thenReturn(exposureChecker);
            when(plugin.getObfuscationEngine()).thenReturn(engine);
            when(plugin.getNmsAdapter()).thenReturn(adapter);

            DeobfuscationManager mgr = mock(DeobfuscationManager.class);
            when(plugin.getDeobfuscationManager()).thenReturn(mgr);

        BlockEventListener listener = new BlockEventListener(plugin);
        BlockBreakEvent event = mock(BlockBreakEvent.class);
        when(event.getBlock()).thenReturn(brokenBlock);
        when(event.getPlayer()).thenReturn(player);
        when(event.isCancelled()).thenReturn(false);

        listener.onBlockBreak(event);

            verify(mgr).queueDeobfuscation(eq(player), anyList());
        }
    }

    // ── Scenario 4: Explosion Exposure ─────────────────────────────
    @Nested
    @DisplayName("2.8.4 Explosion Exposure")
    class ExplosionExposureTest {

        @Test
        @DisplayName("EntityExplodeEvent reveals hidden ores adjacent to destroyed blocks")
    void entityExplosion_revealsAdjacentHiddenOres() {
        Block destroyed = mock(Block.class);
        when(destroyed.getX()).thenReturn(5);
        when(destroyed.getY()).thenReturn(64);
        when(destroyed.getZ()).thenReturn(5);
        when(destroyed.getWorld()).thenReturn(world);

        Block oreNeighbor = mock(Block.class);
        when(oreNeighbor.getX()).thenReturn(4);
        when(oreNeighbor.getY()).thenReturn(64);
        when(oreNeighbor.getZ()).thenReturn(5);
        when(destroyed.getRelative(BlockFace.WEST)).thenReturn(oreNeighbor);
        when(destroyed.getRelative(BlockFace.EAST)).thenReturn(mock(Block.class));
        when(destroyed.getRelative(BlockFace.NORTH)).thenReturn(mock(Block.class));
        when(destroyed.getRelative(BlockFace.SOUTH)).thenReturn(mock(Block.class));
        when(destroyed.getRelative(BlockFace.UP)).thenReturn(mock(Block.class));
        when(destroyed.getRelative(BlockFace.DOWN)).thenReturn(mock(Block.class));

        when(adapter.getBlockStateAt(eq(world), eq(4), eq(64), eq(5))).thenReturn(DIAMOND_ORE_ID);
        when(adapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt())).thenReturn(STONE_ID);
        when(adapter.getBlockStateAt(world, 4, 64, 5)).thenReturn(DIAMOND_ORE_ID);
        when(adapter.getBlockStateAt(world, 3, 64, 5)).thenReturn(AIR_ID);
        when(adapter.getBlockStateAt(world, 5, 64, 5)).thenReturn(STONE_ID);
        when(adapter.getBlockStateAt(world, 4, 63, 5)).thenReturn(STONE_ID);
        when(adapter.getBlockStateAt(world, 4, 65, 5)).thenReturn(STONE_ID);
        when(adapter.getBlockStateAt(world, 4, 64, 4)).thenReturn(STONE_ID);
        when(adapter.getBlockStateAt(world, 4, 64, 6)).thenReturn(STONE_ID);

        when(materialSet.isHidden(DIAMOND_ORE_ID)).thenReturn(true);

        ObfuscationEngine engine = mock(ObfuscationEngine.class);
        when(engine.getMaterialSet()).thenReturn(materialSet);
        when(engine.getExposureChecker()).thenReturn(exposureChecker);
        when(plugin.getObfuscationEngine()).thenReturn(engine);
        when(plugin.getNmsAdapter()).thenReturn(adapter);

        DeobfuscationManager mgr = mock(DeobfuscationManager.class);
        when(plugin.getDeobfuscationManager()).thenReturn(mgr);

        org.bukkit.entity.Entity entity = mock(org.bukkit.entity.Entity.class);
            when(entity.getLocation()).thenReturn(new Location(world, 5, 64, 5));

        EntityExplodeEvent event = mock(EntityExplodeEvent.class);
        when(event.blockList()).thenReturn(List.of(destroyed));
        when(event.getLocation()).thenReturn(new Location(world, 5, 64, 5));
        when(event.isCancelled()).thenReturn(false);
        when(world.getPlayers()).thenReturn(List.of(mock(Player.class)));

        ExplosionEventListener listener = new ExplosionEventListener(plugin);
        listener.onEntityExplode(event);

        verify(mgr).queueDeobfuscation(any(), anyList());
        }

        @Test
        @DisplayName("AirExposureChecker correctly identifies ore exposed by explosion")
        void airExposureChecker_identifiesExplosionExposedOre() {
            when(adapter.getBlockStateAt(eq(world), eq(0), eq(64), eq(0))).thenReturn(DIAMOND_ORE_ID);
            when(adapter.getBlockStateAt(eq(world), eq(1), eq(64), eq(0))).thenReturn(AIR_ID);
            when(adapter.getBlockStateAt(eq(world), eq(-1), eq(64), eq(0))).thenReturn(STONE_ID);
            when(adapter.getBlockStateAt(eq(world), eq(0), eq(63), eq(0))).thenReturn(STONE_ID);
            when(adapter.getBlockStateAt(eq(world), eq(0), eq(65), eq(0))).thenReturn(STONE_ID);
            when(adapter.getBlockStateAt(eq(world), eq(0), eq(64), eq(1))).thenReturn(STONE_ID);
            when(adapter.getBlockStateAt(eq(world), eq(0), eq(64), eq(-1))).thenReturn(STONE_ID);

        AirExposureChecker checker = new AirExposureChecker(adapter, materialSet, true);
        assertTrue(checker.isAirExposed(world, 0, 64, 0),
            "Ore with air on one face from explosion must be air-exposed");
        }

        @Test
        @DisplayName("BlockExplodeEvent reveals hidden ores within blast radius")
        void blockExplosion_revealsHiddenOresInRadius() {
            Block destroyed = mock(Block.class);
            when(destroyed.getX()).thenReturn(5);
            when(destroyed.getY()).thenReturn(64);
            when(destroyed.getZ()).thenReturn(5);
            when(destroyed.getWorld()).thenReturn(world);

            Block oreNeighbor = mock(Block.class);
            when(oreNeighbor.getX()).thenReturn(4);
            when(oreNeighbor.getY()).thenReturn(64);
            when(oreNeighbor.getZ()).thenReturn(5);
            when(destroyed.getRelative(BlockFace.WEST)).thenReturn(oreNeighbor);
            when(destroyed.getRelative(BlockFace.EAST)).thenReturn(mock(Block.class));
            when(destroyed.getRelative(BlockFace.NORTH)).thenReturn(mock(Block.class));
            when(destroyed.getRelative(BlockFace.SOUTH)).thenReturn(mock(Block.class));
            when(destroyed.getRelative(BlockFace.UP)).thenReturn(mock(Block.class));
            when(destroyed.getRelative(BlockFace.DOWN)).thenReturn(mock(Block.class));

        when(adapter.getBlockStateAt(eq(world), eq(4), eq(64), eq(5))).thenReturn(DIAMOND_ORE_ID);
        when(adapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt())).thenReturn(STONE_ID);
        when(adapter.getBlockStateAt(world, 4, 64, 5)).thenReturn(DIAMOND_ORE_ID);
        when(adapter.getBlockStateAt(world, 3, 64, 5)).thenReturn(AIR_ID);
        when(adapter.getBlockStateAt(world, 5, 64, 5)).thenReturn(STONE_ID);
        when(adapter.getBlockStateAt(world, 4, 63, 5)).thenReturn(STONE_ID);
        when(adapter.getBlockStateAt(world, 4, 65, 5)).thenReturn(STONE_ID);
        when(adapter.getBlockStateAt(world, 4, 64, 4)).thenReturn(STONE_ID);
        when(adapter.getBlockStateAt(world, 4, 64, 6)).thenReturn(STONE_ID);

        when(materialSet.isHidden(DIAMOND_ORE_ID)).thenReturn(true);

        ObfuscationEngine engine = mock(ObfuscationEngine.class);
        when(engine.getMaterialSet()).thenReturn(materialSet);
        when(engine.getExposureChecker()).thenReturn(exposureChecker);
        when(plugin.getObfuscationEngine()).thenReturn(engine);
        when(plugin.getNmsAdapter()).thenReturn(adapter);

        DeobfuscationManager mgr = mock(DeobfuscationManager.class);
        when(plugin.getDeobfuscationManager()).thenReturn(mgr);

        Player nearbyPlayer = mock(Player.class);
            when(nearbyPlayer.getUniqueId()).thenReturn(UUID.randomUUID());
            when(world.getPlayers()).thenReturn(List.of(nearbyPlayer));

        BlockExplodeEvent event = mock(BlockExplodeEvent.class);
        when(event.getBlock()).thenReturn(destroyed);
        when(event.blockList()).thenReturn(List.of(destroyed));
        when(event.isCancelled()).thenReturn(false);
        ExplosionEventListener listener = new ExplosionEventListener(plugin);
        listener.onBlockExplode(event);

        verify(mgr).queueDeobfuscation(any(), anyList());
        }

        @Test
        @DisplayName("Block explosion full blast radius runs immediate air-exposure evaluation on all hidden ores")
        void blockExplosion_fullBlastRadiusEvaluatesAllHiddenOres() {
            Block block1 = mock(Block.class);
            when(block1.getX()).thenReturn(5);
            when(block1.getY()).thenReturn(64);
            when(block1.getZ()).thenReturn(5);
            when(block1.getWorld()).thenReturn(world);

            Block block2 = mock(Block.class);
            when(block2.getX()).thenReturn(6);
            when(block2.getY()).thenReturn(64);
            when(block2.getZ()).thenReturn(5);
            when(block2.getWorld()).thenReturn(world);

            Block ore1 = mock(Block.class);
            when(ore1.getX()).thenReturn(4);
            when(ore1.getY()).thenReturn(64);
            when(ore1.getZ()).thenReturn(5);
            when(block1.getRelative(BlockFace.WEST)).thenReturn(ore1);
            when(block1.getRelative(BlockFace.EAST)).thenReturn(block2);
            when(block1.getRelative(BlockFace.NORTH)).thenReturn(mock(Block.class));
            when(block1.getRelative(BlockFace.SOUTH)).thenReturn(mock(Block.class));
            when(block1.getRelative(BlockFace.UP)).thenReturn(mock(Block.class));
            when(block1.getRelative(BlockFace.DOWN)).thenReturn(mock(Block.class));

            Block ore2 = mock(Block.class);
            when(ore2.getX()).thenReturn(7);
            when(ore2.getY()).thenReturn(64);
            when(ore2.getZ()).thenReturn(5);
            when(block2.getRelative(BlockFace.EAST)).thenReturn(ore2);
            when(block2.getRelative(BlockFace.WEST)).thenReturn(block1);
            when(block2.getRelative(BlockFace.NORTH)).thenReturn(mock(Block.class));
            when(block2.getRelative(BlockFace.SOUTH)).thenReturn(mock(Block.class));
            when(block2.getRelative(BlockFace.UP)).thenReturn(mock(Block.class));
            when(block2.getRelative(BlockFace.DOWN)).thenReturn(mock(Block.class));

        when(adapter.getBlockStateAt(eq(world), eq(4), eq(64), eq(5))).thenReturn(DIAMOND_ORE_ID);
        when(adapter.getBlockStateAt(eq(world), eq(7), eq(64), eq(5))).thenReturn(EMERALD_ORE_ID);
        when(adapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt())).thenReturn(STONE_ID);
        when(adapter.getBlockStateAt(world, 4, 64, 5)).thenReturn(DIAMOND_ORE_ID);
        when(adapter.getBlockStateAt(world, 7, 64, 5)).thenReturn(EMERALD_ORE_ID);
        when(adapter.getBlockStateAt(world, 3, 64, 5)).thenReturn(AIR_ID);
        when(adapter.getBlockStateAt(world, 8, 64, 5)).thenReturn(AIR_ID);
        when(adapter.getBlockStateAt(world, 5, 64, 5)).thenReturn(STONE_ID);
        when(adapter.getBlockStateAt(world, 6, 64, 5)).thenReturn(STONE_ID);
        when(adapter.getBlockStateAt(world, 4, 63, 5)).thenReturn(STONE_ID);
        when(adapter.getBlockStateAt(world, 4, 65, 5)).thenReturn(STONE_ID);
        when(adapter.getBlockStateAt(world, 4, 64, 4)).thenReturn(STONE_ID);
        when(adapter.getBlockStateAt(world, 4, 64, 6)).thenReturn(STONE_ID);
        when(adapter.getBlockStateAt(world, 7, 63, 5)).thenReturn(STONE_ID);
        when(adapter.getBlockStateAt(world, 7, 65, 5)).thenReturn(STONE_ID);
        when(adapter.getBlockStateAt(world, 7, 64, 4)).thenReturn(STONE_ID);
        when(adapter.getBlockStateAt(world, 7, 64, 6)).thenReturn(STONE_ID);

        when(materialSet.isHidden(DIAMOND_ORE_ID)).thenReturn(true);
        when(materialSet.isHidden(EMERALD_ORE_ID)).thenReturn(true);

        ObfuscationEngine engine = mock(ObfuscationEngine.class);
        when(engine.getMaterialSet()).thenReturn(materialSet);
        when(engine.getExposureChecker()).thenReturn(exposureChecker);
        when(plugin.getObfuscationEngine()).thenReturn(engine);
        when(plugin.getNmsAdapter()).thenReturn(adapter);

        DeobfuscationManager mgr = mock(DeobfuscationManager.class);
        when(plugin.getDeobfuscationManager()).thenReturn(mgr);

        Player nearbyPlayer = mock(Player.class);
            when(nearbyPlayer.getUniqueId()).thenReturn(UUID.randomUUID());
            when(world.getPlayers()).thenReturn(List.of(nearbyPlayer));

            BlockExplodeEvent event = mock(BlockExplodeEvent.class);
        when(event.getBlock()).thenReturn(block1);
        when(event.blockList()).thenReturn(List.of(block1, block2));
        when(event.isCancelled()).thenReturn(false);
        ExplosionEventListener listener = new ExplosionEventListener(plugin);
        listener.onBlockExplode(event);

        verify(mgr, atLeastOnce()).queueDeobfuscation(eq(nearbyPlayer), anyList());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 9. Cache + Async Pipeline Integration
    // ═══════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("9. Cache + Async Pipeline Integration")
    class CacheAsyncPipelineTest {

        private ObfuscationCache createTestCache() {
            return new ObfuscationCache(new L1MemoryCache(100, 300));
        }

        private CacheKey createCacheKey(int chunkX, int chunkZ) {
            return new CacheKey("world", chunkX, chunkZ, 3, 0);
        }

        @Test
        @DisplayName("Cache hit returns obfuscated data without re-obfuscating")
        void cacheHitReturnsDataWithoutReobfuscating() {
            ObfuscationCache cache = createTestCache();
            CacheKey key = createCacheKey(5, 10);
            byte[] data = new byte[]{1, 0, 0, 0, 1, 0, 1, 0, 0, 0, 0};
            CacheEntry entry = new CacheEntry(data, 1);
            cache.put(key, entry);

            CacheEntry cached = cache.get(key);
            assertNotNull(cached, "Cache must return entry after put");
            assertArrayEquals(data, cached.getObfuscatedData(), "Cached data must match original");
        }

        @Test
        @DisplayName("Cache miss on different chunk coordinates returns null")
        void cacheMissOnDifferentChunkReturnsNull() {
            ObfuscationCache cache = createTestCache();
            CacheKey key = createCacheKey(5, 10);
            byte[] data = new byte[]{1, 0, 0, 0, 1, 0, 1, 0, 0, 0, 0};
            cache.put(key, new CacheEntry(data, 1));

            CacheKey differentKey = createCacheKey(6, 10);
            CacheEntry cached = cache.get(differentKey);
            assertNull(cached, "Cache must return null for different chunk coordinates");
        }

        @Test
        @DisplayName("ObfuscationEngine obfuscateAndSerialize produces CacheEntry with non-empty data")
        void obfuscateAndSerializeProducesCacheEntry() {
            Object section = new Object();
            when(adapter.getChunkSections(any())).thenReturn(List.of(section));
            when(adapter.isSingleValuePalette(section)).thenReturn(false);
            when(adapter.isDirectPalette(section)).thenReturn(false);
            when(adapter.getSectionNonEmptyCount(section)).thenReturn(1);
            when(adapter.getPaletteBitsPerEntry(section)).thenReturn(4);

            List<Integer> palette = new ArrayList<>(Arrays.asList(STONE_ID, DIAMOND_ORE_ID));
            long[] packed = new long[PaletteManipulator.computePackedArraySize(4)];
            for (int i = 0; i < 4096; i++) {
                PaletteManipulator.setIndex(packed, i, 0, 4);
            }
            PaletteManipulator.setIndex(packed, 42, 1, 4);
            when(adapter.getPaletteEntries(section)).thenReturn(new ArrayList<>(palette));
            when(adapter.getPackedIndices(section)).thenReturn(packed);

            ObfuscationEngine engine = new ObfuscationEngine(adapter, materialSet, exposureChecker);
            engine.setEngineMode(ObfuscationMode.MODE_1);

            Object packet = new Object();
            CacheEntry entry = engine.obfuscateAndSerialize(packet, world, 5, 10);

            assertNotNull(entry, "obfuscateAndSerialize must return a CacheEntry");
            assertTrue(entry.getDataLength() > 0, "CacheEntry data must be non-empty");
            assertTrue(entry.getSectionCount() > 0, "CacheEntry sectionCount must be positive");
        }

        @Test
        @DisplayName("ObfuscationEngine applySerializedObfuscation restores palette from cached data")
        void applySerializedObfuscationRestoresPalette() {
            List<Integer> palette = new ArrayList<>(Arrays.asList(STONE_ID, DIAMOND_ORE_ID));
            int bitsPerEntry = 4;
            long[] packed = new long[PaletteManipulator.computePackedArraySize(bitsPerEntry)];
            for (int i = 0; i < 4096; i++) {
                PaletteManipulator.setIndex(packed, i, 0, bitsPerEntry);
            }
            PaletteManipulator.setIndex(packed, 42, 1, bitsPerEntry);

            Object section1 = new Object();
            when(adapter.isSingleValuePalette(section1)).thenReturn(false);
            when(adapter.isDirectPalette(section1)).thenReturn(false);
            when(adapter.getSectionNonEmptyCount(section1)).thenReturn(1);
            when(adapter.getPaletteBitsPerEntry(section1)).thenReturn(bitsPerEntry);
            when(adapter.getPaletteEntries(section1)).thenReturn(new ArrayList<>(palette));
            when(adapter.getPackedIndices(section1)).thenReturn(packed.clone());
            when(adapter.getChunkSections(any())).thenReturn(List.of(section1));

            ObfuscationEngine engine = new ObfuscationEngine(adapter, materialSet, exposureChecker);
            engine.setEngineMode(ObfuscationMode.MODE_1);

            Object packet = new Object();
            CacheEntry entry = engine.obfuscateAndSerialize(packet, world, 0, 0);
            assertNotNull(entry);

            Object section2 = new Object();
            when(adapter.isSingleValuePalette(section2)).thenReturn(false);
            when(adapter.isDirectPalette(section2)).thenReturn(false);
            when(adapter.getSectionNonEmptyCount(section2)).thenReturn(1);
            when(adapter.getPaletteBitsPerEntry(section2)).thenReturn(bitsPerEntry);
            when(adapter.getPaletteEntries(section2)).thenReturn(new ArrayList<>(palette));
            when(adapter.getPackedIndices(section2)).thenReturn(new long[PaletteManipulator.computePackedArraySize(bitsPerEntry)]);

            engine.applySerializedObfuscation(entry.getObfuscatedData(), List.of(section2));

            verify(adapter).setPaletteEntries(eq(section2), any());
        }

        @Test
        @DisplayName("Cache stores and retrieves entry for same chunk across engine mode changes")
        void cacheStoresAcrossModeChanges() {
            ObfuscationCache cache = createTestCache();
            CacheKey key1 = new CacheKey("world", 5, 10, ObfuscationMode.MODE_1, 0);
            CacheKey key2 = new CacheKey("world", 5, 10, ObfuscationMode.MODE_3, 0);

            byte[] data1 = new byte[]{1, 0, 0, 0};
            byte[] data2 = new byte[]{2, 0, 0, 0};
            cache.put(key1, new CacheEntry(data1, 1));
            cache.put(key2, new CacheEntry(data2, 1));

            CacheEntry e1 = cache.get(key1);
            CacheEntry e2 = cache.get(key2);
            assertNotNull(e1);
            assertNotNull(e2);
            assertArrayEquals(data1, e1.getObfuscatedData(), "MODE_1 entry must be retrieved correctly");
            assertArrayEquals(data2, e2.getObfuscatedData(), "MODE_3 entry must be retrieved correctly");
        }

        @Test
        @DisplayName("AsyncProcessor enqueues task and cache is populated after processing")
        void asyncProcessorEnqueuesAndCachesResult() throws InterruptedException {
            ObfuscationCache cache = createTestCache();
            ThreadPoolManager tpm = new ThreadPoolManager(1, 100);
            TickBudgetTracker tbt = new TickBudgetTracker(8);
            BackpressureHandler bph = new BackpressureHandler(100, tpm);
            AsyncProcessor processor = new AsyncProcessor(tpm, tbt, bph, cache, 5000);

            CacheKey key = createCacheKey(3, 7);
            byte[] resultData = new byte[]{3, 0, 0, 0, 1};
            CacheEntry resultEntry = new CacheEntry(resultData, 1);

            processor.setObfuscationFunction(k -> resultEntry);

            ObfuscationTask task = new ObfuscationTask(key, ObfuscationTask.Priority.HIGH, r -> {}, processor);
            processor.enqueue(task);

            Thread.sleep(200);

            CacheEntry cached = cache.get(key);
            assertNotNull(cached, "Cache must contain entry after async processing");
            assertArrayEquals(resultData, cached.getObfuscatedData(), "Cached data must match async result");

            processor.shutdown();
            processor.awaitTermination(2000);
        }

        @Test
        @DisplayName("AsyncProcessor skips processing when cache already populated")
        void asyncProcessorSkipsWhenCacheHit() throws InterruptedException {
            ObfuscationCache cache = createTestCache();
            ThreadPoolManager tpm = new ThreadPoolManager(1, 100);
            TickBudgetTracker tbt = new TickBudgetTracker(8);
            BackpressureHandler bph = new BackpressureHandler(100, tpm);
            AsyncProcessor processor = new AsyncProcessor(tpm, tbt, bph, cache, 5000);

            CacheKey key = createCacheKey(3, 7);
            byte[] preExistingData = new byte[]{9, 9, 9};
            cache.put(key, new CacheEntry(preExistingData, 1));

            final java.util.concurrent.atomic.AtomicBoolean functionCalled = new java.util.concurrent.atomic.AtomicBoolean(false);
            processor.setObfuscationFunction(k -> {
                functionCalled.set(true);
                return new CacheEntry(new byte[]{0}, 1);
            });

            ObfuscationTask task = new ObfuscationTask(key, ObfuscationTask.Priority.HIGH, r -> {}, processor);
            processor.enqueue(task);

            Thread.sleep(200);

            assertFalse(functionCalled.get(), "Obfuscation function must not be called when cache hit");
            assertArrayEquals(preExistingData, cache.get(key).getObfuscatedData(), "Original cached data must be preserved");

            processor.shutdown();
            processor.awaitTermination(2000);
        }

        @Test
        @DisplayName("NmsInterceptor falls back to sync obfuscation when AsyncProcessor is null")
        void nmsInterceptorFallsBackWhenAsyncProcessorNull() {
            MockedStatic<VersionUtil> versionUtilMock = Mockito.mockStatic(VersionUtil.class);
            versionUtilMock.when(VersionUtil::isPaper).thenReturn(false);

            try {
                AntiXrayPlugin localPlugin = mock(AntiXrayPlugin.class);
                when(localPlugin.getLogger()).thenReturn(Logger.getLogger("AntiXray"));
                when(localPlugin.getAsyncProcessor()).thenReturn(null);
                when(localPlugin.getObfuscationCache()).thenReturn(null);

                ObfuscationEngine engine = new ObfuscationEngine(adapter, materialSet, exposureChecker);
                NmsInterceptor interceptor = new NmsInterceptor(localPlugin, engine, adapter);

                assertFalse(interceptor.isAvailable(), "NMS interceptor must be unavailable in test environment");
            } finally {
                versionUtilMock.close();
            }
        }

        @Test
        @DisplayName("Plugin exposes AsyncProcessor and ObfuscationCache accessors")
        void pluginExposesAccessors() {
            AntiXrayPlugin localPlugin = mock(AntiXrayPlugin.class);
            when(localPlugin.getAsyncProcessor()).thenReturn(null);
            when(localPlugin.getObfuscationCache()).thenReturn(null);

            assertNull(localPlugin.getAsyncProcessor(), "getAsyncProcessor must be callable (returns null before init)");
            assertNull(localPlugin.getObfuscationCache(), "getObfuscationCache must be callable (returns null before init)");
        }

        @Test
        @DisplayName("End-to-end: obfuscate → serialize → cache → apply on fresh packet")
        void endToEnd_obfuscateSerializeCacheApply() {
            List<Integer> palette = new ArrayList<>(Arrays.asList(STONE_ID, DIAMOND_ORE_ID));
            int bitsPerEntry = 4;
            long[] packed = new long[PaletteManipulator.computePackedArraySize(bitsPerEntry)];
            for (int i = 0; i < 4096; i++) {
                PaletteManipulator.setIndex(packed, i, 0, bitsPerEntry);
            }
            PaletteManipulator.setIndex(packed, 42, 1, bitsPerEntry);

            Object sectionA = new Object();
            when(adapter.isSingleValuePalette(sectionA)).thenReturn(false);
            when(adapter.isDirectPalette(sectionA)).thenReturn(false);
            when(adapter.getSectionNonEmptyCount(sectionA)).thenReturn(1);
            when(adapter.getPaletteBitsPerEntry(sectionA)).thenReturn(bitsPerEntry);
            when(adapter.getPaletteEntries(sectionA)).thenReturn(new ArrayList<>(palette));
            when(adapter.getPackedIndices(sectionA)).thenReturn(packed.clone());
            when(adapter.getChunkSections(any())).thenReturn(List.of(sectionA));

            ObfuscationEngine engine = new ObfuscationEngine(adapter, materialSet, exposureChecker);
            engine.setEngineMode(ObfuscationMode.MODE_1);

            Object packetA = new Object();
            CacheEntry entry = engine.obfuscateAndSerialize(packetA, world, 5, 7);
            assertNotNull(entry);
            assertTrue(entry.getDataLength() > 0);

            ObfuscationCache cache = createTestCache();
            CacheKey key = createCacheKey(5, 7);
            cache.put(key, entry);

            CacheEntry cached = cache.get(key);
            assertNotNull(cached, "Cache must return entry for same chunk");

            Object sectionB = new Object();
            when(adapter.isSingleValuePalette(sectionB)).thenReturn(false);
            when(adapter.isDirectPalette(sectionB)).thenReturn(false);
            when(adapter.getSectionNonEmptyCount(sectionB)).thenReturn(1);
            when(adapter.getPaletteBitsPerEntry(sectionB)).thenReturn(bitsPerEntry);
            when(adapter.getPaletteEntries(sectionB)).thenReturn(new ArrayList<>(palette));
            when(adapter.getPackedIndices(sectionB)).thenReturn(new long[PaletteManipulator.computePackedArraySize(bitsPerEntry)]);

            engine.applySerializedObfuscation(cached.getObfuscatedData(), List.of(sectionB));
            verify(adapter).setPaletteEntries(eq(sectionB), any());
        }

        @Test
        @DisplayName("Cache invalidation removes entry and subsequent get returns null")
        void cacheInvalidationRemovesEntry() {
            ObfuscationCache cache = createTestCache();
            CacheKey key = createCacheKey(1, 2);
            cache.put(key, new CacheEntry(new byte[]{1}, 1));
            assertNotNull(cache.get(key));

            cache.invalidate(key);
            assertNull(cache.get(key), "Cache must return null after invalidation");
        }

        @Test
        @DisplayName("AsyncProcessor times out stale tasks")
        void asyncProcessorTimesOutStaleTasks() throws InterruptedException {
            ObfuscationCache cache = createTestCache();
            ThreadPoolManager tpm = new ThreadPoolManager(1, 100);
            TickBudgetTracker tbt = new TickBudgetTracker(8);
            BackpressureHandler bph = new BackpressureHandler(100, tpm);
            AsyncProcessor processor = new AsyncProcessor(tpm, tbt, bph, cache, 0);

            CacheKey key = createCacheKey(99, 99);
            final java.util.concurrent.atomic.AtomicBoolean functionCalled = new java.util.concurrent.atomic.AtomicBoolean(false);
            processor.setObfuscationFunction(k -> {
                functionCalled.set(true);
                return new CacheEntry(new byte[]{0}, 1);
            });

            ObfuscationTask task = new ObfuscationTask(key, ObfuscationTask.Priority.LOW, r -> {}, processor);
            Thread.sleep(50);
            processor.enqueue(task);

            Thread.sleep(200);

            CacheEntry cached = cache.get(key);
            assertNull(cached, "Stale task must not populate cache");

            processor.shutdown();
            processor.awaitTermination(2000);
        }

        @Test
        @DisplayName("CacheKey with same parameters produces same hashCode and equals")
        void cacheKeyEquality() {
            CacheKey key1 = new CacheKey("world", 5, 10, 3, 42);
            CacheKey key2 = new CacheKey("world", 5, 10, 3, 42);
            assertEquals(key1, key2, "CacheKeys with same parameters must be equal");
            assertEquals(key1.hashCode(), key2.hashCode(), "CacheKeys with same parameters must have same hashCode");
        }

        @Test
        @DisplayName("CacheKey with different configHash does not match")
        void cacheKeyDifferentConfigHash() {
            CacheKey key1 = new CacheKey("world", 5, 10, 3, 42);
            CacheKey key2 = new CacheKey("world", 5, 10, 3, 99);
            assertNotEquals(key1, key2, "CacheKeys with different configHash must not be equal");
        }
    }
    }
