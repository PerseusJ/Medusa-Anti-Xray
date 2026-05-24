package com.antixray.cache;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CacheEntryTest {

    @Test
    void constructor_setsFields() {
        byte[] data = {1, 2, 3, 4, 5};
        CacheEntry entry = new CacheEntry(data, 3);
        assertEquals(5, entry.getDataLength());
        assertEquals(3, entry.getSectionCount());
        assertTrue(entry.getTimestamp() > 0);
    }

    @Test
    void constructor_withTimestamp_setsFields() {
        byte[] data = {10, 20, 30};
        CacheEntry entry = new CacheEntry(data, 1000L, 2);
        assertEquals(3, entry.getDataLength());
        assertEquals(2, entry.getSectionCount());
        assertEquals(1000L, entry.getTimestamp());
    }

    @Test
    void getObfuscatedData_returnsCopy() {
        byte[] data = {1, 2, 3};
        CacheEntry entry = new CacheEntry(data, 1);
        byte[] retrieved = entry.getObfuscatedData();
        assertArrayEquals(data, retrieved);
        retrieved[0] = 99;
        assertArrayEquals(data, entry.getObfuscatedData());
    }

    @Test
    void constructor_defensiveCopy() {
        byte[] data = {1, 2, 3};
        CacheEntry entry = new CacheEntry(data, 1);
        data[0] = 99;
        assertEquals(1, entry.getObfuscatedData()[0]);
    }

    @Test
    void constructor_withTimestamp_defensiveCopy() {
        byte[] data = {10, 20, 30};
        CacheEntry entry = new CacheEntry(data, 5000L, 1);
        data[0] = 99;
        assertEquals(10, entry.getObfuscatedData()[0]);
    }

    @Test
    void getTimestamp_reflectsCreationTime() {
        long before = System.currentTimeMillis();
        CacheEntry entry = new CacheEntry(new byte[0], 1);
        long after = System.currentTimeMillis();
        assertTrue(entry.getTimestamp() >= before);
        assertTrue(entry.getTimestamp() <= after);
    }

    @Test
    void isExpired_notExpired_false() {
        CacheEntry entry = new CacheEntry(new byte[0], System.currentTimeMillis(), 1);
        assertFalse(entry.isExpired(60_000L));
    }

    @Test
    void isExpired_expired_true() {
        long oldTimestamp = System.currentTimeMillis() - 120_000L;
        CacheEntry entry = new CacheEntry(new byte[0], oldTimestamp, 1);
        assertTrue(entry.isExpired(60_000L));
    }

    @Test
    void isExpired_exactlyAtBoundary_false() {
        long timestamp = System.currentTimeMillis() - 60_000L;
        CacheEntry entry = new CacheEntry(new byte[0], timestamp, 1);
        assertFalse(entry.isExpired(60_001L));
    }

    @Test
    void isExpired_zeroTtl_immediatelyExpired() {
        CacheEntry entry = new CacheEntry(new byte[0], System.currentTimeMillis() - 1, 1);
        assertTrue(entry.isExpired(0));
    }

    @Test
    void sectionCount_storedCorrectly() {
        CacheEntry entry1 = new CacheEntry(new byte[100], 1);
        assertEquals(1, entry1.getSectionCount());

        CacheEntry entry24 = new CacheEntry(new byte[2400], 24);
        assertEquals(24, entry24.getSectionCount());
    }

    @Test
    void emptyData_validEntry() {
        CacheEntry entry = new CacheEntry(new byte[0], 0);
        assertEquals(0, entry.getDataLength());
        assertEquals(0, entry.getSectionCount());
        assertArrayEquals(new byte[0], entry.getObfuscatedData());
    }

    @Test
    void largeData_storedCorrectly() {
        byte[] data = new byte[2048];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i & 0xFF);
        }
        CacheEntry entry = new CacheEntry(data, 16);
        assertEquals(2048, entry.getDataLength());
        assertArrayEquals(data, entry.getObfuscatedData());
    }

    @Test
    void toString_containsRelevantFields() {
        CacheEntry entry = new CacheEntry(new byte[100], 2000L, 5);
        String s = entry.toString();
        assertTrue(s.contains("5"));
        assertTrue(s.contains("100"));
        assertTrue(s.contains("2000"));
    }
}
