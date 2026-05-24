package com.antixray.listener;

import com.antixray.AntiXrayPlugin;
import com.antixray.config.ConfigurationManager;
import com.antixray.deobfuscation.PlayerData;
import com.antixray.detection.DetectionEngine;
import com.antixray.detection.DetectionResult;
import com.antixray.api.AlertLevel;
import com.antixray.detection.AlertManager;
import com.antixray.detection.ActionExecutor;
import com.antixray.detection.PlayerStatistics;
import com.antixray.engine.AirExposureChecker;
import com.antixray.engine.ObfuscationEngine;
import com.antixray.nms.NmsAdapter;
import com.antixray.util.MaterialSet;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.Statistic;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BlockEventListenerTest {

    private AntiXrayPlugin plugin;
    private ConfigurationManager configManager;
    private NmsAdapter nmsAdapter;
    private ObfuscationEngine obfuscationEngine;
    private MaterialSet materialSet;
    private AirExposureChecker exposureChecker;
    private DetectionEngine detectionEngine;
    private AlertManager alertManager;
    private ActionExecutor actionExecutor;

    private BlockEventListener listener;
    private Server oldServer;

    private static final BlockFace[] ADJACENT_FACES = {
        BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    @BeforeEach
    void setUp() throws Exception {
        java.lang.reflect.Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        oldServer = (Server) serverField.get(null);

        Server server = mock(Server.class);
        
        // Return a dynamic proxy for any Registry lookup to satisfy Registry.<clinit> checks
        lenient().when(server.getRegistry(any())).thenAnswer(invocation -> 
            java.lang.reflect.Proxy.newProxyInstance(
                BlockEventListenerTest.class.getClassLoader(),
                new Class<?>[] { Registry.class },
                (proxy, method, args) -> null
            )
        );

        serverField.set(null, server);

        Registry<?> registry = mock(Registry.class);
        lenient().when(server.getRegistry(any())).thenAnswer(invocation -> registry);

        plugin = mock(AntiXrayPlugin.class);
        configManager = mock(ConfigurationManager.class);
        nmsAdapter = mock(NmsAdapter.class);
        obfuscationEngine = mock(ObfuscationEngine.class);
        materialSet = mock(MaterialSet.class);
        exposureChecker = mock(AirExposureChecker.class);
        detectionEngine = mock(DetectionEngine.class);
        alertManager = mock(AlertManager.class);
        actionExecutor = mock(ActionExecutor.class);

        when(plugin.getConfigurationManager()).thenReturn(configManager);
        when(plugin.getNmsAdapter()).thenReturn(nmsAdapter);
        when(plugin.getObfuscationEngine()).thenReturn(obfuscationEngine);
        when(obfuscationEngine.getMaterialSet()).thenReturn(materialSet);
        when(obfuscationEngine.getExposureChecker()).thenReturn(exposureChecker);
        when(plugin.getDetectionEngine()).thenReturn(detectionEngine);
        when(plugin.getAlertManager()).thenReturn(alertManager);
        when(plugin.getActionExecutor()).thenReturn(actionExecutor);

        listener = new BlockEventListener(plugin);
    }

    @AfterEach
    void tearDown() throws Exception {
        java.lang.reflect.Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(null, oldServer);
    }

    private void mockAdjacentBlocks(Block brokenBlock, Material material) {
        for (BlockFace face : ADJACENT_FACES) {
            Block relative = mock(Block.class);
            when(relative.getType()).thenReturn(material);
            when(brokenBlock.getRelative(face)).thenReturn(relative);
        }
    }

    @Test
    void onBlockBreak_updatesStatisticsAndEvaluates() {
        when(configManager.isDetectionEnabled()).thenReturn(true);

        UUID playerId = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getStatistic(Statistic.PLAY_ONE_MINUTE)).thenReturn(12000); // 10 minutes

        PlayerData playerData = new PlayerData(1000);
        when(plugin.getPlayerData(playerId)).thenReturn(playerData);

        Block brokenBlock = mock(Block.class);
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        when(brokenBlock.getWorld()).thenReturn(world);
        when(brokenBlock.getType()).thenReturn(Material.DIAMOND_ORE);
        when(brokenBlock.getX()).thenReturn(100);
        when(brokenBlock.getY()).thenReturn(15);
        when(brokenBlock.getZ()).thenReturn(200);
        when(brokenBlock.getBiome()).thenReturn(Biome.PLAINS);

        mockAdjacentBlocks(brokenBlock, Material.STONE);

        when(detectionEngine.evaluate(eq(playerId), any(PlayerStatistics.class))).thenReturn(DetectionResult.none());

        BlockBreakEvent event = new BlockBreakEvent(brokenBlock, player);
        listener.onBlockBreak(event);

        PlayerStatistics stats = playerData.getStatistics();
        assertEquals(1, stats.getTotalMined());
        assertEquals(1, stats.getTotalOresMined());
        assertEquals(10, stats.getPlayTimeMinutes());

        verify(detectionEngine, times(1)).evaluate(eq(playerId), any(PlayerStatistics.class));
        verify(plugin, never()).triggerAlert(any(), any());
    }

    @Test
    void onBlockBreak_triggersAlertOnDetection() {
        when(configManager.isDetectionEnabled()).thenReturn(true);

        UUID playerId = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getStatistic(Statistic.PLAY_ONE_MINUTE)).thenReturn(12000);

        PlayerData playerData = new PlayerData(1000);
        when(plugin.getPlayerData(playerId)).thenReturn(playerData);

        Block brokenBlock = mock(Block.class);
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        when(brokenBlock.getWorld()).thenReturn(world);
        when(brokenBlock.getType()).thenReturn(Material.DIAMOND_ORE);
        when(brokenBlock.getX()).thenReturn(100);
        when(brokenBlock.getY()).thenReturn(15);
        when(brokenBlock.getZ()).thenReturn(200);
        when(brokenBlock.getBiome()).thenReturn(Biome.PLAINS);

        mockAdjacentBlocks(brokenBlock, Material.STONE);

        DetectionResult warningResult = DetectionResult.of(AlertLevel.WARNING, List.of("oreToStoneRatio"));
        when(detectionEngine.evaluate(eq(playerId), any(PlayerStatistics.class))).thenReturn(warningResult);

        BlockBreakEvent event = new BlockBreakEvent(brokenBlock, player);
        listener.onBlockBreak(event);

        verify(plugin, times(1)).triggerAlert(player, warningResult);
    }

    @Test
    void onBlockBreak_triggersCriticalAlertAndActions() {
        when(configManager.isDetectionEnabled()).thenReturn(true);
        when(configManager.getCriticalActions()).thenReturn(List.of("kick", "ban"));

        UUID playerId = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getStatistic(Statistic.PLAY_ONE_MINUTE)).thenReturn(12000);

        PlayerData playerData = new PlayerData(1000);
        when(plugin.getPlayerData(playerId)).thenReturn(playerData);

        Block brokenBlock = mock(Block.class);
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        when(brokenBlock.getWorld()).thenReturn(world);
        when(brokenBlock.getType()).thenReturn(Material.DIAMOND_ORE);
        when(brokenBlock.getX()).thenReturn(100);
        when(brokenBlock.getY()).thenReturn(15);
        when(brokenBlock.getZ()).thenReturn(200);
        when(brokenBlock.getBiome()).thenReturn(Biome.PLAINS);

        mockAdjacentBlocks(brokenBlock, Material.STONE);

        DetectionResult criticalResult = DetectionResult.of(AlertLevel.CRITICAL, List.of("diamondPerHour", "oreToStoneRatio"));
        when(detectionEngine.evaluate(eq(playerId), any(PlayerStatistics.class))).thenReturn(criticalResult);

        BlockBreakEvent event = new BlockBreakEvent(brokenBlock, player);
        listener.onBlockBreak(event);

        verify(plugin, times(1)).triggerAlert(player, criticalResult);
    }

    @Test
    void onBlockBreak_detectionDisabled_doesNothing() {
        when(configManager.isDetectionEnabled()).thenReturn(false);

        UUID playerId = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);

        PlayerData playerData = new PlayerData(1000);
        when(plugin.getPlayerData(playerId)).thenReturn(playerData);

        Block brokenBlock = mock(Block.class);
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        when(brokenBlock.getWorld()).thenReturn(world);
        when(brokenBlock.getType()).thenReturn(Material.DIAMOND_ORE);
        when(brokenBlock.getX()).thenReturn(100);
        when(brokenBlock.getY()).thenReturn(15);
        when(brokenBlock.getZ()).thenReturn(200);

        mockAdjacentBlocks(brokenBlock, Material.STONE);

        BlockBreakEvent event = new BlockBreakEvent(brokenBlock, player);
        listener.onBlockBreak(event);

        PlayerStatistics stats = playerData.getStatistics();
        assertEquals(0, stats.getTotalMined());

        verify(detectionEngine, never()).evaluate(any(UUID.class), any(PlayerStatistics.class));
    }
}
