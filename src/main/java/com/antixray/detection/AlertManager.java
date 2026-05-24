package com.antixray.detection;

import com.antixray.api.AlertLevel;
import com.antixray.AntiXrayPlugin;
import com.antixray.config.ConfigurationManager;
import com.antixray.util.FoliaSchedulerAdapter;
import com.google.gson.Gson;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public class AlertManager {

    private final AntiXrayPlugin plugin;
    private final Logger fileLogger;
    private FileHandler fileHandler;
    private final Gson gson = new Gson();

    public AlertManager(AntiXrayPlugin plugin) {
        this.plugin = plugin;
        this.fileLogger = Logger.getLogger("AntiXrayDetectionLog");
        this.fileLogger.setUseParentHandlers(false);

        try {
            File logDir = plugin.getDataFolder();
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            File logFile = new File(logDir, "detection.log");
            // Max 10 MB per file, keep up to 5 files, append mode
            this.fileHandler = new FileHandler(logFile.getPath(), 10_000_000, 5, true);
            this.fileHandler.setFormatter(new Formatter() {
                private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

                @Override
                public String format(LogRecord record) {
                    return "[" + sdf.format(new Date(record.getMillis())) + "] [" + record.getLevel() + "] " + record.getMessage() + "\n";
                }
            });
            this.fileLogger.addHandler(fileHandler);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize detection log file handler", e);
        }
    }

    public void dispatch(Player player, AlertLevel level, List<String> metrics) {
        if (player == null || level == null) {
            return;
        }

        StatisticsStorage storage = plugin.getStatisticsStorage();
        if (storage != null) {
            String metricsStr = metrics != null ? String.join(", ", metrics) : "";
            storage.recordAlert(player.getUniqueId(), player.getName(), level, metricsStr);
        }

        dispatchAlert(player, DetectionResult.of(level, metrics));
    }

    public void dispatchAlert(Player player, DetectionResult result) {
        if (result == null || !result.isDetected()) {
            return;
        }

        AlertLevel level = result.getLevel();
        List<String> metrics = result.getTriggeredMetrics();
        String playerName = player.getName();
        String metricsStr = String.join(", ", metrics);

        // 1. Log File (always append all alerts)
        logToFile(playerName, player.getUniqueId(), level, metricsStr);

        // 2. Console Log
        if (plugin.getConfigurationManager().isNotifyConsole()) {
            logToConsole(playerName, level, metricsStr);
        }

        // 3. In-game Chat
        if (plugin.getConfigurationManager().isNotifyInGame()) {
            sendInGameChat(playerName, level, metricsStr);
        }

        // 4. Webhook
        if (plugin.getConfigurationManager().isWebhookEnabled()) {
            sendWebhook(playerName, level, metrics);
        }
    }

    private void logToFile(String playerName, UUID uuid, AlertLevel level, String metricsStr) {
        String logMessage = String.format("Player: %s (%s) | Level: %s | Metrics: %s",
                playerName, uuid.toString(), level.name(), metricsStr);
        fileLogger.log(Level.WARNING, logMessage);
    }

    private void logToConsole(String playerName, AlertLevel level, String metricsStr) {
        String msgKey = "alert-" + level.name().toLowerCase(Locale.ROOT);
        String defTemplate = getFallbackAlertTemplate(level);
        String message = getConsoleMessage(msgKey, defTemplate, "{player}", playerName, "{metrics}", metricsStr);
        plugin.getLogger().warning(message);
    }

    private void sendInGameChat(String playerName, AlertLevel level, String metricsStr) {
        String levelStr = level.name();
        String msgKey = "alert-" + levelStr.toLowerCase(Locale.ROOT);
        String defTemplate = getFallbackAlertTemplate(level);

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission("antixray.notify")) {
                String formattedMsg = getMessage(msgKey, defTemplate, online, "{player}", playerName, "{metrics}", metricsStr);
                online.sendMessage(formattedMsg);
            }
        }
    }

    private void sendWebhook(String playerName, AlertLevel level, List<String> metrics) {
        String urlStr = plugin.getConfigurationManager().getWebhookUrl();
        if (urlStr == null || urlStr.isBlank()) {
            return;
        }

        String format = plugin.getConfigurationManager().getWebhookFormat();
        final String payloadJson;

        long timestamp = System.currentTimeMillis();
        if ("discord-embed".equalsIgnoreCase(format)) {
            // Discord embed color (Hex to Int): CRITICAL=red(16711680), WARNING=orange(16753920), INFO=yellow(16776960)
            int color = 16776960;
            if (level == AlertLevel.CRITICAL) {
                color = 16711680;
            } else if (level == AlertLevel.WARNING) {
                color = 16753920;
            }

            SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
            isoFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            String isoTimestamp = isoFormat.format(new Date(timestamp));

            Map<String, Object> embed = new HashMap<>();
            embed.put("title", "AntiXray Detection Alert");
            embed.put("color", color);
            embed.put("timestamp", isoTimestamp);

            List<Map<String, Object>> fields = new ArrayList<>();
            
            Map<String, Object> fPlayer = new HashMap<>();
            fPlayer.put("name", "Player");
            fPlayer.put("value", playerName);
            fPlayer.put("inline", true);
            fields.add(fPlayer);

            Map<String, Object> fLevel = new HashMap<>();
            fLevel.put("name", "Level");
            fLevel.put("value", level.name());
            fLevel.put("inline", true);
            fields.add(fLevel);

            Map<String, Object> fMetrics = new HashMap<>();
            fMetrics.put("name", "Triggered Metrics");
            fMetrics.put("value", String.join(", ", metrics));
            fMetrics.put("inline", false);
            fields.add(fMetrics);

            embed.put("fields", fields);

            Map<String, Object> root = new HashMap<>();
            root.put("embeds", List.of(embed));
            payloadJson = gson.toJson(root);
        } else {
            // Plain JSON format
            Map<String, Object> root = new HashMap<>();
            root.put("player", playerName);
            root.put("level", level.name());
            root.put("metrics", metrics);
            root.put("timestamp", timestamp);
            payloadJson = gson.toJson(root);
        }

        // Perform async HTTP call
        plugin.getSchedulerAdapter().runAsync(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(urlStr);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("User-Agent", "AntiXray-Webhook");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setDoOutput(true);

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = payloadJson.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int responseCode = conn.getResponseCode();
                if (responseCode < 200 || responseCode >= 300) {
                    plugin.getLogger().warning("Webhook returned HTTP response code: " + responseCode);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to send webhook alert: " + e.getMessage());
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });
    }

    public void shutdown() {
        if (fileHandler != null) {
            fileHandler.close();
            fileLogger.removeHandler(fileHandler);
        }
    }

    private String getFallbackAlertTemplate(AlertLevel level) {
        switch (level) {
            case INFO:
                return "&e[AntiXray] &e{player} &7triggered INFO alert: {metrics}";
            case WARNING:
                return "&6[AntiXray] &6{player} &7triggered WARNING alert: {metrics}";
            case CRITICAL:
                return "&c[AntiXray] &c{player} &7triggered CRITICAL alert: {metrics}";
            default:
                return "[AntiXray] {player} triggered alert: {metrics}";
        }
    }

    private String getMessage(String key, String def, Player player, Object... placeholders) {
        if (plugin.getI18n() != null) {
            return plugin.getI18n().getMessage(key, player, placeholders);
        }
        String msg = def;
        if (plugin.getConfigurationManager() != null) {
            msg = plugin.getConfigurationManager().getMessage(key, def);
        }
        if (msg == null) {
            msg = def;
        }
        if (placeholders.length > 0) {
            for (int i = 0; i < placeholders.length; i += 2) {
                if (i + 1 < placeholders.length) {
                    String placeholder = placeholders[i].toString();
                    String replacement = placeholders[i + 1] != null ? placeholders[i + 1].toString() : "";
                    msg = msg.replace(placeholder, replacement);
                }
            }
        }
        return ChatColor.translateAlternateColorCodes('&', msg);
    }

    private String getConsoleMessage(String key, String def, Object... placeholders) {
        if (plugin.getI18n() != null) {
            return plugin.getI18n().getConsoleMessage(key, placeholders);
        }
        String msg = getMessage(key, def, null, placeholders);
        return ChatColor.stripColor(msg);
    }
}
