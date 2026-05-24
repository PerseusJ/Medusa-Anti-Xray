package com.antixray.detection;

public enum PlayStyleClassifier {

    CAVING(1.3),
    BRANCH_MINING(1.0);

    private static final double CAVING_THRESHOLD = 0.60;
    private static final int RECLASSIFY_INTERVAL = 100;

    private final double thresholdMultiplier;

    PlayStyleClassifier(double thresholdMultiplier) {
        this.thresholdMultiplier = thresholdMultiplier;
    }

    public double getThresholdMultiplier() {
        return thresholdMultiplier;
    }

    public static PlayStyleClassifier classify(double airAdjacentRatio) {
        return airAdjacentRatio > CAVING_THRESHOLD ? CAVING : BRANCH_MINING;
    }

    public static PlayStyleClassifier classify(PlayerStatistics statistics) {
        return classify(statistics.getAirAdjacentRatio());
    }

    public static boolean shouldReclassify(long totalMined, long lastClassifiedAt) {
        return totalMined - lastClassifiedAt >= RECLASSIFY_INTERVAL;
    }

    public static int getReclassifyInterval() {
        return RECLASSIFY_INTERVAL;
    }

    public static double getCavingThreshold() {
        return CAVING_THRESHOLD;
    }
}
