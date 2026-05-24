package com.antixray.cache;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class CompressionUtilTest {

    @Test
    void compress_andDecompress_roundTrip() throws IOException {
        byte[] original = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        byte[] compressed = CompressionUtil.compress(original);
        byte[] decompressed = CompressionUtil.decompress(compressed);
        assertArrayEquals(original, decompressed);
    }

    @Test
    void compress_producesSmallerOutputForRepeatedData() throws IOException {
        byte[] data = new byte[1024];
        for (int i = 0; i < data.length; i++) data[i] = 42;
        byte[] compressed = CompressionUtil.compress(data);
        assertTrue(compressed.length < data.length,
                "Repeated data should compress well: " + compressed.length + " vs " + data.length);
    }

    @Test
    void decompress_invalidData_throwsIOException() {
        byte[] garbage = {0, 1, 2, 3, 4, 5};
        assertThrows(IOException.class, () -> CompressionUtil.decompress(garbage));
    }

    @Test
    void compress_emptyArray_roundTrip() throws IOException {
        byte[] original = new byte[0];
        byte[] compressed = CompressionUtil.compress(original);
        byte[] decompressed = CompressionUtil.decompress(compressed);
        assertArrayEquals(original, decompressed);
    }

    @Test
    void compress_largeData_roundTrip() throws IOException {
        byte[] original = new byte[65536];
        new Random(42).nextBytes(original);
        byte[] compressed = CompressionUtil.compress(original);
        byte[] decompressed = CompressionUtil.decompress(compressed);
        assertArrayEquals(original, decompressed);
    }

    @Test
    void compress_singleByte_roundTrip() throws IOException {
        byte[] original = {99};
        byte[] compressed = CompressionUtil.compress(original);
        byte[] decompressed = CompressionUtil.decompress(compressed);
        assertArrayEquals(original, decompressed);
    }

    @Test
    void compress_allZeros_roundTrip() throws IOException {
        byte[] original = new byte[8192];
        byte[] compressed = CompressionUtil.compress(original);
        byte[] decompressed = CompressionUtil.decompress(compressed);
        assertArrayEquals(original, decompressed);
    }

    @Test
    void decompress_garbageData_throwsException() {
        byte[] garbage = new byte[256];
        new java.util.Random(12345).nextBytes(garbage);
        assertThrows(Exception.class, () -> CompressionUtil.decompress(garbage));
    }
}
