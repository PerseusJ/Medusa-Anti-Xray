package com.antixray.detection;

import com.antixray.api.AlertLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DetectionEngineTest {

    private DetectionEngine engine;
    private DetectionEngine.DetectionThresholds thresholds;
    private UUID playerId;

    @BeforeEach
    void setUp() {
        thresholds = DetectionEngine.DetectionThresholds.defaults();
        engine = new DetectionEngine(thresholds);
        playerId = UUID.randomUUID();
    }

    private PlayerStatistics createStatsWithSampleSize(long totalMined) {
        PlayerStatistics stats = new PlayerStatistics();
        for (long i = 0; i < totalMined; i++) {
            stats.onBlockBreak(org.bukkit.Material.STONE, 64,
                    org.bukkit.block.Biome.PLAINS, false, null);
        }
        stats.updatePlayTime(60);
        return stats;
    }

    private PlayerStatistics createCleanStats() {
        PlayerStatistics stats = createStatsWithSampleSize(200);
        stats.updatePlayTime(60);
        return stats;
    }

    private DetectionEngine.DetectionThresholds isolatedThresholds(String metric, double warning, double critical) {
        DetectionEngine.DetectionThresholds t = DetectionEngine.DetectionThresholds.defaults();
        t.oreToStoneRatioWarning = 99.0; t.oreToStoneRatioCritical = 100.0;
        t.diamondToStoneRatioWarning = 99.0; t.diamondToStoneRatioCritical = 100.0;
        t.orePerHourWarning = 99.0; t.orePerHourCritical = 100.0;
        t.diamondPerHourWarning = 99.0; t.diamondPerHourCritical = 100.0;
        t.shortWindowOreRatioWarning = 99.0; t.shortWindowOreRatioCritical = 100.0;
        t.longWindowOreRatioWarning = 99.0; t.longWindowOreRatioCritical = 100.0;
        t.valuableOreRatioWarning = 99.0; t.valuableOreRatioCritical = 100.0;
        t.straightToOreRatioWarning = 99.0; t.straightToOreRatioCritical = 100.0;
        switch (metric) {
            case "oreToStoneRatio" -> { t.oreToStoneRatioWarning = warning; t.oreToStoneRatioCritical = critical; }
            case "diamondToStoneRatio" -> { t.diamondToStoneRatioWarning = warning; t.diamondToStoneRatioCritical = critical; }
            case "orePerHour" -> { t.orePerHourWarning = warning; t.orePerHourCritical = critical; }
            case "diamondPerHour" -> { t.diamondPerHourWarning = warning; t.diamondPerHourCritical = critical; }
            case "shortWindowOreRatio" -> { t.shortWindowOreRatioWarning = warning; t.shortWindowOreRatioCritical = critical; }
            case "longWindowOreRatio" -> { t.longWindowOreRatioWarning = warning; t.longWindowOreRatioCritical = critical; }
            case "valuableOreRatio" -> { t.valuableOreRatioWarning = warning; t.valuableOreRatioCritical = critical; }
            case "straightToOreRatio" -> { t.straightToOreRatioWarning = warning; t.straightToOreRatioCritical = critical; }
            default -> throw new IllegalArgumentException("Unknown metric: " + metric);
        }
        return t;
    }

    @Nested
    @DisplayName("Minimum sample size guard")
    class MinimumSampleSizeGuard {

        @Test
        @DisplayName("Below minimum sample size returns none")
        void belowMinimumSampleSize_returnsNone() {
            PlayerStatistics stats = createStatsWithSampleSize(50);
            DetectionResult result = engine.evaluate(playerId, stats);
            assertFalse(result.isDetected());
        }

        @Test
        @DisplayName("At minimum sample size evaluates normally")
        void atMinimumSampleSize_evaluates() {
            PlayerStatistics stats = createStatsWithSampleSize(100);
            DetectionResult result = engine.evaluate(playerId, stats);
            assertNotNull(result);
        }

        @Test
        @DisplayName("Custom minimum sample size is respected")
        void customMinimumSampleSize_isRespected() {
            DetectionEngine custom = new DetectionEngine(thresholds, 60_000L, 200, 30);
            PlayerStatistics stats = createStatsWithSampleSize(150);
            DetectionResult result = custom.evaluate(playerId, stats);
            assertFalse(result.isDetected());
        }

        @Test
        @DisplayName("Zero totalMined returns none")
        void zeroTotalMined_returnsNone() {
            PlayerStatistics stats = new PlayerStatistics();
            DetectionResult result = engine.evaluate(playerId, stats);
            assertFalse(result.isDetected());
        }

        @Test
        @DisplayName("High ore ratio below sample size still returns none")
        void highOreRatio_belowSampleSize_returnsNone() {
            DetectionEngine.DetectionThresholds t = isolatedThresholds("oreToStoneRatio", 0.01, 0.05);
            DetectionEngine eng = new DetectionEngine(t);

            PlayerStatistics stats = new PlayerStatistics();
            for (int i = 0; i < 50; i++) {
                stats.onBlockBreak(org.bukkit.Material.DIAMOND_ORE, -10,
                        org.bukkit.block.Biome.PLAINS, true, null);
            }
            stats.updatePlayTime(60);

            DetectionResult result = eng.evaluate(playerId, stats);
            assertFalse(result.isDetected());
        }
    }

    @Nested
    @DisplayName("Alert level determination")
    class AlertLevelDetermination {

        @Test
        @DisplayName("All metrics below thresholds returns none")
        void allBelowThresholds_returnsNone() {
            PlayerStatistics stats = createCleanStats();
            DetectionResult result = engine.evaluate(playerId, stats);
            assertFalse(result.isDetected());
        }

        @Test
        @DisplayName("One metric slightly above warning threshold returns INFO")
        void oneMetricAboveWarning_returnsInfo() {
            DetectionEngine.DetectionThresholds t = isolatedThresholds("oreToStoneRatio", 0.01, 99.0);
            DetectionEngine eng = new DetectionEngine(t);

            PlayerStatistics stats = createStatsWithSampleSize(200);
            for (int i = 0; i < 20; i++) {
                stats.onBlockBreak(org.bukkit.Material.DIAMOND_ORE, -10,
                        org.bukkit.block.Biome.PLAINS, true, null);
            }
            stats.updatePlayTime(120);

            double actualRatio = stats.getOreToStoneRatio();
            assertTrue(actualRatio >= t.oreToStoneRatioWarning,
                    "Expected oreToStoneRatio (" + actualRatio + ") >= warning (" + t.oreToStoneRatioWarning + ")");

            DetectionResult result = eng.evaluate(playerId, stats);
            assertTrue(result.isDetected());
            assertEquals(AlertLevel.INFO, result.getLevel());
            assertTrue(result.getTriggeredMetrics().contains("oreToStoneRatio"));
        }

        @Test
        @DisplayName("Two metrics above warning threshold returns WARNING")
        void twoMetricsAboveWarning_returnsWarning() {
            DetectionEngine.DetectionThresholds t = isolatedThresholds("oreToStoneRatio", 0.01, 99.0);
            t.diamondToStoneRatioWarning = 0.0005;
            t.diamondToStoneRatioCritical = 99.0;
            DetectionEngine eng = new DetectionEngine(t);

            PlayerStatistics stats = createStatsWithSampleSize(200);
            for (int i = 0; i < 20; i++) {
                stats.onBlockBreak(org.bukkit.Material.DIAMOND_ORE, -10,
                        org.bukkit.block.Biome.PLAINS, true, null);
            }
            stats.updatePlayTime(120);

            DetectionResult result = eng.evaluate(playerId, stats);
            assertTrue(result.isDetected());
            assertEquals(AlertLevel.WARNING, result.getLevel());
        }

        @Test
        @DisplayName("One metric significantly above warning threshold returns WARNING")
        void oneMetricSignificantlyAboveWarning_returnsWarning() {
            DetectionEngine.DetectionThresholds t = isolatedThresholds("oreToStoneRatio", 0.05, 0.10);
            DetectionEngine eng = new DetectionEngine(t);

            PlayerStatistics stats = createStatsWithSampleSize(200);
            for (int i = 0; i < 50; i++) {
                stats.onBlockBreak(org.bukkit.Material.DIAMOND_ORE, -10,
                        org.bukkit.block.Biome.PLAINS, true, null);
            }
            stats.updatePlayTime(120);

            double actualRatio = stats.getOreToStoneRatio();
            assertTrue(actualRatio >= 0.05,
                    "Expected oreToStoneRatio (" + actualRatio + ") >= warning (0.05)");

            DetectionResult result = eng.evaluate(playerId, stats);
            assertTrue(result.isDetected());
            assertEquals(AlertLevel.WARNING, result.getLevel());
        }

        @Test
        @DisplayName("Three metrics above critical threshold returns CRITICAL")
        void threeMetricsAboveCritical_returnsCritical() {
            DetectionEngine.DetectionThresholds t = isolatedThresholds("oreToStoneRatio", 0.0001, 0.01);
            t.diamondToStoneRatioWarning = 0.00001; t.diamondToStoneRatioCritical = 0.001;
            t.orePerHourWarning = 1.0; t.orePerHourCritical = 10.0;
            DetectionEngine eng = new DetectionEngine(t);

            PlayerStatistics stats = createStatsWithSampleSize(200);
            for (int i = 0; i < 50; i++) {
                stats.onBlockBreak(org.bukkit.Material.DIAMOND_ORE, -10,
                        org.bukkit.block.Biome.PLAINS, true, null);
            }
            stats.updatePlayTime(60);

            DetectionResult result = eng.evaluate(playerId, stats);
            assertTrue(result.isDetected());
            assertEquals(AlertLevel.CRITICAL, result.getLevel());
            assertTrue(result.getTriggeredMetrics().contains("oreToStoneRatio"));
            assertTrue(result.getTriggeredMetrics().contains("diamondToStoneRatio"));
            assertTrue(result.getTriggeredMetrics().contains("orePerHour"));
        }

        @Test
        @DisplayName("Clear X-ray pattern (short and long windows critical) returns CRITICAL")
        void clearXrayPattern_returnsCritical() {
            DetectionEngine.DetectionThresholds t = isolatedThresholds("shortWindowOreRatio", 0.001, 0.01);
            t.longWindowOreRatioWarning = 0.001; t.longWindowOreRatioCritical = 0.01;
            DetectionEngine eng = new DetectionEngine(t);

            PlayerStatistics stats = createStatsWithSampleSize(200);
            for (int i = 0; i < 50; i++) {
                stats.onBlockBreak(org.bukkit.Material.DIAMOND_ORE, -10,
                        org.bukkit.block.Biome.PLAINS, true, null);
            }
            stats.updatePlayTime(120);

            DetectionResult result = eng.evaluate(playerId, stats);
            assertTrue(result.isDetected());
            assertEquals(AlertLevel.CRITICAL, result.getLevel());
            assertTrue(result.getTriggeredMetrics().contains("shortWindowOreRatio"));
            assertTrue(result.getTriggeredMetrics().contains("longWindowOreRatio"));
        }

        @Test
        @DisplayName("Exactly one metric above warning returns INFO not WARNING")
        void exactlyOneMetricAboveWarning_returnsInfoNotWarning() {
            DetectionEngine.DetectionThresholds t = isolatedThresholds("oreToStoneRatio", 0.05, 1.0);
            DetectionEngine eng = new DetectionEngine(t);

            PlayerStatistics stats = createStatsWithSampleSize(200);
            for (int i = 0; i < 20; i++) {
                stats.onBlockBreak(org.bukkit.Material.IRON_ORE, 11,
                        org.bukkit.block.Biome.PLAINS, false, null);
            }
            stats.updatePlayTime(60);

            DetectionResult result = eng.evaluate(playerId, stats);
            if (result.isDetected()) {
                assertEquals(AlertLevel.INFO, result.getLevel(),
                        "Single metric slightly above warning should be INFO");
            }
        }
    }

    @Nested
    @DisplayName("Grace period guard")
    class GracePeriodGuard {

        @Test
        @DisplayName("Within grace period never flags CRITICAL")
        void withinGracePeriod_noCritical() {
            DetectionEngine.DetectionThresholds t = isolatedThresholds("shortWindowOreRatio", 0.001, 0.01);
            t.longWindowOreRatioWarning = 0.001; t.longWindowOreRatioCritical = 0.01;
            t.oreToStoneRatioWarning = 0.0001; t.oreToStoneRatioCritical = 0.01;
            t.diamondToStoneRatioWarning = 0.00001; t.diamondToStoneRatioCritical = 0.001;
            t.orePerHourWarning = 1.0; t.orePerHourCritical = 10.0;
            DetectionEngine eng = new DetectionEngine(t, 60_000L, 100, 30);

            PlayerStatistics stats = createStatsWithSampleSize(200);
            for (int i = 0; i < 50; i++) {
                stats.onBlockBreak(org.bukkit.Material.DIAMOND_ORE, -10,
                        org.bukkit.block.Biome.PLAINS, true, null);
            }
            stats.updatePlayTime(10);

            DetectionResult result = eng.evaluate(playerId, stats);
            assertTrue(result.isDetected());
            assertNotEquals(AlertLevel.CRITICAL, result.getLevel(),
                    "Should not flag CRITICAL within grace period");
        }

        @Test
        @DisplayName("Within grace period can flag WARNING")
        void withinGracePeriod_canFlagWarning() {
            DetectionEngine.DetectionThresholds t = isolatedThresholds("oreToStoneRatio", 0.01, 99.0);
            t.diamondToStoneRatioWarning = 0.0005; t.diamondToStoneRatioCritical = 99.0;
            DetectionEngine eng = new DetectionEngine(t, 60_000L, 100, 30);

            PlayerStatistics stats = createStatsWithSampleSize(200);
            for (int i = 0; i < 20; i++) {
                stats.onBlockBreak(org.bukkit.Material.DIAMOND_ORE, -10,
                        org.bukkit.block.Biome.PLAINS, true, null);
            }
            stats.updatePlayTime(10);

            DetectionResult result = eng.evaluate(playerId, stats);
            assertTrue(result.isDetected());
            assertEquals(AlertLevel.WARNING, result.getLevel());
        }

        @Test
        @DisplayName("Within grace period can flag INFO")
        void withinGracePeriod_canFlagInfo() {
            DetectionEngine.DetectionThresholds t = isolatedThresholds("oreToStoneRatio", 0.01, 99.0);
            DetectionEngine eng = new DetectionEngine(t, 60_000L, 100, 30);

            PlayerStatistics stats = createStatsWithSampleSize(200);
            for (int i = 0; i < 20; i++) {
                stats.onBlockBreak(org.bukkit.Material.DIAMOND_ORE, -10,
                        org.bukkit.block.Biome.PLAINS, true, null);
            }
            stats.updatePlayTime(10);

            DetectionResult result = eng.evaluate(playerId, stats);
            assertTrue(result.isDetected());
            assertNotEquals(AlertLevel.CRITICAL, result.getLevel());
        }

        @Test
        @DisplayName("After grace period can flag CRITICAL")
        void afterGracePeriod_canFlagCritical() {
            DetectionEngine.DetectionThresholds t = isolatedThresholds("shortWindowOreRatio", 0.001, 0.01);
            t.longWindowOreRatioWarning = 0.001; t.longWindowOreRatioCritical = 0.01;
            t.oreToStoneRatioWarning = 0.0001; t.oreToStoneRatioCritical = 0.01;
            t.diamondToStoneRatioWarning = 0.00001; t.diamondToStoneRatioCritical = 0.001;
            t.orePerHourWarning = 1.0; t.orePerHourCritical = 10.0;
            DetectionEngine eng = new DetectionEngine(t, 60_000L, 100, 30);

            PlayerStatistics stats = createStatsWithSampleSize(200);
            for (int i = 0; i < 50; i++) {
                stats.onBlockBreak(org.bukkit.Material.DIAMOND_ORE, -10,
                        org.bukkit.block.Biome.PLAINS, true, null);
            }
            stats.updatePlayTime(60);

            DetectionResult result = eng.evaluate(playerId, stats);
            assertTrue(result.isDetected());
            assertEquals(AlertLevel.CRITICAL, result.getLevel());
        }

        @Test
        @DisplayName("Default grace period is 30 minutes")
        void defaultGracePeriodIs30() {
            assertEquals(30, engine.getGracePeriodMinutes());
        }

        @Test
        @DisplayName("Custom grace period is respected")
        void customGracePeriod_isRespected() {
            DetectionEngine custom = new DetectionEngine(thresholds, 60_000L, 100, 60);
            assertEquals(60, custom.getGracePeriodMinutes());
        }

        @Test
        @DisplayName("Grace period at exactly 30 minutes allows CRITICAL")
        void gracePeriodExactly30_allowsCritical() {
            DetectionEngine.DetectionThresholds t = isolatedThresholds("shortWindowOreRatio", 0.001, 0.01);
            t.longWindowOreRatioWarning = 0.001; t.longWindowOreRatioCritical = 0.01;
            t.oreToStoneRatioWarning = 0.0001; t.oreToStoneRatioCritical = 0.01;
            t.diamondToStoneRatioWarning = 0.00001; t.diamondToStoneRatioCritical = 0.001;
            t.orePerHourWarning = 1.0; t.orePerHourCritical = 10.0;
            DetectionEngine eng = new DetectionEngine(t, 60_000L, 100, 30);

            PlayerStatistics stats = createStatsWithSampleSize(200);
            for (int i = 0; i < 50; i++) {
                stats.onBlockBreak(org.bukkit.Material.DIAMOND_ORE, -10,
                        org.bukkit.block.Biome.PLAINS, true, null);
            }
            stats.updatePlayTime(30);

            DetectionResult result = eng.evaluate(playerId, stats);
            if (result.isDetected()) {
                assertEquals(AlertLevel.CRITICAL, result.getLevel(),
                        "At exactly grace period minutes, CRITICAL should be allowed");
            }
        }
    }

    @Nested
    @DisplayName("Rate limiting")
    class RateLimiting {

        @Test
        @DisplayName("First evaluation is never rate limited")
        void firstEvaluation_notRateLimited() {
            PlayerStatistics stats = createCleanStats();
            DetectionResult result = engine.evaluate(playerId, stats);
            assertFalse(result.isRateLimited());
        }

        @Test
        @DisplayName("Rapid second alert is rate limited")
        void rapidSecondAlert_isRateLimited() {
            DetectionEngine.DetectionThresholds t = isolatedThresholds("oreToStoneRatio", 0.01, 99.0);
            DetectionEngine eng = new DetectionEngine(t);

            PlayerStatistics stats = createStatsWithSampleSize(200);
            for (int i = 0; i < 20; i++) {
                stats.onBlockBreak(org.bukkit.Material.DIAMOND_ORE, -10,
                        org.bukkit.block.Biome.PLAINS, true, null);
            }
            stats.updatePlayTime(120);

            DetectionResult first = eng.evaluate(playerId, stats);
            assertTrue(first.isDetected());
            assertFalse(first.isRateLimited());

            DetectionResult second = eng.evaluate(playerId, stats);
            assertTrue(second.isRateLimited());
        }

        @Test
        @DisplayName("No detection does not update rate limit timestamp")
        void noDetection_doesNotUpdateRateLimit() {
            PlayerStatistics cleanStats = createCleanStats();
            engine.evaluate(playerId, cleanStats);

            assertFalse(engine.isRateLimited(playerId));
        }

        @Test
        @DisplayName("clearRateLimit allows immediate next alert")
        void clearRateLimit_allowsImmediateAlert() {
            DetectionEngine.DetectionThresholds t = isolatedThresholds("oreToStoneRatio", 0.01, 99.0);
            DetectionEngine eng = new DetectionEngine(t);

            PlayerStatistics stats = createStatsWithSampleSize(200);
            for (int i = 0; i < 20; i++) {
                stats.onBlockBreak(org.bukkit.Material.DIAMOND_ORE, -10,
                        org.bukkit.block.Biome.PLAINS, true, null);
            }
            stats.updatePlayTime(120);

            eng.evaluate(playerId, stats);
            assertTrue(eng.isRateLimited(playerId));

            eng.clearRateLimit(playerId);
            assertFalse(eng.isRateLimited(playerId));

            DetectionResult after = eng.evaluate(playerId, stats);
            assertTrue(after.isDetected());
            assertFalse(after.isRateLimited());
        }

        @Test
        @DisplayName("clearAllRateLimits clears all players")
        void clearAllRateLimits_clearsAll() {
            UUID player2 = UUID.randomUUID();
            DetectionEngine.DetectionThresholds t = isolatedThresholds("oreToStoneRatio", 0.01, 99.0);
            DetectionEngine eng = new DetectionEngine(t);

            PlayerStatistics stats = createStatsWithSampleSize(200);
            for (int i = 0; i < 20; i++) {
                stats.onBlockBreak(org.bukkit.Material.DIAMOND_ORE, -10,
                        org.bukkit.block.Biome.PLAINS, true, null);
            }
            stats.updatePlayTime(120);

            eng.evaluate(playerId, stats);
            eng.evaluate(player2, stats);

            eng.clearAllRateLimits();

            assertFalse(eng.isRateLimited(playerId));
            assertFalse(eng.isRateLimited(player2));
        }

        @Test
        @DisplayName("Rate limited result has no alert level")
        void rateLimitedResult_hasNoAlertLevel() {
            DetectionEngine.DetectionThresholds t = isolatedThresholds("oreToStoneRatio", 0.01, 99.0);
            DetectionEngine eng = new DetectionEngine(t);

            PlayerStatistics stats = createStatsWithSampleSize(200);
            for (int i = 0; i < 20; i++) {
                stats.onBlockBreak(org.bukkit.Material.DIAMOND_ORE, -10,
                        org.bukkit.block.Biome.PLAINS, true, null);
            }
            stats.updatePlayTime(120);

            eng.evaluate(playerId, stats);
            DetectionResult result = eng.evaluate(playerId, stats);
            assertTrue(result.isRateLimited());
            assertFalse(result.isDetected());
            assertNull(result.getLevel());
        }

        @Test
        @DisplayName("Different players have independent rate limits")
        void differentPlayers_independentRateLimits() {
            UUID player2 = UUID.randomUUID();

            DetectionEngine.DetectionThresholds t = isolatedThresholds("oreToStoneRatio", 0.01, 99.0);
            DetectionEngine eng = new DetectionEngine(t);

            PlayerStatistics stats = createStatsWithSampleSize(200);
            for (int i = 0; i < 20; i++) {
                stats.onBlockBreak(org.bukkit.Material.DIAMOND_ORE, -10,
                        org.bukkit.block.Biome.PLAINS, true, null);
            }
            stats.updatePlayTime(120);

            eng.evaluate(playerId, stats);
            assertTrue(eng.isRateLimited(playerId));
            assertFalse(eng.isRateLimited(player2));
        }

        @Test
        @DisplayName("isRateLimited returns false for unknown player")
        void isRateLimited_unknownPlayer_returnsFalse() {
            assertFalse(engine.isRateLimited(UUID.randomUUID()));
        }
    }

    @Nested
    @DisplayName("Play style classification adjustment")
    class PlayStyleAdjustment {

        @Test
        @DisplayName("Caving play style applies threshold multiplier")
        void cavingStyle_appliesMultiplier() {
            double warning = 0.08;
            double cavingWarning = warning * PlayStyleClassifier.CAVING.getThresholdMultiplier();
            DetectionEngine.DetectionThresholds t = isolatedThresholds("oreToStoneRatio", warning, 0.15);
            DetectionEngine eng = new DetectionEngine(t);

            PlayerStatistics stats = new PlayerStatistics();
            for (int i = 0; i < 150; i++) {
                stats.onBlockBreak(org.bukkit.Material.STONE, 64,
                        org.bukkit.block.Biome.PLAINS, true, null);
            }
            stats.updatePlayTime(60);

            assertEquals(PlayStyleClassifier.CAVING, PlayStyleClassifier.classify(stats),
                    "Player with high air-adjacent ratio should be classified as caving");

            double ratio = stats.getOreToStoneRatio();
            if (ratio < cavingWarning) {
                DetectionResult result = eng.evaluate(playerId, stats);
                assertFalse(result.isDetected(),
                        "Caving multiplier should raise warning threshold, avoiding false positive");
            }
        }

        @Test
        @DisplayName("Branch mining play style uses base thresholds")
        void branchMining_usesBaseThresholds() {
            DetectionEngine.DetectionThresholds t = isolatedThresholds("oreToStoneRatio", 0.08, 0.15);
            DetectionEngine eng = new DetectionEngine(t);

            PlayerStatistics stats = new PlayerStatistics();
            for (int i = 0; i < 150; i++) {
                stats.onBlockBreak(org.bukkit.Material.STONE, 64,
                        org.bukkit.block.Biome.PLAINS, false, null);
            }
            stats.updatePlayTime(60);

            assertEquals(PlayStyleClassifier.BRANCH_MINING, PlayStyleClassifier.classify(stats),
                    "Player with low air-adjacent ratio should be classified as branch mining");

            double ratio = stats.getOreToStoneRatio();
            if (ratio < t.oreToStoneRatioWarning) {
                DetectionResult result = eng.evaluate(playerId, stats);
                assertFalse(result.isDetected());
            }
        }

        @Test
        @DisplayName("Caving multiplier is 1.3")
        void cavingMultiplierIs1_3() {
            assertEquals(1.3, PlayStyleClassifier.CAVING.getThresholdMultiplier(), 0.001);
        }

        @Test
        @DisplayName("Branch mining multiplier is 1.0")
        void branchMiningMultiplierIs1_0() {
            assertEquals(1.0, PlayStyleClassifier.BRANCH_MINING.getThresholdMultiplier(), 0.001);
        }
    }

    @Nested
    @DisplayName("Both windows required for CRITICAL via xray pattern")
    class BothWindowsRequiredForCritical {

        @Test
        @DisplayName("Only short window critical does not produce CRITICAL via xray pattern")
        void onlyShortWindowCritical_noXrayPatternCritical() {
            DetectionEngine.DetectionThresholds t = isolatedThresholds("shortWindowOreRatio", 0.001, 0.01);
            DetectionEngine eng = new DetectionEngine(t);

            PlayerStatistics stats = createStatsWithSampleSize(200);
            for (int i = 0; i < 50; i++) {
                stats.onBlockBreak(org.bukkit.Material.DIAMOND_ORE, -10,
                        org.bukkit.block.Biome.PLAINS, true, null);
            }
            stats.updatePlayTime(120);

            DetectionResult result = eng.evaluate(playerId, stats);
            if (result.isDetected()) {
                assertNotEquals(AlertLevel.CRITICAL, result.getLevel(),
                        "CRITICAL from xray pattern requires both short AND long windows critical");
            }
        }

        @Test
        @DisplayName("Only long window critical does not produce CRITICAL via xray pattern")
        void onlyLongWindowCritical_noXrayPatternCritical() {
            DetectionEngine.DetectionThresholds t = isolatedThresholds("longWindowOreRatio", 0.001, 0.01);
            DetectionEngine eng = new DetectionEngine(t);

            PlayerStatistics stats = createStatsWithSampleSize(200);
            for (int i = 0; i < 50; i++) {
                stats.onBlockBreak(org.bukkit.Material.DIAMOND_ORE, -10,
                        org.bukkit.block.Biome.PLAINS, true, null);
            }
            stats.updatePlayTime(120);

            DetectionResult result = eng.evaluate(playerId, stats);
            if (result.isDetected()) {
                assertNotEquals(AlertLevel.CRITICAL, result.getLevel(),
                        "CRITICAL from xray pattern requires both short AND long windows critical");
            }
        }

        @Test
        @DisplayName("Neither window critical cannot produce CRITICAL via xray pattern")
        void neitherWindowCritical_noXrayPatternCritical() {
            DetectionEngine.DetectionThresholds t = isolatedThresholds("oreToStoneRatio", 0.01, 0.05);
            DetectionEngine eng = new DetectionEngine(t);

            PlayerStatistics stats = createStatsWithSampleSize(200);
            for (int i = 0; i < 20; i++) {
                stats.onBlockBreak(org.bukkit.Material.DIAMOND_ORE, -10,
                        org.bukkit.block.Biome.PLAINS, true, null);
            }
            stats.updatePlayTime(120);

            DetectionResult result = eng.evaluate(playerId, stats);
            if (result.isDetected()) {
                assertNotEquals(AlertLevel.CRITICAL, result.getLevel(),
                        "CRITICAL from xray pattern requires both windows critical");
            }
        }
    }

    @Nested
    @DisplayName("DetectionResult")
    class DetectionResultTests {

        @Test
        @DisplayName("none() is not detected")
        void none_isNotDetected() {
            DetectionResult result = DetectionResult.none();
            assertFalse(result.isDetected());
            assertNull(result.getLevel());
            assertTrue(result.getTriggeredMetrics().isEmpty());
            assertFalse(result.isRateLimited());
        }

        @Test
        @DisplayName("of() creates detected result")
        void of_createsDetectedResult() {
            DetectionResult result = DetectionResult.of(AlertLevel.WARNING,
                    java.util.List.of("oreToStoneRatio"));
            assertTrue(result.isDetected());
            assertEquals(AlertLevel.WARNING, result.getLevel());
            assertEquals(1, result.getTriggeredMetrics().size());
            assertEquals("oreToStoneRatio", result.getTriggeredMetrics().get(0));
        }

        @Test
        @DisplayName("rateLimited() creates rate limited result")
        void rateLimited_createsRateLimitedResult() {
            DetectionResult result = DetectionResult.rateLimited();
            assertTrue(result.isRateLimited());
            assertFalse(result.isDetected());
            assertNull(result.getLevel());
            assertTrue(result.getTriggeredMetrics().isEmpty());
        }

        @Test
        @DisplayName("triggeredMetrics is unmodifiable")
        void triggeredMetrics_isUnmodifiable() {
            DetectionResult result = DetectionResult.of(AlertLevel.INFO,
                    java.util.List.of("metric1"));
            assertThrows(UnsupportedOperationException.class,
                    () -> result.getTriggeredMetrics().add("metric2"));
        }

        @Test
        @DisplayName("none() toString is readable")
        void none_toString() {
            DetectionResult result = DetectionResult.none();
            assertTrue(result.toString().contains("none"));
        }

        @Test
        @DisplayName("rateLimited() toString is readable")
        void rateLimited_toString() {
            DetectionResult result = DetectionResult.rateLimited();
            assertTrue(result.toString().contains("rateLimited"));
        }

        @Test
        @DisplayName("detected result toString contains level")
        void detected_toString() {
            DetectionResult result = DetectionResult.of(AlertLevel.WARNING,
                    java.util.List.of("metric1"));
            assertTrue(result.toString().contains("WARNING"));
        }
    }

    @Nested
    @DisplayName("AlertLevel enum")
    class AlertLevelTests {

        @Test
        @DisplayName("AlertLevel has INFO, WARNING, CRITICAL")
        void alertLevel_hasAllValues() {
            AlertLevel[] levels = AlertLevel.values();
            assertEquals(3, levels.length);
            assertNotNull(AlertLevel.INFO);
            assertNotNull(AlertLevel.WARNING);
            assertNotNull(AlertLevel.CRITICAL);
        }

        @Test
        @DisplayName("AlertLevel valueOf works")
        void alertLevel_valueOf() {
            assertEquals(AlertLevel.INFO, AlertLevel.valueOf("INFO"));
            assertEquals(AlertLevel.WARNING, AlertLevel.valueOf("WARNING"));
            assertEquals(AlertLevel.CRITICAL, AlertLevel.valueOf("CRITICAL"));
        }
    }

    @Nested
    @DisplayName("DetectionThresholds")
    class DetectionThresholdsTests {

        @Test
        @DisplayName("defaults() creates non-zero thresholds")
        void defaults_createsNonZeroThresholds() {
            DetectionEngine.DetectionThresholds t = DetectionEngine.DetectionThresholds.defaults();
            assertTrue(t.oreToStoneRatioWarning > 0);
            assertTrue(t.oreToStoneRatioCritical > t.oreToStoneRatioWarning);
            assertTrue(t.diamondToStoneRatioWarning > 0);
            assertTrue(t.diamondToStoneRatioCritical > t.diamondToStoneRatioWarning);
            assertTrue(t.orePerHourWarning > 0);
            assertTrue(t.orePerHourCritical > t.orePerHourWarning);
            assertTrue(t.diamondPerHourWarning > 0);
            assertTrue(t.diamondPerHourCritical > t.diamondPerHourWarning);
            assertTrue(t.shortWindowOreRatioWarning > 0);
            assertTrue(t.shortWindowOreRatioCritical > t.shortWindowOreRatioWarning);
            assertTrue(t.longWindowOreRatioWarning > 0);
            assertTrue(t.longWindowOreRatioCritical > t.longWindowOreRatioWarning);
            assertTrue(t.valuableOreRatioWarning > 0);
            assertTrue(t.valuableOreRatioCritical > t.valuableOreRatioWarning);
            assertTrue(t.straightToOreRatioWarning > 0);
            assertTrue(t.straightToOreRatioCritical > t.straightToOreRatioWarning);
        }

        @Test
        @DisplayName("copy() creates independent copy")
        void copy_createsIndependentCopy() {
            DetectionEngine.DetectionThresholds original = DetectionEngine.DetectionThresholds.defaults();
            DetectionEngine.DetectionThresholds copy = original.copy();

            copy.oreToStoneRatioWarning = 999.0;
            assertNotEquals(original.oreToStoneRatioWarning, copy.oreToStoneRatioWarning);
        }

        @Test
        @DisplayName("copy() preserves all values")
        void copy_preservesAllValues() {
            DetectionEngine.DetectionThresholds original = DetectionEngine.DetectionThresholds.defaults();
            DetectionEngine.DetectionThresholds copy = original.copy();

            assertEquals(original.oreToStoneRatioWarning, copy.oreToStoneRatioWarning);
            assertEquals(original.oreToStoneRatioCritical, copy.oreToStoneRatioCritical);
            assertEquals(original.diamondToStoneRatioWarning, copy.diamondToStoneRatioWarning);
            assertEquals(original.diamondToStoneRatioCritical, copy.diamondToStoneRatioCritical);
            assertEquals(original.shortWindowOreRatioWarning, copy.shortWindowOreRatioWarning);
            assertEquals(original.shortWindowOreRatioCritical, copy.shortWindowOreRatioCritical);
            assertEquals(original.longWindowOreRatioWarning, copy.longWindowOreRatioWarning);
            assertEquals(original.longWindowOreRatioCritical, copy.longWindowOreRatioCritical);
        }
    }

    @Nested
    @DisplayName("Engine configuration accessors")
    class EngineConfigurationTests {

        @Test
        @DisplayName("getThresholds returns configured thresholds")
        void getThresholds_returnsConfigured() {
            assertSame(thresholds, engine.getThresholds());
        }

        @Test
        @DisplayName("getMinimumSampleSize returns default 100")
        void getMinimumSampleSize_returnsDefault() {
            assertEquals(100, engine.getMinimumSampleSize());
        }

        @Test
        @DisplayName("getRateLimitMillis returns default 60000")
        void getRateLimitMillis_returnsDefault() {
            assertEquals(60_000L, engine.getRateLimitMillis());
        }

        @Test
        @DisplayName("getGracePeriodMinutes returns default 30")
        void getGracePeriodMinutes_returnsDefault() {
            assertEquals(30, engine.getGracePeriodMinutes());
        }

        @Test
        @DisplayName("Custom constructor parameters are accessible")
        void customConstructor_parametersAccessible() {
            DetectionEngine custom = new DetectionEngine(thresholds, 30_000L, 200, 60);
            assertEquals(30_000L, custom.getRateLimitMillis());
            assertEquals(200, custom.getMinimumSampleSize());
            assertEquals(60, custom.getGracePeriodMinutes());
        }
    }

    @Nested
    @DisplayName("Integration scenarios")
    class IntegrationScenarios {

        @Test
        @DisplayName("Branch miner with normal stats is not flagged")
        void branchMiner_normalStats_notFlagged() {
            PlayerStatistics stats = new PlayerStatistics();
            for (int i = 0; i < 500; i++) {
                stats.onBlockBreak(org.bukkit.Material.STONE, 11,
                        org.bukkit.block.Biome.PLAINS, false, null);
            }
            stats.onBlockBreak(org.bukkit.Material.DIAMOND_ORE, 11,
                    org.bukkit.block.Biome.PLAINS, false, null);
            stats.onBlockBreak(org.bukkit.Material.IRON_ORE, 11,
                    org.bukkit.block.Biome.PLAINS, false, null);
            stats.updatePlayTime(60);

            DetectionResult result = engine.evaluate(playerId, stats);
            assertFalse(result.isDetected());
        }

        @Test
        @DisplayName("Obvious X-rayer flags CRITICAL")
        void obviousXrayer_flagsCritical() {
            DetectionEngine.DetectionThresholds t = isolatedThresholds("shortWindowOreRatio", 0.001, 0.01);
            t.longWindowOreRatioWarning = 0.001; t.longWindowOreRatioCritical = 0.01;
            t.oreToStoneRatioWarning = 0.0001; t.oreToStoneRatioCritical = 0.01;
            t.diamondToStoneRatioWarning = 0.00001; t.diamondToStoneRatioCritical = 0.001;
            t.orePerHourWarning = 1.0; t.orePerHourCritical = 10.0;
            DetectionEngine eng = new DetectionEngine(t);

            PlayerStatistics stats = new PlayerStatistics();
            for (int i = 0; i < 100; i++) {
                stats.onBlockBreak(org.bukkit.Material.DIAMOND_ORE, -10,
                        org.bukkit.block.Biome.PLAINS, false, null);
            }
            stats.updatePlayTime(60);

            DetectionResult result = eng.evaluate(playerId, stats);
            assertTrue(result.isDetected());
            assertEquals(AlertLevel.CRITICAL, result.getLevel());
        }

        @Test
        @DisplayName("Slightly suspicious player flags INFO with single metric")
        void slightlySuspicious_flagsInfo() {
            DetectionEngine.DetectionThresholds t = isolatedThresholds("oreToStoneRatio", 0.05, 0.50);
            DetectionEngine eng = new DetectionEngine(t);

            PlayerStatistics stats = new PlayerStatistics();
            for (int i = 0; i < 100; i++) {
                stats.onBlockBreak(org.bukkit.Material.STONE, 11,
                        org.bukkit.block.Biome.PLAINS, false, null);
            }
            for (int i = 0; i < 10; i++) {
                stats.onBlockBreak(org.bukkit.Material.IRON_ORE, 11,
                        org.bukkit.block.Biome.PLAINS, false, null);
            }
            stats.updatePlayTime(60);

            DetectionResult result = eng.evaluate(playerId, stats);
            assertTrue(result.isDetected());
            assertEquals(AlertLevel.INFO, result.getLevel());
        }

        @Test
        @DisplayName("Rate limiting prevents alert spam across multiple evaluations")
        void rateLimiting_preventsAlertSpam() {
            DetectionEngine.DetectionThresholds t = isolatedThresholds("oreToStoneRatio", 0.01, 99.0);
            DetectionEngine eng = new DetectionEngine(t);

            PlayerStatistics stats = createStatsWithSampleSize(200);
            for (int i = 0; i < 20; i++) {
                stats.onBlockBreak(org.bukkit.Material.DIAMOND_ORE, -10,
                        org.bukkit.block.Biome.PLAINS, true, null);
            }
            stats.updatePlayTime(120);

            DetectionResult first = eng.evaluate(playerId, stats);
            assertTrue(first.isDetected());
            assertFalse(first.isRateLimited());

            for (int i = 0; i < 5; i++) {
                DetectionResult subsequent = eng.evaluate(playerId, stats);
                assertTrue(subsequent.isRateLimited(), "Subsequent evaluation " + (i + 1) + " should be rate limited");
            }

            eng.clearRateLimit(playerId);
            DetectionResult after = eng.evaluate(playerId, stats);
            assertTrue(after.isDetected());
            assertFalse(after.isRateLimited());
        }

        @Test
        @DisplayName("Multiple players can be detected independently")
        void multiplePlayers_detectedIndependently() {
            UUID player1 = UUID.randomUUID();
            UUID player2 = UUID.randomUUID();

            DetectionEngine.DetectionThresholds t = isolatedThresholds("oreToStoneRatio", 0.01, 99.0);
            DetectionEngine eng = new DetectionEngine(t);

            PlayerStatistics suspiciousStats = createStatsWithSampleSize(200);
            for (int i = 0; i < 20; i++) {
                suspiciousStats.onBlockBreak(org.bukkit.Material.DIAMOND_ORE, -10,
                        org.bukkit.block.Biome.PLAINS, true, null);
            }
            suspiciousStats.updatePlayTime(120);

            PlayerStatistics cleanStats = createStatsWithSampleSize(200);
            cleanStats.updatePlayTime(120);

            DetectionResult result1 = eng.evaluate(player1, suspiciousStats);
            DetectionResult result2 = eng.evaluate(player2, cleanStats);

            assertTrue(result1.isDetected());
            assertFalse(result2.isDetected());
        }
    }
}
