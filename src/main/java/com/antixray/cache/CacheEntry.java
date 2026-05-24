package com.antixray.cache;

import java.util.Arrays;

public final class CacheEntry {

    private final byte[] obfuscatedData;
    private final long timestamp;
    private final int sectionCount;

    public CacheEntry(byte[] obfuscatedData, int sectionCount) {
        this.obfuscatedData = obfuscatedData.clone();
        this.timestamp = System.currentTimeMillis();
        this.sectionCount = sectionCount;
    }

    public CacheEntry(byte[] obfuscatedData, long timestamp, int sectionCount) {
        this.obfuscatedData = obfuscatedData.clone();
        this.timestamp = timestamp;
        this.sectionCount = sectionCount;
    }

    public byte[] getObfuscatedData() {
        return obfuscatedData.clone();
    }

    public long getTimestamp() {
        return timestamp;
    }

    public int getSectionCount() {
        return sectionCount;
    }

    public boolean isExpired(long ttlMillis) {
        return System.currentTimeMillis() - timestamp > ttlMillis;
    }

    public int getDataLength() {
        return obfuscatedData.length;
    }

    @Override
    public String toString() {
        return "CacheEntry{sections=" + sectionCount
                + ", dataLen=" + obfuscatedData.length
                + ", timestamp=" + timestamp + '}';
    }
}
