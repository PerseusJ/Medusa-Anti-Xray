package com.antixray.util;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;

class FrustumDiagTest {
    private static double g(Frustum f, String n) {
        try { Field fd = Frustum.class.getDeclaredField(n); fd.setAccessible(true); return fd.getDouble(f); }
        catch (Exception e) { throw new RuntimeException("Field: " + n, e); }
    }
    
    @Test
    void diagRemaining() {
        Frustum f = new Frustum();
        
        // positivePitchBlockBelowIsNotVisible: pitch=90, eyeY=128, block (0,0,0)
        f.update(0, 128, 0, 0f, 90f, 70.0);
        System.out.println("=== pitch=90 eyeY=128 block(0,0,0) ===");
        System.out.printf("nTop=(%.3f,%.3f,%.3f) dTop=%.1f%n", g(f,"nxTop"), g(f,"nyTop"), g(f,"nzTop"), g(f,"dTop"));
        System.out.printf("nBot=(%.3f,%.3f,%.3f) dBot=%.1f%n", g(f,"nxBottom"), g(f,"nyBottom"), g(f,"nzBottom"), g(f,"dBottom"));
        double topVal = g(f,"nyTop")*0.5;
        double botVal = g(f,"nyBottom")*0.5;
        System.out.printf("  y=0.5: topDot=%.1f vs dTop=%.1f pass=%b | botDot=%.1f vs dBot=%.1f pass=%b%n",
            topVal, g(f,"dTop"), topVal >= g(f,"dTop"), botVal, g(f,"dBottom"), botVal >= g(f,"dBottom"));
        System.out.println("isVisible(0,0,0)=" + f.isVisible(0,0,0) + " expected=false");
        
        // wideFovIncludesMoreBlocks: FOV=170, yaw=0, pitch=0, block(5,64,1)
        f.update(0, 64, 0, 0f, 0f, 170.0);
        System.out.println("\n=== FOV=170 yaw=0 pitch=0 block(5,64,1) ===");
        System.out.printf("nTop=(%.6f,%.6f,%.6f) dTop=%.6f%n", g(f,"nxTop"), g(f,"nyTop"), g(f,"nzTop"), g(f,"dTop"));
        System.out.printf("nBot=(%.6f,%.6f,%.6f) dBot=%.6f%n", g(f,"nxBottom"), g(f,"nyBottom"), g(f,"nzBottom"), g(f,"dBottom"));
        double topV = g(f,"nxTop")*5.5 + g(f,"nyTop")*64.5 + g(f,"nzTop")*1.5;
        double botV = g(f,"nxBottom")*5.5 + g(f,"nyBottom")*64.5 + g(f,"nzBottom")*1.5;
        System.out.printf("  (5.5,64.5,1.5): topDot=%.3f vs dTop=%.3f pass=%b | botDot=%.3f vs dBot=%.3f pass=%b%n",
            topV, g(f,"dTop"), topV >= g(f,"dTop"), botV, g(f,"dBottom"), botV >= g(f,"dBottom"));
        System.out.println("isVisible(5,64,1)=" + f.isVisible(5,64,1) + " expected=true");
    }
}
