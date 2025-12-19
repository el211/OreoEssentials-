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
import org.bukkit.inventory.ItemStack;

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
            String msg = Lang.msg(
                    "enderchest.storage.opened-cross-server",
                    null,
                    p
            );

            if (msg != null && !msg.isEmpty()) {
                p.sendMessage(msg);
            }
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

        // --- CLIC DANS L’INVENTAIRE DU JOUEUR (bas) ---
        if (e.getClickedInventory() != top) {
            // On ne bloque le shift-click que s’il ne peut PAS rentrer dans la zone autorisée
            if (e.isShiftClick()) {
                ItemStack moving = e.getCurrentItem();
                if (moving == null || moving.getType().isAir()) {
                    return; // rien à faire
                }

                int allowed = service.resolveSlots(p);

                boolean canFit = false;
                for (int slot = 0; slot < allowed; slot++) {
                    ItemStack existing = top.getItem(slot);

                    // slot vide dans la zone autorisée
                    if (existing == null || existing.getType().isAir()) {
                        canFit = true;
                        break;
                    }

                    // stack similaire à compléter
                    try {
                        if (existing.isSimilar(moving)
                                && existing.getAmount() < existing.getMaxStackSize()) {
                            canFit = true;
                            break;
                        }
                    } catch (Throwable ignored) {
                        // fallback au type si jamais
                        if (existing.getType() == moving.getType()
                                && existing.getAmount() < existing.getMaxStackSize()) {
                            canFit = true;
                            break;
                        }
                    }
                }

                // aucune place dans la zone autorisée => on bloque
                if (!canFit) {
                    e.setCancelled(true);
                }
            }
            return;
        }

        // --- CLIC DANS L’ENDER CHEST (top) ---
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
