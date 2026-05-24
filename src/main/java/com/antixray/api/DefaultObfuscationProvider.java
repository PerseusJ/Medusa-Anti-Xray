package com.antixray.api;

import com.antixray.AntiXrayPlugin;
import com.antixray.engine.AirExposureChecker;
import com.antixray.nms.NmsAdapter;
import com.antixray.util.MaterialSet;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;

/**
 * Default implementation of ObfuscationProvider using configuration rules.
 */
public class DefaultObfuscationProvider implements ObfuscationProvider {

    @Override
    public boolean shouldObfuscate(BlockState blockState, World world, int x, int y, int z) {
        AntiXrayPlugin plugin = AntiXrayPlugin.getInstance();
        if (plugin == null) return false;
        NmsAdapter adapter = plugin.getNmsAdapter();
        if (plugin.getObfuscationEngine() == null) return false;
        MaterialSet materialSet = plugin.getObfuscationEngine().getMaterialSet();
        AirExposureChecker exposureChecker = plugin.getObfuscationEngine().getExposureChecker();

        if (adapter == null || materialSet == null || exposureChecker == null) return false;

        Material material = blockState.getType();
        int blockStateId = adapter.getBlockStateId(material);
        if (blockStateId == -1) return false;

        if (!materialSet.isHidden(blockStateId)) return false;

        // Check air exposure
        return !exposureChecker.isAirExposed(world, x, y, z);
    }

    @Override
    public Material getReplacementBlock(World world, int y) {
        AntiXrayPlugin plugin = AntiXrayPlugin.getInstance();
        if (plugin == null) return Material.STONE;
        if (plugin.getObfuscationEngine() == null) return Material.STONE;
        MaterialSet materialSet = plugin.getObfuscationEngine().getMaterialSet();
        int deepslateBelowY = plugin.getObfuscationEngine().getDeepslateBelowY();
        int id = materialSet.getReplacement(y, deepslateBelowY, world.getEnvironment().name());
        BlockData blockData = plugin.getNmsAdapter().getBlockDataFromId(id);
        return blockData != null ? blockData.getMaterial() : Material.STONE;
    }
}
