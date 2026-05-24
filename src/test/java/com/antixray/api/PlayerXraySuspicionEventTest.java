package com.antixray.api;

import com.antixray.AntiXrayPlugin;
import com.antixray.config.ConfigurationManager;
import com.antixray.deobfuscation.PlayerData;
import com.antixray.detection.ActionExecutor;
import com.antixray.detection.AlertManager;
import com.antixray.detection.DetectionResult;
import com.antixray.detection.PlayerStatistics;
import com.antixray.detection.StatisticsStorage;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlayerXraySuspicionEventTest {

    private Server oldServer;
    private Server server;
    private PluginManager pluginManager;
    private AntiXrayPlugin plugin;
    private ConfigurationManager configurationManager;
    private PlayerData playerData;
    private PlayerStatistics stats;
    private AlertManager alertManager;
    private ActionExecutor actionExecutor;
    private StatisticsStorage statisticsStorage;

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
        doCallRealMethod().when(plugin).triggerAlert(any(), any());

        configurationManager = mock(ConfigurationManager.class);
        playerData = mock(PlayerData.class);
        stats = mock(PlayerStatistics.class);
        alertManager = mock(AlertManager.class);
        actionExecutor = mock(ActionExecutor.class);
        statisticsStorage = mock(StatisticsStorage.class);

        when(plugin.getPlayerData(any())).thenReturn(playerData);
        when(playerData.getStatistics()).thenReturn(stats);

        setField(plugin, "configurationManager", configurationManager);
        setField(plugin, "alertManager", alertManager);
        setField(plugin, "actionExecutor", actionExecutor);
        setField(plugin, "statisticsStorage", statisticsStorage);

        when(configurationManager.isDetectionEnabled()).thenReturn(true);
    }

    @AfterEach
    void tearDown() throws Exception {
        java.lang.reflect.Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(null, oldServer);
    }

    private void setField(Object target, String name, Object value) throws Exception {
        java.lang.reflect.Field field = AntiXrayPlugin.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    void testPlayerXraySuspicionEventFieldsAccessible() {
        Player player = mock(Player.class);
        Map<String, Double> metrics = new HashMap<>();
        metrics.put("oreToStoneRatio", 0.25);
        PlayerXraySuspicionEvent event = new PlayerXraySuspicionEvent(player, AlertLevel.CRITICAL, metrics);

        assertEquals(player, event.getPlayer());
        assertEquals(AlertLevel.CRITICAL, event.getAlertLevel());
        assertEquals(metrics, event.getTriggeringMetrics());
        assertFalse(event.isCancelled());

        event.setCancelled(true);
        assertTrue(event.isCancelled());

        assertNotNull(event.getHandlers());
        assertNotNull(PlayerXraySuspicionEvent.getHandlerList());
    }

    @Test
    void testPlayerXraySuspicionEventFiresAndMetricsMapCorrect() {
        Player player = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn("sus_miner");

        // Mock statistics values
        when(stats.getOreToStoneRatio()).thenReturn(0.35);
        when(stats.getDiamondToStoneRatio()).thenReturn(0.12);
        when(stats.getOrePerHour()).thenReturn(150.0);
        when(stats.getDiamondPerHour()).thenReturn(45.0);
        when(stats.getShortWindowOreRatio()).thenReturn(0.8);
        when(stats.getLongWindowOreRatio()).thenReturn(0.4);
        when(stats.getValuableOreRatio()).thenReturn(0.2);
        when(stats.getStraightToOreRatio()).thenReturn(0.9);

        List<String> triggeredMetrics = List.of(
            "oreToStoneRatio",
            "diamondToStoneRatio",
            "orePerHour",
            "diamondPerHour",
            "shortWindowOreRatio",
            "longWindowOreRatio",
            "valuableOreRatio",
            "straightToOreRatio"
        );

        DetectionResult result = DetectionResult.of(AlertLevel.WARNING, triggeredMetrics);

        plugin.triggerAlert(player, result);

        // Verify the event is fired
        ArgumentCaptor<PlayerXraySuspicionEvent> eventCaptor = ArgumentCaptor.forClass(PlayerXraySuspicionEvent.class);
        verify(pluginManager, times(1)).callEvent(eventCaptor.capture());

        PlayerXraySuspicionEvent firedEvent = eventCaptor.getValue();
        assertEquals(player, firedEvent.getPlayer());
        assertEquals(AlertLevel.WARNING, firedEvent.getAlertLevel());

        Map<String, Double> expectedMetrics = firedEvent.getTriggeringMetrics();
        assertEquals(0.35, expectedMetrics.get("oreToStoneRatio"));
        assertEquals(0.12, expectedMetrics.get("diamondToStoneRatio"));
        assertEquals(150.0, expectedMetrics.get("orePerHour"));
        assertEquals(45.0, expectedMetrics.get("diamondPerHour"));
        assertEquals(0.8, expectedMetrics.get("shortWindowOreRatio"));
        assertEquals(0.4, expectedMetrics.get("longWindowOreRatio"));
        assertEquals(0.2, expectedMetrics.get("valuableOreRatio"));
        assertEquals(0.9, expectedMetrics.get("straightToOreRatio"));
    }

    @Test
    void testPlayerXraySuspicionEventCancellationPreventsAction() {
        Player player = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn("sus_miner");

        when(stats.getOreToStoneRatio()).thenReturn(0.35);
        List<String> triggeredMetrics = List.of("oreToStoneRatio");
        DetectionResult result = DetectionResult.of(AlertLevel.CRITICAL, triggeredMetrics);

        // Cancel the event during callback
        doAnswer(invocation -> {
            PlayerXraySuspicionEvent event = invocation.getArgument(0);
            event.setCancelled(true);
            return null;
        }).when(pluginManager).callEvent(any(PlayerXraySuspicionEvent.class));

        plugin.triggerAlert(player, result);

        // Verify event was fired
        verify(pluginManager, times(1)).callEvent(any(PlayerXraySuspicionEvent.class));

        // Verify actions were NOT run, alerts NOT dispatched, and NOT stored
        verify(alertManager, never()).dispatchAlert(any(), any());
        verify(actionExecutor, never()).executeActions(any(), any());
        verify(statisticsStorage, never()).recordAlert(any(), any(), any(), any());
    }
}
