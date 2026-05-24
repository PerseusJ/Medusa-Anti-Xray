package com.antixray.nms.v1_20;

import com.antixray.nms.AbstractNmsAdapter;
import org.bukkit.Location;
import org.bukkit.World;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class NmsAdapter_v1_20_R4 extends AbstractNmsAdapter {

    private final Class<?> chunkPacketDataClass;
    private final Field chunkSectionsField;

    public NmsAdapter_v1_20_R4() {
        super(
            "v1_20_R4",
            new String[]{"h", "getPalette"},
            new String[]{"i", "getData", "getStorage"},
            new String[]{"a", "getSize"},
            new String[]{"a", "getId", "valueFor"},
            new String[]{"a", "getId"},
            new String[]{"getHandle"},
            new String[]{"a_", "getBlockState", "getType"},
            new String[]{"nonEmptyCount", "c", "nonEmpty"},
            new String[]{"d", "bitsPerEntry", "bits"}
        );

        this.chunkPacketDataClass = getNmsClass("net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData");
        this.chunkSectionsField = findField(chunkPacketDataClass, new String[]{"c", "sections", "chunkSections"});
    }

    @Override
    public List<Object> getChunkSections(Object packet) {
        try {
            Object sections = getFieldValue(chunkSectionsField, packet);
            if (sections instanceof Object[]) {
                List<Object> result = new ArrayList<>();
                for (Object section : (Object[]) sections) { if (section != null) result.add(section); }
                return result;
            } else if (sections instanceof List) {
                return new ArrayList<>((List<Object>) sections);
            }
            return Collections.emptyList();
        } catch (Exception e) { LOGGER.log(Level.WARNING, "Failed to get chunk sections", e); return Collections.emptyList(); }
    }

    @Override
    public List<Integer> getPaletteEntries(Object chunkSection) {
        try {
            Object pc = getPalettedContainer(chunkSection);
            if (pc == null) return Collections.emptyList();
            Object palette = invokeMethod(palettedContainerGetPalette, pc);
            if (palette == null) return Collections.emptyList();
            return extractPaletteIds(palette);
        } catch (Exception e) { LOGGER.log(Level.WARNING, "Failed to get palette entries", e); return Collections.emptyList(); }
    }

    @Override
    public long[] getPackedIndices(Object chunkSection) {
        try {
            Object pc = getPalettedContainer(chunkSection);
            if (pc == null) return new long[0];
            Object data = invokeMethod(palettedContainerGetData, pc);
            if (data instanceof long[]) return (long[]) data;
            if (data != null) { long[] e = extractPackedArray(data); if (e != null) return e; }
            return new long[0];
        } catch (Exception e) { LOGGER.log(Level.WARNING, "Failed to get packed indices", e); return new long[0]; }
    }

    @Override
    public void setPaletteEntries(Object chunkSection, List<Integer> entries) {
        try {
            Object pc = getPalettedContainer(chunkSection);
            if (pc == null) return;
            Object palette = invokeMethod(palettedContainerGetPalette, pc);
            if (palette == null) return;
            if (singleValuePaletteClass != null && singleValuePaletteClass.isInstance(palette)) {
                upgradeToIndirectPalette(chunkSection, entries.get(0), entries.size() > 1 ? entries.get(1) : entries.get(0));
                return;
            }
            setPaletteEntriesViaReflection(palette, entries);
        } catch (Exception e) { LOGGER.log(Level.WARNING, "Failed to set palette entries", e); }
    }

    @Override
    public void setPackedIndices(Object chunkSection, long[] indices, int bitsPerEntry) {
        try {
            Object pc = getPalettedContainer(chunkSection);
            if (pc == null) return;
            Object data = invokeMethod(palettedContainerGetData, pc);
            if (data != null) {
                Field df = findField(data.getClass(), new String[]{"a", "packed"});
                if (df != null) { df.setAccessible(true); df.set(data, indices); return; }
            }
            Method sm = findMethod(palettedContainerClass, new String[]{"b", "setData", "setStorage"}, false, Object.class);
            if (sm != null) sm.invoke(pc, (Object) indices);
        } catch (Exception e) { LOGGER.log(Level.WARNING, "Failed to set packed indices", e); }
    }

    @Override
    public int getBlockStateAt(World world, int x, int y, int z) {
        try {
            Object nmsWorld = invokeMethod(craftWorldGetHandle, world);
            if (nmsWorld == null) return -1;
            Object blockPos = invokeConstructor(blockPosCreate, x, y, z);
            if (blockPos == null) return -1;
            Object blockState = invokeMethod(nmsLevelGetBlockState, nmsWorld, blockPos);
            return blockState != null ? getBlockStateId(blockState) : -1;
        } catch (Exception e) { return -1; }
    }

    @Override
    public int getSectionNonEmptyCount(Object chunkSection) {
        try {
            if (sectionNonEmptyField != null) {
                Object count = getFieldValue(sectionNonEmptyField, chunkSection);
                if (count instanceof Number) return ((Number) count).intValue();
            }
            Method m = findMethod(chunkSection.getClass(), new String[]{"a", "getNonEmptyCount"}, true);
            if (m != null) { Object r = m.invoke(chunkSection); if (r instanceof Number) return ((Number) r).intValue(); }
            return 0;
        } catch (Exception e) { return 0; }
    }

    @Override
    public Object createBlockUpdatePacket(Location loc, int blockStateId) {
        try {
            Object nmsWorld = invokeMethod(craftWorldGetHandle, loc.getWorld());
            Object blockPos = invokeConstructor(blockPosCreate, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            Object blockState = resolveBlockStateById(nmsWorld, blockStateId);
            if (blockState == null || blockPos == null || blockUpdatePacketClass == null) return null;
            return blockUpdatePacketClass.getConstructor(blockPosClass, blockStateClass).newInstance(blockPos, blockState);
        } catch (Exception e) { LOGGER.log(Level.WARNING, "Failed to create block update packet", e); return null; }
    }

    @Override
    public Object createMultiBlockUpdatePacket(World world, int chunkX, int chunkZ, Map<Location, Integer> changes) {
        try {
            Object sectionPos = sectionPosClass.getConstructor(int.class, int.class, int.class).newInstance(chunkX, 0, chunkZ);
            short[] positions = new short[changes.size()];
            Object[] blockStates = new Object[changes.size()];
            Object nmsWorld = invokeMethod(craftWorldGetHandle, world);
            int i = 0;
            for (Map.Entry<Location, Integer> entry : changes.entrySet()) {
                Location loc = entry.getKey();
                positions[i] = (short) ((loc.getBlockX() & 15) | ((loc.getBlockZ() & 15) << 4) | ((loc.getBlockY() & 15) << 8));
                Object bs = resolveBlockStateById(nmsWorld, entry.getValue());
                blockStates[i] = bs != null ? bs : resolveBlockStateById(nmsWorld, 0);
                i++;
            }
            return multiBlockUpdatePacketClass.getConstructor(sectionPosClass, short[].class, blockStateClass.arrayType()).newInstance(sectionPos, positions, blockStates);
        } catch (Exception e) { LOGGER.log(Level.WARNING, "Failed to create multi-block update packet", e); return null; }
    }

	@Override
	public Object createChunkDataPacket(World world, int chunkX, int chunkZ) {
		return super.createChunkDataPacket(world, chunkX, chunkZ);
	}

	@Override
	public int getPaletteBitsPerEntry(Object chunkSection) {
        try {
            Object pc = getPalettedContainer(chunkSection);
            if (pc == null) return 0;
            if (palettedContainerBitsField != null) { Object bits = getFieldValue(palettedContainerBitsField, pc); if (bits instanceof Number) return ((Number) bits).intValue(); }
            Object palette = invokeMethod(palettedContainerGetPalette, pc);
            if (palette == null) return 0;
            if (singleValuePaletteClass != null && singleValuePaletteClass.isInstance(palette)) return 0;
            if (globalPaletteClass != null && globalPaletteClass.isInstance(palette)) return 15;
            return 4;
        } catch (Exception e) { return 4; }
    }

    @Override
    public boolean isSingleValuePalette(Object chunkSection) {
        try {
            Object pc = getPalettedContainer(chunkSection);
            if (pc == null) return false;
            Object palette = invokeMethod(palettedContainerGetPalette, pc);
            return singleValuePaletteClass != null && singleValuePaletteClass.isInstance(palette);
        } catch (Exception e) { return false; }
    }

    @Override
    public int getSingleValue(Object chunkSection) {
        try {
            Object pc = getPalettedContainer(chunkSection);
            if (pc == null) return 0;
            Object palette = invokeMethod(palettedContainerGetPalette, pc);
            if (palette == null) return 0;
            Field vf = findField(palette.getClass(), new String[]{"b", "value", "a"});
            if (vf != null) return getBlockStateId(vf.get(palette));
            return 0;
        } catch (Exception e) { return 0; }
    }

    @Override
    public void upgradeToIndirectPalette(Object chunkSection, int singleValue, int replacementValue) {
        try {
            Object pc = getPalettedContainer(chunkSection);
            if (pc == null) return;
            Method um = findMethod(palettedContainerClass, new String[]{"a", "upgrade", "switchPalette"}, false, int.class);
            if (um != null) { um.invoke(pc, replacementValue); return; }
            Method sm = findMethod(palettedContainerClass, new String[]{"b", "setPalette"}, false, paletteClass, Object.class);
            if (sm != null && linearPaletteClass != null && paletteClass != null) {
                Object np = linearPaletteClass.getConstructor(paletteClass, int.class).newInstance(null, 2);
                if (np != null) sm.invoke(pc, np, new long[com.antixray.engine.PaletteManipulator.computePackedArraySize(4)]);
            }
        } catch (Exception e) { LOGGER.log(Level.WARNING, "Failed to upgrade single-value palette", e); }
    }

    private List<Integer> extractPaletteIds(Object palette) throws Exception {
        List<Integer> result = new ArrayList<>();
        if (singleValuePaletteClass != null && singleValuePaletteClass.isInstance(palette)) {
            Field vf = findField(palette.getClass(), new String[]{"b", "value", "a"});
            if (vf != null) { vf.setAccessible(true); result.add(getBlockStateId(vf.get(palette))); }
            return result;
        }
        if (globalPaletteClass != null && globalPaletteClass.isInstance(palette)) return result;
        if (paletteGetSize != null) {
            int size = ((Number) paletteGetSize.invoke(palette)).intValue();
            for (int i = 0; i < size; i++) { try { Object entry = paletteGetId.invoke(palette, i); if (entry != null) result.add(getBlockStateId(entry)); } catch (Exception e) { break; } }
        }
        if (result.isEmpty()) result.addAll(extractPaletteEntriesViaFields(palette));
        return result;
    }

    private List<Integer> extractPaletteEntriesViaFields(Object palette) {
        List<Integer> result = new ArrayList<>();
        try {
            Field vf = findField(palette.getClass(), new String[]{"b", "values", "a", "entries"});
            if (vf != null) { vf.setAccessible(true); Object values = vf.get(palette);
                if (values instanceof Object[]) { for (Object entry : (Object[]) values) result.add(getBlockStateId(entry)); }
                else if (values instanceof List) { for (Object entry : (List<?>) values) result.add(getBlockStateId(entry)); }
            }
        } catch (Exception e) { LOGGER.log(Level.FINE, "Could not extract palette entries via fields", e); }
        return result;
    }

    private void setPaletteEntriesViaReflection(Object palette, List<Integer> entries) {
        try {
            Field vf = findField(palette.getClass(), new String[]{"b", "values", "a", "entries"});
            if (vf == null) return; vf.setAccessible(true); Object existing = vf.get(palette);
            if (existing instanceof Object[]) {
                Object[] old = (Object[]) existing; Object[] newArray = new Object[entries.size()];
                for (int i = 0; i < entries.size(); i++) { newArray[i] = resolveBlockStateFromRegistry(entries.get(i)); if (newArray[i] == null && old.length > 0) newArray[i] = old[0]; }
                vf.set(palette, newArray);
            } else if (existing instanceof List) { List<Object> list = (List<Object>) existing; list.clear(); for (Integer e : entries) { Object bs = resolveBlockStateFromRegistry(e); if (bs != null) list.add(bs); } }
        } catch (Exception e) { LOGGER.log(Level.WARNING, "Failed to set palette entries via reflection", e); }
    }

    private long[] extractPackedArray(Object data) {
        try { Field df = findField(data.getClass(), new String[]{"a", "packed"}); if (df != null) { df.setAccessible(true); Object a = df.get(data); if (a instanceof long[]) return (long[]) a; } } catch (Exception ignored) {}
        return null;
    }
}
