package com.antixray.engine;

import java.util.List;
import java.util.Map;

public final class PaletteManipulator {

    public static final int BLOCKS_PER_SECTION = 4096;
    public static final int MIN_INDIRECT_BITS = 4;
    public static final int MAX_INDIRECT_BITS = 8;
    public static final int DIRECT_BITS = 15;

    public record CompactResult(List<Integer> palette, long[] packed, int bitsPerEntry) {}
    public record UpgradeResult(List<Integer> palette, long[] packed, int bitsPerEntry) {}

    private PaletteManipulator() {}

    public static int getIndex(long[] packed, int position, int bitsPerEntry) {
        int bitOffset = position * bitsPerEntry;
        int longIndex = bitOffset >>> 6;
        int bitStart = bitOffset & 63;
        long firstLong = packed[longIndex] >>> bitStart;
        int bitsFromFirstLong = 64 - bitStart;
        if (bitsFromFirstLong >= bitsPerEntry) {
            return (int) (firstLong & ((1L << bitsPerEntry) - 1));
        } else {
            long secondLong = packed[longIndex + 1];
            int bitsFromSecondLong = bitsPerEntry - bitsFromFirstLong;
            return (int) ((firstLong | (secondLong << bitsFromFirstLong)) & ((1L << bitsPerEntry) - 1));
        }
    }

    public static void setIndex(long[] packed, int position, int index, int bitsPerEntry) {
        int bitOffset = position * bitsPerEntry;
        int longIndex = bitOffset >>> 6;
        int bitStart = bitOffset & 63;
        long mask = (1L << bitsPerEntry) - 1;
        packed[longIndex] &= ~(mask << bitStart);
        packed[longIndex] |= ((long) index & mask) << bitStart;
        int bitsFromFirstLong = 64 - bitStart;
        if (bitsFromFirstLong < bitsPerEntry) {
            int bitsInSecondLong = bitsPerEntry - bitsFromFirstLong;
            long secondMask = (1L << bitsInSecondLong) - 1;
            packed[longIndex + 1] &= ~secondMask;
            packed[longIndex + 1] |= (long) (index >>> bitsFromFirstLong) & secondMask;
        }
    }

    public static int ensureInPalette(List<Integer> palette, int blockStateId) {
        int idx = palette.indexOf(blockStateId);
        if (idx >= 0) return idx;
        palette.add(blockStateId);
        return palette.size() - 1;
    }

    public static void remapIndex(long[] packed, int oldIndex, int newIndex, int bitsPerEntry) {
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            if (getIndex(packed, i, bitsPerEntry) == oldIndex) {
                setIndex(packed, i, newIndex, bitsPerEntry);
            }
        }
    }

    public static void remapIndices(long[] packed, Map<Integer, Integer> indexRemapping, int bitsPerEntry) {
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            int current = getIndex(packed, i, bitsPerEntry);
            Integer mapped = indexRemapping.get(current);
            if (mapped != null) {
                setIndex(packed, i, mapped, bitsPerEntry);
            }
        }
    }

    public static long[] reencode(long[] packed, int oldBitsPerEntry, int newBitsPerEntry) {
        int longsNeeded = computePackedArraySize(newBitsPerEntry);
        long[] newPacked = new long[longsNeeded];
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            setIndex(newPacked, i, getIndex(packed, i, oldBitsPerEntry), newBitsPerEntry);
        }
        return newPacked;
    }

    public static CompactResult compact(List<Integer> palette, long[] packed, int bitsPerEntry) {
        if (bitsPerEntry == 0) {
            return new CompactResult(new java.util.ArrayList<>(palette), packed, 0);
        }
        int[] refCount = new int[palette.size()];
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            refCount[getIndex(packed, i, bitsPerEntry)]++;
        }
        int[] oldToNew = new int[palette.size()];
        List<Integer> newPalette = new java.util.ArrayList<>();
        for (int i = 0; i < palette.size(); i++) {
            if (refCount[i] > 0) {
                oldToNew[i] = newPalette.size();
                newPalette.add(palette.get(i));
            }
        }
        int newBitsPerEntry = bitsPerEntry;
        if (newPalette.size() > 1) {
            newBitsPerEntry = computeRequiredBits(newPalette.size());
        } else {
            newBitsPerEntry = 0;
        }
        long[] newPacked;
        if (newBitsPerEntry == 0) {
            newPacked = new long[0];
        } else if (newBitsPerEntry == bitsPerEntry) {
            for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
                setIndex(packed, i, oldToNew[getIndex(packed, i, bitsPerEntry)], bitsPerEntry);
            }
            newPacked = packed;
        } else if (bitsPerEntry == 0) {
            int longsNeeded = computePackedArraySize(newBitsPerEntry);
            newPacked = new long[longsNeeded];
            for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
                setIndex(newPacked, i, oldToNew[0], newBitsPerEntry);
            }
        } else {
            int[] remapped = new int[BLOCKS_PER_SECTION];
            for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
                remapped[i] = oldToNew[getIndex(packed, i, bitsPerEntry)];
            }
            int longsNeeded = computePackedArraySize(newBitsPerEntry);
            newPacked = new long[longsNeeded];
            for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
                setIndex(newPacked, i, remapped[i], newBitsPerEntry);
            }
        }
        return new CompactResult(newPalette, newPacked, newBitsPerEntry);
    }

    public static UpgradeResult upgradeSingleValue(int singleValue, int replacementValue) {
        List<Integer> palette = new java.util.ArrayList<>(2);
        if (singleValue == replacementValue) {
            palette.add(singleValue);
            return new UpgradeResult(palette, new long[0], 0);
        }
        palette.add(singleValue);
        palette.add(replacementValue);
        int longsNeeded = computePackedArraySize(MIN_INDIRECT_BITS);
        long[] packed = new long[longsNeeded];
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            setIndex(packed, i, 0, MIN_INDIRECT_BITS);
        }
        return new UpgradeResult(palette, packed, MIN_INDIRECT_BITS);
    }

    public static int computeRequiredBits(int paletteSize) {
        if (paletteSize <= 1) return 0;
        return Math.max(MIN_INDIRECT_BITS, ceilLog2(paletteSize));
    }

    public static int ceilLog2(int value) {
        return 32 - Integer.numberOfLeadingZeros(value - 1);
    }

    public static int computePackedArraySize(int bitsPerEntry) {
        return (BLOCKS_PER_SECTION * bitsPerEntry + 63) / 64;
    }
}
