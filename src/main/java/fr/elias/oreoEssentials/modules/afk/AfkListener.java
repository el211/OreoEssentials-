package fr.elias.oreoEssentials.modules.afk;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import org.bukkit.entity.Player;

public class AfkListener implements Listener {
    private final AfkService afk;

    public AfkListener(AfkService afk) { this.afk = afk; }

    private void nuke(Player p) { afk.clearAfk(p); }

    @EventHandler public void onMove(PlayerMoveEvent e) { if (!e.getFrom().toVector().equals(e.getTo().toVector())) nuke(e.getPlayer()); }
    @EventHandler public void onChat(AsyncPlayerChatEvent e) { nuke(e.getPlayer()); }
    @EventHandler public void onInteract(PlayerInteractEvent e) { nuke(e.getPlayer()); }
    @EventHandler public void onCommand(PlayerCommandPreprocessEvent e) {
        if (!e.getMessage().toLowerCase().startsWith("/afk")) nuke(e.getPlayer());
    }
}
