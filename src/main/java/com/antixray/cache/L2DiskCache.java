package com.antixray.cache;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public class L2DiskCache {

    private static final Logger LOGGER = Logger.getLogger("AntiXray");
    static final String CACHE_DIR_NAME = "cache";
    static final long DEFAULT_DISK_BUDGET_MB = 500;
    static final double BUDGET_WARNING_THRESHOLD = 0.80;

    private final File baseDir;
    private final long diskBudgetBytes;
    private final long expiryMillis;
    private final ConcurrentHashMap<File, RegionFile> openRegions = new ConcurrentHashMap<>();
    private final Set<CacheKey> dirtyEntries = ConcurrentHashMap.newKeySet();
    private final BlockingQueue<WriteTask> writeQueue = new LinkedBlockingQueue<>();
    private final ExecutorService ioThread;
    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    private volatile boolean budgetWarningLogged = false;

    public L2DiskCache(File pluginDir, long diskBudgetMB, long expirySeconds) {
        this.baseDir = new File(pluginDir, CACHE_DIR_NAME);
        this.diskBudgetBytes = diskBudgetMB * 1024L * 1024L;
        this.expiryMillis = expirySeconds * 1000L;
        this.baseDir.mkdirs();
        this.ioThread = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "AntiXray-L2-IO");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        });
        this.ioThread.submit(this::ioLoop);
    }

    CacheEntry get(CacheKey key) {
        if (shutdown.get()) return null;
        File regionFile = regionFilePath(key.getWorldName(), key.getChunkX(), key.getChunkZ());
        RegionFile rf = getOrOpenRegion(regionFile);
        if (rf == null) return null;

        try {
            int localX = RegionFile.localX(key.getChunkX());
            int localZ = RegionFile.localZ(key.getChunkZ());
            RegionFile.ChunkData chunkData = rf.readChunk(localX, localZ);
            if (chunkData == null) return null;

            CacheKey diskKey = chunkData.key;
            if (diskKey.getEngineMode() != key.getEngineMode()
                    || diskKey.getConfigHash() != key.getConfigHash()
                    || !diskKey.getWorldName().equals(key.getWorldName())) {
                return null;
            }

            if (chunkData.entry.isExpired(expiryMillis)) {
                try {
                    rf.deleteChunk(localX, localZ);
                } catch (IOException ignored) {}
                return null;
            }

            rf.touchLastModified();
            return chunkData.entry;
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "L2 read failed for " + key, e);
            return null;
        }
    }

    void put(CacheKey key, CacheEntry entry) {
        if (shutdown.get()) return;
        writeQueue.offer(new WriteTask(key, entry, WriteTask.Type.WRITE));
    }

    void invalidate(CacheKey key) {
        if (shutdown.get()) return;
        dirtyEntries.add(key);
        writeQueue.offer(new WriteTask(key, null, WriteTask.Type.DELETE));
    }

    void invalidateChunk(String worldName, int chunkX, int chunkZ) {
        if (shutdown.get()) return;
        CacheKey key = new CacheKey(worldName, chunkX, chunkZ, -1, -1);
        dirtyEntries.add(key);
        writeQueue.offer(new WriteTask(key, null, WriteTask.Type.DELETE_BY_CHUNK));
    }

    void invalidateWorld(String worldName) {
        if (shutdown.get()) return;
        writeQueue.offer(new WriteTask(new CacheKey(worldName, 0, 0, 0, 0), null, WriteTask.Type.DELETE_WORLD));
    }

    void invalidateAll() {
        if (shutdown.get()) return;
        writeQueue.offer(new WriteTask(null, null, WriteTask.Type.DELETE_ALL));
    }

    List<CacheKey> preloadRecent(L1MemoryCache l1Cache, int maxEntries) {
        if (shutdown.get()) return Collections.emptyList();
        List<RegionChunk> regionChunks = scanForRecentChunks();
        regionChunks.sort((a, b) -> Long.compare(b.lastModified, a.lastModified));

        List<CacheKey> loaded = new ArrayList<>();
        int count = 0;
        for (RegionChunk rc : regionChunks) {
            if (count >= maxEntries) break;
            RegionFile rf = getOrOpenRegion(rc.regionFile);
            if (rf == null) continue;
            for (int lz = 0; lz < RegionFile.CHUNKS_PER_SIDE; lz++) {
                if (count >= maxEntries) break;
                for (int lx = 0; lx < RegionFile.CHUNKS_PER_SIDE; lx++) {
                    if (count >= maxEntries) break;
                    if (!rf.hasChunk(lx, lz)) continue;
                    try {
                        RegionFile.ChunkData chunkData = rf.readChunk(lx, lz);
                        if (chunkData == null || chunkData.entry.isExpired(expiryMillis)) continue;
                        l1Cache.put(chunkData.key, chunkData.entry);
                        loaded.add(chunkData.key);
                        count++;
                    } catch (IOException ignored) {}
                }
            }
        }
        if (!loaded.isEmpty()) {
            LOGGER.log(Level.INFO, "L2 preloaded {0} recent chunks into L1", loaded.size());
        }
        return loaded;
    }

    private List<RegionChunk> scanForRecentChunks() {
        List<RegionChunk> result = new ArrayList<>();
        File[] worldDirs = baseDir.listFiles(File::isDirectory);
        if (worldDirs == null) return result;

        for (File worldDir : worldDirs) {
            File[] regionFiles = worldDir.listFiles((dir, name) -> name.endsWith(".mca"));
            if (regionFiles == null) continue;

            for (File rf : regionFiles) {
                if (rf.lastModified() < System.currentTimeMillis() - expiryMillis) continue;
                result.add(new RegionChunk(rf, rf.lastModified()));
            }
        }
        return result;
    }

    void shutdown() {
        if (!shutdown.compareAndSet(false, true)) return;
        writeQueue.offer(new WriteTask(null, null, WriteTask.Type.SHUTDOWN));
        ioThread.shutdown();
        try {
            if (!ioThread.awaitTermination(10, TimeUnit.SECONDS)) {
                ioThread.shutdownNow();
            }
        } catch (InterruptedException e) {
            ioThread.shutdownNow();
            Thread.currentThread().interrupt();
        }
        flushAllRegionHeaders();
        closeAllRegions();
    }

    private void ioLoop() {
        while (!Thread.currentThread().isInterrupted() && !shutdown.get()) {
            try {
                List<WriteTask> batch = new ArrayList<>();
                WriteTask first = writeQueue.poll(100, TimeUnit.MILLISECONDS);
                if (first == null) continue;
                batch.add(first);
                writeQueue.drainTo(batch);

                for (WriteTask task : batch) {
                    try {
                        processWriteTask(task);
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "L2 IO task failed", e);
                    }
                }

                sweepDirtyEntries();

                if (!batch.isEmpty()) {
                    flushDirtyRegionHeaders();
                }

                checkDiskBudget();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        List<WriteTask> remaining = new ArrayList<>();
        writeQueue.drainTo(remaining);
        for (WriteTask task : remaining) {
            if (task.type == WriteTask.Type.SHUTDOWN) continue;
            try {
                processWriteTask(task);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "L2 IO final task failed", e);
            }
        }
        flushDirtyRegionHeaders();
    }

    private void processWriteTask(WriteTask task) throws IOException {
        switch (task.type) {
            case WRITE -> {
                if (task.key == null || task.entry == null) return;
                CacheKey key = task.key;
                File regionPath = regionFilePath(key.getWorldName(), key.getChunkX(), key.getChunkZ());
                RegionFile rf = getOrOpenRegion(regionPath);
                if (rf == null) return;
                int localX = RegionFile.localX(key.getChunkX());
                int localZ = RegionFile.localZ(key.getChunkZ());
                RegionFile.ChunkData chunkData = new RegionFile.ChunkData(key, task.entry);
                rf.writeChunk(localX, localZ, chunkData);
                dirtyEntries.remove(key);
            }
            case DELETE -> {
                if (task.key == null) return;
                CacheKey key = task.key;
                deleteChunkFromRegion(key.getWorldName(), key.getChunkX(), key.getChunkZ());
                dirtyEntries.remove(key);
            }
            case DELETE_BY_CHUNK -> {
                if (task.key == null) return;
                deleteChunkFromRegion(task.key.getWorldName(), task.key.getChunkX(), task.key.getChunkZ());
                dirtyEntries.removeIf(k ->
                        k.getWorldName().equals(task.key.getWorldName())
                                && k.getChunkX() == task.key.getChunkX()
                                && k.getChunkZ() == task.key.getChunkZ());
            }
            case DELETE_WORLD -> {
                if (task.key == null) return;
                deleteWorldFiles(task.key.getWorldName());
            }
            case DELETE_ALL -> deleteAllFiles();
            case SHUTDOWN -> {}
        }
    }

    private void deleteChunkFromRegion(String worldName, int chunkX, int chunkZ) throws IOException {
        File regionPath = regionFilePath(worldName, chunkX, chunkZ);
        RegionFile rf = openRegions.get(regionPath);
        if (rf == null) {
            if (!regionPath.exists()) return;
            rf = getOrOpenRegion(regionPath);
            if (rf == null) return;
        }
        rf.deleteChunk(RegionFile.localX(chunkX), RegionFile.localZ(chunkZ));
    }

    private void deleteWorldFiles(String worldName) {
        closeRegionsForWorld(worldName);
        File worldDir = new File(baseDir, worldName);
        File[] files = worldDir.listFiles((dir, name) -> name.endsWith(".mca"));
        if (files != null) {
            for (File f : files) {
                RegionFile.deleteRegionFile(f);
            }
        }
        dirtyEntries.removeIf(k -> k.getWorldName().equals(worldName));
        LOGGER.log(Level.INFO, "L2 cache cleared for world: {0}", worldName);
    }

    private void deleteAllFiles() {
        closeAllRegions();
        File[] worldDirs = baseDir.listFiles(File::isDirectory);
        if (worldDirs != null) {
            for (File worldDir : worldDirs) {
                File[] files = worldDir.listFiles((dir, name) -> name.endsWith(".mca"));
                if (files != null) {
                    for (File f : files) RegionFile.deleteRegionFile(f);
                }
            }
        }
        dirtyEntries.clear();
        LOGGER.log(Level.INFO, "L2 cache fully cleared");
    }

    private void sweepDirtyEntries() {
        if (dirtyEntries.isEmpty()) return;
        Set<CacheKey> snapshot = new HashSet<>(dirtyEntries);
        for (CacheKey key : snapshot) {
            try {
                deleteChunkFromRegion(key.getWorldName(), key.getChunkX(), key.getChunkZ());
                dirtyEntries.remove(key);
            } catch (IOException e) {
                LOGGER.log(Level.FINE, "L2 dirty sweep failed for " + key, e);
            }
        }
    }

    private void checkDiskBudget() {
        long totalSize = computeTotalDiskUsage();
        double usageRatio = (double) totalSize / diskBudgetBytes;

        if (usageRatio >= BUDGET_WARNING_THRESHOLD && !budgetWarningLogged) {
            budgetWarningLogged = true;
            LOGGER.log(Level.WARNING, "L2 disk cache at {0}% of budget ({1} MB / {2} MB)",
                    new Object[]{(int) (usageRatio * 100), totalSize / (1024 * 1024), diskBudgetBytes / (1024 * 1024)});
        }
        if (usageRatio < BUDGET_WARNING_THRESHOLD * 0.9) {
            budgetWarningLogged = false;
        }

        if (totalSize > diskBudgetBytes) {
            evictOldestRegions();
        }
    }

    private void evictOldestRegions() {
        List<File> allRegionFiles = collectAllRegionFiles();
        allRegionFiles.sort((a, b) -> Long.compare(a.lastModified(), b.lastModified()));

        long totalSize = computeTotalDiskUsage();
        for (File regionFile : allRegionFiles) {
            if (totalSize <= diskBudgetBytes) break;
            long fileSize = regionFile.length();
            closeRegionIfExists(regionFile);
            RegionFile.deleteRegionFile(regionFile);
            totalSize -= fileSize;
            LOGGER.log(Level.INFO, "L2 evicted region file: {0}", regionFile.getName());
        }
    }

    private long computeTotalDiskUsage() {
        long total = 0;
        File[] worldDirs = baseDir.listFiles(File::isDirectory);
        if (worldDirs == null) return 0;
        for (File worldDir : worldDirs) {
            File[] files = worldDir.listFiles();
            if (files == null) continue;
            for (File f : files) total += f.length();
        }
        return total;
    }

    private List<File> collectAllRegionFiles() {
        List<File> result = new ArrayList<>();
        File[] worldDirs = baseDir.listFiles(File::isDirectory);
        if (worldDirs == null) return result;
        for (File worldDir : worldDirs) {
            File[] files = worldDir.listFiles((dir, name) -> name.endsWith(".mca"));
            if (files != null) result.addAll(Arrays.asList(files));
        }
        return result;
    }

    private RegionFile getOrOpenRegion(File regionPath) {
        return openRegions.computeIfAbsent(regionPath, p -> {
            try {
                p.getParentFile().mkdirs();
                return new RegionFile(p);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to open region file: " + p.getName(), e);
                return null;
            }
        });
    }

    private File regionFilePath(String worldName, int chunkX, int chunkZ) {
        int regionX = RegionFile.regionCoord(chunkX);
        int regionZ = RegionFile.regionCoord(chunkZ);
        return new File(new File(baseDir, worldName), RegionFile.fileName(regionX, regionZ));
    }

    private void closeRegionIfExists(File regionPath) {
        RegionFile rf = openRegions.remove(regionPath);
        if (rf != null) rf.close();
    }

    private void closeRegionsForWorld(String worldName) {
        List<File> toClose = new ArrayList<>();
        for (Map.Entry<File, RegionFile> entry : openRegions.entrySet()) {
            File f = entry.getKey();
            if (f.getParentFile() != null && f.getParentFile().getName().equals(worldName)) {
                toClose.add(f);
            }
        }
        for (File f : toClose) {
            RegionFile rf = openRegions.remove(f);
            if (rf != null) rf.close();
        }
    }

    private void flushDirtyRegionHeaders() {
        for (RegionFile rf : openRegions.values()) {
            if (rf.isDirty()) {
                try {
                    rf.flushHeader();
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Failed to flush region header", e);
                }
            }
        }
    }

    private void flushAllRegionHeaders() {
        for (RegionFile rf : openRegions.values()) {
            try {
                rf.flushHeader();
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to flush region header on shutdown", e);
            }
        }
    }

    private void closeAllRegions() {
        List<RegionFile> toClose = new ArrayList<>(openRegions.values());
        openRegions.clear();
        for (RegionFile rf : toClose) {
            rf.close();
        }
    }

    static int[] parseRegionCoords(String fileName) {
        if (!fileName.startsWith("r.") || !fileName.endsWith(".mca")) return null;
        String mid = fileName.substring(2, fileName.length() - 4);
        int dotIdx = mid.indexOf('.');
        if (dotIdx < 0) return null;
        try {
            int rx = Integer.parseInt(mid.substring(0, dotIdx));
            int rz = Integer.parseInt(mid.substring(dotIdx + 1));
            return new int[]{rx, rz};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    int getWriteQueueSize() {
        return writeQueue.size();
    }

    int getDirtyCount() {
        return dirtyEntries.size();
    }

    File getBaseDir() {
        return baseDir;
    }

    long getDiskBudgetBytes() {
        return diskBudgetBytes;
    }

    long getExpiryMillis() {
        return expiryMillis;
    }

    Map<File, RegionFile> getOpenRegions() {
        return Collections.unmodifiableMap(openRegions);
    }

    private static class WriteTask {
        enum Type { WRITE, DELETE, DELETE_BY_CHUNK, DELETE_WORLD, DELETE_ALL, SHUTDOWN }

        final CacheKey key;
        final CacheEntry entry;
        final Type type;

        WriteTask(CacheKey key, CacheEntry entry, Type type) {
            this.key = key;
            this.entry = entry;
            this.type = type;
        }
    }

    private static class RegionChunk {
        final File regionFile;
        final long lastModified;

        RegionChunk(File regionFile, long lastModified) {
            this.regionFile = regionFile;
            this.lastModified = lastModified;
        }
    }
}
