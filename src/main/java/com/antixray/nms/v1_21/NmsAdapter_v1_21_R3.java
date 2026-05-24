package com.antixray.nms.v1_21;

import com.antixray.nms.AbstractNmsAdapter;
import com.antixray.nms.NmsAdapter;
import org.bukkit.Location;
import org.bukkit.World;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class NmsAdapter_v1_21_R3 extends AbstractNmsAdapter {

    private final Class<?> chunkPacketDataClass;
    private final Field chunkSectionsField;

    public NmsAdapter_v1_21_R3() {
        super(
            "v1_21_R3",
            new String[]{"h", "getPalette"},
            new String[]{"i", "getData", "getStorage", "j"},
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
                for (Object section : (Object[]) sections) {
                    if (section != null) {
                        result.add(section);
                    }
                }
                return result;
            } else if (sections instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> list = (List<Object>) sections;
                return new ArrayList<>(list);
            }
            return Collections.emptyList();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to get chunk sections", e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<Integer> getPaletteEntries(Object chunkSection) {
        try {
            Object palettedContainer = getPalettedContainer(chunkSection);
            if (palettedContainer == null) return Collections.emptyList();

            Object palette = invokeMethod(palettedContainerGetPalette, palettedContainer);
            if (palette == null) return Collections.emptyList();

            return extractPaletteIds(palette);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to get palette entries", e);
            return Collections.emptyList();
        }
    }

    @Override
    public long[] getPackedIndices(Object chunkSection) {
        try {
            Object palettedContainer = getPalettedContainer(chunkSection);
            if (palettedContainer == null) return new long[0];

            Object data = invokeMethod(palettedContainerGetData, palettedContainer);
            if (data instanceof long[]) {
                return (long[]) data;
            }
            if (data != null) {
                long[] extracted = extractPackedArray(data);
                if (extracted != null) return extracted;
            }
            return new long[0];
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to get packed indices", e);
            return new long[0];
        }
    }

    @Override
    public void setPaletteEntries(Object chunkSection, List<Integer> entries) {
        try {
            Object palettedContainer = getPalettedContainer(chunkSection);
            if (palettedContainer == null) return;

            Object palette = invokeMethod(palettedContainerGetPalette, palettedContainer);
            if (palette == null) return;

            if (singleValuePaletteClass != null && singleValuePaletteClass.isInstance(palette)) {
                upgradeToIndirectPalette(chunkSection, entries.get(0), entries.size() > 1 ? entries.get(1) : entries.get(0));
                return;
            }

            setPaletteEntriesViaReflection(palette, entries);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to set palette entries", e);
        }
    }

    @Override
    public void setPackedIndices(Object chunkSection, long[] indices, int bitsPerEntry) {
        try {
            Object palettedContainer = getPalettedContainer(chunkSection);
            if (palettedContainer == null) return;

            Object data = invokeMethod(palettedContainerGetData, palettedContainer);
            if (data != null) {
                Field dataField = findField(data.getClass(), new String[]{"a", "packed"});
                if (dataField != null) {
                    dataField.setAccessible(true);
                    dataField.set(data, indices);
                    return;
                }
            }

            Method setDataMethod = findMethod(palettedContainerClass,
                    new String[]{"b", "setData", "setStorage"}, false, Object.class);
            if (setDataMethod != null) {
                setDataMethod.invoke(palettedContainer, (Object) indices);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to set packed indices", e);
        }
    }

    @Override
    public int getBlockStateAt(World world, int x, int y, int z) {
        try {
            Object nmsWorld = invokeMethod(craftWorldGetHandle, world);
            if (nmsWorld == null) return -1;

            Object blockPos = invokeConstructor(blockPosCreate, x, y, z);
            if (blockPos == null) return -1;

            Object blockState = invokeMethod(nmsLevelGetBlockState, nmsWorld, blockPos);
            if (blockState == null) return -1;

            return getBlockStateId(blockState);
        } catch (Exception e) {
            return -1;
        }
    }

    @Override
    public int getSectionNonEmptyCount(Object chunkSection) {
        try {
            if (sectionNonEmptyField != null) {
                Object count = getFieldValue(sectionNonEmptyField, chunkSection);
                if (count instanceof Number) {
                    return ((Number) count).intValue();
                }
            }
            Method nonEmptyMethod = findMethod(chunkSection.getClass(),
                    new String[]{"a", "getNonEmptyCount", "nonEmptyBlockCount"}, true);
            if (nonEmptyMethod != null) {
                Object result = nonEmptyMethod.invoke(chunkSection);
                if (result instanceof Number) {
                    return ((Number) result).intValue();
                }
            }
            return 0;
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public Object createBlockUpdatePacket(Location loc, int blockStateId) {
        try {
            Object nmsWorld = invokeMethod(craftWorldGetHandle, loc.getWorld());
            Object blockPos = invokeConstructor(blockPosCreate, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());

            Object blockState = resolveBlockStateById(nmsWorld, blockStateId);
            if (blockState == null || blockPos == null || blockUpdatePacketClass == null) return null;

            return blockUpdatePacketClass.getConstructor(blockPosClass, blockStateClass)
                    .newInstance(blockPos, blockState);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to create block update packet", e);
            return null;
        }
    }

    @Override
    public Object createMultiBlockUpdatePacket(World world, int chunkX, int chunkZ, Map<Location, Integer> changes) {
        try {
            Object sectionPos = sectionPosClass.getConstructor(int.class, int.class, int.class)
                    .newInstance(chunkX, 0, chunkZ);

            short[] positions = new short[changes.size()];
            Object[] blockStates = new Object[changes.size()];

            Object nmsWorld = invokeMethod(craftWorldGetHandle, world);

            int i = 0;
            for (Map.Entry<Location, Integer> entry : changes.entrySet()) {
                Location loc = entry.getKey();
                int stateId = entry.getValue();

                int localX = loc.getBlockX() & 15;
                int localY = loc.getBlockY() & 15;
                int localZ = loc.getBlockZ() & 15;
                positions[i] = (short) (localX | (localZ << 4) | (localY << 8));

                Object blockState = resolveBlockStateById(nmsWorld, stateId);
                blockStates[i] = blockState != null ? blockState : resolveBlockStateById(nmsWorld, 0);
                i++;
            }

            return multiBlockUpdatePacketClass.getConstructor(sectionPosClass, short[].class, blockStateClass.arrayType())
                    .newInstance(sectionPos, positions, blockStates);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to create multi-block update packet", e);
            return null;
        }
    }

	@Override
	public Object createChunkDataPacket(World world, int chunkX, int chunkZ) {
		return super.createChunkDataPacket(world, chunkX, chunkZ);
	}

	@Override
	public int getPaletteBitsPerEntry(Object chunkSection) {
        try {
            Object palettedContainer = getPalettedContainer(chunkSection);
            if (palettedContainer == null) return 0;

            if (palettedContainerBitsField != null) {
                Object bits = getFieldValue(palettedContainerBitsField, palettedContainer);
                if (bits instanceof Number) {
                    return ((Number) bits).intValue();
                }
            }

            Object palette = invokeMethod(palettedContainerGetPalette, palettedContainer);
            if (palette == null) return 0;

            if (singleValuePaletteClass != null && singleValuePaletteClass.isInstance(palette)) {
                return 0;
            }
            if (globalPaletteClass != null && globalPaletteClass.isInstance(palette)) {
                return 15;
            }
            return 4;
        } catch (Exception e) {
            return 4;
        }
    }

    @Override
    public boolean isSingleValuePalette(Object chunkSection) {
        try {
            Object palettedContainer = getPalettedContainer(chunkSection);
            if (palettedContainer == null) return false;
            Object palette = invokeMethod(palettedContainerGetPalette, palettedContainer);
            return singleValuePaletteClass != null && singleValuePaletteClass.isInstance(palette);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public int getSingleValue(Object chunkSection) {
        try {
            Object palettedContainer = getPalettedContainer(chunkSection);
            if (palettedContainer == null) return 0;
            Object palette = invokeMethod(palettedContainerGetPalette, palettedContainer);
            if (palette == null) return 0;

            Field valueField = findField(palette.getClass(), new String[]{"b", "value", "a"});
            if (valueField != null) {
                Object value = valueField.get(palette);
                return getBlockStateId(value);
            }
            return 0;
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public void upgradeToIndirectPalette(Object chunkSection, int singleValue, int replacementValue) {
        try {
            Object palettedContainer = getPalettedContainer(chunkSection);
            if (palettedContainer == null) return;

            Method upgradeMethod = findMethod(palettedContainerClass,
                    new String[]{"a", "upgrade", "switchPalette", "setPalette"}, false, int.class);
            if (upgradeMethod != null) {
                upgradeMethod.invoke(palettedContainer, replacementValue);
                return;
            }

            Method setMethod = findMethod(palettedContainerClass,
                    new String[]{"b", "setPalette"}, false, paletteClass, Object.class);
            if (setMethod != null) {
                Object newPalette = createLinearPalette(2);
                if (newPalette != null) {
                    setMethod.invoke(palettedContainer, newPalette, new long[com.antixray.engine.PaletteManipulator.computePackedArraySize(4)]);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to upgrade single-value palette", e);
        }
    }

    private List<Integer> extractPaletteIds(Object palette) throws Exception {
        List<Integer> result = new ArrayList<>();

        if (singleValuePaletteClass != null && singleValuePaletteClass.isInstance(palette)) {
            Field valueField = findField(palette.getClass(), new String[]{"b", "value", "a"});
            if (valueField != null) {
                valueField.setAccessible(true);
                Object value = valueField.get(palette);
                result.add(getBlockStateId(value));
            }
            return result;
        }

        if (globalPaletteClass != null && globalPaletteClass.isInstance(palette)) {
            return result;
        }

        if (paletteGetSize != null) {
            Object sizeObj = paletteGetSize.invoke(palette);
            int size = ((Number) sizeObj).intValue();

            for (int i = 0; i < size; i++) {
                try {
                    Object entry = paletteGetId.invoke(palette, i);
                    if (entry != null) {
                        result.add(getBlockStateId(entry));
                    }
                } catch (Exception e) {
                    break;
                }
            }
        }

        if (result.isEmpty()) {
            result.addAll(extractPaletteEntriesViaFields(palette));
        }

        return result;
    }

    private List<Integer> extractPaletteEntriesViaFields(Object palette) {
        List<Integer> result = new ArrayList<>();
        try {
            Field valuesField = findField(palette.getClass(), new String[]{"b", "values", "a", "entries"});
            if (valuesField != null) {
                valuesField.setAccessible(true);
                Object values = valuesField.get(palette);
                if (values instanceof Object[]) {
                    for (Object entry : (Object[]) values) {
                        result.add(getBlockStateId(entry));
                    }
                } else if (values instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Object> list = (List<Object>) values;
                    for (Object entry : list) {
                        result.add(getBlockStateId(entry));
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Could not extract palette entries via fields", e);
        }
        return result;
    }

    private void setPaletteEntriesViaReflection(Object palette, List<Integer> entries) {
        try {
            Field valuesField = findField(palette.getClass(), new String[]{"b", "values", "a", "entries"});
            if (valuesField != null) {
                valuesField.setAccessible(true);
                Object existingValues = valuesField.get(palette);

                if (existingValues instanceof Object[]) {
                    Object[] oldArray = (Object[]) existingValues;
                    Object[] newArray = new Object[entries.size()];
                    for (int i = 0; i < entries.size(); i++) {
                        newArray[i] = resolveBlockStateFromRegistry(entries.get(i));
                        if (newArray[i] == null && oldArray.length > 0) {
                            newArray[i] = oldArray[0];
                        }
                    }
                    valuesField.set(palette, newArray);
                } else if (existingValues instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Object> list = (List<Object>) existingValues;
                    list.clear();
                    for (Integer entry : entries) {
                        Object blockState = resolveBlockStateFromRegistry(entry);
                        if (blockState != null) {
                            list.add(blockState);
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to set palette entries via reflection", e);
        }
    }

    private long[] extractPackedArray(Object data) {
        try {
            Field dataField = findField(data.getClass(), new String[]{"a", "packed"});
            if (dataField != null) {
                dataField.setAccessible(true);
                Object array = dataField.get(data);
                if (array instanceof long[]) {
                    return (long[]) array;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private Object createLinearPalette(int size) {
        if (linearPaletteClass == null || paletteClass == null) return null;
        try {
            return linearPaletteClass.getConstructor(paletteClass, int.class)
                    .newInstance(null, size);
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to create LinearPalette", e);
            return null;
        }
    }
}
