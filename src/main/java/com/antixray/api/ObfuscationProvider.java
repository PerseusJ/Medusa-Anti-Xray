package com.antixray.api;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockState;

/**
 * Interface for custom obfuscation logic.
 */
public interface ObfuscationProvider {

    /**
     * Determine if a block should be obfuscated.
     *
     * @param blockState the BlockState of the block (may be a thread-safe proxy)
     * @param world the world the block is in
     * @param x block x coordinate
     * @param y block y coordinate
     * @param z block z coordinate
     * @return true if the block should be obfuscated, false if it should be skipped
     */
    boolean shouldObfuscate(BlockState blockState, World world, int x, int y, int z);

    /**
     * Get the replacement block material for the given Y level in the world.
     *
     * @param world the world
     * @param y block y coordinate
     * @return the replacement block material
     */
    Material getReplacementBlock(World world, int y);
}
