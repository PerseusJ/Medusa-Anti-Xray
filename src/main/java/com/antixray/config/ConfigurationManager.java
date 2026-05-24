package com.antixray.config;

import com.antixray.engine.ObfuscationMode;
import com.antixray.detection.DetectionEngine;
import com.antixray.detection.DetectionEngine.DetectionThresholds;
import com.antixray.nms.NmsAdapter;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.ChatColor;
import com.antixray.AntiXrayPlugin;
import com.antixray.i18n.I18n;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public class ConfigurationManager {

    private final JavaPlugin plugin;
    private final NmsAdapter nmsAdapter;
    private final Logger logger;
    private final ConfigValidator validator;

    private volatile WorldConfig globalConfig;
    private volatile Map<String, WorldConfig> worldConfigs;
    private volatile int configHash;
    private final List<ConfigChangeListener> configChangeListeners = new ArrayList<>();

    private volatile boolean detectionEnabled = true;
    private volatile int detectionMinimumSampleSize = 100;
    private volatile int detectionGracePeriodMinutes = 30;
    private volatile DetectionThresholds detectionThresholds = DetectionThresholds.defaults();
    private volatile List<String> warningActions = List.of("log", "notify");
    private volatile List<String> criticalActions = List.of("log", "notify", "command:tempban {player} 1h Suspected X-ray use");
    private volatile boolean notifyInGame = true;
    private volatile boolean notifyConsole = true;
    private volatile boolean webhookEnabled = false;
    private volatile String webhookUrl = "";
    private volatile String webhookFormat = "discord-embed";
    private volatile boolean forcePack = false;
    private volatile String packUrl = "";
    private volatile String packHash = "";
    private volatile boolean kickOnDecline = true;
    private volatile String kickMessage = "This server requires the official resource pack.";
    private volatile boolean delayJoinUntilLoaded = true;
    private volatile boolean kickOnFailed = false;

    public ConfigurationManager(JavaPlugin plugin, NmsAdapter nmsAdapter) {
        this.plugin = plugin;
        this.nmsAdapter = nmsAdapter;
        this.logger = plugin.getLogger();
        this.validator = new ConfigValidator(logger);
        this.worldConfigs = new HashMap<>();
    }

    public void addConfigChangeListener(ConfigChangeListener listener) {
        configChangeListeners.add(listener);
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        int issues = validator.validate(config);
        if (issues > 0) {
            logger.info("Config validation completed with " + issues + " issue(s). Safe defaults were applied.");
        }

        globalConfig = buildGlobalConfig(config);

        this.detectionEnabled = config.getBoolean("detection.enabled", true);
        this.detectionMinimumSampleSize = config.getInt("detection.minimum-sample-size", 100);
        this.detectionGracePeriodMinutes = config.getInt("detection.grace-period-minutes", 30);
        
        DetectionThresholds t = DetectionThresholds.defaults();
        ConfigurationSection threshSection = config.getConfigurationSection("detection.thresholds");
        if (threshSection != null) {
            t.oreToStoneRatioWarning = threshSection.getDouble("ore-to-stone-ratio.warning", t.oreToStoneRatioWarning);
            t.oreToStoneRatioCritical = threshSection.getDouble("ore-to-stone-ratio.critical", t.oreToStoneRatioCritical);
            t.diamondToStoneRatioWarning = threshSection.getDouble("diamond-to-stone-ratio.warning", t.diamondToStoneRatioWarning);
            t.diamondToStoneRatioCritical = threshSection.getDouble("diamond-to-stone-ratio.critical", t.diamondToStoneRatioCritical);
            t.orePerHourWarning = threshSection.getDouble("ore-per-hour.warning", t.orePerHourWarning);
            t.orePerHourCritical = threshSection.getDouble("ore-per-hour.critical", t.orePerHourCritical);
            t.diamondPerHourWarning = threshSection.getDouble("diamond-per-hour.warning", t.diamondPerHourWarning);
            t.diamondPerHourCritical = threshSection.getDouble("diamond-per-hour.critical", t.diamondPerHourCritical);
            t.shortWindowOreRatioWarning = threshSection.getDouble("short-window-ore-ratio.warning", t.shortWindowOreRatioWarning);
            t.shortWindowOreRatioCritical = threshSection.getDouble("short-window-ore-ratio.critical", t.shortWindowOreRatioCritical);
            t.longWindowOreRatioWarning = threshSection.getDouble("long-window-ore-ratio.warning", t.longWindowOreRatioWarning);
            t.longWindowOreRatioCritical = threshSection.getDouble("long-window-ore-ratio.critical", t.longWindowOreRatioCritical);
            t.valuableOreRatioWarning = threshSection.getDouble("valuable-ore-ratio.warning", t.valuableOreRatioWarning);
            t.valuableOreRatioCritical = threshSection.getDouble("valuable-ore-ratio.critical", t.valuableOreRatioCritical);
            t.straightToOreRatioWarning = threshSection.getDouble("straight-to-ore-ratio.warning", t.straightToOreRatioWarning);
            t.straightToOreRatioCritical = threshSection.getDouble("straight-to-ore-ratio.critical", t.straightToOreRatioCritical);
        }
        this.detectionThresholds = t;

        List<String> warnActs = config.getStringList("detection.actions.warning");
        this.warningActions = (warnActs != null && !warnActs.isEmpty()) ? warnActs : List.of("log", "notify");
        
        List<String> critActs = config.getStringList("detection.actions.critical");
        this.criticalActions = (critActs != null && !critActs.isEmpty()) ? critActs : List.of("log", "notify", "command:tempban {player} 1h Suspected X-ray use");

        this.notifyInGame = config.getBoolean("detection.notifications.in-game", true);
        this.notifyConsole = config.getBoolean("detection.notifications.console", true);
        this.webhookEnabled = config.getBoolean("detection.notifications.webhook.enabled", false);
        this.webhookUrl = config.getString("detection.notifications.webhook.url", "");
        this.webhookFormat = config.getString("detection.notifications.webhook.format", "discord-embed");

        this.forcePack = config.getBoolean("resource-pack.force-pack", false);
        this.packUrl = config.getString("resource-pack.pack-url", "");
        this.packHash = config.getString("resource-pack.pack-hash", "");
        this.kickOnDecline = config.getBoolean("resource-pack.kick-on-decline", true);
        this.kickMessage = ChatColor.translateAlternateColorCodes('&',
                config.getString("resource-pack.kick-message", "This server requires the official resource pack."));
        this.delayJoinUntilLoaded = config.getBoolean("resource-pack.delay-join-until-loaded", true);
        this.kickOnFailed = config.getBoolean("resource-pack.kick-on-failed", false);

        Map<String, WorldConfig> newWorldConfigs = new HashMap<>();

        ConfigurationSection worldsSection = config.getConfigurationSection("worlds");
        if (worldsSection != null) {
            for (String worldName : worldsSection.getKeys(false)) {
                ConfigurationSection worldSection = worldsSection.getConfigurationSection(worldName);
                if (worldSection == null) continue;
                WorldConfig merged = buildWorldConfig(worldSection, globalConfig);
                newWorldConfigs.put(worldName.toLowerCase(Locale.ROOT), merged);
            }
        }

        worldConfigs = newWorldConfigs;
        configHash = globalConfig.getConfigHash();

        logger.info("Configuration loaded. " + newWorldConfigs.size() + " per-world override(s).");
    }

    public void reload() {
        WorldConfig oldGlobal = this.globalConfig;
        Map<String, WorldConfig> oldWorlds = this.worldConfigs;
        int oldHash = this.configHash;

        if (plugin instanceof AntiXrayPlugin) {
            I18n i18n = ((AntiXrayPlugin) plugin).getI18n();
            if (i18n != null) {
                i18n.load();
            }
        }

        load();

        boolean modeChanged = oldGlobal != null && globalConfig.getEngineMode() != oldGlobal.getEngineMode();
        boolean blocksChanged = oldGlobal != null && !globalConfig.getHiddenBlocks().equals(oldGlobal.getHiddenBlocks());

        for (Map.Entry<String, WorldConfig> entry : worldConfigs.entrySet()) {
            WorldConfig old = oldWorlds.get(entry.getKey());
            if (old == null) {
                modeChanged = true;
                blocksChanged = true;
                break;
            }
            if (entry.getValue().getEngineMode() != old.getEngineMode()) modeChanged = true;
            if (!entry.getValue().getHiddenBlocks().equals(old.getHiddenBlocks())) blocksChanged = true;
        }

        if (modeChanged || blocksChanged) {
            configHash = globalConfig.getConfigHash();
            logger.info("Obfuscation mode or hidden-blocks changed — caches should be cleared.");
            for (ConfigChangeListener listener : configChangeListeners) {
                listener.onObfuscationConfigChanged();
            }
        }
    }

    public WorldConfig getWorldConfig(World world) {
        String key = world.getName().toLowerCase(Locale.ROOT);
        WorldConfig perWorld = worldConfigs.get(key);
        return perWorld != null ? perWorld : globalConfig;
    }

    public WorldConfig getGlobalConfig() {
        return globalConfig;
    }

    public int getConfigHash() {
        return configHash;
    }

    private WorldConfig buildGlobalConfig(FileConfiguration config) {
        Set<Integer> hiddenIds = resolveHiddenBlockIds(config.getStringList("hidden-blocks"));

        int stoneId = nmsAdapter.getBlockStateId(Material.STONE);
        int deepslateId = nmsAdapter.getBlockStateId(Material.DEEPSLATE);
        int netherrackId = nmsAdapter.getBlockStateId(Material.NETHERRACK);
        int endStoneId = nmsAdapter.getBlockStateId(Material.END_STONE);

        String overworldReplacement = config.getString("replacement-blocks.overworld.default", "stone");
        String overworldDeepReplacement = config.getString("replacement-blocks.overworld.below-y", "deepslate");
        String netherReplacement = config.getString("replacement-blocks.nether", "netherrack");
        String endReplacement = config.getString("replacement-blocks.end", "end_stone");

        return WorldConfig.builder()
                .enabled(config.getBoolean("enabled", true))
                .engineMode(parseEngineMode(config.get("engine-mode")))
                .hiddenBlocks(hiddenIds)
                .replacementOverworld(resolveBlockId(overworldReplacement, stoneId))
                .replacementOverworldDeep(resolveBlockId(overworldDeepReplacement, deepslateId))
                .deepslateBelowY(config.getInt("replacement-blocks.overworld.deepslate-below-y",
                        config.getInt("deepslate-below-y", 0)))
                .replacementNether(resolveBlockId(netherReplacement, netherrackId))
                .replacementEnd(resolveBlockId(endReplacement, endStoneId))
                .maxBlockHeight(config.getInt("max-block-height", 64))
                .fakeOreChance(config.getDouble("fake-ore-chance", 0.07))
                .lavaObscures(config.getBoolean("lava-obscures", true))
                .leavesAreTransparent(config.getBoolean("leaves-are-transparent", true))
		.bypassPermission(config.getString("bypass-permission", "antixray.bypass"))
                .maxRevealedPerPlayer(config.getInt("proximity.max-revealed-per-player", 10000))
                .movementThreshold(config.getDouble("proximity.movement-threshold", 0.5))
		.updateRadius(config.getInt("proximity.update-radius", 4))
                .maxDeobfuscationUpdatesPerTick(config.getInt("proximity.max-deobfuscation-updates-per-tick", 64))
                .elytraVelocityThreshold(config.getDouble("proximity.elytra-velocity-threshold", 1.5))
                .build();
    }

    private WorldConfig buildWorldConfig(ConfigurationSection worldSection, WorldConfig defaults) {
        Set<Integer> hiddenIds = worldSection.contains("hidden-blocks")
                ? resolveHiddenBlockIds(worldSection.getStringList("hidden-blocks"))
                : defaults.getHiddenBlocks();

        int replacementOverworld = worldSection.contains("replacement-blocks.overworld.default")
                ? resolveBlockId(worldSection.getString("replacement-blocks.overworld.default"),
                        defaults.getReplacementOverworld())
                : defaults.getReplacementOverworld();

        int replacementOverworldDeep = worldSection.contains("replacement-blocks.overworld.below-y")
                ? resolveBlockId(worldSection.getString("replacement-blocks.overworld.below-y"),
                        defaults.getReplacementOverworldDeep())
                : defaults.getReplacementOverworldDeep();

        int deepslateBelowY = worldSection.contains("replacement-blocks.overworld.deepslate-below-y")
                ? worldSection.getInt("replacement-blocks.overworld.deepslate-below-y")
                : worldSection.getInt("deepslate-below-y", defaults.getDeepslateBelowY());

        int replacementNether = worldSection.contains("replacement-blocks.nether")
                ? resolveBlockId(worldSection.getString("replacement-blocks.nether"),
                        defaults.getReplacementNether())
                : defaults.getReplacementNether();

        int replacementEnd = worldSection.contains("replacement-blocks.end")
                ? resolveBlockId(worldSection.getString("replacement-blocks.end"),
                        defaults.getReplacementEnd())
                : defaults.getReplacementEnd();

        return WorldConfig.builder()
                .enabled(worldSection.getBoolean("enabled", defaults.isEnabled()))
                .engineMode(worldSection.contains("engine-mode")
                        ? parseEngineMode(worldSection.get("engine-mode"))
                        : defaults.getEngineMode())
                .hiddenBlocks(hiddenIds)
                .replacementOverworld(replacementOverworld)
                .replacementOverworldDeep(replacementOverworldDeep)
                .deepslateBelowY(deepslateBelowY)
                .replacementNether(replacementNether)
                .replacementEnd(replacementEnd)
                .maxBlockHeight(worldSection.getInt("max-block-height", defaults.getMaxBlockHeight()))
                .fakeOreChance(worldSection.getDouble("fake-ore-chance", defaults.getFakeOreChance()))
                .lavaObscures(worldSection.getBoolean("lava-obscures", defaults.isLavaObscures()))
                .leavesAreTransparent(worldSection.getBoolean("leaves-are-transparent",
                        defaults.isLeavesAreTransparent()))
        .bypassPermission(worldSection.getString("bypass-permission", defaults.getBypassPermission()))
        .maxRevealedPerPlayer(worldSection.getInt("max-revealed-per-player",
            worldSection.contains("proximity.max-revealed-per-player")
                ? worldSection.getInt("proximity.max-revealed-per-player", defaults.getMaxRevealedPerPlayer())
                : defaults.getMaxRevealedPerPlayer()))
        .movementThreshold(worldSection.contains("proximity.movement-threshold")
            ? worldSection.getDouble("proximity.movement-threshold", defaults.getMovementThreshold())
            : worldSection.getDouble("movement-threshold", defaults.getMovementThreshold()))
		.updateRadius(worldSection.contains("proximity.update-radius")
			? worldSection.getInt("proximity.update-radius", defaults.getUpdateRadius())
			: worldSection.getInt("update-radius", defaults.getUpdateRadius()))
                .maxDeobfuscationUpdatesPerTick(worldSection.contains("proximity.max-deobfuscation-updates-per-tick")
                        ? worldSection.getInt("proximity.max-deobfuscation-updates-per-tick", defaults.getMaxDeobfuscationUpdatesPerTick())
                        : worldSection.getInt("max-deobfuscation-updates-per-tick", defaults.getMaxDeobfuscationUpdatesPerTick()))
                .elytraVelocityThreshold(worldSection.contains("proximity.elytra-velocity-threshold")
                        ? worldSection.getDouble("proximity.elytra-velocity-threshold", defaults.getElytraVelocityThreshold())
                        : worldSection.getDouble("elytra-velocity-threshold", defaults.getElytraVelocityThreshold()))
                .build();
    }

    private ObfuscationMode parseEngineMode(Object value) {
        if (value == null) return ObfuscationMode.MODE_3;
        if (value instanceof Number) {
            return switch (((Number) value).intValue()) {
                case 1 -> ObfuscationMode.MODE_1;
                case 2 -> ObfuscationMode.MODE_2;
                default -> ObfuscationMode.MODE_3;
            };
        }
        String str = value.toString().toUpperCase(Locale.ROOT);
        try {
            if (!str.startsWith("MODE_")) str = "MODE_" + str;
            return ObfuscationMode.valueOf(str);
        } catch (IllegalArgumentException e) {
            logger.warning("Invalid engine-mode: " + value + ", defaulting to MODE_3");
            return ObfuscationMode.MODE_3;
        }
    }

    private Set<Integer> resolveHiddenBlockIds(List<String> names) {
        Set<Integer> ids = new HashSet<>();
        for (String name : names) {
            Material mat = Material.matchMaterial(name);
            if (mat == null) {
                mat = Material.matchMaterial(name.toUpperCase(Locale.ROOT).replace(" ", "_"));
            }
            if (mat == null || !mat.isBlock()) {
                logger.warning("Unknown or non-block material in hidden-blocks: " + name);
                continue;
            }
            int id = nmsAdapter.getBlockStateId(mat);
            if (id >= 0) ids.add(id);
        }
        return ids;
    }

    private int resolveBlockId(String materialName, int fallback) {
        if (materialName == null || materialName.isBlank()) return fallback;
        Material mat = Material.matchMaterial(materialName);
        if (mat == null) {
            mat = Material.matchMaterial(materialName.toUpperCase(Locale.ROOT).replace(" ", "_"));
        }
        if (mat == null || !mat.isBlock()) {
            logger.warning("Unknown replacement block: " + materialName + ", using fallback (id=" + fallback + ")");
            return fallback;
        }
        int id = nmsAdapter.getBlockStateId(mat);
        return id >= 0 ? id : fallback;
    }

    public boolean isDetectionEnabled() { return detectionEnabled; }
    public int getDetectionMinimumSampleSize() { return detectionMinimumSampleSize; }
    public int getDetectionGracePeriodMinutes() { return detectionGracePeriodMinutes; }
    public DetectionThresholds getDetectionThresholds() { return detectionThresholds; }
    public List<String> getWarningActions() { return warningActions; }
    public List<String> getCriticalActions() { return criticalActions; }
    public boolean isNotifyInGame() { return notifyInGame; }
    public boolean isNotifyConsole() { return notifyConsole; }
    public boolean isWebhookEnabled() { return webhookEnabled; }
    public String getWebhookUrl() { return webhookUrl; }
    public String getWebhookFormat() { return webhookFormat; }

    public boolean isForcePack() { return forcePack; }
    public String getPackUrl() { return packUrl; }
    public String getPackHash() { return packHash; }
    public boolean isKickOnDecline() { return kickOnDecline; }
    public String getKickMessage() { return kickMessage; }
    public boolean isDelayJoinUntilLoaded() { return delayJoinUntilLoaded; }
    public boolean isKickOnFailed() { return kickOnFailed; }

    public String getMessage(String key, String def) {
        if (plugin instanceof AntiXrayPlugin) {
            I18n i18n = ((AntiXrayPlugin) plugin).getI18n();
            if (i18n != null) {
                String msg = i18n.getMessage(key, (Player) null);
                if (msg != null && !msg.equals(key)) {
                    return msg;
                }
            }
        }
        return ChatColor.translateAlternateColorCodes('&', def);
    }
}
