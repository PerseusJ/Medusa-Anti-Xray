package com.antixray.config;

import com.antixray.engine.ObfuscationMode;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

public final class WorldConfig {

    private final boolean enabled;
    private final ObfuscationMode engineMode;
    private final Set<Integer> hiddenBlocks;
    private final int replacementOverworld;
    private final int replacementOverworldDeep;
    private final int deepslateBelowY;
    private final int replacementNether;
    private final int replacementEnd;
    private final int maxBlockHeight;
    private final double fakeOreChance;
    private final boolean lavaObscures;
    private final boolean leavesAreTransparent;
    private final String bypassPermission;
    private final int maxRevealedPerPlayer;
    private final double movementThreshold;
    private final int updateRadius;
    private final int maxDeobfuscationUpdatesPerTick;
    private final double elytraVelocityThreshold;
    private final int configHash;

	private WorldConfig(Builder builder) {
		this.enabled = builder.enabled;
		this.engineMode = builder.engineMode;
		this.hiddenBlocks = Collections.unmodifiableSet(builder.hiddenBlocks);
		this.replacementOverworld = builder.replacementOverworld;
		this.replacementOverworldDeep = builder.replacementOverworldDeep;
		this.deepslateBelowY = builder.deepslateBelowY;
		this.replacementNether = builder.replacementNether;
		this.replacementEnd = builder.replacementEnd;
		this.maxBlockHeight = builder.maxBlockHeight;
		this.fakeOreChance = builder.fakeOreChance;
		this.lavaObscures = builder.lavaObscures;
		this.leavesAreTransparent = builder.leavesAreTransparent;
        this.bypassPermission = builder.bypassPermission;
        this.maxRevealedPerPlayer = builder.maxRevealedPerPlayer;
		this.movementThreshold = builder.movementThreshold;
        this.updateRadius = builder.updateRadius;
        this.maxDeobfuscationUpdatesPerTick = builder.maxDeobfuscationUpdatesPerTick;
        this.elytraVelocityThreshold = builder.elytraVelocityThreshold;
        this.configHash = computeHash();
	}

	private int computeHash() {
		int hash = 1;
		hash = 31 * hash + (enabled ? 1 : 0);
		hash = 31 * hash + engineMode.hashCode();
		for (int id : hiddenBlocks) hash = 31 * hash + id;
		hash = 31 * hash + replacementOverworld;
		hash = 31 * hash + replacementOverworldDeep;
		hash = 31 * hash + deepslateBelowY;
		hash = 31 * hash + replacementNether;
		hash = 31 * hash + replacementEnd;
		hash = 31 * hash + maxBlockHeight;
		hash = 31 * hash + Double.hashCode(fakeOreChance);
		hash = 31 * hash + (lavaObscures ? 1 : 0);
		hash = 31 * hash + (leavesAreTransparent ? 1 : 0);
		hash = 31 * hash + bypassPermission.hashCode();
        hash = 31 * hash + maxRevealedPerPlayer;
		hash = 31 * hash + Double.hashCode(movementThreshold);
		hash = 31 * hash + updateRadius;
        hash = 31 * hash + maxDeobfuscationUpdatesPerTick;
        hash = 31 * hash + Double.hashCode(elytraVelocityThreshold);
        return hash;
	}

    public boolean isEnabled() { return enabled; }

    public ObfuscationMode getEngineMode() { return engineMode; }

    public Set<Integer> getHiddenBlocks() { return hiddenBlocks; }

    public int getReplacementOverworld() { return replacementOverworld; }

    public int getReplacementOverworldDeep() { return replacementOverworldDeep; }

    public int getDeepslateBelowY() { return deepslateBelowY; }

    public int getReplacementNether() { return replacementNether; }

    public int getReplacementEnd() { return replacementEnd; }

    public int getMaxBlockHeight() { return maxBlockHeight; }

    public double getFakeOreChance() { return fakeOreChance; }

    public boolean isLavaObscures() { return lavaObscures; }

    public boolean isLeavesAreTransparent() { return leavesAreTransparent; }

	public String getBypassPermission() { return bypassPermission; }

    public int getMaxRevealedPerPlayer() { return maxRevealedPerPlayer; }

    public double getMovementThreshold() { return movementThreshold; }

	public int getUpdateRadius() { return updateRadius; }

    public int getMaxDeobfuscationUpdatesPerTick() { return maxDeobfuscationUpdatesPerTick; }

    public double getElytraVelocityThreshold() { return elytraVelocityThreshold; }

    public int getConfigHash() { return configHash; }

    public int getReplacement(int y, String environment) {
        return switch (environment) {
            case "NETHER" -> replacementNether;
            case "THE_END" -> replacementEnd;
            default -> y < deepslateBelowY ? replacementOverworldDeep : replacementOverworld;
        };
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WorldConfig that)) return false;
        return configHash == that.configHash
                && enabled == that.enabled
                && engineMode == that.engineMode
                && hiddenBlocks.equals(that.hiddenBlocks)
                && replacementOverworld == that.replacementOverworld
                && replacementOverworldDeep == that.replacementOverworldDeep
                && deepslateBelowY == that.deepslateBelowY
                && replacementNether == that.replacementNether
                && replacementEnd == that.replacementEnd
                && maxBlockHeight == that.maxBlockHeight
                && Double.compare(that.fakeOreChance, fakeOreChance) == 0
                && lavaObscures == that.lavaObscures
                && leavesAreTransparent == that.leavesAreTransparent
            && bypassPermission.equals(that.bypassPermission)
            && maxRevealedPerPlayer == that.maxRevealedPerPlayer
	&& Double.compare(that.movementThreshold, movementThreshold) == 0
        && updateRadius == that.updateRadius
        && maxDeobfuscationUpdatesPerTick == that.maxDeobfuscationUpdatesPerTick
        && Double.compare(that.elytraVelocityThreshold, elytraVelocityThreshold) == 0;
    }

    @Override
    public int hashCode() { return configHash; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private boolean enabled = true;
        private ObfuscationMode engineMode = ObfuscationMode.MODE_3;
        private Set<Integer> hiddenBlocks = Set.of();
        private int replacementOverworld = 0;
        private int replacementOverworldDeep = 0;
        private int deepslateBelowY = 0;
        private int replacementNether = 0;
        private int replacementEnd = 0;
        private int maxBlockHeight = 64;
        private double fakeOreChance = 0.07;
        private boolean lavaObscures = true;
        private boolean leavesAreTransparent = true;
	private String bypassPermission = "antixray.bypass";
        private int maxRevealedPerPlayer = 10000;
        private double movementThreshold = 0.5;
		private int updateRadius = 4;
        private int maxDeobfuscationUpdatesPerTick = 64;
        private double elytraVelocityThreshold = 1.5;

        private Builder() {}

        public Builder enabled(boolean enabled) { this.enabled = enabled; return this; }

        public Builder engineMode(ObfuscationMode engineMode) { this.engineMode = engineMode; return this; }

        public Builder hiddenBlocks(Set<Integer> hiddenBlocks) {
            this.hiddenBlocks = Objects.requireNonNull(hiddenBlocks);
            return this;
        }

        public Builder replacementOverworld(int replacementOverworld) {
            this.replacementOverworld = replacementOverworld; return this;
        }

        public Builder replacementOverworldDeep(int replacementOverworldDeep) {
            this.replacementOverworldDeep = replacementOverworldDeep; return this;
        }

        public Builder deepslateBelowY(int deepslateBelowY) {
            this.deepslateBelowY = deepslateBelowY; return this;
        }

        public Builder replacementNether(int replacementNether) {
            this.replacementNether = replacementNether; return this;
        }

        public Builder replacementEnd(int replacementEnd) {
            this.replacementEnd = replacementEnd; return this;
        }

        public Builder maxBlockHeight(int maxBlockHeight) {
            this.maxBlockHeight = maxBlockHeight; return this;
        }

        public Builder fakeOreChance(double fakeOreChance) {
            this.fakeOreChance = fakeOreChance; return this;
        }

        public Builder lavaObscures(boolean lavaObscures) {
            this.lavaObscures = lavaObscures; return this;
        }

        public Builder leavesAreTransparent(boolean leavesAreTransparent) {
            this.leavesAreTransparent = leavesAreTransparent; return this;
        }

	public Builder bypassPermission(String bypassPermission) {
			this.bypassPermission = Objects.requireNonNull(bypassPermission);
			return this;
		}

        public Builder maxRevealedPerPlayer(int maxRevealedPerPlayer) {
            this.maxRevealedPerPlayer = maxRevealedPerPlayer;
            return this;
        }

        public Builder movementThreshold(double movementThreshold) {
            this.movementThreshold = movementThreshold;
            return this;
        }

		public Builder updateRadius(int updateRadius) {
			this.updateRadius = updateRadius;
			return this;
		}

        public Builder maxDeobfuscationUpdatesPerTick(int maxDeobfuscationUpdatesPerTick) {
            this.maxDeobfuscationUpdatesPerTick = maxDeobfuscationUpdatesPerTick;
            return this;
        }

        public Builder elytraVelocityThreshold(double elytraVelocityThreshold) {
            this.elytraVelocityThreshold = elytraVelocityThreshold;
            return this;
        }

        public WorldConfig build() { return new WorldConfig(this); }
    }
}
