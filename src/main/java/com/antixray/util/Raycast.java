package com.antixray.util;

import com.antixray.nms.NmsAdapter;
import org.bukkit.World;

public final class Raycast {

    private Raycast() {}

    @FunctionalInterface
    public interface VoxelPredicate {
        boolean test(World world, int x, int y, int z);
    }

    /**
     * Casts a ray from the given eye position to the target block center using DDA
     * (Amanatides &amp; Woo) voxel traversal.
     * <p>
     * Returns {@code true} if the ray reaches the target block without hitting
     * an opaque voxel; {@code false} if an opaque voxel is hit along the way
     * or if {@code maxSteps} is exhausted before reaching the target.
     * The conservative "return false on max-steps-exceeded" policy prevents
     * revealing blocks that cannot be confirmed visible within the step budget.
     *
     * @param world     the world to test voxels in
     * @param eyeX      eye origin X
     * @param eyeY      eye origin Y
     * @param eyeZ      eye origin Z
     * @param targetX   target block X coordinate
     * @param targetY   target block Y coordinate
     * @param targetZ   target block Z coordinate
     * @param maxSteps  maximum number of voxel traversal steps before aborting
     * @param isOpaque  predicate that returns true if a voxel is opaque
     * @return true if the target is reachable (line of sight), false if occluded
     *         or step budget exceeded
     */

    public static boolean hasLineOfSight(World world,
                                          double eyeX, double eyeY, double eyeZ,
                                          int targetX, int targetY, int targetZ,
                                          int maxSteps,
                                          VoxelPredicate isOpaque) {

        int currentVoxelX = floorInt(eyeX);
        int currentVoxelY = floorInt(eyeY);
        int currentVoxelZ = floorInt(eyeZ);

        double dirX = targetX + 0.5 - eyeX;
        double dirY = targetY + 0.5 - eyeY;
        double dirZ = targetZ + 0.5 - eyeZ;

        if (dirX == 0.0 && dirY == 0.0 && dirZ == 0.0) {
            return true;
        }

        double tMaxX, tMaxY, tMaxZ;
        double tDeltaX, tDeltaY, tDeltaZ;
        int stepX, stepY, stepZ;

        if (dirX >= 0.0) {
            stepX = 1;
            tDeltaX = 1.0 / dirX;
            tMaxX = (currentVoxelX + 1.0 - eyeX) * tDeltaX;
        } else {
            stepX = -1;
            tDeltaX = 1.0 / -dirX;
            tMaxX = (eyeX - currentVoxelX) * tDeltaX;
        }

        if (dirY >= 0.0) {
            stepY = 1;
            tDeltaY = 1.0 / dirY;
            tMaxY = (currentVoxelY + 1.0 - eyeY) * tDeltaY;
        } else {
            stepY = -1;
            tDeltaY = 1.0 / -dirY;
            tMaxY = (eyeY - currentVoxelY) * tDeltaY;
        }

        if (dirZ >= 0.0) {
            stepZ = 1;
            tDeltaZ = 1.0 / dirZ;
            tMaxZ = (currentVoxelZ + 1.0 - eyeZ) * tDeltaZ;
        } else {
            stepZ = -1;
            tDeltaZ = 1.0 / -dirZ;
            tMaxZ = (eyeZ - currentVoxelZ) * tDeltaZ;
        }

        if (currentVoxelX == targetX && currentVoxelY == targetY && currentVoxelZ == targetZ) {
            return true;
        }

        if (isOpaque.test(world, currentVoxelX, currentVoxelY, currentVoxelZ)) {
            return false;
        }

        for (int i = 0; i < maxSteps; i++) {
            if (tMaxX < tMaxY) {
                if (tMaxX < tMaxZ) {
                    currentVoxelX += stepX;
                    tMaxX += tDeltaX;
                } else {
                    currentVoxelZ += stepZ;
                    tMaxZ += tDeltaZ;
                }
            } else {
                if (tMaxY < tMaxZ) {
                    currentVoxelY += stepY;
                    tMaxY += tDeltaY;
                } else {
                    currentVoxelZ += stepZ;
                    tMaxZ += tDeltaZ;
                }
            }

            if (currentVoxelX == targetX && currentVoxelY == targetY && currentVoxelZ == targetZ) {
                return true;
            }

            if (isOpaque.test(world, currentVoxelX, currentVoxelY, currentVoxelZ)) {
                return false;
            }
        }

        return false;
    }

    public static boolean hasLineOfSight(World world,
                                          double eyeX, double eyeY, double eyeZ,
                                          int targetX, int targetY, int targetZ,
                                          int maxSteps,
                                          NmsAdapter adapter,
                                          MaterialSet materialSet) {

        VoxelPredicate isOpaque = (w, x, y, z) -> {
            int blockStateId = adapter.getBlockStateAt(w, x, y, z);
            if (blockStateId == -1) return false;
            return !materialSet.isTransparent(blockStateId);
        };

        return hasLineOfSight(world, eyeX, eyeY, eyeZ,
                targetX, targetY, targetZ, maxSteps, isOpaque);
    }

    public static int computeMaxSteps(double eyeX, double eyeY, double eyeZ,
                                       int targetX, int targetY, int targetZ,
                                       int extraPadding) {
        int voxelX = floorInt(eyeX);
        int voxelY = floorInt(eyeY);
        int voxelZ = floorInt(eyeZ);
        int manhattan = Math.abs(targetX - voxelX) + Math.abs(targetY - voxelY) + Math.abs(targetZ - voxelZ);
        return manhattan + extraPadding;
    }

    private static int floorInt(double value) {
        int i = (int) value;
        return value < i ? i - 1 : i;
    }
}
