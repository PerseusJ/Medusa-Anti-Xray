package com.antixray.i18n;

import com.antixray.AntiXrayPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class I18nTest {

    private AntiXrayPlugin plugin;
    private I18n i18n;

    @TempDir
    Path tempFolder;

    @BeforeEach
    void setUp() {
        plugin = mock(AntiXrayPlugin.class);
        Logger logger = Logger.getLogger("AntiXrayTestLogger");
        when(plugin.getLogger()).thenReturn(logger);
        when(plugin.getDataFolder()).thenReturn(tempFolder.toFile());

        // Mock getResource to return the streams from classpath resources
        when(plugin.getResource(anyString())).thenAnswer(invocation -> {
            String path = invocation.getArgument(0);
            return I18nTest.class.getClassLoader().getResourceAsStream(path);
        });

        // Mock saveResource to copy files to target path
        doAnswer(invocation -> {
            String resourcePath = invocation.getArgument(0);
            File outFile = new File(tempFolder.toFile(), resourcePath);
            outFile.getParentFile().mkdirs();
            try (InputStream in = I18nTest.class.getClassLoader().getResourceAsStream(resourcePath);
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

        i18n = new I18n(plugin);
    }

    @Test
    void testLoadAndDefaultFallback() {
        i18n.load();

        // Check that a message is successfully fetched from the English file when no locale is provided
        String prefix = i18n.getMessage("prefix", (Player) null);
        assertNotNull(prefix);
        assertTrue(prefix.contains("Medusa-Anti-Xray"));

        // Try getting a nonexistent key, it should fallback to returning the key itself
        String nonexistent = i18n.getMessage("nonexistent-key-xyz", (Player) null);
        assertEquals("nonexistent-key-xyz", nonexistent);
    }

    @Test
    void testPlayerLocaleResolution() {
        i18n.load();

        Player spanishPlayer = mock(Player.class);
        when(spanishPlayer.getLocale()).thenReturn("es_ES");

        Player germanPlayer = mock(Player.class);
        when(germanPlayer.getLocale()).thenReturn("de_DE");

        Player unknownPlayer = mock(Player.class);
        when(unknownPlayer.getLocale()).thenReturn("it_IT"); // Italian (not supported, fallback to en)

        // plugin-enabled in Spanish (es.yml) -> "AntiXray habilitado."
        String esMsg = i18n.getMessage("plugin-enabled", spanishPlayer);
        assertTrue(esMsg.contains("habilitado"));

        // plugin-enabled in German (de.yml) -> "AntiXray aktiviert."
        String deMsg = i18n.getMessage("plugin-enabled", germanPlayer);
        assertTrue(deMsg.contains("aktiviert"));

        // plugin-enabled in Italian -> fallback to en.yml -> "AntiXray enabled."
        String itMsg = i18n.getMessage("plugin-enabled", unknownPlayer);
        assertTrue(itMsg.contains("enabled"));
    }

    @Test
    void testPlaceholders() {
        i18n.load();

        Player player = mock(Player.class);
        when(player.getLocale()).thenReturn("en_US");

        // player-not-found: "&cPlayer not found: {player}"
        String msg = i18n.getMessage("player-not-found", player, "{player}", "Dinnerbone");
        assertTrue(msg.contains("Dinnerbone"));
        assertFalse(msg.contains("{player}"));
    }

    @Test
    void testConsoleMessage() {
        i18n.load();

        // should strip color codes
        String colored = i18n.getMessage("plugin-enabled", "en");
        String plain = i18n.getConsoleMessage("plugin-enabled");

        assertTrue(colored.contains("\u00a7a") || colored.contains("§a"));
        assertFalse(plain.contains("\u00a7"));
        assertFalse(plain.contains("§"));
    }
}
