package com.antixray.deobfuscation;

import org.bukkit.Location;
import com.antixray.detection.PlayerStatistics;

public class PlayerData {

    private static final int ELYTRA_DECAY_TICKS = 40;
    private static final double ELYTRA_RADIUS_MULTIPLIER = 1.5;

    private final RevealedBlocksSet revealedBlocks;
    private volatile Location lastCheckedPosition;
    private volatile long lastCheckTick;
    private volatile int elytraLowVelocityTicks;
    private volatile boolean elytraExpanded;
    private volatile int pendingJoinTaskId;
    private final PlayerStatistics statistics;
    private final long joinTimeMillis;
    private volatile boolean resourcePackPending;
    private volatile int resourcePackTimeoutTaskId;

    public PlayerData(int maxRevealedPerPlayer) {
        this.revealedBlocks = new RevealedBlocksSet(maxRevealedPerPlayer);
        this.lastCheckedPosition = null;
        this.lastCheckTick = 0;
        this.elytraLowVelocityTicks = 0;
        this.elytraExpanded = false;
        this.pendingJoinTaskId = -1;
        this.statistics = new PlayerStatistics();
        this.joinTimeMillis = System.currentTimeMillis();
        this.resourcePackPending = false;
        this.resourcePackTimeoutTaskId = -1;
    }

    public RevealedBlocksSet getRevealedBlocks() {
        return revealedBlocks;
    }

    public Location getLastCheckedPosition() {
        return lastCheckedPosition;
    }

    public void setLastCheckedPosition(Location lastCheckedPosition) {
        this.lastCheckedPosition = lastCheckedPosition;
    }

    public long getLastCheckTick() {
        return lastCheckTick;
    }

    public void setLastCheckTick(long lastCheckTick) {
        this.lastCheckTick = lastCheckTick;
    }

    public int getPendingJoinTaskId() {
        return pendingJoinTaskId;
    }

    public void setPendingJoinTaskId(int taskId) {
        this.pendingJoinTaskId = taskId;
    }

    public void updateElytraState(boolean aboveThreshold) {
        if (aboveThreshold) {
            elytraLowVelocityTicks = 0;
            elytraExpanded = true;
        } else if (elytraExpanded) {
            elytraLowVelocityTicks++;
            if (elytraLowVelocityTicks >= ELYTRA_DECAY_TICKS) {
                elytraExpanded = false;
                elytraLowVelocityTicks = 0;
            }
        }
    }

    public boolean isElytraExpanded() {
        return elytraExpanded;
    }

    public double applyElytraRadius(double baseRadius) {
        return elytraExpanded ? baseRadius * ELYTRA_RADIUS_MULTIPLIER : baseRadius;
    }

    public int applyElytraRadius(int baseRadius) {
        return elytraExpanded ? (int) Math.ceil(baseRadius * ELYTRA_RADIUS_MULTIPLIER) : baseRadius;
    }

    public void clear() {
        revealedBlocks.clear();
        lastCheckedPosition = null;
        lastCheckTick = 0;
        statistics.reset();
    }

    public void fullReset() {
        clear();
        elytraLowVelocityTicks = 0;
        elytraExpanded = false;
        pendingJoinTaskId = -1;
        resourcePackPending = false;
        resourcePackTimeoutTaskId = -1;
    }

    public PlayerStatistics getStatistics() {
        return statistics;
    }

    public long getJoinTimeMillis() {
        return joinTimeMillis;
    }

    public boolean isResourcePackPending() {
        return resourcePackPending;
    }

    public void setResourcePackPending(boolean pending) {
        this.resourcePackPending = pending;
    }

    public int getResourcePackTimeoutTaskId() {
        return resourcePackTimeoutTaskId;
    }

    public void setResourcePackTimeoutTaskId(int taskId) {
        this.resourcePackTimeoutTaskId = taskId;
    }
}
