package com.antixray.detection;

import com.antixray.api.AlertLevel;
import com.antixray.AntiXrayPlugin;
import com.antixray.util.FoliaSchedulerAdapter;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import org.bukkit.Material;
import org.bukkit.block.Biome;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class StatisticsStorage {

    private static final String CREATE_PLAYER_STATS = """
            CREATE TABLE IF NOT EXISTS player_stats (
                uuid TEXT PRIMARY KEY,
                last_known_name TEXT,
                stone_mined BIGINT,
                total_ores_mined BIGINT,
                diamonds_mined BIGINT,
                emeralds_mined BIGINT,
                ancient_debris_mined BIGINT,
                total_mined BIGINT,
                air_adjacent_mined BIGINT,
                play_time_minutes BIGINT,
                short_window_blocks BIGINT,
                short_window_ores BIGINT,
                long_window_blocks BIGINT,
                long_window_ores BIGINT,
                last_updated BIGINT
            )""";

    private static final String CREATE_ORE_BREAKS = """
            CREATE TABLE IF NOT EXISTS ore_breaks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                uuid TEXT,
                material TEXT,
                world TEXT,
                x INT,
                y INT,
                z INT,
                biome TEXT,
                timestamp BIGINT
            )""";

    private static final String CREATE_ALERTS = """
            CREATE TABLE IF NOT EXISTS alerts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                uuid TEXT,
                player_name TEXT,
                alert_level TEXT,
                triggering_metrics TEXT,
                timestamp BIGINT
            )""";

    private static final String UPSERT_PLAYER_STATS = """
            INSERT INTO player_stats (uuid, last_known_name, stone_mined, total_ores_mined,
                diamonds_mined, emeralds_mined, ancient_debris_mined, total_mined,
                air_adjacent_mined, play_time_minutes, short_window_blocks,
                short_window_ores, long_window_blocks, long_window_ores, last_updated)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(uuid) DO UPDATE SET
                last_known_name=excluded.last_known_name,
                stone_mined=excluded.stone_mined,
                total_ores_mined=excluded.total_ores_mined,
                diamonds_mined=excluded.diamonds_mined,
                emeralds_mined=excluded.emeralds_mined,
                ancient_debris_mined=excluded.ancient_debris_mined,
                total_mined=excluded.total_mined,
                air_adjacent_mined=excluded.air_adjacent_mined,
                play_time_minutes=excluded.play_time_minutes,
                short_window_blocks=excluded.short_window_blocks,
                short_window_ores=excluded.short_window_ores,
                long_window_blocks=excluded.long_window_blocks,
                long_window_ores=excluded.long_window_ores,
                last_updated=excluded.last_updated""";

    private static final String MYSQL_UPSERT_PLAYER_STATS = """
            INSERT INTO player_stats (uuid, last_known_name, stone_mined, total_ores_mined,
                diamonds_mined, emeralds_mined, ancient_debris_mined, total_mined,
                air_adjacent_mined, play_time_minutes, short_window_blocks,
                short_window_ores, long_window_blocks, long_window_ores, last_updated)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                last_known_name=VALUES(last_known_name),
                stone_mined=VALUES(stone_mined),
                total_ores_mined=VALUES(total_ores_mined),
                diamonds_mined=VALUES(diamonds_mined),
                emeralds_mined=VALUES(emeralds_mined),
                ancient_debris_mined=VALUES(ancient_debris_mined),
                total_mined=VALUES(total_mined),
                air_adjacent_mined=VALUES(air_adjacent_mined),
                play_time_minutes=VALUES(play_time_minutes),
                short_window_blocks=VALUES(short_window_blocks),
                short_window_ores=VALUES(short_window_ores),
                long_window_blocks=VALUES(long_window_blocks),
                long_window_ores=VALUES(long_window_ores),
                last_updated=VALUES(last_updated)""";

    private static final String INSERT_ORE_BREAK = """
            INSERT INTO ore_breaks (uuid, material, world, x, y, z, biome, timestamp)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)""";

    private static final String INSERT_ALERT = """
            INSERT INTO alerts (uuid, player_name, alert_level, triggering_metrics, timestamp)
            VALUES (?, ?, ?, ?, ?)""";

    private static final String SELECT_PLAYER_STATS = """
            SELECT last_known_name, stone_mined, total_ores_mined, diamonds_mined,
                emeralds_mined, ancient_debris_mined, total_mined, air_adjacent_mined,
                play_time_minutes, short_window_blocks, short_window_ores,
                long_window_blocks, long_window_ores, last_updated
            FROM player_stats WHERE uuid = ?""";

    private final AntiXrayPlugin plugin;
    private final Logger logger;
    private final boolean mysql;
    private final HikariDataSource dataSource;
    private final ExecutorService writeExecutor;
    private final ConcurrentHashMap<UUID, CachedStats> statsCache = new ConcurrentHashMap<>();
    private volatile int periodicSaveTaskId = -1;
    private volatile boolean shutdown = false;

    public StatisticsStorage(AntiXrayPlugin plugin, String storageType, File dataFolder,
                             String mysqlHost, int mysqlPort, String mysqlDatabase,
                             String mysqlUsername, String mysqlPassword) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.mysql = "mysql".equalsIgnoreCase(storageType);

        if (this.mysql) {
            this.dataSource = createMySqlPool(mysqlHost, mysqlPort, mysqlDatabase, mysqlUsername, mysqlPassword);
        } else {
            this.dataSource = createSqlitePool(dataFolder);
        }

        this.writeExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "AntiXray-Storage-Writer");
            t.setDaemon(true);
            return t;
        });

        initializeSchema();
    }

    public StatisticsStorage(File dataFolder) {
        this.plugin = null;
        this.logger = Logger.getLogger("AntiXray-Storage");
        this.mysql = false;
        this.dataSource = createSqlitePool(dataFolder);
        this.writeExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "AntiXray-Storage-Writer");
            t.setDaemon(true);
            return t;
        });
        initializeSchema();
    }

    private HikariDataSource createSqlitePool(File dataFolder) {
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        File dbFile = new File(dataFolder, "detection.db");
        String jdbcUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setDriverClassName("org.sqlite.JDBC");
        config.setMaximumPoolSize(1);
        config.setConnectionTestQuery("SELECT 1");
        config.setPoolName("AntiXray-SQLite");

        config.addDataSourceProperty("journal_mode", "WAL");
        config.addDataSourceProperty("synchronous", "NORMAL");
        config.addDataSourceProperty("busy_timeout", "10000");

        HikariDataSource ds = new HikariDataSource(config);

        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL");
            stmt.execute("PRAGMA synchronous=NORMAL");
            stmt.execute("PRAGMA busy_timeout=10000");
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to set SQLite WAL mode via initial connection", e);
        }

        return ds;
    }

    private HikariDataSource createMySqlPool(String host, int port, String database,
                                              String username, String password) {
        String jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=false&useUnicode=true&characterEncoding=UTF-8";

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setConnectionTestQuery("SELECT 1");
        config.setPoolName("AntiXray-MySQL");

        return new HikariDataSource(config);
    }

    private void initializeSchema() {
        String playerStatsSql = CREATE_PLAYER_STATS;
        String oreBreaksSql = CREATE_ORE_BREAKS;
        String alertsSql = CREATE_ALERTS;

        if (mysql) {
            playerStatsSql = playerStatsSql.replace("INTEGER PRIMARY KEY AUTOINCREMENT", "INT AUTO_INCREMENT PRIMARY KEY");
            oreBreaksSql = oreBreaksSql.replace("INTEGER PRIMARY KEY AUTOINCREMENT", "INT AUTO_INCREMENT PRIMARY KEY");
            alertsSql = alertsSql.replace("INTEGER PRIMARY KEY AUTOINCREMENT", "INT AUTO_INCREMENT PRIMARY KEY");
            playerStatsSql = playerStatsSql.replace("TEXT PRIMARY KEY", "VARCHAR(36) PRIMARY KEY");
            oreBreaksSql = oreBreaksSql.replace("uuid TEXT", "uuid VARCHAR(36)");
            alertsSql = alertsSql.replace("uuid TEXT", "uuid VARCHAR(36)");
            alertsSql = alertsSql.replace("player_name TEXT", "player_name VARCHAR(16)");
            alertsSql = alertsSql.replace("alert_level TEXT", "alert_level VARCHAR(16)");
            alertsSql = alertsSql.replace("triggering_metrics TEXT", "triggering_metrics TEXT");
            oreBreaksSql = oreBreaksSql.replace("material TEXT", "material VARCHAR(64)");
            oreBreaksSql = oreBreaksSql.replace("world TEXT", "world VARCHAR(64)");
            oreBreaksSql = oreBreaksSql.replace("biome TEXT", "biome VARCHAR(64)");
            playerStatsSql = playerStatsSql.replace("last_known_name TEXT", "last_known_name VARCHAR(16)");
        }

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(playerStatsSql);
            stmt.execute(oreBreaksSql);
            stmt.execute(alertsSql);

            if (!mysql) {
                createIndexes(stmt);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to initialize detection database schema", e);
        }
    }

    private void createIndexes(Statement stmt) throws SQLException {
        String[] indexes = {
            "CREATE INDEX IF NOT EXISTS idx_ore_breaks_uuid ON ore_breaks(uuid)",
            "CREATE INDEX IF NOT EXISTS idx_ore_breaks_timestamp ON ore_breaks(timestamp)",
            "CREATE INDEX IF NOT EXISTS idx_alerts_uuid ON alerts(uuid)",
            "CREATE INDEX IF NOT EXISTS idx_alerts_timestamp ON alerts(timestamp)",
            "CREATE INDEX IF NOT EXISTS idx_player_stats_last_updated ON player_stats(last_updated)"
        };
        for (String idx : indexes) {
            stmt.execute(idx);
        }
    }

    public void updateStatsInMemory(UUID uuid, String playerName, PlayerStatistics stats) {
        if (uuid == null || stats == null) return;
        CachedStats cached = new CachedStats(uuid, playerName, snapshotStats(stats));
        statsCache.put(uuid, cached);
    }

    public void savePlayerStats(UUID uuid, String playerName, PlayerStatistics stats) {
        if (uuid == null || stats == null) return;
        StatsSnapshot snapshot = snapshotStats(stats);
        CachedStats cached = new CachedStats(uuid, playerName, snapshot);
        statsCache.put(uuid, cached);
        writeExecutor.submit(() -> persistPlayerStats(cached));
    }

    public void saveAllDirtyStats() {
        List<CachedStats> snapshots = new ArrayList<>(statsCache.values());
        if (snapshots.isEmpty()) return;
        writeExecutor.submit(() -> {
            for (CachedStats cached : snapshots) {
                persistPlayerStats(cached);
            }
        });
    }

    public void loadPlayerStats(UUID uuid, PlayerStatistics stats) {
        if (uuid == null || stats == null) return;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_PLAYER_STATS)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    applyLoadedStats(stats, rs);
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to load player stats for " + uuid, e);
        }
    }

    public void recordOreBreak(UUID uuid, Material material, String world, int x, int y, int z,
                                Biome biome) {
        if (uuid == null || material == null) return;
        String biomeName = biome != null ? biome.name() : "UNKNOWN";
        long timestamp = System.currentTimeMillis();
        writeExecutor.submit(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(INSERT_ORE_BREAK)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, material.name());
                ps.setString(3, world != null ? world : "unknown");
                ps.setInt(4, x);
                ps.setInt(5, y);
                ps.setInt(6, z);
                ps.setString(7, biomeName);
                ps.setLong(8, timestamp);
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to record ore break for " + uuid, e);
            }
        });
    }

    public void recordAlert(UUID uuid, String playerName, AlertLevel level, String triggeringMetrics) {
        if (uuid == null || level == null) return;
        long timestamp = System.currentTimeMillis();
        writeExecutor.submit(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(INSERT_ALERT)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, playerName != null ? playerName : "unknown");
                ps.setString(3, level.name());
                ps.setString(4, triggeringMetrics != null ? triggeringMetrics : "");
                ps.setLong(5, timestamp);
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to record alert for " + uuid, e);
            }
        });
    }

    public void startPeriodicSaveTask() {
        if (periodicSaveTaskId != -1) return;
        if (plugin != null && plugin.getSchedulerAdapter() != null) {
            periodicSaveTaskId = plugin.getSchedulerAdapter().runAsyncTimer(
                this::saveAllDirtyStats,
                60 * 20L,
                60 * 20L
            );
        }
    }

    public void stopPeriodicSaveTask() {
        if (periodicSaveTaskId != -1 && plugin != null) {
            plugin.getSchedulerAdapter().cancelTask(periodicSaveTaskId);
            periodicSaveTaskId = -1;
        }
    }

    public void shutdown() {
        if (shutdown) return;
        shutdown = true;

        stopPeriodicSaveTask();

        saveAllDirtyStats();

        writeExecutor.shutdown();
        try {
            if (!writeExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                writeExecutor.shutdownNow();
                logger.warning("Storage write executor did not terminate within 10 seconds");
            }
        } catch (InterruptedException e) {
            writeExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    public void forceShutdown() {
        if (shutdown) return;
        shutdown = true;

        stopPeriodicSaveTask();

        writeExecutor.shutdownNow();

        if (dataSource != null && !dataSource.isClosed()) {
            try {
                dataSource.close();
            } catch (Exception ignored) {
            }
        }
    }

    public boolean isShutdown() {
        return shutdown;
    }

    public boolean isMysql() {
        return mysql;
    }

    public int getCachedPlayerCount() {
        return statsCache.size();
    }

    Connection getConnectionForTest() throws SQLException {
        return dataSource.getConnection();
    }

    private void persistPlayerStats(CachedStats cached) {
        String sql = mysql ? MYSQL_UPSERT_PLAYER_STATS : UPSERT_PLAYER_STATS;
        StatsSnapshot s = cached.snapshot;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, cached.uuid.toString());
            ps.setString(2, cached.playerName);
            ps.setLong(3, s.stoneMined);
            ps.setLong(4, s.totalOresMined);
            ps.setLong(5, s.diamondsMined);
            ps.setLong(6, s.emeraldsMined);
            ps.setLong(7, s.ancientDebrisMined);
            ps.setLong(8, s.totalMined);
            ps.setLong(9, s.airAdjacentMined);
            ps.setLong(10, s.playTimeMinutes);
            ps.setDouble(11, s.shortWindowBlocks);
            ps.setDouble(12, s.shortWindowOres);
            ps.setDouble(13, s.longWindowBlocks);
            ps.setDouble(14, s.longWindowOres);
            ps.setLong(15, s.lastUpdated);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to persist player stats for " + cached.uuid, e);
        }
    }

    private StatsSnapshot snapshotStats(PlayerStatistics stats) {
        return new StatsSnapshot(
            stats.getStoneMined(),
            stats.getTotalOresMined(),
            stats.getDiamondsMined(),
            stats.getEmeraldsMined(),
            stats.getAncientDebrisMined(),
            stats.getTotalMined(),
            stats.getAirAdjacentMined(),
            stats.getPlayTimeMinutes(),
            stats.getShortWindowBlocks(),
            stats.getShortWindowOres(),
            stats.getLongWindowBlocks(),
            stats.getLongWindowOres(),
            System.currentTimeMillis()
        );
    }

    private void applyLoadedStats(PlayerStatistics stats, ResultSet rs) throws SQLException {
        long storedStoneMined = rs.getLong("stone_mined");
        long storedTotalOresMined = rs.getLong("total_ores_mined");
        long storedDiamondsMined = rs.getLong("diamonds_mined");
        long storedEmeraldsMined = rs.getLong("emeralds_mined");
        long storedAncientDebrisMined = rs.getLong("ancient_debris_mined");
        long storedTotalMined = rs.getLong("total_mined");
        long storedAirAdjacentMined = rs.getLong("air_adjacent_mined");
        long storedPlayTimeMinutes = rs.getLong("play_time_minutes");
        double storedShortWindowBlocks = rs.getDouble("short_window_blocks");
        double storedShortWindowOres = rs.getDouble("short_window_ores");
        double storedLongWindowBlocks = rs.getDouble("long_window_blocks");
        double storedLongWindowOres = rs.getDouble("long_window_ores");

        applyPersistedStats(stats, storedStoneMined, storedTotalOresMined,
            storedDiamondsMined, storedEmeraldsMined, storedAncientDebrisMined,
            storedTotalMined, storedAirAdjacentMined, storedPlayTimeMinutes,
            storedShortWindowBlocks, storedShortWindowOres,
            storedLongWindowBlocks, storedLongWindowOres);
    }

    void applyPersistedStats(PlayerStatistics stats,
                              long stoneMined, long totalOresMined,
                              long diamondsMined, long emeraldsMined,
                              long ancientDebrisMined,
                              long totalMined, long airAdjacentMined,
                              long playTimeMinutes,
                              double shortWindowBlocks, double shortWindowOres,
                              double longWindowBlocks, double longWindowOres) {
        stats.setPersistedValues(stoneMined, totalOresMined, diamondsMined,
            emeraldsMined, ancientDebrisMined, totalMined, airAdjacentMined,
            playTimeMinutes, shortWindowBlocks, shortWindowOres,
            longWindowBlocks, longWindowOres);
    }

    private static final class CachedStats {
        final UUID uuid;
        final String playerName;
        final StatsSnapshot snapshot;

        CachedStats(UUID uuid, String playerName, StatsSnapshot snapshot) {
            this.uuid = uuid;
            this.playerName = playerName;
            this.snapshot = snapshot;
        }
    }

    static final class StatsSnapshot {
        final long stoneMined;
        final long totalOresMined;
        final long diamondsMined;
        final long emeraldsMined;
        final long ancientDebrisMined;
        final long totalMined;
        final long airAdjacentMined;
        final long playTimeMinutes;
        final double shortWindowBlocks;
        final double shortWindowOres;
        final double longWindowBlocks;
        final double longWindowOres;
        final long lastUpdated;

        StatsSnapshot(long stoneMined, long totalOresMined, long diamondsMined,
                      long emeraldsMined, long ancientDebrisMined, long totalMined,
                      long airAdjacentMined, long playTimeMinutes,
                      double shortWindowBlocks, double shortWindowOres,
                      double longWindowBlocks, double longWindowOres, long lastUpdated) {
            this.stoneMined = stoneMined;
            this.totalOresMined = totalOresMined;
            this.diamondsMined = diamondsMined;
            this.emeraldsMined = emeraldsMined;
            this.ancientDebrisMined = ancientDebrisMined;
            this.totalMined = totalMined;
            this.airAdjacentMined = airAdjacentMined;
            this.playTimeMinutes = playTimeMinutes;
            this.shortWindowBlocks = shortWindowBlocks;
            this.shortWindowOres = shortWindowOres;
            this.longWindowBlocks = longWindowBlocks;
            this.longWindowOres = longWindowOres;
            this.lastUpdated = lastUpdated;
        }
    }
}
