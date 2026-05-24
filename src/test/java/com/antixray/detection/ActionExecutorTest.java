package com.antixray.detection;

import com.antixray.api.AlertLevel;
import com.antixray.AntiXrayPlugin;
import com.antixray.config.ConfigurationManager;
import com.antixray.util.FoliaSchedulerAdapter;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ActionExecutorTest {

    private AntiXrayPlugin plugin;
    private ConfigurationManager configManager;
    private Server server;
    private PluginManager pluginManager;
    private BukkitScheduler scheduler;
    private BanList banList;
    private Server oldServer;
    private ActionExecutor actionExecutor;
    private FoliaSchedulerAdapter schedulerAdapter;

    @BeforeEach
    void setUp() throws Exception {
        // Mock Bukkit Server
        java.lang.reflect.Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        oldServer = (Server) serverField.get(null);

        server = mock(Server.class);
        serverField.set(null, server);

        pluginManager = mock(PluginManager.class);
        when(server.getPluginManager()).thenReturn(pluginManager);

        scheduler = mock(BukkitScheduler.class);
        when(server.getScheduler()).thenReturn(scheduler);

        banList = mock(BanList.class);
        when(server.getBanList(BanList.Type.NAME)).thenReturn(banList);

        plugin = mock(AntiXrayPlugin.class);
        when(plugin.getName()).thenReturn("AntiXray");
        when(plugin.getLogger()).thenReturn(Logger.getLogger("AntiXrayTestLogger"));

        configManager = mock(ConfigurationManager.class);
        when(plugin.getConfigurationManager()).thenReturn(configManager);

        schedulerAdapter = new FoliaSchedulerAdapter(plugin);
        when(plugin.getSchedulerAdapter()).thenReturn(schedulerAdapter);

        actionExecutor = new ActionExecutor(plugin);
    }

    @AfterEach
    void tearDown() throws Exception {
        java.lang.reflect.Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(null, oldServer);
        com.antixray.util.VersionUtil.resetCache();
    }

    @Test
    void testWarnAction() {
        when(configManager.getWarningActions()).thenReturn(List.of("warn"));
        when(configManager.getMessage("action-warn-message", "&cYour mining activity has been flagged as suspicious. Staff have been notified."))
                .thenReturn("Suspicious warning message!");

        // Simulate main thread
        when(server.isPrimaryThread()).thenReturn(true);

        Player player = mock(Player.class);
        when(player.getName()).thenReturn("TestPlayer");

        actionExecutor.executeActions(player, AlertLevel.WARNING);

        verify(player, times(1)).sendMessage("Suspicious warning message!");
    }

    @Test
    void testKickAction() {
        when(configManager.getCriticalActions()).thenReturn(List.of("kick"));
        when(configManager.getMessage("action-kick-message", "&cYou have been kicked for suspected X-ray use."))
                .thenReturn("Kicked!");

        when(server.isPrimaryThread()).thenReturn(true);

        Player player = mock(Player.class);
        when(player.getName()).thenReturn("TestPlayer");

        actionExecutor.executeActions(player, AlertLevel.CRITICAL);

        verify(player, times(1)).kickPlayer("Kicked!");
    }

    @Test
    void testBanActionWithoutVault() {
        when(configManager.getCriticalActions()).thenReturn(List.of("ban"));
        when(configManager.getMessage("action-ban-message", "&cYou have been banned for suspected X-ray use."))
                .thenReturn("Banned!");

        // Mock Vault plugin as not present
        when(pluginManager.getPlugin("Vault")).thenReturn(null);

        when(server.isPrimaryThread()).thenReturn(true);

        Player player = mock(Player.class);
        when(player.getName()).thenReturn("BannedPlayer");

        actionExecutor.executeActions(player, AlertLevel.CRITICAL);

        verify(banList, times(1)).addBan(eq("BannedPlayer"), eq("Banned!"), (Date) isNull(), eq("AntiXray"));
        verify(player, times(1)).kickPlayer("Banned!");
    }

    @Test
    void testBanActionWithVault() {
        when(configManager.getCriticalActions()).thenReturn(List.of("ban"));
        when(configManager.getMessage("action-ban-message", "&cYou have been banned for suspected X-ray use."))
                .thenReturn("Banned!");

        // Mock Vault plugin as present
        Plugin vaultPlugin = mock(Plugin.class);
        when(pluginManager.getPlugin("Vault")).thenReturn(vaultPlugin);

        when(server.isPrimaryThread()).thenReturn(true);

        Player player = mock(Player.class);
        when(player.getName()).thenReturn("BannedPlayer");

        actionExecutor.executeActions(player, AlertLevel.CRITICAL);

        verify(banList, times(1)).addBan(eq("BannedPlayer"), eq("Banned!"), (Date) isNull(), eq("AntiXray"));
        verify(player, times(1)).kickPlayer("Banned!");
    }

    @Test
    void testCommandAction() {
        when(configManager.getCriticalActions()).thenReturn(List.of("command:tempban {player} 1d X-ray"));
        when(server.isPrimaryThread()).thenReturn(true);

        Player player = mock(Player.class);
        when(player.getName()).thenReturn("CheaterPlayer");

        actionExecutor.executeActions(player, AlertLevel.CRITICAL);

        verify(server, times(1)).dispatchCommand(any(), eq("tempban CheaterPlayer 1d X-ray"));
    }

    @Test
    void testThreadSafetyOffPrimaryThread() {
        when(configManager.getCriticalActions()).thenReturn(List.of("kick"));
        when(configManager.getMessage("action-kick-message", "&cYou have been kicked for suspected X-ray use."))
            .thenReturn("Kicked!");

        BukkitTask bukkitTask = mock(BukkitTask.class);
        when(bukkitTask.getTaskId()).thenReturn(1);
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenReturn(bukkitTask);

        when(server.isPrimaryThread()).thenReturn(false);

        Player player = mock(Player.class);
        when(player.getName()).thenReturn("TestPlayer");

        actionExecutor.executeActions(player, AlertLevel.CRITICAL);

        verify(player, never()).kickPlayer(anyString());

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler, times(1)).runTask(eq(plugin), runnableCaptor.capture());

        when(server.isPrimaryThread()).thenReturn(true);
        runnableCaptor.getValue().run();
        verify(player, times(1)).kickPlayer("Kicked!");
    }
}
