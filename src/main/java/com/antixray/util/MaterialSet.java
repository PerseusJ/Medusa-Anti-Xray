package com.antixray.util;

import com.antixray.nms.NmsAdapter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import com.antixray.AntiXrayPlugin;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class MaterialSet {

    private static final Logger LOGGER = Logger.getLogger("AntiXray");

    private volatile int[] transparentSorted = new int[0];
    private volatile int[] hiddenSorted = new int[0];
    private volatile int[] tileEntitySorted = new int[0];
    private volatile int[] hiddenPaletteSorted = new int[0];
    private volatile int[] airBlockStateIdsSorted = new int[0];

    private final Set<Integer> transparentBlocks = new HashSet<>(256);
    private final Set<Integer> hiddenBlockTypes = new HashSet<>(64);
    private final Set<Integer> tileEntityBlocks = new HashSet<>(16);
    private final Set<Integer> hiddenBlockPalette = new HashSet<>(64);
    private final Set<Integer> airBlockStateIds = new HashSet<>(4);
    private final Set<Material> customHiddenBlocks = ConcurrentHashMap.newKeySet();

    private final int replacementOverworld;
    private final int replacementOverworldDeep;
    private final int replacementNether;
    private final int replacementEnd;

    private boolean lavaObscures = true;
    private boolean leavesAreTransparent = true;
    private boolean airInHiddenBlocks = false;

    private Set<Material> baseTransparent;
    private Set<Material> leafMaterials;
    private Set<Material> tileEntityDefaults;
    private boolean materialSetsInitialized = false;

    public synchronized void initializeMaterialSets() {
        if (materialSetsInitialized) return;

        baseTransparent = EnumSet.noneOf(Material.class);
        baseTransparent.add(Material.AIR);
        baseTransparent.add(Material.CAVE_AIR);
        baseTransparent.add(Material.VOID_AIR);
        baseTransparent.add(Material.WATER);
        baseTransparent.add(Material.LAVA);
        baseTransparent.add(Material.GLASS);
        baseTransparent.add(Material.TINTED_GLASS);
        baseTransparent.add(Material.GLASS_PANE);

        for (Material mat : Material.values()) {
            if (mat.isAir() && !baseTransparent.contains(mat)) {
                baseTransparent.add(mat);
            }
            String name = mat.name();
            if (name.contains("STAINED_GLASS") && !name.equals("GLASS") && !name.equals("GLASS_PANE")
                    && !name.equals("TINTED_GLASS")) {
                baseTransparent.add(mat);
            }
            if (name.contains("SIGN")) baseTransparent.add(mat);
            if (name.contains("BANNER")) baseTransparent.add(mat);
            if (name.contains("CARPET")) baseTransparent.add(mat);
            if (name.contains("SLAB")) baseTransparent.add(mat);
            if (name.contains("STAIRS")) baseTransparent.add(mat);
            if (name.contains("RAIL")) baseTransparent.add(mat);
            if (name.equals("IRON_BARS")) baseTransparent.add(mat);
            if (name.contains("FENCE_GATE")) baseTransparent.add(mat);
            if (name.contains("ANVIL")) baseTransparent.add(mat);
            if (name.contains("CAULDRON")) baseTransparent.add(mat);
            if (name.equals("COMPOSTER")) baseTransparent.add(mat);
            if (isWallBlock(name)) baseTransparent.add(mat);
            if (isPlantLike(name)) baseTransparent.add(mat);
            if (name.equals("SNOW") || name.equals("ICE") || name.equals("PACKED_ICE")
                    || name.equals("BLUE_ICE") || name.equals("FROSTED_ICE")
                    || name.equals("POWDER_SNOW")) baseTransparent.add(mat);
            if (name.contains("CANDLE")) baseTransparent.add(mat);
            if (name.contains("AMETHYST_CLUSTER") || name.contains("AMETHYST_BUD")) baseTransparent.add(mat);
            if (name.contains("POINTED_DRIPSTONE")) baseTransparent.add(mat);
            if (name.equals("SCULK_VEIN")) baseTransparent.add(mat);
            if (name.contains("CHAIN")) baseTransparent.add(mat);
            if (name.equals("LANTERN") || name.equals("SOUL_LANTERN")) baseTransparent.add(mat);
            if (isMiscTransparent(name)) baseTransparent.add(mat);
            if (mat.hasGravity()) baseTransparent.add(mat);
        }
        baseTransparent.removeIf(m -> !m.isBlock());

        leafMaterials = EnumSet.noneOf(Material.class);
        for (Material mat : Material.values()) {
            if (mat.name().contains("LEAVES")) leafMaterials.add(mat);
        }

        tileEntityDefaults = EnumSet.of(
                Material.SPAWNER,
                Material.CHEST,
                Material.ENDER_CHEST,
                Material.TRAPPED_CHEST
        );

        materialSetsInitialized = true;
    }

    private static boolean isWallBlock(String name) {
        if (!name.contains("WALL")) return false;
        if (name.contains("WALL_SIGN") || name.contains("WALL_BANNER")
                || name.contains("WALL_HEAD") || name.contains("WALL_SKULL")
                || name.contains("WALL_TORCH") || name.contains("WALL_CORAL")
                || name.contains("WALL_FAN")) {
            return false;
        }
        return true;
    }

    private static boolean isPlantLike(String name) {
        return name.contains("PLANT") || name.contains("FLOWER") || name.contains("MUSHROOM")
                || name.contains("SAPLING") || name.equals("GRASS") || name.contains("FERN")
                || name.contains("BUSH") || name.contains("CROP") || name.contains("STEM")
                || name.contains("VINE") || name.contains("WEEPING") || name.contains("TWISTING")
                || name.contains("ROOTS") || name.contains("SPORE") || name.contains("AZALEA")
                || name.contains("MOSS_CARPET") || name.contains("DRIPLEAF")
                || name.contains("SPORE_BLOSSOM") || name.contains("CAVE_VINE")
                || name.contains("HANGING_ROOTS") || name.equals("TALL_GRASS")
                || name.equals("LARGE_FERN") || name.contains("SUNFLOWER")
                || name.equals("LILAC") || name.equals("ROSE_BUSH")
                || name.equals("PEONY") || name.contains("PITCHER")
                || name.contains("TORCHFLOWER") || name.equals("PINK_PETALS");
    }

    private static boolean isMiscTransparent(String name) {
        return name.equals("COCOA") || name.equals("SUGAR_CANE") || name.equals("BAMBOO")
                || name.equals("CACTUS") || name.equals("KELP") || name.contains("SEAGRASS")
                || name.contains("CORAL") || name.equals("SEA_PICKLE")
                || name.contains("LILY_PAD") || name.equals("CONDUIT");
    }

    public MaterialSet(int replacementOverworld, int replacementOverworldDeep,
                       int replacementNether, int replacementEnd) {
        this.replacementOverworld = replacementOverworld;
        this.replacementOverworldDeep = replacementOverworldDeep;
        this.replacementNether = replacementNether;
        this.replacementEnd = replacementEnd;
    }

    public boolean isTransparent(int blockStateId) {
        return binarySearch(transparentSorted, blockStateId) >= 0;
    }

    public boolean isHidden(int blockStateId) {
        if (binarySearch(hiddenSorted, blockStateId) >= 0) {
            return true;
        }
        if (!customHiddenBlocks.isEmpty()) {
            Material type = AntiXrayPlugin.getInstance().getNmsAdapter().getTypeFromId(blockStateId);
            return customHiddenBlocks.contains(type);
        }
        return false;
    }

    public synchronized void registerCustomHiddenBlock(NmsAdapter adapter, Material material) {
        if (material == null || !material.isBlock()) return;
        customHiddenBlocks.add(material);
        try {
            int id = adapter.getBlockStateId(material);
            if (id >= 0) {
                hiddenBlockTypes.add(id);
                hiddenBlockPalette.add(id);
                rebuildSortedArrays();
            }
        } catch (Exception ignored) {}
    }

    public synchronized void unregisterCustomHiddenBlock(NmsAdapter adapter, Material material) {
        if (material == null) return;
        customHiddenBlocks.remove(material);
        try {
            int id = adapter.getBlockStateId(material);
            if (id >= 0) {
                hiddenBlockTypes.remove(id);
                hiddenBlockPalette.remove(id);
                rebuildSortedArrays();
            }
        } catch (Exception ignored) {}
    }

    public boolean isTileEntity(int blockStateId) {
        return binarySearch(tileEntitySorted, blockStateId) >= 0;
    }

    public boolean isHiddenPaletteBlock(int blockStateId) {
        return binarySearch(hiddenPaletteSorted, blockStateId) >= 0;
    }

    public boolean isAirBlock(int blockStateId) {
        return binarySearch(airBlockStateIdsSorted, blockStateId) >= 0;
    }

    public boolean isAirInHiddenBlocks() {
        return airInHiddenBlocks;
    }

    public Set<Integer> getHiddenBlockPalette() {
        return Collections.unmodifiableSet(hiddenBlockPalette);
    }

    public int[] getHiddenBlockPaletteArray() {
        return hiddenPaletteSorted.clone();
    }

    public int getReplacement(int y, int deepslateBelowY, String environment) {
        return switch (environment) {
            case "NETHER" -> replacementNether;
            case "THE_END" -> replacementEnd;
            default -> y < deepslateBelowY ? replacementOverworldDeep : replacementOverworld;
        };
    }

    public int getReplacementOverworld() {
        return replacementOverworld;
    }

    public int getReplacementOverworldDeep() {
        return replacementOverworldDeep;
    }

    public int getReplacementNether() {
        return replacementNether;
    }

    public int getReplacementEnd() {
        return replacementEnd;
    }

    public boolean isLavaObscures() {
        return lavaObscures;
    }

    public void setLavaObscures(boolean lavaObscures) {
        this.lavaObscures = lavaObscures;
    }

    public boolean isLeavesAreTransparent() {
        return leavesAreTransparent;
    }

    public void setLeavesAreTransparent(boolean leavesAreTransparent) {
        this.leavesAreTransparent = leavesAreTransparent;
    }

    public void populateTransparentBlocks(NmsAdapter adapter) {
        initializeMaterialSets();
        transparentBlocks.clear();
        airBlockStateIds.clear();
        addMaterialStateIds(adapter, baseTransparent, transparentBlocks);
        if (leavesAreTransparent) {
            addMaterialStateIds(adapter, leafMaterials, transparentBlocks);
        }
        if (lavaObscures) {
            removeMaterialStateIds(adapter, EnumSet.of(Material.LAVA), transparentBlocks);
        }
        transparentSorted = toSortedArray(transparentBlocks);
        for (Material mat : Material.values()) {
            if (mat.isBlock() && mat.isAir()) {
                try {
                    int id = adapter.getBlockStateId(mat);
                    if (id >= 0) {
                        airBlockStateIds.add(id);
                    }
                } catch (Exception ignored) {
                }
            }
        }
        airBlockStateIdsSorted = toSortedArray(airBlockStateIds);
    }

    public void populateHiddenBlockTypes(NmsAdapter adapter, List<String> hiddenBlockNames) {
        initializeMaterialSets();
        hiddenBlockTypes.clear();
        tileEntityBlocks.clear();
        hiddenBlockPalette.clear();
        airInHiddenBlocks = false;
        addStateIdsFromNames(adapter, hiddenBlockNames, hiddenBlockTypes);
        addMaterialStateIds(adapter, tileEntityDefaults, tileEntityBlocks);
        hiddenBlockPalette.addAll(hiddenBlockTypes);
        hiddenSorted = toSortedArray(hiddenBlockTypes);
        tileEntitySorted = toSortedArray(tileEntityBlocks);
        hiddenPaletteSorted = toSortedArray(hiddenBlockPalette);
        detectAirInHiddenBlocks(adapter, hiddenBlockNames);
    }

    private void detectAirInHiddenBlocks(NmsAdapter adapter, List<String> hiddenBlockNames) {
        for (String name : hiddenBlockNames) {
            try {
                Material mat = Material.matchMaterial(name);
                if (mat == null) {
                    mat = Material.matchMaterial(name.toUpperCase(Locale.ROOT).replace(" ", "_"));
                }
                if (mat != null && mat.isAir()) {
                    airInHiddenBlocks = true;
                    return;
                }
            } catch (Exception ignored) {
            }
        }
    }

    public void populateTileEntityBlocks(NmsAdapter adapter, List<String> additionalTileEntityNames) {
        addStateIdsFromNames(adapter, additionalTileEntityNames, tileEntityBlocks);
        tileEntitySorted = toSortedArray(tileEntityBlocks);
    }

    public void rebuildSortedArrays() {
        transparentSorted = toSortedArray(transparentBlocks);
        hiddenSorted = toSortedArray(hiddenBlockTypes);
        tileEntitySorted = toSortedArray(tileEntityBlocks);
        hiddenPaletteSorted = toSortedArray(hiddenBlockPalette);
    }

    public int computeConfigHash() {
        int hash = 1;
        for (int id : hiddenBlockTypes) hash = 31 * hash + id;
        hash = 31 * hash + replacementOverworld;
        hash = 31 * hash + replacementOverworldDeep;
        hash = 31 * hash + replacementNether;
        hash = 31 * hash + replacementEnd;
        hash = 31 * hash + (lavaObscures ? 1 : 0);
        hash = 31 * hash + (leavesAreTransparent ? 1 : 0);
        return hash;
    }

    private void addMaterialStateIds(NmsAdapter adapter, Set<Material> materials, Set<Integer> target) {
        for (Material mat : materials) {
            if (!mat.isBlock()) continue;
            try {
                int id = adapter.getBlockStateId(mat);
                if (id >= 0) target.add(id);
            } catch (Exception e) {
                LOGGER.fine("Could not resolve block state ID for " + mat.name() + ": " + e.getMessage());
            }
        }
    }

    private void removeMaterialStateIds(NmsAdapter adapter, Set<Material> materials, Set<Integer> target) {
        for (Material mat : materials) {
            if (!mat.isBlock()) continue;
            try {
                int id = adapter.getBlockStateId(mat);
                target.remove(id);
            } catch (Exception ignored) {
            }
        }
    }

    private void addStateIdsFromNames(NmsAdapter adapter, List<String> names, Set<Integer> target) {
        for (String name : names) {
            try {
                Material mat = Material.matchMaterial(name);
                if (mat == null) {
                    mat = Material.matchMaterial(name.toUpperCase(Locale.ROOT).replace(" ", "_"));
                }
                if (mat == null || !mat.isBlock()) {
                    LOGGER.warning("Unknown or non-block material in config: " + name);
                    continue;
                }
                int id = adapter.getBlockStateId(mat);
                if (id >= 0) target.add(id);
            } catch (Exception e) {
                LOGGER.warning("Failed to resolve block state for: " + name + " - " + e.getMessage());
            }
        }
    }

    private static int binarySearch(int[] arr, int key) {
        int low = 0;
        int high = arr.length - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int midVal = arr[mid];
            if (midVal < key) low = mid + 1;
            else if (midVal > key) high = mid - 1;
            else return mid;
        }
        return -(low + 1);
    }

    private static int[] toSortedArray(Set<Integer> set) {
        int[] arr = new int[set.size()];
        int i = 0;
        for (int val : set) arr[i++] = val;
        Arrays.sort(arr);
        return arr;
    }
}
