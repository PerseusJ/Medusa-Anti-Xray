package io.papermc.paper.threadedregions.scheduler;

import org.bukkit.plugin.Plugin;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public interface AsyncScheduler {
    ScheduledTask runNow(Plugin plugin, Consumer<ScheduledTask> run);
    ScheduledTask runAtFixedRate(Plugin plugin, Consumer<ScheduledTask> run, long delayMs, long periodMs, TimeUnit unit);
}
