package com.antixray.async;

import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ThreadPoolManager {

    private static final Logger LOGGER = Logger.getLogger("AntiXray");
    private static final String THREAD_NAME_PREFIX = "AntiXray-Worker-";

    private volatile ThreadPoolExecutor executor;
    private volatile int corePoolSize;
    private final int maxQueueSize;

    public ThreadPoolManager(int poolSize, int maxQueueSize) {
        this.corePoolSize = poolSize;
        this.maxQueueSize = maxQueueSize;
        this.executor = createExecutor(poolSize, maxQueueSize);
    }

    private ThreadPoolExecutor createExecutor(int poolSize, int maxQueueSize) {
        PriorityBlockingQueue<Runnable> workQueue = new PriorityBlockingQueue<>(maxQueueSize, (a, b) -> {
            if (a instanceof ObfuscationTask ta && b instanceof ObfuscationTask tb) {
                return ta.compareTo(tb);
            }
            return 0;
        });

        ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, THREAD_NAME_PREFIX + counter.incrementAndGet());
                t.setPriority(Thread.NORM_PRIORITY - 1);
                t.setDaemon(true);
                return t;
            }
        };

        ThreadPoolExecutor exec = new ThreadPoolExecutor(
                poolSize,
                poolSize,
                0L, TimeUnit.MILLISECONDS,
                workQueue,
                threadFactory,
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        LOGGER.log(Level.INFO, "Thread pool created: {0} workers, max queue: {1}",
                new Object[]{poolSize, maxQueueSize});

        return exec;
    }

    public static int computeDefaultPoolSize() {
        int cpus = Runtime.getRuntime().availableProcessors();
        return Math.max(2, cpus - 2);
    }

    public void execute(ObfuscationTask task) {
        executor.execute(task);
    }

    public PriorityBlockingQueue<Runnable> getQueue() {
        @SuppressWarnings("unchecked")
        PriorityBlockingQueue<Runnable> q = (PriorityBlockingQueue<Runnable>) executor.getQueue();
        return q;
    }

    public int getQueueSize() {
        return executor.getQueue().size();
    }

    public int getActiveCount() {
        return executor.getActiveCount();
    }

    public long getCompletedTaskCount() {
        return executor.getCompletedTaskCount();
    }

    public int getPoolSize() {
        return executor.getPoolSize();
    }

    public int getCorePoolSize() {
        return corePoolSize;
    }

    public int getMaxQueueSize() {
        return maxQueueSize;
    }

    public RejectedExecutionHandler getRejectedExecutionHandler() {
        return executor.getRejectedExecutionHandler();
    }

    public synchronized void resize(int newSize) {
        if (newSize == corePoolSize) return;
        if (newSize < 1) newSize = 1;

        LOGGER.log(Level.INFO, "Resizing thread pool from {0} to {1}",
                new Object[]{corePoolSize, newSize});

        int oldSize = corePoolSize;
        corePoolSize = newSize;

        if (newSize > oldSize) {
            executor.setMaximumPoolSize(newSize);
            executor.setCorePoolSize(newSize);
            executor.prestartAllCoreThreads();
        } else {
            executor.setCorePoolSize(newSize);
            executor.setMaximumPoolSize(newSize);
        }

        LOGGER.log(Level.INFO, "Thread pool resized to {0} workers", newSize);
    }

    public void shutdown() {
        LOGGER.log(Level.INFO, "Shutting down thread pool");
        executor.shutdown();
    }

    public boolean awaitTermination(long timeoutMs) throws InterruptedException {
        return executor.awaitTermination(timeoutMs, TimeUnit.MILLISECONDS);
    }

    public boolean isShutdown() {
        return executor.isShutdown();
    }

    public boolean isTerminated() {
        return executor.isTerminated();
    }
}
