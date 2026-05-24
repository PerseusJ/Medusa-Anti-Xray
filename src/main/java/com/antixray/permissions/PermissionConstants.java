package com.antixray.permissions;

/**
 * All permission node constants used by the AntiXray plugin.
 *
 * <p>These constants match the permission nodes declared in plugin.yml.
 * Sub-commands should check the appropriate permission before executing.</p>
 */
public final class PermissionConstants {

    private PermissionConstants() {
        // Utility class — no instantiation
    }

    /** Skip obfuscation for this player — they see real blocks. */
    public static final String BYPASS = "antixray.bypass";

    /** Access to all admin commands (parent node). */
    public static final String ADMIN = "antixray.admin";

    /** Receive X-ray detection alerts in chat. */
    public static final String NOTIFY = "antixray.notify";

    /** Reload configuration via {@code /antixray reload}. */
    public static final String RELOAD = "antixray.reload";

    /** View detection statistics. */
    public static final String STATS = "antixray.stats";

    /** Manual review of player mining patterns. */
    public static final String CHECK = "antixray.check";

    /** Change engine mode at runtime. */
    public static final String MODE = "antixray.mode";

    /** Manage the obfuscation cache. */
    public static final String CACHE = "antixray.cache";

    /** Enable or disable the plugin per world. */
    public static final String TOGGLE = "antixray.toggle";
}
