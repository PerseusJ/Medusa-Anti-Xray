package com.antixray.api;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Bukkit event fired when a block is deobfuscated for a player.
 */
public class BlockVisibilityEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Location location;
    private final Material realMaterial;
    private final Material obfuscatedMaterial;
    private boolean cancelled;

    public BlockVisibilityEvent(Player player, Location location, Material realMaterial, Material obfuscatedMaterial) {
        this.player = player;
        this.location = location;
        this.realMaterial = realMaterial;
        this.obfuscatedMaterial = obfuscatedMaterial;
    }

    public Player getPlayer() {
        return player;
    }

    public Location getLocation() {
        return location;
    }

    public Material getRealMaterial() {
        return realMaterial;
    }

    public Material getObfuscatedMaterial() {
        return obfuscatedMaterial;
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
