package com.antixray.packet;

import com.antixray.AntiXrayPlugin;
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
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ProtocolLibInterceptor implements PacketInterceptor {

    private static final Logger LOGGER = Logger.getLogger("AntiXray");

    private final AntiXrayPlugin plugin;
    private final ObfuscationEngine engine;
    private final NmsAdapter adapter;

    private PacketAdapter packetAdapter;
    private boolean registered = false;
    private boolean available = false;

    private Field chunkXField;
    private Field chunkZField;
    private Field chunkDataField;

    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final AtomicLong syncObfuscations = new AtomicLong(0);

    public ProtocolLibInterceptor(AntiXrayPlugin plugin, ObfuscationEngine engine, NmsAdapter adapter) {
        this.plugin = plugin;
        this.engine = engine;
        this.adapter = adapter;
        probeAvailability();
    }

    private void probeAvailability() {
        try {
            Class.forName("com.comphenix.protocol.ProtocolLibrary");
            available = true;
        } catch (ClassNotFoundException e) {
            available = false;
        }
    }

    @Override
    public void register() {
        if (registered) return;
        if (!available) {
            LOGGER.warning("ProtocolLib not available, cannot register interceptor");
            return;
        }

        try {
            ProtocolManager manager = ProtocolLibrary.getProtocolManager();
            if (manager == null) {
                LOGGER.warning("ProtocolLib ProtocolManager is null");
                available = false;
                return;
            }

            packetAdapter = new PacketAdapter(plugin, ListenerPriority.NORMAL,
                    PacketType.Play.Server.MAP_CHUNK) {
                @Override
                public void onPacketSending(PacketEvent event) {
                    ProtocolLibInterceptor.this.onPacketSending(event);
                }
            };

            manager.addPacketListener(packetAdapter);
            registered = true;
            LOGGER.info("ProtocolLib packet interceptor registered (MAP_CHUNK listener)");
        } catch (NoClassDefFoundError e) {
            LOGGER.warning("ProtocolLib classes not found at runtime: " + e.getMessage());
            available = false;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to register ProtocolLib interceptor", e);
            available = false;
        }
    }

    private void onPacketSending(PacketEvent event) {
        try {
            Player player = event.getPlayer();
            World world = player.getWorld();

            Object nmsPacket = event.getPacket().getHandle();
            int chunkX = getChunkX(nmsPacket);
            int chunkZ = getChunkZ(nmsPacket);

            ObfuscationCache cache = plugin.getObfuscationCache();

            if (cache != null) {
                CacheKey key = buildCacheKey(world, chunkX, chunkZ);
                CacheEntry cached = cache.get(key);

                if (cached != null) {
                    List<Object> sections = adapter.getChunkSections(nmsPacket);
                    if (sections.isEmpty()) return;
                    engine.applySerializedObfuscation(cached.getObfuscatedData(), sections);
                    applyDeobfuscationOverlay(sections, world, chunkX, chunkZ, player);
                    cacheHits.incrementAndGet();
                    return;
                }

                cacheMisses.incrementAndGet();
            }

            List<Object> sections = adapter.getChunkSections(nmsPacket);
            if (sections.isEmpty()) return;

            for (int i = 0; i < sections.size(); i++) {
                Object section = sections.get(i);
                if (section == null) continue;
                int sectionBaseY = (i - getSectionOffset(world)) * 16;
                engine.obfuscateSection(section, world, chunkX, chunkZ, sectionBaseY);
            }
            syncObfuscations.incrementAndGet();

            if (cache != null) {
                CacheKey key = buildCacheKey(world, chunkX, chunkZ);
                CacheEntry entry = engine.obfuscateAndSerialize(nmsPacket, world, chunkX, chunkZ);
                if (entry != null) {
                    cache.put(key, entry);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "ProtocolLib packet interception failed", e);
        }
    }

    private CacheKey buildCacheKey(World world, int chunkX, int chunkZ) {
        String worldName = world.getName();
        ObfuscationMode mode = engine.getEngineMode();
        int configHash = plugin.getConfigurationManager().getConfigHash();
        return new CacheKey(worldName, chunkX, chunkZ, mode, configHash);
    }

    private void applyDeobfuscationOverlay(List<Object> sections, World world, int chunkX, int chunkZ, Player player) {
        try {
            DeobfuscationManager deobfManager = plugin.getDeobfuscationManager();
            ObfuscationCache cache = plugin.getObfuscationCache();
            if (deobfManager == null || cache == null) return;

            PlayerData data = plugin.getPlayerData(player.getUniqueId());
            if (data == null) return;
            RevealedBlocksSet revealed = data.getRevealedBlocks();
            if (revealed == null || revealed.size() == 0) return;

            List<BlockPosition> overlayPositions = cache.getOverlayPositions(
                revealed, chunkX, chunkZ, world.getName());
            if (!overlayPositions.isEmpty()) {
                deobfManager.queueDeobfuscation(player, overlayPositions);
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to apply deobfuscation overlay", e);
        }
    }

    private int getChunkX(Object packet) {
        try {
            if (chunkXField == null) {
                chunkXField = findIntField(packet, "a", "chunkX", "x", "field_149266_c");
            }
            if (chunkXField != null) {
                return chunkXField.getInt(packet);
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    private int getChunkZ(Object packet) {
        try {
            if (chunkZField == null) {
                chunkZField = findIntField(packet, "b", "chunkZ", "z", "field_149271_d");
            }
            if (chunkZField != null) {
                return chunkZField.getInt(packet);
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    private int getSectionOffset(World world) {
        return world.getMinHeight() >> 4;
    }

    private Field findIntField(Object packet, String... names) {
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
    public void unregister() {
        if (!registered) return;

        try {
            ProtocolManager manager = ProtocolLibrary.getProtocolManager();
            if (manager != null && packetAdapter != null) {
                manager.removePacketListener(packetAdapter);
            }
            registered = false;
            packetAdapter = null;
            LOGGER.info("ProtocolLib packet interceptor unregistered");
        } catch (NoClassDefFoundError ignored) {
            registered = false;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error during ProtocolLib interceptor unregistration", e);
            registered = false;
        }
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public InterceptionMode getMode() {
        return InterceptionMode.PROTOCOL_LIB;
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
}
