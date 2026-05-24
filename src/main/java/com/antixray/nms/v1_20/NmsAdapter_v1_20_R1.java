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

public class NmsAdapter_v1_20_R1 extends AbstractNmsAdapter {

    private final Class<?> chunkPacketDataClass;
    private final Field chunkSectionsField;

    public NmsAdapter_v1_20_R1() {
        super(
            "v1_20_R1",
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
            if (sections instanceof Object[]) { List<Object> r = new ArrayList<>(); for (Object s : (Object[]) sections) { if (s != null) r.add(s); } return r; }
            else if (sections instanceof List) { return new ArrayList<>((List<Object>) sections); }
            return Collections.emptyList();
        } catch (Exception e) { LOGGER.log(Level.WARNING, "Failed to get chunk sections", e); return Collections.emptyList(); }
    }

    @Override
    public List<Integer> getPaletteEntries(Object chunkSection) {
        try { Object pc = getPalettedContainer(chunkSection); if (pc == null) return Collections.emptyList(); Object p = invokeMethod(palettedContainerGetPalette, pc); if (p == null) return Collections.emptyList(); return extractPaletteIds(p); }
        catch (Exception e) { LOGGER.log(Level.WARNING, "Failed to get palette entries", e); return Collections.emptyList(); }
    }

    @Override
    public long[] getPackedIndices(Object chunkSection) {
        try { Object pc = getPalettedContainer(chunkSection); if (pc == null) return new long[0]; Object d = invokeMethod(palettedContainerGetData, pc); if (d instanceof long[]) return (long[]) d; if (d != null) { long[] e = extractPackedArray(d); if (e != null) return e; } return new long[0]; }
        catch (Exception e) { LOGGER.log(Level.WARNING, "Failed to get packed indices", e); return new long[0]; }
    }

    @Override
    public void setPaletteEntries(Object chunkSection, List<Integer> entries) {
        try { Object pc = getPalettedContainer(chunkSection); if (pc == null) return; Object p = invokeMethod(palettedContainerGetPalette, pc); if (p == null) return;
            if (singleValuePaletteClass != null && singleValuePaletteClass.isInstance(p)) { upgradeToIndirectPalette(chunkSection, entries.get(0), entries.size() > 1 ? entries.get(1) : entries.get(0)); return; } setPaletteEntriesViaReflection(p, entries); }
        catch (Exception e) { LOGGER.log(Level.WARNING, "Failed to set palette entries", e); }
    }

    @Override
    public void setPackedIndices(Object chunkSection, long[] indices, int bitsPerEntry) {
        try { Object pc = getPalettedContainer(chunkSection); if (pc == null) return; Object d = invokeMethod(palettedContainerGetData, pc);
            if (d != null) { Field df = findField(d.getClass(), new String[]{"a", "packed"}); if (df != null) { df.setAccessible(true); df.set(d, indices); return; } }
            Method sm = findMethod(palettedContainerClass, new String[]{"b", "setData", "setStorage"}, false, Object.class); if (sm != null) sm.invoke(pc, (Object) indices); }
        catch (Exception e) { LOGGER.log(Level.WARNING, "Failed to set packed indices", e); }
    }

    @Override
    public int getBlockStateAt(World world, int x, int y, int z) {
        try { Object nmsWorld = invokeMethod(craftWorldGetHandle, world); if (nmsWorld == null) return -1; Object bp = invokeConstructor(blockPosCreate, x, y, z); if (bp == null) return -1; Object bs = invokeMethod(nmsLevelGetBlockState, nmsWorld, bp); return bs != null ? getBlockStateId(bs) : -1; }
        catch (Exception e) { return -1; }
    }

    @Override
    public int getSectionNonEmptyCount(Object chunkSection) {
        try { if (sectionNonEmptyField != null) { Object c = getFieldValue(sectionNonEmptyField, chunkSection); if (c instanceof Number) return ((Number) c).intValue(); }
            Method m = findMethod(chunkSection.getClass(), new String[]{"a", "getNonEmptyCount"}, true); if (m != null) { Object r = m.invoke(chunkSection); if (r instanceof Number) return ((Number) r).intValue(); } return 0; }
        catch (Exception e) { return 0; }
    }

    @Override
    public Object createBlockUpdatePacket(Location loc, int blockStateId) {
        try { Object nmsWorld = invokeMethod(craftWorldGetHandle, loc.getWorld()); Object bp = invokeConstructor(blockPosCreate, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()); Object bs = resolveBlockStateById(nmsWorld, blockStateId);
            if (bs == null || bp == null || blockUpdatePacketClass == null) return null; return blockUpdatePacketClass.getConstructor(blockPosClass, blockStateClass).newInstance(bp, bs); }
        catch (Exception e) { LOGGER.log(Level.WARNING, "Failed to create block update packet", e); return null; }
    }

    @Override
    public Object createMultiBlockUpdatePacket(World world, int chunkX, int chunkZ, Map<Location, Integer> changes) {
        try { Object sp = sectionPosClass.getConstructor(int.class, int.class, int.class).newInstance(chunkX, 0, chunkZ); short[] pos = new short[changes.size()]; Object[] bss = new Object[changes.size()]; Object nmsWorld = invokeMethod(craftWorldGetHandle, world);
            int i = 0; for (Map.Entry<Location, Integer> e : changes.entrySet()) { Location l = e.getKey(); pos[i] = (short) ((l.getBlockX() & 15) | ((l.getBlockZ() & 15) << 4) | ((l.getBlockY() & 15) << 8)); Object bs = resolveBlockStateById(nmsWorld, e.getValue()); bss[i] = bs != null ? bs : resolveBlockStateById(nmsWorld, 0); i++; }
            return multiBlockUpdatePacketClass.getConstructor(sectionPosClass, short[].class, blockStateClass.arrayType()).newInstance(sp, pos, bss); }
        catch (Exception e) { LOGGER.log(Level.WARNING, "Failed to create multi-block update packet", e); return null; }
    }

	@Override
	public Object createChunkDataPacket(World world, int chunkX, int chunkZ) {
		return super.createChunkDataPacket(world, chunkX, chunkZ);
	}

	@Override
	public int getPaletteBitsPerEntry(Object chunkSection) {
        try { Object pc = getPalettedContainer(chunkSection); if (pc == null) return 0; if (palettedContainerBitsField != null) { Object b = getFieldValue(palettedContainerBitsField, pc); if (b instanceof Number) return ((Number) b).intValue(); }
            Object p = invokeMethod(palettedContainerGetPalette, pc); if (p == null) return 0; if (singleValuePaletteClass != null && singleValuePaletteClass.isInstance(p)) return 0; if (globalPaletteClass != null && globalPaletteClass.isInstance(p)) return 15; return 4; }
        catch (Exception e) { return 4; }
    }

    @Override
    public boolean isSingleValuePalette(Object chunkSection) {
        try { Object pc = getPalettedContainer(chunkSection); if (pc == null) return false; Object p = invokeMethod(palettedContainerGetPalette, pc); return singleValuePaletteClass != null && singleValuePaletteClass.isInstance(p); } catch (Exception e) { return false; }
    }

    @Override
    public int getSingleValue(Object chunkSection) {
        try { Object pc = getPalettedContainer(chunkSection); if (pc == null) return 0; Object p = invokeMethod(palettedContainerGetPalette, pc); if (p == null) return 0;
            Field vf = findField(p.getClass(), new String[]{"b", "value", "a"}); if (vf != null) return getBlockStateId(vf.get(p)); return 0; } catch (Exception e) { return 0; }
    }

    @Override
    public void upgradeToIndirectPalette(Object chunkSection, int singleValue, int replacementValue) {
        try { Object pc = getPalettedContainer(chunkSection); if (pc == null) return;
            Method um = findMethod(palettedContainerClass, new String[]{"a", "upgrade", "switchPalette"}, false, int.class); if (um != null) { um.invoke(pc, replacementValue); return; }
            Method sm = findMethod(palettedContainerClass, new String[]{"b", "setPalette"}, false, paletteClass, Object.class);
            if (sm != null && linearPaletteClass != null && paletteClass != null) { Object np = linearPaletteClass.getConstructor(paletteClass, int.class).newInstance(null, 2); if (np != null) sm.invoke(pc, np, new long[com.antixray.engine.PaletteManipulator.computePackedArraySize(4)]); } }
        catch (Exception e) { LOGGER.log(Level.WARNING, "Failed to upgrade single-value palette", e); }
    }

    private List<Integer> extractPaletteIds(Object palette) throws Exception {
        List<Integer> result = new ArrayList<>();
        if (singleValuePaletteClass != null && singleValuePaletteClass.isInstance(palette)) { Field vf = findField(palette.getClass(), new String[]{"b", "value", "a"}); if (vf != null) { vf.setAccessible(true); result.add(getBlockStateId(vf.get(palette))); } return result; }
        if (globalPaletteClass != null && globalPaletteClass.isInstance(palette)) return result;
        if (paletteGetSize != null) { int size = ((Number) paletteGetSize.invoke(palette)).intValue(); for (int i = 0; i < size; i++) { try { Object e = paletteGetId.invoke(palette, i); if (e != null) result.add(getBlockStateId(e)); } catch (Exception ex) { break; } } }
        if (result.isEmpty()) result.addAll(extractPaletteEntriesViaFields(palette));
        return result;
    }

    private List<Integer> extractPaletteEntriesViaFields(Object palette) {
        List<Integer> result = new ArrayList<>();
        try { Field vf = findField(palette.getClass(), new String[]{"b", "values", "a", "entries"}); if (vf != null) { vf.setAccessible(true); Object v = vf.get(palette);
            if (v instanceof Object[]) { for (Object e : (Object[]) v) result.add(getBlockStateId(e)); } else if (v instanceof List) { for (Object e : (List<?>) v) result.add(getBlockStateId(e)); } } }
        catch (Exception e) { LOGGER.log(Level.FINE, "Could not extract palette entries via fields", e); }
        return result;
    }

    private void setPaletteEntriesViaReflection(Object palette, List<Integer> entries) {
        try { Field vf = findField(palette.getClass(), new String[]{"b", "values", "a", "entries"}); if (vf == null) return; vf.setAccessible(true); Object existing = vf.get(palette);
            if (existing instanceof Object[]) { Object[] old = (Object[]) existing; Object[] nw = new Object[entries.size()]; for (int i = 0; i < entries.size(); i++) { nw[i] = resolveBlockStateFromRegistry(entries.get(i)); if (nw[i] == null && old.length > 0) nw[i] = old[0]; } vf.set(palette, nw); }
            else if (existing instanceof List) { List<Object> list = (List<Object>) existing; list.clear(); for (Integer e : entries) { Object bs = resolveBlockStateFromRegistry(e); if (bs != null) list.add(bs); } } }
        catch (Exception e) { LOGGER.log(Level.WARNING, "Failed to set palette entries via reflection", e); }
    }

    private long[] extractPackedArray(Object data) {
        try { Field df = findField(data.getClass(), new String[]{"a", "packed"}); if (df != null) { df.setAccessible(true); Object a = df.get(data); if (a instanceof long[]) return (long[]) a; } } catch (Exception ignored) {}
        return null;
    }
}
