package com.antixray.deobfuscation;

import com.antixray.nms.NmsAdapter;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class UpdatePacketBuilder {

	private static final Logger LOGGER = Logger.getLogger("AntiXray");

	private final NmsAdapter nmsAdapter;

	public UpdatePacketBuilder(NmsAdapter nmsAdapter) {
		this.nmsAdapter = nmsAdapter;
	}

	public Object buildBlockUpdate(Location location, int blockStateId) {
		if (location == null || location.getWorld() == null) return null;
		try {
			return nmsAdapter.createBlockUpdatePacket(location, blockStateId);
		} catch (Exception e) {
			LOGGER.log(Level.FINE, "Failed to build block update packet at " + location, e);
			return null;
		}
	}

	public Object buildMultiBlockUpdate(World world, int chunkX, int chunkZ,
										Map<Location, Integer> changes) {
		if (world == null || changes == null || changes.isEmpty()) return null;
		try {
			return nmsAdapter.createMultiBlockUpdatePacket(world, chunkX, chunkZ, changes);
		} catch (Exception e) {
			LOGGER.log(Level.FINE, "Failed to build multi-block update packet for chunk ["
				+ chunkX + ", " + chunkZ + "]", e);
			return null;
		}
	}

	public Object buildChunkDataPacket(World world, int chunkX, int chunkZ) {
		if (world == null) return null;
		try {
			return nmsAdapter.createChunkDataPacket(world, chunkX, chunkZ);
		} catch (Exception e) {
			LOGGER.log(Level.FINE, "Failed to build chunk data packet for chunk ["
				+ chunkX + ", " + chunkZ + "]", e);
			return null;
		}
	}
}
