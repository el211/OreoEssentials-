package fr.elias.oreoEssentials.modules.playtime;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PrewardsListeners implements Listener {
    private final OreoEssentials plugin;
    private final PlaytimeRewardsService svc;

    private final Map<UUID, BukkitTask> remindTasks = new HashMap<>();

    public PrewardsListeners(OreoEssentials plugin, PlaytimeRewardsService svc) {
        this.plugin = plugin;
        this.svc = svc;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        final Player p = e.getPlayer();

        svc.checkPlayer(p, true);

        final int mins = svc.notifyEveryMinutes;
        if (mins <= 0) return;

        BukkitTask old = remindTasks.remove(p.getUniqueId());
        if (old != null) old.cancel();

        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                () -> {
                    if (!p.isOnline()) return;
                    int ready = svc.rewardsReady(p).size();
                    if (ready > 0 && p.hasPermission("oreo.prewards.notify")) {
                        // If you added svc.msg(key, def) use that; else fallback text:
                        p.sendMessage(svc.color("&aYou have rewards ready: &f" + ready));
                    }
                },
                20L * 60,
                20L * 60 * Math.max(1, mins)
        );

        remindTasks.put(p.getUniqueId(), task);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        BukkitTask task = remindTasks.remove(id);
        if (task != null) task.cancel();
    }
}
