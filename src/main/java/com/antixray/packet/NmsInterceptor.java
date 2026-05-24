package com.antixray.packet;

import com.antixray.AntiXrayPlugin;
import com.antixray.async.AsyncProcessor;
import com.antixray.async.ObfuscationTask;
import com.antixray.cache.CacheEntry;
import com.antixray.cache.CacheKey;
import com.antixray.cache.ObfuscationCache;
import com.antixray.deobfuscation.DeobfuscationManager;
import com.antixray.deobfuscation.PlayerData;
import com.antixray.deobfuscation.RevealedBlocksSet;
import com.antixray.engine.ObfuscationEngine;
import com.antixray.engine.ObfuscationMode;
import com.antixray.nms.NmsAdapter;
import com.antixray.util.BlockPosition;
import com.antixray.util.VersionUtil;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

public class NmsInterceptor implements PacketInterceptor {

    private static final Logger LOGGER = Logger.getLogger("AntiXray");
    private static final long CRITICAL_SYNC_TIMEOUT_MS = 50;

    private final AntiXrayPlugin plugin;
    private final ObfuscationEngine engine;
    private final NmsAdapter adapter;

    private Object originalController;
    private Object customController;
    private Field controllerField;
    private boolean registered = false;
    private boolean available = false;

    private Class<?> chunkPacketBlockControllerClass;
    private Class<?> worldServerClass;
    private Class<?> chunkPacketBlockControllerAbstractClass;

    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final AtomicLong syncObfuscations = new AtomicLong(0);
    private final AtomicLong asyncEnqueues = new AtomicLong(0);

    public NmsInterceptor(AntiXrayPlugin plugin, ObfuscationEngine engine, NmsAdapter adapter) {
        this.plugin = plugin;
        this.engine = engine;
        this.adapter = adapter;
        probeAvailability();
    }

    private void probeAvailability() {
        if (!VersionUtil.isPaper()) {
            available = false;
            return;
        }

        try {
            chunkPacketBlockControllerAbstractClass = Class.forName(
                "com.destroystokyo.paper.antixray.ChunkPacketBlockController");
        } catch (ClassNotFoundException ignored) {
            try {
                chunkPacketBlockControllerAbstractClass = Class.forName(
                    "io.papermc.paper.antixray.ChunkPacketBlockController");
            } catch (ClassNotFoundException ignored2) {
                LOGGER.fine("ChunkPacketBlockController abstract class not found");
            }
        }

        if (chunkPacketBlockControllerAbstractClass == null) {
            available = false;
            return;
        }

        try {
            chunkPacketBlockControllerClass = Class.forName(
                "com.destroystokyo.paper.antixray.ChunkPacketBlockControllerAntiXray");
        } catch (ClassNotFoundException ignored) {
            try {
                chunkPacketBlockControllerClass = Class.forName(
                    "io.papermc.paper.antixray.ChunkPacketBlockControllerAntiXray");
            } catch (ClassNotFoundException ignored2) {
                LOGGER.fine("ChunkPacketBlockControllerAntiXray implementation class not found");
            }
        }

        try {
            worldServerClass = Class.forName(
                "net.minecraft.server.level.WorldServer");
        } catch (ClassNotFoundException ignored) {
            try {
                worldServerClass = Class.forName(
                    "net.minecraft.server.level.ServerLevel");
            } catch (ClassNotFoundException ignored2) {
                LOGGER.fine("WorldServer/ServerLevel class not found");
            }
        }

        if (worldServerClass != null) {
            controllerField = findControllerField();
        }

        available = (controllerField != null);
    }

    private Field findControllerField() {
        if (worldServerClass == null) return null;

        String[] fieldNames = {"chunkPacketBlockController", "antiXray", "paperAntiXray"};
        for (String name : fieldNames) {
            try {
                Field field = worldServerClass.getDeclaredField(name);
                field.setAccessible(true);
                if (chunkPacketBlockControllerAbstractClass.isAssignableFrom(field.getType())) {
                    return field;
                }
            } catch (NoSuchFieldException ignored) {
            }
        }

        for (Field field : worldServerClass.getDeclaredFields()) {
            if (field.getType() != null
                && chunkPacketBlockControllerAbstractClass.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                return field;
            }
        }

        return null;
    }

    private Object getNmsWorld(World world) {
        try {
            Method getHandle = world.getClass().getMethod("getHandle");
            return getHandle.invoke(world);
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to get NMS world handle", e);
            return null;
        }
    }

    @Override
    public synchronized void register() {
        if (registered) return;
        if (!available) {
            LOGGER.warning("NMS interception not available, cannot register");
            return;
        }

        try {
            for (World world : plugin.getServer().getWorlds()) {
                installController(world);
            }
            registered = true;
            LOGGER.info("NMS packet interceptor registered (ChunkPacketBlockController hook)");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to register NMS interceptor, falling back", e);
            available = false;
            registered = false;
        }
    }

    private void installController(World world) {
        try {
            Object nmsWorld = getNmsWorld(world);
            if (nmsWorld == null) return;

            Object existing = controllerField.get(nmsWorld);
            if (existing == null) {
                LOGGER.fine("No existing ChunkPacketBlockController in world: " + world.getName());
            }

            originalController = existing;

            customController = createCustomController(world, nmsWorld);
            if (customController != null) {
                controllerField.set(nmsWorld, customController);
                LOGGER.fine("Installed custom ChunkPacketBlockController in world: " + world.getName());
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to install controller in world: " + world.getName(), e);
        }
    }

    private Object createCustomController(World world, Object nmsWorld) {
        if (chunkPacketBlockControllerClass == null) return null;

        try {
            Object controller = createAntiXrayController(nmsWorld);
            if (controller != null) {
                wrapController(controller, world);
                return controller;
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to create ChunkPacketBlockControllerAntiXray, trying proxy", e);
        }

        try {
            return createProxyController(world, nmsWorld);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to create proxy controller", e);
            return null;
        }
    }

    private Object createAntiXrayController(Object nmsWorld) {
        try {
            return chunkPacketBlockControllerClass.getDeclaredConstructor().newInstance();
        } catch (Exception ignored) {
        }

        try {
            Class<?> worldClass = nmsWorld.getClass();
            while (worldClass != null) {
                for (Constructor<?> ctor : worldClass.getDeclaredConstructors()) {
                    Class<?>[] params = ctor.getParameterTypes();
                    if (params.length == 0) continue;
                    boolean hasChunkPacketParam = false;
                    for (Class<?> p : params) {
                        if (chunkPacketBlockControllerAbstractClass.isAssignableFrom(p)) {
                            hasChunkPacketParam = true;
                            break;
                        }
                    }
                    if (hasChunkPacketParam) continue;

                    for (Class<?> p : params) {
                        if (worldServerClass.isAssignableFrom(p)
                            || p.isInstance(nmsWorld)
                            || p.getName().contains("World")
                            || p.getName().contains("Level")) {
                            try {
                                ctor.setAccessible(true);
                                Object[] args = buildConstructorArgs(ctor, nmsWorld);
                                return ctor.newInstance(args);
                            } catch (Exception ignored2) {
                                break;
                            }
                        }
                    }
                }
                worldClass = worldClass.getSuperclass();
            }
        } catch (Exception ignored) {
        }

        try {
            for (Constructor<?> ctor : chunkPacketBlockControllerClass.getDeclaredConstructors()) {
                if (ctor.getParameterCount() == 0) {
                    ctor.setAccessible(true);
                    return ctor.newInstance();
                }
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    private Object[] buildConstructorArgs(Constructor<?> ctor, Object nmsWorld) {
        Class<?>[] paramTypes = ctor.getParameterTypes();
        Object[] args = new Object[paramTypes.length];
        for (int i = 0; i < paramTypes.length; i++) {
            if (paramTypes[i].isInstance(nmsWorld) || worldServerClass.isAssignableFrom(paramTypes[i])) {
                args[i] = nmsWorld;
            } else if (paramTypes[i] == boolean.class || paramTypes[i] == Boolean.class) {
                args[i] = true;
            } else if (paramTypes[i] == int.class || paramTypes[i] == Integer.class) {
                args[i] = 0;
            } else if (paramTypes[i] == long.class || paramTypes[i] == Long.class) {
                args[i] = 0L;
            } else if (paramTypes[i] == double.class || paramTypes[i] == Double.class) {
                args[i] = 0.0;
            } else if (paramTypes[i] == float.class || paramTypes[i] == Float.class) {
                args[i] = 0.0f;
            } else if (paramTypes[i] == String.class) {
                args[i] = "";
            } else if (paramTypes[i].isArray()) {
                args[i] = java.lang.reflect.Array.newInstance(paramTypes[i].getComponentType(), 0);
            } else {
                args[i] = null;
            }
        }
        return args;
    }

    private Object createProxyController(World world, Object nmsWorld) {
        return java.lang.reflect.Proxy.newProxyInstance(
            chunkPacketBlockControllerAbstractClass.getClassLoader(),
            new Class<?>[]{chunkPacketBlockControllerAbstractClass},
            (java.lang.reflect.InvocationHandler) (proxy, method, args) -> handleProxyMethod(proxy, method, args, world));
    }

    private Object handleProxyMethod(Object proxy, Method method, Object[] args, World world) {
        String methodName = method.getName();

        if (methodName.equals("modifyBlocksPacket") || methodName.equals("onPacketSending")
            || methodName.equals("a")) {
            if (args != null && args.length >= 2) {
                try {
                    Object packet = args[args.length - 1];
                    if (packet != null) {
                        obfuscatePacketAsync(packet, world);
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.FINE, "Proxy modifyBlocksPacket failed", e);
                }
            }
            return args != null && args.length > 0 ? args[args.length - 1] : null;
        }

        if (originalController != null) {
            try {
                return method.invoke(originalController, args);
            } catch (Exception ignored) {
            }
        }

        return getDefaultReturnValue(method.getReturnType());
    }

    private Object getDefaultReturnValue(Class<?> returnType) {
        if (returnType == boolean.class) return false;
        if (returnType == int.class) return 0;
        if (returnType == long.class) return 0L;
        if (returnType == double.class) return 0.0;
        if (returnType == float.class) return 0.0f;
        if (returnType == short.class) return (short) 0;
        if (returnType == byte.class) return (byte) 0;
        if (returnType == char.class) return '\0';
        return null;
    }

    private void wrapController(Object controller, World world) {
        try {
            Field engineField = null;
            String[] engineFieldNames = {"obfuscationEngine", "engine", "antiXrayEngine",
                "chunkPacketBlockControllerAntiXrayEngine"};

            for (String name : engineFieldNames) {
                try {
                    Field f = controller.getClass().getDeclaredField(name);
                    f.setAccessible(true);
                    engineField = f;
                    break;
                } catch (NoSuchFieldException ignored) {
                }
            }

            if (engineField == null) {
                Class<?> current = controller.getClass();
                while (current != null && current != Object.class) {
                    for (Field f : current.getDeclaredFields()) {
                        if (f.getType().getName().contains("Engine")
                            || f.getType().getName().contains("AntiXray")
                            || f.getType().getName().contains("Obfuscation")) {
                            f.setAccessible(true);
                            engineField = f;
                            break;
                        }
                    }
                    if (engineField != null) break;
                    current = current.getSuperclass();
                }
            }

            if (engineField != null) {
                Object existingEngine = engineField.get(controller);
                injectObfuscationCallback(controller, world, engineField);
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Could not wrap existing controller, relying on field replacement", e);
        }
    }

    private void injectObfuscationCallback(Object controller, World world, Field engineField) {
    }

    private void obfuscatePacketAsync(Object packet, World world) {
        try {
            Field chunkXField = findPacketIntField(packet, "a", "chunkX", "x");
            Field chunkZField = findPacketIntField(packet, "b", "chunkZ", "z");

            int chunkX = 0;
            int chunkZ = 0;
            if (chunkXField != null) {
                chunkX = chunkXField.getInt(packet);
            }
            if (chunkZField != null) {
                chunkZ = chunkZField.getInt(packet);
            }

            AsyncProcessor asyncProcessor = plugin.getAsyncProcessor();
            ObfuscationCache cache = plugin.getObfuscationCache();

            if (asyncProcessor == null || cache == null) {
                engine.obfuscateChunk(packet, world, chunkX, chunkZ);
                syncObfuscations.incrementAndGet();
                return;
            }

            CacheKey key = buildCacheKey(world, chunkX, chunkZ);

            CacheEntry cached = cache.get(key);
            if (cached != null) {
                List<Object> sections = adapter.getChunkSections(packet);
                engine.applySerializedObfuscation(cached.getObfuscatedData(), sections);
                applyDeobfuscationOverlay(sections, world, chunkX, chunkZ);
                cacheHits.incrementAndGet();
                return;
            }

            cacheMisses.incrementAndGet();

            ObfuscationTask.Priority priority = determinePriority(world, chunkX, chunkZ);

            if (priority == ObfuscationTask.Priority.CRITICAL) {
                engine.obfuscateChunk(packet, world, chunkX, chunkZ);
                CacheEntry entry = engine.obfuscateAndSerialize(packet, world, chunkX, chunkZ);
                if (entry != null) {
                    cache.put(key, entry);
                }
                syncObfuscations.incrementAndGet();
            } else {
                engine.obfuscateChunk(packet, world, chunkX, chunkZ);
                enqueueAsyncObfuscation(key, world, chunkX, chunkZ);
                asyncEnqueues.incrementAndGet();
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to obfuscate packet via NMS interceptor", e);
        }
    }

    private void obfuscatePacket(Object packet, World world) {
        try {
            Field chunkXField = findPacketIntField(packet, "a", "chunkX", "x");
            Field chunkZField = findPacketIntField(packet, "b", "chunkZ", "z");

            int chunkX = 0;
            int chunkZ = 0;
            if (chunkXField != null) {
                chunkX = chunkXField.getInt(packet);
            }
            if (chunkZField != null) {
                chunkZ = chunkZField.getInt(packet);
            }

            engine.obfuscateChunk(packet, world, chunkX, chunkZ);
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to obfuscate packet via NMS interceptor", e);
        }
    }

    private CacheKey buildCacheKey(World world, int chunkX, int chunkZ) {
        String worldName = world.getName();
        ObfuscationMode mode = engine.getEngineMode();
        int configHash = plugin.getConfigurationManager().getConfigHash();
        return new CacheKey(worldName, chunkX, chunkZ, mode, configHash);
    }

    private ObfuscationTask.Priority determinePriority(World world, int chunkX, int chunkZ) {
        return ObfuscationTask.Priority.HIGH;
    }

    private void enqueueAsyncObfuscation(CacheKey key, World world, int chunkX, int chunkZ) {
        AsyncProcessor asyncProcessor = plugin.getAsyncProcessor();
        if (asyncProcessor == null) return;

        String worldName = world.getName();
        ObfuscationTask task = new ObfuscationTask(key, ObfuscationTask.Priority.LOW, result -> {
            if (result == null) return;
            LOGGER.log(Level.FINE, "Async obfuscation completed for {0}", key);
        }, asyncProcessor);

        asyncProcessor.enqueue(task);
    }

    private void applyDeobfuscationOverlay(List<Object> sections, World world, int chunkX, int chunkZ) {
        try {
            DeobfuscationManager deobfManager = plugin.getDeobfuscationManager();
            ObfuscationCache cache = plugin.getObfuscationCache();
            if (deobfManager == null || cache == null) return;

            for (Player onlinePlayer : world.getPlayers()) {
                PlayerData data = plugin.getPlayerData(onlinePlayer.getUniqueId());
                if (data == null) continue;
                RevealedBlocksSet revealed = data.getRevealedBlocks();
                if (revealed == null || revealed.size() == 0) continue;

                List<BlockPosition> overlayPositions = cache.getOverlayPositions(
                    revealed, chunkX, chunkZ, world.getName());
                if (!overlayPositions.isEmpty()) {
                    deobfManager.queueDeobfuscation(onlinePlayer, overlayPositions);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to apply deobfuscation overlay", e);
        }
    }

    private Field findPacketIntField(Object packet, String... names) {
        Class<?> clazz = packet.getClass();
        while (clazz != null && clazz != Object.class) {
            for (String name : names) {
                try {
                    Field f = clazz.getDeclaredField(name);
                    if (f.getType() == int.class) {
                        f.setAccessible(true);
                        return f;
                    }
                } catch (NoSuchFieldException ignored) {
                }
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    @Override
    public synchronized void unregister() {
        if (!registered) return;

        try {
            for (World world : plugin.getServer().getWorlds()) {
                restoreController(world);
            }
            registered = false;
            LOGGER.info("NMS packet interceptor unregistered");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error during NMS interceptor unregistration", e);
            registered = false;
        }
    }

    private void restoreController(World world) {
        try {
            Object nmsWorld = getNmsWorld(world);
            if (nmsWorld == null) return;

            Object current = controllerField.get(nmsWorld);
            if (current == customController) {
                controllerField.set(nmsWorld, originalController);
                LOGGER.fine("Restored original ChunkPacketBlockController in world: " + world.getName());
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to restore controller in world: " + world.getName(), e);
        }
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public InterceptionMode getMode() {
        return InterceptionMode.NMS;
    }

    public long getCacheHits() {
        return cacheHits.get();
    }

    public long getCacheMisses() {
        return cacheMisses.get();
    }

    public long getSyncObfuscations() {
        return syncObfuscations.get();
    }

    public long getAsyncEnqueues() {
        return asyncEnqueues.get();
    }
}
