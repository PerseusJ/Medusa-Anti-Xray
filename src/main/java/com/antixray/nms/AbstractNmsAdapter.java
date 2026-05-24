package com.antixray.nms;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class AbstractNmsAdapter implements NmsAdapter {

    protected static final Logger LOGGER = Logger.getLogger("AntiXray");

    private final String versionString;

    protected final Class<?> levelChunkSectionClass;
    protected final Class<?> palettedContainerClass;
    protected final Class<?> paletteClass;
    protected final Class<?> singleValuePaletteClass;
    protected final Class<?> linearPaletteClass;
    protected final Class<?> globalPaletteClass;
    protected final Class<?> blockStateClass;
    protected final Class<?> blockStateListClass;
    protected final Class<?> craftWorldClass;
    protected final Class<?> nmsLevelClass;
    protected final Class<?> blockPosClass;
    protected final Class<?> blockUpdatePacketClass;
    protected final Class<?> multiBlockUpdatePacketClass;
	protected final Class<?> sectionPosClass;
	protected final Class<?> craftBlockDataClass;
	protected final Class<?> craftBlockStateClass;
	protected final Class<?> chunkDataPacketClass;
	protected final Class<?> levelChunkClass;

    protected final Method palettedContainerGetPalette;
    protected final Method palettedContainerGetData;
    protected final Method paletteGetSize;
    protected final Method paletteGetId;
    protected final Method blockStateGetId;

    protected final Method craftWorldGetHandle;
    protected final Method nmsLevelGetBlockState;
    protected final Constructor<?> blockPosCreate;

    protected final Field sectionNonEmptyField;
    protected final Field palettedContainerBitsField;

    protected AbstractNmsAdapter(String versionString,
                                 String[] palettedContainerGetPaletteNames,
                                 String[] palettedContainerGetDataNames,
                                 String[] paletteGetSizeNames,
                                 String[] paletteGetIdNames,
                                 String[] blockStateGetIdNames,
                                 String[] craftWorldGetHandleNames,
                                 String[] nmsLevelGetBlockStateNames,
                                 String[] sectionNonEmptyFieldNames,
                                 String[] palettedContainerBitsFieldNames) {
        this.versionString = versionString;

        this.levelChunkSectionClass = getNmsClass("net.minecraft.world.level.chunk.LevelChunkSection");
        this.palettedContainerClass = getNmsClass("net.minecraft.world.level.chunk.PalettedContainer");
        this.paletteClass = getNmsClass("net.minecraft.world.level.chunk.Palette");
        this.singleValuePaletteClass = getNmsClass("net.minecraft.world.level.chunk.SingleValuePalette");
        this.linearPaletteClass = getNmsClass("net.minecraft.world.level.chunk.LinearPalette");
        this.globalPaletteClass = getNmsClass("net.minecraft.world.level.chunk.GlobalPalette");
        this.blockStateClass = getNmsClass("net.minecraft.world.level.block.state.IBlockData");
        this.blockStateListClass = getNmsClass("net.minecraft.core.RegistryBlockID");
        this.craftWorldClass = getBukkitClass("org.bukkit.craftbukkit.CraftWorld");
        this.nmsLevelClass = getNmsClass("net.minecraft.server.level.WorldServer");
        this.blockPosClass = getNmsClass("net.minecraft.core.BlockPosition");
        this.blockUpdatePacketClass = getNmsClass("net.minecraft.network.protocol.game.PacketPlayOutBlockChange");
        this.multiBlockUpdatePacketClass = getNmsClass("net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket");
        this.sectionPosClass = getNmsClass("net.minecraft.core.SectionPosition");

		this.craftBlockDataClass = getBukkitClass("org.bukkit.craftbukkit.block.data.CraftBlockData");
		this.craftBlockStateClass = getBukkitClass("org.bukkit.craftbukkit.block.CraftBlockStates");
		this.chunkDataPacketClass = getNmsClass("net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket");
		this.levelChunkClass = getNmsClass("net.minecraft.world.level.chunk.LevelChunk");

        this.palettedContainerGetPalette = findMethod(palettedContainerClass, palettedContainerGetPaletteNames, true);
        this.palettedContainerGetData = findMethod(palettedContainerClass, palettedContainerGetDataNames, true);
        this.paletteGetSize = findMethod(paletteClass, paletteGetSizeNames, true);
        this.paletteGetId = findMethod(paletteClass, paletteGetIdNames, false, Object.class);
        this.blockStateGetId = findMethod(blockStateClass, blockStateGetIdNames, true);

        this.craftWorldGetHandle = findMethod(craftWorldClass, craftWorldGetHandleNames, true);
        this.nmsLevelGetBlockState = findMethod(nmsLevelClass, nmsLevelGetBlockStateNames, false, blockPosClass);
        this.blockPosCreate = findConstructor(blockPosClass, int.class, int.class, int.class);

        this.sectionNonEmptyField = findField(levelChunkSectionClass, sectionNonEmptyFieldNames);
        this.palettedContainerBitsField = findField(palettedContainerClass, palettedContainerBitsFieldNames);
    }

    protected Object getPalettedContainer(Object chunkSection) {
        try {
            if (palettedContainerClass != null) {
                for (Field field : chunkSection.getClass().getDeclaredFields()) {
                    if (palettedContainerClass.isAssignableFrom(field.getType())) {
                        field.setAccessible(true);
                        return field.get(chunkSection);
                    }
                }
            }
            Method method = findMethod(chunkSection.getClass(),
                    new String[]{"a", "getBlocks", "getBlockStates", "getPalettedContainer"}, true);
            if (method != null) {
                return method.invoke(chunkSection);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to get PalettedContainer from chunk section", e);
        }
        return null;
    }

    protected int getBlockStateId(Object blockState) {
        if (blockState == null) return 0;
        if (blockState instanceof Number) {
            return ((Number) blockState).intValue();
        }
        try {
            if (blockStateGetId != null) {
                Object id = blockStateGetId.invoke(blockState);
                if (id instanceof Number) {
                    return ((Number) id).intValue();
                }
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    protected Object resolveBlockStateById(Object nmsWorld, int blockStateId) {
        try {
            if (blockStateListClass != null) {
                Method fromIdMethod = findMethod(blockStateListClass,
                        new String[]{"a", "fromId", "getById", "byId"}, false, int.class);
                if (fromIdMethod != null) {
                    return fromIdMethod.invoke(null, blockStateId);
                }
            }
            Method getBlockStateMethod = findMethod(nmsLevelClass,
                    new String[]{"a_", "getBlockStateFromId"}, false, int.class);
            if (getBlockStateMethod != null) {
                return getBlockStateMethod.invoke(nmsWorld, blockStateId);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    protected Object resolveBlockStateFromRegistry(int blockStateId) {
        try {
            if (blockStateListClass != null) {
                Field instanceField = findField(blockStateListClass, new String[]{"a", "INSTANCE", "REGISTRY"});
                if (instanceField != null) {
                    instanceField.setAccessible(true);
                    Object registry = instanceField.get(null);
                    if (registry != null) {
                        Method fromIdMethod = findMethod(registry.getClass(),
                                new String[]{"a", "fromId", "getById"}, false, int.class);
                        if (fromIdMethod != null) {
                            return fromIdMethod.invoke(registry, blockStateId);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    protected Object invokeMethod(Method method, Object obj, Object... args) {
        if (method == null) return null;
        try {
            return method.invoke(obj, args);
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Method invocation failed: " + method.getName(), e);
            return null;
        }
    }

    protected Object invokeConstructor(Constructor<?> constructor, Object... args) {
        if (constructor == null) return null;
        try {
            return constructor.newInstance(args);
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Constructor invocation failed", e);
            return null;
        }
    }

    protected Object getFieldValue(Field field, Object obj) {
        if (field == null) return null;
        try {
            return field.get(obj);
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Field access failed: " + field.getName(), e);
            return null;
        }
    }

    @Override
    public String getVersionString() {
        return versionString;
    }

    @Override
    public boolean isDirectPalette(Object chunkSection) {
        try {
            Object palettedContainer = getPalettedContainer(chunkSection);
            if (palettedContainer == null) return false;
            Object palette = invokeMethod(palettedContainerGetPalette, palettedContainer);
            return globalPaletteClass != null && globalPaletteClass.isInstance(palette);
        } catch (Exception e) {
            return false;
        }
    }

    protected static Class<?> getNmsClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            LOGGER.fine("NMS class not found: " + className);
            return null;
        }
    }

    protected static Class<?> getBukkitClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            String craftBukkitPrefix = Bukkit.getServer().getClass().getPackage().getName();
            if (craftBukkitPrefix != null) {
                try {
                    return Class.forName(craftBukkitPrefix + "." + className.substring(className.lastIndexOf('.') + 1));
                } catch (ClassNotFoundException ignored) {
                }
            }
            LOGGER.fine("Bukkit class not found: " + className);
            return null;
        }
    }

    protected static Method findMethod(Class<?> clazz, String[] names, boolean isNoArg) {
        if (clazz == null) return null;
        for (String name : names) {
            try {
                if (isNoArg) {
                    Method m = clazz.getDeclaredMethod(name);
                    m.setAccessible(true);
                    return m;
                } else {
                    for (Method m : clazz.getDeclaredMethods()) {
                        if (m.getName().equals(name)) {
                            m.setAccessible(true);
                            return m;
                        }
                    }
                }
            } catch (NoSuchMethodException ignored) {
            }
        }
        if (clazz.getSuperclass() != null) {
            Method m = findMethod(clazz.getSuperclass(), names, isNoArg);
            if (m != null) return m;
        }
        for (Class<?> iface : clazz.getInterfaces()) {
            Method m = findMethod(iface, names, isNoArg);
            if (m != null) return m;
        }
        return null;
    }

    protected static Method findMethod(Class<?> clazz, String[] names, boolean isNoArg, Class<?>... paramTypes) {
        if (clazz == null) return null;
        for (String name : names) {
            try {
                Method m = clazz.getDeclaredMethod(name, paramTypes);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
            }
        }
        for (Method m : clazz.getDeclaredMethods()) {
            for (String name : names) {
                if (m.getName().equals(name) && m.getParameterCount() == paramTypes.length) {
                    m.setAccessible(true);
                    return m;
                }
            }
        }
        return null;
    }

    protected static Constructor<?> findConstructor(Class<?> clazz, Class<?>... paramTypes) {
        if (clazz == null) return null;
        try {
            Constructor<?> c = clazz.getDeclaredConstructor(paramTypes);
            c.setAccessible(true);
            return c;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    @Override
    public int getBlockStateId(BlockData blockData) {
        if (blockData == null) return -1;
        try {
            if (craftBlockDataClass != null && craftBlockDataClass.isInstance(blockData)) {
                Method getStateMethod = findMethod(craftBlockDataClass,
                        new String[]{"getState", "a", "getHandle"}, true);
                if (getStateMethod != null) {
                    Object nmsBlockState = getStateMethod.invoke(blockData);
                    if (nmsBlockState != null) {
                        return getBlockStateId(nmsBlockState);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to get block state ID from BlockData", e);
        }
        return getBlockStateIdFallback(blockData.getMaterial());
    }

    @Override
    public int getBlockStateId(Material material) {
        if (material == null || !material.isBlock()) return -1;
        try {
            BlockData blockData = material.createBlockData();
            return getBlockStateId(blockData);
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to get block state ID for Material: " + material, e);
            return getBlockStateIdFallback(material);
        }
    }

    @Override
    public BlockData getBlockDataFromId(int blockStateId) {
        Object nmsBlockState = resolveBlockStateFromRegistry(blockStateId);
        if (nmsBlockState == null) return null;
        try {
            if (craftBlockDataClass != null) {
                Method fromDataMethod = findMethod(craftBlockDataClass, new String[]{"fromData"}, false, blockStateClass);
                if (fromDataMethod != null) {
                    return (BlockData) fromDataMethod.invoke(null, nmsBlockState);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to convert NMS block state to Bukkit BlockData", e);
        }
        return null;
    }

    @Override
    public Material getTypeFromId(int blockStateId) {
        BlockData blockData = getBlockDataFromId(blockStateId);
        return blockData != null ? blockData.getMaterial() : Material.AIR;
    }

    private int getBlockStateIdFallback(Material material) {
        try {
            org.bukkit.World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
            if (world != null) {
                BlockData blockData = material.createBlockData();
                world.setBlockData(0, world.getMinHeight(), 0, blockData);
                int id = getBlockStateAt(world, 0, world.getMinHeight(), 0);
                return id;
            }
        } catch (Exception ignored) {
        }
        return -1;
    }

    private volatile Method craftPlayerGetHandle;
    private volatile Field playerConnectionField;
    private volatile Method sendPacketMethod;

    @Override
    public void sendPacket(Player player, Object packet) {
        if (player == null || packet == null) return;
        try {
            Object craftPlayer = craftPlayerGetHandle(player);
            if (craftPlayer == null) return;
            Object connection = getPlayerConnection(craftPlayer);
            if (connection == null) return;
            Method send = resolveSendPacketMethod(connection);
            if (send != null) {
                send.invoke(connection, packet);
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to send packet to " + player.getName(), e);
        }
    }

    private Object craftPlayerGetHandle(Player player) throws Exception {
        if (craftPlayerGetHandle == null) {
            synchronized (this) {
                if (craftPlayerGetHandle == null) {
                    Class<?> craftPlayerClass = player.getClass();
                    Method m = findMethod(craftPlayerClass,
                            new String[]{"getHandle", "a"}, true);
                    if (m != null) m.setAccessible(true);
                    craftPlayerGetHandle = m;
                }
            }
        }
        if (craftPlayerGetHandle == null) return null;
        return craftPlayerGetHandle.invoke(player);
    }

    private Object getPlayerConnection(Object nmsPlayer) throws Exception {
        if (playerConnectionField == null) {
            synchronized (this) {
                if (playerConnectionField == null) {
                    String[] connectionFieldNames = {
                            "connection", "b", "playerConnection",
                            "c", "f", "netHandler"
                    };
                    Class<?> current = nmsPlayer.getClass();
                    while (current != null && current != Object.class) {
                        for (String name : connectionFieldNames) {
                            try {
                                Field f = current.getDeclaredField(name);
                                f.setAccessible(true);
                                Object value = f.get(nmsPlayer);
                                if (value != null) {
                                    playerConnectionField = f;
                                    return value;
                                }
                            } catch (NoSuchFieldException ignored) {
                            }
                        }
                        for (Field f : current.getDeclaredFields()) {
                            String typeName = f.getType().getName();
                            if (typeName.contains("Connection") || typeName.contains("NetHandler")
                                    || typeName.contains("PlayerConnection")
                                    || typeName.contains("ServerCommonPacketListener")
                                    || typeName.contains("ServerGamePacketListener")
                                    || typeName.contains("ServerPlayPacketListener")) {
                                f.setAccessible(true);
                                Object value = f.get(nmsPlayer);
                                if (value != null) {
                                    playerConnectionField = f;
                                    return value;
                                }
                            }
                        }
                        current = current.getSuperclass();
                    }
                }
            }
        }
        if (playerConnectionField == null) return null;
        return playerConnectionField.get(nmsPlayer);
    }

    private Method resolveSendPacketMethod(Object connection) {
        if (sendPacketMethod == null) {
            synchronized (this) {
                if (sendPacketMethod == null) {
                    String[] sendNames = {"send", "a", "dispatchPacket", "consumePacket"};
                    Class<?> current = connection.getClass();
                    while (current != null && current != Object.class) {
                        for (Method m : current.getDeclaredMethods()) {
                            for (String name : sendNames) {
                                if (m.getName().equals(name) && m.getParameterCount() == 1) {
                                    Class<?> paramType = m.getParameterTypes()[0];
                                    if (paramType.getName().contains("Packet")) {
                                        m.setAccessible(true);
                                        sendPacketMethod = m;
                                        return m;
                                    }
                                }
                            }
                        }
                        current = current.getSuperclass();
                    }
                }
            }
        }
        return sendPacketMethod;
    }

	@Override
	public Object createChunkDataPacket(World world, int chunkX, int chunkZ) {
		try {
			Object nmsWorld = invokeMethod(craftWorldGetHandle, world);
			if (nmsWorld == null) return null;

			Method getChunkMethod = findMethod(nmsWorld.getClass(),
				new String[]{"a", "getChunk", "getChunkAt"}, false, int.class, int.class);
			if (getChunkMethod == null) return null;

			Object levelChunk = getChunkMethod.invoke(nmsWorld, chunkX, chunkZ);
			if (levelChunk == null || chunkDataPacketClass == null) return null;

			return chunkDataPacketClass.getConstructor(levelChunkClass, nmsLevelClass, null, null, null, null)
				.newInstance(levelChunk, nmsWorld, null, null, null, null);
		} catch (Exception e1) {
			try {
				Object nmsWorld = invokeMethod(craftWorldGetHandle, world);
				if (nmsWorld == null) return null;

				Method getChunkMethod = findMethod(nmsWorld.getClass(),
					new String[]{"a", "getChunk", "getChunkAt"}, false, int.class, int.class);
				if (getChunkMethod == null) return null;

				Object levelChunk = getChunkMethod.invoke(nmsWorld, chunkX, chunkZ);
				if (levelChunk == null || chunkDataPacketClass == null) return null;

				for (Constructor<?> ctor : chunkDataPacketClass.getConstructors()) {
					Class<?>[] params = ctor.getParameterTypes();
					if (params.length >= 2 && params[0].isAssignableFrom(levelChunk.getClass())) {
						Object[] args = new Object[params.length];
						args[0] = levelChunk;
						args[1] = nmsWorld;
						return ctor.newInstance(args);
					}
				}
			} catch (Exception e2) {
				LOGGER.log(Level.FINE, "Failed to create chunk data packet", e2);
			}
			return null;
		}
	}

	protected static Field findField(Class<?> clazz, String[] names) {
        if (clazz == null) return null;
        for (String name : names) {
            try {
                Field f = clazz.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
            }
        }
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Field f : current.getDeclaredFields()) {
                for (String name : names) {
                    if (f.getName().equals(name)) {
                        f.setAccessible(true);
                        return f;
                    }
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }
}
