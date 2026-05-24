package com.antixray.engine;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class PaletteManipulatorTest {

    private static final int BLOCKS_PER_SECTION = PaletteManipulator.BLOCKS_PER_SECTION;

    private long[] buildPacked(int bitsPerEntry) {
        return new long[PaletteManipulator.computePackedArraySize(bitsPerEntry)];
    }

    // ======================== getIndex / setIndex roundtrips ========================

    @Test
    void getIndex_setIndex_roundtrip_4bit() {
        long[] packed = buildPacked(4);
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            PaletteManipulator.setIndex(packed, i, i % 16, 4);
        }
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            assertEquals(i % 16, PaletteManipulator.getIndex(packed, i, 4),
                    "Mismatch at position " + i);
        }
    }

    @Test
    void getIndex_setIndex_roundtrip_5bit() {
        long[] packed = buildPacked(5);
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            PaletteManipulator.setIndex(packed, i, i % 32, 5);
        }
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            assertEquals(i % 32, PaletteManipulator.getIndex(packed, i, 5),
                    "Mismatch at position " + i);
        }
    }

    @Test
    void getIndex_setIndex_roundtrip_6bit() {
        long[] packed = buildPacked(6);
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            PaletteManipulator.setIndex(packed, i, i % 64, 6);
        }
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            assertEquals(i % 64, PaletteManipulator.getIndex(packed, i, 6),
                    "Mismatch at position " + i);
        }
    }

    @Test
    void getIndex_setIndex_roundtrip_7bit() {
        long[] packed = buildPacked(7);
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            PaletteManipulator.setIndex(packed, i, i % 128, 7);
        }
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            assertEquals(i % 128, PaletteManipulator.getIndex(packed, i, 7),
                    "7-bit mismatch at position " + i);
        }
    }

    @Test
    void getIndex_setIndex_roundtrip_8bit() {
        long[] packed = buildPacked(8);
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            PaletteManipulator.setIndex(packed, i, i % 256, 8);
        }
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            assertEquals(i % 256, PaletteManipulator.getIndex(packed, i, 8),
                    "Mismatch at position " + i);
        }
    }

    @Test
    void getIndex_setIndex_roundtrip_9bit() {
        long[] packed = buildPacked(9);
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            PaletteManipulator.setIndex(packed, i, i % 512, 9);
        }
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            assertEquals(i % 512, PaletteManipulator.getIndex(packed, i, 9),
                    "9-bit mismatch at position " + i);
        }
    }

    @Test
    void getIndex_setIndex_roundtrip_15bit() {
        long[] packed = buildPacked(15);
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            PaletteManipulator.setIndex(packed, i, i % 32768, 15);
        }
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            assertEquals(i % 32768, PaletteManipulator.getIndex(packed, i, 15),
                    "Mismatch at position " + i);
        }
    }

    // ======================== Boundary positions ========================

    @Test
    void setIndex_allZero_thenSetPosition0() {
        long[] packed = buildPacked(4);
        PaletteManipulator.setIndex(packed, 0, 7, 4);
        assertEquals(7, PaletteManipulator.getIndex(packed, 0, 4));
        assertEquals(0, PaletteManipulator.getIndex(packed, 1, 4));
    }

    @Test
    void setIndex_position4095_boundary() {
        long[] packed = buildPacked(4);
        PaletteManipulator.setIndex(packed, 4095, 15, 4);
        assertEquals(15, PaletteManipulator.getIndex(packed, 4095, 4));
    }

    @Test
    void edge_position0_and_position4095() {
        long[] packed = buildPacked(4);
        PaletteManipulator.setIndex(packed, 0, 1, 4);
        PaletteManipulator.setIndex(packed, 4095, 14, 4);
        assertEquals(1, PaletteManipulator.getIndex(packed, 0, 4));
        assertEquals(14, PaletteManipulator.getIndex(packed, 4095, 4));
        assertEquals(0, PaletteManipulator.getIndex(packed, 2048, 4));
    }

    @Test
    void setIndex_position4095_5bit() {
        long[] packed = buildPacked(5);
        PaletteManipulator.setIndex(packed, 4095, 31, 5);
        assertEquals(31, PaletteManipulator.getIndex(packed, 4095, 5));
    }

    @Test
    void setIndex_position4095_15bit() {
        long[] packed = buildPacked(15);
        PaletteManipulator.setIndex(packed, 4095, 12345, 15);
        assertEquals(12345, PaletteManipulator.getIndex(packed, 4095, 15));
    }

    // ======================== Long boundary crossing ========================

    @Test
    void setIndex_crossLongBoundary_4bit() {
        long[] packed = buildPacked(4);
        int pos = 15;
        PaletteManipulator.setIndex(packed, pos, 13, 4);
        assertEquals(13, PaletteManipulator.getIndex(packed, pos, 4));
    }

    @Test
    void setIndex_crossLongBoundary_5bit() {
        long[] packed = buildPacked(5);
        for (int pos : new int[]{12, 13, 25, 26}) {
            PaletteManipulator.setIndex(packed, pos, 31, 5);
            assertEquals(31, PaletteManipulator.getIndex(packed, pos, 5));
        }
    }

    @Test
    void setIndex_crossLongBoundary_7bit() {
        long[] packed = buildPacked(7);
        int pos = 9;
        PaletteManipulator.setIndex(packed, pos, 127, 7);
        assertEquals(127, PaletteManipulator.getIndex(packed, pos, 7));
        assertEquals(0, PaletteManipulator.getIndex(packed, pos - 1, 7));
        assertEquals(0, PaletteManipulator.getIndex(packed, pos + 1, 7));
    }

    @Test
    void setIndex_crossLongBoundary_15bit() {
        long[] packed = buildPacked(15);
        int pos = 4;
        PaletteManipulator.setIndex(packed, pos, 0x7FFF, 15);
        assertEquals(0x7FFF, PaletteManipulator.getIndex(packed, pos, 15));
        assertEquals(0, PaletteManipulator.getIndex(packed, pos - 1, 15));
        assertEquals(0, PaletteManipulator.getIndex(packed, pos + 1, 15));
    }

    @Test
    void setIndex_doesNotCorruptAdjacent_4bit() {
        long[] packed = buildPacked(4);
        PaletteManipulator.setIndex(packed, 0, 5, 4);
        PaletteManipulator.setIndex(packed, 1, 9, 4);
        assertEquals(5, PaletteManipulator.getIndex(packed, 0, 4));
        assertEquals(9, PaletteManipulator.getIndex(packed, 1, 4));
        PaletteManipulator.setIndex(packed, 0, 0, 4);
        assertEquals(0, PaletteManipulator.getIndex(packed, 0, 4));
        assertEquals(9, PaletteManipulator.getIndex(packed, 1, 4));
    }

    @Test
    void setIndex_doesNotCorruptAdjacent_5bit() {
        long[] packed = buildPacked(5);
        PaletteManipulator.setIndex(packed, 12, 31, 5);
        PaletteManipulator.setIndex(packed, 13, 17, 5);
        PaletteManipulator.setIndex(packed, 11, 5, 5);
        assertEquals(5, PaletteManipulator.getIndex(packed, 11, 5));
        assertEquals(31, PaletteManipulator.getIndex(packed, 12, 5));
        assertEquals(17, PaletteManipulator.getIndex(packed, 13, 5));
    }

    // ======================== Max value at all bit widths ========================

    @Test
    void setIndex_maxValue_4bit() {
        long[] packed = buildPacked(4);
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            PaletteManipulator.setIndex(packed, i, 15, 4);
        }
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            assertEquals(15, PaletteManipulator.getIndex(packed, i, 4));
        }
    }

    @Test
    void setIndex_maxValue_5bit() {
        long[] packed = buildPacked(5);
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            PaletteManipulator.setIndex(packed, i, 31, 5);
        }
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            assertEquals(31, PaletteManipulator.getIndex(packed, i, 5));
        }
    }

    @Test
    void setIndex_maxValue_6bit() {
        long[] packed = buildPacked(6);
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            PaletteManipulator.setIndex(packed, i, 63, 6);
        }
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            assertEquals(63, PaletteManipulator.getIndex(packed, i, 6));
        }
    }

    @Test
    void setIndex_maxValue_7bit() {
        long[] packed = buildPacked(7);
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            PaletteManipulator.setIndex(packed, i, 127, 7);
        }
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            assertEquals(127, PaletteManipulator.getIndex(packed, i, 7));
        }
    }

    @Test
    void setIndex_maxValue_8bit() {
        long[] packed = buildPacked(8);
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            PaletteManipulator.setIndex(packed, i, 255, 8);
        }
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            assertEquals(255, PaletteManipulator.getIndex(packed, i, 8));
        }
    }

    @Test
    void setIndex_maxValue_15bit() {
        long[] packed = buildPacked(15);
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            PaletteManipulator.setIndex(packed, i, 0x7FFF, 15);
        }
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            assertEquals(0x7FFF, PaletteManipulator.getIndex(packed, i, 15));
        }
    }

    // ======================== Overwrite ========================

    @Test
    void setIndex_overwritePreviousValue() {
        long[] packed = buildPacked(4);
        PaletteManipulator.setIndex(packed, 0, 5, 4);
        assertEquals(5, PaletteManipulator.getIndex(packed, 0, 4));
        PaletteManipulator.setIndex(packed, 0, 10, 4);
        assertEquals(10, PaletteManipulator.getIndex(packed, 0, 4));
    }

    @Test
    void setIndex_overwrite_preservesAdjacent_5bit() {
        long[] packed = buildPacked(5);
        for (int i = 0; i < 20; i++) {
            PaletteManipulator.setIndex(packed, i, i, 5);
        }
        PaletteManipulator.setIndex(packed, 10, 0, 5);
        assertEquals(0, PaletteManipulator.getIndex(packed, 10, 5));
        assertEquals(9, PaletteManipulator.getIndex(packed, 9, 5));
        assertEquals(11, PaletteManipulator.getIndex(packed, 11, 5));
    }

    // ======================== Random read/write ========================

    @Test
    void randomReadWrite_allPositions_4bit() {
        Random rng = new Random(42);
        long[] packed = buildPacked(4);
        int[] expected = new int[BLOCKS_PER_SECTION];
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            expected[i] = rng.nextInt(16);
            PaletteManipulator.setIndex(packed, i, expected[i], 4);
        }
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            assertEquals(expected[i], PaletteManipulator.getIndex(packed, i, 4),
                    "Mismatch at random position " + i);
        }
    }

    @Test
    void randomReadWrite_allPositions_5bit() {
        Random rng = new Random(123);
        long[] packed = buildPacked(5);
        int[] expected = new int[BLOCKS_PER_SECTION];
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            expected[i] = rng.nextInt(32);
            PaletteManipulator.setIndex(packed, i, expected[i], 5);
        }
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            assertEquals(expected[i], PaletteManipulator.getIndex(packed, i, 5),
                    "Mismatch at random position " + i);
        }
    }

    @Test
    void randomReadWrite_allPositions_7bit() {
        Random rng = new Random(456);
        long[] packed = buildPacked(7);
        int[] expected = new int[BLOCKS_PER_SECTION];
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            expected[i] = rng.nextInt(128);
            PaletteManipulator.setIndex(packed, i, expected[i], 7);
        }
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            assertEquals(expected[i], PaletteManipulator.getIndex(packed, i, 7),
                    "Mismatch at random position " + i);
        }
    }

    // ======================== Stress tests ========================

    @Test
    void stress_randomSetAndGet_4bit() {
        Random rng = new Random(999);
        long[] packed = buildPacked(4);
        int[] expected = new int[BLOCKS_PER_SECTION];
        for (int op = 0; op < 50000; op++) {
            int pos = rng.nextInt(BLOCKS_PER_SECTION);
            int val = rng.nextInt(16);
            expected[pos] = val;
            PaletteManipulator.setIndex(packed, pos, val, 4);
        }
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            assertEquals(expected[i], PaletteManipulator.getIndex(packed, i, 4),
                    "Stress test mismatch at position " + i);
        }
    }

    @Test
    void stress_randomSetAndGet_5bit() {
        Random rng = new Random(7777);
        long[] packed = buildPacked(5);
        int[] expected = new int[BLOCKS_PER_SECTION];
        for (int op = 0; op < 50000; op++) {
            int pos = rng.nextInt(BLOCKS_PER_SECTION);
            int val = rng.nextInt(32);
            expected[pos] = val;
            PaletteManipulator.setIndex(packed, pos, val, 5);
        }
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            assertEquals(expected[i], PaletteManipulator.getIndex(packed, i, 5),
                    "Stress test mismatch at position " + i);
        }
    }

    @Test
    void stress_randomSetAndGet_8bit() {
        Random rng = new Random(31415);
        long[] packed = buildPacked(8);
        int[] expected = new int[BLOCKS_PER_SECTION];
        for (int op = 0; op < 50000; op++) {
            int pos = rng.nextInt(BLOCKS_PER_SECTION);
            int val = rng.nextInt(256);
            expected[pos] = val;
            PaletteManipulator.setIndex(packed, pos, val, 8);
        }
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            assertEquals(expected[i], PaletteManipulator.getIndex(packed, i, 8),
                    "Stress test mismatch at position " + i);
        }
    }

    // ======================== ensureInPalette ========================

    @Test
    void ensureInPalette_existingEntry_returnsIndex() {
        List<Integer> palette = new ArrayList<>(Arrays.asList(100, 200, 300));
        assertEquals(1, PaletteManipulator.ensureInPalette(palette, 200));
        assertEquals(3, palette.size());
    }

    @Test
    void ensureInPalette_newEntry_appendsAndReturnsIndex() {
        List<Integer> palette = new ArrayList<>(Arrays.asList(100, 200, 300));
        assertEquals(3, PaletteManipulator.ensureInPalette(palette, 400));
        assertEquals(4, palette.size());
        assertEquals(400, palette.get(3));
    }

    @Test
    void ensureInPalette_emptyPalette_appends() {
        List<Integer> palette = new ArrayList<>();
        assertEquals(0, PaletteManipulator.ensureInPalette(palette, 100));
        assertEquals(1, palette.size());
        assertEquals(100, palette.get(0));
    }

    @Test
    void ensureInPalette_duplicateCall_idempotent() {
        List<Integer> palette = new ArrayList<>(Arrays.asList(100));
        assertEquals(0, PaletteManipulator.ensureInPalette(palette, 100));
        assertEquals(0, PaletteManipulator.ensureInPalette(palette, 100));
        assertEquals(1, palette.size());
    }

    @Test
    void ensureInPalette_multipleAppends() {
        List<Integer> palette = new ArrayList<>();
        assertEquals(0, PaletteManipulator.ensureInPalette(palette, 10));
        assertEquals(1, PaletteManipulator.ensureInPalette(palette, 20));
        assertEquals(2, PaletteManipulator.ensureInPalette(palette, 30));
        assertEquals(3, palette.size());
        assertEquals(Arrays.asList(10, 20, 30), palette);
    }

    @Test
    void ensureInPalette_existingFirstElement() {
        List<Integer> palette = new ArrayList<>(Arrays.asList(50, 60, 70));
        assertEquals(0, PaletteManipulator.ensureInPalette(palette, 50));
        assertEquals(3, palette.size());
    }

    @Test
    void ensureInPalette_existingLastElement() {
        List<Integer> palette = new ArrayList<>(Arrays.asList(50, 60, 70));
        assertEquals(2, PaletteManipulator.ensureInPalette(palette, 70));
        assertEquals(3, palette.size());
    }

    // ======================== remapIndex ========================

    @Test
    void remapIndex_singleValue() {
        long[] packed = buildPacked(4);
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            PaletteManipulator.setIndex(packed, i, 3, 4);
        }
        PaletteManipulator.setIndex(packed, 100, 5, 4);

        PaletteManipulator.remapIndex(packed, 3, 7, 4);

        assertEquals(7, PaletteManipulator.getIndex(packed, 0, 4));
        assertEquals(7, PaletteManipulator.getIndex(packed, 99, 4));
        assertEquals(5, PaletteManipulator.getIndex(packed, 100, 4));
        assertEquals(7, PaletteManipulator.getIndex(packed, 101, 4));
    }

    @Test
    void remapIndex_noMatches_noChange() {
        long[] packed = buildPacked(4);
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            PaletteManipulator.setIndex(packed, i, 1, 4);
        }
        PaletteManipulator.remapIndex(packed, 5, 7, 4);
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            assertEquals(1, PaletteManipulator.getIndex(packed, i, 4));
        }
    }

    @Test
    void remapIndex_identityRemap_noChange() {
        long[] packed = buildPacked(4);
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            PaletteManipulator.setIndex(packed, i, 3, 4);
        }
        PaletteManipulator.remapIndex(packed, 3, 3, 4);
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            assertEquals(3, PaletteManipulator.getIndex(packed, i, 4));
        }
    }

    @Test
    void remapIndex_5bit() {
        long[] packed = buildPacked(5);
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            PaletteManipulator.setIndex(packed, i, i % 32, 5);
        }
        PaletteManipulator.remapIndex(packed, 5, 31, 5);
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            int expected = (i % 32 == 5) ? 31 : (i % 32);
            assertEquals(expected, PaletteManipulator.getIndex(packed, i, 5),
                    "Mismatch at position " + i);
        }
    }

    // ======================== remapIndices ========================

    @Test
    void remapIndices_batchRemap() {
        long[] packed = buildPacked(4);
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            PaletteManipulator.setIndex(packed, i, i % 3, 4);
        }
        Map<Integer, Integer> remapping = new HashMap<>();
        remapping.put(0, 5);
        remapping.put(1, 6);
        remapping.put(2, 7);

        PaletteManipulator.remapIndices(packed, remapping, 4);

        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            int original = i % 3;
            int expected = remapping.get(original);
            assertEquals(expected, PaletteManipulator.getIndex(packed, i, 4),
                    "Mismatch at position " + i);
        }
    }

    @Test
    void remapIndices_partialRemap() {
        long[] packed = buildPacked(4);
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            PaletteManipulator.setIndex(packed, i, i % 4, 4);
        }
        Map<Integer, Integer> remapping = new HashMap<>();
        remapping.put(1, 9);

        PaletteManipulator.remapIndices(packed, remapping, 4);

        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            int original = i % 4;
            if (original == 1) {
                assertEquals(9, PaletteManipulator.getIndex(packed, i, 4));
            } else {
                assertEquals(original, PaletteManipulator.getIndex(packed, i, 4));
            }
        }
    }

    @Test
    void remapIndices_emptyMap_noChange() {
        long[] packed = buildPacked(4);
        PaletteManipulator.setIndex(packed, 0, 5, 4);
        PaletteManipulator.remapIndices(packed, Collections.emptyMap(), 4);
        assertEquals(5, PaletteManipulator.getIndex(packed, 0, 4));
    }

    @Test
    void remapIndices_multipleRemaps_5bit() {
        long[] packed = buildPacked(5);
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            PaletteManipulator.setIndex(packed, i, i % 5, 5);
        }
        Map<Integer, Integer> remapping = new HashMap<>();
        remapping.put(0, 10);
        remapping.put(2, 20);
        remapping.put(4, 30);

        PaletteManipulator.remapIndices(packed, remapping, 5);

        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            int original = i % 5;
            if (remapping.containsKey(original)) {
                assertEquals(remapping.get(original), PaletteManipulator.getIndex(packed, i, 5),
                        "Mismatch at position " + i);
            } else {
                assertEquals(original, PaletteManipulator.getIndex(packed, i, 5),
                        "Unmapped value changed at position " + i);
            }
        }
    }

    // ======================== reencode ========================

    @Test
    void reencode_4bit_to_5bit() {
        long[] packed4 = buildPacked(4);
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            PaletteManipulator.setIndex(packed4, i, i % 16, 4);
        }
        long[] packed5 = PaletteManipulator.reencode(packed4, 4, 5);
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            assertEquals(i % 16, PaletteManipulator.getIndex(packed5, i, 5),
                    "Mismatch at position " + i);
        }
    }

    @Test
    void reencode_5bit_to_6bit() {
        long[] packed5 = buildPacked(5);
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            PaletteManipulator.setIndex(packed5, i, i % 32, 5);
        }
        long[] packed6 = PaletteManipulator.reencode(packed5, 5, 6);
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            assertEquals(i % 32, PaletteManipulator.getIndex(packed6, i, 6),
                    "Mismatch at position " + i);
        }
    }

    @Test
    void reencode_4bit_to_8bit() {
        long[] packed4 = buildPacked(4);
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            PaletteManipulator.setIndex(packed4, i, i % 16, 4);
        }
        long[] packed8 = PaletteManipulator.reencode(packed4, 4, 8);
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            assertEquals(i % 16, PaletteManipulator.getIndex(packed8, i, 8),
                    "Mismatch at position " + i);
        }
    }

    @Test
    void reencode_4bit_to_6bit_roundtrip() {
        long[] packed4 = buildPacked(4);
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            PaletteManipulator.setIndex(packed4, i, (i * 7 + 3) % 16, 4);
        }
        long[] packed6 = PaletteManipulator.reencode(packed4, 4, 6);
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            assertEquals((i * 7 + 3) % 16, PaletteManipulator.getIndex(packed6, i, 6),
                    "4->6 reencode mismatch at position " + i);
        }
    }

    @Test
    void reencode_sameBits_identity() {
        long[] packed = buildPacked(4);
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            PaletteManipulator.setIndex(packed, i, i % 16, 4);
        }
        long[] reencoded = PaletteManipulator.reencode(packed, 4, 4);
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            assertEquals(i % 16, PaletteManipulator.getIndex(reencoded, i, 4),
                    "Mismatch at position " + i);
        }
    }

    @Test
    void reencode_largerArraySize() {
        long[] small = buildPacked(4);
        long[] large = PaletteManipulator.reencode(small, 4, 8);
        assertEquals(PaletteManipulator.computePackedArraySize(8), large.length);
    }

    @Test
    void reencode_6bit_to_4bit_truncationAwareness() {
        long[] packed6 = buildPacked(6);
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            PaletteManipulator.setIndex(packed6, i, i % 16, 6);
        }
        long[] packed4 = PaletteManipulator.reencode(packed6, 6, 4);
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            assertEquals(i % 16, PaletteManipulator.getIndex(packed4, i, 4));
        }
    }

    @Test
    void reencode_4bit_to_15bit() {
        long[] packed4 = buildPacked(4);
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            PaletteManipulator.setIndex(packed4, i, i % 16, 4);
        }
        long[] packed15 = PaletteManipulator.reencode(packed4, 4, 15);
        assertEquals(PaletteManipulator.computePackedArraySize(15), packed15.length);
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            assertEquals(i % 16, PaletteManipulator.getIndex(packed15, i, 15),
                    "4->15 reencode mismatch at position " + i);
        }
    }

    @Test
    void reencode_8bit_to_4bit() {
        long[] packed8 = buildPacked(8);
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            PaletteManipulator.setIndex(packed8, i, i % 16, 8);
        }
        long[] packed4 = PaletteManipulator.reencode(packed8, 8, 4);
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            assertEquals(i % 16, PaletteManipulator.getIndex(packed4, i, 4));
        }
    }

    // ======================== compact ========================

    @Test
    void compact_removesUnreferencedEntries() {
        List<Integer> palette = new ArrayList<>(Arrays.asList(100, 200, 300));
        long[] packed = buildPacked(4);
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            PaletteManipulator.setIndex(packed, i, 0, 4);
        }
        PaletteManipulator.setIndex(packed, 0, 2, 4);

        PaletteManipulator.CompactResult result = PaletteManipulator.compact(palette, packed, 4);

        assertEquals(2, result.palette().size());
        assertTrue(result.palette().contains(100));
        assertTrue(result.palette().contains(300));
        assertFalse(result.palette().contains(200));

        assertEquals(0, PaletteManipulator.getIndex(result.packed(), 1, result.bitsPerEntry()));
        assertEquals(1, PaletteManipulator.getIndex(result.packed(), 0, result.bitsPerEntry()));
    }

    @Test
    void compact_allSameEntry_reducesPalette() {
        List<Integer> palette = new ArrayList<>(Arrays.asList(100, 200, 300));
        long[] packed = buildPacked(4);
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            PaletteManipulator.setIndex(packed, i, 1, 4);
        }

        PaletteManipulator.CompactResult result = PaletteManipulator.compact(palette, packed, 4);

        assertEquals(1, result.palette().size());
        assertEquals(200, result.palette().get(0));
        assertEquals(0, result.bitsPerEntry());
        assertEquals(0, result.packed().length);
    }

    @Test
    void compact_noUnreferenced_noChange() {
        List<Integer> palette = new ArrayList<>(Arrays.asList(100, 200));
        long[] packed = buildPacked(4);
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            PaletteManipulator.setIndex(packed, i, i % 2, 4);
        }

        PaletteManipulator.CompactResult result = PaletteManipulator.compact(palette, packed, 4);

        assertEquals(2, result.palette().size());
        assertEquals(4, result.bitsPerEntry());
    }

    @Test
    void compact_allEntriesDifferent() {
        List<Integer> palette = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            palette.add(100 + i);
        }
        long[] packed = buildPacked(4);
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            PaletteManipulator.setIndex(packed, i, i % 16, 4);
        }

        PaletteManipulator.CompactResult result = PaletteManipulator.compact(palette, packed, 4);

        assertEquals(16, result.palette().size());
        assertEquals(4, result.bitsPerEntry());
    }

    @Test
    void compact_shrinksBitsPerEntry() {
        List<Integer> palette = new ArrayList<>();
        for (int i = 0; i < 32; i++) {
            palette.add(100 + i);
        }
        long[] packed = buildPacked(5);
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            PaletteManipulator.setIndex(packed, i, i % 2, 5);
        }

        PaletteManipulator.CompactResult result = PaletteManipulator.compact(palette, packed, 5);

        assertEquals(2, result.palette().size());
        assertEquals(PaletteManipulator.MIN_INDIRECT_BITS, result.bitsPerEntry());
    }

    @Test
    void compact_preservesBlockStateIds() {
        List<Integer> palette = new ArrayList<>(Arrays.asList(500, 600, 700));
        long[] packed = buildPacked(4);
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            PaletteManipulator.setIndex(packed, i, 0, 4);
        }
        PaletteManipulator.setIndex(packed, 50, 2, 4);
        PaletteManipulator.setIndex(packed, 100, 2, 4);

        PaletteManipulator.CompactResult result = PaletteManipulator.compact(palette, packed, 4);

        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            int idx = PaletteManipulator.getIndex(result.packed(), i, result.bitsPerEntry());
            int blockStateId = result.palette().get(idx);
            if (i == 50 || i == 100) {
                assertEquals(700, blockStateId);
            } else {
                assertEquals(500, blockStateId);
            }
        }
    }

    @Test
    void compact_fromZeroBits_singleValuePalette() {
        List<Integer> palette = new ArrayList<>(Arrays.asList(42));
        long[] packed = new long[0];

        PaletteManipulator.CompactResult result = PaletteManipulator.compact(palette, packed, 0);

        assertEquals(1, result.palette().size());
        assertEquals(42, result.palette().get(0));
        assertEquals(0, result.bitsPerEntry());
    }

    @Test
    void compact_singleEntry_bitsPerEntryZero() {
        List<Integer> palette = new ArrayList<>(Arrays.asList(42));
        long[] packed = buildPacked(4);
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            PaletteManipulator.setIndex(packed, i, 0, 4);
        }

        PaletteManipulator.CompactResult result = PaletteManipulator.compact(palette, packed, 4);

        assertEquals(1, result.palette().size());
        assertEquals(42, result.palette().get(0));
        assertEquals(0, result.bitsPerEntry());
        assertEquals(0, result.packed().length);
    }

    @Test
    void compact_expandsBitsPerEntry_whenPaletteGrowsBeyondCurrent() {
        List<Integer> palette = new ArrayList<>(Arrays.asList(100, 200));
        long[] packed = buildPacked(4);
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            PaletteManipulator.setIndex(packed, i, i % 2, 4);
        }

        palette.add(300);
        palette.add(400);
        palette.add(500);
        palette.add(600);
        palette.add(700);
        palette.add(800);
        palette.add(900);
        palette.add(1000);
        palette.add(1100);
        palette.add(1200);
        palette.add(1300);
        palette.add(1400);
        palette.add(1500);
        palette.add(1600);
        palette.add(1700);
        for (int i = 0; i < 16; i++) {
            PaletteManipulator.setIndex(packed, i * 256, i, 4);
        }

        PaletteManipulator.CompactResult result = PaletteManipulator.compact(palette, packed, 4);

        assertEquals(16, result.palette().size());
        assertEquals(4, result.bitsPerEntry());
        for (int i = 0; i < 16; i++) {
            int idx = PaletteManipulator.getIndex(result.packed(), i * 256, result.bitsPerEntry());
            assertEquals(100 + i * 100, result.palette().get(idx));
        }
    }

    @Test
    void compact_remapDoesNotCorrupt_5bit_to_4bit() {
        List<Integer> palette = new ArrayList<>();
        for (int i = 0; i < 32; i++) {
            palette.add(500 + i);
        }
        long[] packed = buildPacked(5);
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            PaletteManipulator.setIndex(packed, i, i % 2, 5);
        }

        PaletteManipulator.CompactResult result = PaletteManipulator.compact(palette, packed, 5);

        assertEquals(2, result.palette().size());
        assertTrue(result.palette().contains(500));
        assertTrue(result.palette().contains(501));
        assertEquals(4, result.bitsPerEntry());
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            int idx = PaletteManipulator.getIndex(result.packed(), i, result.bitsPerEntry());
            int blockStateId = result.palette().get(idx);
            assertEquals((i % 2 == 0) ? 500 : 501, blockStateId,
                    "Block state mismatch at position " + i);
        }
    }

    @Test
    void compact_remapDoesNotCorrupt_8bit_to_4bit() {
        List<Integer> palette = new ArrayList<>();
        for (int i = 0; i < 256; i++) {
            palette.add(1000 + i);
        }
        long[] packed = buildPacked(8);
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            PaletteManipulator.setIndex(packed, i, i % 3, 8);
        }

        PaletteManipulator.CompactResult result = PaletteManipulator.compact(palette, packed, 8);

        assertEquals(3, result.palette().size());
        assertEquals(4, result.bitsPerEntry());
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            int idx = PaletteManipulator.getIndex(result.packed(), i, result.bitsPerEntry());
            int blockStateId = result.palette().get(idx);
            assertEquals(1000 + (i % 3), blockStateId,
                    "Block state mismatch at position " + i);
        }
    }

    @Test
    void compact_multipleUnreferenced_reindexesCorrectly() {
        List<Integer> palette = new ArrayList<>(Arrays.asList(10, 20, 30, 40, 50));
        long[] packed = buildPacked(4);
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            PaletteManipulator.setIndex(packed, i, 0, 4);
        }
        PaletteManipulator.setIndex(packed, 0, 2, 4);
        PaletteManipulator.setIndex(packed, 1, 4, 4);

        PaletteManipulator.CompactResult result = PaletteManipulator.compact(palette, packed, 4);

        assertEquals(3, result.palette().size());
        assertTrue(result.palette().contains(10));
        assertTrue(result.palette().contains(30));
        assertTrue(result.palette().contains(50));
        assertFalse(result.palette().contains(20));
        assertFalse(result.palette().contains(40));

        assertEquals(result.palette().indexOf(30),
                PaletteManipulator.getIndex(result.packed(), 0, result.bitsPerEntry()));
        assertEquals(result.palette().indexOf(50),
                PaletteManipulator.getIndex(result.packed(), 1, result.bitsPerEntry()));
        assertEquals(result.palette().indexOf(10),
                PaletteManipulator.getIndex(result.packed(), 2, result.bitsPerEntry()));
    }

    // ======================== upgradeSingleValue ========================

    @Test
    void upgradeSingleValue_createsCorrectPalette() {
        int singleValue = 500;
        int replacementValue = 100;

        PaletteManipulator.UpgradeResult result = PaletteManipulator.upgradeSingleValue(singleValue, replacementValue);

        assertEquals(2, result.palette().size());
        assertEquals(500, result.palette().get(0));
        assertEquals(100, result.palette().get(1));
    }

    @Test
    void upgradeSingleValue_allPositionsDefaultToSingleValue() {
        int singleValue = 500;
        int replacementValue = 100;

        PaletteManipulator.UpgradeResult result = PaletteManipulator.upgradeSingleValue(singleValue, replacementValue);

        assertEquals(PaletteManipulator.MIN_INDIRECT_BITS, result.bitsPerEntry());
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            assertEquals(0, PaletteManipulator.getIndex(result.packed(), i, result.bitsPerEntry()),
                    "Position " + i + " should index palette entry 0 (the single value)");
        }
    }

    @Test
    void upgradeSingleValue_canSetReplacementIndex() {
        int singleValue = 500;
        int replacementValue = 100;

        PaletteManipulator.UpgradeResult result = PaletteManipulator.upgradeSingleValue(singleValue, replacementValue);

        PaletteManipulator.setIndex(result.packed(), 0, 1, result.bitsPerEntry());
        assertEquals(1, PaletteManipulator.getIndex(result.packed(), 0, result.bitsPerEntry()));
        assertEquals(replacementValue, result.palette().get(1));
    }

    @Test
    void upgradeSingleValue_correctPackedArraySize() {
        PaletteManipulator.UpgradeResult result = PaletteManipulator.upgradeSingleValue(1, 2);
        int expectedLongs = PaletteManipulator.computePackedArraySize(PaletteManipulator.MIN_INDIRECT_BITS);
        assertEquals(expectedLongs, result.packed().length);
    }

    @Test
    void upgradeSingleValue_identicalValues_returnsSingleEntryPalette() {
        PaletteManipulator.UpgradeResult result = PaletteManipulator.upgradeSingleValue(500, 500);

        assertEquals(1, result.palette().size());
        assertEquals(500, result.palette().get(0));
        assertEquals(0, result.bitsPerEntry());
        assertEquals(0, result.packed().length);
    }

    @Test
    void upgradeSingleValue_thenCompact_correctResult() {
        PaletteManipulator.UpgradeResult upgraded = PaletteManipulator.upgradeSingleValue(500, 100);
        PaletteManipulator.setIndex(upgraded.packed(), 0, 1, upgraded.bitsPerEntry());
        PaletteManipulator.setIndex(upgraded.packed(), 1, 1, upgraded.bitsPerEntry());

        PaletteManipulator.CompactResult compacted = PaletteManipulator.compact(
                upgraded.palette(), upgraded.packed(), upgraded.bitsPerEntry());

        assertEquals(2, compacted.palette().size());
        assertEquals(4, compacted.bitsPerEntry());
        assertEquals(1, PaletteManipulator.getIndex(compacted.packed(), 0, compacted.bitsPerEntry()));
        assertEquals(1, PaletteManipulator.getIndex(compacted.packed(), 1, compacted.bitsPerEntry()));
        assertEquals(0, PaletteManipulator.getIndex(compacted.packed(), 2, compacted.bitsPerEntry()));
        assertEquals(500, compacted.palette().get(
                PaletteManipulator.getIndex(compacted.packed(), 2, compacted.bitsPerEntry())));
    }

    // ======================== Edge cases ========================

    @Test
    void edge_allEntriesSameIndex() {
        long[] packed = buildPacked(4);
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            PaletteManipulator.setIndex(packed, i, 5, 4);
        }
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            assertEquals(5, PaletteManipulator.getIndex(packed, i, 4));
        }
    }

    @Test
    void edge_allEntriesDifferentIndices_8bit() {
        long[] packed = buildPacked(8);
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            PaletteManipulator.setIndex(packed, i, i % 256, 8);
        }
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            assertEquals(i % 256, PaletteManipulator.getIndex(packed, i, 8));
        }
    }

    @Test
    void edge_allZero_4bit() {
        long[] packed = buildPacked(4);
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            assertEquals(0, PaletteManipulator.getIndex(packed, i, 4));
        }
    }

    @Test
    void edge_allZero_5bit() {
        long[] packed = buildPacked(5);
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            assertEquals(0, PaletteManipulator.getIndex(packed, i, 5));
        }
    }

    @Test
    void edge_alternatingValues_4bit() {
        long[] packed = buildPacked(4);
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            PaletteManipulator.setIndex(packed, i, i % 2 == 0 ? 0 : 15, 4);
        }
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            assertEquals(i % 2 == 0 ? 0 : 15, PaletteManipulator.getIndex(packed, i, 4));
        }
    }

    // ======================== Utility methods ========================

    @Test
    void computeRequiredBits_size1_returns0() {
        assertEquals(0, PaletteManipulator.computeRequiredBits(1));
    }

    @Test
    void computeRequiredBits_size2_returns4() {
        assertEquals(4, PaletteManipulator.computeRequiredBits(2));
    }

    @Test
    void computeRequiredBits_size16_returns4() {
        assertEquals(4, PaletteManipulator.computeRequiredBits(16));
    }

    @Test
    void computeRequiredBits_size17_returns5() {
        assertEquals(5, PaletteManipulator.computeRequiredBits(17));
    }

    @Test
    void computeRequiredBits_size32_returns5() {
        assertEquals(5, PaletteManipulator.computeRequiredBits(32));
    }

    @Test
    void computeRequiredBits_size256_returns8() {
        assertEquals(8, PaletteManipulator.computeRequiredBits(256));
    }

    @Test
    void computeRequiredBits_size9_returns4() {
        assertEquals(4, PaletteManipulator.computeRequiredBits(9));
    }

    @Test
    void computeRequiredBits_size128_returns7() {
        assertEquals(7, PaletteManipulator.computeRequiredBits(128));
    }

    @Test
    void ceilLog2_powersOf2() {
        assertEquals(0, PaletteManipulator.ceilLog2(1));
        assertEquals(1, PaletteManipulator.ceilLog2(2));
        assertEquals(2, PaletteManipulator.ceilLog2(4));
        assertEquals(3, PaletteManipulator.ceilLog2(8));
        assertEquals(4, PaletteManipulator.ceilLog2(16));
    }

    @Test
    void ceilLog2_nonPowers() {
        assertEquals(2, PaletteManipulator.ceilLog2(3));
        assertEquals(3, PaletteManipulator.ceilLog2(5));
        assertEquals(4, PaletteManipulator.ceilLog2(9));
        assertEquals(5, PaletteManipulator.ceilLog2(17));
    }

    @Test
    void computePackedArraySize_4bit() {
        int expected = (4096 * 4 + 63) / 64;
        assertEquals(expected, PaletteManipulator.computePackedArraySize(4));
    }

    @Test
    void computePackedArraySize_5bit() {
        int expected = (4096 * 5 + 63) / 64;
        assertEquals(expected, PaletteManipulator.computePackedArraySize(5));
    }

    @Test
    void computePackedArraySize_15bit() {
        int expected = (4096 * 15 + 63) / 64;
        assertEquals(expected, PaletteManipulator.computePackedArraySize(15));
    }

    @Test
    void computePackedArraySize_minimum4bit() {
        int size = PaletteManipulator.computePackedArraySize(4);
        assertTrue(size > 0);
        assertTrue(4096 * 4 <= size * 64);
    }

    @Test
    void blockConstants_areConsistent() {
        assertEquals(4096, PaletteManipulator.BLOCKS_PER_SECTION);
        assertEquals(4, PaletteManipulator.MIN_INDIRECT_BITS);
        assertEquals(8, PaletteManipulator.MAX_INDIRECT_BITS);
        assertEquals(15, PaletteManipulator.DIRECT_BITS);
    }

    // ======================== Integration / workflow tests ========================

    @Test
    void reencode_thenCompact_roundtrip() {
        long[] packed4 = buildPacked(4);
        List<Integer> palette = new ArrayList<>(Arrays.asList(100, 200, 300, 400));
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            PaletteManipulator.setIndex(packed4, i, i % 4, 4);
        }

        long[] packed5 = PaletteManipulator.reencode(packed4, 4, 5);
        PaletteManipulator.CompactResult compacted = PaletteManipulator.compact(palette, packed5, 5);

        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            int idx = PaletteManipulator.getIndex(compacted.packed(), i, compacted.bitsPerEntry());
            int blockStateId = compacted.palette().get(idx);
            int originalIdx = i % 4;
            int expectedId = palette.get(originalIdx);
            assertEquals(expectedId, blockStateId, "Mismatch at position " + i);
        }
    }

    @Test
    void remapIndex_thenReencode_correctResult() {
        long[] packed = buildPacked(4);
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            PaletteManipulator.setIndex(packed, i, 2, 4);
        }

        PaletteManipulator.remapIndex(packed, 2, 15, 4);
        long[] packed5 = PaletteManipulator.reencode(packed, 4, 5);

        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            assertEquals(15, PaletteManipulator.getIndex(packed5, i, 5));
        }
    }

    @Test
    void remapIndices_thenCompact_correctResult() {
        List<Integer> palette = new ArrayList<>(Arrays.asList(100, 200, 300, 400, 500));
        long[] packed = buildPacked(5);
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            PaletteManipulator.setIndex(packed, i, i % 3, 5);
        }

        Map<Integer, Integer> remapping = new HashMap<>();
        remapping.put(0, 3);
        remapping.put(1, 4);
        PaletteManipulator.remapIndices(packed, remapping, 5);

        PaletteManipulator.CompactResult result = PaletteManipulator.compact(palette, packed, 5);

        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            int idx = PaletteManipulator.getIndex(result.packed(), i, result.bitsPerEntry());
            int blockStateId = result.palette().get(idx);
            int original = i % 3;
            if (original == 0) assertEquals(400, blockStateId);
            else if (original == 1) assertEquals(500, blockStateId);
            else assertEquals(300, blockStateId);
        }
    }

    @Test
    void fullObfuscationWorkflow_mode1_simulation() {
        List<Integer> palette = new ArrayList<>(Arrays.asList(1, 50, 60));
        long[] packed = buildPacked(4);
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            PaletteManipulator.setIndex(packed, i, 0, 4);
        }
        PaletteManipulator.setIndex(packed, 42, 1, 4);
        PaletteManipulator.setIndex(packed, 100, 2, 4);

        int replacementIdx = PaletteManipulator.ensureInPalette(palette, 1);
        PaletteManipulator.remapIndex(packed, 1, replacementIdx, 4);
        PaletteManipulator.remapIndex(packed, 2, replacementIdx, 4);

        PaletteManipulator.CompactResult result = PaletteManipulator.compact(palette, packed, 4);

        assertEquals(1, result.palette().size());
        assertEquals(0, result.bitsPerEntry());
        assertEquals(1, (int) result.palette().get(0));
    }

    @Test
    void fullObfuscationWorkflow_mode1_withPaletteExpansion() {
        List<Integer> palette = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            palette.add(100 + i);
        }
        long[] packed = buildPacked(4);
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            PaletteManipulator.setIndex(packed, i, 0, 4);
        }
        PaletteManipulator.setIndex(packed, 42, 1, 4);
        PaletteManipulator.setIndex(packed, 100, 2, 4);

        int replacementStateId = 99;
        int replacementIdx = PaletteManipulator.ensureInPalette(palette, replacementStateId);
        assertEquals(16, replacementIdx);

        int newBits = PaletteManipulator.computeRequiredBits(palette.size());
        assertEquals(5, newBits);
        long[] newPacked = PaletteManipulator.reencode(packed, 4, newBits);

        PaletteManipulator.remapIndex(newPacked, 1, replacementIdx, newBits);
        PaletteManipulator.remapIndex(newPacked, 2, replacementIdx, newBits);

        PaletteManipulator.CompactResult result = PaletteManipulator.compact(palette, newPacked, newBits);

        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            int idx = PaletteManipulator.getIndex(result.packed(), i, result.bitsPerEntry());
            int blockStateId = result.palette().get(idx);
            if (i == 42 || i == 100) {
                assertEquals(replacementStateId, blockStateId,
                        "Hidden block at position " + i + " should be replaced");
            } else {
                assertEquals(100, blockStateId);
            }
        }
    }

    @Test
    void upgradeSingleValue_thenSetReplacement_thenRemap() {
        PaletteManipulator.UpgradeResult upgraded = PaletteManipulator.upgradeSingleValue(500, 100);

        PaletteManipulator.remapIndex(upgraded.packed(), 0, 1, upgraded.bitsPerEntry());

        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            assertEquals(1, PaletteManipulator.getIndex(upgraded.packed(), i, upgraded.bitsPerEntry()));
        }
    }

    @Test
    void ensureInPalette_triggersBitWidthChange() {
        List<Integer> palette = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            palette.add(100 + i);
        }
        assertEquals(4, PaletteManipulator.computeRequiredBits(palette.size()));

        PaletteManipulator.ensureInPalette(palette, 999);
        assertEquals(17, palette.size());
        assertEquals(5, PaletteManipulator.computeRequiredBits(palette.size()));
    }

    @Test
    void compact_thenReencode_backToOriginalBitWidth() {
        List<Integer> palette = new ArrayList<>();
        for (int i = 0; i < 32; i++) {
            palette.add(100 + i);
        }
        long[] packed = buildPacked(5);
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            PaletteManipulator.setIndex(packed, i, i % 2, 5);
        }

        PaletteManipulator.CompactResult result = PaletteManipulator.compact(palette, packed, 5);

        assertEquals(4, result.bitsPerEntry());
        assertEquals(PaletteManipulator.computePackedArraySize(4), result.packed().length);
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            int idx = PaletteManipulator.getIndex(result.packed(), i, 4);
            int blockStateId = result.palette().get(idx);
            assertEquals((i % 2 == 0) ? 100 : 101, blockStateId);
        }
    }

    @Test
    void compact_preservesOrdering() {
        List<Integer> palette = new ArrayList<>(Arrays.asList(50, 10, 30));
        long[] packed = buildPacked(4);
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            PaletteManipulator.setIndex(packed, i, i % 3, 4);
        }

        PaletteManipulator.CompactResult result = PaletteManipulator.compact(palette, packed, 4);

        assertEquals(3, result.palette().size());
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            int idx = PaletteManipulator.getIndex(result.packed(), i, result.bitsPerEntry());
            int blockStateId = result.palette().get(idx);
            int original = i % 3;
            assertEquals(palette.get(original), blockStateId,
                    "Ordering corrupted at position " + i);
        }
    }
}
