package fr.elias.oreoEssentials.commands.core.playercommands.back;


import fr.elias.oreoEssentials.OreoEssentials;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;

public class BackListener implements Listener {

    private final BackService backService;
    private final String serverName;

    public BackListener(BackService backService, OreoEssentials plugin) {
        this.backService = backService;
        this.serverName = plugin.getConfigService().serverName();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player p = event.getPlayer();

        // tu peux filtrer : COMMAND, PLUGIN, UNKNOWN, etc.
        switch (event.getCause()) {
            case COMMAND:
            case PLUGIN:
            case UNKNOWN:
                backService.setLast(
                        p.getUniqueId(),
                        BackLocation.from(serverName, event.getFrom())
                );
                break;
            default:
                // pas de back sur enderpearl / nether / portal etc (à toi de voir)
                break;
        }
    }
}
