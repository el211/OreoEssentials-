// File: src/main/java/fr/elias/oreoEssentials/enderchest/EnderChestListener.java
package fr.elias.oreoEssentials.enderchest;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;

public class EnderChestListener implements Listener {

    private static final String TITLE_PLAIN = "Ender Chest";

    private final EnderChestService service;
    private final boolean crossServer;

    public EnderChestListener(fr.elias.oreoEssentials.OreoEssentials plugin,
                              EnderChestService service,
                              boolean crossServer) {
        this.service = service;
        this.crossServer = crossServer;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        if (TITLE_PLAIN.equalsIgnoreCase(ChatColor.stripColor(e.getView().getTitle()))) {
            service.saveFromInventory(p, e.getInventory());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent e) {
        if (!crossServer) return;
        if (!(e.getPlayer() instanceof Player p)) return;

        if (e.getInventory().getType() == InventoryType.ENDER_CHEST) {
            e.setCancelled(true);
            p.closeInventory();
            service.open(p);

            // Utilise Lang.msg pour prefix + couleurs + PAPI
            p.sendMessage(fr.elias.oreoEssentials.util.Lang.msg(
                    "enderchest.storage.opened-cross-server",
                    null,
                    p
            ));
        }
    }


    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;

        if (!TITLE_PLAIN.equalsIgnoreCase(ChatColor.stripColor(e.getView().getTitle()))) return;
        if (e.getClickedInventory() == null) return;

        Inventory top = e.getView().getTopInventory();

        // Shift-click from bottom into top: block entirely to avoid bypassing
        if (e.getClickedInventory() != top) {
            if (e.isShiftClick()) e.setCancelled(true);
            return;
        }

        int raw = e.getRawSlot();
        if (service.isLockedSlot(p, raw)) {
            e.setCancelled(true);
            return;
        }

        // also block placing/picking the lock item in allowed area (safety)
        if (service.isLockItem(e.getCurrentItem()) || service.isLockItem(e.getCursor())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;

        if (!TITLE_PLAIN.equalsIgnoreCase(ChatColor.stripColor(e.getView().getTitle()))) return;

        for (int raw : e.getRawSlots()) {
            if (raw < e.getView().getTopInventory().getSize() && service.isLockedSlot(p, raw)) {
                e.setCancelled(true);
                return;
            }
        }
    }
}
