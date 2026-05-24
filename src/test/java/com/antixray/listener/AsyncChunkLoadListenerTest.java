package com.antixray.listener;

import com.antixray.AntiXrayPlugin;
import com.antixray.async.AsyncProcessor;
import com.antixray.async.BackpressureHandler;
import com.antixray.async.ChunkPreObfuscator;
import com.antixray.async.TickBudgetTracker;
import com.antixray.async.ThreadPoolManager;
import com.antixray.cache.ObfuscationCache;
import com.antixray.config.ConfigurationManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AsyncChunkLoadListenerTest {

    private AntiXrayPlugin plugin;
    private AsyncProcessor asyncProcessor;
    private ObfuscationCache cache;
    private ChunkPreObfuscator preObfuscator;
    private ConfigurationManager configManager;

    @BeforeEach
    void setUp() {
        plugin = mock(AntiXrayPlugin.class);
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("TestAntiXray"));

        ThreadPoolManager poolManager = new ThreadPoolManager(2, 1000);
        TickBudgetTracker budgetTracker = new TickBudgetTracker(8);
        BackpressureHandler backpressureHandler = new BackpressureHandler(1000, poolManager);
        cache = new ObfuscationCache(100, 300);
        asyncProcessor = new AsyncProcessor(poolManager, budgetTracker, backpressureHandler, cache, 5000);
        asyncProcessor.setObfuscationFunction(key -> new com.antixray.cache.CacheEntry(new byte[]{1}, 1));

        when(plugin.getAsyncProcessor()).thenReturn(asyncProcessor);
        when(plugin.getObfuscationCache()).thenReturn(cache);

        preObfuscator = new ChunkPreObfuscator(asyncProcessor, cache, 12345);
        configManager = mock(ConfigurationManager.class);
        when(configManager.getConfigHash()).thenReturn(12345);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        if (asyncProcessor != null) {
            asyncProcessor.shutdown();
            asyncProcessor.awaitTermination(2000);
        }
    }

    @Test
    void register_nonPaper_returnsFalse() {
        AsyncChunkLoadListener listener = new AsyncChunkLoadListener(plugin, preObfuscator, configManager);
        boolean result = listener.register();

        assertFalse(result);
        assertFalse(listener.isRegistered());
        assertEquals(1, listener.getTasksSkippedNotPaper());
    }

    @Test
    void unregister_whenNotRegistered_noException() {
        AsyncChunkLoadListener listener = new AsyncChunkLoadListener(plugin, preObfuscator, configManager);
        assertDoesNotThrow(() -> listener.unregister());
    }

    @Test
    void register_doubleRegister_idempotent() {
        AsyncChunkLoadListener listener = new AsyncChunkLoadListener(plugin, preObfuscator, configManager);
        listener.register();
        boolean second = listener.register();
        assertFalse(second);
    }

    @Test
    void isRegistered_initiallyFalse() {
        AsyncChunkLoadListener listener = new AsyncChunkLoadListener(plugin, preObfuscator, configManager);
        assertFalse(listener.isRegistered());
    }

    @Test
    void isPaperDetected_initiallyFalse() {
        AsyncChunkLoadListener listener = new AsyncChunkLoadListener(plugin, preObfuscator, configManager);
        assertFalse(listener.isPaperDetected());
    }

    @Test
    void getPreObfuscator_returnsSameInstance() {
        AsyncChunkLoadListener listener = new AsyncChunkLoadListener(plugin, preObfuscator, configManager);
        assertSame(preObfuscator, listener.getPreObfuscator());
    }

    @Test
    void resetStats_clearsAllCounters() {
        AsyncChunkLoadListener listener = new AsyncChunkLoadListener(plugin, preObfuscator, configManager);
        listener.resetStats();

        assertEquals(0, listener.getTasksEnqueued());
        assertEquals(0, listener.getTasksSkippedCacheHit());
        assertEquals(0, listener.getTasksSkippedDisabled());
        assertEquals(0, listener.getTasksSkippedNotPaper());
    }

    @Test
    void getTasksEnqueued_delegatesToPreObfuscator() {
        AsyncChunkLoadListener listener = new AsyncChunkLoadListener(plugin, preObfuscator, configManager);
        assertEquals(preObfuscator.getTasksEnqueued(), listener.getTasksEnqueued());
    }

    @Test
    void getTasksSkippedCacheHit_delegatesToPreObfuscator() {
        AsyncChunkLoadListener listener = new AsyncChunkLoadListener(plugin, preObfuscator, configManager);
        assertEquals(preObfuscator.getTasksSkippedCacheHit(), listener.getTasksSkippedCacheHit());
    }

    @Test
    void getTasksSkippedDisabled_delegatesToPreObfuscator() {
        AsyncChunkLoadListener listener = new AsyncChunkLoadListener(plugin, preObfuscator, configManager);
        assertEquals(preObfuscator.getTasksSkippedDisabled(), listener.getTasksSkippedDisabled());
    }
}
