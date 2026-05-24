package com.antixray.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BlockPositionTest {

    @Test
    void constructorSetsFields() {
        BlockPosition pos = new BlockPosition("world", 10, 64, -32);
        assertEquals("world", pos.getWorldName());
        assertEquals(10, pos.getX());
        assertEquals(64, pos.getY());
        assertEquals(-32, pos.getZ());
    }

    @Test
    void constructorRejectsNullWorld() {
        assertThrows(NullPointerException.class, () -> new BlockPosition(null, 0, 0, 0));
    }

    @Test
    void equalitySameValues() {
        BlockPosition a = new BlockPosition("world", 1, 2, 3);
        BlockPosition b = new BlockPosition("world", 1, 2, 3);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void equalityDifferentWorld() {
        BlockPosition a = new BlockPosition("world1", 1, 2, 3);
        BlockPosition b = new BlockPosition("world2", 1, 2, 3);
        assertNotEquals(a, b);
    }

    @Test
    void equalityDifferentCoords() {
        BlockPosition a = new BlockPosition("world", 1, 2, 3);
        BlockPosition b = new BlockPosition("world", 1, 2, 4);
        assertNotEquals(a, b);
    }

    @Test
    void equalityWithNull() {
        BlockPosition pos = new BlockPosition("world", 0, 0, 0);
        assertNotEquals(null, pos);
    }

    @Test
    void hashCodeConsistentAcrossCalls() {
        BlockPosition pos = new BlockPosition("world", 42, -64, 100);
        int h1 = pos.hashCode();
        int h2 = pos.hashCode();
        assertEquals(h1, h2);
    }

    @Test
    void getChunkX() {
        assertEquals(0, new BlockPosition("w", 0, 0, 0).getChunkX());
        assertEquals(1, new BlockPosition("w", 16, 0, 0).getChunkX());
        assertEquals(-1, new BlockPosition("w", -16, 0, 0).getChunkX());
        assertEquals(-1, new BlockPosition("w", -1, 0, 0).getChunkX());
    }

    @Test
    void getChunkZ() {
        assertEquals(0, new BlockPosition("w", 0, 0, 0).getChunkZ());
        assertEquals(1, new BlockPosition("w", 0, 0, 16).getChunkZ());
        assertEquals(-1, new BlockPosition("w", 0, 0, -1).getChunkZ());
    }

    @Test
    void getSectionY() {
        assertEquals(0, new BlockPosition("w", 0, 0, 0).getSectionY());
        assertEquals(1, new BlockPosition("w", 0, 16, 0).getSectionY());
        assertEquals(-4, new BlockPosition("w", 0, -64, 0).getSectionY());
    }

    @Test
    void withWorldReturnsNewInstance() {
        BlockPosition original = new BlockPosition("world1", 5, 10, 15);
        BlockPosition modified = original.withWorld("world2");
        assertEquals("world2", modified.getWorldName());
        assertEquals(5, modified.getX());
        assertEquals(10, modified.getY());
        assertEquals(15, modified.getZ());
        assertEquals("world1", original.getWorldName());
    }

    @Test
    void offsetReturnsNewInstance() {
        BlockPosition original = new BlockPosition("world", 10, 20, 30);
        BlockPosition offset = original.offset(1, -1, 5);
        assertEquals(11, offset.getX());
        assertEquals(19, offset.getY());
        assertEquals(35, offset.getZ());
        assertEquals(10, original.getX());
    }

    @Test
    void encodeToLongStatic() {
        long encoded = BlockPosition.encodeToLong(10, 64, -32);
        BlockPosition decoded = BlockPosition.fromLong(encoded, "world");
        assertEquals(10, decoded.getX());
        assertEquals(64, decoded.getY());
        assertEquals(-32, decoded.getZ());
    }

    @Test
    void encodeToLongInstance() {
        BlockPosition pos = new BlockPosition("world", 10, 64, -32);
        long encoded = pos.encodeToLong();
        BlockPosition decoded = BlockPosition.fromLong(encoded, "world");
        assertEquals(pos.getX(), decoded.getX());
        assertEquals(pos.getY(), decoded.getY());
        assertEquals(pos.getZ(), decoded.getZ());
    }

    @Test
    void encodeToLongNegativeCoords() {
        BlockPosition pos = new BlockPosition("w", -100, -64, -200);
        long encoded = pos.encodeToLong();
        BlockPosition decoded = BlockPosition.fromLong(encoded, "w");
        assertEquals(pos.getX(), decoded.getX());
        assertEquals(pos.getY(), decoded.getY());
        assertEquals(pos.getZ(), decoded.getZ());
    }

    @Test
    void toStringContainsAllFields() {
        BlockPosition pos = new BlockPosition("myworld", 1, 2, 3);
        String s = pos.toString();
        assertTrue(s.contains("myworld"));
        assertTrue(s.contains("1"));
        assertTrue(s.contains("2"));
        assertTrue(s.contains("3"));
    }

    @Test
    void immutabilityWithWorldDoesNotModifyOriginal() {
        BlockPosition pos = new BlockPosition("a", 1, 2, 3);
        pos.withWorld("b");
        assertEquals("a", pos.getWorldName());
    }

    @Test
    void immutabilityOffsetDoesNotModifyOriginal() {
        BlockPosition pos = new BlockPosition("w", 1, 2, 3);
        pos.offset(10, 10, 10);
        assertEquals(1, pos.getX());
        assertEquals(2, pos.getY());
        assertEquals(3, pos.getZ());
    }

    @Test
    void encodeToLongLargePositiveX() {
        BlockPosition pos = new BlockPosition("w", 33554431, 0, 0);
        long encoded = pos.encodeToLong();
        BlockPosition decoded = BlockPosition.fromLong(encoded, "w");
        assertEquals(33554431, decoded.getX());
        assertEquals(0, decoded.getY());
        assertEquals(0, decoded.getZ());
    }

    @Test
    void encodeToLongMinX() {
        BlockPosition pos = new BlockPosition("w", -33554432, 0, 0);
        long encoded = pos.encodeToLong();
        BlockPosition decoded = BlockPosition.fromLong(encoded, "w");
        assertEquals(-33554432, decoded.getX());
        assertEquals(0, decoded.getY());
        assertEquals(0, decoded.getZ());
    }

    @Test
    void encodeToLongXSignExtensionSymmetricWithZ() {
        BlockPosition posX = new BlockPosition("w", -1, 0, 0);
        BlockPosition posZ = new BlockPosition("w", 0, 0, -1);
        BlockPosition decodedX = BlockPosition.fromLong(posX.encodeToLong(), "w");
        BlockPosition decodedZ = BlockPosition.fromLong(posZ.encodeToLong(), "w");
        assertEquals(-1, decodedX.getX());
        assertEquals(-1, decodedZ.getZ());
    }

    @Test
    void encodeToLongBoundaryPositiveX() {
        int x = 0x1FFFFFF;
        BlockPosition pos = new BlockPosition("w", x, 0, 0);
        long encoded = pos.encodeToLong();
        BlockPosition decoded = BlockPosition.fromLong(encoded, "w");
        assertEquals(x, decoded.getX());
    }

    @Test
    void encodeToLongBoundaryNegativeX() {
        int x = -0x2000000;
        BlockPosition pos = new BlockPosition("w", x, 0, 0);
        long encoded = pos.encodeToLong();
        BlockPosition decoded = BlockPosition.fromLong(encoded, "w");
        assertEquals(x, decoded.getX());
    }
}
