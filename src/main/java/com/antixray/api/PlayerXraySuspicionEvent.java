package com.antixray.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Map;

/**
 * Bukkit event fired when the detection module flags a player for xray suspicion.
 */
public class PlayerXraySuspicionEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final AlertLevel alertLevel;
    private final Map<String, Double> triggeringMetrics;
    private boolean cancelled;

    public PlayerXraySuspicionEvent(Player player, AlertLevel alertLevel, Map<String, Double> triggeringMetrics) {
        this.player = player;
        this.alertLevel = alertLevel;
        this.triggeringMetrics = triggeringMetrics;
    }

    public Player getPlayer() {
        return player;
    }

    public AlertLevel getAlertLevel() {
        return alertLevel;
    }

    public Map<String, Double> getTriggeringMetrics() {
        return triggeringMetrics;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
