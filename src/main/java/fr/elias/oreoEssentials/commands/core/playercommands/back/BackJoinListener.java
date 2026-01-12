package fr.elias.oreoEssentials.commands.core.playercommands.back;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.UUID;

/**
 * Handles pending back teleports when players join the server
 */
public class BackJoinListener implements Listener {

    private final OreoEssentials plugin;

    public BackJoinListener(OreoEssentials plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Check if this player has a pending back teleport
        BackLocation pending = plugin.getPendingBackTeleports().remove(uuid);

        if (pending == null) {
            return; // No pending teleport
        }

        plugin.getLogger().info("[BackJoinListener] Player " + player.getName() +
                " joined with pending back teleport to world: " + pending.getWorldName());

        // Teleport the player after a short delay (let them fully load in)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return; // Player disconnected already
            }

            World world = Bukkit.getWorld(pending.getWorldName());

            if (world == null) {
                player.sendMessage("§cCouldn't teleport back - world not found: " +
                        pending.getWorldName());
                plugin.getLogger().warning("[BackJoinListener] World not found: " +
                        pending.getWorldName());
                return;
            }

            Location location = new Location(
                    world,
                    pending.getX(),
                    pending.getY(),
                    pending.getZ(),
                    pending.getYaw(),
                    pending.getPitch()
            );

            player.teleport(location);
            player.sendMessage("§aTeleported back!");

            plugin.getLogger().info("[BackJoinListener] Teleported " + player.getName() +
                    " to back location");

        }, 20L); // 1 second delay (20 ticks)
    }
}