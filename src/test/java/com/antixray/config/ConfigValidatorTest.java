package com.antixray.config;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.List;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConfigValidatorTest {

    private Logger logger;
    private MockedStatic<Bukkit> bukkitMock;
    private MockedStatic<Material> materialMock;

    @BeforeEach
    void setUp() {
        logger = mock(Logger.class);
        Server server = mock(Server.class);
        lenient().when(server.getName()).thenReturn("TestServer");
        lenient().when(server.getVersion()).thenReturn("1.21");
        lenient().when(server.getBukkitVersion()).thenReturn("1.21-R0.1-SNAPSHOT");
        bukkitMock = Mockito.mockStatic(Bukkit.class);
        bukkitMock.when(Bukkit::getServer).thenReturn(server);

        materialMock = Mockito.mockStatic(Material.class);
        materialMock.when(() -> Material.matchMaterial(eq("fake_material_that_does_not_exist"))).thenReturn(null);
        materialMock.when(() -> Material.matchMaterial(eq("FAKE_MATERIAL_THAT_DOES_NOT_EXIST"))).thenReturn(null);
    }

    @AfterEach
    void tearDown() {
        if (materialMock != null) {
            materialMock.close();
        }
        if (bukkitMock != null) {
            bukkitMock.close();
        }
    }

    @Test
    void invalidEngineMode_outOfRange_fallbackTo3() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("engine-mode", 5);

        ConfigValidator validator = new ConfigValidator(logger);
        int issues = validator.validate(config);

        assertTrue(issues > 0);
        assertEquals(3, config.getInt("engine-mode"));
    }

    @Test
    void invalidEngineMode_zero_fallbackTo3() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("engine-mode", 0);

        ConfigValidator validator = new ConfigValidator(logger);
        int issues = validator.validate(config);

        assertTrue(issues > 0);
        assertEquals(3, config.getInt("engine-mode"));
    }

    @Test
    void invalidEngineMode_negative_fallbackTo3() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("engine-mode", -1);

        ConfigValidator validator = new ConfigValidator(logger);
        int issues = validator.validate(config);

        assertTrue(issues > 0);
        assertEquals(3, config.getInt("engine-mode"));
    }

    @Test
    void invalidEngineMode_invalidString_fallbackTo3() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("engine-mode", "invalid");

        ConfigValidator validator = new ConfigValidator(logger);
        int issues = validator.validate(config);

        assertTrue(issues > 0);
        assertEquals(3, config.getInt("engine-mode"));
    }

    @Test
    void validEngineMode_1_accepted() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("engine-mode", 1);

        ConfigValidator validator = new ConfigValidator(logger);
        int issues = validator.validate(config);

        assertEquals(0, issues);
        assertEquals(1, config.getInt("engine-mode"));
    }

    @Test
    void validEngineMode_2_accepted() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("engine-mode", 2);

        ConfigValidator validator = new ConfigValidator(logger);
        int issues = validator.validate(config);

        assertEquals(0, issues);
        assertEquals(2, config.getInt("engine-mode"));
    }

    @Test
    void validEngineMode_3_accepted() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("engine-mode", 3);

        ConfigValidator validator = new ConfigValidator(logger);
        int issues = validator.validate(config);

        assertEquals(0, issues);
        assertEquals(3, config.getInt("engine-mode"));
    }

    @Test
    void emptyHiddenBlocks_noWarning() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("hidden-blocks", List.of());

        ConfigValidator validator = new ConfigValidator(logger);
        validator.validate(config);

        verify(logger, never()).warning(contains("hidden-blocks"));
    }

    @Test
    void hiddenBlocksNotList_fallbackToEmpty() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("hidden-blocks", "not-a-list");

        ConfigValidator validator = new ConfigValidator(logger);
        int issues = validator.validate(config);

        assertTrue(issues > 0);
        assertTrue(config.getStringList("hidden-blocks").isEmpty());
    }

    @Test
    void invalidMaterialName_inHiddenBlocks_notResolvedByValidator() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("hidden-blocks", List.of("fake_material_that_does_not_exist"));

        ConfigValidator validator = new ConfigValidator(logger);
        validator.validate(config);

        assertFalse(config.getStringList("hidden-blocks").isEmpty(),
                "ConfigValidator doesn't resolve material names — that is done by ConfigurationManager");
    }

    @Test
    void maxBlockHeight_outOfRange_defaultsTo64() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("max-block-height", 500);

        ConfigValidator validator = new ConfigValidator(logger);
        int issues = validator.validate(config);

        assertTrue(issues > 0);
        assertEquals(64, config.getInt("max-block-height"));
    }

    @Test
    void maxBlockHeight_negative_defaultsTo64() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("max-block-height", -10);

        ConfigValidator validator = new ConfigValidator(logger);
        int issues = validator.validate(config);

        assertTrue(issues > 0);
        assertEquals(64, config.getInt("max-block-height"));
    }

    @Test
    void fakeOreChance_outOfRange_defaults() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("fake-ore-chance", 2.0);

        ConfigValidator validator = new ConfigValidator(logger);
        int issues = validator.validate(config);

        assertTrue(issues > 0);
        assertEquals(0.07, config.getDouble("fake-ore-chance"), 0.001);
    }

    @Test
    void fakeOreChance_negative_defaults() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("fake-ore-chance", -0.5);

        ConfigValidator validator = new ConfigValidator(logger);
        int issues = validator.validate(config);

        assertTrue(issues > 0);
        assertEquals(0.07, config.getDouble("fake-ore-chance"), 0.001);
    }

    @Test
    void deepslateBelowY_outOfRange_defaultsTo0() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("deepslate-below-y", 500);

        ConfigValidator validator = new ConfigValidator(logger);
        int issues = validator.validate(config);

        assertTrue(issues > 0);
        assertEquals(0, config.getInt("deepslate-below-y"));
    }

    @Test
    void enabled_notBoolean_defaultsToTrue() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("enabled", "yes");

        ConfigValidator validator = new ConfigValidator(logger);
        int issues = validator.validate(config);

        assertTrue(issues > 0);
        assertTrue(config.getBoolean("enabled"));
    }

    @Test
    void lavaObscures_notBoolean_defaultsToTrue() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("lava-obscures", "no");

        ConfigValidator validator = new ConfigValidator(logger);
        int issues = validator.validate(config);

        assertTrue(issues > 0);
        assertTrue(config.getBoolean("lava-obscures"));
    }

    @Test
    void leavesAreTransparent_notBoolean_defaultsToTrue() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("leaves-are-transparent", 123);

        ConfigValidator validator = new ConfigValidator(logger);
        int issues = validator.validate(config);

        assertTrue(issues > 0);
        assertTrue(config.getBoolean("leaves-are-transparent"));
    }

    @Test
    void bypassPermission_blank_defaultsToAntixrayBypass() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("bypass-permission", "  ");

        ConfigValidator validator = new ConfigValidator(logger);
        int issues = validator.validate(config);

        assertTrue(issues > 0);
        assertEquals("antixray.bypass", config.getString("bypass-permission"));
    }

    @Test
    void bypassPermission_null_defaultsToAntixrayBypass() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("bypass-permission", "");

        ConfigValidator validator = new ConfigValidator(logger);
        int issues = validator.validate(config);

        assertTrue(issues > 0);
        assertEquals("antixray.bypass", config.getString("bypass-permission"));
    }

    @Test
    void validConfig_noIssues() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("engine-mode", 3);
        config.set("enabled", true);
        config.set("max-block-height", 64);
        config.set("fake-ore-chance", 0.07);
        config.set("lava-obscures", true);
        config.set("leaves-are-transparent", true);
        config.set("bypass-permission", "antixray.bypass");
        config.set("hidden-blocks", List.of("diamond_ore", "iron_ore"));

        ConfigValidator validator = new ConfigValidator(logger);
        int issues = validator.validate(config);

        assertEquals(0, issues);
    }

    @Test
    void missingOptionalFields_noIssues() {
        YamlConfiguration config = new YamlConfiguration();

        ConfigValidator validator = new ConfigValidator(logger);
        int issues = validator.validate(config);

        assertEquals(0, issues);
    }

    @Test
    void multipleIssues_allReported() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("engine-mode", 99);
        config.set("max-block-height", 500);
        config.set("fake-ore-chance", -1.0);
        config.set("enabled", "notbool");

        ConfigValidator validator = new ConfigValidator(logger);
        int issues = validator.validate(config);

        assertTrue(issues >= 4);
    }

    @Test
    void worldOverride_invalidEngineMode_fallbackTo3() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("worlds.test_world.engine-mode", 99);

        ConfigValidator validator = new ConfigValidator(logger);
        int issues = validator.validate(config);

        assertTrue(issues > 0);
        assertEquals(3, config.getInt("worlds.test_world.engine-mode"));
    }

    @Test
    void worldOverride_invalidMaxBlockHeight_defaultsTo64() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("worlds.test_world.max-block-height", 9999);

        ConfigValidator validator = new ConfigValidator(logger);
        int issues = validator.validate(config);

        assertTrue(issues > 0);
        assertEquals(64, config.getInt("worlds.test_world.max-block-height"));
    }

    @Test
    void worldOverride_invalidFakeOreChance_defaults() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("worlds.test_world.fake-ore-chance", 5.0);

        ConfigValidator validator = new ConfigValidator(logger);
        int issues = validator.validate(config);

        assertTrue(issues > 0);
        assertEquals(0.07, config.getDouble("worlds.test_world.fake-ore-chance"), 0.001);
    }

    @Test
    void worldOverride_enabledNotBoolean_defaultsToTrue() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("worlds.test_world.enabled", "yes");

        ConfigValidator validator = new ConfigValidator(logger);
        int issues = validator.validate(config);

        assertTrue(issues > 0);
        assertTrue(config.getBoolean("worlds.test_world.enabled"));
    }

    @Test
    void engineModeString_validFormat_accepted() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("engine-mode", "MODE_1");

        ConfigValidator validator = new ConfigValidator(logger);
        int issues = validator.validate(config);

        assertEquals(0, issues);
    }

    @Test
    void engineModeString_withoutPrefix_accepted() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("engine-mode", "1");

        ConfigValidator validator = new ConfigValidator(logger);
        int issues = validator.validate(config);

        assertEquals(0, issues);
    }

    @Test
    void validateReturnsIssueCount() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("engine-mode", 99);

        ConfigValidator validator = new ConfigValidator(logger);
        int result = validator.validate(config);

        assertTrue(result > 0);
    }

    @Test
    void worldOverride_blankBypassPermission_defaults() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("worlds.test_world.bypass-permission", "");

        ConfigValidator validator = new ConfigValidator(logger);
        int issues = validator.validate(config);

        assertTrue(issues > 0);
        assertEquals("antixray.bypass", config.getString("worlds.test_world.bypass-permission"));
    }

    @Test
    void validateCache_invalidValues_fallbacksApplied() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("cache.l1.max-size", 50); // < 100
        config.set("cache.l1.expiry-seconds", 5); // < 10
        config.set("cache.l2.enabled", "not_a_boolean");
        config.set("cache.l2.max-disk-mb", 5); // < 10
        config.set("cache.l2.expiry-seconds", 8); // < 10

        ConfigValidator validator = new ConfigValidator(logger);
        int issues = validator.validate(config);

        assertTrue(issues > 0);
        assertEquals(5000, config.getInt("cache.l1.max-size"));
        assertEquals(300, config.getInt("cache.l1.expiry-seconds"));
        assertTrue(config.getBoolean("cache.l2.enabled"));
        assertEquals(500, config.getInt("cache.l2.max-disk-mb"));
        assertEquals(86400, config.getInt("cache.l2.expiry-seconds"));
    }

    @Test
    void validateAsync_invalidValues_fallbacksApplied() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("async.pool-size", 100); // > 64
        config.set("async.per-tick-budget-ms", 0); // < 1
        config.set("async.chunk-timeout-ms", 2); // < 5
        config.set("async.max-queue-size", 5); // < 10

        ConfigValidator validator = new ConfigValidator(logger);
        int issues = validator.validate(config);

        assertTrue(issues > 0);
        assertEquals(0, config.getInt("async.pool-size"));
        assertEquals(5, config.getLong("async.per-tick-budget-ms"));
        assertEquals(50, config.getLong("async.chunk-timeout-ms"));
        assertEquals(10000, config.getInt("async.max-queue-size"));
    }
}
