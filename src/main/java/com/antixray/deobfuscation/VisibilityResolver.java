package com.antixray.deobfuscation;

import com.antixray.nms.NmsAdapter;
import com.antixray.util.Frustum;
import com.antixray.util.MaterialSet;
import com.antixray.util.Raycast;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

public class VisibilityResolver {

    private final NmsAdapter nmsAdapter;
    private final MaterialSet materialSet;
    private final Frustum frustum;
    private final int raycastMaxSteps;
    private final int raycastPadding;
    private final boolean frustumCullingEnabled;
    private final boolean raycastEnabled;

    public VisibilityResolver(NmsAdapter nmsAdapter, MaterialSet materialSet,
                               boolean frustumCullingEnabled, boolean raycastEnabled,
                               int raycastMaxSteps, int raycastPadding) {
        this.nmsAdapter = nmsAdapter;
        this.materialSet = materialSet;
        this.frustumCullingEnabled = frustumCullingEnabled;
        this.raycastEnabled = raycastEnabled;
        this.raycastMaxSteps = raycastMaxSteps;
        this.raycastPadding = raycastPadding;
        this.frustum = new Frustum();
    }

    public void updateFrustum(Player player, double fov) {
        if (!frustumCullingEnabled) {
            frustum.setEnabled(false);
            return;
        }
        Location eye = player.getEyeLocation();
        float yaw = eye.getYaw();
        float pitch = eye.getPitch();
        frustum.update(eye.getX(), eye.getY(), eye.getZ(), yaw, pitch, fov);
    }

    public boolean isVisible(Player player, int blockX, int blockY, int blockZ) {
        if (frustumCullingEnabled && !frustum.isVisible(blockX, blockY, blockZ)) {
            return false;
        }

        if (raycastEnabled) {
            return checkLineOfSight(player, blockX, blockY, blockZ);
        }

        return true;
    }

    public boolean isVisible(int blockX, int blockY, int blockZ) {
        if (frustumCullingEnabled && !frustum.isVisible(blockX, blockY, blockZ)) {
            return false;
        }
        return true;
    }

    private boolean checkLineOfSight(Player player, int blockX, int blockY, int blockZ) {
        Location eye = player.getEyeLocation();
        World world = player.getWorld();

        int maxSteps = raycastMaxSteps > 0
                ? raycastMaxSteps
                : Raycast.computeMaxSteps(eye.getX(), eye.getY(), eye.getZ(),
                blockX, blockY, blockZ, raycastPadding);

        return Raycast.hasLineOfSight(world,
                eye.getX(), eye.getY(), eye.getZ(),
                blockX, blockY, blockZ,
                maxSteps, nmsAdapter, materialSet);
    }

    public boolean isFrustumCullingEnabled() {
        return frustumCullingEnabled;
    }

    public boolean isRaycastEnabled() {
        return raycastEnabled;
    }

    public Frustum getFrustum() {
        return frustum;
    }
}
