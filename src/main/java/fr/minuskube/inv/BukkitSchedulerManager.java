package fr.minuskube.inv;

import fr.elias.oreoEssentials.util.OreScheduler;
import fr.elias.oreoEssentials.util.OreTask;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;

/**
 * Folia-compatible replacement for SmartInventory's BukkitSchedulerManager.
 *
 * The original class calls BukkitRunnable.runTaskTimer() which throws
 * UnsupportedOperationException on Folia.  This version falls back to
 * OreScheduler (entity scheduler) when running on Folia so that GUI update
 * tasks are bound to the player's owning region thread.
 *
 * Maven shade includes project classes before dependency classes, so this file
 * overrides the one bundled in bukkit-smart-invs-1.3.5.jar at build time.
 */
public class BukkitSchedulerManager implements SchedulerManager {

    private final JavaPlugin plugin;
    private final Map<Player, BukkitTask> tasks     = new HashMap<>();
    private final Map<Player, OreTask>    foliaTasks = new HashMap<>();

    public BukkitSchedulerManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void runTask(BukkitRunnable runnable, Player player,
                        long delay, long period,
                        SchedulerManager.SchedulerType type) {
        if (OreScheduler.isFolia()) {
            OreTask existing = foliaTasks.remove(player);
            if (existing != null) existing.cancel();

            Runnable r = runnable::run;
            OreTask oreTask;
            if (delay == 0 && period == 0) {
                oreTask = OreScheduler.runForEntity(plugin, player, r);
            } else if (delay > 0 && period == 0) {
                oreTask = OreScheduler.runLaterForEntity(plugin, player, r, delay);
            } else {
                long d = Math.max(1L, delay);
                long p = Math.max(1L, period);
                oreTask = OreScheduler.runTimerForEntity(plugin, player, r, d, p);
            }
            if (player != null) foliaTasks.put(player, oreTask);
        } else {
            BukkitTask existing = tasks.remove(player);
            if (existing != null) existing.cancel();

            BukkitTask task;
            if (delay == 0 && period == 0) {
                task = runnable.runTask(plugin);
            } else if (delay > 0 && period == 0) {
                task = runnable.runTaskLater(plugin, delay);
            } else {
                task = runnable.runTaskTimer(plugin, Math.max(0, delay), Math.max(1L, period));
            }
            if (player != null) tasks.put(player, task);
        }
    }

    @Override
    public void cancelTaskByPlayer(Player player) {
        if (OreScheduler.isFolia()) {
            OreTask task = foliaTasks.remove(player);
            if (task != null) task.cancel();
        } else {
            BukkitTask task = tasks.remove(player);
            if (task != null) task.cancel();
        }
    }
}
