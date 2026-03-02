package fr.elias.oreoEssentials.modules.shop.listeners;

import fr.elias.oreoEssentials.modules.shop.ShopModule;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class AntiDupeListener implements Listener {

    private final ShopModule module;

    public AntiDupeListener(ShopModule module) {
        this.module = module;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        module.getTransactionProcessor().getAntiDupe()
                .cleanupPlayer(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKick(PlayerKickEvent e) {
        module.getTransactionProcessor().getAntiDupe()
                .cleanupPlayer(e.getPlayer().getUniqueId());
    }
}