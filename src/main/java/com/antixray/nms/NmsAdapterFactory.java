package com.antixray.nms;

import com.antixray.util.VersionUtil;

import java.util.logging.Logger;

public class NmsAdapterFactory {

    private static final Logger LOGGER = Logger.getLogger("AntiXray");

    private static final String ADAPTER_PACKAGE = "com.antixray.nms";

    private static final String[][] ADAPTER_VERSIONS = {
        {"v1_21_R3", "v1_21"},
        {"v1_21_R2", "v1_21"},
        {"v1_21_R1", "v1_21"},
        {"v1_20_R4", "v1_20"},
        {"v1_20_R3", "v1_20"},
        {"v1_20_R2", "v1_20"},
        {"v1_20_R1", "v1_20"},
        {"v1_19_R3", "v1_19"}
    };

    private NmsAdapterFactory() {
    }

    public static NmsAdapter create() {
        String nmsVersion = VersionUtil.getNmsVersion();
        return create(nmsVersion);
    }

    public static NmsAdapter create(String nmsVersion) {
        NmsAdapter exactAdapter = tryLoad(nmsVersion);
        if (exactAdapter != null) {
            LOGGER.info("Loaded NMS adapter: " + nmsVersion);
            return exactAdapter;
        }

        LOGGER.warning("No exact adapter for NMS version " + nmsVersion + ", trying fallbacks");

        for (String[] entry : ADAPTER_VERSIONS) {
            String version = entry[0];
            if (version.equals(nmsVersion)) {
                continue;
            }
            NmsAdapter fallback = tryLoad(version);
            if (fallback != null) {
                LOGGER.warning("Using fallback NMS adapter: " + version + " (requested: " + nmsVersion + ")");
                return fallback;
            }
        }

        LOGGER.severe("No compatible NMS adapter found for version " + nmsVersion);
        return null;
    }

    private static NmsAdapter tryLoad(String nmsVersion) {
        String subPackage = toSubPackage(nmsVersion);
        String className = ADAPTER_PACKAGE + "." + subPackage + ".NmsAdapter_" + nmsVersion;
        try {
            Class<?> clazz = Class.forName(className);
            return (NmsAdapter) clazz.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException e) {
            LOGGER.fine("Adapter class not found: " + className);
            return null;
        } catch (Exception e) {
            LOGGER.warning("Failed to instantiate adapter " + className + ": " + e.getMessage());
            return null;
        }
    }

    private static String toSubPackage(String nmsVersion) {
        int secondUnderscore = nmsVersion.indexOf('_', nmsVersion.indexOf('_') + 1);
        if (secondUnderscore < 0) {
            return nmsVersion.toLowerCase();
        }
        return nmsVersion.substring(0, secondUnderscore).toLowerCase();
    }
}
