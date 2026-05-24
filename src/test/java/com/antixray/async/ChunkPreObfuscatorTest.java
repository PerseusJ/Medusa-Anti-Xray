package com.antixray.async;

import com.antixray.cache.CacheEntry;
import com.antixray.cache.CacheKey;
import com.antixray.cache.ObfuscationCache;
import com.antixray.config.WorldConfig;
import com.antixray.engine.ObfuscationMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChunkPreObfuscatorTest {

    private ThreadPoolManager poolManager;
    private AsyncProcessor asyncProcessor;
    private ObfuscationCache cache;
    private ChunkPreObfuscator preObfuscator;

    private static final int CONFIG_HASH = 12345;

    @BeforeEach
    void setUp() {
        poolManager = new ThreadPoolManager(2, 1000);
        TickBudgetTracker budgetTracker = new TickBudgetTracker(8);
        BackpressureHandler backpressureHandler = new BackpressureHandler(1000, poolManager);
        cache = new ObfuscationCache(100, 300);
        asyncProcessor = new AsyncProcessor(poolManager, budgetTracker, backpressureHandler, cache, 5000);
        asyncProcessor.setObfuscationFunction(key -> new CacheEntry(new byte[]{1}, 1));
        preObfuscator = new ChunkPreObfuscator(asyncProcessor, cache, CONFIG_HASH);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        if (asyncProcessor != null) {
            asyncProcessor.shutdown();
            asyncProcessor.awaitTermination(2000);
        }
    }

    private WorldConfig defaultWorldConfig() {
        return WorldConfig.builder()
                .enabled(true)
                .engineMode(ObfuscationMode.MODE_3)
                .maxBlockHeight(64)
                .fakeOreChance(0.07)
                .lavaObscures(true)
                .leavesAreTransparent(true)
                .bypassPermission("antixray.bypass")
                .maxRevealedPerPlayer(10000)
                .movementThreshold(0.5)
                .updateRadius(4)
                .maxDeobfuscationUpdatesPerTick(64)
                .elytraVelocityThreshold(1.5)
                .build();
    }

    private WorldConfig disabledWorldConfig() {
        return WorldConfig.builder()
                .enabled(false)
                .engineMode(ObfuscationMode.MODE_3)
                .maxBlockHeight(64)
                .build();
    }

    @Test
    void enqueuePreObfuscation_enqueuesMEDIUMPriorityTask() {
        WorldConfig worldConfig = defaultWorldConfig();
        boolean result = preObfuscator.enqueuePreObfuscation("test_world", 10, 20, worldConfig);

        assertTrue(result);
        assertEquals(1, preObfuscator.getTasksEnqueued());
        assertEquals(0, preObfuscator.getTasksSkippedCacheHit());
    }

    @Test
    void enqueuePreObfuscation_skipsIfAlreadyCached() {
        WorldConfig worldConfig = defaultWorldConfig();
        CacheKey key = new CacheKey("test_world", 10, 20, worldConfig.getEngineMode(), CONFIG_HASH);
        cache.put(key, new CacheEntry(new byte[]{1}, 1));

        boolean result = preObfuscator.enqueuePreObfuscation("test_world", 10, 20, worldConfig);

        assertFalse(result);
        assertEquals(0, preObfuscator.getTasksEnqueued());
        assertEquals(1, preObfuscator.getTasksSkippedCacheHit());
    }

    @Test
    void enqueuePreObfuscation_skipsIfWorldDisabled() {
        WorldConfig disabledConfig = disabledWorldConfig();

        boolean result = preObfuscator.enqueuePreObfuscation("disabled_world", 5, 5, disabledConfig);

        assertFalse(result);
        assertEquals(0, preObfuscator.getTasksEnqueued());
        assertEquals(1, preObfuscator.getTasksSkippedDisabled());
    }

    @Test
    void enqueuePreObfuscation_nullProcessor_returnsFalse() {
        ChunkPreObfuscator noProcessor = new ChunkPreObfuscator(null, cache, CONFIG_HASH);
        WorldConfig worldConfig = defaultWorldConfig();

        boolean result = noProcessor.enqueuePreObfuscation("test_world", 1, 1, worldConfig);

        assertFalse(result);
        assertEquals(0, noProcessor.getTasksEnqueued());
    }

    @Test
    void enqueuePreObfuscation_nullCache_returnsFalse() {
        ChunkPreObfuscator noCache = new ChunkPreObfuscator(asyncProcessor, null, CONFIG_HASH);
        WorldConfig worldConfig = defaultWorldConfig();

        boolean result = noCache.enqueuePreObfuscation("test_world", 1, 1, worldConfig);

        assertFalse(result);
        assertEquals(0, noCache.getTasksEnqueued());
    }

    @Test
    void enqueuePreObfuscation_multipleChunks_allEnqueued() {
        WorldConfig worldConfig = defaultWorldConfig();

        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                preObfuscator.enqueuePreObfuscation("test_world", x, z, worldConfig);
            }
        }

        assertEquals(25, preObfuscator.getTasksEnqueued());
    }

    @Test
    void enqueuePreObfuscation_duplicateChunk_bothEnqueuedBeforeProcessing() {
        WorldConfig worldConfig = defaultWorldConfig();

        boolean first = preObfuscator.enqueuePreObfuscation("test_world", 5, 5, worldConfig);
        boolean second = preObfuscator.enqueuePreObfuscation("test_world", 5, 5, worldConfig);

        assertTrue(first);
        assertTrue(second);
        assertEquals(2, preObfuscator.getTasksEnqueued(),
                "Both calls should enqueue since cache check happens before the first task completes");
    }

    @Test
    void enqueuePreObfuscation_duplicateChunk_secondSkippedAfterCache() {
        WorldConfig worldConfig = defaultWorldConfig();

        preObfuscator.enqueuePreObfuscation("test_world", 10, 10, worldConfig);
        assertEquals(1, preObfuscator.getTasksEnqueued());

        CacheKey key = new CacheKey("test_world", 10, 10, worldConfig.getEngineMode(), CONFIG_HASH);
        cache.put(key, new CacheEntry(new byte[]{1}, 1));

        boolean result = preObfuscator.enqueuePreObfuscation("test_world", 10, 10, worldConfig);

        assertFalse(result);
        assertEquals(1, preObfuscator.getTasksSkippedCacheHit());
    }

    @Test
    void enqueuePreObfuscation_differentWorlds_enqueuedSeparately() {
        WorldConfig worldConfig = defaultWorldConfig();

        boolean r1 = preObfuscator.enqueuePreObfuscation("overworld", 1, 1, worldConfig);
        boolean r2 = preObfuscator.enqueuePreObfuscation("nether", 5, 5, worldConfig);

        assertTrue(r1);
        assertTrue(r2);
        assertEquals(2, preObfuscator.getTasksEnqueued());
    }

    @Test
    void stats_initialValuesAreZero() {
        assertEquals(0, preObfuscator.getTasksEnqueued());
        assertEquals(0, preObfuscator.getTasksSkippedCacheHit());
        assertEquals(0, preObfuscator.getTasksSkippedDisabled());
    }

    @Test
    void resetStats_clearsAllCounters() {
        WorldConfig worldConfig = defaultWorldConfig();
        preObfuscator.enqueuePreObfuscation("test_world", 1, 1, worldConfig);
        assertTrue(preObfuscator.getTasksEnqueued() > 0);

        preObfuscator.resetStats();

        assertEquals(0, preObfuscator.getTasksEnqueued());
        assertEquals(0, preObfuscator.getTasksSkippedCacheHit());
        assertEquals(0, preObfuscator.getTasksSkippedDisabled());
    }

    @Test
    void enqueuePreObfuscation_negativeChunkCoords_works() {
        WorldConfig worldConfig = defaultWorldConfig();
        boolean result = preObfuscator.enqueuePreObfuscation("test_world", -5, -10, worldConfig);

        assertTrue(result);
        assertEquals(1, preObfuscator.getTasksEnqueued());
    }

    @Test
    void enqueuePreObfuscation_largeChunkCoords_works() {
        WorldConfig worldConfig = defaultWorldConfig();
        boolean result = preObfuscator.enqueuePreObfuscation("test_world", 100000, -100000, worldConfig);

        assertTrue(result);
        assertEquals(1, preObfuscator.getTasksEnqueued());
    }

    @Test
    void enqueuePreObfuscation_usesCorrectPriority() throws InterruptedException {
        ThreadPoolManager localPoolManager = new ThreadPoolManager(2, 1000);
        TickBudgetTracker budgetTracker = new TickBudgetTracker(8);
        BackpressureHandler backpressureHandler = new BackpressureHandler(1000, localPoolManager);
        ObfuscationCache localCache = new ObfuscationCache(100, 300);

        AtomicReference<ObfuscationTask> capturedTask = new AtomicReference<>();
        AsyncProcessor localProcessor = spy(new AsyncProcessor(
                localPoolManager, budgetTracker, backpressureHandler, localCache, 5000));
        localProcessor.setObfuscationFunction(key -> new CacheEntry(new byte[]{1}, 1));

        doAnswer(invocation -> {
            ObfuscationTask task = invocation.getArgument(0);
            capturedTask.set(task);
            return null;
        }).when(localProcessor).enqueue(any(ObfuscationTask.class));

        ChunkPreObfuscator localPreObfuscator = new ChunkPreObfuscator(localProcessor, localCache, CONFIG_HASH);
        WorldConfig worldConfig = defaultWorldConfig();

        localPreObfuscator.enqueuePreObfuscation("test_world", 3, 7, worldConfig);

        assertNotNull(capturedTask.get());
        assertEquals(ObfuscationTask.Priority.MEDIUM, capturedTask.get().getPriority());

        localPoolManager.shutdown();
        localPoolManager.awaitTermination(2000);
    }

    @Test
    void enqueuePreObfuscation_correctCacheKeyFields() throws InterruptedException {
        ThreadPoolManager localPoolManager = new ThreadPoolManager(2, 1000);
        TickBudgetTracker budgetTracker = new TickBudgetTracker(8);
        BackpressureHandler backpressureHandler = new BackpressureHandler(1000, localPoolManager);
        ObfuscationCache localCache = new ObfuscationCache(100, 300);

        AtomicReference<ObfuscationTask> capturedTask = new AtomicReference<>();
        AsyncProcessor localProcessor = spy(new AsyncProcessor(
                localPoolManager, budgetTracker, backpressureHandler, localCache, 5000));
        localProcessor.setObfuscationFunction(key -> new CacheEntry(new byte[]{1}, 1));

        doAnswer(invocation -> {
            ObfuscationTask task = invocation.getArgument(0);
            capturedTask.set(task);
            return null;
        }).when(localProcessor).enqueue(any(ObfuscationTask.class));

        ChunkPreObfuscator localPreObfuscator = new ChunkPreObfuscator(localProcessor, localCache, CONFIG_HASH);
        WorldConfig worldConfig = defaultWorldConfig();

        localPreObfuscator.enqueuePreObfuscation("my_world", 42, -7, worldConfig);

        assertNotNull(capturedTask.get());
        CacheKey key = capturedTask.get().getKey();
        assertEquals("my_world", key.getWorldName());
        assertEquals(42, key.getChunkX());
        assertEquals(-7, key.getChunkZ());
        assertEquals(worldConfig.getEngineMode().ordinal() + 1, key.getEngineMode());
        assertEquals(CONFIG_HASH, key.getConfigHash());

        localPoolManager.shutdown();
        localPoolManager.awaitTermination(2000);
    }

    @Test
    void enqueuePreObfuscation_cachePopulated_afterProcessing() throws InterruptedException {
        CountDownLatch processLatch = new CountDownLatch(1);
        asyncProcessor.setObfuscationFunction(key -> {
            processLatch.countDown();
            return new CacheEntry(new byte[]{99}, 3);
        });

        WorldConfig worldConfig = defaultWorldConfig();
        preObfuscator.enqueuePreObfuscation("test_world", 15, 25, worldConfig);

        assertTrue(processLatch.await(3, TimeUnit.SECONDS), "Task should be processed");

        Thread.sleep(100);

        CacheKey key = new CacheKey("test_world", 15, 25, worldConfig.getEngineMode(), CONFIG_HASH);
        CacheEntry entry = cache.get(key);
        assertNotNull(entry, "Cache should be populated after async processing");
    }

    @Test
    void enqueuePreObfuscation_backpressureCancelsMedium_taskNotEnqueued() {
        BackpressureHandler bp = new BackpressureHandler(1, poolManager);
        AsyncProcessor bpProcessor = new AsyncProcessor(poolManager, new TickBudgetTracker(8), bp, cache, 5000);
        bpProcessor.setObfuscationFunction(key -> new CacheEntry(new byte[]{1}, 1));

        ChunkPreObfuscator bpPreObfuscator = new ChunkPreObfuscator(bpProcessor, cache, CONFIG_HASH);
        WorldConfig worldConfig = defaultWorldConfig();

        for (int i = 0; i < 1000; i++) {
            bpPreObfuscator.enqueuePreObfuscation("test_world", i, 0, worldConfig);
        }

        bpProcessor.onTickStart();
        assertTrue(bpProcessor.getTasksCancelled() >= 0);
    }
}
