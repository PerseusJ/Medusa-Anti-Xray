package com.antixray;

import com.antixray.commands.AntiXrayCommand;
import com.antixray.commands.ReloadCommand;
import com.antixray.async.AsyncProcessor;
import com.antixray.async.BackpressureHandler;
import com.antixray.async.ObfuscationTask;
import com.antixray.async.ThreadPoolManager;
import com.antixray.async.TickBudgetTracker;
import com.antixray.cache.CacheEntry;
import com.antixray.cache.CacheKey;
import com.antixray.cache.ObfuscationCache;
import com.antixray.config.ConfigurationManager;
import com.antixray.deobfuscation.DeobfuscationManager;
import com.antixray.deobfuscation.PlayerData;
import com.antixray.engine.AirExposureChecker;
import com.antixray.engine.ObfuscationEngine;
import com.antixray.engine.ObfuscationMode;
import com.antixray.listener.AsyncChunkLoadListener;
import com.antixray.listener.BlockEventListener;
import com.antixray.listener.ExplosionEventListener;
import com.antixray.listener.PlayerEventListener;
import com.antixray.listener.WorldEventListener;
import com.antixray.nms.NmsAdapter;
import com.antixray.commands.AntiXrayCommand;
import com.antixray.commands.ReloadCommand;
import com.antixray.async.AsyncProcessor;
import com.antixray.async.BackpressureHandler;
import com.antixray.async.ObfuscationTask;
import com.antixray.async.ThreadPoolManager;
import com.antixray.async.TickBudgetTracker;
import com.antixray.cache.CacheEntry;
import com.antixray.cache.CacheKey;
import com.antixray.cache.ObfuscationCache;
import com.antixray.config.ConfigurationManager;
import com.antixray.deobfuscation.DeobfuscationManager;
import com.antixray.deobfuscation.PlayerData;
import com.antixray.engine.AirExposureChecker;
import com.antixray.engine.ObfuscationEngine;
import com.antixray.engine.ObfuscationMode;
import com.antixray.listener.AsyncChunkLoadListener;
import com.antixray.listener.BlockEventListener;
import com.antixray.listener.ExplosionEventListener;
import com.antixray.listener.PlayerEventListener;
import com.antixray.listener.WorldEventListener;
import com.antixray.nms.NmsAdapter;
import com.antixray.nms.NmsAdapterFactory;
import com.antixray.packet.InterceptionMode;
import com.antixray.packet.NmsInterceptor;
import com.antixray.packet.PacketInterceptor;
import com.antixray.packet.ProtocolLibInterceptor;
import com.antixray.util.FoliaSchedulerAdapter;
import com.antixray.util.MaterialSet;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import com.antixray.detection.DetectionEngine;
import com.antixray.i18n.I18n;
import com.antixray.util.VersionUtil;
import com.antixray.detection.DetectionResult;
import com.antixray.api.AlertLevel;
import com.antixray.detection.AlertManager;
import com.antixray.detection.ActionExecutor;
import com.antixray.detection.StatisticsStorage;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class AntiXrayPlugin extends JavaPlugin {

    private static AntiXrayPlugin instance;

    private NmsAdapter nmsAdapter;
    private ConfigurationManager configurationManager;
    private ObfuscationEngine obfuscationEngine;
    private PacketInterceptor packetInterceptor;
    private DeobfuscationManager deobfuscationManager;
    private ObfuscationCache obfuscationCache;
    private AsyncProcessor asyncProcessor;
    private AsyncChunkLoadListener asyncChunkLoadListener;
    private InterceptionMode interceptionMode = InterceptionMode.NONE;
    private AntiXrayCommand antiXrayCommand;
    private I18n i18n;

    public I18n getI18n() {
        return i18n;
    }

	private final Map<UUID, PlayerData> playerData = new ConcurrentHashMap<>();
	private final Map<String, Map<String, Object>> worldState = new ConcurrentHashMap<>();
    private volatile DetectionEngine detectionEngine;
    private volatile AlertManager alertManager;
    private volatile ActionExecutor actionExecutor;
    private volatile StatisticsStorage statisticsStorage;
    private FoliaSchedulerAdapter schedulerAdapter;
    private final AntiXrayAPIImpl api = new AntiXrayAPIImpl(this);

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        schedulerAdapter = new FoliaSchedulerAdapter(this);

        i18n = new I18n(this);
        i18n.load();

        if (schedulerAdapter.isFolia()) {
            getLogger().warning(i18n.getConsoleMessage("warning-folia-experimental"));
        }

        nmsAdapter = NmsAdapterFactory.create();
        if (nmsAdapter == null) {
            getLogger().severe("Failed to create NMS adapter. Plugin cannot function.");
            getLogger().severe(i18n.getConsoleMessage("warning-no-interception"));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        configurationManager = new ConfigurationManager(this, nmsAdapter);
        configurationManager.load();

        // Check for conflicts and seed leaks
        checkConflicts();
        try {
            org.bukkit.configuration.file.YamlConfiguration spigotConfig = Bukkit.spigot().getConfig();
            if (spigotConfig != null) {
                int seedFeature = spigotConfig.getInt("world-settings.default.seed-feature", 14357617);
                int seedStructure = spigotConfig.getInt("world-settings.default.seed-structure", 14357617);
                if (seedFeature == 14357617 || seedStructure == 14357617) {
                    getLogger().warning(i18n.getConsoleMessage("warning-seed-leak"));
                }
            }
        } catch (Throwable ignored) {}

        detectionEngine = new DetectionEngine(
            configurationManager.getDetectionThresholds(),
            60_000L,
            configurationManager.getDetectionMinimumSampleSize(),
            configurationManager.getDetectionGracePeriodMinutes()
        );
        alertManager = new AlertManager(this);
        actionExecutor = new ActionExecutor(this);

        statisticsStorage = createStatisticsStorage();
        statisticsStorage.startPeriodicSaveTask();

        registerListeners();
        registerCommands();

        obfuscationEngine = createEngine();

        obfuscationCache = createCache();

        configurationManager.addConfigChangeListener(() -> {
            if (obfuscationCache != null) {
                obfuscationCache.invalidateAll();
            }
            detectionEngine = new DetectionEngine(
                configurationManager.getDetectionThresholds(),
                60_000L,
                configurationManager.getDetectionMinimumSampleSize(),
                configurationManager.getDetectionGracePeriodMinutes()
            );
        });

        asyncProcessor = createAsyncProcessor(obfuscationCache, obfuscationEngine);

        asyncChunkLoadListener = new AsyncChunkLoadListener(this);
        if (getConfig().getBoolean("async.pre-obfuscation-enabled", true)) {
            asyncChunkLoadListener.register();
        }

        deobfuscationManager = new DeobfuscationManager(this, nmsAdapter, obfuscationEngine.getMaterialSet());
	deobfuscationManager.startFlushTask();

        if (!initializePacketInterceptor()) {
            getLogger().severe(i18n.getConsoleMessage("warning-no-interception"));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getLogger().info(i18n.getConsoleMessage("plugin-enabled") + " Interception mode: " + interceptionMode);

        startTickHook();
    }

    public void checkConflicts() {
        if (getServer().getPluginManager().getPlugin("Orebfuscator") != null) {
            getLogger().warning(i18n.getConsoleMessage("warning-orebfuscator-conflict"));
        }

        boolean paperAntiXrayEnabled = false;
        java.io.File paperWorldDefaultsFile = new java.io.File("config/paper-world-defaults.yml");
        if (paperWorldDefaultsFile.exists()) {
            try {
                org.bukkit.configuration.file.YamlConfiguration paperConfig =
                        org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(paperWorldDefaultsFile);
                if (paperConfig.getBoolean("anticheat.anti-xray.enabled", false)) {
                    paperAntiXrayEnabled = true;
                }
            } catch (Throwable ignored) {}
        }
        if (!paperAntiXrayEnabled) {
            java.io.File legacyPaperFile = new java.io.File("paper.yml");
            if (legacyPaperFile.exists()) {
                try {
                    org.bukkit.configuration.file.YamlConfiguration legacyConfig =
                            org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(legacyPaperFile);
                    if (legacyConfig.getBoolean("world-settings.default.anti-xray.enabled", false)) {
                        paperAntiXrayEnabled = true;
                    }
                } catch (Throwable ignored) {}
            }
        }
        if (!paperAntiXrayEnabled) {
            try {
                if (Bukkit.spigot().getConfig().getBoolean("settings.anti-xray.enabled", false)) {
                    paperAntiXrayEnabled = true;
                }
            } catch (Throwable ignored) {}
        }
        if (paperAntiXrayEnabled) {
            getLogger().warning(i18n.getConsoleMessage("warning-paper-antixray-conflict"));
        }

        try {
            for (org.bukkit.plugin.Plugin p : getServer().getPluginManager().getPlugins()) {
                String name = p.getName();
                if (name.equalsIgnoreCase(this.getName())) {
                    continue;
                }
                String lowerName = name.toLowerCase(java.util.Locale.ROOT);
                if (lowerName.contains("xray") || lowerName.contains("orebfuscator") || lowerName.contains("anti-xray")) {
                    getLogger().info(i18n.getConsoleMessage("warning-other-antixray-plugin", "{plugin}", name));
                }
            }
        } catch (Throwable ignored) {}
    }

    private ObfuscationEngine createEngine() {
        MaterialSet materialSet = new MaterialSet(
            nmsAdapter.getBlockStateId(org.bukkit.Material.STONE),
            nmsAdapter.getBlockStateId(org.bukkit.Material.DEEPSLATE),
            nmsAdapter.getBlockStateId(org.bukkit.Material.NETHERRACK),
            nmsAdapter.getBlockStateId(org.bukkit.Material.END_STONE)
        );

        materialSet.populateTransparentBlocks(nmsAdapter);
        materialSet.populateHiddenBlockTypes(nmsAdapter,
            getConfig().getStringList("hidden-blocks"));

        AirExposureChecker exposureChecker = new AirExposureChecker(
            nmsAdapter, materialSet, materialSet.isLavaObscures());

        ObfuscationEngine engine = new ObfuscationEngine(nmsAdapter, materialSet, exposureChecker);

        String modeName = getConfig().getString("engine-mode", "MODE_3");
        try {
            engine.setEngineMode(ObfuscationMode.valueOf(modeName.toUpperCase()));
        } catch (IllegalArgumentException e) {
            getLogger().warning("Invalid engine-mode: " + modeName + ", defaulting to MODE_3");
            engine.setEngineMode(ObfuscationMode.MODE_3);
        }

        engine.setDeepslateBelowY(getConfig().getInt("deepslate-below-y", 0));
        engine.setMaxBlockHeight(getConfig().getInt("max-block-height", 64));
        engine.setServerSalt(getConfig().getLong("server-salt", 0L));
        engine.setFakeOreChance(getConfig().getDouble("fake-ore-chance", 0.07));

        return engine;
    }

    private ObfuscationCache createCache() {
        long maxL1Size = getConfig().getLong("cache.l1.max-size", 5000);
        long l1ExpirySeconds = getConfig().getLong("cache.l1.expiry-seconds", 300);
        boolean l2Enabled = getConfig().getBoolean("cache.l2.enabled", true);

        if (l2Enabled) {
            java.io.File cacheDir = new java.io.File(getDataFolder(), "cache");
            long diskBudgetMB = getConfig().getLong("cache.l2.max-disk-mb", 500);
            long l2ExpirySeconds = getConfig().getLong("cache.l2.expiry-seconds", 86400);
            return new ObfuscationCache(maxL1Size, l1ExpirySeconds, cacheDir, diskBudgetMB, l2ExpirySeconds);
        }
        return new ObfuscationCache(maxL1Size, l1ExpirySeconds);
    }

    private StatisticsStorage createStatisticsStorage() {
        String storageType = getConfig().getString("detection.storage.type", "sqlite");
        java.io.File dataFolder = getDataFolder();

        if ("mysql".equalsIgnoreCase(storageType)) {
            String host = getConfig().getString("detection.storage.mysql.host", "localhost");
            int port = getConfig().getInt("detection.storage.mysql.port", 3306);
            String database = getConfig().getString("detection.storage.mysql.database", "antixray");
            String username = getConfig().getString("detection.storage.mysql.username", "");
            String password = getConfig().getString("detection.storage.mysql.password", "");
            return new StatisticsStorage(this, "mysql", dataFolder, host, port, database, username, password);
        }
        return new StatisticsStorage(this, "sqlite", dataFolder, null, 0, null, null, null);
    }

    private AsyncProcessor createAsyncProcessor(ObfuscationCache cache, ObfuscationEngine engine) {
        int poolSize = getConfig().getInt("async.pool-size", ThreadPoolManager.computeDefaultPoolSize());
        if (poolSize <= 0) {
            poolSize = ThreadPoolManager.computeDefaultPoolSize();
        }
        int maxQueueSize = getConfig().getInt("async.max-queue-size", 10000);
        long tickBudgetMs = getConfig().getLong("async.per-tick-budget-ms", 5);
        long chunkTimeoutMs = getConfig().getLong("async.chunk-timeout-ms", 50);

        ThreadPoolManager threadPoolManager = new ThreadPoolManager(poolSize, maxQueueSize);
        TickBudgetTracker tickBudgetTracker = new TickBudgetTracker(tickBudgetMs);
        BackpressureHandler backpressureHandler = new BackpressureHandler(maxQueueSize, threadPoolManager);

        AsyncProcessor processor = new AsyncProcessor(threadPoolManager, tickBudgetTracker, backpressureHandler, cache, chunkTimeoutMs);

        processor.setObfuscationFunction(key -> {
            String worldName = key.getWorldName();
            int chunkX = key.getChunkX();
            int chunkZ = key.getChunkZ();
            org.bukkit.World world = Bukkit.getWorld(worldName);
            if (world == null) return null;

            Object packet = nmsAdapter.createChunkDataPacket(world, chunkX, chunkZ);
            if (packet == null) return null;

            return engine.obfuscateAndSerialize(packet, world, chunkX, chunkZ);
        });

        return processor;
    }

    private volatile int tickHookTaskId = -1;

    private void startTickHook() {
        if (tickHookTaskId != -1) return;
        if (asyncProcessor == null) return;
        tickHookTaskId = schedulerAdapter.runTaskTimer(() -> {
            asyncProcessor.onTickStart();
            asyncProcessor.onTickEnd();
        }, 1L, 1L);
    }

    private void stopTickHook() {
        if (tickHookTaskId != -1) {
            schedulerAdapter.cancelTask(tickHookTaskId);
            tickHookTaskId = -1;
        }
    }

    private boolean initializePacketInterceptor() {
        NmsInterceptor nmsInterceptor = new NmsInterceptor(this, obfuscationEngine, nmsAdapter);
        if (nmsInterceptor.isAvailable()) {
            nmsInterceptor.register();
            if (nmsInterceptor.isAvailable()) {
                packetInterceptor = nmsInterceptor;
                interceptionMode = InterceptionMode.NMS;
                getLogger().info("Using NMS packet interception (Paper ChunkPacketBlockController)");
                return true;
            }
            getLogger().warning("NMS interceptor registration failed, falling back to ProtocolLib");
        }

        ProtocolLibInterceptor protLibInterceptor = new ProtocolLibInterceptor(
            this, obfuscationEngine, nmsAdapter);
        if (protLibInterceptor.isAvailable()) {
            protLibInterceptor.register();
            if (protLibInterceptor.isAvailable()) {
                packetInterceptor = protLibInterceptor;
                interceptionMode = InterceptionMode.PROTOCOL_LIB;
                getLogger().info(i18n.getConsoleMessage("warning-protocolib-fallback"));
                return true;
            }
            getLogger().warning("ProtocolLib interceptor registration failed");
        }

        interceptionMode = InterceptionMode.NONE;
        return false;
    }

    @Override
    public void onDisable() {
        stopTickHook();

        if (schedulerAdapter != null) {
            schedulerAdapter.cancelAllTasks();
        }

        if (statisticsStorage != null) {
            statisticsStorage.shutdown();
            statisticsStorage = null;
        }

        if (alertManager != null) {
            alertManager.shutdown();
            alertManager = null;
        }

        if (asyncChunkLoadListener != null) {
            asyncChunkLoadListener.unregister();
            asyncChunkLoadListener = null;
        }

        if (asyncProcessor != null) {
            asyncProcessor.shutdown();
            try {
                asyncProcessor.awaitTermination(5000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            asyncProcessor = null;
        }

        if (obfuscationCache != null) {
            obfuscationCache.shutdown();
            obfuscationCache = null;
        }

        if (deobfuscationManager != null) {
            deobfuscationManager.stopFlushTask();
        }
        if (packetInterceptor != null) {
            packetInterceptor.unregister();
            packetInterceptor = null;
        }
        interceptionMode = InterceptionMode.NONE;
        playerData.clear();
        worldState.clear();
        if (i18n != null) {
            getLogger().info(i18n.getConsoleMessage("plugin-disabled"));
        } else {
            getLogger().info("AntiXray disabled.");
        }
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new PlayerEventListener(this), this);
        getServer().getPluginManager().registerEvents(new WorldEventListener(this), this);
        getServer().getPluginManager().registerEvents(new BlockEventListener(this), this);
        getServer().getPluginManager().registerEvents(new ExplosionEventListener(this), this);

        for (org.bukkit.World world : getServer().getWorlds()) {
            initializeWorldState(world.getName());
        }

        for (org.bukkit.entity.Player player : getServer().getOnlinePlayers()) {
            initializePlayerData(player.getUniqueId());
        }
    }

    private void registerCommands() {
        antiXrayCommand = new AntiXrayCommand(this);
        antiXrayCommand.registerSubCommand(new ReloadCommand(this));

        PluginCommand cmd = getCommand("antixray");
        if (cmd != null) {
            cmd.setExecutor(antiXrayCommand);
            cmd.setTabCompleter(antiXrayCommand);
        } else {
            getLogger().warning("Could not register /antixray command — missing from plugin.yml?");
        }
    }

	public void initializePlayerData(UUID playerId) {
		int maxRevealed = configurationManager != null
			? configurationManager.getGlobalConfig().getMaxRevealedPerPlayer()
			: 10000;
		playerData.put(playerId, new PlayerData(maxRevealed));
	}

    public void removePlayerData(UUID playerId) {
        PlayerData data = playerData.remove(playerId);
        if (data != null) {
            int taskId = data.getPendingJoinTaskId();
            if (taskId != -1) {
                schedulerAdapter.cancelTask(taskId);
            }
            int rpTaskId = data.getResourcePackTimeoutTaskId();
            if (rpTaskId != -1) {
                schedulerAdapter.cancelTask(rpTaskId);
            }
            data.fullReset();
        }
    }

	public void resetPlayerData(UUID playerId) {
		PlayerData data = playerData.get(playerId);
		if (data != null) {
			data.clear();
		}
	}

	public PlayerData getPlayerData(UUID playerId) {
		return playerData.get(playerId);
	}

	public Map<UUID, PlayerData> getAllPlayerData() {
		return Collections.unmodifiableMap(playerData);
	}

    public void initializeWorldState(String worldName) {
        worldState.put(worldName.toLowerCase(), new ConcurrentHashMap<>());
    }

    public void removeWorldState(String worldName) {
        worldState.remove(worldName.toLowerCase());
    }

    public Map<String, Object> getWorldState(String worldName) {
        return worldState.get(worldName.toLowerCase());
    }

    public Map<String, Map<String, Object>> getAllWorldState() {
        return Collections.unmodifiableMap(worldState);
    }

    public static AntiXrayPlugin getInstance() {
        return instance;
    }

    public NmsAdapter getNmsAdapter() {
        return nmsAdapter;
    }

    public ConfigurationManager getConfigurationManager() {
        return configurationManager;
    }

    public ObfuscationEngine getObfuscationEngine() {
        return obfuscationEngine;
    }

    public PacketInterceptor getPacketInterceptor() {
        return packetInterceptor;
    }

    public InterceptionMode getInterceptionMode() {
        return interceptionMode;
    }

    public DeobfuscationManager getDeobfuscationManager() {
        return deobfuscationManager;
    }

    public AsyncProcessor getAsyncProcessor() {
        return asyncProcessor;
    }

    public ObfuscationCache getObfuscationCache() {
        return obfuscationCache;
    }

    public AsyncChunkLoadListener getAsyncChunkLoadListener() {
        return asyncChunkLoadListener;
    }

    public AntiXrayCommand getAntiXrayCommand() {
        return antiXrayCommand;
    }

    public int scheduleSyncDelayedTask(Runnable task, long delayTicks) {
        return schedulerAdapter.runTaskLater(task, delayTicks);
    }

    public void cancelTask(int taskId) {
        schedulerAdapter.cancelTask(taskId);
    }

    public com.antixray.api.AntiXrayAPI getAPI() {
        return api;
    }

    public DetectionEngine getDetectionEngine() {
        return detectionEngine;
    }

    public AlertManager getAlertManager() {
        return alertManager;
    }

    public ActionExecutor getActionExecutor() {
        return actionExecutor;
    }

    public StatisticsStorage getStatisticsStorage() {
        return statisticsStorage;
    }

    public FoliaSchedulerAdapter getSchedulerAdapter() {
        return schedulerAdapter;
    }

    public void triggerAlert(Player player, DetectionResult result) {
        if (configurationManager == null || !configurationManager.isDetectionEnabled()) {
            return;
        }

        java.util.Map<String, Double> metricsMap = new java.util.HashMap<>();
        PlayerData data = getPlayerData(player.getUniqueId());
        if (data != null && data.getStatistics() != null) {
            com.antixray.detection.PlayerStatistics stats = data.getStatistics();
            for (String metric : result.getTriggeredMetrics()) {
                double val = 0.0;
                switch (metric) {
                    case "oreToStoneRatio": val = stats.getOreToStoneRatio(); break;
                    case "diamondToStoneRatio": val = stats.getDiamondToStoneRatio(); break;
                    case "orePerHour": val = stats.getOrePerHour(); break;
                    case "diamondPerHour": val = stats.getDiamondPerHour(); break;
                    case "shortWindowOreRatio": val = stats.getShortWindowOreRatio(); break;
                    case "longWindowOreRatio": val = stats.getLongWindowOreRatio(); break;
                    case "valuableOreRatio": val = stats.getValuableOreRatio(); break;
                    case "straightToOreRatio": val = stats.getStraightToOreRatio(); break;
                }
                metricsMap.put(metric, val);
            }
        }

        com.antixray.api.PlayerXraySuspicionEvent event = new com.antixray.api.PlayerXraySuspicionEvent(
            player, result.getLevel(), metricsMap
        );
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }

        if (alertManager != null) {
            alertManager.dispatchAlert(player, result);
        }

        if (actionExecutor != null && result.isDetected()) {
            actionExecutor.executeActions(player, result.getLevel());
        }

        if (statisticsStorage != null && result.isDetected()) {
            String metricsStr = String.join(", ", result.getTriggeredMetrics());
            statisticsStorage.recordAlert(player.getUniqueId(), player.getName(),
                    result.getLevel(), metricsStr);
        }
    }
}
