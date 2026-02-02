package fr.elias.oreoEssentials.modules.commandcontrol;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Locale;
import java.util.Map;

public final class CommandControlListener implements Listener {

    private final OreoEssentials plugin;
    private final CommandControlService service;

    public CommandControlListener(OreoEssentials plugin, CommandControlService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (!service.isEnabled()) return;

        Player p = e.getPlayer();
        if (p == null) return;
        if (service.canBypass(p)) return;

        String msg = e.getMessage();
        if (msg == null || msg.isBlank()) return;

        String raw = msg.startsWith("/") ? msg.substring(1) : msg;
        raw = raw.trim();
        if (raw.isEmpty()) return;

        String[] parts = raw.split("\\s+");
        if (parts.length == 0) return;

        String root = parts[0].toLowerCase(Locale.ROOT);
        int colon = root.indexOf(':');
        if (colon >= 0 && colon + 1 < root.length()) root = root.substring(colon + 1);

        String sub = parts.length >= 2 ? parts[1].toLowerCase(Locale.ROOT) : "";

        if (service.isBlocked(p, root, sub, raw)) {
            e.setCancelled(true);

            String deny = service.getDenyMessage();
            if (deny != null && !deny.isBlank()) {
                Lang.send(p, "command-control.denied", deny, Map.of());
            }
        }
    }
}
