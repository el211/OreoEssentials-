package fr.elias.oreoEssentials.modules.trade.ui;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.trade.service.TradeService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;

import java.util.UUID;

public final class TradeInventoryCloseListener implements Listener {
    private final OreoEssentials plugin;

    public TradeInventoryCloseListener(OreoEssentials plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;

        final TradeService svc = plugin.getTradeService();
        if (svc == null) return;

        final TradeMenuRegistry reg = svc.getMenuRegistry();
        if (reg == null) return;

        final UUID viewerId = p.getUniqueId();

        TradeMenu menu = null;
        try {
            try {
                menu = reg.peek(viewerId);
            } catch (NoSuchMethodError | Exception ignored) {
                try {
                    menu = reg.get(viewerId);
                } catch (Throwable ignoredToo) {
                }
            }

            if (menu != null) {
                try {
                    menu.onClose(p);
                } catch (Throwable ignored) {}
            } else {
                try { reg.unregister(viewerId); } catch (Throwable ignored) {}
            }
        } catch (Throwable ignoredOuter) {
            try { reg.unregister(viewerId); } catch (Throwable ignored) {}
        }
    }
}
