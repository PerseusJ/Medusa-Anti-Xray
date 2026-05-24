package com.antixray.detection;

import com.antixray.api.AlertLevel;
import java.util.Collections;
import java.util.List;

public final class DetectionResult {

    private final AlertLevel level;
    private final List<String> triggeredMetrics;
    private final boolean rateLimited;

    private static final DetectionResult NONE = new DetectionResult(null, Collections.emptyList(), false);

    private DetectionResult(AlertLevel level, List<String> triggeredMetrics, boolean rateLimited) {
        this.level = level;
        this.triggeredMetrics = triggeredMetrics != null ? Collections.unmodifiableList(triggeredMetrics) : Collections.emptyList();
        this.rateLimited = rateLimited;
    }

    public static DetectionResult none() {
        return NONE;
    }

    public static DetectionResult of(AlertLevel level, List<String> triggeredMetrics) {
        return new DetectionResult(level, triggeredMetrics, false);
    }

    public static DetectionResult rateLimited() {
        return new DetectionResult(null, Collections.emptyList(), true);
    }

    public boolean isDetected() {
        return level != null;
    }

    public AlertLevel getLevel() {
        return level;
    }

    public List<String> getTriggeredMetrics() {
        return triggeredMetrics;
    }

    public boolean isRateLimited() {
        return rateLimited;
    }

    @Override
    public String toString() {
        if (rateLimited) return "DetectionResult{rateLimited=true}";
        if (level == null) return "DetectionResult{none}";
        return "DetectionResult{level=" + level + ", metrics=" + triggeredMetrics + "}";
    }
}
