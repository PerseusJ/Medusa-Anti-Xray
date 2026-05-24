package com.antixray.detection;

import com.antixray.api.AlertLevel;
import com.antixray.AntiXrayPlugin;
import com.antixray.config.ConfigurationManager;
import com.antixray.util.FoliaSchedulerAdapter;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AlertManagerTest {

    private AntiXrayPlugin plugin;
    private ConfigurationManager configManager;
    private Server server;
    private BukkitScheduler scheduler;
    private Server oldServer;
    private File tempDir;
    private AlertManager alertManager;

    @BeforeEach
    void setUp() throws Exception {
        // Mock Bukkit Server
        java.lang.reflect.Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        oldServer = (Server) serverField.get(null);

        server = mock(Server.class);
        serverField.set(null, server);

        scheduler = mock(BukkitScheduler.class);
        when(server.getScheduler()).thenReturn(scheduler);

        plugin = mock(AntiXrayPlugin.class);
        configManager = mock(ConfigurationManager.class);
        when(plugin.getConfigurationManager()).thenReturn(configManager);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("AntiXrayTestLogger"));

        FoliaSchedulerAdapter schedulerAdapter = new FoliaSchedulerAdapter(plugin);
        when(plugin.getSchedulerAdapter()).thenReturn(schedulerAdapter);

        // Setup temporary directory for log file tests
        Path tempPath = Files.createTempDirectory("antixray-test");
        tempDir = tempPath.toFile();
        when(plugin.getDataFolder()).thenReturn(tempDir);

        alertManager = new AlertManager(plugin);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (alertManager != null) {
            alertManager.shutdown();
        }

        // Clean up temp files
        if (tempDir != null && tempDir.exists()) {
            File[] files = tempDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    f.delete();
                }
            }
            tempDir.delete();
        }

        java.lang.reflect.Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(null, oldServer);
    }

    @Test
    void testLogFileCreationAndAppend() throws Exception {
        UUID playerId = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getName()).thenReturn("TestPlayer");
        when(player.getUniqueId()).thenReturn(playerId);

        DetectionResult result = DetectionResult.of(AlertLevel.CRITICAL, List.of("diamondPerHour", "oreToStoneRatio"));

        alertManager.dispatchAlert(player, result);
        alertManager.shutdown(); // Close file handlers to flush content

        File logFile = new File(tempDir, "detection.log");
        if (!logFile.exists()) {
            logFile = new File(tempDir, "detection.log.0");
        }
        assertTrue(logFile.exists(), "Log file should be created");

        List<String> lines = Files.readAllLines(logFile.toPath());
        assertFalse(lines.isEmpty(), "Log file should contain entries");
        assertTrue(lines.get(0).contains("TestPlayer"), "Log should contain player name");
        assertTrue(lines.get(0).contains("CRITICAL"), "Log should contain alert level");
        assertTrue(lines.get(0).contains("diamondPerHour, oreToStoneRatio"), "Log should contain metrics");
    }

    @Test
    void testInGameChatNotification() {
        when(configManager.isNotifyInGame()).thenReturn(true);
        when(configManager.getMessage(anyString(), anyString())).thenAnswer(invocation -> invocation.getArgument(1));

        Player notifier = mock(Player.class);
        when(notifier.getName()).thenReturn("NotifyMe");
        when(notifier.hasPermission("antixray.notify")).thenReturn(true);

        Player nonNotifier = mock(Player.class);
        when(nonNotifier.getName()).thenReturn("IgnoreMe");
        when(nonNotifier.hasPermission("antixray.notify")).thenReturn(false);

        // Mock Server.getOnlinePlayers()
        Collection<Player> players = List.of(notifier, nonNotifier);
        doReturn(players).when(server).getOnlinePlayers();

        Player target = mock(Player.class);
        when(target.getName()).thenReturn("SusPlayer");
        when(target.getUniqueId()).thenReturn(UUID.randomUUID());

        DetectionResult result = DetectionResult.of(AlertLevel.WARNING, List.of("straightToOreRatio"));

        alertManager.dispatchAlert(target, result);

        verify(notifier, times(1)).sendMessage(contains("SusPlayer"));
        verify(nonNotifier, never()).sendMessage(anyString());
    }

    @Test
    void testConsoleLogNotification() {
        when(configManager.isNotifyConsole()).thenReturn(true);

        Player player = mock(Player.class);
        when(player.getName()).thenReturn("ConsoleTestPlayer");
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());

        DetectionResult result = DetectionResult.of(AlertLevel.INFO, List.of("valuableOreRatio"));

        List<String> loggedMessages = new ArrayList<>();
        java.util.logging.Handler testHandler = new java.util.logging.Handler() {
            @Override
            public void publish(java.util.logging.LogRecord record) {
                loggedMessages.add(record.getMessage());
            }

            @Override
            public void flush() {}

            @Override
            public void close() throws SecurityException {}
        };
        
        Logger logger = plugin.getLogger();
        logger.addHandler(testHandler);
        try {
            alertManager.dispatchAlert(player, result);
        } finally {
            logger.removeHandler(testHandler);
        }

        boolean found = false;
        for (String msg : loggedMessages) {
            if (msg.contains("ConsoleTestPlayer")) {
                found = true;
                break;
            }
        }
        assertTrue(found, "Console log should contain player name ConsoleTestPlayer");
    }

    @Test
    void testWebhookPlainJsonDispatch() {
        when(configManager.isWebhookEnabled()).thenReturn(true);
        when(configManager.getWebhookUrl()).thenReturn("http://localhost:12345/webhook");
        when(configManager.getWebhookFormat()).thenReturn("plain");

        Player player = mock(Player.class);
        when(player.getName()).thenReturn("WebhookPlayer");
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());

        DetectionResult result = DetectionResult.of(AlertLevel.CRITICAL, List.of("orePerHour"));

        alertManager.dispatchAlert(player, result);

        // Verify scheduler task is scheduled asynchronously
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler, times(1)).runTaskAsynchronously(eq(plugin), runnableCaptor.capture());
        
        // Execute the task to cover connection logic (fails gracefully on invalid port/URL)
        Runnable task = runnableCaptor.getValue();
        assertNotNull(task);
        assertDoesNotThrow(task::run);
    }
}
