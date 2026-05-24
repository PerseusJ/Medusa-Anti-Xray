package com.antixray.util;

import java.util.Collections;
import java.util.List;

public final class FoliaCompat {

    public static final String FOLIA_EXPERIMENTAL_KEY = "warning-folia-experimental";
    public static final String LIMITATION_ASYNC_TIMER_KEY = "warning-folia-limitation-async-timer";
    public static final String LIMITATION_ENTITY_SCHEDULER_KEY = "warning-folia-limitation-entity-scheduler";
    public static final String LIMITATION_GLOBAL_REGION_KEY = "warning-folia-limitation-global-region";
    public static final String LIMITATION_IS_PRIMARY_THREAD_KEY = "warning-folia-limitation-is-primary-thread";
    public static final String LIMITATION_PLAYER_EVENT_SCHEDULING_KEY = "warning-folia-limitation-player-event-scheduling";
    public static final String LIMITATION_REGION_BASED_DEOBFUSCATION_KEY = "warning-folia-limitation-region-reobfuscation";

    private static volatile boolean markedExperimental = false;

    private FoliaCompat() {
    }

    public static boolean isFolia() {
        return VersionUtil.isFolia();
    }

    public static boolean isMarkedExperimental() {
        return markedExperimental;
    }

    public static void markExperimental(boolean experimental) {
        markedExperimental = experimental;
    }

    public static String getFoliaStatus() {
        if (!isFolia()) {
            return "NOT_RUNNING";
        }
        return markedExperimental ? "EXPERIMENTAL" : "SUPPORTED";
    }

    public static List<String> getKnownLimitations() {
        if (!isFolia()) {
            return Collections.emptyList();
        }
        return List.of(
            LIMITATION_ASYNC_TIMER_KEY,
            LIMITATION_ENTITY_SCHEDULER_KEY,
            LIMITATION_GLOBAL_REGION_KEY,
            LIMITATION_IS_PRIMARY_THREAD_KEY,
            LIMITATION_PLAYER_EVENT_SCHEDULING_KEY,
            LIMITATION_REGION_BASED_DEOBFUSCATION_KEY
        );
    }

    public static List<String> getKnownLimitationDescriptions() {
        if (!isFolia()) {
            return Collections.emptyList();
        }
        return List.of(
            "Async timers use wall-clock time (ticks converted at 50ms/tick) instead of server tick timing.",
            "Entity-bound tasks may fail if the entity is unloaded or removed during scheduling.",
            "Global region tasks run on Folia's global region scheduler, not on a single main thread.",
            "Location-based scheduling uses chunk coordinates; operations may be delayed if the region is unloaded.",
            "Cancellation of Folia tasks uses reflection; cancellation may not be instantaneous.",
            "Bukkit.isPrimaryThread() always returns false on Folia; use FoliaSchedulerAdapter.isGlobalRegionThread() instead.",
            "Player-bound delayed tasks (join, gamemode change) use EntityScheduler on Folia; tasks are cancelled if the player disconnects before execution.",
            "Deobfuscation flush tasks run on the global region scheduler; per-player packet sends may be delayed by region tick boundaries."
        );
    }
}
