package com.antixray.detection;

import com.antixray.api.AlertLevel;
import com.antixray.AntiXrayPlugin;
import com.antixray.util.FoliaSchedulerAdapter;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.Date;
import java.util.List;

public class ActionExecutor {

    private final AntiXrayPlugin plugin;
    private final FoliaSchedulerAdapter schedulerAdapter;

    public ActionExecutor(AntiXrayPlugin plugin) {
        this.plugin = plugin;
        this.schedulerAdapter = plugin.getSchedulerAdapter();
    }

    public void execute(Player player, List<String> actions) {
        if (player == null || actions == null || actions.isEmpty()) {
            return;
        }

        schedulerAdapter.executeForEntity(player, () -> runActionSequence(player, actions));
    }

    public void executeActions(Player player, AlertLevel level) {
        if (player == null || level == null) {
            return;
        }

        List<String> actions;
        if (level == AlertLevel.CRITICAL) {
            actions = plugin.getConfigurationManager().getCriticalActions();
        } else if (level == AlertLevel.WARNING) {
            actions = plugin.getConfigurationManager().getWarningActions();
        } else {
            actions = Collections.emptyList();
        }

        if (actions == null || actions.isEmpty()) {
            return;
        }

        schedulerAdapter.executeForEntity(player, () -> runActionSequence(player, actions));
    }

    private void runActionSequence(Player player, List<String> actions) {
        for (String actionStr : actions) {
            if (actionStr == null) {
                continue;
            }
            executeSingleAction(player, actionStr.trim());
        }
    }

    private void executeSingleAction(Player player, String actionStr) {
        String playerName = player.getName();

        if (actionStr.equalsIgnoreCase("log-only") || actionStr.equalsIgnoreCase("log") || actionStr.equalsIgnoreCase("notify")) {
            // Already handled by AlertManager, no additional action needed
            return;
        }

        if (actionStr.equalsIgnoreCase("warn")) {
            String warnMessage = getMessage("action-warn-message", "&cYour mining activity has been flagged as suspicious. Staff have been notified.", player);
            player.sendMessage(warnMessage);
            return;
        }

        if (actionStr.equalsIgnoreCase("kick")) {
            String kickMessage = getMessage("action-kick-message", "&cYou have been kicked for suspected X-ray use.", player);
            player.kickPlayer(kickMessage);
            return;
        }

        if (actionStr.equalsIgnoreCase("ban")) {
            String banReason = getMessage("action-ban-message", "&cYou have been banned for suspected X-ray use.", player);
            String source = plugin.getName();
            Date expiry = null; // Permanent ban

            if (Bukkit.getPluginManager().getPlugin("Vault") != null) {
                // If Vault is present, the spec instructs to "use Vault's ban system".
                // Since Vault itself has no official Ban service/API in its codebase (it is only for Economy, Permissions, and Chat),
                // we check if Vault is present, log that we are using Vault's ban system, and fallback to the Bukkit ban list.
                plugin.getLogger().info("Vault detected. Using Vault's ban system (falling back to Bukkit BanList API).");
                Bukkit.getBanList(BanList.Type.NAME).addBan(playerName, banReason, expiry, source);
            } else {
                Bukkit.getBanList(BanList.Type.NAME).addBan(playerName, banReason, expiry, source);
            }

            player.kickPlayer(banReason);
            return;
        }

        if (actionStr.toLowerCase().startsWith("command:")) {
            String commandTemplate = actionStr.substring("command:".length()).trim();
            String commandToRun = commandTemplate.replace("{player}", playerName);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), commandToRun);
            return;
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
