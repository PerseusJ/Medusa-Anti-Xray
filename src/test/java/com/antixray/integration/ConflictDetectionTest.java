package com.antixray.integration;

import com.antixray.AntiXrayPlugin;
import com.antixray.i18n.I18n;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConflictDetectionTest {

    private AntiXrayPlugin plugin;
    private Server server;
    private PluginManager pluginManager;
    private Logger logger;
    private List<String> warningLogs;
    private List<String> infoLogs;
    private MockedStatic<Bukkit> bukkitMock;
    private Server.Spigot spigotMock;
    private YamlConfiguration spigotConfig;

    @TempDir
    Path tempFolder;

    private File originalWorkingDir;

    @BeforeEach
    void setUp() throws Exception {
        warningLogs = new ArrayList<>();
        infoLogs = new ArrayList<>();

        // Create a custom logger that captures logs
        logger = Logger.getLogger("AntiXrayTestLogger_" + System.nanoTime());
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getLevel() == Level.WARNING) {
                    warningLogs.add(record.getMessage());
                } else if (record.getLevel() == Level.INFO) {
                    infoLogs.add(record.getMessage());
                }
            }

            @Override
            public void flush() {}

            @Override
            public void close() throws SecurityException {}
        });

        // Mock Server and PluginManager
        server = mock(Server.class);
        pluginManager = mock(PluginManager.class);
        when(server.getPluginManager()).thenReturn(pluginManager);
        when(pluginManager.getPlugins()).thenReturn(new Plugin[0]);

        // Mock Bukkit
        bukkitMock = Mockito.mockStatic(Bukkit.class);
        bukkitMock.when(Bukkit::getServer).thenReturn(server);

        spigotMock = mock(Server.Spigot.class);
        bukkitMock.when(Bukkit::spigot).thenReturn(spigotMock);

        spigotConfig = mock(YamlConfiguration.class);
        when(spigotMock.getConfig()).thenReturn(spigotConfig);

        // Instantiate Plugin Mock to test checkConflicts()
        plugin = mock(AntiXrayPlugin.class);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(logger);
        when(plugin.getName()).thenReturn("AntiXray");
        when(plugin.getDataFolder()).thenReturn(tempFolder.toFile());

        // Mock getResource to load files from classpath
        when(plugin.getResource(anyString())).thenAnswer(invocation -> {
            String path = invocation.getArgument(0);
            return ConflictDetectionTest.class.getClassLoader().getResourceAsStream(path);
        });

        // Mock saveResource to copy resource to temp folder
        doAnswer(invocation -> {
            String resourcePath = invocation.getArgument(0);
            File outFile = new File(tempFolder.toFile(), resourcePath);
            outFile.getParentFile().mkdirs();
            try (InputStream in = ConflictDetectionTest.class.getClassLoader().getResourceAsStream(resourcePath);
                 java.io.FileOutputStream out = new java.io.FileOutputStream(outFile)) {
                if (in != null) {
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                }
            }
            return null;
        }).when(plugin).saveResource(anyString(), anyBoolean());

        // Load I18n and inject it into the plugin using reflection
        I18n i18n = new I18n(plugin);
        i18n.load();

        Field i18nField = AntiXrayPlugin.class.getDeclaredField("i18n");
        i18nField.setAccessible(true);
        i18nField.set(plugin, i18n);

        // Direct checkConflicts calls to the real method
        doCallRealMethod().when(plugin).checkConflicts();
    }

    @AfterEach
    void tearDown() {
        if (bukkitMock != null) {
            bukkitMock.close();
        }
        // Clean up config files created in current working directory
        deleteFile(new File("config/paper-world-defaults.yml"));
        deleteFile(new File("config"));
        deleteFile(new File("paper.yml"));
    }

    private void deleteFile(File file) {
        if (file.exists()) {
            if (file.isDirectory()) {
                for (File child : file.listFiles()) {
                    deleteFile(child);
                }
            }
            file.delete();
        }
    }

    private void writeYamlFile(File file, String content) throws Exception {
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
    }

    @Test
    void testNoConflicts() {
        plugin.checkConflicts();
        assertTrue(warningLogs.isEmpty(), "Warnings list should be empty: " + warningLogs);
        assertTrue(infoLogs.isEmpty(), "Info logs should be empty: " + infoLogs);
    }

    @Test
    void testPaperAntiXrayEnabledInDefaults() throws Exception {
        File file = new File("config/paper-world-defaults.yml");
        writeYamlFile(file, "anticheat:\n  anti-xray:\n    enabled: true\n");

        plugin.checkConflicts();

        assertFalse(warningLogs.isEmpty(), "Should have a warning");
        boolean found = warningLogs.stream().anyMatch(log -> log.contains("Paper's built-in Anti-Xray is enabled"));
        assertTrue(found, "Warning should mention Paper's built-in Anti-Xray. Found logs: " + warningLogs);
    }

    @Test
    void testPaperAntiXrayDisabledInDefaults() throws Exception {
        File file = new File("config/paper-world-defaults.yml");
        writeYamlFile(file, "anticheat:\n  anti-xray:\n    enabled: false\n");

        plugin.checkConflicts();
        assertTrue(warningLogs.isEmpty(), "Should not warn if paper defaults anti-xray is disabled");
    }

    @Test
    void testPaperAntiXrayLegacyEnabled() throws Exception {
        File file = new File("paper.yml");
        writeYamlFile(file, "world-settings:\n  default:\n    anti-xray:\n      enabled: true\n");

        plugin.checkConflicts();

        assertFalse(warningLogs.isEmpty(), "Should have a warning");
        boolean found = warningLogs.stream().anyMatch(log -> log.contains("Paper's built-in Anti-Xray is enabled"));
        assertTrue(found, "Warning should mention Paper's built-in Anti-Xray. Found logs: " + warningLogs);
    }

    @Test
    void testSpigotAntiXrayEnabled() {
        when(spigotConfig.getBoolean("settings.anti-xray.enabled", false)).thenReturn(true);

        plugin.checkConflicts();

        assertFalse(warningLogs.isEmpty(), "Should have a warning");
        boolean found = warningLogs.stream().anyMatch(log -> log.contains("Paper's built-in Anti-Xray is enabled"));
        assertTrue(found, "Warning should mention Paper's built-in Anti-Xray. Found logs: " + warningLogs);
    }

    @Test
    void testOrebfuscatorPluginConflict() {
        Plugin orebfuscator = mock(Plugin.class);
        when(orebfuscator.getName()).thenReturn("Orebfuscator");
        when(pluginManager.getPlugin("Orebfuscator")).thenReturn(orebfuscator);

        plugin.checkConflicts();

        assertFalse(warningLogs.isEmpty(), "Should have a warning");
        boolean found = warningLogs.stream().anyMatch(log -> log.contains("Orebfuscator detected"));
        assertTrue(found, "Warning should mention Orebfuscator. Found logs: " + warningLogs);
    }

    @Test
    void testGenericPluginNameConflicts() {
        Plugin xrayPlugin = mock(Plugin.class);
        when(xrayPlugin.getName()).thenReturn("SuperXrayHelper");

        Plugin orebfPlugin = mock(Plugin.class);
        when(orebfPlugin.getName()).thenReturn("Orebfuscator-Addon");

        Plugin antiXrayPlugin = mock(Plugin.class);
        when(antiXrayPlugin.getName()).thenReturn("Paper-Anti-Xray-Bypass");

        Plugin ourPlugin = mock(Plugin.class);
        when(ourPlugin.getName()).thenReturn("AntiXray");

        when(pluginManager.getPlugins()).thenReturn(new Plugin[]{xrayPlugin, orebfPlugin, antiXrayPlugin, ourPlugin});

        plugin.checkConflicts();

        // Should log info messages about SuperXrayHelper, Orebfuscator-Addon, Paper-Anti-Xray-Bypass, but not AntiXray (ourselves)
        assertEquals(3, infoLogs.size(), "Should log exactly 3 potential conflict info warnings");
        assertTrue(infoLogs.stream().anyMatch(log -> log.contains("SuperXrayHelper")), "Should log SuperXrayHelper warning");
        assertTrue(infoLogs.stream().anyMatch(log -> log.contains("Orebfuscator-Addon")), "Should log Orebfuscator-Addon warning");
        assertTrue(infoLogs.stream().anyMatch(log -> log.contains("Paper-Anti-Xray-Bypass")), "Should log Paper-Anti-Xray-Bypass warning");
        assertFalse(infoLogs.stream().anyMatch(log -> log.contains("AntiXray")), "Should not warn about ourselves");
    }
}
