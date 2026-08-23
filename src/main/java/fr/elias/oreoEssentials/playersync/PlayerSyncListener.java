package fr.elias.oreoEssentials.playersync;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.util.OreScheduler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public final class PlayerSyncListener implements Listener {
    private final PlayerSyncService service;
    private final boolean enabled;

    public PlayerSyncListener(PlayerSyncService service, boolean enabled) {
        this.service = service;
        this.enabled = enabled;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        if (!enabled) return;

        final Player quitting = e.getPlayer();
        final UUID uuid = quitting.getUniqueId();

        // Read Bukkit player state while we still own the player's region/thread,
        // then perform only serialization/storage work asynchronously.
        final PlayerSyncSnapshot snapshot;
        try {
            snapshot = service.captureSnapshot(quitting);
        } catch (Throwable t) {
            OreoEssentials.get().getLogger().warning("[SYNC] capture failed for " + uuid + ": " + t.getMessage());
            return;
        }

        OreScheduler.runAsync(OreoEssentials.get(), () -> service.saveSnapshot(uuid, snapshot));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        if (!enabled) return;
        final Player p = e.getPlayer();
        final UUID uuid = p.getUniqueId();
        final String name = p.getName();

        // PlayerDirectory uses the synchronous Mongo driver, so never query/write it on a region thread.
        OreScheduler.runAsync(OreoEssentials.get(), () -> {
            try {
                var directory = OreoEssentials.get().getPlayerDirectory();
                if (directory != null) directory.saveMapping(name, uuid);
            } catch (Throwable ignored) {}
        });

        // Preserve the original 10-tick join grace period, but move the storage load off-thread.
        OreScheduler.runLaterForEntity(OreoEssentials.get(), p, () -> {
            if (!p.isOnline()) return;

            OreScheduler.runAsync(OreoEssentials.get(), () -> {
                PlayerSyncSnapshot snapshot = service.loadSnapshot(uuid);
                if (snapshot == null) return;

                OreScheduler.runForEntity(OreoEssentials.get(), p, () -> {
                    Player online = Bukkit.getPlayer(uuid);
                    if (online == null || !online.isOnline()) return;
                    service.applySnapshot(online, snapshot);
                });
            });
        }, 10L);
    }
}
