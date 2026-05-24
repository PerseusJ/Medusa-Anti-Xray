package com.antixray.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FrustumTest {

    private static final double FOV = 70.0;
    private static final double EPSILON = 0.001;

    private Frustum frustum;

    @BeforeEach
    void setUp() {
        frustum = new Frustum();
    }

    @Test
    void disabledByDefault() {
        assertFalse(frustum.isEnabled());
    }

    @Test
    void disabledFrustumReturnsAlwaysVisible() {
        assertTrue(frustum.isVisible(0, 0, 0));
        assertTrue(frustum.isVisible(1000, 1000, 1000));
        assertTrue(frustum.isVisible(-1000, -1000, -1000));
    }

    @Test
    void setEnabledChangesState() {
        frustum.setEnabled(true);
        assertTrue(frustum.isEnabled());
        frustum.setEnabled(false);
        assertFalse(frustum.isEnabled());
    }

    @Test
    void updateEnablesFrustum() {
        frustum.update(0, 64, 0, 0f, 0f, FOV);
        assertTrue(frustum.isEnabled());
    }

    @Test
    void lookingSouthForwardBlockIsVisible() {
        frustum.update(0, 64, 0, 0f, 0f, FOV);
        assertTrue(frustum.isVisible(0, 64, 10));
    }

    @Test
    void lookingSouthBlockBehindIsNotVisible() {
        frustum.update(0, 64, 0, 0f, 0f, FOV);
        assertFalse(frustum.isVisible(0, 64, -10));
    }

    @Test
    void lookingNorthForwardBlockIsVisible() {
        frustum.update(0, 64, 0, 180f, 0f, FOV);
        assertTrue(frustum.isVisible(0, 64, -10));
    }

    @Test
    void lookingNorthBlockBehindIsNotVisible() {
        frustum.update(0, 64, 0, 180f, 0f, FOV);
        assertFalse(frustum.isVisible(0, 64, 10));
    }

    @Test
    void lookingEastForwardBlockIsVisible() {
        frustum.update(0, 64, 0, -90f, 0f, FOV);
        assertTrue(frustum.isVisible(10, 64, 0));
    }

    @Test
    void lookingWestForwardBlockIsVisible() {
        frustum.update(0, 64, 0, 90f, 0f, FOV);
        assertTrue(frustum.isVisible(-10, 64, 0));
    }

    @Test
    void lookingSouthSideBlockWithinFovIsVisible() {
        frustum.update(0, 64, 0, 0f, 0f, FOV);
        assertTrue(frustum.isVisible(5, 64, 10));
    }

    @Test
    void lookingSouthSideBlockOutsideFovIsNotVisible() {
        frustum.update(0, 64, 0, 0f, 0f, FOV);
        assertFalse(frustum.isVisible(100, 64, 1));
    }

    @Test
    void zeroPitchBlockAboveAtSameYIsVisible() {
        frustum.update(0, 64, 0, 0f, 0f, FOV);
        assertTrue(frustum.isVisible(0, 64, 10));
    }

    @Test
    void negativePitchBlockBelowIsVisible() {
        frustum.update(0, 70, 0, 0f, -45f, FOV);
        assertTrue(frustum.isVisible(0, 64, 10));
    }

    @Test
    void positivePitchBlockAboveIsVisible() {
        frustum.update(0, 64, 0, 0f, 45f, FOV);
        assertTrue(frustum.isVisible(0, 70, 10));
    }

    @Test
    void positivePitchBlockBelowIsVisible() {
        frustum.update(0, 128, 0, 0f, 90f, FOV);
        // Block is directly below the eye when looking straight down with 70° FOV
        assertTrue(frustum.isVisible(0, 0, 0));
    }

    @Test
    void extremePitchPositive90LookingDown() {
        frustum.update(0, 128, 0, 0f, 90f, FOV);
        assertTrue(frustum.isVisible(0, 64, 0));
    }

    @Test
    void extremePitchNegative90LookingUp() {
        frustum.update(0, 0, 0, 0f, -90f, FOV);
        assertTrue(frustum.isVisible(0, 64, 0));
    }

    @Test
    void extremePitch90BlockBehindNotVisible() {
        frustum.update(0, 64, 0, 0f, 90f, FOV);
        assertFalse(frustum.isVisible(0, 200, 0));
    }

    @Test
    void extremePitchMinus90BlockBelowNotVisible() {
        frustum.update(0, 64, 0, 0f, -90f, FOV);
        assertFalse(frustum.isVisible(0, 0, 0));
    }

    @Test
    void isAabbVisibleWithDisabledFrustum() {
        assertTrue(frustum.isAabbVisible(0, 0, 0, 10, 10, 10));
    }

    @Test
    void isAabbVisibleAfterUpdate() {
        frustum.update(0, 64, 0, 0f, 0f, FOV);
        assertTrue(frustum.isAabbVisible(-5, 60, 5, 5, 68, 15));
    }

    @Test
    void isAabbNotVisibleWhenFullyBehind() {
        frustum.update(0, 64, 0, 0f, 0f, FOV);
        assertFalse(frustum.isAabbVisible(-5, 60, -15, 5, 68, -5));
    }

    @Test
    void isChunkVisibleWithDisabledFrustum() {
        assertTrue(frustum.isChunkVisible(0, 0, 0));
    }

    @Test
    void isChunkVisibleInFront() {
        frustum.update(0, 64, 0, 0f, 0f, FOV);
        assertTrue(frustum.isChunkVisible(0, 60, 1));
    }

    @Test
    void isChunkNotVisibleBehind() {
        frustum.update(0, 64, 0, 0f, 0f, FOV);
        assertFalse(frustum.isChunkVisible(0, 60, -2));
    }

    @Test
    void blockCenterTestWithIntCoords() {
        frustum.update(0, 64, 0, 0f, 0f, FOV);
        assertTrue(frustum.isVisible(0, 64, 10));
    }

    @Test
    void multipleUpdatesOverwritePreviousState() {
        frustum.update(0, 64, 0, 0f, 0f, FOV);
        assertFalse(frustum.isVisible(0, 64, -10));

        frustum.update(0, 64, 0, 180f, 0f, FOV);
        assertTrue(frustum.isVisible(0, 64, -10));
    }

    @Test
    void eyePositionAffectsPlaneDistances() {
        frustum.update(100, 64, 100, 0f, 0f, FOV);
        assertTrue(frustum.isVisible(100, 64, 110));
        assertFalse(frustum.isVisible(0, 64, 0));
    }

    @Test
    void narrowFovExcludesMoreBlocks() {
        frustum.update(0, 64, 0, 0f, 0f, 10.0);
        assertFalse(frustum.isVisible(2, 64, 10));
    }

    @Test
    void wideFovIncludesMoreBlocks() {
        frustum.update(0, 64, 0, 0f, 0f, 170.0);
        assertTrue(frustum.isVisible(5, 64, 1));
    }
}
