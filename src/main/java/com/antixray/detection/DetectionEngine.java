package com.antixray.detection;

import org.bukkit.entity.Player;
import com.antixray.api.AlertLevel;
import com.antixray.AntiXrayPlugin;
import com.antixray.deobfuscation.PlayerData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DetectionEngine {

    private static final long DEFAULT_RATE_LIMIT_MILLIS = 60_000L;
    private static final int DEFAULT_MINIMUM_SAMPLE_SIZE = 100;
    private static final int DEFAULT_GRACE_PERIOD_MINUTES = 30;

    private final DetectionThresholds thresholds;
    private final Map<UUID, Long> lastAlertTimes = new HashMap<>();
    private final Map<UUID, List<String>> lastTriggeredMetrics = new HashMap<>();
    private final long rateLimitMillis;
    private final int minimumSampleSize;
    private final int gracePeriodMinutes;

    public DetectionEngine(DetectionThresholds thresholds) {
        this(thresholds, DEFAULT_RATE_LIMIT_MILLIS, DEFAULT_MINIMUM_SAMPLE_SIZE, DEFAULT_GRACE_PERIOD_MINUTES);
    }

    public DetectionEngine(DetectionThresholds thresholds, long rateLimitMillis, int minimumSampleSize, int gracePeriodMinutes) {
        this.thresholds = thresholds;
        this.rateLimitMillis = rateLimitMillis;
        this.minimumSampleSize = minimumSampleSize;
        this.gracePeriodMinutes = gracePeriodMinutes;
    }

    public DetectionResult evaluate(UUID playerId, PlayerStatistics statistics) {
        if (statistics.getTotalMined() < minimumSampleSize) {
            return DetectionResult.none();
        }

        PlayStyleClassifier playStyle = PlayStyleClassifier.classify(statistics);
        double multiplier = playStyle.getThresholdMultiplier();

        List<ThresholdBreach> warningBreaches = new ArrayList<>();
        List<ThresholdBreach> criticalBreaches = new ArrayList<>();
        List<String> significantlyAboveWarning = new ArrayList<>();

        checkMetric(warningBreaches, criticalBreaches, significantlyAboveWarning,
                "oreToStoneRatio", statistics.getOreToStoneRatio(),
                thresholds.oreToStoneRatioWarning * multiplier,
                thresholds.oreToStoneRatioCritical * multiplier);

        checkMetric(warningBreaches, criticalBreaches, significantlyAboveWarning,
                "diamondToStoneRatio", statistics.getDiamondToStoneRatio(),
                thresholds.diamondToStoneRatioWarning * multiplier,
                thresholds.diamondToStoneRatioCritical * multiplier);

        checkMetric(warningBreaches, criticalBreaches, significantlyAboveWarning,
                "orePerHour", statistics.getOrePerHour(),
                thresholds.orePerHourWarning * multiplier,
                thresholds.orePerHourCritical * multiplier);

        checkMetric(warningBreaches, criticalBreaches, significantlyAboveWarning,
                "diamondPerHour", statistics.getDiamondPerHour(),
                thresholds.diamondPerHourWarning * multiplier,
                thresholds.diamondPerHourCritical * multiplier);

        checkMetric(warningBreaches, criticalBreaches, significantlyAboveWarning,
                "shortWindowOreRatio", statistics.getShortWindowOreRatio(),
                thresholds.shortWindowOreRatioWarning * multiplier,
                thresholds.shortWindowOreRatioCritical * multiplier);

        checkMetric(warningBreaches, criticalBreaches, significantlyAboveWarning,
                "longWindowOreRatio", statistics.getLongWindowOreRatio(),
                thresholds.longWindowOreRatioWarning * multiplier,
                thresholds.longWindowOreRatioCritical * multiplier);

        if (statistics.getTotalOresMined() >= 10) {
            checkMetric(warningBreaches, criticalBreaches, significantlyAboveWarning,
                    "valuableOreRatio", statistics.getValuableOreRatio(),
                    thresholds.valuableOreRatioWarning * multiplier,
                    thresholds.valuableOreRatioCritical * multiplier);
        }

        if (statistics.getDirectionChanges() >= 10) {
            checkMetric(warningBreaches, criticalBreaches, significantlyAboveWarning,
                    "straightToOreRatio", statistics.getStraightToOreRatio(),
                    thresholds.straightToOreRatioWarning * multiplier,
                    thresholds.straightToOreRatioCritical * multiplier);
        }

        if (warningBreaches.isEmpty() && criticalBreaches.isEmpty()) {
            return DetectionResult.none();
        }

        boolean withinGracePeriod = statistics.getPlayTimeMinutes() < gracePeriodMinutes;
        boolean shortWindowCritical = isMetricCritical("shortWindowOreRatio", criticalBreaches);
        boolean longWindowCritical = isMetricCritical("longWindowOreRatio", criticalBreaches);
        boolean clearXrayPattern = shortWindowCritical && longWindowCritical;

        AlertLevel level;
        List<String> triggeredMetrics;

        if (!withinGracePeriod && (criticalBreaches.size() >= 3 || clearXrayPattern)) {
            level = AlertLevel.CRITICAL;
            triggeredMetrics = extractNames(criticalBreaches);
        } else if (warningBreaches.size() >= 2 || !significantlyAboveWarning.isEmpty()) {
            level = AlertLevel.WARNING;
            triggeredMetrics = extractNames(warningBreaches);
        } else {
            level = AlertLevel.INFO;
            triggeredMetrics = extractNames(warningBreaches);
        }

        if (isRateLimited(playerId)) {
            return DetectionResult.rateLimited();
        }

        lastAlertTimes.put(playerId, System.currentTimeMillis());
        return DetectionResult.of(level, triggeredMetrics);
    }

    public AlertLevel evaluate(Player player) {
        if (player == null) return null;
        AntiXrayPlugin plugin = AntiXrayPlugin.getInstance();
        if (plugin == null) return null;
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        if (data == null) return null;
        PlayerStatistics stats = data.getStatistics();
        if (stats == null) return null;

        DetectionResult result = evaluate(player.getUniqueId(), stats);
        if (result != null && result.isDetected()) {
            lastTriggeredMetrics.put(player.getUniqueId(), result.getTriggeredMetrics());
            return result.getLevel();
        }
        lastTriggeredMetrics.remove(player.getUniqueId());
        return null;
    }

    public List<String> getLastTriggeredMetrics(UUID playerId) {
        return lastTriggeredMetrics.getOrDefault(playerId, java.util.Collections.emptyList());
    }

    public boolean isRateLimited(UUID playerId) {
        Long lastAlert = lastAlertTimes.get(playerId);
        if (lastAlert == null) return false;
        return (System.currentTimeMillis() - lastAlert) < rateLimitMillis;
    }

    public void clearRateLimit(UUID playerId) {
        lastAlertTimes.remove(playerId);
    }

    public void clearAllRateLimits() {
        lastAlertTimes.clear();
    }

    public DetectionThresholds getThresholds() {
        return thresholds;
    }

    public int getMinimumSampleSize() {
        return minimumSampleSize;
    }

    public int getGracePeriodMinutes() {
        return gracePeriodMinutes;
    }

    public long getRateLimitMillis() {
        return rateLimitMillis;
    }

    private void checkMetric(List<ThresholdBreach> warningBreaches,
                             List<ThresholdBreach> criticalBreaches,
                             List<String> significantlyAboveWarning,
                             String metricName,
                             double value,
                             double warningThreshold,
                             double criticalThreshold) {
        if (value >= criticalThreshold) {
            criticalBreaches.add(new ThresholdBreach(metricName, value, warningThreshold, criticalThreshold));
            warningBreaches.add(new ThresholdBreach(metricName, value, warningThreshold, criticalThreshold));
            significantlyAboveWarning.add(metricName);
        } else if (value >= warningThreshold) {
            warningBreaches.add(new ThresholdBreach(metricName, value, warningThreshold, criticalThreshold));
            if (criticalThreshold > 0 && value >= criticalThreshold * 0.8) {
                significantlyAboveWarning.add(metricName);
            }
        }
    }

    private boolean isMetricCritical(String metricName, List<ThresholdBreach> criticalBreaches) {
        for (ThresholdBreach breach : criticalBreaches) {
            if (breach.metricName.equals(metricName)) return true;
        }
        return false;
    }

    private List<String> extractNames(List<ThresholdBreach> breaches) {
        List<String> names = new ArrayList<>(breaches.size());
        for (ThresholdBreach breach : breaches) {
            names.add(breach.metricName);
        }
        return names;
    }

    private static final class ThresholdBreach {
        final String metricName;
        final double value;
        final double warningThreshold;
        final double criticalThreshold;

        ThresholdBreach(String metricName, double value, double warningThreshold, double criticalThreshold) {
            this.metricName = metricName;
            this.value = value;
            this.warningThreshold = warningThreshold;
            this.criticalThreshold = criticalThreshold;
        }
    }

    public static final class DetectionThresholds {
        public double oreToStoneRatioWarning;
        public double oreToStoneRatioCritical;
        public double diamondToStoneRatioWarning;
        public double diamondToStoneRatioCritical;
        public double orePerHourWarning;
        public double orePerHourCritical;
        public double diamondPerHourWarning;
        public double diamondPerHourCritical;
        public double shortWindowOreRatioWarning;
        public double shortWindowOreRatioCritical;
        public double longWindowOreRatioWarning;
        public double longWindowOreRatioCritical;
        public double valuableOreRatioWarning;
        public double valuableOreRatioCritical;
        public double straightToOreRatioWarning;
        public double straightToOreRatioCritical;

        public static DetectionThresholds defaults() {
            DetectionThresholds t = new DetectionThresholds();
            t.oreToStoneRatioWarning = 0.08;
            t.oreToStoneRatioCritical = 0.15;
            t.diamondToStoneRatioWarning = 0.005;
            t.diamondToStoneRatioCritical = 0.01;
            t.orePerHourWarning = 120.0;
            t.orePerHourCritical = 200.0;
            t.diamondPerHourWarning = 5.0;
            t.diamondPerHourCritical = 10.0;
            t.shortWindowOreRatioWarning = 0.12;
            t.shortWindowOreRatioCritical = 0.25;
            t.longWindowOreRatioWarning = 0.10;
            t.longWindowOreRatioCritical = 0.20;
            t.valuableOreRatioWarning = 0.15;
            t.valuableOreRatioCritical = 0.30;
            t.straightToOreRatioWarning = 0.60;
            t.straightToOreRatioCritical = 0.80;
            return t;
        }

        public DetectionThresholds copy() {
            DetectionThresholds t = new DetectionThresholds();
            t.oreToStoneRatioWarning = this.oreToStoneRatioWarning;
            t.oreToStoneRatioCritical = this.oreToStoneRatioCritical;
            t.diamondToStoneRatioWarning = this.diamondToStoneRatioWarning;
            t.diamondToStoneRatioCritical = this.diamondToStoneRatioCritical;
            t.orePerHourWarning = this.orePerHourWarning;
            t.orePerHourCritical = this.orePerHourCritical;
            t.diamondPerHourWarning = this.diamondPerHourWarning;
            t.diamondPerHourCritical = this.diamondPerHourCritical;
            t.shortWindowOreRatioWarning = this.shortWindowOreRatioWarning;
            t.shortWindowOreRatioCritical = this.shortWindowOreRatioCritical;
            t.longWindowOreRatioWarning = this.longWindowOreRatioWarning;
            t.longWindowOreRatioCritical = this.longWindowOreRatioCritical;
            t.valuableOreRatioWarning = this.valuableOreRatioWarning;
            t.valuableOreRatioCritical = this.valuableOreRatioCritical;
            t.straightToOreRatioWarning = this.straightToOreRatioWarning;
            t.straightToOreRatioCritical = this.straightToOreRatioCritical;
            return t;
        }
    }
}
