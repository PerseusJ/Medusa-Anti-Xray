package io.papermc.paper.threadedregions.scheduler;

import org.bukkit.plugin.Plugin;
import java.util.function.Consumer;

public interface GlobalRegionScheduler {
    void execute(Plugin plugin, Runnable run);
    ScheduledTask runDelayed(Plugin plugin, Consumer<ScheduledTask> run, long delayTicks);
    ScheduledTask runAtFixedRate(Plugin plugin, Consumer<ScheduledTask> run, long delayTicks, long periodTicks);
}
