package com.antixray.util;

public final class Frustum {

    private double nxLeft, nzLeft, dLeft;
    private double nxRight, nzRight, dRight;

    private double nxTop, nyTop, nzTop, dTop;
    private double nxBottom, nyBottom, nzBottom, dBottom;

    private double forwardXZ_X, forwardXZ_Z;
    private double eyeX, eyeY, eyeZ;
    private double halfFovCos;
    private boolean enabled;
    private double sinPitch; // stored for extreme-pitch cone check
    private boolean lrActive;
    private boolean lrAngular;

    public Frustum() {
        this.enabled = false;
    }

    public void update(double eyeX, double eyeY, double eyeZ,
                       float yaw, float pitch,
                       double fovDegrees) {

        this.eyeX = eyeX;
        this.eyeY = eyeY;
        this.eyeZ = eyeZ;

        double halfFov = Math.toRadians(fovDegrees * 0.5);
        halfFovCos = Math.cos(halfFov);

        double yawRad = Math.toRadians(yaw);
        double sinYaw = Math.sin(yawRad);
        double cosYaw = Math.cos(yawRad);

        double pitchRad = Math.toRadians(pitch);
        double sinPitch = Math.sin(pitchRad);
        double cosPitch = Math.cos(pitchRad);
        this.sinPitch = sinPitch;

        forwardXZ_X = -sinYaw;
        forwardXZ_Z = cosYaw;
        double rightXZ_X = cosYaw;
        double rightXZ_Z = sinYaw;

        double cosHalf = halfFovCos;
        double sinHalf = Math.sin(halfFov);

        if (Math.abs(cosPitch) < 1e-6) {
            lrActive = false;
            lrAngular = false;
        } else {
            lrActive = true;
            lrAngular = (halfFovCos < 0.999);

            double leftEdgeX = forwardXZ_X * cosHalf - rightXZ_X * sinHalf;
            double leftEdgeZ = forwardXZ_Z * cosHalf - rightXZ_Z * sinHalf;
            double invLenL = 1.0 / Math.sqrt(leftEdgeX * leftEdgeX + leftEdgeZ * leftEdgeZ);
            nxLeft = leftEdgeX * invLenL;
            nzLeft = leftEdgeZ * invLenL;
            dLeft = nxLeft * eyeX + nzLeft * eyeZ;

            double rightEdgeX = forwardXZ_X * cosHalf + rightXZ_X * sinHalf;
            double rightEdgeZ = forwardXZ_Z * cosHalf + rightXZ_Z * sinHalf;
            double invLenR = 1.0 / Math.sqrt(rightEdgeX * rightEdgeX + rightEdgeZ * rightEdgeZ);
            nxRight = rightEdgeX * invLenR;
            nzRight = rightEdgeZ * invLenR;
            dRight = nxRight * eyeX + nzRight * eyeZ;
        }

        // --- Top / Bottom planes ---
        if (Math.abs(cosPitch) < 1e-6) {
            // Extreme pitch (±90°): frustum is a cone around the vertical axis.
            // Set top/bottom planes as horizontal caps at eyeY, which will be
            // supplemented by the vertical cone check in isVisible().
            nyTop = 1.0;
            nxTop = 0.0;
            nzTop = 0.0;
            dTop = eyeY;

            nyBottom = -1.0;
            nxBottom = 0.0;
            nzBottom = 0.0;
            dBottom = eyeY;
        } else {
            // 3D forward vector
            double fwdX = -sinYaw * cosPitch;
            double fwdY = sinPitch;
            double fwdZ = cosYaw * cosPitch;

            // 3D right vector (horizontal right, zero Y component)
            double rX = rightXZ_X;
            double rZ = rightXZ_Z;

        // Up = Forward × Right (right-handed system, correct upward direction)
        double upX = fwdY * rZ - fwdZ * 0.0;
        double upY = fwdZ * rX - fwdX * rZ;
        double upZ = fwdX * 0.0 - fwdY * rX;

        // Normalize up vector for numerical stability
        double upLen = Math.sqrt(upX * upX + upY * upY + upZ * upZ);
        upX /= upLen; upY /= upLen; upZ /= upLen;

        // Top edge: rotate forward toward up by half-FOV
        double topEdgeX = fwdX * cosHalf + upX * sinHalf;
        double topEdgeY = fwdY * cosHalf + upY * sinHalf;
        double topEdgeZ = fwdZ * cosHalf + upZ * sinHalf;
        // Top plane normal = Right × TopEdge (points inward, below the top plane)
        double topNX = 0.0 * topEdgeZ - rZ * topEdgeY;
        double topNY = rZ * topEdgeX - rX * topEdgeZ;
        double topNZ = rX * topEdgeY - 0.0 * topEdgeX;
        double invLenT = 1.0 / Math.sqrt(topNX * topNX + topNY * topNY + topNZ * topNZ);
        nxTop = topNX * invLenT;
        nyTop = topNY * invLenT;
        nzTop = topNZ * invLenT;
        dTop = nxTop * eyeX + nyTop * eyeY + nzTop * eyeZ;

        // Bottom edge: rotate forward toward -up by half-FOV
        double bottomEdgeX = fwdX * cosHalf - upX * sinHalf;
        double bottomEdgeY = fwdY * cosHalf - upY * sinHalf;
        double bottomEdgeZ = fwdZ * cosHalf - upZ * sinHalf;
        // Bottom plane normal = BottomEdge × Right (points inward, above the bottom plane)
        // A × B = (A_y*B_z - A_z*B_y, A_z*B_x - A_x*B_z, A_x*B_y - A_y*B_x)
        // Here A = BottomEdge, B = Right = (rX, 0, rZ)
        double bottomNX = bottomEdgeY * rZ - bottomEdgeZ * 0.0;
        double bottomNY = bottomEdgeZ * rX - bottomEdgeX * rZ;
        double bottomNZ = bottomEdgeX * 0.0 - bottomEdgeY * rX;
        double invLenB = 1.0 / Math.sqrt(bottomNX * bottomNX + bottomNY * bottomNY + bottomNZ * bottomNZ);
        nxBottom = bottomNX * invLenB;
        nyBottom = bottomNY * invLenB;
        nzBottom = bottomNZ * invLenB;
        dBottom = nxBottom * eyeX + nyBottom * eyeY + nzBottom * eyeZ;
        }

        this.enabled = true;
    }

    private boolean isInHorizontalFov(double px, double pz) {
        double dx = px - eyeX;
        double dz = pz - eyeZ;
        double lenSq = dx * dx + dz * dz;
        if (lenSq < 1e-10) return true;
        double cosAngle = (dx * forwardXZ_X + dz * forwardXZ_Z) / Math.sqrt(lenSq);
        return cosAngle >= halfFovCos - 1e-10;
    }

    public boolean isVisible(double x, double y, double z) {
        if (!enabled) return true;

        if (lrActive && lrAngular) {
            if (!isInHorizontalFov(x, z)) return false;
        }

        // For extreme pitch (±90°), the frustum is a cone around the vertical axis.
        // The horizontal plane checks alone are insufficient. Apply a vertical
        // cone check: the angle from the look direction (up or down) must be
        // within half-FOV.
        if (!lrActive) {
            double dy = y - eyeY;
            double dx = x - eyeX;
            double dz = z - eyeZ;
            double lenSq = dx * dx + dy * dy + dz * dz;
            if (lenSq < 1e-10) return true;
            // cosAngle from vertical axis, signed with look direction
            double cosAngle = -dy * sinPitch / Math.sqrt(lenSq);
            if (cosAngle < halfFovCos - 1e-10) return false;
            // Also ensure block is on the correct side of the eye
            // (looking down → dy < 0, looking up → dy > 0)
            if (sinPitch > 0 && dy > 0) return false;  // looking down but block is above
            if (sinPitch < 0 && dy < 0) return false;  // looking up but block is below
            return true;  // cone check is sufficient for extreme pitch
        }

        if (nxTop * x + nyTop * y + nzTop * z < dTop) return false;
        if (nxBottom * x + nyBottom * y + nzBottom * z < dBottom) return false;

        return true;
    }

    public boolean isVisible(int blockX, int blockY, int blockZ) {
        return isVisible(blockX + 0.5, blockY + 0.5, blockZ + 0.5);
    }

    public boolean isAabbVisible(double minX, double minY, double minZ,
                                    double maxX, double maxY, double maxZ) {
        if (!enabled) return true;

        if (lrActive && lrAngular) {
            double centerX = (minX + maxX) * 0.5;
            double centerZ = (minZ + maxZ) * 0.5;
            if (!isInHorizontalFov(centerX, centerZ)) return false;
        }

        // For extreme pitch (±90°), apply the vertical cone check
        // using the AABB corner closest to the eye.
        if (!lrActive) {
            double closestX = (eyeX >= minX && eyeX <= maxX) ? eyeX : (eyeX < minX ? minX : maxX);
            double closestY = (eyeY >= minY && eyeY <= maxY) ? eyeY : (eyeY < minY ? minY : maxY);
            double closestZ = (eyeZ >= minZ && eyeZ <= maxZ) ? eyeZ : (eyeZ < minZ ? minZ : maxZ);

            double dy = closestY - eyeY;
            double dx = closestX - eyeX;
            double dz = closestZ - eyeZ;
            double lenSq = dx * dx + dy * dy + dz * dz;
            if (lenSq < 1e-10) return true;
            double cosAngle = -dy * sinPitch / Math.sqrt(lenSq);
            if (cosAngle < halfFovCos - 1e-10) return false;
            if (sinPitch > 0 && dy > 0) return false;
            if (sinPitch < 0 && dy < 0) return false;
            return true;
        }

        // Use n-vertex (most in direction of normal) for optimistic test:
        // if even the most favorable corner fails a plane, the AABB is fully outside.
        double px, py, pz;
        px = nxTop >= 0 ? maxX : minX;
        py = nyTop >= 0 ? maxY : minY;
        pz = nzTop >= 0 ? maxZ : minZ;
        if (nxTop * px + nyTop * py + nzTop * pz < dTop) return false;

        px = nxBottom >= 0 ? maxX : minX;
        py = nyBottom >= 0 ? maxY : minY;
        pz = nzBottom >= 0 ? maxZ : minZ;
        if (nxBottom * px + nyBottom * py + nzBottom * pz < dBottom) return false;

        return true;
    }

    public boolean isChunkVisible(int chunkX, int minY, int chunkZ) {
        double minX = chunkX << 4;
        double maxX = minX + 15.0;
        double minZ = chunkZ << 4;
        double maxZ = minZ + 15.0;
        return isAabbVisible(minX, minY, minZ, maxX, minY + 15.0, maxZ);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    boolean isLrActive() {
        return lrActive;
    }
}
