package com.antixray.cache;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class RegionFileTest {

    @TempDir
    File tempDir;

    private RegionFile regionFile;

    @BeforeEach
    void setUp() throws IOException {
        File file = new File(tempDir, "r.0.0.mca");
        regionFile = new RegionFile(file);
    }

    @AfterEach
    void tearDown() {
        if (regionFile != null) {
            regionFile.close();
        }
    }

    @Test
    void constructor_createsNewFile() {
        assertTrue(regionFile.getFile().exists());
        assertTrue(regionFile.getFile().length() >= RegionFile.HEADER_SIZE);
    }

    @Test
    void constructor_existingFileWithValidHeader() throws IOException {
        CacheKey key = new CacheKey("world", 0, 0, 1, 0);
        CacheEntry entry = new CacheEntry(new byte[]{1, 2, 3}, 1);
        RegionFile.ChunkData chunkData = new RegionFile.ChunkData(key, entry);
        regionFile.writeChunk(0, 0, chunkData);
        regionFile.close();

        File file = new File(tempDir, "r.0.0.mca");
        regionFile = new RegionFile(file);
        assertTrue(regionFile.getFile().exists());
    }

    @Test
    void chunkIndex_validCoords() {
        assertEquals(0, RegionFile.chunkIndex(0, 0));
        assertEquals(1, RegionFile.chunkIndex(1, 0));
        assertEquals(32, RegionFile.chunkIndex(0, 1));
        assertEquals(1023, RegionFile.chunkIndex(31, 31));
    }

    @Test
    void chunkIndex_wrapsAround() {
        assertEquals(0, RegionFile.chunkIndex(32, 0));
        assertEquals(0, RegionFile.chunkIndex(0, 32));
    }

    @Test
    void localX_localZ_extractsCorrectValues() {
        assertEquals(0, RegionFile.localX(0));
        assertEquals(31, RegionFile.localX(31));
        assertEquals(0, RegionFile.localX(32));
        assertEquals(1, RegionFile.localX(33));
        assertEquals(31, RegionFile.localX(-1));

        assertEquals(0, RegionFile.localZ(0));
        assertEquals(31, RegionFile.localZ(31));
        assertEquals(0, RegionFile.localZ(32));
    }

    @Test
    void regionCoord_convertsCorrectly() {
        assertEquals(0, RegionFile.regionCoord(0));
        assertEquals(0, RegionFile.regionCoord(31));
        assertEquals(1, RegionFile.regionCoord(32));
        assertEquals(-1, RegionFile.regionCoord(-1));
        assertEquals(-1, RegionFile.regionCoord(-32));
        assertEquals(-2, RegionFile.regionCoord(-33));
    }

    @Test
    void fileName_formatCorrect() {
        assertEquals("r.0.0.mca", RegionFile.fileName(0, 0));
        assertEquals("r.1.-1.mca", RegionFile.fileName(1, -1));
    }

    @Test
    void hasChunk_initiallyFalse() {
        assertFalse(regionFile.hasChunk(0, 0));
        assertFalse(regionFile.hasChunk(5, 10));
    }

    @Test
    void writeChunk_andReadChunk_roundTrip() throws IOException {
        CacheKey key = new CacheKey("world", 0, 0, 1, 100);
        CacheEntry entry = new CacheEntry(new byte[]{10, 20, 30}, 3);
        RegionFile.ChunkData chunkData = new RegionFile.ChunkData(key, entry);

        regionFile.writeChunk(0, 0, chunkData);

        RegionFile.ChunkData read = regionFile.readChunk(0, 0);
        assertNotNull(read);
        assertEquals(0, read.key.getChunkX());
        assertEquals(0, read.key.getChunkZ());
        assertEquals(1, read.key.getEngineMode());
        assertEquals(100, read.key.getConfigHash());
        assertArrayEquals(new byte[]{10, 20, 30}, read.entry.getObfuscatedData());
        assertEquals(3, read.entry.getSectionCount());
    }

    @Test
    void hasChunk_trueAfterWrite() throws IOException {
        CacheKey key = new CacheKey("world", 0, 0, 1, 0);
        regionFile.writeChunk(0, 0, new RegionFile.ChunkData(key, new CacheEntry(new byte[]{1}, 1)));
        assertTrue(regionFile.hasChunk(0, 0));
    }

    @Test
    void readChunk_missingChunk_returnsNull() throws IOException {
        assertNull(regionFile.readChunk(5, 10));
    }

    @Test
    void writeChunk_multipleChunksInSameRegion() throws IOException {
        for (int lx = 0; lx < 5; lx++) {
            for (int lz = 0; lz < 5; lz++) {
                CacheKey key = new CacheKey("world", lx, lz, 1, 0);
                byte[] data = {(byte) (lx * 5 + lz)};
                regionFile.writeChunk(lx, lz, new RegionFile.ChunkData(key, new CacheEntry(data, 1)));
            }
        }

        for (int lx = 0; lx < 5; lx++) {
            for (int lz = 0; lz < 5; lz++) {
                RegionFile.ChunkData read = regionFile.readChunk(lx, lz);
                assertNotNull(read, "Chunk at (" + lx + "," + lz + ") should exist");
                assertEquals(lx * 5 + lz, read.entry.getObfuscatedData()[0]);
            }
        }
    }

    @Test
    void deleteChunk_removesChunk() throws IOException {
        CacheKey key = new CacheKey("world", 5, 5, 1, 0);
        regionFile.writeChunk(5, 5, new RegionFile.ChunkData(key, new CacheEntry(new byte[]{1}, 1)));
        assertNotNull(regionFile.readChunk(5, 5));

        regionFile.deleteChunk(5, 5);
        assertNull(regionFile.readChunk(5, 5));
        assertFalse(regionFile.hasChunk(5, 5));
    }

    @Test
    void deleteChunk_nonExistent_noException() {
        assertDoesNotThrow(() -> regionFile.deleteChunk(0, 0));
    }

    @Test
    void overwriteChunk_replacesData() throws IOException {
        CacheKey key = new CacheKey("world", 0, 0, 1, 0);
        regionFile.writeChunk(0, 0, new RegionFile.ChunkData(key, new CacheEntry(new byte[]{1}, 1)));

        regionFile.writeChunk(0, 0, new RegionFile.ChunkData(key, new CacheEntry(new byte[]{2, 3}, 2)));

        RegionFile.ChunkData read = regionFile.readChunk(0, 0);
        assertNotNull(read);
        assertArrayEquals(new byte[]{2, 3}, read.entry.getObfuscatedData());
        assertEquals(2, read.entry.getSectionCount());
    }

    @Test
    void writeChunk_largeData_roundTrip() throws IOException {
        byte[] largeData = new byte[16384];
        for (int i = 0; i < largeData.length; i++) {
            largeData[i] = (byte) (i & 0xFF);
        }
        CacheKey key = new CacheKey("world", 0, 0, 1, 0);
        regionFile.writeChunk(0, 0, new RegionFile.ChunkData(key, new CacheEntry(largeData, 24)));

        RegionFile.ChunkData read = regionFile.readChunk(0, 0);
        assertNotNull(read);
        assertArrayEquals(largeData, read.entry.getObfuscatedData());
        assertEquals(24, read.entry.getSectionCount());
    }

    @Test
    void flushHeader_persistsOffsets() throws IOException {
        CacheKey key = new CacheKey("world", 0, 0, 1, 0);
        regionFile.writeChunk(0, 0, new RegionFile.ChunkData(key, new CacheEntry(new byte[]{1}, 1)));
        regionFile.flushHeader();
        regionFile.close();

        File file = new File(tempDir, "r.0.0.mca");
        regionFile = new RegionFile(file);

        RegionFile.ChunkData read = regionFile.readChunk(0, 0);
        assertNotNull(read, "Chunk should be readable after flush and reopen");
        assertEquals(1, read.entry.getObfuscatedData()[0]);
    }

    @Test
    void close_flushesDirtyHeader() throws IOException {
        CacheKey key = new CacheKey("world", 0, 0, 1, 0);
        regionFile.writeChunk(0, 0, new RegionFile.ChunkData(key, new CacheEntry(new byte[]{1}, 1)));
        assertTrue(regionFile.isDirty());
        regionFile.close();

        File file = new File(tempDir, "r.0.0.mca");
        regionFile = new RegionFile(file);
        assertNotNull(regionFile.readChunk(0, 0));
    }

    @Test
    void close_onAlreadyClosed_noException() {
        regionFile.close();
        assertDoesNotThrow(() -> regionFile.close());
    }

    @Test
    void operations_onClosedFile_throwsIllegalState() throws IOException {
        regionFile.close();
        assertThrows(IllegalStateException.class, () -> regionFile.readChunk(0, 0));
    }

    @Test
    void getFile_returnsCorrectFile() {
        assertEquals(new File(tempDir, "r.0.0.mca"), regionFile.getFile());
    }

    @Test
    void getFileSize_increasesAfterWrite() throws IOException {
        long initialSize = regionFile.getFileSize();
        CacheKey key = new CacheKey("world", 0, 0, 1, 0);
        regionFile.writeChunk(0, 0, new RegionFile.ChunkData(key, new CacheEntry(new byte[1024], 1)));
        assertTrue(regionFile.getFileSize() > initialSize);
    }

    @Test
    void deleteRegionFile_removesFile() throws IOException {
        File file = new File(tempDir, "r.1.0.mca");
        RegionFile rf = new RegionFile(file);
        rf.close();
        assertTrue(file.exists());

        RegionFile.deleteRegionFile(file);
        assertFalse(file.exists());
    }

    @Test
    void deleteRegionFile_nonExistentFile_noException() {
        File nonExistent = new File(tempDir, "r.99.99.mca");
        assertDoesNotThrow(() -> RegionFile.deleteRegionFile(nonExistent));
    }

    @Test
    void chunkData_serializeDeserialize_roundTrip() throws IOException {
        CacheKey key = new CacheKey("world", 5, 10, 3, 999);
        CacheEntry entry = new CacheEntry(new byte[]{1, 2, 3, 4, 5}, 3);
        RegionFile.ChunkData original = new RegionFile.ChunkData(key, entry);

        byte[] serialized = original.serialize();
        RegionFile.ChunkData restored = RegionFile.ChunkData.deserialize(serialized);

        assertEquals(key.getChunkX(), restored.key.getChunkX());
        assertEquals(key.getChunkZ(), restored.key.getChunkZ());
        assertEquals(key.getEngineMode(), restored.key.getEngineMode());
        assertEquals(key.getConfigHash(), restored.key.getConfigHash());
        assertArrayEquals(entry.getObfuscatedData(), restored.entry.getObfuscatedData());
        assertEquals(entry.getSectionCount(), restored.entry.getSectionCount());
        assertEquals(entry.getTimestamp(), restored.entry.getTimestamp());
    }

    @Test
    void chunkData_emptyData_roundTrip() throws IOException {
        CacheKey key = new CacheKey("world", 0, 0, 1, 0);
        CacheEntry entry = new CacheEntry(new byte[0], 0);
        RegionFile.ChunkData original = new RegionFile.ChunkData(key, entry);

        byte[] serialized = original.serialize();
        RegionFile.ChunkData restored = RegionFile.ChunkData.deserialize(serialized);

        assertEquals(0, restored.entry.getObfuscatedData().length);
        assertEquals(0, restored.entry.getSectionCount());
    }

    @Test
    void writeAndRead_negativeChunkCoords() throws IOException {
        CacheKey key = new CacheKey("world", -1, -1, 1, 0);
        CacheEntry entry = new CacheEntry(new byte[]{42}, 1);
        regionFile.writeChunk(RegionFile.localX(-1), RegionFile.localZ(-1),
                new RegionFile.ChunkData(key, entry));

        RegionFile.ChunkData read = regionFile.readChunk(RegionFile.localX(-1), RegionFile.localZ(-1));
        assertNotNull(read);
        assertEquals(-1, read.key.getChunkX());
        assertEquals(-1, read.key.getChunkZ());
        assertEquals(42, read.entry.getObfuscatedData()[0]);
    }

    @Test
    void writeAndRead_timestampPreserved() throws IOException {
        long specificTimestamp = 1700000000000L;
        CacheKey key = new CacheKey("world", 0, 0, 1, 0);
        CacheEntry entry = new CacheEntry(new byte[]{1}, specificTimestamp, 1);
        regionFile.writeChunk(0, 0, new RegionFile.ChunkData(key, entry));

        RegionFile.ChunkData read = regionFile.readChunk(0, 0);
        assertNotNull(read);
        assertEquals(specificTimestamp, read.entry.getTimestamp());
    }
}
