package com.antixray.util;

import org.junit.jupiter.api.Test;

/**
 * Diagnostic test that prints frustum internals to understand why isVisible fails.
 */
class FrustumDebugTest {

    @Test
    void debugLookingSouthForwardBlock() {
        Frustum f = new Frustum();
        // yaw=0 (south), pitch=0, FOV=70
        f.update(0, 64, 0, 0f, 0f, 70.0);

        System.out.println("=== yaw=0 (south), pitch=0, fov=70, eye=(0,64,0) ===");
        printFrustumState(f);

        // Test block at (0, 64, 10) - should be visible (in front)
        System.out.println("\nTest (0,64,10):");
        System.out.println("  isVisible(0,64,10) = " + f.isVisible(0, 64, 10));
        System.out.println("  isVisible(0.5,64.5,10.5) = " + f.isVisible(0.5, 64.5, 10.5));

        // Manual check with known correct values
        // For yaw=0: forward = (0,0,1), right = (1,0,0), up = (0,1,0)
        // With 70° FOV: halfFov=35°
        // Top plane normal should point roughly "up" but tilted slightly back
        System.out.println("\nExpected: forward=(0,0,1), right=(1,0,0), up=(0,1,0)");
    }

    @Test
    void debugLookingEast() {
        Frustum f = new Frustum();
        f.update(0, 64, 0, -90f, 0f, 70.0);

        System.out.println("\n=== yaw=-90 (east), pitch=0, fov=70, eye=(0,64,0) ===");
        printFrustumState(f);
        System.out.println("\nTest (10,64,0):");
        System.out.println("  isVisible(10,64,0) = " + f.isVisible(10, 64, 0));
    }

    @Test
    void debugPitch90() {
        Frustum f = new Frustum();
        f.update(0, 128, 0, 0f, 90f, 70.0);

        System.out.println("\n=== yaw=0, pitch=90 (looking down), fov=70, eye=(0,128,0) ===");
        printFrustumState(f);
        System.out.println("\nTest (0,64,0):");
        System.out.println("  isVisible(0,64,0) = " + f.isVisible(0, 64, 0));
    }

    @Test
    void debugManualComputation() {
        // Manual computation for yaw=0, pitch=0, FOV=70
        double fovDeg = 70.0;
        double halfFov = Math.toRadians(fovDeg * 0.5);
        double cosHalf = Math.cos(halfFov);
        double sinHalf = Math.sin(halfFov);

        double yawRad = Math.toRadians(0.0);
        double sinYaw = Math.sin(yawRad);   // 0
        double cosYaw = Math.cos(yawRad);   // 1

        double pitchRad = Math.toRadians(0.0);
        double sinPitch = Math.sin(pitchRad); // 0
        double cosPitch = Math.cos(pitchRad); // 1

        // Forward direction
        double fwdX = -sinYaw * cosPitch;  // 0
        double fwdY = sinPitch;            // 0
        double fwdZ = cosYaw * cosPitch;   // 1

        // Right direction (horizontal)
        double rightX = cosYaw;   // 1
        double rightZ = sinYaw;   // 0

        // Up = right × forward
        double upX = rightZ * fwdY - 0 * fwdZ;   // 0*0 - 0*1 = 0
        double upY = 0 * fwdX - rightX * fwdZ;    // 0*0 - 1*1 = -1
        double upZ = rightX * fwdY - rightZ * fwdX; // 1*0 - 0*0 = 0

        System.out.println("\n=== Manual computation for yaw=0, pitch=0, FOV=70 ===");
        System.out.printf("forward = (%.3f, %.3f, %.3f)%n", fwdX, fwdY, fwdZ);
        System.out.printf("right   = (%.3f, 0, %.3f)%n", rightX, rightZ);
        System.out.printf("up      = (%.3f, %.3f, %.3f)%n", upX, upY, upZ);
        System.out.println("PROBLEM: up=(0,-1,0) but should be (0,1,0)!");
        System.out.println("Right × Forward gives -up when right=(1,0,0) and forward=(0,0,1)");
        System.out.println("We need Forward × Right instead (or negate the result)");

        // Correct: up = forward × right
        double upX2 = fwdY * rightZ - fwdZ * 0;
        double upY2 = fwdZ * rightX - fwdX * rightZ;
        double upZ2 = fwdX * 0 - fwdY * rightX;
        System.out.printf("\nforward × right = (%.3f, %.3f, %.3f)%n", upX2, upY2, upZ2);
    }

    private void printFrustumState(Frustum f) {
        try {
            java.lang.reflect.Field nxTopF = Frustum.class.getDeclaredField("nxTop");
            java.lang.reflect.Field nyTopF = Frustum.class.getDeclaredField("nyTop");
            java.lang.reflect.Field nzTopF = Frustum.class.getDeclaredField("nzTop");
            java.lang.reflect.Field dTopF = Frustum.class.getDeclaredField("dTop");
            java.lang.reflect.Field nxBotF = Frustum.class.getDeclaredField("nxBottom");
            java.lang.reflect.Field nyBotF = Frustum.class.getDeclaredField("nyBottom");
            java.lang.reflect.Field nzBotF = Frustum.class.getDeclaredField("nzBottom");
            java.lang.reflect.Field dBotF = Frustum.class.getDeclaredField("dBottom");
            java.lang.reflect.Field fwdXZ_XF = Frustum.class.getDeclaredField("forwardXZ_X");
            java.lang.reflect.Field fwdXZ_ZF = Frustum.class.getDeclaredField("forwardXZ_Z");

            nxTopF.setAccessible(true); nyTopF.setAccessible(true); nzTopF.setAccessible(true); dTopF.setAccessible(true);
            nxBotF.setAccessible(true); nyBotF.setAccessible(true); nzBotF.setAccessible(true); dBotF.setAccessible(true);
            fwdXZ_XF.setAccessible(true); fwdXZ_ZF.setAccessible(true);

            System.out.printf("forwardXZ = (%.3f, %.3f)%n", fwdXZ_XF.getDouble(f), fwdXZ_ZF.getDouble(f));
            System.out.printf("top   normal = (%.6f, %.6f, %.6f) d=%.6f%n",
                nxTopF.getDouble(f), nyTopF.getDouble(f), nzTopF.getDouble(f), dTopF.getDouble(f));
            System.out.printf("bot   normal = (%.6f, %.6f, %.6f) d=%.6f%n",
                nxBotF.getDouble(f), nyBotF.getDouble(f), nzBotF.getDouble(f), dBotF.getDouble(f));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
