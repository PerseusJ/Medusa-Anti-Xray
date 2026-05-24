package com.antixray.util;

import org.bukkit.Bukkit;

public final class VersionUtil {

    private static String cachedNmsVersion;
    private static Boolean cachedIsPaper;
    private static Boolean cachedIsFolia;
    private static int[] cachedMinecraftVersion;

    private VersionUtil() {
    }

    public static String getNmsVersion() {
        if (cachedNmsVersion != null) return cachedNmsVersion;

        cachedNmsVersion = detectNmsVersionFromPackage();
        if (cachedNmsVersion != null) return cachedNmsVersion;

        int[] version = getMinecraftVersion();
        cachedNmsVersion = inferNmsVersion(version);
        return cachedNmsVersion;
    }

    private static String detectNmsVersionFromPackage() {
        try {
            Package pkg = Bukkit.getServer().getClass().getPackage();
            if (pkg == null) return null;
            String name = pkg.getName();
            String[] parts = name.split("\\.");
            if (parts.length >= 4) {
                String candidate = parts[3];
                if (candidate.startsWith("v1_")) {
                    return candidate;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String inferNmsVersion(int[] version) {
        int minor = version.length > 1 ? version[1] : 0;
        int patch = version.length > 2 ? version[2] : 0;

        if (minor >= 21) {
            if (patch >= 4) return "v1_21_R3";
            if (patch >= 2) return "v1_21_R2";
            return "v1_21_R1";
        }
        if (minor == 20) {
            if (patch >= 5) return "v1_20_R4";
            if (patch >= 3) return "v1_20_R3";
            if (patch >= 2) return "v1_20_R2";
            return "v1_20_R1";
        }
        if (minor == 19) {
            return "v1_19_R3";
        }
        if (minor >= 22) {
            return "v1_21_R3";
        }
        return "v1_21_R3";
    }

    public static boolean isPaper() {
        if (cachedIsPaper != null) return cachedIsPaper;
        try {
            Class.forName("com.destroystokyo.paper.PaperConfig");
            cachedIsPaper = true;
            return true;
        } catch (ClassNotFoundException ignored) {
        }
        try {
            Class.forName("io.papermc.paper.configuration.Configuration");
            cachedIsPaper = true;
            return true;
        } catch (ClassNotFoundException ignored) {
        }
        try {
            Class.forName("io.papermc.paper.ServerBuildInfo");
            cachedIsPaper = true;
            return true;
        } catch (ClassNotFoundException ignored) {
        }
        cachedIsPaper = false;
        return false;
    }

    public static boolean isFolia() {
        if (cachedIsFolia != null) return cachedIsFolia;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            cachedIsFolia = true;
            return true;
        } catch (ClassNotFoundException ignored) {
        }
        cachedIsFolia = false;
        return false;
    }

    public static int[] getMinecraftVersion() {
        if (cachedMinecraftVersion != null) return cachedMinecraftVersion;
        try {
            String bukkitVersion = Bukkit.getBukkitVersion();
            cachedMinecraftVersion = parseVersionString(bukkitVersion);
            return cachedMinecraftVersion;
        } catch (Exception ignored) {
        }
        cachedMinecraftVersion = new int[]{1, 21, 4};
        return cachedMinecraftVersion;
    }

    private static int[] parseVersionString(String version) {
        String cleaned = version.split("-")[0].split(" ")[0].trim();
        String[] parts = cleaned.split("\\.");
        int major = parts.length > 0 ? parsePositiveInt(parts[0], 1) : 1;
        int minor = parts.length > 1 ? parsePositiveInt(parts[1], 0) : 0;
        int patch = parts.length > 2 ? parsePositiveInt(parts[2], 0) : 0;
        return new int[]{major, minor, patch};
    }

    private static int parsePositiveInt(String s, int defaultVal) {
        try {
            int val = Integer.parseInt(s.trim());
            return val >= 0 ? val : defaultVal;
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    public static void resetCache() {
        cachedNmsVersion = null;
        cachedIsPaper = null;
        cachedIsFolia = null;
        cachedMinecraftVersion = null;
    }
}
