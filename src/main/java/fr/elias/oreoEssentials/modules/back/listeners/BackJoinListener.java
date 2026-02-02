package fr.elias.oreoEssentials.modules.back.listeners;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.back.BackLocation;
import fr.elias.oreoEssentials.modules.back.service.BackService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.UUID;


public class BackJoinListener implements Listener {

    private final OreoEssentials plugin;
    private final BackService backService;

    private static final long TELEPORT_DELAY_TICKS = 20L;

    public BackJoinListener(OreoEssentials plugin, BackService backService) {
        this.plugin = plugin;
        this.backService = backService;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        final UUID uuid = player.getUniqueId();

        final BackLocation pending = plugin.getPendingBackTeleports().remove(uuid);
        if (pending == null) return;

        plugin.getLogger().info("[BackJoinListener] " + player.getName()
                + " joined with pending /back teleport to: "
                + pending.getWorldName() + " (" + pending.getX() + ", " + pending.getY() + ", " + pending.getZ() + ")");

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                if (!player.isOnline()) {
                    plugin.getLogger().warning("[BackJoinListener] " + player.getName() + " disconnected before pending /back teleport.");
                    return;
                }

                World world = Bukkit.getWorld(pending.getWorldName());
                if (world == null) {
                    player.sendMessage("§cCouldn't teleport back - world not found: §e" + pending.getWorldName());
                    plugin.getLogger().warning("[BackJoinListener] World not found for pending /back: " + pending.getWorldName());
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

                boolean ok = player.teleport(location);
                if (ok) {
                    player.sendMessage("§aTeleported back!");
                    plugin.getLogger().info("[BackJoinListener] Teleported " + player.getName()
                            + " to pending /back location in world: " + pending.getWorldName());
                } else {
                    player.sendMessage("§cCouldn't teleport back - teleport failed.");
                    plugin.getLogger().warning("[BackJoinListener] Teleport returned false for " + player.getName()
                            + " to world: " + pending.getWorldName());
                }

            } catch (Throwable t) {
                plugin.getLogger().severe("[BackJoinListener] Error while executing pending /back teleport for " + player.getName());
                t.printStackTrace();
                try {
                    player.sendMessage("§cCouldn't teleport back - an error occurred.");
                } catch (Throwable ignored) {
                }
            } finally {
                try {
                    backService.unmarkCrossServerSwitch(uuid);
                } catch (Throwable ignored) {
                }
            }
        }, TELEPORT_DELAY_TICKS);
    }
}
