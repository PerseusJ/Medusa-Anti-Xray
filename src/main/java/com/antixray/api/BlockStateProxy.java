package com.antixray.api;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Utility to create a thread-safe proxy for Bukkit's BlockState.
 * This allows ObfuscationProviders to check blocks asynchronously without calling thread-unsafe Bukkit APIs.
 */
public class BlockStateProxy {

    /**
     * Create a thread-safe BlockState proxy.
     *
     * @param world the world
     * @param x coordinate x
     * @param y coordinate y
     * @param z coordinate z
     * @param type the Material type
     * @param blockData the BlockData
     * @return a proxy implementing BlockState
     */
    public static BlockState create(World world, int x, int y, int z, Material type, BlockData blockData) {
        return (BlockState) Proxy.newProxyInstance(
                BlockStateProxy.class.getClassLoader(),
                new Class<?>[]{BlockState.class},
                new BlockStateInvocationHandler(world, x, y, z, type, blockData)
        );
    }

    private static class BlockStateInvocationHandler implements InvocationHandler {
        private final World world;
        private final int x;
        private final int y;
        private final int z;
        private final Material type;
        private final BlockData blockData;

        public BlockStateInvocationHandler(World world, int x, int y, int z, Material type, BlockData blockData) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.type = type;
            this.blockData = blockData;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            switch (method.getName()) {
                case "getType":
                    return type;
                case "getBlockData":
                    return blockData;
                case "getWorld":
                    return world;
                case "getX":
                    return x;
                case "getY":
                    return y;
                case "getZ":
                    return z;
                case "getLocation":
                    if (args != null && args.length == 1 && args[0] instanceof Location) {
                        Location loc = (Location) args[0];
                        loc.setWorld(world);
                        loc.setX(x);
                        loc.setY(y);
                        loc.setZ(z);
                        return loc;
                    }
                    return new Location(world, x, y, z);
                case "isPlaced":
                    return true;
                case "equals":
                    if (args != null && args.length == 1) {
                        Object other = args[0];
                        if (other instanceof BlockState) {
                            BlockState bs = (BlockState) other;
                            return bs.getWorld().equals(world) && bs.getX() == x && bs.getY() == y && bs.getZ() == z;
                        }
                    }
                    return false;
                case "hashCode":
                    return world.hashCode() ^ x ^ y ^ z;
                case "toString":
                    return "BlockStateProxy{world=" + world.getName() + ",x=" + x + ",y=" + y + ",z=" + z + ",type=" + type + "}";
                default:
                    throw new UnsupportedOperationException("Method " + method.getName() + " is not supported on BlockStateProxy");
            }
        }
    }
}
