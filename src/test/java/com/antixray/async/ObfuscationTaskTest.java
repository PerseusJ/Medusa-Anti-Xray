package com.antixray.async;

import com.antixray.cache.CacheEntry;
import com.antixray.cache.CacheKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ObfuscationTaskTest {

    private CacheKey key;

    @BeforeEach
    void setUp() {
        key = new CacheKey("world", 1, 2, 3, 100);
    }

    @Test
    void priority_enumOrdering() {
        assertEquals(0, ObfuscationTask.Priority.CRITICAL.getLevel());
        assertEquals(1, ObfuscationTask.Priority.HIGH.getLevel());
        assertEquals(2, ObfuscationTask.Priority.MEDIUM.getLevel());
        assertEquals(3, ObfuscationTask.Priority.LOW.getLevel());
    }

    @Test
    void constructor_setsFields() {
        AtomicReference<CacheEntry> ref = new AtomicReference<>();
        ObfuscationTask task = new ObfuscationTask(key, ObfuscationTask.Priority.HIGH, ref::set);

        assertEquals(key, task.getKey());
        assertEquals(ObfuscationTask.Priority.HIGH, task.getPriority());
        assertNotNull(task.getCallback());
        assertTrue(task.getEnqueueTime() > 0);
        assertFalse(task.isCancelled());
    }

    @Test
    void cancel_setsCancelled() {
        ObfuscationTask task = new ObfuscationTask(key, ObfuscationTask.Priority.LOW, e -> {});
        assertFalse(task.isCancelled());
        assertTrue(task.cancel());
        assertTrue(task.isCancelled());
    }

    @Test
    void cancel_idempotent() {
        ObfuscationTask task = new ObfuscationTask(key, ObfuscationTask.Priority.LOW, e -> {});
        assertTrue(task.cancel());
        assertFalse(task.cancel());
        assertTrue(task.isCancelled());
    }

    @Test
    void getWaitTimeMs_increasesOverTime() throws InterruptedException {
        ObfuscationTask task = new ObfuscationTask(key, ObfuscationTask.Priority.CRITICAL, e -> {});
        long wait1 = task.getWaitTimeMs();
        Thread.sleep(10);
        long wait2 = task.getWaitTimeMs();
        assertTrue(wait2 >= wait1, "Wait time should increase");
    }

    @Test
    void compareTo_priorityOrdering() {
        ObfuscationTask critical = new ObfuscationTask(key, ObfuscationTask.Priority.CRITICAL, e -> {});
        ObfuscationTask high = new ObfuscationTask(key, ObfuscationTask.Priority.HIGH, e -> {});
        ObfuscationTask medium = new ObfuscationTask(key, ObfuscationTask.Priority.MEDIUM, e -> {});
        ObfuscationTask low = new ObfuscationTask(key, ObfuscationTask.Priority.LOW, e -> {});

        assertTrue(critical.compareTo(high) < 0, "CRITICAL < HIGH");
        assertTrue(high.compareTo(medium) < 0, "HIGH < MEDIUM");
        assertTrue(medium.compareTo(low) < 0, "MEDIUM < LOW");
        assertEquals(0, critical.compareTo(critical), "Same priority equals");
    }

    @Test
    void compareTo_fifoWithinSamePriority() throws InterruptedException {
        ObfuscationTask first = new ObfuscationTask(key, ObfuscationTask.Priority.HIGH, e -> {});
        Thread.sleep(1);
        ObfuscationTask second = new ObfuscationTask(key, ObfuscationTask.Priority.HIGH, e -> {});
        assertTrue(first.compareTo(second) < 0, "Earlier enqueue time sorts first");
        assertTrue(second.compareTo(first) > 0, "Later enqueue time sorts after");
    }

    @Test
    void callback_canBeNull() {
        ObfuscationTask task = new ObfuscationTask(key, ObfuscationTask.Priority.CRITICAL, null);
        assertNull(task.getCallback());
    }

    @Test
    void callback_invokedWithEntry() {
        List<CacheEntry> results = new ArrayList<>();
        ObfuscationTask task = new ObfuscationTask(key, ObfuscationTask.Priority.HIGH, results::add);
        CacheEntry entry = new CacheEntry(new byte[]{1, 2, 3}, 1);
        task.getCallback().accept(entry);
        assertEquals(1, results.size());
        assertArrayEquals(new byte[]{1, 2, 3}, results.get(0).getObfuscatedData());
    }

    @Test
    void differentPriorities_sortedCorrectly() {
        List<ObfuscationTask> tasks = new ArrayList<>();
        tasks.add(new ObfuscationTask(key, ObfuscationTask.Priority.LOW, e -> {}));
        tasks.add(new ObfuscationTask(key, ObfuscationTask.Priority.CRITICAL, e -> {}));
        tasks.add(new ObfuscationTask(key, ObfuscationTask.Priority.MEDIUM, e -> {}));
        tasks.add(new ObfuscationTask(key, ObfuscationTask.Priority.HIGH, e -> {}));

        tasks.sort(null);

        assertEquals(ObfuscationTask.Priority.CRITICAL, tasks.get(0).getPriority());
        assertEquals(ObfuscationTask.Priority.HIGH, tasks.get(1).getPriority());
        assertEquals(ObfuscationTask.Priority.MEDIUM, tasks.get(2).getPriority());
        assertEquals(ObfuscationTask.Priority.LOW, tasks.get(3).getPriority());
    }

    @Test
    void timeoutDetection_isDetected() throws InterruptedException {
        ObfuscationTask task = new ObfuscationTask(key, ObfuscationTask.Priority.LOW, e -> {});
        long limitMs = 5;
        Thread.sleep(15);
        assertTrue(task.getWaitTimeMs() > limitMs, "Task wait time should exceed limit for timeout detection");
    }
}
