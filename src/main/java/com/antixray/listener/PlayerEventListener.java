package com.antixray.listener;

import com.antixray.AntiXrayPlugin;
import com.antixray.deobfuscation.DeobfuscationManager;
import com.antixray.deobfuscation.PlayerData;
import com.antixray.detection.PlayerStatistics;
import com.antixray.detection.StatisticsStorage;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.ChatColor;
import com.antixray.config.ConfigurationManager;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.entity.EntityDamageEvent;

public class PlayerEventListener implements Listener {

    private static final long JOIN_PROXIMITY_DELAY_TICKS = 20L;

    private final AntiXrayPlugin plugin;

    public PlayerEventListener(AntiXrayPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.initializePlayerData(player.getUniqueId());

        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        if (data != null) {
            StatisticsStorage storage = plugin.getStatisticsStorage();
            if (storage != null) {
                storage.loadPlayerStats(player.getUniqueId(), data.getStatistics());
            }

        int taskId = plugin.getSchedulerAdapter().runTaskLaterForEntity(player, () -> {
            Player p = Bukkit.getPlayer(player.getUniqueId());
            if (p == null || !p.isOnline()) return;
            if (p.getGameMode() == GameMode.SPECTATOR) return;

            PlayerData d = plugin.getPlayerData(player.getUniqueId());
            if (d == null) return;

            DeobfuscationManager manager = plugin.getDeobfuscationManager();
            if (manager != null) {
                manager.deobfuscateAround(p, p.getLocation());
            }
        }, JOIN_PROXIMITY_DELAY_TICKS);
        data.setPendingJoinTaskId(taskId);
        }

        plugin.getLogger().info("Player " + player.getName() + " connected. "
                + "AntiXray active (mode: " + plugin.getInterceptionMode() + ").");

        ConfigurationManager config = plugin.getConfigurationManager();
        if (config != null && config.isForcePack()) {
            String url = config.getPackUrl();
            String hash = config.getPackHash();
            if (url != null && !url.isEmpty()) {
                if (config.isDelayJoinUntilLoaded() && data != null) {
                    data.setResourcePackPending(true);

            int timeoutTaskId = plugin.getSchedulerAdapter().runTaskLaterForEntity(player, () -> {
                Player p = Bukkit.getPlayer(player.getUniqueId());
                if (p != null && p.isOnline()) {
                    PlayerData d = plugin.getPlayerData(p.getUniqueId());
                    if (d != null && d.isResourcePackPending()) {
                        plugin.getLogger().warning("Resource pack loading timed out for player " + p.getName() + ". Kicking.");
                        p.kickPlayer(getMessage("resource-pack-timeout-kick", "Resource pack download timed out. Please rejoin.", p));
                    }
                }
            }, 30 * 20L);
            data.setResourcePackTimeoutTaskId(timeoutTaskId);
                }

                sendResourcePack(player, url, hash, config.isKickOnDecline(), getMessage("resource-pack-kick-message", config.getKickMessage() != null ? config.getKickMessage() : "This server requires the official resource pack.", player));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        StatisticsStorage storage = plugin.getStatisticsStorage();
        if (storage != null) {
            PlayerData data = plugin.getPlayerData(player.getUniqueId());
            if (data != null) {
                storage.savePlayerStats(player.getUniqueId(), player.getName(), data.getStatistics());
            }
        }
        plugin.removePlayerData(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        if (data != null) {
            data.fullReset();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        if (data == null) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        double threshold = plugin.getConfigurationManager().getGlobalConfig().getMovementThreshold();
        if (threshold <= 0) return;

        Location lastChecked = data.getLastCheckedPosition();
        if (lastChecked != null && !hasMovedPastThreshold(to, lastChecked, threshold)) {
            return;
        }

        org.bukkit.util.Vector velocity = player.getVelocity();
        double speedSq = velocity.lengthSquared();
        double elytraThreshold = plugin.getConfigurationManager()
                .getWorldConfig(to.getWorld()).getElytraVelocityThreshold();
        data.updateElytraState(speedSq > (elytraThreshold * elytraThreshold));

        data.setLastCheckedPosition(to.clone());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        if (data != null) {
            data.clear();
        }

        Location destination = event.getTo();
        if (destination == null || destination.getWorld() == null) return;

        if (player.getGameMode() == GameMode.SPECTATOR) return;

        DeobfuscationManager manager = plugin.getDeobfuscationManager();
        if (manager != null) {
            manager.deobfuscateAround(player, destination);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        if (data != null) {
            data.clear();
        }

        Location respawnLoc = event.getRespawnLocation();
        if (respawnLoc.getWorld() == null) return;

        if (player.getGameMode() == GameMode.SPECTATOR) return;

        DeobfuscationManager manager = plugin.getDeobfuscationManager();
        if (manager != null) {
            manager.deobfuscateAround(player, respawnLoc);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerGameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        GameMode newMode = event.getNewGameMode();
        GameMode oldMode = player.getGameMode();

        if (newMode == GameMode.SPECTATOR) {
            PlayerData data = plugin.getPlayerData(player.getUniqueId());
            if (data != null) {
                data.clear();
            }
        }

        if (oldMode == GameMode.SPECTATOR && newMode != GameMode.SPECTATOR) {
        plugin.getSchedulerAdapter().runTaskLaterForEntity(player, () -> {
            Player p = Bukkit.getPlayer(player.getUniqueId());
            if (p == null || !p.isOnline()) return;

            DeobfuscationManager manager = plugin.getDeobfuscationManager();
            if (manager != null) {
                manager.deobfuscateAround(p, p.getLocation());
            }
        }, 1L);
        }
    }

    private boolean hasMovedPastThreshold(Location current, Location last, double threshold) {
        if (!current.getWorld().getName().equals(last.getWorld().getName())) return true;
        double dx = current.getX() - last.getX();
        double dy = current.getY() - last.getY();
        double dz = current.getZ() - last.getZ();
        return (dx * dx + dy * dy + dz * dz) > (threshold * threshold);
    }

    private void sendResourcePack(Player player, String url, String hash, boolean required, String promptText) {
        try {
            Class<?> componentClass = Class.forName("net.kyori.adventure.text.Component");
            Class<?> miniMessageClass = Class.forName("net.kyori.adventure.text.minimessage.MiniMessage");
            Object miniMessage = miniMessageClass.getMethod("miniMessage").invoke(null);
            Object promptComponent = miniMessageClass.getMethod("deserialize", String.class).invoke(miniMessage, promptText);

            java.lang.reflect.Method method = player.getClass().getMethod("setResourcePack", String.class, String.class, boolean.class, componentClass);
            method.invoke(player, url, hash, required, promptComponent);
            return;
        } catch (Throwable ignored) {}

        try {
            java.lang.reflect.Method method = player.getClass().getMethod("setResourcePack", String.class, String.class, boolean.class);
            method.invoke(player, url, hash, required);
            return;
        } catch (Throwable ignored) {}

        try {
            byte[] hashBytes = hexToBytes(hash);
            if (hashBytes != null && hashBytes.length == 20) {
                player.setResourcePack(url, hashBytes);
                return;
            }
        } catch (Throwable ignored) {}

        player.setResourcePack(url);
    }

    private byte[] hexToBytes(String hex) {
        if (hex == null || hex.isEmpty()) {
            return null;
        }
        try {
            String cleanHex = hex.trim();
            if (cleanHex.length() != 40) {
                return null;
            }
            byte[] bytes = new byte[20];
            for (int i = 0; i < 20; i++) {
                bytes[i] = (byte) Integer.parseInt(cleanHex.substring(i * 2, i * 2 + 2), 16);
            }
            return bytes;
        } catch (Exception e) {
            return null;
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerResourcePackStatus(PlayerResourcePackStatusEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        ConfigurationManager config = plugin.getConfigurationManager();

        if (config == null || !config.isForcePack()) return;

        PlayerResourcePackStatusEvent.Status status = event.getStatus();

        switch (status) {
            case DECLINED -> {
                plugin.getLogger().info("Player " + player.getName() + " declined the resource pack.");
                if (config.isKickOnDecline()) {
                    player.kickPlayer(getMessage("resource-pack-declined-kick", config.getKickMessage(), player));
                } else {
                    if (data != null) {
                        data.setResourcePackPending(false);
                        int timeoutId = data.getResourcePackTimeoutTaskId();
                        if (timeoutId != -1) {
                            plugin.cancelTask(timeoutId);
                            data.setResourcePackTimeoutTaskId(-1);
                        }
                    }
                }
            }
            case ACCEPTED -> {
                plugin.getLogger().info("Player " + player.getName() + " accepted the resource pack. Downloading...");
                if (data != null) {
                    int timeoutId = data.getResourcePackTimeoutTaskId();
                    if (timeoutId != -1) {
                        plugin.cancelTask(timeoutId);
                        data.setResourcePackTimeoutTaskId(-1);
                    }
                }
            }
            case SUCCESSFULLY_LOADED -> {
                plugin.getLogger().info("Player " + player.getName() + " successfully loaded the resource pack.");
                if (data != null) {
                    data.setResourcePackPending(false);
                    int timeoutId = data.getResourcePackTimeoutTaskId();
                    if (timeoutId != -1) {
                        plugin.cancelTask(timeoutId);
                        data.setResourcePackTimeoutTaskId(-1);
                    }
                }
            }
            case FAILED_DOWNLOAD -> {
                plugin.getLogger().warning("Player " + player.getName() + " failed to download the resource pack.");
                if (config.isKickOnFailed()) {
                    player.kickPlayer(getMessage("resource-pack-kick-message", config.getKickMessage(), player));
                } else {
                    if (data != null) {
                        data.setResourcePackPending(false);
                        int timeoutId = data.getResourcePackTimeoutTaskId();
                        if (timeoutId != -1) {
                            plugin.cancelTask(timeoutId);
                            data.setResourcePackTimeoutTaskId(-1);
                        }
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerMoveFreeze(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        if (data != null && data.isResourcePackPending()) {
            Location from = event.getFrom();
            Location to = event.getTo();
            if (to != null && (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ())) {
                Location loc = from.clone();
                loc.setYaw(to.getYaw());
                loc.setPitch(to.getPitch());
                event.setTo(loc);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        if (data != null && data.isResourcePackPending()) {
            event.setCancelled(true);
            player.sendMessage(getMessage("resource-pack-chat-blocked", "&cYou must accept the resource pack to chat.", player));
        }

        // Hide chat from other pending players
        event.getRecipients().removeIf(p -> {
            PlayerData d = plugin.getPlayerData(p.getUniqueId());
            return d != null && d.isResourcePackPending();
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        if (data != null && data.isResourcePackPending()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        if (data != null && data.isResourcePackPending()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        if (data != null && data.isResourcePackPending()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        if (data != null && data.isResourcePackPending()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        if (data != null && data.isResourcePackPending()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerPickupItem(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            PlayerData data = plugin.getPlayerData(player.getUniqueId());
            if (data != null && data.isResourcePackPending()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player) {
            PlayerData data = plugin.getPlayerData(player.getUniqueId());
            if (data != null && data.isResourcePackPending()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            PlayerData data = plugin.getPlayerData(player.getUniqueId());
            if (data != null && data.isResourcePackPending()) {
                event.setCancelled(true);
            }
        }
    }

    private String getMessage(String key, String def, Player player) {
        if (plugin.getI18n() != null) {
            return plugin.getI18n().getMessage(key, player);
        }
        String msg = def;
        if (plugin.getConfigurationManager() != null) {
            msg = plugin.getConfigurationManager().getMessage(key, def);
        }
        if (msg == null) {
            msg = def;
        }
        if (msg == null) {
            msg = "";
        }
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', msg);
    }
}
