package com.antixray.util;

import java.util.Objects;

public final class BlockPosition {

    private final String worldName;
    private final int x;
    private final int y;
    private final int z;
    private final int cachedHashCode;

    public BlockPosition(String worldName, int x, int y, int z) {
        this.worldName = Objects.requireNonNull(worldName, "worldName");
        this.x = x;
        this.y = y;
        this.z = z;
        this.cachedHashCode = computeHashCode();
    }

    public String getWorldName() {
        return worldName;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public int getChunkX() {
        return x >> 4;
    }

    public int getChunkZ() {
        return z >> 4;
    }

    public int getSectionY() {
        return y >> 4;
    }

    public BlockPosition withWorld(String newWorldName) {
        return new BlockPosition(newWorldName, x, y, z);
    }

    public BlockPosition offset(int dx, int dy, int dz) {
        return new BlockPosition(worldName, x + dx, y + dy, z + dz);
    }

    public long encodeToLong() {
        return encodeToLong(x, y, z);
    }

    public static long encodeToLong(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38)
                | ((long) (z & 0x3FFFFFF) << 12)
                | ((long) (y & 0xFFF));
    }

    public static BlockPosition fromLong(long encoded, String worldName) {
        int x = (int) (encoded >> 38);
        if (x >= 0x2000000) x -= 0x4000000;
        int z = (int) ((encoded >> 12) & 0x3FFFFFF);
        if (z >= 0x2000000) z -= 0x4000000;
        int y = (int) (encoded & 0xFFF);
        if (y >= 0x800) y -= 0x1000;
        return new BlockPosition(worldName, x, y, z);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BlockPosition that)) return false;
        return x == that.x && y == that.y && z == that.z && worldName.equals(that.worldName);
    }

    @Override
    public int hashCode() {
        return cachedHashCode;
    }

    private int computeHashCode() {
        int result = worldName.hashCode();
        result = 31 * result + x;
        result = 31 * result + y;
        result = 31 * result + z;
        return result;
    }

    @Override
    public String toString() {
        return "BlockPosition{world='" + worldName + "', x=" + x + ", y=" + y + ", z=" + z + "}";
    }
}
