package com.antixray.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.logging.Logger;

public class ConfigValidator {

    private static final int MIN_BLOCK_HEIGHT = 0;
    private static final int MAX_BLOCK_HEIGHT = 384;
    private static final double MIN_FAKE_ORE_CHANCE = 0.0;
    private static final double MAX_FAKE_ORE_CHANCE = 1.0;
    private static final int MIN_DEEPSLATE_BELOW_Y = -64;
    private static final int MAX_DEEPSLATE_BELOW_Y = 384;

    private final Logger logger;
    private int issueCount;

    public ConfigValidator(Logger logger) {
        this.logger = logger;
    }

    public int validate(FileConfiguration config) {
        issueCount = 0;
        validateEngineMode(config);
        validateMaxBlockHeight(config);
        validateFakeOreChance(config);
        validateDeepslateBelowY(config);
        validateEnabled(config);
        validateHiddenBlocks(config);
        validateLavaObscures(config);
        validateLeavesAreTransparent(config);
        validateBypassPermission(config);
        validateReplacementBlocks(config);
        validateWorldOverrides(config);
        validateResourcePack(config);
        validateCache(config);
        validateAsync(config);
        return issueCount;
    }

    private void validateEngineMode(FileConfiguration config) {
        Object mode = config.get("engine-mode");
        if (mode == null) return;
        if (mode instanceof Number) {
            int val = ((Number) mode).intValue();
            if (val < 1 || val > 3) {
                warn("engine-mode", val, 3);
                config.set("engine-mode", 3);
            }
            return;
        }
        String str = mode.toString().toUpperCase(java.util.Locale.ROOT);
        if (!str.startsWith("MODE_")) str = "MODE_" + str;
        try {
            com.antixray.engine.ObfuscationMode.valueOf(str);
        } catch (IllegalArgumentException e) {
            warn("engine-mode", mode, "MODE_3");
            config.set("engine-mode", 3);
        }
    }

    private void validateMaxBlockHeight(FileConfiguration config) {
        if (!config.contains("max-block-height")) return;
        int val = config.getInt("max-block-height");
        if (val < MIN_BLOCK_HEIGHT || val > MAX_BLOCK_HEIGHT) {
            warn("max-block-height", val, 64);
            config.set("max-block-height", 64);
        }
    }

    private void validateFakeOreChance(FileConfiguration config) {
        if (!config.contains("fake-ore-chance")) return;
        double val = config.getDouble("fake-ore-chance");
        if (val < MIN_FAKE_ORE_CHANCE || val > MAX_FAKE_ORE_CHANCE) {
            warn("fake-ore-chance", val, 0.07);
            config.set("fake-ore-chance", 0.07);
        }
    }

    private void validateDeepslateBelowY(FileConfiguration config) {
        if (!config.contains("deepslate-below-y")
                && !config.contains("replacement-blocks.overworld.deepslate-below-y")) return;
        int val = config.getInt("replacement-blocks.overworld.deepslate-below-y",
                config.getInt("deepslate-below-y", 0));
        if (val < MIN_DEEPSLATE_BELOW_Y || val > MAX_DEEPSLATE_BELOW_Y) {
            warn("deepslate-below-y", val, 0);
            config.set("deepslate-below-y", 0);
        }
    }

    private void validateEnabled(FileConfiguration config) {
        if (!config.contains("enabled")) return;
        Object val = config.get("enabled");
        if (!(val instanceof Boolean)) {
            warn("enabled", val, true);
            config.set("enabled", true);
        }
    }

    private void validateHiddenBlocks(FileConfiguration config) {
        if (!config.contains("hidden-blocks")) return;
        if (!(config.get("hidden-blocks") instanceof List)) {
            warn("hidden-blocks", config.get("hidden-blocks"), "empty list");
            config.set("hidden-blocks", List.of());
        }
    }

    private void validateLavaObscures(FileConfiguration config) {
        if (!config.contains("lava-obscures")) return;
        Object val = config.get("lava-obscures");
        if (!(val instanceof Boolean)) {
            warn("lava-obscures", val, true);
            config.set("lava-obscures", true);
        }
    }

    private void validateLeavesAreTransparent(FileConfiguration config) {
        if (!config.contains("leaves-are-transparent")) return;
        Object val = config.get("leaves-are-transparent");
        if (!(val instanceof Boolean)) {
            warn("leaves-are-transparent", val, true);
            config.set("leaves-are-transparent", true);
        }
    }

    private void validateBypassPermission(FileConfiguration config) {
        if (!config.contains("bypass-permission")) return;
        String val = config.getString("bypass-permission");
        if (val == null || val.isBlank()) {
            warn("bypass-permission", val, "antixray.bypass");
            config.set("bypass-permission", "antixray.bypass");
        }
    }

    private void validateReplacementBlocks(FileConfiguration config) {
        validateReplacementMaterial(config, "replacement-blocks.overworld.default", "stone");
        validateReplacementMaterial(config, "replacement-blocks.overworld.below-y", "deepslate");
        validateReplacementMaterial(config, "replacement-blocks.nether", "netherrack");
        validateReplacementMaterial(config, "replacement-blocks.end", "end_stone");
    }

    private void validateReplacementMaterial(FileConfiguration config, String path, String fallback) {
        if (!config.contains(path)) return;
        String val = config.getString(path);
        if (val == null || val.isBlank()) {
            warn(path, val, fallback);
            config.set(path, fallback);
            return;
        }
        org.bukkit.Material mat = org.bukkit.Material.matchMaterial(val);
        if (mat == null) {
            mat = org.bukkit.Material.matchMaterial(val.toUpperCase(java.util.Locale.ROOT).replace(" ", "_"));
        }
        if (mat == null || !mat.isBlock()) {
            warn(path, val, fallback);
            config.set(path, fallback);
        }
    }

    private void validateWorldOverrides(FileConfiguration config) {
        ConfigurationSection worlds = config.getConfigurationSection("worlds");
        if (worlds == null) return;
        for (String worldName : worlds.getKeys(false)) {
            ConfigurationSection ws = worlds.getConfigurationSection(worldName);
            if (ws == null) continue;
            String prefix = "worlds." + worldName + ".";
            if (ws.contains("engine-mode")) validateWorldEngineMode(ws, prefix);
            if (ws.contains("max-block-height")) validateWorldInt(ws, prefix, "max-block-height",
                    MIN_BLOCK_HEIGHT, MAX_BLOCK_HEIGHT, 64);
            if (ws.contains("fake-ore-chance")) validateWorldDouble(ws, prefix, "fake-ore-chance",
                    MIN_FAKE_ORE_CHANCE, MAX_FAKE_ORE_CHANCE, 0.07);
            if (ws.contains("enabled")) validateWorldBoolean(ws, prefix, "enabled");
            if (ws.contains("lava-obscures")) validateWorldBoolean(ws, prefix, "lava-obscures");
            if (ws.contains("leaves-are-transparent")) validateWorldBoolean(ws, prefix, "leaves-are-transparent");
            if (ws.contains("bypass-permission")) {
                String val = ws.getString("bypass-permission");
                if (val == null || val.isBlank()) {
                    warn(prefix + "bypass-permission", val, "antixray.bypass");
                    ws.set("bypass-permission", "antixray.bypass");
                }
            }
        }
    }

    private void validateWorldEngineMode(ConfigurationSection ws, String prefix) {
        Object mode = ws.get("engine-mode");
        if (mode instanceof Number) {
            int val = ((Number) mode).intValue();
            if (val < 1 || val > 3) {
                warn(prefix + "engine-mode", val, 3);
                ws.set("engine-mode", 3);
            }
            return;
        }
        if (mode != null) {
            String str = mode.toString().toUpperCase(java.util.Locale.ROOT);
            if (!str.startsWith("MODE_")) str = "MODE_" + str;
            try {
                com.antixray.engine.ObfuscationMode.valueOf(str);
            } catch (IllegalArgumentException e) {
                warn(prefix + "engine-mode", mode, "MODE_3");
                ws.set("engine-mode", 3);
            }
        }
    }

    private void validateWorldInt(ConfigurationSection ws, String prefix, String key, int min, int max, int fallback) {
        int val = ws.getInt(key);
        if (val < min || val > max) {
            warn(prefix + key, val, fallback);
            ws.set(key, fallback);
        }
    }

    private void validateWorldDouble(ConfigurationSection ws, String prefix, String key, double min, double max, double fallback) {
        double val = ws.getDouble(key);
        if (val < min || val > max) {
            warn(prefix + key, val, fallback);
            ws.set(key, fallback);
        }
    }

    private void validateWorldBoolean(ConfigurationSection ws, String prefix, String key) {
        Object val = ws.get(key);
        if (!(val instanceof Boolean)) {
            warn(prefix + key, val, true);
            ws.set(key, true);
        }
    }

    private void validateResourcePack(org.bukkit.configuration.file.FileConfiguration config) {
        if (!config.contains("resource-pack")) return;

        ConfigurationSection rp = config.getConfigurationSection("resource-pack");
        if (rp == null) return;

        if (rp.contains("force-pack") && !(rp.get("force-pack") instanceof Boolean)) {
            warn("resource-pack.force-pack", rp.get("force-pack"), false);
            rp.set("force-pack", false);
        }
        if (rp.contains("pack-url") && !(rp.get("pack-url") instanceof String)) {
            warn("resource-pack.pack-url", rp.get("pack-url"), "");
            rp.set("pack-url", "");
        }
        if (rp.contains("pack-hash") && !(rp.get("pack-hash") instanceof String)) {
            warn("resource-pack.pack-hash", rp.get("pack-hash"), "");
            rp.set("pack-hash", "");
        }
        if (rp.contains("kick-on-decline") && !(rp.get("kick-on-decline") instanceof Boolean)) {
            warn("resource-pack.kick-on-decline", rp.get("kick-on-decline"), true);
            rp.set("kick-on-decline", true);
        }
        if (rp.contains("kick-message") && !(rp.get("kick-message") instanceof String)) {
            warn("resource-pack.kick-message", rp.get("kick-message"), "This server requires the official resource pack.");
            rp.set("kick-message", "This server requires the official resource pack.");
        }
        if (rp.contains("delay-join-until-loaded") && !(rp.get("delay-join-until-loaded") instanceof Boolean)) {
            warn("resource-pack.delay-join-until-loaded", rp.get("delay-join-until-loaded"), true);
            rp.set("delay-join-until-loaded", true);
        }
        if (rp.contains("kick-on-failed") && !(rp.get("kick-on-failed") instanceof Boolean)) {
            warn("resource-pack.kick-on-failed", rp.get("kick-on-failed"), false);
            rp.set("kick-on-failed", false);
        }
    }

    private void warn(String field, Object invalidValue, Object fallback) {
        logger.warning("Invalid config value for '" + field + "': " + invalidValue
                + " — falling back to: " + fallback);
        issueCount++;
    }

    private void validateCache(FileConfiguration config) {
        if (config.contains("cache.l1.max-size")) {
            int val = config.getInt("cache.l1.max-size");
            if (val < 100) {
                warn("cache.l1.max-size", val, 5000);
                config.set("cache.l1.max-size", 5000);
            }
        }
        if (config.contains("cache.l1.expiry-seconds")) {
            int val = config.getInt("cache.l1.expiry-seconds");
            if (val < 10) {
                warn("cache.l1.expiry-seconds", val, 300);
                config.set("cache.l1.expiry-seconds", 300);
            }
        }
        if (config.contains("cache.l2.enabled") && !(config.get("cache.l2.enabled") instanceof Boolean)) {
            warn("cache.l2.enabled", config.get("cache.l2.enabled"), true);
            config.set("cache.l2.enabled", true);
        }
        if (config.contains("cache.l2.max-disk-mb")) {
            int val = config.getInt("cache.l2.max-disk-mb");
            if (val < 10) {
                warn("cache.l2.max-disk-mb", val, 500);
                config.set("cache.l2.max-disk-mb", 500);
            }
        }
        if (config.contains("cache.l2.expiry-seconds")) {
            int val = config.getInt("cache.l2.expiry-seconds");
            if (val < 10) {
                warn("cache.l2.expiry-seconds", val, 86400);
                config.set("cache.l2.expiry-seconds", 86400);
            }
        }
    }

    private void validateAsync(FileConfiguration config) {
        if (config.contains("async.pool-size")) {
            int val = config.getInt("async.pool-size");
            if (val < 0 || val > 64) {
                warn("async.pool-size", val, 0);
                config.set("async.pool-size", 0);
            }
        }
        if (config.contains("async.per-tick-budget-ms")) {
            long val = config.getLong("async.per-tick-budget-ms");
            if (val < 1 || val > 50) {
                warn("async.per-tick-budget-ms", val, 5);
                config.set("async.per-tick-budget-ms", 5);
            }
        }
        if (config.contains("async.chunk-timeout-ms")) {
            long val = config.getLong("async.chunk-timeout-ms");
            if (val < 5 || val > 10000) {
                warn("async.chunk-timeout-ms", val, 50);
                config.set("async.chunk-timeout-ms", 50);
            }
        }
        if (config.contains("async.max-queue-size")) {
            int val = config.getInt("async.max-queue-size");
            if (val < 10) {
                warn("async.max-queue-size", val, 10000);
                config.set("async.max-queue-size", 10000);
            }
        }
    }
}
