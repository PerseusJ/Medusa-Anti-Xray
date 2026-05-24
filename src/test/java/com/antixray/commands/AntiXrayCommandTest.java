package com.antixray.commands;

import com.antixray.AntiXrayPlugin;
import com.antixray.permissions.PermissionConstants;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AntiXrayCommandTest {

    private AntiXrayPlugin plugin;
    private Command command;
    private AntiXrayCommand antiXrayCommand;
    private MockedStatic<Bukkit> bukkitMock;
    private Server server;

    @BeforeEach
    void setUp() {
        plugin = mock(AntiXrayPlugin.class);
        command = mock(Command.class);
        antiXrayCommand = new AntiXrayCommand(plugin);

        server = mock(Server.class);
        bukkitMock = Mockito.mockStatic(Bukkit.class);
        bukkitMock.when(Bukkit::getServer).thenReturn(server);
    }

    @AfterEach
    void tearDown() {
        bukkitMock.close();
    }

    @Test
    @DisplayName("Tab completion suggests subcommands filter by prefix and permission")
    void tabComplete_suggestsSubcommands_filtered() {
        CommandSender sender = mock(CommandSender.class);
        // sender has permission for reload and stats, but not check
        when(sender.hasPermission(PermissionConstants.RELOAD)).thenReturn(true);
        when(sender.hasPermission(PermissionConstants.STATS)).thenReturn(true);
        when(sender.hasPermission(PermissionConstants.CHECK)).thenReturn(false);
        when(sender.hasPermission(PermissionConstants.MODE)).thenReturn(false);
        when(sender.hasPermission(PermissionConstants.CACHE)).thenReturn(false);
        when(sender.hasPermission(PermissionConstants.TOGGLE)).thenReturn(false);
        when(sender.hasPermission(PermissionConstants.ADMIN)).thenReturn(false);

        // typing nothing (prefix "")
        List<String> result1 = antiXrayCommand.onTabComplete(sender, command, "antixray", new String[]{""});
        assertNotNull(result1);
        assertTrue(result1.contains("reload"));
        assertTrue(result1.contains("stats"));
        assertFalse(result1.contains("check"));

        // typing "re"
        List<String> result2 = antiXrayCommand.onTabComplete(sender, command, "antixray", new String[]{"re"});
        assertNotNull(result2);
        assertTrue(result2.contains("reload"));
        assertFalse(result2.contains("stats"));

        // typing "rl" (reload alias check)
        List<String> result3 = antiXrayCommand.onTabComplete(sender, command, "antixray", new String[]{"rl"});
        assertNotNull(result3);
        assertTrue(result3.contains("reload"));
    }

    @Test
    @DisplayName("Tab completion suggests online players for stats and check")
    void tabComplete_suggestsPlayers() {
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission(PermissionConstants.STATS)).thenReturn(true);
        when(sender.hasPermission(PermissionConstants.CHECK)).thenReturn(true);

        Player player1 = mock(Player.class);
        when(player1.getName()).thenReturn("Steve");
        Player player2 = mock(Player.class);
        when(player2.getName()).thenReturn("Alex");

        bukkitMock.when(Bukkit::getOnlinePlayers).thenReturn(List.of(player1, player2));

        // /antixray stats -> suggest "Steve", "Alex"
        List<String> statsCompletions = antiXrayCommand.onTabComplete(sender, command, "antixray", new String[]{"stats", ""});
        assertNotNull(statsCompletions);
        assertTrue(statsCompletions.contains("Steve"));
        assertTrue(statsCompletions.contains("Alex"));

        // /antixray stats St -> suggest "Steve"
        List<String> statsFilter = antiXrayCommand.onTabComplete(sender, command, "antixray", new String[]{"stats", "St"});
        assertNotNull(statsFilter);
        assertTrue(statsFilter.contains("Steve"));
        assertFalse(statsFilter.contains("Alex"));

        // /antixray check -> suggest players
        List<String> checkCompletions = antiXrayCommand.onTabComplete(sender, command, "antixray", new String[]{"check", "Al"});
        assertNotNull(checkCompletions);
        assertFalse(checkCompletions.contains("Steve"));
        assertTrue(checkCompletions.contains("Alex"));
    }

    @Test
    @DisplayName("Tab completion suggests modes and worlds for mode command")
    void tabComplete_modeCommand() {
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission(PermissionConstants.MODE)).thenReturn(true);

        World world1 = mock(World.class);
        when(world1.getName()).thenReturn("world");
        World world2 = mock(World.class);
        when(world2.getName()).thenReturn("world_nether");

        bukkitMock.when(Bukkit::getWorlds).thenReturn(List.of(world1, world2));

        // /antixray mode -> suggest 1, 2, 3
        List<String> modes = antiXrayCommand.onTabComplete(sender, command, "antixray", new String[]{"mode", ""});
        assertNotNull(modes);
        assertEquals(3, modes.size());
        assertTrue(modes.contains("1"));
        assertTrue(modes.contains("2"));
        assertTrue(modes.contains("3"));

        // /antixray mode 1 -> suggest worlds
        List<String> worlds = antiXrayCommand.onTabComplete(sender, command, "antixray", new String[]{"mode", "1", ""});
        assertNotNull(worlds);
        assertEquals(2, worlds.size());
        assertTrue(worlds.contains("world"));
        assertTrue(worlds.contains("world_nether"));
    }

    @Test
    @DisplayName("Tab completion suggests clear and worlds for cache command")
    void tabComplete_cacheCommand() {
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission(PermissionConstants.CACHE)).thenReturn(true);

        World world = mock(World.class);
        when(world.getName()).thenReturn("world_the_end");
        bukkitMock.when(Bukkit::getWorlds).thenReturn(List.of(world));

        // /antixray cache -> suggest "clear"
        List<String> cacheArgs1 = antiXrayCommand.onTabComplete(sender, command, "antixray", new String[]{"cache", ""});
        assertNotNull(cacheArgs1);
        assertEquals(1, cacheArgs1.size());
        assertTrue(cacheArgs1.contains("clear"));

        // /antixray cache clear -> suggest worlds
        List<String> cacheArgs2 = antiXrayCommand.onTabComplete(sender, command, "antixray", new String[]{"cache", "clear", ""});
        assertNotNull(cacheArgs2);
        assertTrue(cacheArgs2.contains("world_the_end"));
    }

    @Test
    @DisplayName("Tab completion suggests worlds for toggle command")
    void tabComplete_toggleCommand() {
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission(PermissionConstants.TOGGLE)).thenReturn(true);

        World world = mock(World.class);
        when(world.getName()).thenReturn("lobby");
        bukkitMock.when(Bukkit::getWorlds).thenReturn(List.of(world));

        // /antixray toggle -> suggest worlds
        List<String> toggleArgs = antiXrayCommand.onTabComplete(sender, command, "antixray", new String[]{"toggle", ""});
        assertNotNull(toggleArgs);
        assertTrue(toggleArgs.contains("lobby"));
    }

    @Test
    @DisplayName("Tab completion restricts by permissions for subcommands deeper args")
    void tabComplete_permissionRestricted() {
        CommandSender sender = mock(CommandSender.class);
        // Sender does not have permission for stats
        when(sender.hasPermission(PermissionConstants.STATS)).thenReturn(false);

        List<String> completions = antiXrayCommand.onTabComplete(sender, command, "antixray", new String[]{"stats", ""});
        assertNotNull(completions);
        assertTrue(completions.isEmpty());
    }
}
