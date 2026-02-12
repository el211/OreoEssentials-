package fr.elias.oreoEssentials.modules.invsee.menu;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.invsee.InvseeService;
import fr.elias.oreoEssentials.modules.invsee.InvseeSession;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import fr.minuskube.inv.content.SlotPos;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public final class InvseeMenu implements InventoryProvider {

    private final OreoEssentials plugin;
    private final InvseeService service;
    private final InvseeSession session;
    private final SmartInventory inv;
    private final ItemStack[] lastSnapshot = new ItemStack[36];
    private int tickCounter = 0;

    public InvseeMenu(OreoEssentials plugin,
                      InvseeService service,
                      InvseeSession session,
                      Player viewer) {
        this.plugin = plugin;
        this.service = service;
        this.session = session;

        this.inv = SmartInventory.builder()
                .provider(this)
                .size(6, 9)
                .title("Invsee: " + session.getTargetNameOrFallback())
                .manager(plugin.getInvManager())
                .build();
    }

    public void open(Player viewer) {
        inv.open(viewer);
    }

    @Override
    public void init(Player viewer, InventoryContents contents) {
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 9; col++) {
                contents.setEditable(SlotPos.of(row, col), true);
            }
        }
    }

    @Override
    public void update(Player viewer, InventoryContents contents) {
        Inventory top = viewer.getOpenInventory().getTopInventory();
        UUID viewerId = viewer.getUniqueId();
        UUID targetId = session.getTargetId();

        for (int slot = 0; slot < 36; slot++) {
            ItemStack now = top.getItem(slot);
            ItemStack was = lastSnapshot[slot];
            if (!same(now, was)) {
                lastSnapshot[slot] = cloneOrNull(now);
                service.sendEdit(viewerId, targetId, slot, now);
            }
        }

        ItemStack[] snap = session.getLastSnapshot();
        if (snap != null) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (int slot = 0; slot < Math.min(36, snap.length); slot++) {
                    ItemStack want = cloneOrNull(snap[slot]);
                    ItemStack have = top.getItem(slot);
                    if (!same(have, want)) {
                        top.setItem(slot, want);
                        lastSnapshot[slot] = cloneOrNull(want);
                    }
                }
            });
        }

        tickCounter++;
        if (tickCounter % 10 == 0) {
            var broker = service.getBroker();
            if (broker != null && plugin.isMessagingAvailable()) {
                broker.requestOpen(
                        viewer,
                        session.getTargetId(),
                        session.getTargetNameOrFallback()
                );
            }
        }
    }


    public void refreshFromSession(InvseeSession sess) {
        Player viewer = null;

    }

    private static boolean same(ItemStack a, ItemStack b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        try {
            return a.isSimilar(b) && a.getAmount() == b.getAmount();
        } catch (Throwable t) {
            return a.getType() == b.getType() && a.getAmount() == b.getAmount();
        }
    }

    private static ItemStack cloneOrNull(ItemStack it) {
        if (it == null || it.getType().isAir() || it.getAmount() <= 0) return null;
        return it.clone();
    }
}
