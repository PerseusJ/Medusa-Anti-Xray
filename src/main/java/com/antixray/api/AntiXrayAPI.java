package com.antixray.api;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

/**
 * Public API for third-party access to Anti-Xray functionality.
 */
public interface AntiXrayAPI {

    /**
     * Is the block at this location currently obfuscated?
     *
     * @param location the location to check
     * @return true if the block is currently obfuscated, false otherwise
     */
    boolean isObfuscated(Location location);

    /**
     * Current engine mode (1/2/3) for the world.
     *
     * @param world the world to check
     * @return the engine mode, or -1 if the plugin is not enabled for the world
     */
    int getEngineMode(World world);

    /**
     * Is the plugin enabled for the world?
     *
     * @param world the world to check
     * @return true if enabled, false otherwise
     */
    boolean isEnabled(World world);

    /**
     * Add a material to the runtime hidden-blocks set.
     *
     * @param material the material to add
     */
    void registerCustomHiddenBlock(Material material);

    /**
     * Remove a material from the runtime hidden-blocks set.
     *
     * @param material the material to remove
     */
    void unregisterCustomHiddenBlock(Material material);

    /**
     * Get the provider for custom obfuscation logic.
     *
     * @param world the world
     * @return the obfuscation provider, or null if none
     */
    ObfuscationProvider getObfuscationProvider(World world);

    /**
     * Set the provider for custom obfuscation logic.
     *
     * @param world the world
     * @param provider the custom obfuscation provider
     */
    void setObfuscationProvider(World world, ObfuscationProvider provider);
}
