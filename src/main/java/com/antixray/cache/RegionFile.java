package com.antixray.cache;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RegionFile implements AutoCloseable {

    private static final Logger LOGGER = Logger.getLogger("AntiXray");
    static final int CHUNKS_PER_SIDE = 32;
    static final int TOTAL_CHUNKS = CHUNKS_PER_SIDE * CHUNKS_PER_SIDE;
    static final int HEADER_SIZE = TOTAL_CHUNKS * 4;
    static final int HEADER_SECTORS = (HEADER_SIZE + 511) / 512;
    static final int COMPRESSION_ZLIB = 2;
    static final int CHUNK_NOT_PRESENT = 0;
    static final byte[] HEADER_FILL = new byte[HEADER_SIZE];

    private final File file;
    private RandomAccessFile raf;
    private final int[] offsets = new int[TOTAL_CHUNKS];
    private volatile boolean dirty = false;
    private volatile boolean closed = false;

    public RegionFile(File file) throws IOException {
        this.file = file;
        if (file.exists() && file.length() > 0) {
            raf = new RandomAccessFile(file, "rw");
            readHeader();
        } else {
            raf = new RandomAccessFile(file, "rw");
            raf.write(HEADER_FILL);
            raf.getFD().sync();
            Arrays.fill(offsets, CHUNK_NOT_PRESENT);
        }
    }

    private void readHeader() throws IOException {
        raf.seek(0);
        byte[] headerBytes = new byte[HEADER_SIZE];
        int totalRead = 0;
        while (totalRead < HEADER_SIZE) {
            int read = raf.read(headerBytes, totalRead, HEADER_SIZE - totalRead);
            if (read == -1) throw new EOFException("Unexpected end of region file header");
            totalRead += read;
        }
        ByteBuffer bb = ByteBuffer.wrap(headerBytes).order(ByteOrder.BIG_ENDIAN);
        for (int i = 0; i < TOTAL_CHUNKS; i++) {
            offsets[i] = bb.getInt();
        }
        dirty = false;
    }

    static int chunkIndex(int localX, int localZ) {
        return ((localZ & 31) << 5) | (localX & 31);
    }

    static int localX(int chunkX) {
        return chunkX & 31;
    }

    static int localZ(int chunkZ) {
        return chunkZ & 31;
    }

    static String fileName(int regionX, int regionZ) {
        return "r." + regionX + "." + regionZ + ".mca";
    }

    static int regionCoord(int chunkCoord) {
        return chunkCoord >> 5;
    }

    boolean hasChunk(int localX, int localZ) {
        checkOpen();
        return offsets[chunkIndex(localX, localZ)] != CHUNK_NOT_PRESENT;
    }

    synchronized ChunkData readChunk(int localX, int localZ) throws IOException {
        checkOpen();
        int index = chunkIndex(localX, localZ);
        int offset = offsets[index];
        if (offset == CHUNK_NOT_PRESENT) return null;

        try {
            raf.seek(offset);
            int length = raf.readInt();
            if (length <= 1) {
                offsets[index] = CHUNK_NOT_PRESENT;
                dirty = true;
                return null;
            }
            int compressionType = raf.readByte();
            if (compressionType != COMPRESSION_ZLIB) {
                offsets[index] = CHUNK_NOT_PRESENT;
                dirty = true;
                return null;
            }
            int dataLen = length - 1;
            if (dataLen <= 0) return null;
            byte[] compressed = new byte[dataLen];
            int totalRead = 0;
            while (totalRead < dataLen) {
                int read = raf.read(compressed, totalRead, dataLen - totalRead);
                if (read == -1) throw new EOFException("Unexpected end of chunk data");
                totalRead += read;
            }
            return ChunkData.deserialize(compressed);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to read chunk at (" + localX + "," + localZ + ") in " + file.getName(), e);
            offsets[index] = CHUNK_NOT_PRESENT;
            dirty = true;
            return null;
        }
    }

    synchronized void writeChunk(int localX, int localZ, ChunkData chunkData) throws IOException {
        checkOpen();
        int index = chunkIndex(localX, localZ);
        byte[] compressed = chunkData.serialize();

        raf.seek(raf.length());
        int offset = (int) raf.getFilePointer();
        raf.writeInt(compressed.length + 1);
        raf.writeByte(COMPRESSION_ZLIB);
        raf.write(compressed);
        raf.getFD().sync();

        offsets[index] = offset;
        dirty = true;
    }

    synchronized void deleteChunk(int localX, int localZ) throws IOException {
        checkOpen();
        int index = chunkIndex(localX, localZ);
        if (offsets[index] != CHUNK_NOT_PRESENT) {
            offsets[index] = CHUNK_NOT_PRESENT;
            dirty = true;
        }
    }

    synchronized boolean isDirty() {
        return dirty;
    }

    synchronized void flushHeader() throws IOException {
        checkOpen();
        if (!dirty) return;
        ByteBuffer bb = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.BIG_ENDIAN);
        for (int offset : offsets) {
            bb.putInt(offset);
        }
        raf.seek(0);
        raf.write(bb.array());
        raf.getFD().sync();
        dirty = false;
    }

    long getFileSize() {
        checkOpen();
        try {
            return raf.length();
        } catch (IOException e) {
            return 0;
        }
    }

    long getLastModified() {
        return file.lastModified();
    }

    void touchLastModified() {
        file.setLastModified(System.currentTimeMillis());
    }

    File getFile() {
        return file;
    }

    private void checkOpen() {
        if (closed) throw new IllegalStateException("RegionFile is closed: " + file.getName());
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        try {
            if (dirty) {
                ByteBuffer bb = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.BIG_ENDIAN);
                for (int offset : offsets) {
                    bb.putInt(offset);
                }
                raf.seek(0);
                raf.write(bb.array());
                raf.getFD().sync();
                dirty = false;
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to flush header on close for " + file.getName(), e);
        }
        try {
            raf.close();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to close region file " + file.getName(), e);
        }
    }

    static class ChunkData {
        final CacheKey key;
        final CacheEntry entry;

        ChunkData(CacheKey key, CacheEntry entry) {
            this.key = key;
            this.entry = entry;
        }

        byte[] serialize() throws IOException {
            byte[] worldBytes = key.getWorldName().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            byte[] obfData = entry.getObfuscatedData();
            int totalLen = 4 + worldBytes.length + 4 + 4 + 4 + 4 + 8 + 4 + obfData.length;
            ByteBuffer bb = ByteBuffer.allocate(totalLen).order(ByteOrder.BIG_ENDIAN);
            bb.putInt(worldBytes.length);
            bb.put(worldBytes);
            bb.putInt(key.getChunkX());
            bb.putInt(key.getChunkZ());
            bb.putInt(key.getEngineMode());
            bb.putInt(key.getConfigHash());
            bb.putLong(entry.getTimestamp());
            bb.putInt(entry.getSectionCount());
            bb.put(obfData);
            return CompressionUtil.compress(bb.array());
        }

        static ChunkData deserialize(byte[] compressed) throws IOException {
            byte[] raw = CompressionUtil.decompress(compressed);
            ByteBuffer bb = ByteBuffer.wrap(raw).order(ByteOrder.BIG_ENDIAN);
            int worldLen = bb.getInt();
            byte[] worldBytes = new byte[worldLen];
            bb.get(worldBytes);
            String worldName = new String(worldBytes, java.nio.charset.StandardCharsets.UTF_8);
            int chunkX = bb.getInt();
            int chunkZ = bb.getInt();
            int engineMode = bb.getInt();
            int configHash = bb.getInt();
            long timestamp = bb.getLong();
            int sectionCount = bb.getInt();
            byte[] obfData = new byte[bb.remaining()];
            bb.get(obfData);
            CacheKey key = new CacheKey(worldName, chunkX, chunkZ, engineMode, configHash);
            CacheEntry entry = new CacheEntry(obfData, timestamp, sectionCount);
            return new ChunkData(key, entry);
        }
    }

    static void deleteRegionFile(File file) {
        if (file == null || !file.exists()) return;
        try {
            Files.delete(file.toPath());
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to delete region file: " + file.getName(), e);
        }
    }
}
