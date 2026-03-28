package fr.elias.oreoEssentials.util;

import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.TimeUnit;

/**
 * Folia/Luminol-compatible scheduler utility.
 *
 * Uses Paper's new regional scheduler APIs (available since Paper 1.19.4+) which
 * work transparently on both standard Paper and Folia/Luminol:
 *   - GlobalRegionScheduler  → sync tasks not tied to a specific entity/location
 *   - EntityScheduler        → sync tasks tied to a specific entity (e.g. players)
 *   - AsyncScheduler         → async tasks
 *
 * Drop-in replacement for all Bukkit.getScheduler() calls.
 */
public final class OreScheduler {

    private OreScheduler() {}

    // -------------------------------------------------------------------------
    // Sync – global region
    // -------------------------------------------------------------------------

    /** Run a sync task on the next available global tick. */
    public static OreTask run(Plugin plugin, Runnable task) {
        return new OreTask(
                plugin.getServer().getGlobalRegionScheduler()
                        .run(plugin, ctx -> task.run())
        );
    }

    /** Run a sync task after {@code delayTicks} ticks. */
    public static OreTask runLater(Plugin plugin, Runnable task, long delayTicks) {
        return new OreTask(
                plugin.getServer().getGlobalRegionScheduler()
                        .runDelayed(plugin, ctx -> task.run(), Math.max(1, delayTicks))
        );
    }

    /** Run a repeating sync task (global). */
    public static OreTask runTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        return new OreTask(
                plugin.getServer().getGlobalRegionScheduler()
                        .runAtFixedRate(plugin, ctx -> task.run(),
                                Math.max(1, delayTicks), Math.max(1, periodTicks))
        );
    }

    // -------------------------------------------------------------------------
    // Sync – entity-bound (use for player-specific tasks to stay in the right region)
    // -------------------------------------------------------------------------

    /** Run a sync task bound to an entity's region. Falls back to EMPTY if entity is invalid. */
    public static OreTask runForEntity(Plugin plugin, Entity entity, Runnable task) {
        var t = entity.getScheduler().run(plugin, ctx -> task.run(), null);
        return t != null ? new OreTask(t) : OreTask.EMPTY;
    }

    /** Run a delayed sync task bound to an entity's region. */
    public static OreTask runLaterForEntity(Plugin plugin, Entity entity, Runnable task, long delayTicks) {
        var t = entity.getScheduler().runDelayed(plugin, ctx -> task.run(), null, Math.max(1, delayTicks));
        return t != null ? new OreTask(t) : OreTask.EMPTY;
    }

    /** Run a repeating sync task bound to an entity's region. */
    public static OreTask runTimerForEntity(Plugin plugin, Entity entity, Runnable task, long delayTicks, long periodTicks) {
        var t = entity.getScheduler().runAtFixedRate(plugin, ctx -> task.run(), null,
                Math.max(1, delayTicks), Math.max(1, periodTicks));
        return t != null ? new OreTask(t) : OreTask.EMPTY;
    }

    // -------------------------------------------------------------------------
    // Async
    // -------------------------------------------------------------------------

    /** Run a task asynchronously. */
    public static OreTask runAsync(Plugin plugin, Runnable task) {
        return new OreTask(
                plugin.getServer().getAsyncScheduler()
                        .runNow(plugin, ctx -> task.run())
        );
    }

    /** Run an async task after {@code delayTicks} ticks (converted to ms). */
    public static OreTask runAsyncLater(Plugin plugin, Runnable task, long delayTicks) {
        return new OreTask(
                plugin.getServer().getAsyncScheduler()
                        .runDelayed(plugin, ctx -> task.run(),
                                delayTicks * 50L, TimeUnit.MILLISECONDS)
        );
    }

    /** Run a repeating async task. */
    public static OreTask runAsyncTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        return new OreTask(
                plugin.getServer().getAsyncScheduler()
                        .runAtFixedRate(plugin, ctx -> task.run(),
                                Math.max(1, delayTicks) * 50L,
                                Math.max(1, periodTicks) * 50L,
                                TimeUnit.MILLISECONDS)
        );
    }

    // -------------------------------------------------------------------------
    // Cancellation
    // -------------------------------------------------------------------------

    /**
     * Cancel all tasks registered by {@code plugin}.
     * Replaces Bukkit.getScheduler().cancelTasks(plugin).
     */
    public static void cancelAll(Plugin plugin) {
        try {
            plugin.getServer().getGlobalRegionScheduler().cancelTasks(plugin);
        } catch (Throwable ignored) {}
        try {
            plugin.getServer().getAsyncScheduler().cancelTasks(plugin);
        } catch (Throwable ignored) {}
    }
}
