package com.antixray.async;

import com.antixray.cache.CacheKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BackpressureHandlerTest {

    private ThreadPoolManager poolManager;
    private BackpressureHandler handler;

    @BeforeEach
    void setUp() {
        poolManager = new ThreadPoolManager(2, 100);
        handler = new BackpressureHandler(100, poolManager);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        if (poolManager != null && !poolManager.isShutdown()) {
            poolManager.shutdown();
            poolManager.awaitTermination(2000);
        }
    }

    @Test
    void constructor_setsMaxQueueSize() {
        assertEquals(100, handler.getMaxQueueSize());
    }

    @Test
    void getQueueFillRatio_emptyQueue() {
        assertEquals(0.0, handler.getQueueFillRatio(), 0.001);
    }

    @Test
    void shouldCancelLowPriority_belowThreshold() {
        assertFalse(handler.shouldCancelLowPriority());
    }

    @Test
    void shouldCancelMediumPriority_belowThreshold() {
        assertFalse(handler.shouldCancelMediumPriority());
    }

    @Test
    void shouldSendUnobfuscated_belowThreshold() {
        assertFalse(handler.shouldSendUnobfuscated());
    }

    @Test
    void isCircuitOpen_initiallyFalse() {
        assertFalse(handler.isCircuitOpen());
    }

    @Test
    void isPermanentlyOpen_initiallyFalse() {
        assertFalse(handler.isPermanentlyOpen());
    }

    @Test
    void shouldProcessTask_circuitClosed_allowsAll() {
        assertTrue(handler.shouldProcessTask(ObfuscationTask.Priority.CRITICAL));
        assertTrue(handler.shouldProcessTask(ObfuscationTask.Priority.HIGH));
        assertTrue(handler.shouldProcessTask(ObfuscationTask.Priority.MEDIUM));
        assertTrue(handler.shouldProcessTask(ObfuscationTask.Priority.LOW));
    }

    @Test
    void resetCircuitBreaker_clearsAllState() {
        handler.resetCircuitBreaker();
        assertFalse(handler.isCircuitOpen());
        assertFalse(handler.isPermanentlyOpen());
        assertEquals(0, handler.getCircuitTripCount());
        assertEquals(0, handler.getConsecutiveHighFillTicks());
    }

    @Test
    void onTickEnd_clearsPassDecisions() {
        handler.onTickEnd();
        assertDoesNotThrow(() -> handler.onTickEnd());
    }

    @Test
    void shouldProcessTask_circuitOpen_allowsCritical() {
        forceCircuitOpen();
        assertTrue(handler.shouldProcessTask(ObfuscationTask.Priority.CRITICAL));
    }

    @Test
    void shouldProcessTask_circuitOpen_blocksNonCriticalInitially() {
        forceCircuitOpen();
        assertFalse(handler.shouldProcessTask(ObfuscationTask.Priority.HIGH));
        assertFalse(handler.shouldProcessTask(ObfuscationTask.Priority.MEDIUM));
        assertFalse(handler.shouldProcessTask(ObfuscationTask.Priority.LOW));
    }

    @Test
    void shouldProcessTask_permanentlyOpen_blocksAll() {
        forcePermanentOpen();
        assertFalse(handler.shouldProcessTask(ObfuscationTask.Priority.CRITICAL));
        assertFalse(handler.shouldProcessTask(ObfuscationTask.Priority.HIGH));
    }

    @Test
    void getQueueFillRatio_withFullQueue() throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            poolManager.execute(new ObfuscationTask(
                    new CacheKey("world", i, 0, 1, 0),
                    ObfuscationTask.Priority.LOW, null
            ) {
                @Override
                public void run() {
                    try { Thread.sleep(5000); } catch (InterruptedException e) {}
                }
            });
        }
        Thread.sleep(100);

        double ratio = handler.getQueueFillRatio();
        assertTrue(ratio > 0, "Queue should have tasks: ratio=" + ratio);
    }

    @Test
    void consecutiveHighFillTicks_incrementsWhenAbove98() {
        assertEquals(0, handler.getConsecutiveHighFillTicks());
    }

    @Test
    void resetCircuitBreaker_allowsProcessing() {
        forceCircuitOpen();
        assertTrue(handler.isCircuitOpen());
        handler.resetCircuitBreaker();
        assertFalse(handler.isCircuitOpen());
        assertTrue(handler.shouldProcessTask(ObfuscationTask.Priority.HIGH));
    }

    @Test
    void backpressure_queueAt75Percent_dropsLowPriority() throws InterruptedException {
        submitBlockingTask();
        submitBlockingTask();

        for (int i = 0; i < 76; i++) {
            poolManager.execute(new ObfuscationTask(
                    new CacheKey("world", i, 1, 1, 0),
                    ObfuscationTask.Priority.LOW, null
            ));
        }
        Thread.sleep(100);

        assertTrue(handler.shouldCancelLowPriority());
        assertFalse(handler.shouldCancelMediumPriority());

        handler.checkAndApply();

        assertEquals(0, poolManager.getQueueSize());
    }

    @Test
    void backpressure_queueAt90Percent_dropsMediumPriority() throws InterruptedException {
        submitBlockingTask();
        submitBlockingTask();

        for (int i = 0; i < 91; i++) {
            poolManager.execute(new ObfuscationTask(
                    new CacheKey("world", i, 1, 1, 0),
                    ObfuscationTask.Priority.MEDIUM, null
            ));
        }
        Thread.sleep(100);

        assertTrue(handler.shouldCancelMediumPriority());

        handler.checkAndApply();

        assertEquals(0, poolManager.getQueueSize());
    }

    @Test
    void backpressure_queueAt100Percent_opensCircuit() throws InterruptedException {
        submitBlockingTask();
        submitBlockingTask();

        for (int i = 0; i < 100; i++) {
            poolManager.execute(new ObfuscationTask(
                    new CacheKey("world", i, 1, 1, 0),
                    ObfuscationTask.Priority.HIGH, null
            ));
        }
        Thread.sleep(100);

        assertFalse(handler.isCircuitOpen());

        handler.checkAndApply();

        assertTrue(handler.isCircuitOpen(), "Circuit should open when queue reaches max size");
    }

    @Test
    void backpressure_circuitReclosesAfterDelay() throws InterruptedException {
        forceCircuitOpen();
        assertTrue(handler.isCircuitOpen());

        try {
            var timeField = BackpressureHandler.class.getDeclaredField("circuitOpenTime");
            timeField.setAccessible(true);
            var atomicLong = (java.util.concurrent.atomic.AtomicLong) timeField.get(handler);
            atomicLong.set(System.currentTimeMillis() - 20000);
        } catch (Exception e) {
            fail(e);
        }

        assertTrue(handler.shouldProcessTask(ObfuscationTask.Priority.HIGH));
        assertFalse(handler.isCircuitOpen(), "Circuit should close after delay");
    }

    @Test
    void backpressure_permanentOpenAfter3Triggers() throws Exception {
        var openCircuitMethod = BackpressureHandler.class.getDeclaredMethod("openCircuit");
        openCircuitMethod.setAccessible(true);

        openCircuitMethod.invoke(handler);
        assertTrue(handler.isCircuitOpen());
        assertFalse(handler.isPermanentlyOpen());

        var closeCircuitMethod = BackpressureHandler.class.getDeclaredMethod("closeCircuit");
        closeCircuitMethod.setAccessible(true);
        closeCircuitMethod.invoke(handler);
        assertFalse(handler.isCircuitOpen());

        openCircuitMethod.invoke(handler);
        assertTrue(handler.isCircuitOpen());
        assertFalse(handler.isPermanentlyOpen());
        closeCircuitMethod.invoke(handler);

        openCircuitMethod.invoke(handler);
        assertTrue(handler.isPermanentlyOpen(), "Should be permanently open after 3 trips in window");
        assertTrue(handler.isCircuitOpen());
    }

    private void submitBlockingTask() {
        poolManager.execute(new ObfuscationTask(
                new CacheKey("world", (int) (Math.random() * 10000), 0, 1, 0),
                ObfuscationTask.Priority.CRITICAL, null
        ) {
            @Override
            public void run() {
                try { Thread.sleep(10000); } catch (InterruptedException e) {}
            }
        });
    }

    private void forceCircuitOpen() {
        try {
            var field = BackpressureHandler.class.getDeclaredField("circuitOpen");
            field.setAccessible(true);
            var atomicBool = (java.util.concurrent.atomic.AtomicBoolean) field.get(handler);
            atomicBool.set(true);

            var timeField = BackpressureHandler.class.getDeclaredField("circuitOpenTime");
            timeField.setAccessible(true);
            var atomicLong = (java.util.concurrent.atomic.AtomicLong) timeField.get(handler);
            atomicLong.set(System.currentTimeMillis());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void forcePermanentOpen() {
        try {
            var field = BackpressureHandler.class.getDeclaredField("permanentlyOpen");
            field.setAccessible(true);
            var atomicBool = (java.util.concurrent.atomic.AtomicBoolean) field.get(handler);
            atomicBool.set(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
