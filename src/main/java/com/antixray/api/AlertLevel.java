package com.antixray.api;

/**
 * Represents the level of xray suspicion detected for a player.
 */
public enum AlertLevel {
    /**
     * Informational alert, low suspicion.
     */
    INFO,

    /**
     * Warning alert, moderate suspicion.
     */
    WARNING,

    /**
     * Critical alert, high suspicion.
     */
    CRITICAL
}
