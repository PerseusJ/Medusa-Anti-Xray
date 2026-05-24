package com.antixray;

import com.antixray.api.AntiXrayAPI;
import com.antixray.api.ObfuscationProvider;
import com.antixray.api.DefaultObfuscationProvider;
import com.antixray.config.WorldConfig;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of the AntiXrayAPI.
 */
public class AntiXrayAPIImpl implements AntiXrayAPI {

    private final AntiXrayPlugin plugin;
    private final Map<UUID, ObfuscationProvider> providers = new ConcurrentHashMap<>();
    private final DefaultObfuscationProvider defaultProvider = new DefaultObfuscationProvider();

    public AntiXrayAPIImpl(AntiXrayPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean isObfuscated(Location location) {
        if (location == null) return false;
        World world = location.getWorld();
        if (world == null || !isEnabled(world)) {
            return false;
        }
        int blockStateId = plugin.getNmsAdapter().getBlockStateAt(world, location.getBlockX(), location.getBlockY(), location.getBlockZ());
        if (blockStateId == -1) {
            return false;
        }
        if (!plugin.getObfuscationEngine().getMaterialSet().isHidden(blockStateId)) {
            return false;
        }
        return !plugin.getObfuscationEngine().getExposureChecker().isAirExposed(world, location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    @Override
    public int getEngineMode(World world) {
        if (world == null) return -1;
        WorldConfig wc = plugin.getConfigurationManager().getWorldConfig(world);
        if (wc == null || !wc.isEnabled()) return -1;
        return switch (wc.getEngineMode()) {
            case MODE_1 -> 1;
            case MODE_2 -> 2;
            case MODE_3 -> 3;
        };
    }

    @Override
    public boolean isEnabled(World world) {
        if (world == null) return false;
        WorldConfig wc = plugin.getConfigurationManager().getWorldConfig(world);
        return wc != null && wc.isEnabled();
    }

    @Override
    public void registerCustomHiddenBlock(Material material) {
        if (plugin.getObfuscationEngine() != null) {
            plugin.getObfuscationEngine().getMaterialSet().registerCustomHiddenBlock(plugin.getNmsAdapter(), material);
        }
    }

    @Override
    public void unregisterCustomHiddenBlock(Material material) {
        if (plugin.getObfuscationEngine() != null) {
            plugin.getObfuscationEngine().getMaterialSet().unregisterCustomHiddenBlock(plugin.getNmsAdapter(), material);
        }
    }

    @Override
    public ObfuscationProvider getObfuscationProvider(World world) {
        if (world == null) return null;
        ObfuscationProvider provider = providers.get(world.getUID());
        return provider != null ? provider : defaultProvider;
    }

    @Override
    public void setObfuscationProvider(World world, ObfuscationProvider provider) {
        if (world == null) return;
        if (provider == null) {
            providers.remove(world.getUID());
        } else {
            providers.put(world.getUID(), provider);
        }
    }
}
