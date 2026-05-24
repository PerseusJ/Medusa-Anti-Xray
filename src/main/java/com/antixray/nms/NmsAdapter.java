package com.antixray.nms;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

public interface NmsAdapter {

    List<Object> getChunkSections(Object packet);

    List<Integer> getPaletteEntries(Object chunkSection);

    long[] getPackedIndices(Object chunkSection);

    void setPaletteEntries(Object chunkSection, List<Integer> entries);

    void setPackedIndices(Object chunkSection, long[] indices, int bitsPerEntry);

    int getBlockStateAt(World world, int x, int y, int z);

    int getSectionNonEmptyCount(Object chunkSection);

    Object createBlockUpdatePacket(Location loc, int blockStateId);

	Object createMultiBlockUpdatePacket(World world, int chunkX, int chunkZ, Map<Location, Integer> changes);

	Object createChunkDataPacket(World world, int chunkX, int chunkZ);

    int getPaletteBitsPerEntry(Object chunkSection);

    boolean isSingleValuePalette(Object chunkSection);

    int getSingleValue(Object chunkSection);

    void upgradeToIndirectPalette(Object chunkSection, int singleValue, int replacementValue);

    String getVersionString();

    boolean isDirectPalette(Object chunkSection);

    int getBlockStateId(BlockData blockData);

    int getBlockStateId(Material material);

    BlockData getBlockDataFromId(int blockStateId);

    Material getTypeFromId(int blockStateId);

    void sendPacket(Player player, Object packet);
}
