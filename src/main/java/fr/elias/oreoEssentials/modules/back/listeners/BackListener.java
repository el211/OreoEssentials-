    package fr.elias.oreoEssentials.modules.back.listeners;


    import fr.elias.oreoEssentials.OreoEssentials;

    import fr.elias.oreoEssentials.modules.back.BackLocation;
    import fr.elias.oreoEssentials.modules.back.service.BackService;
    import org.bukkit.entity.Player;
    import org.bukkit.event.EventHandler;
    import org.bukkit.event.EventPriority;
    import org.bukkit.event.Listener;
    import org.bukkit.event.player.PlayerTeleportEvent;
    import org.bukkit.event.player.PlayerQuitEvent;
    import org.bukkit.event.player.PlayerKickEvent;
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
                    break;
            }
        }
        @EventHandler(priority = EventPriority.MONITOR)
        public void onQuit(PlayerQuitEvent event) {
            Player p = event.getPlayer();

            if (backService.isCrossServerSwitch(p.getUniqueId())) return;

            backService.setLast(
                    p.getUniqueId(),
                    BackLocation.from(serverName, p.getLocation())
            );
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onKick(PlayerKickEvent event) {
            Player p = event.getPlayer();

            if (backService.isCrossServerSwitch(p.getUniqueId())) return;

            backService.setLast(
                    p.getUniqueId(),
                    BackLocation.from(serverName, p.getLocation())
            );
        }

    }
