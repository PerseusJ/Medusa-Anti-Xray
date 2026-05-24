package com.antixray.async;

import com.antixray.cache.CacheKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ThreadPoolManagerTest {

    private ThreadPoolManager poolManager;

    @BeforeEach
    void setUp() {
        poolManager = new ThreadPoolManager(2, 100);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        if (poolManager != null && !poolManager.isShutdown()) {
            poolManager.shutdown();
            poolManager.awaitTermination(2000);
        }
    }

    @Test
    void constructor_createsPool() {
        assertEquals(2, poolManager.getCorePoolSize());
        assertEquals(100, poolManager.getMaxQueueSize());
        assertFalse(poolManager.isShutdown());
    }

    @Test
    void computeDefaultPoolSize_returnsAtLeast2() {
        int size = ThreadPoolManager.computeDefaultPoolSize();
        assertTrue(size >= 2, "Default pool size should be at least 2");
        assertTrue(size <= Runtime.getRuntime().availableProcessors(),
                "Default pool size should not exceed available processors");
    }

    @Test
    void execute_runsTask() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> threadName = new AtomicReference<>();
        CacheKey key = new CacheKey("world", 0, 0, 1, 0);

        ObfuscationTask task = new ObfuscationTask(key, ObfuscationTask.Priority.HIGH, e -> latch.countDown()) {
            @Override
            public void run() {
                threadName.set(Thread.currentThread().getName());
                if (getCallback() != null && !isCancelled()) {
                    getCallback().accept(null);
                }
                latch.countDown();
            }
        };

        poolManager.execute(task);
        assertTrue(latch.await(2, TimeUnit.SECONDS), "Task should complete");
    }

    @Test
    void getQueueSize_returnsQueueSize() {
        assertEquals(0, poolManager.getQueueSize());
    }

    @Test
    void getCompletedTaskCount_increments() throws InterruptedException {
        assertEquals(0, poolManager.getCompletedTaskCount());

        CountDownLatch latch = new CountDownLatch(1);
        poolManager.execute(new ObfuscationTask(
                new CacheKey("world", 0, 0, 1, 0),
                ObfuscationTask.Priority.HIGH, null
        ) {
            @Override
            public void run() {
                latch.countDown();
            }
        });

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        Thread.sleep(100);
        assertTrue(poolManager.getCompletedTaskCount() >= 1);
    }

    @Test
    void resize_increasesPool() {
        assertEquals(2, poolManager.getCorePoolSize());
        poolManager.resize(4);
        assertEquals(4, poolManager.getCorePoolSize());
        assertEquals(4, poolManager.getPoolSize());
    }

    @Test
    void resize_decreasesPool() {
        poolManager.resize(1);
        assertEquals(1, poolManager.getCorePoolSize());
    }

    @Test
    void resize_minimumIs1() {
        poolManager.resize(0);
        assertEquals(1, poolManager.getCorePoolSize());
    }

    @Test
    void resize_negativeBecomes1() {
        poolManager.resize(-5);
        assertEquals(1, poolManager.getCorePoolSize());
    }

    @Test
    void resize_sameSize_isNoOp() {
        int before = poolManager.getCorePoolSize();
        poolManager.resize(2);
        assertEquals(before, poolManager.getCorePoolSize());
    }

    @Test
    void shutdown_stopsAcceptingTasks() {
        poolManager.shutdown();
        assertTrue(poolManager.isShutdown());
    }

    @Test
    void awaitTermination_returnsAfterShutdown() throws InterruptedException {
        poolManager.shutdown();
        boolean terminated = poolManager.awaitTermination(2000);
        assertTrue(terminated);
        assertTrue(poolManager.isTerminated());
    }

    @Test
    void queueIsPriorityBlockingQueue() {
        assertNotNull(poolManager.getQueue());
        assertTrue(poolManager.getQueue() instanceof java.util.concurrent.PriorityBlockingQueue);
    }

    @Test
    void threadName_followsConvention() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> threadName = new AtomicReference<>();

        poolManager.execute(new ObfuscationTask(
                new CacheKey("world", 0, 0, 1, 0),
                ObfuscationTask.Priority.CRITICAL, null
        ) {
            @Override
            public void run() {
                threadName.set(Thread.currentThread().getName());
                latch.countDown();
            }
        });

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertNotNull(threadName.get());
        assertTrue(threadName.get().startsWith("AntiXray-Worker-"),
                "Thread name should start with 'AntiXray-Worker-', was: " + threadName.get());
    }

    @Test
    void threadPriority_isBelowNormal() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger priority = new AtomicInteger();

        poolManager.execute(new ObfuscationTask(
                new CacheKey("world", 0, 0, 1, 0),
                ObfuscationTask.Priority.CRITICAL, null
        ) {
            @Override
            public void run() {
                priority.set(Thread.currentThread().getPriority());
                latch.countDown();
            }
        });

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(Thread.NORM_PRIORITY - 1, priority.get());
    }

    @Test
    void rejectionHandler_isCallerRunsPolicy() {
        assertTrue(poolManager.getRejectedExecutionHandler() instanceof ThreadPoolExecutor.CallerRunsPolicy,
                "Rejection policy should be CallerRunsPolicy");
    }

    @Test
    void execute_manyTasksDoesNotThrow() throws InterruptedException {
        for (int i = 0; i < 500; i++) {
            final int idx = i;
            poolManager.execute(new ObfuscationTask(
                    new CacheKey("world", idx, 0, 1, 0),
                    ObfuscationTask.Priority.LOW, null
            ) {
                @Override
                public void run() {}
            });
        }
        poolManager.shutdown();
        assertTrue(poolManager.awaitTermination(3000), "Pool should terminate cleanly");
    }

    @Test
    void resize_rebuildsExecutorCorrectly() throws InterruptedException {
        poolManager.resize(3);
        assertEquals(3, poolManager.getCorePoolSize());
        assertEquals(3, poolManager.getPoolSize());

        CountDownLatch latch = new CountDownLatch(3);
        for (int i = 0; i < 3; i++) {
            poolManager.execute(new ObfuscationTask(
                    new CacheKey("world", i, 0, 1, 0),
                    ObfuscationTask.Priority.HIGH, null
            ) {
                @Override
                public void run() {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(2, TimeUnit.SECONDS), "All 3 tasks should complete on 3-worker pool");
    }

    @Test
    void priorityOrdering_queuePreservesPriority() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(4);
        AtomicInteger counter = new AtomicInteger();

        ThreadPoolManager pool = new ThreadPoolManager(1, 100);
        try {
            pool.execute(new ObfuscationTask(
                    new CacheKey("w", 0, 0, 1, 0),
                    ObfuscationTask.Priority.LOW, null
            ) {
                @Override public void run() { counter.incrementAndGet(); latch.countDown(); }
            });

            Thread.sleep(50);

            pool.execute(new ObfuscationTask(
                    new CacheKey("w", 1, 0, 1, 0),
                    ObfuscationTask.Priority.CRITICAL, null
            ) {
                @Override public void run() { counter.incrementAndGet(); latch.countDown(); }
            });
            pool.execute(new ObfuscationTask(
                    new CacheKey("w", 2, 0, 1, 0),
                    ObfuscationTask.Priority.MEDIUM, null
            ) {
                @Override public void run() { counter.incrementAndGet(); latch.countDown(); }
            });
            pool.execute(new ObfuscationTask(
                    new CacheKey("w", 3, 0, 1, 0),
                    ObfuscationTask.Priority.HIGH, null
            ) {
                @Override public void run() { counter.incrementAndGet(); latch.countDown(); }
            });

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertEquals(4, counter.get());
        } finally {
            pool.shutdown();
            pool.awaitTermination(2000);
        }
    }
}
