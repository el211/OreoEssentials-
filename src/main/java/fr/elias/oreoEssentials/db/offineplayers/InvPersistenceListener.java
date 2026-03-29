package fr.elias.oreoEssentials.db.offineplayers;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.enderchest.EnderSnapshot;
import fr.elias.oreoEssentials.util.Async;
import fr.elias.oreoEssentials.util.OreScheduler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.UUID;

public final class InvPersistenceListener implements Listener {
    private final SnapshotStorage storage;

    public InvPersistenceListener(SnapshotStorage storage) { this.storage = storage; }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();

        // Capture inventory state synchronously on the entity thread (safe).
        InvSnapshot inv = new InvSnapshot();
        inv.contents = Arrays.copyOf(p.getInventory().getContents(), p.getInventory().getContents().length);
        inv.armor    = Arrays.copyOf(p.getInventory().getArmorContents(), 4);
        inv.offhand  = p.getInventory().getItemInOffHand();

        EnderSnapshot ec = new EnderSnapshot();
        ec.chest = Arrays.copyOf(p.getEnderChest().getContents(), 27);

        // Dispatch file I/O to async so it doesn't block the entity region thread.
        UUID id = p.getUniqueId();
        Async.run(() -> {
            storage.saveInv(id, inv);
            storage.saveEnder(id, ec);
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        OreoEssentials plugin = OreoEssentials.get();

        // Load pending snapshots from disk asynchronously, then apply on entity thread.
        Async.run(() -> {
            InvSnapshot pendingInv     = storage.loadPendingInv(id);
            EnderSnapshot pendingEc    = storage.loadPendingEnder(id);

            if (pendingInv == null && pendingEc == null) return;

            OreScheduler.runForEntity(plugin, p, () -> {
                if (!p.isOnline()) return;

                if (pendingInv != null) {
                    ItemStack[] cont = pad(pendingInv.contents, p.getInventory().getContents().length);
                    p.getInventory().setContents(cont);
                    if (pendingInv.armor != null) p.getInventory().setArmorContents(pad(pendingInv.armor, 4));
                    if (pendingInv.offhand != null) p.getInventory().setItemInOffHand(pendingInv.offhand);
                }

                if (pendingEc != null) {
                    p.getEnderChest().setContents(pad(pendingEc.chest, 27));
                }

                // Clear pending files async so we don't block entity thread again.
                Async.run(() -> {
                    if (pendingInv != null) storage.clearPendingInv(id);
                    if (pendingEc  != null) storage.clearPendingEnder(id);
                });
            });
        });
    }

    private static ItemStack[] pad(ItemStack[] src, int size) {
        if (src == null) return new ItemStack[size];
        return Arrays.copyOf(src, size);
    }
}
