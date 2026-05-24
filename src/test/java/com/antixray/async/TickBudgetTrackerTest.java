package com.antixray.async;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TickBudgetTrackerTest {

    private TickBudgetTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new TickBudgetTracker(5);
    }

    @Test
    void constructor_setsBudget() {
        assertEquals(5, tracker.getPerTickBudgetMs());
    }

    @Test
    void onTickStart_resetsCumulativeTime() {
        tracker.recordProcessingTime(3);
        assertTrue(tracker.getCumulativeTimeMs() > 0);
        tracker.onTickStart();
        assertEquals(0, tracker.getCumulativeTimeMs());
    }

    @Test
    void canProcessMore_trueInitially() {
        assertTrue(tracker.canProcessMore());
    }

    @Test
    void canProcessMore_falseAfterBudgetExceeded() {
        tracker.recordProcessingTime(6);
        assertFalse(tracker.canProcessMore());
    }

    @Test
    void canProcessMore_trueAtExactBudget() {
        tracker.recordProcessingTime(5);
        assertTrue(tracker.canProcessMore(), "5ms should still be within 5ms budget (strict less-than)");
    }

    @Test
    void recordProcessingTime_accumulates() {
        tracker.recordProcessingTime(2);
        assertEquals(2, tracker.getCumulativeTimeMs());
        tracker.recordProcessingTime(2);
        assertEquals(4, tracker.getCumulativeTimeMs());
    }

    @Test
    void recordProcessingTimeNs_accumulatesInNanos() {
        tracker.recordProcessingTimeNs(1_000_000);
        assertEquals(1, tracker.getCumulativeTimeMs());
    }

    @Test
    void getRemainingBudgetMs_decreasesAfterProcessing() {
        assertEquals(5, tracker.getRemainingBudgetMs());
        tracker.recordProcessingTime(3);
        assertEquals(2, tracker.getRemainingBudgetMs());
    }

    @Test
    void getRemainingBudgetMs_clampsToZero() {
        tracker.recordProcessingTime(100);
        assertEquals(0, tracker.getRemainingBudgetMs());
    }

    @Test
    void onTickStart_resetsRemainingBudget() {
        tracker.recordProcessingTime(4);
        assertEquals(1, tracker.getRemainingBudgetMs());
        tracker.onTickStart();
        assertEquals(5, tracker.getRemainingBudgetMs());
    }

    @Test
    void multipleTicks_resetBudgetEachTick() {
        tracker.recordProcessingTime(4);
        assertFalse(tracker.canProcessMore() == false);
        tracker.onTickStart();
        assertTrue(tracker.canProcessMore());
        tracker.recordProcessingTime(2);
        assertTrue(tracker.canProcessMore());
        tracker.recordProcessingTime(4);
        assertFalse(tracker.canProcessMore());
    }

    @Test
    void tickStartNanos_updatesOnTickStart() throws InterruptedException {
        long first = tracker.getTickStartNanos();
        Thread.sleep(10);
        tracker.onTickStart();
        long second = tracker.getTickStartNanos();
        assertTrue(second > first, "Tick start nanos should update on onTickStart");
    }

    @Test
    void budgetOf1ms_worksCorrectly() {
        TickBudgetTracker small = new TickBudgetTracker(1);
        assertTrue(small.canProcessMore());
        small.recordProcessingTime(1);
        assertTrue(small.canProcessMore());
        small.recordProcessingTime(1);
        assertFalse(small.canProcessMore());
    }

    @Test
    void budgetOf0ms_alwaysOverBudget() {
        TickBudgetTracker zero = new TickBudgetTracker(0);
        assertFalse(zero.canProcessMore());
    }
}
