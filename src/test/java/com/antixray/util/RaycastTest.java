package com.antixray.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RaycastTest {

    private static final Raycast.VoxelPredicate NEVER_OPAQUE = (w, x, y, z) -> false;
    private static final Raycast.VoxelPredicate ALWAYS_OPAQUE = (w, x, y, z) -> true;

    @Test
    void sameBlockReturnsTrue() {
        assertTrue(Raycast.hasLineOfSight(null, 5.5, 64.5, 10.5, 5, 64, 10, 10, NEVER_OPAQUE));
    }

    @Test
    void adjacentBlockUnoccludedReturnsTrue() {
        assertTrue(Raycast.hasLineOfSight(null, 5.5, 64.5, 10.5, 6, 64, 10, 10, NEVER_OPAQUE));
    }

    @Test
    void adjacentBlockOccludedReturnsFalse() {
        assertFalse(Raycast.hasLineOfSight(null, 5.5, 64.5, 10.5, 6, 64, 10, 10, ALWAYS_OPAQUE));
    }

    @Test
    void clearPathReturnsTrue() {
        assertTrue(Raycast.hasLineOfSight(null, 0.5, 64.5, 0.5, 5, 64, 0, 20, NEVER_OPAQUE));
    }

    @Test
    void pathBlockedByOpaqueReturnsFalse() {
        Raycast.VoxelPredicate blockAt2 = (w, x, y, z) -> x == 2 && y == 64 && z == 0;
        assertFalse(Raycast.hasLineOfSight(null, 0.5, 64.5, 0.5, 5, 64, 0, 20, blockAt2));
    }

    @Test
    void pathNotBlockedByTransparentAt2() {
        Raycast.VoxelPredicate transparentAt2 = (w, x, y, z) -> false;
        assertTrue(Raycast.hasLineOfSight(null, 0.5, 64.5, 0.5, 5, 64, 0, 20, transparentAt2));
    }

    @Test
    void maxStepsExceededReturnsFalseConservative() {
        assertFalse(Raycast.hasLineOfSight(null, 0.5, 64.5, 0.5, 100, 64, 0, 3, NEVER_OPAQUE));
    }

    @Test
    void sufficientMaxStepsReturnsTrue() {
        assertTrue(Raycast.hasLineOfSight(null, 0.5, 64.5, 0.5, 5, 64, 0, 20, NEVER_OPAQUE));
    }

    @Test
    void diagonalPathUnoccluded() {
        assertTrue(Raycast.hasLineOfSight(null, 0.5, 64.5, 0.5, 3, 64, 3, 20, NEVER_OPAQUE));
    }

    @Test
    void diagonalPathOccluded() {
        Raycast.VoxelPredicate blockAt1_64_1 = (w, x, y, z) -> x == 1 && y == 64 && z == 1;
        assertFalse(Raycast.hasLineOfSight(null, 0.5, 64.5, 0.5, 3, 64, 3, 20, blockAt1_64_1));
    }

    @Test
    void negativeDirectionClearPath() {
        assertTrue(Raycast.hasLineOfSight(null, 10.5, 64.5, 10.5, 5, 64, 5, 20, NEVER_OPAQUE));
    }

    @Test
    void verticalPathUpward() {
        assertTrue(Raycast.hasLineOfSight(null, 5.5, 60.5, 5.5, 5, 65, 5, 20, NEVER_OPAQUE));
    }

    @Test
    void verticalPathDownward() {
        assertTrue(Raycast.hasLineOfSight(null, 5.5, 70.5, 5.5, 5, 65, 5, 20, NEVER_OPAQUE));
    }

    @Test
    void verticalPathBlocked() {
        Raycast.VoxelPredicate blockAt65 = (w, x, y, z) -> x == 5 && y == 65 && z == 5;
        assertFalse(Raycast.hasLineOfSight(null, 5.5, 70.5, 5.5, 5, 60, 5, 20, blockAt65));
    }

    @Test
    void zeroMaxStepsTargetAdjacentReturnsFalse() {
        assertFalse(Raycast.hasLineOfSight(null, 5.5, 64.5, 10.5, 6, 64, 10, 0, NEVER_OPAQUE));
    }

    @Test
    void zeroMaxStepsSameBlockReturnsTrue() {
        assertTrue(Raycast.hasLineOfSight(null, 5.5, 64.5, 10.5, 5, 64, 10, 0, NEVER_OPAQUE));
    }

    @Test
    void computeMaxStepsReturnsPositiveValue() {
        int steps = Raycast.computeMaxSteps(0, 64, 0, 10, 64, 10, 2);
        assertTrue(steps > 0);
    }

    @Test
    void computeMaxStepsMatchesDistance() {
        int steps = Raycast.computeMaxSteps(0, 0, 0, 5, 0, 0, 0);
        assertEquals(5, steps);
    }

    @Test
    void computeMaxStepsWithPadding() {
        int steps = Raycast.computeMaxSteps(0, 0, 0, 5, 0, 0, 3);
        assertEquals(8, steps);
    }

    @Test
    void startingInOpaqueBlockReturnsFalse() {
        Raycast.VoxelPredicate opaqueAtOrigin = (w, x, y, z) -> x == 0 && y == 64 && z == 0;
        assertFalse(Raycast.hasLineOfSight(null, 0.5, 64.5, 0.5, 5, 64, 0, 20, opaqueAtOrigin));
    }

    @Test
    void targetAdjacentToOriginUnoccluded() {
        assertTrue(Raycast.hasLineOfSight(null, 0.5, 64.5, 0.5, 1, 64, 0, 5, NEVER_OPAQUE));
    }

    @Test
    void targetAdjacentNegativeDirection() {
        assertTrue(Raycast.hasLineOfSight(null, 5.5, 64.5, 5.5, 4, 64, 5, 5, NEVER_OPAQUE));
    }

    @Test
    void longDistancePathWithinSteps() {
        assertTrue(Raycast.hasLineOfSight(null, 0.5, 64.5, 0.5, 50, 64, 0, 100, NEVER_OPAQUE));
    }

    @Test
    void floorIntBehaviorForNegativeValues() {
        assertTrue(Raycast.hasLineOfSight(null, -0.1, 64.5, 0.5, -1, 64, 0, 5, NEVER_OPAQUE));
    }
}
