// File: src/main/java/fr/elias/oreoEssentials/enderchest/EnderChestListener.java
package fr.elias.oreoEssentials.enderchest;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;

public class EnderChestListener implements Listener {

    // Strip colors so we can compare even if lang.yml changes the title formatting
    private static final String TITLE_STRIPPED = ChatColor.stripColor(EnderChestService.TITLE);

    private final EnderChestService service;
    private final boolean crossServer;

    public EnderChestListener(OreoEssentials plugin,
                              EnderChestService service,
                              boolean crossServer) {
        this.service = service;
        this.crossServer = crossServer;
    }

    // --------------------------------------------------
    // SAVE ON CLOSE
    // --------------------------------------------------
    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        if (!isEc(e)) return;

        service.saveFromInventory(p, e.getInventory());
    }

    // --------------------------------------------------
    // INTERCEPT VANILLA ENDER CHEST → OPEN VIRTUAL (CROSS-SERVER)
    // --------------------------------------------------
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent e) {
        if (!crossServer) return;
        if (!(e.getPlayer() instanceof Player p)) return;

        if (e.getInventory().getType() == InventoryType.ENDER_CHEST) {
            e.setCancelled(true);
            p.closeInventory();
            service.open(p);

            // Message: enderchest.storage.opened-cross-server
            p.sendMessage(Lang.msg(
                    "enderchest.storage.opened-cross-server",
                    null,
                    p
            ));
        }
    }

    // --------------------------------------------------
    // CLICK PROTECTION (BLOCK LOCKED SLOTS + LOCK ITEMS)
    // --------------------------------------------------
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!isEc(e)) return;
        if (e.getClickedInventory() == null) return;

        Inventory top = e.getView().getTopInventory();

        // If clicking in bottom inventory:
        // - block shift-click to avoid dumping items into locked area
        if (e.getClickedInventory() != top) {
            if (e.isShiftClick()) {
                e.setCancelled(true);
            }
            return;
        }

        // Now we are in the top (virtual EC GUI)
        int raw = e.getRawSlot();
        if (service.isLockedSlot(p, raw)) {
            e.setCancelled(true);
            return;
        }

        // Safety: do not allow the special lock item to move at all
        if (service.isLockItem(e.getCurrentItem()) || service.isLockItem(e.getCursor())) {
            e.setCancelled(true);
        }
    }

    // --------------------------------------------------
    // DRAG PROTECTION (BLOCK DRAGGING INTO LOCKED SLOTS)
    // --------------------------------------------------
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!isEc(e)) return;

        Inventory top = e.getView().getTopInventory();

        for (int raw : e.getRawSlots()) {
            // Only care about slots in the top inventory
            if (raw < top.getSize() && service.isLockedSlot(p, raw)) {
                e.setCancelled(true);
                return;
            }
        }
    }

    // --------------------------------------------------
    // HELPER: IS THIS OUR VIRTUAL ENDER CHEST GUI?
    // --------------------------------------------------
    private boolean isEc(InventoryEvent e) {
        String title = ChatColor.stripColor(e.getView().getTitle());
        return TITLE_STRIPPED.equalsIgnoreCase(title);
    }
}
