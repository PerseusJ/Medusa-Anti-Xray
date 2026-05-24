package com.antixray.listener;

import com.antixray.AntiXrayPlugin;
import com.antixray.async.AsyncProcessor;
import com.antixray.async.ChunkPreObfuscator;
import com.antixray.cache.ObfuscationCache;
import com.antixray.config.ConfigurationManager;
import com.antixray.config.WorldConfig;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

public class AsyncChunkLoadListener {

    private final AntiXrayPlugin plugin;
    private final ChunkPreObfuscator preObfuscator;
    private final ConfigurationManager configManager;

    private volatile boolean registered = false;
    private volatile boolean paperDetected = false;

    private final AtomicLong tasksSkippedNotPaper = new AtomicLong(0);

    private Listener fallbackListener;

    public AsyncChunkLoadListener(AntiXrayPlugin plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigurationManager();
        this.preObfuscator = new ChunkPreObfuscator(
                plugin.getAsyncProcessor(),
                plugin.getObfuscationCache(),
                configManager.getConfigHash()
        );
    }

    public AsyncChunkLoadListener(AntiXrayPlugin plugin, ChunkPreObfuscator preObfuscator,
                                  ConfigurationManager configManager) {
        this.plugin = plugin;
        this.preObfuscator = preObfuscator;
        this.configManager = configManager;
    }

    public boolean register() {
        if (registered) return true;

        paperDetected = checkPaperAvailable();

        if (!paperDetected) {
            plugin.getLogger().info("Paper async chunk load API not available — pre-obfuscation disabled");
            tasksSkippedNotPaper.incrementAndGet();
            return false;
        }

        try {
            boolean eventRegistered = tryRegisterPaperAsyncChunkEvent();
            if (!eventRegistered) {
                plugin.getLogger().info("Paper detected but no async chunk load event class found. "
                        + "Using world chunk-load fallback for pre-obfuscation.");
                registerFallbackChunkLoadListener();
            }
            registered = true;
            plugin.getLogger().info("Paper async chunk pre-obfuscation registered (MEDIUM priority)");
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "Failed to register Paper async chunk load callback — pre-obfuscation disabled", e);
            return false;
        }
    }

    public void unregister() {
        if (!registered) return;
        registered = false;
        fallbackListener = null;
        plugin.getLogger().info("Paper async chunk pre-obfuscation unregistered");
    }

    private boolean checkPaperAvailable() {
        String[] paperClassNames = {
                "io.papermc.paper.event.chunk.AsyncChunkLoadEvent",
                "com.destroystokyo.paper.event.server.AsyncChunkLoadEvent",
                "io.papermc.paper.chunk.system.event.ChunkEvents",
                "io.papermc.paper.configuration.GlobalConfiguration",
                "com.destroystokyo.paper.PaperConfig"
        };
        for (String className : paperClassNames) {
            try {
                Class.forName(className);
                return true;
            } catch (ClassNotFoundException ignored) {
            }
        }
        return false;
    }

    private boolean tryRegisterPaperAsyncChunkEvent() {
        Class<?> eventClass = tryLoadAsyncChunkLoadEventClass();
        if (eventClass == null) return false;

        try {
            registerBukkitListenerForEvent(eventClass);
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINE,
                    "Bukkit event registration for " + eventClass.getName() + " failed", e);
            return false;
        }
    }

    private Class<?> tryLoadAsyncChunkLoadEventClass() {
        String[] classNames = {
                "io.papermc.paper.event.chunk.AsyncChunkLoadEvent",
                "com.destroystokyo.paper.event.server.AsyncChunkLoadEvent"
        };
        for (String className : classNames) {
            try {
                return Class.forName(className);
            } catch (ClassNotFoundException ignored) {
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private void registerBukkitListenerForEvent(Class<?> eventClass) {
        Listener listener = new Listener() {};

        org.bukkit.plugin.EventExecutor executor = (l, event) -> {
            if (!registered) return;
            try {
                handleAsyncChunkLoadEvent(event);
            } catch (Exception e) {
                plugin.getLogger().log(Level.FINE, "Error handling async chunk load event", e);
            }
        };

        Bukkit.getPluginManager().registerEvent(
                (Class<? extends org.bukkit.event.Event>) eventClass,
                listener,
                EventPriority.MONITOR,
                executor,
                plugin
        );
    }

    private void handleAsyncChunkLoadEvent(Object event) {
        World world = null;
        int chunkX = 0;
        int chunkZ = 0;

        try {
            java.lang.reflect.Method getWorld = event.getClass().getMethod("getWorld");
            world = (World) getWorld.invoke(event);
        } catch (Exception e) {
            try {
                java.lang.reflect.Method getWorldName = event.getClass().getMethod("getWorldName");
                String worldName = (String) getWorldName.invoke(event);
                world = Bukkit.getWorld(worldName);
            } catch (Exception ignored) {
            }
        }

        try {
            java.lang.reflect.Method getChunk = event.getClass().getMethod("getChunk");
            Object chunk = getChunk.invoke(event);
            if (chunk != null) {
                java.lang.reflect.Method getX = chunk.getClass().getMethod("getX");
                java.lang.reflect.Method getZ = chunk.getClass().getMethod("getZ");
                chunkX = (int) getX.invoke(chunk);
                chunkZ = (int) getZ.invoke(chunk);
            }
        } catch (Exception e) {
            try {
                java.lang.reflect.Method getChunkX = event.getClass().getMethod("getChunkX");
                java.lang.reflect.Method getChunkZ = event.getClass().getMethod("getChunkZ");
                chunkX = (int) getChunkX.invoke(event);
                chunkZ = (int) getChunkZ.invoke(event);
            } catch (Exception ignored) {
            }
        }

        if (world == null) return;

        enqueuePreObfuscation(world, chunkX, chunkZ);
    }

    private void registerFallbackChunkLoadListener() {
        fallbackListener = new Listener() {
            @EventHandler(priority = EventPriority.MONITOR)
            public void onChunkLoad(ChunkLoadEvent event) {
                if (!registered) return;
                if (event.isNewChunk()) return;

                World world = event.getWorld();
                int chunkX = event.getChunk().getX();
                int chunkZ = event.getChunk().getZ();

                enqueuePreObfuscation(world, chunkX, chunkZ);
            }
        };

        Bukkit.getPluginManager().registerEvents(fallbackListener, plugin);
    }

    public void enqueuePreObfuscation(World world, int chunkX, int chunkZ) {
        String worldName = world.getName();
        WorldConfig worldConfig = configManager.getWorldConfig(world);
        preObfuscator.enqueuePreObfuscation(worldName, chunkX, chunkZ, worldConfig);
    }

    public ChunkPreObfuscator getPreObfuscator() {
        return preObfuscator;
    }

    public boolean isRegistered() {
        return registered;
    }

    public boolean isPaperDetected() {
        return paperDetected;
    }

    public long getTasksEnqueued() {
        return preObfuscator.getTasksEnqueued();
    }

    public long getTasksSkippedCacheHit() {
        return preObfuscator.getTasksSkippedCacheHit();
    }

    public long getTasksSkippedDisabled() {
        return preObfuscator.getTasksSkippedDisabled();
    }

    public long getTasksSkippedNotPaper() {
        return tasksSkippedNotPaper.get();
    }

    public void resetStats() {
        preObfuscator.resetStats();
        tasksSkippedNotPaper.set(0);
    }
}
