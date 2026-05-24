package com.antixray.integration;

import com.antixray.AntiXrayPlugin;
import com.antixray.config.ConfigurationManager;
import com.antixray.config.WorldConfig;
import com.antixray.nms.NmsAdapter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.messaging.Messenger;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class DiagnosticTest {

    private static final int STONE_ID = 1;
    private static final int DEEPSLATE_ID = 2;
    private static final int NETHERRACK_ID = 3;
    private static final int END_STONE_ID = 4;
    private static final int DIAMOND_ORE_ID = 50;
    private static final int IRON_ORE_ID = 51;
    private static final int GOLD_ORE_ID = 52;

    private NmsAdapter adapter;
    private MockedStatic<Bukkit> bukkitMock;
    private MockedStatic<Material> materialMock;

    @BeforeEach
    void setUp() {
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

        adapter = mock(NmsAdapter.class);
        when(adapter.getBlockStateId(Material.STONE)).thenReturn(STONE_ID);
        when(adapter.getBlockStateId(Material.DEEPSLATE)).thenReturn(DEEPSLATE_ID);
        when(adapter.getBlockStateId(Material.NETHERRACK)).thenReturn(NETHERRACK_ID);
        when(adapter.getBlockStateId(Material.END_STONE)).thenReturn(END_STONE_ID);
        when(adapter.getBlockStateId(Material.DIAMOND_ORE)).thenReturn(DIAMOND_ORE_ID);
        when(adapter.getBlockStateId(Material.IRON_ORE)).thenReturn(IRON_ORE_ID);
        when(adapter.getBlockStateId(Material.GOLD_ORE)).thenReturn(GOLD_ORE_ID);
        when(adapter.getVersionString()).thenReturn("v1_21_R3");

        materialMock = Mockito.mockStatic(Material.class);
        Material stoneMock = mockMaterial("STONE", true);
        Material deepslateMock = mockMaterial("DEEPSLATE", true);
        Material netherrackMock = mockMaterial("NETHERRACK", true);
        Material endStoneMock = mockMaterial("END_STONE", true);
        Material diamondOreMock = mockMaterial("DIAMOND_ORE", true);
        Material ironOreMock = mockMaterial("IRON_ORE", true);
        Material goldOreMock = mockMaterial("GOLD_ORE", true);

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
    }

    @AfterEach
    void tearDown() {
        if (materialMock != null) materialMock.close();
        if (bukkitMock != null) bukkitMock.close();
    }

    private Material mockMaterial(String name, boolean isBlock) {
        Material mat = mock(Material.class);
        lenient().when(mat.name()).thenReturn(name);
        lenient().when(mat.isBlock()).thenReturn(isBlock);
        return mat;
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

    @Test
    void diagnosticHiddenBlocksChange() {
        AntiXrayPlugin localPlugin = mock(AntiXrayPlugin.class);
        Logger logger = mock(Logger.class);
        when(localPlugin.getLogger()).thenReturn(logger);

        YamlConfiguration config = createDefaultConfig();
        when(localPlugin.getConfig()).thenReturn(config);

        ConfigurationManager configMgr = new ConfigurationManager(localPlugin, adapter);
        configMgr.load();

        WorldConfig firstGlobal = configMgr.getGlobalConfig();
        Set<Integer> firstHidden = firstGlobal.getHiddenBlocks();
        System.out.println("=== FIRST LOAD ===");
        System.out.println("Hidden blocks: " + firstHidden);
        System.out.println("Hidden blocks class: " + firstHidden.getClass().getName());
        System.out.println("Config hash: " + firstGlobal.getConfigHash());

        // Now modify
        config.set("hidden-blocks", List.of("diamond_ore"));
        System.out.println("=== AFTER CONFIG SET ===");
        System.out.println("Config hidden-blocks: " + config.getStringList("hidden-blocks"));
        
        // Also check what matchMaterial returns for iron_ore when the list only contains diamond_ore
        System.out.println("Material.matchMaterial('diamond_ore') = " + Material.matchMaterial("diamond_ore"));
        System.out.println("Material.matchMaterial('iron_ore') = " + Material.matchMaterial("iron_ore"));

        configMgr.reload();

        WorldConfig secondGlobal = configMgr.getGlobalConfig();
        Set<Integer> secondHidden = secondGlobal.getHiddenBlocks();
        System.out.println("=== AFTER RELOAD ===");
        System.out.println("Hidden blocks: " + secondHidden);
        System.out.println("Hidden blocks class: " + secondHidden.getClass().getName());
        System.out.println("Config hash: " + secondGlobal.getConfigHash());

        System.out.println("=== COMPARISON ===");
        System.out.println("firstHidden.equals(secondHidden): " + firstHidden.equals(secondHidden));
        System.out.println("secondHidden.equals(firstHidden): " + secondHidden.equals(firstHidden));
        System.out.println("Same object? " + (firstHidden == secondHidden));
        System.out.println("engineMode changed? " + (firstGlobal.getEngineMode() != secondGlobal.getEngineMode()));
    }
}
