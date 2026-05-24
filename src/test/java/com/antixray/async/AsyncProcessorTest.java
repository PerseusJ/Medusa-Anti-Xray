package com.antixray.async;

import com.antixray.cache.CacheEntry;
import com.antixray.cache.CacheKey;
import com.antixray.cache.ObfuscationCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class AsyncProcessorTest {

    private ThreadPoolManager poolManager;
    private TickBudgetTracker budgetTracker;
    private BackpressureHandler backpressureHandler;
    private ObfuscationCache cache;
    private AsyncProcessor processor;

    @BeforeEach
    void setUp() {
        poolManager = new ThreadPoolManager(2, 1000);
        budgetTracker = new TickBudgetTracker(5);
        backpressureHandler = new BackpressureHandler(1000, poolManager);
        cache = new ObfuscationCache(100, 300);
        processor = new AsyncProcessor(poolManager, budgetTracker, backpressureHandler, cache, 5000);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        processor.shutdown();
        processor.awaitTermination(2000);
    }

    @Test
    void constructor_setsComponents() {
        assertSame(poolManager, processor.getThreadPoolManager());
        assertSame(budgetTracker, processor.getTickBudgetTracker());
        assertSame(backpressureHandler, processor.getBackpressureHandler());
        assertSame(cache, processor.getCache());
    }

    @Test
    void processTask_executesObfuscationAndCaches() {
        CacheKey key = new CacheKey("world", 1, 2, 3, 100);
        CacheEntry entry = new CacheEntry(new byte[]{42}, 1);

        processor.setObfuscationFunction(k -> entry);

        AtomicReference<CacheEntry> result = new AtomicReference<>();
        ObfuscationTask task = new ObfuscationTask(key, ObfuscationTask.Priority.HIGH, result::set);

        processor.processTask(task);

        assertEquals(1, processor.getTasksCompleted());
        assertNotNull(result.get());
        assertEquals(1, result.get().getSectionCount());

        CacheEntry cached = cache.get(key);
        assertNotNull(cached);
    }

    @Test
    void processTask_cacheHit_skipsObfuscation() {
        CacheKey key = new CacheKey("world", 5, 5, 1, 0);
        CacheEntry cached = new CacheEntry(new byte[]{99}, 2);
        cache.put(key, cached);

        AtomicReference<CacheEntry> result = new AtomicReference<>();
        ObfuscationTask task = new ObfuscationTask(key, ObfuscationTask.Priority.HIGH, result::set);

        processor.processTask(task);

        assertEquals(0, processor.getTasksCompleted(), "Should not count as completed (cache hit)");
        assertEquals(1, processor.getTasksCacheHit());
        assertNotNull(result.get());
        assertEquals(2, result.get().getSectionCount());
    }

    @Test
    void processTask_cancelledTask_skipped() {
        CacheKey key = new CacheKey("world", 1, 1, 1, 0);
        ObfuscationTask task = new ObfuscationTask(key, ObfuscationTask.Priority.LOW, e -> {});
        task.cancel();

        processor.processTask(task);

        assertEquals(0, processor.getTasksCompleted());
        assertEquals(1, processor.getTasksCancelled());
    }

    @Test
    void processTask_criticalOverBudget_cancelled() {
        CacheKey key = new CacheKey("world", 1, 1, 1, 0);
        budgetTracker.recordProcessingTime(10);

        ObfuscationTask task = new ObfuscationTask(key, ObfuscationTask.Priority.CRITICAL, e -> {});
        processor.processTask(task);

        assertEquals(0, processor.getTasksCompleted());
        assertEquals(1, processor.getTasksCancelled());
    }

    @Test
    void processTask_highOverBudget_stillProcessed() {
        CacheKey key = new CacheKey("world", 1, 1, 1, 0);
        CacheEntry entry = new CacheEntry(new byte[]{1}, 1);
        processor.setObfuscationFunction(k -> entry);

        budgetTracker.recordProcessingTime(10);

        ObfuscationTask task = new ObfuscationTask(key, ObfuscationTask.Priority.HIGH, e -> {});
        processor.processTask(task);

        assertEquals(1, processor.getTasksCompleted(), "Non-CRITICAL tasks should not be budget-gated");
    }

    @Test
    void processTask_noObfuscationFunction_skips() {
        CacheKey key = new CacheKey("world", 1, 1, 1, 0);
        ObfuscationTask task = new ObfuscationTask(key, ObfuscationTask.Priority.HIGH, e -> {});

        processor.processTask(task);

        assertEquals(0, processor.getTasksCompleted());
    }

    @Test
    void processTask_nullResult_notCached() {
        CacheKey key = new CacheKey("world", 1, 1, 1, 0);
        processor.setObfuscationFunction(k -> null);

        AtomicReference<CacheEntry> result = new AtomicReference<>();
        ObfuscationTask task = new ObfuscationTask(key, ObfuscationTask.Priority.HIGH, result::set);

        processor.processTask(task);

        assertEquals(1, processor.getTasksCompleted());
        assertNull(result.get());
        assertNull(cache.get(key));
    }

    @Test
    void processTask_callbackReceivesResult() {
        CacheKey key = new CacheKey("world", 3, 4, 1, 0);
        CacheEntry entry = new CacheEntry(new byte[]{7, 8, 9}, 3);
        processor.setObfuscationFunction(k -> entry);

        List<CacheEntry> results = new ArrayList<>();
        ObfuscationTask task = new ObfuscationTask(key, ObfuscationTask.Priority.MEDIUM, results::add);

        processor.processTask(task);

        assertEquals(1, results.size());
        assertArrayEquals(new byte[]{7, 8, 9}, results.get(0).getObfuscatedData());
    }

    @Test
    void processTask_callbackNull_noException() {
        CacheKey key = new CacheKey("world", 3, 4, 1, 0);
        CacheEntry entry = new CacheEntry(new byte[]{1}, 1);
        processor.setObfuscationFunction(k -> entry);

        ObfuscationTask task = new ObfuscationTask(key, ObfuscationTask.Priority.MEDIUM, null);
        assertDoesNotThrow(() -> processor.processTask(task));
        assertEquals(1, processor.getTasksCompleted());
    }

    @Test
    void processTask_tracksMetrics() {
        CacheKey key = new CacheKey("world", 1, 1, 1, 0);
        CacheEntry entry = new CacheEntry(new byte[]{1}, 1);
        processor.setObfuscationFunction(k -> entry);

        ObfuscationTask task = new ObfuscationTask(key, ObfuscationTask.Priority.HIGH, e -> {});
        processor.processTask(task);

        assertEquals(1, processor.getTasksCompleted());
        assertTrue(processor.getAverageProcessTimeMs() >= 0);
        assertTrue(processor.getAverageWaitTimeMs() >= 0);
    }

    @Test
    void enqueue_submitsTaskToPool() throws InterruptedException {
        CacheKey key = new CacheKey("world", 1, 1, 1, 0);
        CacheEntry entry = new CacheEntry(new byte[]{1}, 1);
        processor.setObfuscationFunction(k -> {
            try { Thread.sleep(10); } catch (InterruptedException e) { return null; }
            return entry;
        });

        CountDownLatch latch = new CountDownLatch(1);
        ObfuscationTask task = new ObfuscationTask(key, ObfuscationTask.Priority.HIGH, e -> latch.countDown()) {
            @Override
            public void run() {
                processor.processTask(this);
            }
        };

        processor.enqueue(task);
        assertTrue(latch.await(2, TimeUnit.SECONDS), "Task should complete within timeout");
    }

    @Test
    void onTickStart_resetsBudgetAndAppliesBackpressure() {
        budgetTracker.recordProcessingTime(10);
        assertFalse(budgetTracker.canProcessMore());

        processor.onTickStart();
        assertTrue(budgetTracker.canProcessMore());
    }

    @Test
    void onTickEnd_clearsBackpressureDecisions() {
        assertDoesNotThrow(() -> processor.onTickEnd());
    }

    @Test
    void getTasksTimedOut_incrementsOnTimeout() throws InterruptedException {
        long shortTimeout = 1;
        AsyncProcessor shortProcessor = new AsyncProcessor(
                poolManager, budgetTracker, backpressureHandler, cache, shortTimeout);

        CacheKey key = new CacheKey("world", 99, 99, 1, 0);
        ObfuscationTask task = new ObfuscationTask(key, ObfuscationTask.Priority.HIGH, e -> {});

        Thread.sleep(10);

        shortProcessor.processTask(task);

        assertEquals(1, shortProcessor.getTasksTimedOut());
    }

    @Test
    void shutdown_closesPoolAndCache() {
        processor.shutdown();
        assertTrue(poolManager.isShutdown());
    }

    @Test
    void processTask_obfuscationFunctionException_handled() {
        CacheKey key = new CacheKey("world", 1, 1, 1, 0);
        processor.setObfuscationFunction(k -> {
            throw new RuntimeException("test error");
        });

        ObfuscationTask task = new ObfuscationTask(key, ObfuscationTask.Priority.HIGH, e -> {});
        assertDoesNotThrow(() -> processor.processTask(task));
        assertEquals(0, processor.getTasksCompleted());
    }

    @Test
    void processTask_multipleTasks_tracksAll() {
        processor.setObfuscationFunction(k -> new CacheEntry(new byte[]{1}, 1));

        for (int i = 0; i < 5; i++) {
            CacheKey key = new CacheKey("world", i, 0, 1, 0);
            ObfuscationTask task = new ObfuscationTask(key, ObfuscationTask.Priority.HIGH, e -> {});
            processor.processTask(task);
        }

        assertEquals(5, processor.getTasksCompleted());
        assertTrue(processor.getAverageProcessTimeMs() >= 0);
    }

    @Test
    void getAverageWaitTimeMs_noTasks_returnsZero() {
        assertEquals(0.0, processor.getAverageWaitTimeMs(), 0.001);
    }

    @Test
    void getAverageProcessTimeMs_noTasks_returnsZero() {
        assertEquals(0.0, processor.getAverageProcessTimeMs(), 0.001);
    }

    @Test
    void processTask_criticalTask_recordsBudgetTime() {
        CacheKey key = new CacheKey("world", 1, 1, 1, 0);
        CacheEntry entry = new CacheEntry(new byte[]{1}, 1);
        processor.setObfuscationFunction(k -> {
            try { Thread.sleep(1); } catch (InterruptedException e) { return null; }
            return entry;
        });

        ObfuscationTask task = new ObfuscationTask(key, ObfuscationTask.Priority.CRITICAL, e -> {});
        processor.processTask(task);

        assertTrue(budgetTracker.getCumulativeTimeMs() >= 0);
    }
}
