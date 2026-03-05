package fr.elias.oreoEssentials.modules.auctionhouse;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;

/**
 * Removes players from the BrowseGUI viewer registry when they close any
 * inventory. This prevents stale-viewer refreshes from re-opening an AH GUI
 * on a player who has already moved on to something else.
 *
 * If the player re-opens BrowseGUI, BrowseGUI.init() will re-register them.
 */
public class AHGuiListener implements Listener {

    private final AuctionHouseModule module;

    public AHGuiListener(AuctionHouseModule module) {
        this.module = module;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player p)) return;
        module.unregisterBrowseViewer(p.getUniqueId());
    }
}
