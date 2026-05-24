package com.antixray.cache;

import com.antixray.engine.ObfuscationMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CacheKeyTest {

    @Test
    void constructor_intFields_storesValues() {
        CacheKey key = new CacheKey("world", 1, 2, 3, 999);
        assertEquals("world", key.getWorldName());
        assertEquals(1, key.getChunkX());
        assertEquals(2, key.getChunkZ());
        assertEquals(3, key.getEngineMode());
        assertEquals(999, key.getConfigHash());
    }

    @Test
    void constructor_obfuscationMode_convertsToOrdinal() {
        CacheKey key1 = new CacheKey("world", 0, 0, ObfuscationMode.MODE_1, 0);
        assertEquals(1, key1.getEngineMode());

        CacheKey key2 = new CacheKey("world", 0, 0, ObfuscationMode.MODE_2, 0);
        assertEquals(2, key2.getEngineMode());

        CacheKey key3 = new CacheKey("world", 0, 0, ObfuscationMode.MODE_3, 0);
        assertEquals(3, key3.getEngineMode());
    }

    @Test
    void constructor_nullWorldName_throwsNPE() {
        assertThrows(NullPointerException.class, () -> new CacheKey(null, 0, 0, 1, 0));
    }

    @Test
    void equals_sameValues_true() {
        CacheKey a = new CacheKey("world", 5, 10, 3, 1234);
        CacheKey b = new CacheKey("world", 5, 10, 3, 1234);
        assertEquals(a, b);
    }

    @Test
    void equals_differentWorld_false() {
        CacheKey a = new CacheKey("world_a", 5, 10, 3, 1234);
        CacheKey b = new CacheKey("world_b", 5, 10, 3, 1234);
        assertNotEquals(a, b);
    }

    @Test
    void equals_differentChunkX_false() {
        CacheKey a = new CacheKey("world", 5, 10, 3, 1234);
        CacheKey b = new CacheKey("world", 6, 10, 3, 1234);
        assertNotEquals(a, b);
    }

    @Test
    void equals_differentChunkZ_false() {
        CacheKey a = new CacheKey("world", 5, 10, 3, 1234);
        CacheKey b = new CacheKey("world", 5, 11, 3, 1234);
        assertNotEquals(a, b);
    }

    @Test
    void equals_differentEngineMode_false() {
        CacheKey a = new CacheKey("world", 5, 10, 2, 1234);
        CacheKey b = new CacheKey("world", 5, 10, 3, 1234);
        assertNotEquals(a, b);
    }

    @Test
    void equals_differentConfigHash_false() {
        CacheKey a = new CacheKey("world", 5, 10, 3, 1234);
        CacheKey b = new CacheKey("world", 5, 10, 3, 5678);
        assertNotEquals(a, b);
    }

    @Test
    void equals_null_false() {
        CacheKey a = new CacheKey("world", 0, 0, 1, 0);
        assertNotEquals(null, a);
    }

    @Test
    void equals_differentType_false() {
        CacheKey a = new CacheKey("world", 0, 0, 1, 0);
        assertNotEquals("not a key", a);
    }

    @Test
    void equals_reflexive() {
        CacheKey key = new CacheKey("world", 1, 2, 3, 100);
        assertEquals(key, key);
    }

    @Test
    void equals_symmetric() {
        CacheKey a = new CacheKey("world", 1, 2, 3, 100);
        CacheKey b = new CacheKey("world", 1, 2, 3, 100);
        assertEquals(a, b);
        assertEquals(b, a);
    }

    @Test
    void equals_transitive() {
        CacheKey a = new CacheKey("world", 1, 2, 3, 100);
        CacheKey b = new CacheKey("world", 1, 2, 3, 100);
        CacheKey c = new CacheKey("world", 1, 2, 3, 100);
        assertEquals(a, b);
        assertEquals(b, c);
        assertEquals(a, c);
    }

    @Test
    void hashCode_equalKeys_sameHash() {
        CacheKey a = new CacheKey("world", 5, 10, 3, 1234);
        CacheKey b = new CacheKey("world", 5, 10, 3, 1234);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void hashCode_differentKeys_differentHash() {
        CacheKey a = new CacheKey("world_a", 1, 2, 1, 100);
        CacheKey b = new CacheKey("world_b", 3, 4, 2, 200);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void hashCode_consistentAcrossCalls() {
        CacheKey key = new CacheKey("world", 1, 2, 3, 999);
        int h1 = key.hashCode();
        int h2 = key.hashCode();
        assertEquals(h1, h2);
    }

    @Test
    void hashCode_usedInHashMap() {
        java.util.HashMap<CacheKey, String> map = new java.util.HashMap<>();
        CacheKey key = new CacheKey("world", 5, 10, 3, 1234);
        map.put(key, "value");
        CacheKey lookup = new CacheKey("world", 5, 10, 3, 1234);
        assertEquals("value", map.get(lookup));
    }

    @Test
    void configHashChange_preventsLookup() {
        java.util.HashMap<CacheKey, String> map = new java.util.HashMap<>();
        CacheKey key = new CacheKey("world", 5, 10, 3, 1234);
        map.put(key, "old");
        CacheKey newConfigKey = new CacheKey("world", 5, 10, 3, 5678);
        assertNull(map.get(newConfigKey));
    }

    @Test
    void negativeChunkCoords_valid() {
        CacheKey key = new CacheKey("world", -1, -2, 1, 0);
        assertEquals(-1, key.getChunkX());
        assertEquals(-2, key.getChunkZ());
    }

    @Test
    void largeChunkCoords_valid() {
        CacheKey key = new CacheKey("world", Integer.MAX_VALUE, Integer.MIN_VALUE, 3, 0);
        assertEquals(Integer.MAX_VALUE, key.getChunkX());
        assertEquals(Integer.MIN_VALUE, key.getChunkZ());
    }

    @Test
    void toString_containsAllFields() {
        CacheKey key = new CacheKey("myworld", 5, 10, 3, 1234);
        String s = key.toString();
        assertTrue(s.contains("myworld"));
        assertTrue(s.contains("5"));
        assertTrue(s.contains("10"));
        assertTrue(s.contains("3"));
        assertTrue(s.contains("1234"));
    }

    @Test
    void obfuscationModeConstructor_matchesIntConstructor() {
        CacheKey fromMode = new CacheKey("world", 1, 2, ObfuscationMode.MODE_1, 100);
        CacheKey fromInt = new CacheKey("world", 1, 2, 1, 100);
        assertEquals(fromMode, fromInt);
        assertEquals(fromMode.hashCode(), fromInt.hashCode());
    }
}
