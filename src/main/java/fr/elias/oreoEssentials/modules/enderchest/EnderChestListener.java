package fr.elias.oreoEssentials.modules.enderchest;

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

import java.util.Map;


public class EnderChestListener implements Listener {

    private static final String TITLE_STRIPPED = ChatColor.stripColor(EnderChestService.TITLE);

    private final EnderChestService service;
    private final boolean crossServer;

    public EnderChestListener(OreoEssentials plugin,
                              EnderChestService service,
                              boolean crossServer) {
        this.service = service;
        this.crossServer = crossServer;
    }


    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        if (!isEc(e)) return;

        service.saveFromInventory(p, e.getInventory());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent e) {
        if (!crossServer) return;
        if (!(e.getPlayer() instanceof Player p)) return;

        if (e.getInventory().getType() == InventoryType.ENDER_CHEST) {
            e.setCancelled(true);
            p.closeInventory();
            service.open(p);

            // Notify player that cross-server storage is being used
            Lang.send(p, "enderchest.storage.opened-cross-server",
                    "<gray>Opening your cross-server ender chest...</gray>",
                    Map.of());
        }
    }


    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!isEc(e)) return;
        if (e.getClickedInventory() == null) return;

        Inventory top = e.getView().getTopInventory();

        if (e.getClickedInventory() != top) {
            if (e.isShiftClick()) {
                ItemStack moving = e.getCurrentItem();
                if (moving == null || moving.getType().isAir()) {
                    return;
                }

                int allowed = service.resolveSlots(p);

                boolean canFit = false;
                for (int slot = 0; slot < allowed; slot++) {
                    ItemStack existing = top.getItem(slot);

                    if (existing == null || existing.getType().isAir()) {
                        canFit = true;
                        break;
                    }

                    try {
                        if (existing.isSimilar(moving)
                                && existing.getAmount() < existing.getMaxStackSize()) {
                            canFit = true;
                            break;
                        }
                    } catch (Throwable ignored) {
                        if (existing.getType() == moving.getType()
                                && existing.getAmount() < existing.getMaxStackSize()) {
                            canFit = true;
                            break;
                        }
                    }
                }

                if (!canFit) {
                    e.setCancelled(true);
                }
            }
            return;
        }

        int raw = e.getRawSlot();
        if (service.isLockedSlot(p, raw)) {
            e.setCancelled(true);
            return;
        }

        if (service.isLockItem(e.getCurrentItem()) || service.isLockItem(e.getCursor())) {
            e.setCancelled(true);
        }
    }


    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!isEc(e)) return;

        Inventory top = e.getView().getTopInventory();

        for (int raw : e.getRawSlots()) {
            if (raw < top.getSize() && service.isLockedSlot(p, raw)) {
                e.setCancelled(true);
                return;
            }
        }
    }
    private boolean isEc(InventoryEvent e) {
        String title = ChatColor.stripColor(e.getView().getTitle());
        return TITLE_STRIPPED.equalsIgnoreCase(title);
    }
}