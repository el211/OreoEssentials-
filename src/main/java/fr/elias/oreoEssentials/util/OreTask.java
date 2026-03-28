package fr.elias.oreoEssentials.util;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

/**
 * Unified task handle for both Paper and Folia/Luminol schedulers.
 * Replaces BukkitTask across the codebase.
 */
public final class OreTask {

    public static final OreTask EMPTY = new OreTask(null);

    private final ScheduledTask task;

    OreTask(ScheduledTask task) {
        this.task = task;
    }

    public void cancel() {
        if (task != null) {
            try {
                task.cancel();
            } catch (Throwable ignored) {}
        }
    }

    public boolean isCancelled() {
        if (task == null) return true;
        try {
            return task.isCancelled();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Returns true if this handle actually wraps a task. */
    public boolean isValid() {
        return task != null;
    }
}
