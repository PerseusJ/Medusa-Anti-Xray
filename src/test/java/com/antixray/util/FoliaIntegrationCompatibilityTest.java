package com.antixray.util;

import com.antixray.AntiXrayPlugin;
import com.antixray.config.ConfigurationManager;
import com.antixray.config.WorldConfig;
import com.antixray.deobfuscation.DeobfuscationManager;
import com.antixray.deobfuscation.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FoliaIntegrationCompatibilityTest {

    private AntiXrayPlugin plugin;
    private Server server;
    private BukkitScheduler scheduler;
    private Server oldServer;

    interface FoliaServer extends Server {
        io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler getGlobalRegionScheduler();
        io.papermc.paper.threadedregions.scheduler.RegionScheduler getRegionScheduler();
        io.papermc.paper.threadedregions.scheduler.AsyncScheduler getAsyncScheduler();
    }

    interface FoliaEntity extends org.bukkit.entity.Entity {
        io.papermc.paper.threadedregions.scheduler.EntityScheduler getScheduler();
    }

    @BeforeEach
    void setUp() throws Exception {
        Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        oldServer = (Server) serverField.get(null);

        server = mock(Server.class);
        serverField.set(null, server);

        scheduler = mock(BukkitScheduler.class);
        when(server.getScheduler()).thenReturn(scheduler);

        plugin = mock(AntiXrayPlugin.class);
        when(plugin.getName()).thenReturn("AntiXray");
        when(plugin.getLogger()).thenReturn(Logger.getLogger("AntiXrayTest"));
    }

    @AfterEach
    void tearDown() throws Exception {
        Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(null, oldServer);
        VersionUtil.resetCache();
        FoliaCompat.markExperimental(false);
    }

    private FoliaServer setupFoliaServer() throws Exception {
        FoliaServer foliaServer = mock(FoliaServer.class);
        when(foliaServer.getScheduler()).thenReturn(scheduler);

        Field foliaField = VersionUtil.class.getDeclaredField("cachedIsFolia");
        foliaField.setAccessible(true);
        foliaField.set(null, Boolean.TRUE);

        Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(null, foliaServer);

        return foliaServer;
    }

    @Test
    void foliaDetectionTriggersExperimentalWarning() throws Exception {
        FoliaServer foliaServer = setupFoliaServer();
        FoliaSchedulerAdapter adapter = new FoliaSchedulerAdapter(plugin);
        assertTrue(adapter.isFolia());
        assertTrue(FoliaCompat.isFolia());
        assertEquals("SUPPORTED", FoliaCompat.getFoliaStatus());
    }

    @Test
    void foliaExperimentalFlagCanBeSet() throws Exception {
        setupFoliaServer();
        FoliaCompat.markExperimental(true);
        assertEquals("EXPERIMENTAL", FoliaCompat.getFoliaStatus());
        assertTrue(FoliaCompat.isMarkedExperimental());
    }

    @Test
    void entitySchedulerUsedForPlayerDelayedTasksOnFolia() throws Exception {
        FoliaServer foliaServer = setupFoliaServer();

        io.papermc.paper.threadedregions.scheduler.EntityScheduler entityScheduler =
            mock(io.papermc.paper.threadedregions.scheduler.EntityScheduler.class);
        io.papermc.paper.threadedregions.scheduler.ScheduledTask scheduledTask =
            mock(io.papermc.paper.threadedregions.scheduler.ScheduledTask.class);

        FoliaEntity player = mock(FoliaEntity.class);
        when(player.getScheduler()).thenReturn(entityScheduler);
        when(entityScheduler.runDelayed(eq(plugin), any(), eq(null), eq(20L)))
            .thenReturn(scheduledTask);

        FoliaSchedulerAdapter adapter = new FoliaSchedulerAdapter(plugin);
        int taskId = adapter.runTaskLaterForEntity(player, () -> {}, 20L);

        assertTrue(taskId > 0);
        verify(entityScheduler, times(1)).runDelayed(eq(plugin), any(), eq(null), eq(20L));
        verify(scheduler, never()).runTaskLater(any(), any(Runnable.class), anyLong());
    }

    @Test
    void entitySchedulerUsedForPlayerTimerTasksOnFolia() throws Exception {
        FoliaServer foliaServer = setupFoliaServer();

        io.papermc.paper.threadedregions.scheduler.EntityScheduler entityScheduler =
            mock(io.papermc.paper.threadedregions.scheduler.EntityScheduler.class);
        io.papermc.paper.threadedregions.scheduler.ScheduledTask scheduledTask =
            mock(io.papermc.paper.threadedregions.scheduler.ScheduledTask.class);

        FoliaEntity player = mock(FoliaEntity.class);
        when(player.getScheduler()).thenReturn(entityScheduler);
        when(entityScheduler.runAtFixedRate(eq(plugin), any(), eq(null), eq(1L), eq(1L)))
            .thenReturn(scheduledTask);

        FoliaSchedulerAdapter adapter = new FoliaSchedulerAdapter(plugin);
        int taskId = adapter.runTaskTimerForEntity(player, () -> {}, 1L, 1L);

        assertTrue(taskId > 0);
        verify(entityScheduler, times(1)).runAtFixedRate(eq(plugin), any(), eq(null), eq(1L), eq(1L));
    }

    @Test
    void regionSchedulerUsedForLocationBasedTasksOnFolia() throws Exception {
        FoliaServer foliaServer = setupFoliaServer();

        io.papermc.paper.threadedregions.scheduler.RegionScheduler regionScheduler =
            mock(io.papermc.paper.threadedregions.scheduler.RegionScheduler.class);
        when(foliaServer.getRegionScheduler()).thenReturn(regionScheduler);

        io.papermc.paper.threadedregions.scheduler.ScheduledTask scheduledTask =
            mock(io.papermc.paper.threadedregions.scheduler.ScheduledTask.class);

        World world = mock(World.class);
        Location loc = new Location(world, 100, 64, 200);
        when(regionScheduler.runAtFixedRate(eq(plugin), eq(loc), any(), eq(1L), eq(5L)))
            .thenReturn(scheduledTask);

        FoliaSchedulerAdapter adapter = new FoliaSchedulerAdapter(plugin);
        adapter.runTaskTimerAtLocation(loc, () -> {}, 1L, 5L);

        verify(regionScheduler, times(1)).runAtFixedRate(eq(plugin), eq(loc), any(), eq(1L), eq(5L));
    }

    @Test
    void globalSchedulerUsedForGlobalDelayedTasksOnFolia() throws Exception {
        FoliaServer foliaServer = setupFoliaServer();

        io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler globalScheduler =
            mock(io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler.class);
        when(foliaServer.getGlobalRegionScheduler()).thenReturn(globalScheduler);

        io.papermc.paper.threadedregions.scheduler.ScheduledTask scheduledTask =
            mock(io.papermc.paper.threadedregions.scheduler.ScheduledTask.class);
        when(globalScheduler.runDelayed(eq(plugin), any(), eq(1L))).thenReturn(scheduledTask);

        FoliaSchedulerAdapter adapter = new FoliaSchedulerAdapter(plugin);
        int taskId = adapter.runTaskLater(() -> {}, 1L);

        assertTrue(taskId > 0);
        verify(globalScheduler, times(1)).runDelayed(eq(plugin), any(), eq(1L));
    }

    @Test
    void asyncSchedulerUsedForAsyncTasksOnFolia() throws Exception {
        FoliaServer foliaServer = setupFoliaServer();

        io.papermc.paper.threadedregions.scheduler.AsyncScheduler asyncScheduler =
            mock(io.papermc.paper.threadedregions.scheduler.AsyncScheduler.class);
        when(foliaServer.getAsyncScheduler()).thenReturn(asyncScheduler);

        FoliaSchedulerAdapter adapter = new FoliaSchedulerAdapter(plugin);
        adapter.runAsync(() -> {});

        verify(asyncScheduler, times(1)).runNow(eq(plugin), any());
    }

    @Test
    void asyncTimerSchedulerUsedForAsyncTimerTasksOnFolia() throws Exception {
        FoliaServer foliaServer = setupFoliaServer();

        io.papermc.paper.threadedregions.scheduler.AsyncScheduler asyncScheduler =
            mock(io.papermc.paper.threadedregions.scheduler.AsyncScheduler.class);
        when(foliaServer.getAsyncScheduler()).thenReturn(asyncScheduler);

        io.papermc.paper.threadedregions.scheduler.ScheduledTask scheduledTask =
            mock(io.papermc.paper.threadedregions.scheduler.ScheduledTask.class);
        when(asyncScheduler.runAtFixedRate(eq(plugin), any(), eq(50L), eq(50L),
            eq(java.util.concurrent.TimeUnit.MILLISECONDS))).thenReturn(scheduledTask);

        FoliaSchedulerAdapter adapter = new FoliaSchedulerAdapter(plugin);
        int taskId = adapter.runAsyncTimer(() -> {}, 1L, 1L);

        assertTrue(taskId > 0);
        verify(asyncScheduler, times(1)).runAtFixedRate(eq(plugin), any(), eq(50L), eq(50L),
            eq(java.util.concurrent.TimeUnit.MILLISECONDS));
    }

    @Test
    void taskCancellationThroughAdapterOnFolia() throws Exception {
        FoliaServer foliaServer = setupFoliaServer();

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
    void allLimitationsPresentOnFolia() throws Exception {
        setupFoliaServer();
        assertFalse(FoliaCompat.getKnownLimitations().isEmpty());
        assertFalse(FoliaCompat.getKnownLimitationDescriptions().isEmpty());

        assertTrue(FoliaCompat.getKnownLimitations().contains(FoliaCompat.LIMITATION_IS_PRIMARY_THREAD_KEY));
        assertTrue(FoliaCompat.getKnownLimitations().contains(FoliaCompat.LIMITATION_PLAYER_EVENT_SCHEDULING_KEY));
        assertTrue(FoliaCompat.getKnownLimitations().contains(FoliaCompat.LIMITATION_REGION_BASED_DEOBFUSCATION_KEY));
    }

    @Test
    void spigotSchedulerNotUsedOnFoliaForEntityTasks() throws Exception {
        FoliaServer foliaServer = setupFoliaServer();

        io.papermc.paper.threadedregions.scheduler.EntityScheduler entityScheduler =
            mock(io.papermc.paper.threadedregions.scheduler.EntityScheduler.class);
        io.papermc.paper.threadedregions.scheduler.ScheduledTask scheduledTask =
            mock(io.papermc.paper.threadedregions.scheduler.ScheduledTask.class);

        FoliaEntity player = mock(FoliaEntity.class);
        when(player.getScheduler()).thenReturn(entityScheduler);
        when(entityScheduler.execute(eq(plugin), any(), eq(null))).thenReturn(true);

        FoliaSchedulerAdapter adapter = new FoliaSchedulerAdapter(plugin);
        adapter.executeForEntity(player, () -> {});

        verify(entityScheduler, times(1)).execute(eq(plugin), any(), eq(null));
        verify(scheduler, never()).runTask(any(), any(Runnable.class));
    }

    @Test
    void foliaSchedulerAdapterConstructsCorrectlyOnFolia() throws Exception {
        setupFoliaServer();
        FoliaSchedulerAdapter adapter = new FoliaSchedulerAdapter(plugin);
        assertTrue(adapter.isFolia());
    }

    @Test
    void foliaSchedulerAdapterConstructsCorrectlyOnSpigot() {
        VersionUtil.resetCache();
        FoliaSchedulerAdapter adapter = new FoliaSchedulerAdapter(plugin);
        assertFalse(adapter.isFolia());
    }

    @Test
    void entityExecuteOnFoliaDelegatesCorrectly() throws Exception {
        FoliaServer foliaServer = setupFoliaServer();

        io.papermc.paper.threadedregions.scheduler.EntityScheduler entityScheduler =
            mock(io.papermc.paper.threadedregions.scheduler.EntityScheduler.class);
        FoliaEntity entity = mock(FoliaEntity.class);
        when(entity.getScheduler()).thenReturn(entityScheduler);
        when(entityScheduler.execute(eq(plugin), any(), eq(null))).thenReturn(true);

        FoliaSchedulerAdapter adapter = new FoliaSchedulerAdapter(plugin);
        Runnable task = mock(Runnable.class);
        adapter.executeForEntity(entity, task);

        verify(entityScheduler, times(1)).execute(eq(plugin), any(), eq(null));
    }

    @Test
    void nullEntityFallsBackToGlobalSchedulerOnFolia() throws Exception {
        FoliaServer foliaServer = setupFoliaServer();

        io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler globalScheduler =
            mock(io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler.class);
        when(foliaServer.getGlobalRegionScheduler()).thenReturn(globalScheduler);

        FoliaSchedulerAdapter adapter = new FoliaSchedulerAdapter(plugin);
        Runnable task = mock(Runnable.class);
        adapter.executeForEntity(null, task);

        verify(globalScheduler, times(1)).execute(eq(plugin), eq(task));
    }

    @Test
    void tickToMillisConversionCorrectForAsyncTimer() throws Exception {
        FoliaServer foliaServer = setupFoliaServer();

        io.papermc.paper.threadedregions.scheduler.AsyncScheduler asyncScheduler =
            mock(io.papermc.paper.threadedregions.scheduler.AsyncScheduler.class);
        when(foliaServer.getAsyncScheduler()).thenReturn(asyncScheduler);

        io.papermc.paper.threadedregions.scheduler.ScheduledTask scheduledTask =
            mock(io.papermc.paper.threadedregions.scheduler.ScheduledTask.class);
        when(asyncScheduler.runAtFixedRate(eq(plugin), any(), eq(100L), eq(200L),
            eq(java.util.concurrent.TimeUnit.MILLISECONDS))).thenReturn(scheduledTask);

        FoliaSchedulerAdapter adapter = new FoliaSchedulerAdapter(plugin);
        adapter.runAsyncTimer(() -> {}, 2L, 4L);

        verify(asyncScheduler, times(1)).runAtFixedRate(eq(plugin), any(), eq(100L), eq(200L),
            eq(java.util.concurrent.TimeUnit.MILLISECONDS));
    }
}
