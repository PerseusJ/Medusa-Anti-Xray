package com.antixray.deobfuscation;

import com.antixray.util.BlockPosition;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

public class RevealedBlocksSet {

    private final ConcurrentMap<Long, Long> entries = new ConcurrentHashMap<>();
    private final int maxSize;
    private final AtomicInteger size = new AtomicInteger(0);

    public RevealedBlocksSet(int maxSize) {
        this.maxSize = maxSize;
    }

    public void add(BlockPosition pos, long tick) {
        long key = pos.encodeToLong();
        Long previous = entries.put(key, tick);
        if (previous == null) {
            int current = size.incrementAndGet();
            if (current > maxSize) {
                evictOldest();
            }
        }
    }

    public boolean contains(BlockPosition pos) {
        return entries.containsKey(pos.encodeToLong());
    }

    public void remove(BlockPosition pos) {
        Long removed = entries.remove(pos.encodeToLong());
        if (removed != null) {
            size.decrementAndGet();
        }
    }

    public List<BlockPosition> getRevealedBeforeTickNoRemove(long tick, String worldName) {
        List<BlockPosition> result = new ArrayList<>();

        for (var entry : entries.entrySet()) {
            if (entry.getValue() < tick) {
                result.add(BlockPosition.fromLong(entry.getKey(), worldName));
            }
        }

        return result;
    }

    public List<BlockPosition> getRevealedBeforeTick(long tick, String worldName) {
        List<Long> keysToRemove = new ArrayList<>();
        List<BlockPosition> result = new ArrayList<>();

        for (var entry : entries.entrySet()) {
            if (entry.getValue() < tick) {
                keysToRemove.add(entry.getKey());
            }
        }

        for (long key : keysToRemove) {
            Long removed = entries.remove(key);
            if (removed != null) {
                size.decrementAndGet();
                result.add(BlockPosition.fromLong(key, worldName));
            }
        }

        return result;
    }

    public void clear() {
        entries.clear();
        size.set(0);
    }

    public int size() {
        return size.get();
    }

    public int getMaxSize() {
        return maxSize;
    }

    private void evictOldest() {
        long oldestKey = -1;
        long oldestTick = Long.MAX_VALUE;

        for (var entry : entries.entrySet()) {
            if (entry.getValue() < oldestTick) {
                oldestTick = entry.getValue();
                oldestKey = entry.getKey();
            }
        }

        if (oldestKey != -1) {
            if (entries.remove(oldestKey) != null) {
                size.decrementAndGet();
            } else {
                int actualSize = entries.size();
                size.set(actualSize);
            }
        }
    }
}
