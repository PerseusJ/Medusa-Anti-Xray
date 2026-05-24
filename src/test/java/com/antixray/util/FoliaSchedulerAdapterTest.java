package com.antixray.util;

import com.antixray.AntiXrayPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FoliaSchedulerAdapterTest {

    private AntiXrayPlugin plugin;
    private Server server;
    private BukkitScheduler scheduler;
    private Server oldServer;

    @BeforeEach
    void setUp() throws Exception {
        java.lang.reflect.Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        oldServer = (Server) serverField.get(null);

        server = mock(Server.class);
        serverField.set(null, server);

        scheduler = mock(BukkitScheduler.class);
        when(server.getScheduler()).thenReturn(scheduler);

        plugin = mock(AntiXrayPlugin.class);
        when(plugin.getName()).thenReturn("AntiXray");
        when(plugin.getLogger()).thenReturn(Logger.getLogger("AntiXrayTestLogger"));
    }

    @AfterEach
    void tearDown() throws Exception {
        java.lang.reflect.Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(null, oldServer);
        VersionUtil.resetCache();
    }

    @Test
    void isFoliaReturnsFalseOnSpigot() {
        VersionUtil.resetCache();
        FoliaSchedulerAdapter adapter = new FoliaSchedulerAdapter(plugin);
        assertFalse(adapter.isFolia());
    }

    @Test
    void executeOnMainUsesBukkitSchedulerOnSpigot() {
        BukkitTask task = mock(BukkitTask.class);
        when(task.getTaskId()).thenReturn(42);
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenReturn(task);

        FoliaSchedulerAdapter adapter = new FoliaSchedulerAdapter(plugin);
        Runnable dummy = () -> {};
        int id = adapter.executeOnMain(dummy);

        verify(scheduler, times(1)).runTask(eq(plugin), any(Runnable.class));
        assertEquals(42, id);
    }

    @Test
    void runTaskLaterUsesBukkitSchedulerOnSpigot() {
        BukkitTask task = mock(BukkitTask.class);
        when(task.getTaskId()).thenReturn(10);
        when(scheduler.runTaskLater(eq(plugin), any(Runnable.class), eq(5L))).thenReturn(task);

        FoliaSchedulerAdapter adapter = new FoliaSchedulerAdapter(plugin);
        int id = adapter.runTaskLater(() -> {}, 5L);

        verify(scheduler, times(1)).runTaskLater(eq(plugin), any(Runnable.class), eq(5L));
        assertEquals(10, id);
    }

    @Test
    void runTaskTimerUsesBukkitSchedulerOnSpigot() {
        BukkitTask task = mock(BukkitTask.class);
        when(task.getTaskId()).thenReturn(20);
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(1L), eq(1L))).thenReturn(task);

        FoliaSchedulerAdapter adapter = new FoliaSchedulerAdapter(plugin);
        int id = adapter.runTaskTimer(() -> {}, 1L, 1L);

        verify(scheduler, times(1)).runTaskTimer(eq(plugin), any(Runnable.class), eq(1L), eq(1L));
        assertEquals(20, id);
    }

    @Test
    void runAsyncUsesBukkitSchedulerOnSpigot() {
        FoliaSchedulerAdapter adapter = new FoliaSchedulerAdapter(plugin);
        Runnable dummy = () -> {};
        adapter.runAsync(dummy);

        verify(scheduler, times(1)).runTaskAsynchronously(eq(plugin), any(Runnable.class));
    }

    @Test
    void runAsyncTimerUsesBukkitSchedulerOnSpigot() {
        BukkitTask task = mock(BukkitTask.class);
        when(task.getTaskId()).thenReturn(30);
        when(scheduler.runTaskTimerAsynchronously(eq(plugin), any(Runnable.class), eq(60L), eq(60L))).thenReturn(task);

        FoliaSchedulerAdapter adapter = new FoliaSchedulerAdapter(plugin);
        int id = adapter.runAsyncTimer(() -> {}, 60L, 60L);

        verify(scheduler, times(1)).runTaskTimerAsynchronously(eq(plugin), any(Runnable.class), eq(60L), eq(60L));
        assertEquals(30, id);
    }

    @Test
    void cancelTaskDelegatesToBukkitSchedulerOnSpigot() {
        FoliaSchedulerAdapter adapter = new FoliaSchedulerAdapter(plugin);
        adapter.cancelTask(42);

        verify(scheduler, times(1)).cancelTask(42);
    }

    @Test
    void cancelTaskWithMinusOneIsNoOp() {
        FoliaSchedulerAdapter adapter = new FoliaSchedulerAdapter(plugin);
        adapter.cancelTask(-1);

        verify(scheduler, never()).cancelTask(anyInt());
    }

    @Test
    void executeForEntityFallsBackToBukkitOnSpigot() {
        BukkitTask task = mock(BukkitTask.class);
        when(task.getTaskId()).thenReturn(55);
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenReturn(task);

        FoliaSchedulerAdapter adapter = new FoliaSchedulerAdapter(plugin);
        org.bukkit.entity.Entity entity = mock(org.bukkit.entity.Entity.class);
        int id = adapter.executeForEntity(entity, () -> {});

        verify(scheduler, times(1)).runTask(eq(plugin), any(Runnable.class));
        assertEquals(55, id);
    }

    @Test
    void executeForEntityNullFallsBackToMain() {
        BukkitTask task = mock(BukkitTask.class);
        when(task.getTaskId()).thenReturn(56);
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenReturn(task);

        FoliaSchedulerAdapter adapter = new FoliaSchedulerAdapter(plugin);
        int id = adapter.executeForEntity(null, () -> {});

        verify(scheduler, times(1)).runTask(eq(plugin), any(Runnable.class));
        assertEquals(56, id);
    }

    @Test
    void runTaskLaterForEntityNullFallsBackToRunTaskLater() {
        BukkitTask task = mock(BukkitTask.class);
        when(task.getTaskId()).thenReturn(57);
        when(scheduler.runTaskLater(eq(plugin), any(Runnable.class), eq(10L))).thenReturn(task);

        FoliaSchedulerAdapter adapter = new FoliaSchedulerAdapter(plugin);
        int id = adapter.runTaskLaterForEntity(null, () -> {}, 10L);

        verify(scheduler, times(1)).runTaskLater(eq(plugin), any(Runnable.class), eq(10L));
        assertEquals(57, id);
    }

    @Test
    void cancelAllTasksCleansUpActiveTasks() {
        BukkitTask task = mock(BukkitTask.class);
        when(task.getTaskId()).thenReturn(100);
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(1L), eq(1L))).thenReturn(task);

        FoliaSchedulerAdapter adapter = new FoliaSchedulerAdapter(plugin);
        adapter.runTaskTimer(() -> {}, 1L, 1L);

        adapter.cancelAllTasks();

        // After cancelAllTasks, subsequent cancelTask should not throw
        assertDoesNotThrow(() -> adapter.cancelTask(100));
    }

    @Test
    void executeOnMainAtLocationFallsBackToBukkitOnSpigot() {
        BukkitTask task = mock(BukkitTask.class);
        when(task.getTaskId()).thenReturn(77);
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenReturn(task);

        FoliaSchedulerAdapter adapter = new FoliaSchedulerAdapter(plugin);
        org.bukkit.World world = mock(org.bukkit.World.class);
        org.bukkit.Location loc = new org.bukkit.Location(world, 16, 64, 32);
        int id = adapter.executeOnMainAtLocation(loc, () -> {});

        verify(scheduler, times(1)).runTask(eq(plugin), any(Runnable.class));
        assertEquals(77, id);
    }

    @Test
    void runTaskTimerForEntityNullFallsBackToRunTaskTimer() {
        BukkitTask task = mock(BukkitTask.class);
        when(task.getTaskId()).thenReturn(88);
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(1L), eq(2L))).thenReturn(task);

        FoliaSchedulerAdapter adapter = new FoliaSchedulerAdapter(plugin);
        int id = adapter.runTaskTimerForEntity(null, () -> {}, 1L, 2L);

        verify(scheduler, times(1)).runTaskTimer(eq(plugin), any(Runnable.class), eq(1L), eq(2L));
        assertEquals(88, id);
    }

    interface FoliaServer extends Server {
        io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler getGlobalRegionScheduler();
        io.papermc.paper.threadedregions.scheduler.RegionScheduler getRegionScheduler();
        io.papermc.paper.threadedregions.scheduler.AsyncScheduler getAsyncScheduler();
    }

    interface FoliaEntity extends org.bukkit.entity.Entity {
        io.papermc.paper.threadedregions.scheduler.EntityScheduler getScheduler();
    }

    private void enableFoliaMode(FoliaServer foliaServer) throws Exception {
        java.lang.reflect.Field foliaField = VersionUtil.class.getDeclaredField("cachedIsFolia");
        foliaField.setAccessible(true);
        foliaField.set(null, Boolean.TRUE);

        java.lang.reflect.Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(null, foliaServer);

        when(foliaServer.getScheduler()).thenReturn(scheduler);
    }

    @Test
    void executeOnMainUsesFoliaGlobalScheduler() throws Exception {
        FoliaServer foliaServer = mock(FoliaServer.class);
        enableFoliaMode(foliaServer);

        io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler globalScheduler = 
                mock(io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler.class);
        when(foliaServer.getGlobalRegionScheduler()).thenReturn(globalScheduler);

        FoliaSchedulerAdapter adapter = new FoliaSchedulerAdapter(plugin);
        assertTrue(adapter.isFolia());

        Runnable dummy = mock(Runnable.class);
        adapter.executeOnMain(dummy);

        verify(globalScheduler, times(1)).execute(eq(plugin), eq(dummy));
    }

    @Test
    void executeOnMainAtLocationUsesFoliaRegionScheduler() throws Exception {
        FoliaServer foliaServer = mock(FoliaServer.class);
        enableFoliaMode(foliaServer);

        io.papermc.paper.threadedregions.scheduler.RegionScheduler regionScheduler = 
                mock(io.papermc.paper.threadedregions.scheduler.RegionScheduler.class);
        when(foliaServer.getRegionScheduler()).thenReturn(regionScheduler);

        FoliaSchedulerAdapter adapter = new FoliaSchedulerAdapter(plugin);
        org.bukkit.World world = mock(org.bukkit.World.class);
        org.bukkit.Location loc = new org.bukkit.Location(world, 100, 64, 200);

        Runnable dummy = mock(Runnable.class);
        adapter.executeOnMainAtLocation(loc, dummy);

        verify(regionScheduler, times(1)).execute(eq(plugin), eq(loc), eq(dummy));
    }

    @Test
    void executeForEntityUsesFoliaEntityScheduler() throws Exception {
        FoliaServer foliaServer = mock(FoliaServer.class);
        enableFoliaMode(foliaServer);

        FoliaEntity entity = mock(FoliaEntity.class);
        io.papermc.paper.threadedregions.scheduler.EntityScheduler entityScheduler = 
                mock(io.papermc.paper.threadedregions.scheduler.EntityScheduler.class);
        when(entity.getScheduler()).thenReturn(entityScheduler);

        FoliaSchedulerAdapter adapter = new FoliaSchedulerAdapter(plugin);
        Runnable dummy = mock(Runnable.class);
        adapter.executeForEntity(entity, dummy);

        verify(entityScheduler, times(1)).execute(eq(plugin), eq(dummy), eq(null));
    }

    @Test
    void runTaskLaterUsesFoliaGlobalScheduler() throws Exception {
        FoliaServer foliaServer = mock(FoliaServer.class);
        enableFoliaMode(foliaServer);

        io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler globalScheduler = 
                mock(io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler.class);
        when(foliaServer.getGlobalRegionScheduler()).thenReturn(globalScheduler);

        io.papermc.paper.threadedregions.scheduler.ScheduledTask scheduledTask = 
                mock(io.papermc.paper.threadedregions.scheduler.ScheduledTask.class);
        when(globalScheduler.runDelayed(eq(plugin), any(), eq(10L))).thenReturn(scheduledTask);

        FoliaSchedulerAdapter adapter = new FoliaSchedulerAdapter(plugin);
        int taskId = adapter.runTaskLater(() -> {}, 10L);

        assertTrue(taskId > 0);
        verify(globalScheduler, times(1)).runDelayed(eq(plugin), any(), eq(10L));
    }

    @Test
    void runTaskLaterForEntityUsesFoliaEntityScheduler() throws Exception {
        FoliaServer foliaServer = mock(FoliaServer.class);
        enableFoliaMode(foliaServer);

        FoliaEntity entity = mock(FoliaEntity.class);
        io.papermc.paper.threadedregions.scheduler.EntityScheduler entityScheduler = 
                mock(io.papermc.paper.threadedregions.scheduler.EntityScheduler.class);
        when(entity.getScheduler()).thenReturn(entityScheduler);

        io.papermc.paper.threadedregions.scheduler.ScheduledTask scheduledTask = 
                mock(io.papermc.paper.threadedregions.scheduler.ScheduledTask.class);
        when(entityScheduler.runDelayed(eq(plugin), any(), eq(null), eq(20L))).thenReturn(scheduledTask);

        FoliaSchedulerAdapter adapter = new FoliaSchedulerAdapter(plugin);
        int taskId = adapter.runTaskLaterForEntity(entity, () -> {}, 20L);

        assertTrue(taskId > 0);
        verify(entityScheduler, times(1)).runDelayed(eq(plugin), any(), eq(null), eq(20L));
    }

    @Test
    void runTaskTimerUsesFoliaGlobalScheduler() throws Exception {
        FoliaServer foliaServer = mock(FoliaServer.class);
        enableFoliaMode(foliaServer);

        io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler globalScheduler = 
                mock(io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler.class);
        when(foliaServer.getGlobalRegionScheduler()).thenReturn(globalScheduler);

        io.papermc.paper.threadedregions.scheduler.ScheduledTask scheduledTask = 
                mock(io.papermc.paper.threadedregions.scheduler.ScheduledTask.class);
        when(globalScheduler.runAtFixedRate(eq(plugin), any(), eq(5L), eq(10L))).thenReturn(scheduledTask);

        FoliaSchedulerAdapter adapter = new FoliaSchedulerAdapter(plugin);
        int taskId = adapter.runTaskTimer(() -> {}, 5L, 10L);

        assertTrue(taskId > 0);
        verify(globalScheduler, times(1)).runAtFixedRate(eq(plugin), any(), eq(5L), eq(10L));
    }

    @Test
    void runTaskTimerForEntityUsesFoliaEntityScheduler() throws Exception {
        FoliaServer foliaServer = mock(FoliaServer.class);
        enableFoliaMode(foliaServer);

        FoliaEntity entity = mock(FoliaEntity.class);
        io.papermc.paper.threadedregions.scheduler.EntityScheduler entityScheduler = 
                mock(io.papermc.paper.threadedregions.scheduler.EntityScheduler.class);
        when(entity.getScheduler()).thenReturn(entityScheduler);

        io.papermc.paper.threadedregions.scheduler.ScheduledTask scheduledTask = 
                mock(io.papermc.paper.threadedregions.scheduler.ScheduledTask.class);
        when(entityScheduler.runAtFixedRate(eq(plugin), any(), eq(null), eq(5L), eq(10L))).thenReturn(scheduledTask);

        FoliaSchedulerAdapter adapter = new FoliaSchedulerAdapter(plugin);
        int taskId = adapter.runTaskTimerForEntity(entity, () -> {}, 5L, 10L);

        assertTrue(taskId > 0);
        verify(entityScheduler, times(1)).runAtFixedRate(eq(plugin), any(), eq(null), eq(5L), eq(10L));
    }

    @Test
    void runTaskTimerAtLocationUsesFoliaRegionScheduler() throws Exception {
        FoliaServer foliaServer = mock(FoliaServer.class);
        enableFoliaMode(foliaServer);

        io.papermc.paper.threadedregions.scheduler.RegionScheduler regionScheduler = 
                mock(io.papermc.paper.threadedregions.scheduler.RegionScheduler.class);
        when(foliaServer.getRegionScheduler()).thenReturn(regionScheduler);

        io.papermc.paper.threadedregions.scheduler.ScheduledTask scheduledTask = 
                mock(io.papermc.paper.threadedregions.scheduler.ScheduledTask.class);
        org.bukkit.World world = mock(org.bukkit.World.class);
        org.bukkit.Location loc = new org.bukkit.Location(world, 16, 64, 32);

        when(regionScheduler.runAtFixedRate(eq(plugin), eq(loc), any(), eq(1L), eq(2L))).thenReturn(scheduledTask);

        FoliaSchedulerAdapter adapter = new FoliaSchedulerAdapter(plugin);
        adapter.runTaskTimerAtLocation(loc, () -> {}, 1L, 2L);

        verify(regionScheduler, times(1)).runAtFixedRate(eq(plugin), eq(loc), any(), eq(1L), eq(2L));
    }

    @Test
    void runAsyncUsesFoliaAsyncScheduler() throws Exception {
        FoliaServer foliaServer = mock(FoliaServer.class);
        enableFoliaMode(foliaServer);

        io.papermc.paper.threadedregions.scheduler.AsyncScheduler asyncScheduler = 
                mock(io.papermc.paper.threadedregions.scheduler.AsyncScheduler.class);
        when(foliaServer.getAsyncScheduler()).thenReturn(asyncScheduler);

        FoliaSchedulerAdapter adapter = new FoliaSchedulerAdapter(plugin);
        adapter.runAsync(() -> {});

        verify(asyncScheduler, times(1)).runNow(eq(plugin), any());
    }

    @Test
    void runAsyncTimerUsesFoliaAsyncScheduler() throws Exception {
        FoliaServer foliaServer = mock(FoliaServer.class);
        enableFoliaMode(foliaServer);

        io.papermc.paper.threadedregions.scheduler.AsyncScheduler asyncScheduler = 
                mock(io.papermc.paper.threadedregions.scheduler.AsyncScheduler.class);
        when(foliaServer.getAsyncScheduler()).thenReturn(asyncScheduler);

        io.papermc.paper.threadedregions.scheduler.ScheduledTask scheduledTask = 
                mock(io.papermc.paper.threadedregions.scheduler.ScheduledTask.class);
        when(asyncScheduler.runAtFixedRate(eq(plugin), any(), eq(50L), eq(100L), eq(java.util.concurrent.TimeUnit.MILLISECONDS))).thenReturn(scheduledTask);

        FoliaSchedulerAdapter adapter = new FoliaSchedulerAdapter(plugin);
        int taskId = adapter.runAsyncTimer(() -> {}, 1L, 2L); // 1 tick = 50ms, 2 ticks = 100ms

        assertTrue(taskId > 0);
        verify(asyncScheduler, times(1)).runAtFixedRate(eq(plugin), any(), eq(50L), eq(100L), eq(java.util.concurrent.TimeUnit.MILLISECONDS));
    }

    @Test
    void cancelTaskCancelsFoliaTask() throws Exception {
        FoliaServer foliaServer = mock(FoliaServer.class);
        enableFoliaMode(foliaServer);

        io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler globalScheduler =
            mock(io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler.class);
        when(foliaServer.getGlobalRegionScheduler()).thenReturn(globalScheduler);

        io.papermc.paper.threadedregions.scheduler.ScheduledTask scheduledTask =
            mock(io.papermc.paper.threadedregions.scheduler.ScheduledTask.class);
        when(globalScheduler.runDelayed(eq(plugin), any(), eq(10L))).thenReturn(scheduledTask);

        FoliaSchedulerAdapter adapter = new FoliaSchedulerAdapter(plugin);
        int taskId = adapter.runTaskLater(() -> {}, 10L);

        adapter.cancelTask(taskId);

        verify(scheduledTask, times(1)).cancel();
    }

    @Test
    void isGlobalRegionThreadReturnsTrueOnPrimaryThreadSpigot() {
        when(server.isPrimaryThread()).thenReturn(true);
        FoliaSchedulerAdapter adapter = new FoliaSchedulerAdapter(plugin);
        assertTrue(adapter.isGlobalRegionThread());
    }

    @Test
    void isGlobalRegionThreadReturnsFalseOffPrimaryThreadSpigot() {
        when(server.isPrimaryThread()).thenReturn(false);
        FoliaSchedulerAdapter adapter = new FoliaSchedulerAdapter(plugin);
        assertFalse(adapter.isGlobalRegionThread());
    }

    @Test
    void isGlobalRegionThreadOnFoliaChecksThreadName() throws Exception {
        FoliaServer foliaServer = mock(FoliaServer.class);
        enableFoliaMode(foliaServer);
        when(foliaServer.getScheduler()).thenReturn(scheduler);

        FoliaSchedulerAdapter adapter = new FoliaSchedulerAdapter(plugin);
        String currentThreadName = Thread.currentThread().getName();
        boolean expected = currentThreadName.startsWith("Region Scheduler")
            || currentThreadName.startsWith("Global Region");
        assertEquals(expected, adapter.isGlobalRegionThread());
    }

    @Test
    void executeOnMainForceScheduleOnSpigot() {
        BukkitTask task = mock(BukkitTask.class);
        when(task.getTaskId()).thenReturn(99);
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenReturn(task);
        when(server.isPrimaryThread()).thenReturn(true);

        FoliaSchedulerAdapter adapter = new FoliaSchedulerAdapter(plugin);
        int id = adapter.executeOnMain(() -> {}, true);

        verify(scheduler, times(1)).runTask(eq(plugin), any(Runnable.class));
        assertEquals(99, id);
    }

    @Test
    void executeOnMainForceScheduleFalseOnPrimaryThreadRunsInline() {
        when(server.isPrimaryThread()).thenReturn(true);

        FoliaSchedulerAdapter adapter = new FoliaSchedulerAdapter(plugin);
        Runnable dummy = mock(Runnable.class);
        int id = adapter.executeOnMain(dummy, false);

        verify(dummy, times(1)).run();
        verify(scheduler, never()).runTask(any(), any(Runnable.class));
        assertEquals(-1, id);
    }

    @Test
    void executeOnMainForceScheduleFalseOffPrimaryThreadSchedules() {
        BukkitTask task = mock(BukkitTask.class);
        when(task.getTaskId()).thenReturn(88);
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenReturn(task);
        when(server.isPrimaryThread()).thenReturn(false);

        FoliaSchedulerAdapter adapter = new FoliaSchedulerAdapter(plugin);
        int id = adapter.executeOnMain(() -> {}, false);

        verify(scheduler, times(1)).runTask(eq(plugin), any(Runnable.class));
        assertEquals(88, id);
    }

    @Test
    void executeOnMainAtLocationNullLocationFallsBackOnFolia() throws Exception {
        FoliaServer foliaServer = mock(FoliaServer.class);
        enableFoliaMode(foliaServer);

        io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler globalScheduler =
            mock(io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler.class);
        when(foliaServer.getGlobalRegionScheduler()).thenReturn(globalScheduler);

        FoliaSchedulerAdapter adapter = new FoliaSchedulerAdapter(plugin);
        Runnable dummy = mock(Runnable.class);
        adapter.executeOnMainAtLocation(null, dummy);

        verify(globalScheduler, times(1)).execute(eq(plugin), eq(dummy));
    }

    @Test
    void executeOnMainAtLocationNullWorldFallsBackOnFolia() throws Exception {
        FoliaServer foliaServer = mock(FoliaServer.class);
        enableFoliaMode(foliaServer);

        io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler globalScheduler =
            mock(io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler.class);
        when(foliaServer.getGlobalRegionScheduler()).thenReturn(globalScheduler);

        FoliaSchedulerAdapter adapter = new FoliaSchedulerAdapter(plugin);
        org.bukkit.Location loc = new org.bukkit.Location(null, 10, 64, 20);
        Runnable dummy = mock(Runnable.class);
        adapter.executeOnMainAtLocation(loc, dummy);

        verify(globalScheduler, times(1)).execute(eq(plugin), eq(dummy));
    }

    @Test
    void runTaskLaterForEntityNullFallsBackToGlobalOnFolia() throws Exception {
        FoliaServer foliaServer = mock(FoliaServer.class);
        enableFoliaMode(foliaServer);

        io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler globalScheduler =
            mock(io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler.class);
        when(foliaServer.getGlobalRegionScheduler()).thenReturn(globalScheduler);

        io.papermc.paper.threadedregions.scheduler.ScheduledTask scheduledTask =
            mock(io.papermc.paper.threadedregions.scheduler.ScheduledTask.class);
        when(globalScheduler.runDelayed(eq(plugin), any(), eq(20L))).thenReturn(scheduledTask);

        FoliaSchedulerAdapter adapter = new FoliaSchedulerAdapter(plugin);
        int taskId = adapter.runTaskLaterForEntity(null, () -> {}, 20L);

        assertTrue(taskId > 0);
        verify(globalScheduler, times(1)).runDelayed(eq(plugin), any(), eq(20L));
    }

    @Test
    void cancelAllTasksCleansUpMultipleActiveTasksOnFolia() throws Exception {
        FoliaServer foliaServer = mock(FoliaServer.class);
        enableFoliaMode(foliaServer);

        io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler globalScheduler =
            mock(io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler.class);
        when(foliaServer.getGlobalRegionScheduler()).thenReturn(globalScheduler);

        io.papermc.paper.threadedregions.scheduler.ScheduledTask task1 =
            mock(io.papermc.paper.threadedregions.scheduler.ScheduledTask.class);
        io.papermc.paper.threadedregions.scheduler.ScheduledTask task2 =
            mock(io.papermc.paper.threadedregions.scheduler.ScheduledTask.class);
        when(globalScheduler.runDelayed(eq(plugin), any(), eq(10L))).thenReturn(task1);
        when(globalScheduler.runDelayed(eq(plugin), any(), eq(20L))).thenReturn(task2);

        FoliaSchedulerAdapter adapter = new FoliaSchedulerAdapter(plugin);
        adapter.runTaskLater(() -> {}, 10L);
        adapter.runTaskLater(() -> {}, 20L);

        adapter.cancelAllTasks();

        verify(task1, times(1)).cancel();
        verify(task2, times(1)).cancel();
    }

    @Test
    void runAsyncOnFoliaFallsBackOnException() throws Exception {
        FoliaServer foliaServer = mock(FoliaServer.class);
        enableFoliaMode(foliaServer);

        when(foliaServer.getAsyncScheduler()).thenThrow(new RuntimeException("not available"));

        FoliaSchedulerAdapter adapter = new FoliaSchedulerAdapter(plugin);
        Runnable task = mock(Runnable.class);
        adapter.runAsync(task);

        verify(task, times(1)).run();
    }

    @Test
    void executeForEntityOnFoliaFallsBackOnException() throws Exception {
        FoliaServer foliaServer = mock(FoliaServer.class);
        enableFoliaMode(foliaServer);

        io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler globalScheduler =
            mock(io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler.class);
        when(foliaServer.getGlobalRegionScheduler()).thenReturn(globalScheduler);

        FoliaEntity entity = mock(FoliaEntity.class);
        when(entity.getScheduler()).thenThrow(new RuntimeException("entity scheduler failed"));

        FoliaSchedulerAdapter adapter = new FoliaSchedulerAdapter(plugin);
        Runnable dummy = mock(Runnable.class);
        adapter.executeForEntity(entity, dummy);

        verify(globalScheduler, times(1)).execute(eq(plugin), eq(dummy));
    }
}
