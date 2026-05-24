package io.papermc.paper.threadedregions.scheduler;

import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import java.util.function.Consumer;

public interface RegionScheduler {
    void execute(Plugin plugin, Location location, Runnable run);
    ScheduledTask runAtFixedRate(Plugin plugin, Location location, Consumer<ScheduledTask> run, long delayTicks, long periodTicks);
}
