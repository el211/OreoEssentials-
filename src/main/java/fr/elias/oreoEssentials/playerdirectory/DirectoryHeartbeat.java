package fr.elias.oreoEssentials.playerdirectory;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public final class DirectoryHeartbeat {
    private final PlayerDirectory dir;
    private final String server;
    private BukkitTask task;

    public DirectoryHeartbeat(PlayerDirectory dir, String server) {
        this.dir = dir;
        this.server = server;
    }

    /** Start a lightweight repeating task that "touches" presence. */
    public void start() {
        stop();
        this.task = Bukkit.getScheduler().runTaskTimer(
                OreoEssentials.get(),
                () -> {
                    try {
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            dir.upsertPresence(p.getUniqueId(), p.getName(), server);
                        }
                    } catch (Throwable ignored) {}
                },
                20L * 10,
                20L * 30
        );
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
