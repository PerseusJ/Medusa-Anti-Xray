package com.antixray.detection;

import com.antixray.api.AlertLevel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class StatisticsStorageTest {

    @TempDir
    File tempDir;

    private StatisticsStorage storage;

    @BeforeEach
    void setUp() {
        storage = new StatisticsStorage(tempDir);
    }

    @AfterEach
    void tearDown() {
        if (storage != null) {
            storage.shutdown();
        }
    }

    private void awaitWrites() throws InterruptedException {
        TimeUnit.MILLISECONDS.sleep(300);
    }

    @Nested
    @DisplayName("Schema initialization")
    class SchemaInitialization {

        @Test
        @DisplayName("Tables are created on initialization")
        void tablesCreatedOnInit() throws Exception {
            try (Connection conn = storage.getConnectionForTest();
                 Statement stmt = conn.createStatement()) {

                ResultSet rs = stmt.executeQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name IN ('player_stats','ore_breaks','alerts') ORDER BY name");
                java.util.Set<String> tables = new java.util.TreeSet<>();
                while (rs.next()) {
                    tables.add(rs.getString("name"));
                }
                assertTrue(tables.contains("player_stats"), "player_stats table should exist");
                assertTrue(tables.contains("ore_breaks"), "ore_breaks table should exist");
                assertTrue(tables.contains("alerts"), "alerts table should exist");
            }
        }

        @Test
        @DisplayName("SQLite WAL mode is enabled")
        void walModeEnabled() throws Exception {
            try (Connection conn = storage.getConnectionForTest();
                 Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery("PRAGMA journal_mode");
                assertTrue(rs.next());
                String mode = rs.getString(1);
                assertEquals("wal", mode.toLowerCase(), "Journal mode should be WAL");
            }
        }

        @Test
        @DisplayName("Indexes are created")
        void indexesCreated() throws Exception {
            try (Connection conn = storage.getConnectionForTest();
                 Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery(
                    "SELECT name FROM sqlite_master WHERE type='index' AND name LIKE 'idx_%' ORDER BY name");
                java.util.Set<String> indexes = new java.util.TreeSet<>();
                while (rs.next()) {
                    indexes.add(rs.getString("name"));
                }
                assertTrue(indexes.contains("idx_ore_breaks_uuid"), "ore_breaks uuid index should exist");
                assertTrue(indexes.contains("idx_ore_breaks_timestamp"), "ore_breaks timestamp index should exist");
                assertTrue(indexes.contains("idx_alerts_uuid"), "alerts uuid index should exist");
                assertTrue(indexes.contains("idx_alerts_timestamp"), "alerts timestamp index should exist");
                assertTrue(indexes.contains("idx_player_stats_last_updated"), "player_stats last_updated index should exist");
            }
        }

        @Test
        @DisplayName("Storage type is sqlite by default")
        void storageTypeIsSqlite() {
            assertFalse(storage.isMysql());
        }
    }

    @Nested
    @DisplayName("player_stats persistence")
    class PlayerStatsPersistence {

        @Test
        @DisplayName("savePlayerStats persists stats to database")
        void savePlayerStats_persistsToDb() throws Exception {
            UUID uuid = UUID.randomUUID();
            PlayerStatistics stats = new PlayerStatistics();
            stats.onBlockBreak(org.bukkit.Material.STONE, 64, org.bukkit.block.Biome.PLAINS, false, null);
            stats.onBlockBreak(org.bukkit.Material.DIAMOND_ORE, -10, org.bukkit.block.Biome.PLAINS, true, null);
            stats.onBlockBreak(org.bukkit.Material.EMERALD_ORE, 11, org.bukkit.block.Biome.PLAINS, false, null);
            stats.updatePlayTime(120);

            storage.savePlayerStats(uuid, "TestPlayer", stats);
            awaitWrites();

            try (Connection conn = storage.getConnectionForTest();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT stone_mined, total_ores_mined, diamonds_mined, emeralds_mined, " +
                     "ancient_debris_mined, total_mined, air_adjacent_mined, play_time_minutes, " +
                     "last_known_name FROM player_stats WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(1, rs.getLong("stone_mined"));
                    assertEquals(2, rs.getLong("total_ores_mined"));
                    assertEquals(1, rs.getLong("diamonds_mined"));
                    assertEquals(1, rs.getLong("emeralds_mined"));
                    assertEquals(0, rs.getLong("ancient_debris_mined"));
                    assertEquals(3, rs.getLong("total_mined"));
                    assertEquals(1, rs.getLong("air_adjacent_mined"));
                    assertEquals(120, rs.getLong("play_time_minutes"));
                    assertEquals("TestPlayer", rs.getString("last_known_name"));
                }
            }
        }

        @Test
        @DisplayName("savePlayerStats upserts existing records")
        void savePlayerStats_upsertsExisting() throws Exception {
            UUID uuid = UUID.randomUUID();

            PlayerStatistics stats1 = new PlayerStatistics();
            stats1.onBlockBreak(org.bukkit.Material.STONE, 64, org.bukkit.block.Biome.PLAINS, false, null);
            storage.savePlayerStats(uuid, "Player1", stats1);
            awaitWrites();

            PlayerStatistics stats2 = new PlayerStatistics();
            stats2.onBlockBreak(org.bukkit.Material.STONE, 64, org.bukkit.block.Biome.PLAINS, false, null);
            stats2.onBlockBreak(org.bukkit.Material.STONE, 64, org.bukkit.block.Biome.PLAINS, false, null);
            storage.savePlayerStats(uuid, "Player2", stats2);
            awaitWrites();

            try (Connection conn = storage.getConnectionForTest();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT stone_mined, last_known_name FROM player_stats WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(2, rs.getLong("stone_mined"));
                    assertEquals("Player2", rs.getString("last_known_name"));
                    assertFalse(rs.next(), "Should only have one row for this uuid");
                }
            }
        }

        @Test
        @DisplayName("loadPlayerStats loads persisted values into PlayerStatistics")
        void loadPlayerStats_loadsValues() throws Exception {
            UUID uuid = UUID.randomUUID();
            PlayerStatistics original = new PlayerStatistics();
            for (int i = 0; i < 100; i++) {
                original.onBlockBreak(org.bukkit.Material.STONE, 64, org.bukkit.block.Biome.PLAINS, false, null);
            }
            original.onBlockBreak(org.bukkit.Material.DIAMOND_ORE, -10, org.bukkit.block.Biome.PLAINS, true, null);
            original.onBlockBreak(org.bukkit.Material.EMERALD_ORE, 11, org.bukkit.block.Biome.PLAINS, false, null);
            original.updatePlayTime(60);

            storage.savePlayerStats(uuid, "TestPlayer", original);
            awaitWrites();

            PlayerStatistics loaded = new PlayerStatistics();
            storage.loadPlayerStats(uuid, loaded);

            assertEquals(original.getStoneMined(), loaded.getStoneMined());
            assertEquals(original.getTotalOresMined(), loaded.getTotalOresMined());
            assertEquals(original.getDiamondsMined(), loaded.getDiamondsMined());
            assertEquals(original.getEmeraldsMined(), loaded.getEmeraldsMined());
            assertEquals(original.getTotalMined(), loaded.getTotalMined());
            assertEquals(original.getAirAdjacentMined(), loaded.getAirAdjacentMined());
            assertEquals(original.getPlayTimeMinutes(), loaded.getPlayTimeMinutes());
        }

        @Test
        @DisplayName("loadPlayerStats with non-existent UUID leaves stats at zero")
        void loadPlayerStats_nonExistentUuid_leavesZero() {
            UUID uuid = UUID.randomUUID();
            PlayerStatistics stats = new PlayerStatistics();
            storage.loadPlayerStats(uuid, stats);

            assertEquals(0, stats.getStoneMined());
            assertEquals(0, stats.getTotalOresMined());
            assertEquals(0, stats.getTotalMined());
        }

        @Test
        @DisplayName("short and long window values are persisted and loaded")
        void windowValues_persistedAndLoaded() throws Exception {
            UUID uuid = UUID.randomUUID();
            PlayerStatistics original = new PlayerStatistics();
            for (int i = 0; i < 100; i++) {
                original.onBlockBreak(org.bukkit.Material.DIAMOND_ORE, -10,
                    org.bukkit.block.Biome.PLAINS, true, null);
            }
            original.updatePlayTime(60);

            storage.savePlayerStats(uuid, "WindowPlayer", original);
            awaitWrites();

            PlayerStatistics loaded = new PlayerStatistics();
            storage.loadPlayerStats(uuid, loaded);

            assertEquals(original.getShortWindowBlocks(), loaded.getShortWindowBlocks(), 0.01);
            assertEquals(original.getShortWindowOres(), loaded.getShortWindowOres(), 0.01);
            assertEquals(original.getLongWindowBlocks(), loaded.getLongWindowBlocks(), 0.01);
            assertEquals(original.getLongWindowOres(), loaded.getLongWindowOres(), 0.01);
        }

        @Test
        @DisplayName("ancient debris is persisted")
        void ancientDebris_persisted() throws Exception {
            UUID uuid = UUID.randomUUID();
            PlayerStatistics stats = new PlayerStatistics();
            stats.onBlockBreak(org.bukkit.Material.ANCIENT_DEBRIS, 15,
                org.bukkit.block.Biome.NETHER_WASTES, false, null);
            stats.updatePlayTime(60);

            storage.savePlayerStats(uuid, "NetherPlayer", stats);
            awaitWrites();

            try (Connection conn = storage.getConnectionForTest();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT ancient_debris_mined FROM player_stats WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(1, rs.getLong("ancient_debris_mined"));
                }
            }
        }

        @Test
        @DisplayName("Null UUID does not throw")
        void nullUuid_noThrow() {
            PlayerStatistics stats = new PlayerStatistics();
            assertDoesNotThrow(() -> storage.savePlayerStats(null, "test", stats));
            assertDoesNotThrow(() -> storage.loadPlayerStats(null, stats));
            assertDoesNotThrow(() -> storage.updateStatsInMemory(null, "test", stats));
        }
    }

    @Nested
    @DisplayName("ore_breaks recording")
    class OreBreaksRecording {

        @Test
        @DisplayName("recordOreBreak inserts ore break entry")
        void recordOreBreak_insertsEntry() throws Exception {
            UUID uuid = UUID.randomUUID();
            storage.recordOreBreak(uuid, org.bukkit.Material.DIAMOND_ORE,
                "world", 100, -10, 200, org.bukkit.block.Biome.PLAINS);
            awaitWrites();

            try (Connection conn = storage.getConnectionForTest();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT uuid, material, world, x, y, z, biome FROM ore_breaks WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(uuid.toString(), rs.getString("uuid"));
                    assertEquals("DIAMOND_ORE", rs.getString("material"));
                    assertEquals("world", rs.getString("world"));
                    assertEquals(100, rs.getInt("x"));
                    assertEquals(-10, rs.getInt("y"));
                    assertEquals(200, rs.getInt("z"));
                    assertEquals("PLAINS", rs.getString("biome"));
                }
            }
        }

        @Test
        @DisplayName("recordOreBreak handles null biome gracefully")
        void recordOreBreak_nullBiome() throws Exception {
            UUID uuid = UUID.randomUUID();
            storage.recordOreBreak(uuid, org.bukkit.Material.EMERALD_ORE,
                "world", 50, 11, 100, null);
            awaitWrites();

            try (Connection conn = storage.getConnectionForTest();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT biome FROM ore_breaks WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("UNKNOWN", rs.getString("biome"));
                }
            }
        }

        @Test
        @DisplayName("Multiple ore breaks are recorded independently")
        void multipleOreBreaks_recordedIndependently() throws Exception {
            UUID uuid = UUID.randomUUID();
            storage.recordOreBreak(uuid, org.bukkit.Material.DIAMOND_ORE,
                "world", 1, 2, 3, org.bukkit.block.Biome.PLAINS);
            storage.recordOreBreak(uuid, org.bukkit.Material.EMERALD_ORE,
                "world", 4, 5, 6, org.bukkit.block.Biome.DESERT);
            awaitWrites();

            try (Connection conn = storage.getConnectionForTest();
                 Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM ore_breaks WHERE uuid = '" + uuid.toString() + "'");
                assertTrue(rs.next());
                assertEquals(2, rs.getInt(1));
            }
        }

        @Test
        @DisplayName("Null UUID does not throw for recordOreBreak")
        void nullUuid_noThrow() {
            assertDoesNotThrow(() -> storage.recordOreBreak(null,
                org.bukkit.Material.DIAMOND_ORE, "world", 1, 2, 3,
                org.bukkit.block.Biome.PLAINS));
        }
    }

    @Nested
    @DisplayName("alerts recording")
    class AlertsRecording {

        @Test
        @DisplayName("recordAlert inserts alert entry")
        void recordAlert_insertsEntry() throws Exception {
            UUID uuid = UUID.randomUUID();
            storage.recordAlert(uuid, "TestPlayer", AlertLevel.CRITICAL, "oreToStoneRatio, diamondPerHour");
            awaitWrites();

            try (Connection conn = storage.getConnectionForTest();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT player_name, alert_level, triggering_metrics FROM alerts WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("TestPlayer", rs.getString("player_name"));
                    assertEquals("CRITICAL", rs.getString("alert_level"));
                    assertEquals("oreToStoneRatio, diamondPerHour", rs.getString("triggering_metrics"));
                }
            }
        }

        @Test
        @DisplayName("recordAlert with null player name uses 'unknown'")
        void recordAlert_nullPlayerName() throws Exception {
            UUID uuid = UUID.randomUUID();
            storage.recordAlert(uuid, null, AlertLevel.WARNING, "oreToStoneRatio");
            awaitWrites();

            try (Connection conn = storage.getConnectionForTest();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT player_name FROM alerts WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("unknown", rs.getString("player_name"));
                }
            }
        }

        @Test
        @DisplayName("Multiple alerts for same player are stored separately")
        void multipleAlerts_storedSeparately() throws Exception {
            UUID uuid = UUID.randomUUID();
            storage.recordAlert(uuid, "TestPlayer", AlertLevel.INFO, "oreToStoneRatio");
            storage.recordAlert(uuid, "TestPlayer", AlertLevel.CRITICAL, "diamondPerHour");
            awaitWrites();

            try (Connection conn = storage.getConnectionForTest();
                 Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM alerts WHERE uuid = '" + uuid.toString() + "'");
                assertTrue(rs.next());
                assertEquals(2, rs.getInt(1));
            }
        }
    }

    @Nested
    @DisplayName("In-memory cache")
    class InMemoryCache {

        @Test
        @DisplayName("updateStatsInMemory adds player to cache")
        void updateStatsInMemory_addsToCache() {
            UUID uuid = UUID.randomUUID();
            PlayerStatistics stats = new PlayerStatistics();
            stats.onBlockBreak(org.bukkit.Material.STONE, 64, org.bukkit.block.Biome.PLAINS, false, null);

            storage.updateStatsInMemory(uuid, "CachedPlayer", stats);
            assertEquals(1, storage.getCachedPlayerCount());
        }

        @Test
        @DisplayName("saveAllDirtyStats persists cached stats")
        void saveAllDirtyStats_persistsCached() throws Exception {
            UUID uuid = UUID.randomUUID();
            PlayerStatistics stats = new PlayerStatistics();
            stats.onBlockBreak(org.bukkit.Material.DIAMOND_ORE, -10,
                org.bukkit.block.Biome.PLAINS, true, null);
            stats.updatePlayTime(60);

            storage.updateStatsInMemory(uuid, "DirtyPlayer", stats);
            storage.saveAllDirtyStats();
            awaitWrites();

            try (Connection conn = storage.getConnectionForTest();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT last_known_name FROM player_stats WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("DirtyPlayer", rs.getString("last_known_name"));
                }
            }
        }

        @Test
        @DisplayName("Empty cache save does not throw")
        void emptyCacheSave_noThrow() {
            assertDoesNotThrow(() -> storage.saveAllDirtyStats());
        }
    }

    @Nested
    @DisplayName("Shutdown behavior")
    class ShutdownBehavior {

        @Test
        @DisplayName("shutdown flushes pending writes")
        void shutdown_flushesWrites() throws Exception {
            UUID uuid = UUID.randomUUID();
            PlayerStatistics stats = new PlayerStatistics();
            stats.onBlockBreak(org.bukkit.Material.STONE, 64, org.bukkit.block.Biome.PLAINS, false, null);
            storage.savePlayerStats(uuid, "ShutdownPlayer", stats);

            storage.shutdown();
            storage = null;

            try (Connection conn = new org.sqlite.JDBC().connect(
                    "jdbc:sqlite:" + new File(tempDir, "detection.db").getAbsolutePath(),
                    new java.util.Properties());
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT last_known_name FROM player_stats WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("ShutdownPlayer", rs.getString("last_known_name"));
                }
            }
        }

        @Test
        @DisplayName("Double shutdown does not throw")
        void doubleShutdown_noThrow() {
            StatisticsStorage local = new StatisticsStorage(tempDir);
            assertDoesNotThrow(local::shutdown);
            assertDoesNotThrow(local::shutdown);
        }

        @Test
        @DisplayName("isShutdown returns true after shutdown")
        void isShutdown_returnsTrue() {
            assertFalse(storage.isShutdown());
            storage.shutdown();
            assertTrue(storage.isShutdown());
        }
    }

    @Nested
    @DisplayName("StatsSnapshot")
    class StatsSnapshotTests {

        @Test
        @DisplayName("StatsSnapshot captures all fields")
        void snapshotCapturesAllFields() {
            StatisticsStorage.StatsSnapshot snapshot = new StatisticsStorage.StatsSnapshot(
                100, 50, 10, 5, 2, 200, 30, 120,
                1.5, 0.8, 0.9, 0.4, System.currentTimeMillis()
            );
            assertEquals(100, snapshot.stoneMined);
            assertEquals(50, snapshot.totalOresMined);
            assertEquals(10, snapshot.diamondsMined);
            assertEquals(5, snapshot.emeraldsMined);
            assertEquals(2, snapshot.ancientDebrisMined);
            assertEquals(200, snapshot.totalMined);
            assertEquals(30, snapshot.airAdjacentMined);
            assertEquals(120, snapshot.playTimeMinutes);
            assertEquals(1.5, snapshot.shortWindowBlocks, 0.001);
            assertEquals(0.8, snapshot.shortWindowOres, 0.001);
            assertEquals(0.9, snapshot.longWindowBlocks, 0.001);
            assertEquals(0.4, snapshot.longWindowOres, 0.001);
            assertTrue(snapshot.lastUpdated > 0);
        }
    }

    @Nested
    @DisplayName("PlayerStatistics persistence helpers")
    class PlayerStatisticsHelpers {

        @Test
        @DisplayName("getEmeraldsMined returns correct count")
        void getEmeraldsMined_correct() {
            PlayerStatistics stats = new PlayerStatistics();
            stats.onBlockBreak(org.bukkit.Material.EMERALD_ORE, 11,
                org.bukkit.block.Biome.PLAINS, false, null);
            stats.onBlockBreak(org.bukkit.Material.EMERALD_ORE, 11,
                org.bukkit.block.Biome.PLAINS, false, null);
            assertEquals(2, stats.getEmeraldsMined());
        }

        @Test
        @DisplayName("getAncientDebrisMined returns correct count")
        void getAncientDebrisMined_correct() {
            PlayerStatistics stats = new PlayerStatistics();
            stats.onBlockBreak(org.bukkit.Material.ANCIENT_DEBRIS, 15,
                org.bukkit.block.Biome.NETHER_WASTES, false, null);
            assertEquals(1, stats.getAncientDebrisMined());
        }

        @Test
        @DisplayName("setPersistedValues sets fields correctly")
        void setPersistedValues_setsFields() {
            PlayerStatistics stats = new PlayerStatistics();
            stats.setPersistedValues(1000, 50, 10, 5, 2, 2000, 30, 120,
                1.5, 0.8, 0.9, 0.4);

            assertEquals(1000, stats.getStoneMined());
            assertEquals(50, stats.getTotalOresMined());
            assertEquals(10, stats.getDiamondsMined());
            assertEquals(5, stats.getEmeraldsMined());
            assertEquals(2, stats.getAncientDebrisMined());
            assertEquals(2000, stats.getTotalMined());
            assertEquals(30, stats.getAirAdjacentMined());
            assertEquals(120, stats.getPlayTimeMinutes());
            assertEquals(1.5, stats.getShortWindowBlocks(), 0.01);
            assertEquals(0.8, stats.getShortWindowOres(), 0.01);
            assertEquals(0.9, stats.getLongWindowBlocks(), 0.01);
            assertEquals(0.4, stats.getLongWindowOres(), 0.01);
        }

        @Test
        @DisplayName("setPersistedValues recomputes ratios")
        void setPersistedValues_recomputesRatios() {
            PlayerStatistics stats = new PlayerStatistics();
            stats.setPersistedValues(1000, 50, 10, 5, 2, 2000, 30, 120,
                1.5, 0.8, 0.9, 0.4);

            double expectedOreRatio = 50.0 / 1000.0;
            assertEquals(expectedOreRatio, stats.getOreToStoneRatio(), 0.001);
            assertTrue(stats.getOrePerHour() > 0);
            assertTrue(stats.getShortWindowOreRatio() > 0);
        }

        @Test
        @DisplayName("setPersistedValues with zero stone does not divide by zero")
        void setPersistedValues_zeroStone_noDivisionByZero() {
            PlayerStatistics stats = new PlayerStatistics();
            assertDoesNotThrow(() -> stats.setPersistedValues(0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0));
            assertEquals(0, stats.getOreToStoneRatio());
            assertEquals(0, stats.getDiamondToStoneRatio());
        }
    }
}
