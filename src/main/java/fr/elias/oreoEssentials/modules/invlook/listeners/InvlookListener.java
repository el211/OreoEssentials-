package fr.elias.oreoEssentials.modules.invlook.listeners;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;

public class InvlookListener implements Listener {

    private final OreoEssentials plugin;

    public InvlookListener(OreoEssentials plugin) {
        this.plugin = plugin;
    }

    private boolean isInvlook(Player p) {
        return plugin.getInvlookManager() != null && plugin.getInvlookManager().isReadOnly(p.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!isInvlook(p)) return;

        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!isInvlook(p)) return;
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreative(InventoryCreativeEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!isInvlook(p)) return;
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        if (plugin.getInvlookManager() != null) {
            plugin.getInvlookManager().unmark(p.getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (plugin.getInvlookManager() != null) {
            plugin.getInvlookManager().unmark(e.getPlayer().getUniqueId());
        }
    }
}
