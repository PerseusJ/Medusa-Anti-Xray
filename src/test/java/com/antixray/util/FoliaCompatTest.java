package com.antixray.util;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FoliaCompatTest {

    private Server oldServer;

    @BeforeEach
    void setUp() throws Exception {
        Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        oldServer = (Server) serverField.get(null);

        Server server = mock(Server.class);
        serverField.set(null, server);
    }

    @AfterEach
    void tearDown() throws Exception {
        Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(null, oldServer);
        VersionUtil.resetCache();
        FoliaCompat.markExperimental(false);
    }

    @Test
    void isFoliaReturnsFalseOnNonFolia() {
        VersionUtil.resetCache();
        assertFalse(FoliaCompat.isFolia());
    }

    @Test
    void getKnownLimitationsReturnsEmptyOnNonFolia() {
        VersionUtil.resetCache();
        assertTrue(FoliaCompat.getKnownLimitations().isEmpty());
    }

    @Test
    void getKnownLimitationDescriptionsReturnsEmptyOnNonFolia() {
        VersionUtil.resetCache();
        assertTrue(FoliaCompat.getKnownLimitationDescriptions().isEmpty());
    }

    @Test
    void limitationKeysAreConsistent() {
        assertEquals("warning-folia-limitation-async-timer", FoliaCompat.LIMITATION_ASYNC_TIMER_KEY);
        assertEquals("warning-folia-limitation-entity-scheduler", FoliaCompat.LIMITATION_ENTITY_SCHEDULER_KEY);
        assertEquals("warning-folia-limitation-global-region", FoliaCompat.LIMITATION_GLOBAL_REGION_KEY);
        assertEquals("warning-folia-experimental", FoliaCompat.FOLIA_EXPERIMENTAL_KEY);
        assertEquals("warning-folia-limitation-is-primary-thread", FoliaCompat.LIMITATION_IS_PRIMARY_THREAD_KEY);
        assertEquals("warning-folia-limitation-player-event-scheduling", FoliaCompat.LIMITATION_PLAYER_EVENT_SCHEDULING_KEY);
        assertEquals("warning-folia-limitation-region-reobfuscation", FoliaCompat.LIMITATION_REGION_BASED_DEOBFUSCATION_KEY);
    }

    @Test
    void getKnownLimitationsReturnsAllKeysOnFolia() throws Exception {
        setFoliaMode(true);
        List<String> limitations = FoliaCompat.getKnownLimitations();
        assertEquals(6, limitations.size());
        assertTrue(limitations.contains(FoliaCompat.LIMITATION_ASYNC_TIMER_KEY));
        assertTrue(limitations.contains(FoliaCompat.LIMITATION_ENTITY_SCHEDULER_KEY));
        assertTrue(limitations.contains(FoliaCompat.LIMITATION_GLOBAL_REGION_KEY));
        assertTrue(limitations.contains(FoliaCompat.LIMITATION_IS_PRIMARY_THREAD_KEY));
        assertTrue(limitations.contains(FoliaCompat.LIMITATION_PLAYER_EVENT_SCHEDULING_KEY));
        assertTrue(limitations.contains(FoliaCompat.LIMITATION_REGION_BASED_DEOBFUSCATION_KEY));
    }

    @Test
    void getKnownLimitationDescriptionsReturnsAllOnFolia() throws Exception {
        setFoliaMode(true);
        List<String> descriptions = FoliaCompat.getKnownLimitationDescriptions();
        assertEquals(8, descriptions.size());
        assertTrue(descriptions.stream().anyMatch(d -> d.contains("isPrimaryThread")));
        assertTrue(descriptions.stream().anyMatch(d -> d.contains("EntityScheduler")));
        assertTrue(descriptions.stream().anyMatch(d -> d.contains("Global region")));
        assertTrue(descriptions.stream().anyMatch(d -> d.contains("wall-clock")));
        assertTrue(descriptions.stream().anyMatch(d -> d.contains("Cancellation")));
        assertTrue(descriptions.stream().anyMatch(d -> d.contains("isGlobalRegionThread")));
        assertTrue(descriptions.stream().anyMatch(d -> d.contains("disconnects")));
        assertTrue(descriptions.stream().anyMatch(d -> d.contains("region tick boundaries")));
    }

    @Test
    void getFoliaStatusReturnsNotRunningOnNonFolia() {
        VersionUtil.resetCache();
        assertEquals("NOT_RUNNING", FoliaCompat.getFoliaStatus());
    }

    @Test
    void getFoliaStatusReturnsSupportedOnFoliaByDefault() throws Exception {
        setFoliaMode(true);
        assertEquals("SUPPORTED", FoliaCompat.getFoliaStatus());
    }

    @Test
    void getFoliaStatusReturnsExperimentalWhenMarked() throws Exception {
        setFoliaMode(true);
        FoliaCompat.markExperimental(true);
        assertEquals("EXPERIMENTAL", FoliaCompat.getFoliaStatus());
    }

    @Test
    void markExperimentalDoesNotAffectNonFolia() {
        FoliaCompat.markExperimental(true);
        assertEquals("NOT_RUNNING", FoliaCompat.getFoliaStatus());
    }

    @Test
    void isMarkedExperimentalDefaultFalse() {
        assertFalse(FoliaCompat.isMarkedExperimental());
    }

    @Test
    void markExperimentalToggles() {
        FoliaCompat.markExperimental(true);
        assertTrue(FoliaCompat.isMarkedExperimental());
        FoliaCompat.markExperimental(false);
        assertFalse(FoliaCompat.isMarkedExperimental());
    }

    @Test
    void limitationDescriptionsMatchKeysCount() throws Exception {
        setFoliaMode(true);
        List<String> keys = FoliaCompat.getKnownLimitations();
        List<String> descriptions = FoliaCompat.getKnownLimitationDescriptions();
        assertTrue(descriptions.size() >= keys.size(),
            "Should have at least as many descriptions as limitation keys");
    }

    @Test
    void privateConstructorCoverage() throws Exception {
        java.lang.reflect.Constructor<FoliaCompat> ctor = FoliaCompat.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        FoliaCompat instance = ctor.newInstance();
        assertNotNull(instance);
    }

    private void setFoliaMode(boolean folia) throws Exception {
        Field foliaField = VersionUtil.class.getDeclaredField("cachedIsFolia");
        foliaField.setAccessible(true);
        foliaField.set(null, folia);
    }
}
