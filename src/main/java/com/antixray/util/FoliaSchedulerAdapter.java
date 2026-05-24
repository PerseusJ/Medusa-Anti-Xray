package com.antixray.util;

import com.antixray.AntiXrayPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public final class FoliaSchedulerAdapter {

    private final AntiXrayPlugin plugin;
    private final boolean folia;
    private final List<ScheduledTaskHandle> activeTasks = new ArrayList<>();

    public FoliaSchedulerAdapter(AntiXrayPlugin plugin) {
        this.plugin = plugin;
        this.folia = VersionUtil.isFolia();
    }

    public boolean isFolia() {
        return folia;
    }

    public boolean isGlobalRegionThread() {
        if (!folia) {
            return Bukkit.isPrimaryThread();
        }
        try {
            Thread currentThread = Thread.currentThread();
            return currentThread.getName().startsWith("Region Scheduler") || currentThread.getName().startsWith("Global Region");
        } catch (Exception e) {
            return false;
        }
    }

    public int executeOnMain(Runnable task) {
        if (folia) {
            try {
                Object globalRegionScheduler = getGlobalRegionScheduler();
                Class<?> globalSchedulerClass = Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
                java.lang.reflect.Method executeMethod = globalSchedulerClass
                    .getMethod("execute", org.bukkit.plugin.Plugin.class, Runnable.class);
                executeMethod.invoke(globalRegionScheduler, plugin, task);
                return -1;
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to schedule task via Folia global region scheduler", e);
                return -1;
            }
        } else {
            if (Bukkit.isPrimaryThread()) {
                task.run();
                return -1;
            }
            return Bukkit.getScheduler().runTask(plugin, task).getTaskId();
        }
    }

    public int executeOnMain(Runnable task, boolean forceSchedule) {
        if (folia) {
            return executeOnMain(task);
        }
        if (forceSchedule || !Bukkit.isPrimaryThread()) {
            return Bukkit.getScheduler().runTask(plugin, task).getTaskId();
        }
        task.run();
        return -1;
    }

    public int executeOnMainAtLocation(Location location, Runnable task) {
        if (folia) {
            if (location == null || location.getWorld() == null) {
                return executeOnMain(task);
            }
            try {
                Object regionScheduler = getRegionScheduler();
                Class<?> regionSchedulerClass = Class.forName("io.papermc.paper.threadedregions.scheduler.RegionScheduler");
                java.lang.reflect.Method executeMethod = regionSchedulerClass
                    .getMethod("execute", org.bukkit.plugin.Plugin.class, Location.class, Runnable.class);
                executeMethod.invoke(regionScheduler, plugin, location, task);
                return -1;
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to schedule location task via Folia region scheduler", e);
                return executeOnMain(task);
            }
        } else {
            if (!Bukkit.isPrimaryThread()) {
                return Bukkit.getScheduler().runTask(plugin, task).getTaskId();
            }
            task.run();
            return -1;
        }
    }

    public int executeForEntity(Entity entity, Runnable task) {
        if (entity == null) {
            return executeOnMain(task);
        }
        if (folia) {
            try {
                java.lang.reflect.Method getSchedulerMethod = entity.getClass().getMethod("getScheduler");
                Object entityScheduler = getSchedulerMethod.invoke(entity);
                Class<?> entitySchedulerClass = Class.forName("io.papermc.paper.threadedregions.scheduler.EntityScheduler");
                java.lang.reflect.Method executeMethod = entitySchedulerClass
                    .getMethod("execute", org.bukkit.plugin.Plugin.class, Runnable.class, Runnable.class);
                executeMethod.invoke(entityScheduler, plugin, task, null);
                return -1;
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to schedule entity task via Folia entity scheduler", e);
                return executeOnMain(task);
            }
        } else {
            if (!Bukkit.isPrimaryThread()) {
                return Bukkit.getScheduler().runTask(plugin, task).getTaskId();
            }
            task.run();
            return -1;
        }
    }

    public int runTaskLater(Runnable task, long delayTicks) {
        if (folia) {
            try {
                Object globalRegionScheduler = getGlobalRegionScheduler();
                Class<?> globalSchedulerClass = Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
                java.lang.reflect.Method runDelayedMethod = globalSchedulerClass
                        .getMethod("runDelayed", org.bukkit.plugin.Plugin.class, java.util.function.Consumer.class, long.class);
                java.util.function.Consumer<Object> consumer = scheduledTask -> task.run();
                Object scheduledTask = runDelayedMethod.invoke(globalRegionScheduler, plugin, consumer, delayTicks);
                ScheduledTaskHandle handle = new ScheduledTaskHandle(scheduledTask, true);
                synchronized (activeTasks) {
                    activeTasks.add(handle);
                }
                return handle.getId();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to schedule delayed task via Folia global region scheduler", e);
                return -1;
            }
        } else {
            return Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks).getTaskId();
        }
    }

    public int runTaskLaterForEntity(Entity entity, Runnable task, long delayTicks) {
        if (entity == null) {
            return runTaskLater(task, delayTicks);
        }
        if (folia) {
            try {
                java.lang.reflect.Method getSchedulerMethod = entity.getClass().getMethod("getScheduler");
                Object entityScheduler = getSchedulerMethod.invoke(entity);
                Class<?> entitySchedulerClass = Class.forName("io.papermc.paper.threadedregions.scheduler.EntityScheduler");
                java.lang.reflect.Method runDelayedMethod = entitySchedulerClass
                        .getMethod("runDelayed", org.bukkit.plugin.Plugin.class, java.util.function.Consumer.class, Runnable.class, long.class);
                java.util.function.Consumer<Object> consumer = scheduledTask -> task.run();
                Object scheduledTask = runDelayedMethod.invoke(entityScheduler, plugin, consumer, null, delayTicks);
                ScheduledTaskHandle handle = new ScheduledTaskHandle(scheduledTask, true);
                synchronized (activeTasks) {
                    activeTasks.add(handle);
                }
                return handle.getId();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to schedule delayed entity task via Folia entity scheduler", e);
                return runTaskLater(task, delayTicks);
            }
        } else {
            return Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks).getTaskId();
        }
    }

    public int runTaskTimer(Runnable task, long delayTicks, long periodTicks) {
        if (folia) {
            try {
                Object globalRegionScheduler = getGlobalRegionScheduler();
                Class<?> globalSchedulerClass = Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
                java.lang.reflect.Method runAtFixedRateMethod = globalSchedulerClass
                        .getMethod("runAtFixedRate", org.bukkit.plugin.Plugin.class, java.util.function.Consumer.class, long.class, long.class);
                java.util.function.Consumer<Object> consumer = scheduledTask -> task.run();
                Object scheduledTask = runAtFixedRateMethod.invoke(globalRegionScheduler, plugin, consumer, delayTicks, periodTicks);
                ScheduledTaskHandle handle = new ScheduledTaskHandle(scheduledTask, true);
                synchronized (activeTasks) {
                    activeTasks.add(handle);
                }
                return handle.getId();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to schedule timer task via Folia global region scheduler", e);
                return -1;
            }
        } else {
            return Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks).getTaskId();
        }
    }

    public int runTaskTimerForEntity(Entity entity, Runnable task, long delayTicks, long periodTicks) {
        if (entity == null) {
            return runTaskTimer(task, delayTicks, periodTicks);
        }
        if (folia) {
            try {
                java.lang.reflect.Method getSchedulerMethod = entity.getClass().getMethod("getScheduler");
                Object entityScheduler = getSchedulerMethod.invoke(entity);
                Class<?> entitySchedulerClass = Class.forName("io.papermc.paper.threadedregions.scheduler.EntityScheduler");
                java.lang.reflect.Method runAtFixedRateMethod = entitySchedulerClass
                        .getMethod("runAtFixedRate", org.bukkit.plugin.Plugin.class, java.util.function.Consumer.class, Runnable.class, long.class, long.class);
                java.util.function.Consumer<Object> consumer = scheduledTask -> task.run();
                Object scheduledTask = runAtFixedRateMethod.invoke(entityScheduler, plugin, consumer, null, delayTicks, periodTicks);
                ScheduledTaskHandle handle = new ScheduledTaskHandle(scheduledTask, true);
                synchronized (activeTasks) {
                    activeTasks.add(handle);
                }
                return handle.getId();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to schedule entity timer task via Folia entity scheduler", e);
                return runTaskTimer(task, delayTicks, periodTicks);
            }
        } else {
            return Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks).getTaskId();
        }
    }

    public void runTaskTimerAtLocation(Location location, Runnable task, long delayTicks, long periodTicks) {
        if (folia) {
            if (location == null || location.getWorld() == null) {
                runTaskTimer(task, delayTicks, periodTicks);
                return;
            }
            try {
                Object regionScheduler = getRegionScheduler();
                Class<?> regionSchedulerClass = Class.forName("io.papermc.paper.threadedregions.scheduler.RegionScheduler");
                java.lang.reflect.Method runAtFixedRateMethod = regionSchedulerClass
                        .getMethod("runAtFixedRate", org.bukkit.plugin.Plugin.class, Location.class, java.util.function.Consumer.class, long.class, long.class);
                java.util.function.Consumer<Object> consumer = scheduledTask -> task.run();
                Object scheduledTask = runAtFixedRateMethod.invoke(regionScheduler, plugin, location, consumer, delayTicks, periodTicks);
                ScheduledTaskHandle handle = new ScheduledTaskHandle(scheduledTask, true);
                synchronized (activeTasks) {
                    activeTasks.add(handle);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to schedule location timer task via Folia region scheduler", e);
                runTaskTimer(task, delayTicks, periodTicks);
            }
        } else {
            Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
        }
    }

    public void runAsync(Runnable task) {
        if (folia) {
            try {
                Object asyncScheduler = getAsyncScheduler();
                Class<?> asyncSchedulerClass = Class.forName("io.papermc.paper.threadedregions.scheduler.AsyncScheduler");
                java.lang.reflect.Method runNowMethod = asyncSchedulerClass
                        .getMethod("runNow", org.bukkit.plugin.Plugin.class, java.util.function.Consumer.class);
                java.util.function.Consumer<Object> consumer = scheduledTask -> task.run();
                runNowMethod.invoke(asyncScheduler, plugin, consumer);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to schedule async task via Folia async scheduler", e);
                task.run();
            }
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }
    }

    public int runAsyncTimer(Runnable task, long delayTicks, long periodTicks) {
        if (folia) {
            try {
                Object asyncScheduler = getAsyncScheduler();
                long delayMs = delayTicks * 50L;
                long periodMs = periodTicks * 50L;
                Class<?> asyncSchedulerClass = Class.forName("io.papermc.paper.threadedregions.scheduler.AsyncScheduler");
                java.lang.reflect.Method runAtFixedRateMethod = asyncSchedulerClass
                        .getMethod("runAtFixedRate", org.bukkit.plugin.Plugin.class, java.util.function.Consumer.class, long.class, long.class, TimeUnit.class);
                java.util.function.Consumer<Object> consumer = scheduledTask -> task.run();
                Object scheduledTask = runAtFixedRateMethod.invoke(asyncScheduler, plugin, consumer, delayMs, periodMs, TimeUnit.MILLISECONDS);
                ScheduledTaskHandle handle = new ScheduledTaskHandle(scheduledTask, true);
                synchronized (activeTasks) {
                    activeTasks.add(handle);
                }
                return handle.getId();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to schedule async timer task via Folia async scheduler", e);
                return -1;
            }
        } else {
            return Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks).getTaskId();
        }
    }

    public void cancelTask(int taskId) {
        if (taskId == -1) return;
        if (folia) {
            synchronized (activeTasks) {
                activeTasks.removeIf(handle -> {
                    if (handle.getId() == taskId) {
                        handle.cancel();
                        return true;
                    }
                    return false;
                });
            }
            Bukkit.getScheduler().cancelTask(taskId);
        } else {
            Bukkit.getScheduler().cancelTask(taskId);
        }
    }

    public void cancelAllTasks() {
        synchronized (activeTasks) {
            for (ScheduledTaskHandle handle : activeTasks) {
                handle.cancel();
            }
            activeTasks.clear();
        }
    }

    private Object getGlobalRegionScheduler() throws Exception {
        java.lang.reflect.Method method = Bukkit.getServer().getClass().getMethod("getGlobalRegionScheduler");
        return method.invoke(Bukkit.getServer());
    }

    private Object getRegionScheduler() throws Exception {
        java.lang.reflect.Method method = Bukkit.getServer().getClass().getMethod("getRegionScheduler");
        return method.invoke(Bukkit.getServer());
    }

    private Object getAsyncScheduler() throws Exception {
        java.lang.reflect.Method method = Bukkit.getServer().getClass().getMethod("getAsyncScheduler");
        return method.invoke(Bukkit.getServer());
    }

    private static final class ScheduledTaskHandle {
        private static int nextId = 1;
        private final int id;
        private final Object foliaTask;
        private final boolean isFoliaTask;

        ScheduledTaskHandle(Object foliaTask, boolean isFoliaTask) {
            this.id = isFoliaTask ? nextId++ : -1;
            this.foliaTask = foliaTask;
            this.isFoliaTask = isFoliaTask;
        }

        int getId() {
            return id;
        }

        void cancel() {
            if (foliaTask != null && isFoliaTask) {
                try {
                    Class<?> scheduledTaskClass = Class.forName("io.papermc.paper.threadedregions.scheduler.ScheduledTask");
                    java.lang.reflect.Method cancelMethod = scheduledTaskClass.getMethod("cancel");
                    cancelMethod.invoke(foliaTask);
                } catch (Exception ignored) {
                }
            }
        }
    }
}
