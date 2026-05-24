package io.papermc.paper.threadedregions.scheduler;

import org.bukkit.plugin.Plugin;
import java.util.function.Consumer;

public interface EntityScheduler {
    boolean execute(Plugin plugin, Runnable run, Runnable retired);
    ScheduledTask runDelayed(Plugin plugin, Consumer<ScheduledTask> run, Runnable retired, long delayTicks);
    ScheduledTask runAtFixedRate(Plugin plugin, Consumer<ScheduledTask> run, Runnable retired, long delayTicks, long periodTicks);
}
