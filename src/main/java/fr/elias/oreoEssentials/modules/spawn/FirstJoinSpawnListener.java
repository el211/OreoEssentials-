package fr.elias.oreoEssentials.modules.spawn;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.util.Async;
import fr.elias.oreoEssentials.util.OreScheduler;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class FirstJoinSpawnListener implements Listener {
    private final OreoEssentials plugin;

    public FirstJoinSpawnListener(OreoEssentials plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onFirstJoin(PlayerJoinEvent event) {
        ConfigurationSection sec = plugin.getSettingsConfig().getRoot()
                .getConfigurationSection("features.first-join-spawn");
        if (sec == null || !sec.getBoolean("enabled", false)) return;

        Player p = event.getPlayer();
        if (p.hasPlayedBefore()) return;

        SpawnService spawn = plugin.getSpawnService();
        if (spawn == null) return;

        // Fetch location async then teleport on entity thread (1 tick delay to let join finish)
        Async.run(() -> {
            // Priority: first-join spawn → local spawn → global spawn
            Location loc = spawn.getFirstJoinSpawn();
            if (loc == null) loc = spawn.getLocalSpawn();
            if (loc == null) loc = spawn.getGlobalSpawn();

            if (loc == null) {
                plugin.getLogger().warning("[FirstJoinSpawn] No spawn location set. Skipping teleport for " + p.getName());
                return;
            }

            final Location target = loc;
            OreScheduler.runLaterForEntity(plugin, p, () -> {
                if (!p.isOnline()) return;
                if (target.getWorld() == null) {
                    plugin.getLogger().warning("[FirstJoinSpawn] Spawn world is not loaded. Skipping teleport for " + p.getName());
                    return;
                }
                if (OreScheduler.isFolia()) {
                    p.teleportAsync(target);
                } else {
                    p.teleport(target);
                }
                plugin.getLogger().info("[FirstJoinSpawn] Teleported " + p.getName() + " to spawn on first join.");
            }, 2L);
        });
    }
}
