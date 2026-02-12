package fr.elias.oreoEssentials.modules.jail;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.entity.Player;

public final class JailGuardListener implements Listener {
    private final JailService service;

    public JailGuardListener(JailService service) {
        this.service = service;
    }

    @EventHandler
    public void onCmd(PlayerCommandPreprocessEvent e) {
        String msg = e.getMessage();
        if (!msg.startsWith("/")) return;
        String base = msg.substring(1).split("\\s+")[0];
        Player p = e.getPlayer();
        if (p.hasPermission("oreo.jail.bypass")) return;
        if (service.isCommandBlockedFor(p, base)) {
            p.sendMessage("§cYou cannot use that command while jailed.");
            e.setCancelled(true);
        }
    }
}
