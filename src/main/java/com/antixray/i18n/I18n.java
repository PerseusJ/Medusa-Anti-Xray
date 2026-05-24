package com.antixray.i18n;

import com.antixray.AntiXrayPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages plugin internationalization (i18n), supporting per-player setting locales,
 * and falling back to English (en.yml) if translations are missing.
 */
public class I18n {

    private final AntiXrayPlugin plugin;
    private final Map<String, FileConfiguration> localeConfigs = new ConcurrentHashMap<>();
    private FileConfiguration defaultConfiguration;

    public I18n(AntiXrayPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Saves default resource files to the plugin data folder and loads them.
     */
    public void load() {
        localeConfigs.clear();
        File langDir = new File(plugin.getDataFolder(), "languages");
        if (!langDir.exists()) {
            langDir.mkdirs();
        }

        String[] locales = {"en", "es", "de", "fr", "ja", "zh", "ko"};

        // Load default en.yml configuration from resources as a final fallback in memory
        try (InputStream is = plugin.getResource("languages/en.yml")) {
            if (is != null) {
                defaultConfiguration = YamlConfiguration.loadConfiguration(new InputStreamReader(is, StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load default en.yml resource: " + e.getMessage());
        }

        for (String locale : locales) {
            File file = new File(langDir, locale + ".yml");
            if (!file.exists()) {
                try {
                    plugin.saveResource("languages/" + locale + ".yml", false);
                } catch (Exception e) {
                    plugin.getLogger().warning("Could not save languages/" + locale + ".yml: " + e.getMessage());
                }
            }
            if (file.exists()) {
                try {
                    FileConfiguration config = YamlConfiguration.loadConfiguration(file);
                    // Add default resource fallback to each loaded config for missing keys
                    try (InputStream is = plugin.getResource("languages/" + locale + ".yml")) {
                        if (is != null) {
                            FileConfiguration def = YamlConfiguration.loadConfiguration(new InputStreamReader(is, StandardCharsets.UTF_8));
                            config.setDefaults(def);
                        }
                    } catch (Exception ignored) {}

                    localeConfigs.put(locale.toLowerCase(Locale.ROOT), config);
                } catch (Exception e) {
                    plugin.getLogger().severe("Failed to load language file " + file.getName() + ": " + e.getMessage());
                }
            }
        }
    }

    /**
     * Translates a message key according to the sender's locale.
     *
     * @param key          the translation key
     * @param sender       the command sender
     * @param placeholders alternating key-value pairs for replacement (e.g. "{player}", name)
     * @return the formatted, colorized, translated message
     */
    public String getMessage(String key, CommandSender sender, Object... placeholders) {
        if (sender instanceof Player) {
            return getMessage(key, (Player) sender, placeholders);
        }
        return getMessage(key, "en", placeholders);
    }

    /**
     * Translates a message key according to the player's client settings locale.
     *
     * @param key          the translation key
     * @param player       the player
     * @param placeholders alternating key-value pairs for replacement (e.g. "{player}", name)
     * @return the formatted, colorized, translated message
     */
    public String getMessage(String key, Player player, Object... placeholders) {
        String localeStr = "en";
        if (player != null) {
            String clientLocale = player.getLocale();
            if (clientLocale != null && !clientLocale.isEmpty()) {
                localeStr = clientLocale.toLowerCase(Locale.ROOT);
            }
        }
        return getMessage(key, localeStr, placeholders);
    }

    /**
     * Translates a message key according to a given locale string.
     *
     * @param key          the translation key
     * @param localeStr    the locale identifier (e.g., "es", "zh_CN")
     * @param placeholders alternating key-value pairs for replacement (e.g. "{player}", name)
     * @return the formatted, colorized, translated message
     */
    public String getMessage(String key, String localeStr, Object... placeholders) {
        String message = getRawMessage(key, localeStr);
        if (message == null) {
            message = key;
        }

        // Color format
        message = ChatColor.translateAlternateColorCodes('&', message);

        // Replace placeholders
        if (placeholders.length > 0) {
            for (int i = 0; i < placeholders.length; i += 2) {
                if (i + 1 < placeholders.length) {
                    String placeholder = placeholders[i].toString();
                    String replacement = placeholders[i + 1] != null ? placeholders[i + 1].toString() : "";
                    message = message.replace(placeholder, replacement);
                }
            }
        }

        return message;
    }

    /**
     * Returns a translated message stripped of ChatColors (ideal for console logs).
     *
     * @param key          the translation key
     * @param placeholders alternating key-value pairs for replacement (e.g. "{player}", name)
     * @return the uncolorized, translated message
     */
    public String getConsoleMessage(String key, Object... placeholders) {
        String msg = getMessage(key, "en", placeholders);
        return ChatColor.stripColor(msg);
    }

    private String getRawMessage(String key, String localeStr) {
        if (localeStr == null) {
            localeStr = "en";
        }

        // Normalize locale e.g. en_US -> en_us or en-us -> en_us
        localeStr = localeStr.replace('-', '_').toLowerCase(Locale.ROOT);

        // Try exact locale configuration, e.g. es_es
        FileConfiguration config = localeConfigs.get(localeStr);
        if (config != null && config.getString(key) != null) {
            return config.getString(key);
        }

        // Try base language code, e.g. es
        String lang = localeStr;
        int underscoreIdx = localeStr.indexOf('_');
        if (underscoreIdx != -1) {
            lang = localeStr.substring(0, underscoreIdx);
            config = localeConfigs.get(lang);
            if (config != null && config.getString(key) != null) {
                return config.getString(key);
            }
        }

        // Try fallback to en (English) config
        config = localeConfigs.get("en");
        if (config != null && config.getString(key) != null) {
            return config.getString(key);
        }

        // Try default configuration loaded from resource
        if (defaultConfiguration != null && defaultConfiguration.getString(key) != null) {
            return defaultConfiguration.getString(key);
        }

        return null;
    }
}
