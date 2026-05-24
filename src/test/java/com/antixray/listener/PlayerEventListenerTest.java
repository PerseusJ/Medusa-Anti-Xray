package com.antixray.listener;

import com.antixray.AntiXrayPlugin;
import com.antixray.config.ConfigurationManager;
import com.antixray.config.WorldConfig;
import com.antixray.deobfuscation.PlayerData;
import com.antixray.util.FoliaSchedulerAdapter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.*;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlayerEventListenerTest {

    private AntiXrayPlugin plugin;
    private ConfigurationManager configManager;
    private PlayerEventListener listener;
    private Server oldServer;
    private Server server;
    private BukkitScheduler scheduler;

    @BeforeEach
    void setUp() throws Exception {
        java.lang.reflect.Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        oldServer = (Server) serverField.get(null);

        server = mock(Server.class);
        serverField.set(null, server);

        scheduler = mock(BukkitScheduler.class);
        when(server.getScheduler()).thenReturn(scheduler);

        BukkitTask bukkitTask = mock(BukkitTask.class);
        when(bukkitTask.getTaskId()).thenReturn(101);
        when(scheduler.runTaskLater(any(org.bukkit.plugin.Plugin.class), any(Runnable.class), anyLong())).thenReturn(bukkitTask);

        plugin = mock(AntiXrayPlugin.class);
        configManager = mock(ConfigurationManager.class);
        when(plugin.getConfigurationManager()).thenReturn(configManager);

        java.util.logging.Logger logger = java.util.logging.Logger.getLogger("AntiXrayTest");
        when(plugin.getLogger()).thenReturn(logger);
        when(plugin.getInterceptionMode()).thenReturn(com.antixray.packet.InterceptionMode.NONE);

        FoliaSchedulerAdapter schedulerAdapter = new FoliaSchedulerAdapter(plugin);
        when(plugin.getSchedulerAdapter()).thenReturn(schedulerAdapter);

        // Mock a world config for proximity updates
        WorldConfig worldConfig = mock(WorldConfig.class);
        when(configManager.getGlobalConfig()).thenReturn(worldConfig);
        when(worldConfig.getMaxRevealedPerPlayer()).thenReturn(1000);

        listener = new PlayerEventListener(plugin);
    }

    @AfterEach
    void tearDown() throws Exception {
        java.lang.reflect.Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(null, oldServer);
    }

    @Test
    void onPlayerJoin_forcePackDisabled_doesNothing() {
        when(configManager.isForcePack()).thenReturn(false);

        Player player = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);

        PlayerJoinEvent event = new PlayerJoinEvent(player, "Joined");
        listener.onPlayerJoin(event);

        verify(player, never()).setResourcePack(anyString());
        verify(player, never()).setResourcePack(anyString(), any(byte[].class));
    }

    @Test
    void onPlayerJoin_forcePackEnabled_sendsPackAndSetsPending() {
        when(configManager.isForcePack()).thenReturn(true);
        when(configManager.getPackUrl()).thenReturn("http://example.com/pack.zip");
        when(configManager.getPackHash()).thenReturn("da39a3ee5e6b4b0d3255bfef95601890afd80709");
        when(configManager.isDelayJoinUntilLoaded()).thenReturn(true);
        when(configManager.isKickOnDecline()).thenReturn(true);

        Player player = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);

        PlayerData playerData = new PlayerData(1000);
        when(plugin.getPlayerData(uuid)).thenReturn(playerData);

        PlayerJoinEvent event = new PlayerJoinEvent(player, "Joined");
        listener.onPlayerJoin(event);

        // Verify resource pack is requested
        verify(player, atLeastOnce()).setResourcePack(eq("http://example.com/pack.zip"), any(byte[].class));
        assertTrue(playerData.isResourcePackPending());
        assertNotEquals(-1, playerData.getResourcePackTimeoutTaskId());
    }

    @Test
    void onPlayerResourcePackStatus_declinedAndKick_kicksPlayer() {
        when(configManager.isForcePack()).thenReturn(true);
        when(configManager.isKickOnDecline()).thenReturn(true);
        when(configManager.getKickMessage()).thenReturn("Declined kick");

        Player player = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);

        PlayerResourcePackStatusEvent event = new PlayerResourcePackStatusEvent(
                player, UUID.randomUUID(), PlayerResourcePackStatusEvent.Status.DECLINED);
        listener.onPlayerResourcePackStatus(event);

        verify(player, times(1)).kickPlayer("Declined kick");
    }

    @Test
    void onPlayerResourcePackStatus_declinedAndNoKick_unfreezesPlayer() {
        when(configManager.isForcePack()).thenReturn(true);
        when(configManager.isKickOnDecline()).thenReturn(false);

        Player player = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);

        PlayerData playerData = new PlayerData(1000);
        playerData.setResourcePackPending(true);
        playerData.setResourcePackTimeoutTaskId(42);
        when(plugin.getPlayerData(uuid)).thenReturn(playerData);

        PlayerResourcePackStatusEvent event = new PlayerResourcePackStatusEvent(
                player, UUID.randomUUID(), PlayerResourcePackStatusEvent.Status.DECLINED);
        listener.onPlayerResourcePackStatus(event);

        verify(player, never()).kickPlayer(anyString());
        assertFalse(playerData.isResourcePackPending());
        verify(plugin, times(1)).cancelTask(42);
        assertEquals(-1, playerData.getResourcePackTimeoutTaskId());
    }

    @Test
    void onPlayerResourcePackStatus_accepted_cancelsTimeoutButKeepsPending() {
        when(configManager.isForcePack()).thenReturn(true);

        Player player = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);

        PlayerData playerData = new PlayerData(1000);
        playerData.setResourcePackPending(true);
        playerData.setResourcePackTimeoutTaskId(42);
        when(plugin.getPlayerData(uuid)).thenReturn(playerData);

        PlayerResourcePackStatusEvent event = new PlayerResourcePackStatusEvent(
                player, UUID.randomUUID(), PlayerResourcePackStatusEvent.Status.ACCEPTED);
        listener.onPlayerResourcePackStatus(event);

        assertTrue(playerData.isResourcePackPending());
        verify(plugin, times(1)).cancelTask(42);
        assertEquals(-1, playerData.getResourcePackTimeoutTaskId());
    }

    @Test
    void onPlayerResourcePackStatus_successfullyLoaded_unfreezesPlayer() {
        when(configManager.isForcePack()).thenReturn(true);

        Player player = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);

        PlayerData playerData = new PlayerData(1000);
        playerData.setResourcePackPending(true);
        playerData.setResourcePackTimeoutTaskId(42);
        when(plugin.getPlayerData(uuid)).thenReturn(playerData);

        PlayerResourcePackStatusEvent event = new PlayerResourcePackStatusEvent(
                player, UUID.randomUUID(), PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED);
        listener.onPlayerResourcePackStatus(event);

        assertFalse(playerData.isResourcePackPending());
        verify(plugin, times(1)).cancelTask(42);
        assertEquals(-1, playerData.getResourcePackTimeoutTaskId());
    }

    @Test
    void onPlayerMoveFreeze_pending_cancelsMovementButAllowsLooking() {
        Player player = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);

        PlayerData playerData = new PlayerData(1000);
        playerData.setResourcePackPending(true);
        when(plugin.getPlayerData(uuid)).thenReturn(playerData);

        World world = mock(World.class);
        Location from = new Location(world, 10, 64, 10, 0, 0);
        Location to = new Location(world, 11, 64, 10, 45, 10);

        PlayerMoveEvent event = new PlayerMoveEvent(player, from, to);
        listener.onPlayerMoveFreeze(event);

        // Should modify destination coordinate but keep look angles (yaw/pitch)
        Location resultTo = event.getTo();
        assertEquals(from.getX(), resultTo.getX());
        assertEquals(from.getY(), resultTo.getY());
        assertEquals(from.getZ(), resultTo.getZ());
        assertEquals(to.getYaw(), resultTo.getYaw());
        assertEquals(to.getPitch(), resultTo.getPitch());
    }

    @Test
    void onPlayerChat_pending_cancelsEventAndRemovesAsRecipient() {
        Player sender = mock(Player.class);
        UUID senderId = UUID.randomUUID();
        when(sender.getUniqueId()).thenReturn(senderId);

        PlayerData senderData = new PlayerData(1000);
        senderData.setResourcePackPending(true);
        when(plugin.getPlayerData(senderId)).thenReturn(senderData);

        Player recipient1 = mock(Player.class);
        UUID recId1 = UUID.randomUUID();
        when(recipient1.getUniqueId()).thenReturn(recId1);
        PlayerData recData1 = new PlayerData(1000);
        recData1.setResourcePackPending(false);
        when(plugin.getPlayerData(recId1)).thenReturn(recData1);

        Player recipient2 = mock(Player.class);
        UUID recId2 = UUID.randomUUID();
        when(recipient2.getUniqueId()).thenReturn(recId2);
        PlayerData recData2 = new PlayerData(1000);
        recData2.setResourcePackPending(true);
        when(plugin.getPlayerData(recId2)).thenReturn(recData2);

        Set<Player> recipients = new HashSet<>(Arrays.asList(recipient1, recipient2));
        AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(false, sender, "hello", recipients);

        listener.onPlayerChat(event);

        assertTrue(event.isCancelled());
        // recipient2 should be removed because they are pending, recipient1 should remain
        assertTrue(recipients.contains(recipient1));
        assertFalse(recipients.contains(recipient2));
    }

    @Test
    void onBlockBreak_pending_cancelsEvent() {
        Player player = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);

        PlayerData playerData = new PlayerData(1000);
        playerData.setResourcePackPending(true);
        when(plugin.getPlayerData(uuid)).thenReturn(playerData);

        org.bukkit.block.Block block = mock(org.bukkit.block.Block.class);
        BlockBreakEvent event = new BlockBreakEvent(block, player);

        listener.onBlockBreak(event);

        assertTrue(event.isCancelled());
    }
}
