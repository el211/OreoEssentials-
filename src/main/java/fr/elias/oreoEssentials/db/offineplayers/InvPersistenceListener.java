package fr.elias.oreoEssentials.db.offineplayers;

import fr.elias.oreoEssentials.modules.enderchest.EnderSnapshot;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;

public final class InvPersistenceListener implements Listener {
    private final SnapshotStorage storage;

    public InvPersistenceListener(SnapshotStorage storage) { this.storage = storage; }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();

        InvSnapshot inv = new InvSnapshot();
        inv.contents = Arrays.copyOf(p.getInventory().getContents(), p.getInventory().getContents().length);
        inv.armor    = Arrays.copyOf(p.getInventory().getArmorContents(), 4);
        inv.offhand  = p.getInventory().getItemInOffHand();
        storage.saveInv(p.getUniqueId(), inv);

        EnderSnapshot ec = new EnderSnapshot();
        ec.chest = Arrays.copyOf(p.getEnderChest().getContents(), 27);
        storage.saveEnder(p.getUniqueId(), ec);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();

        InvSnapshot pendingInv = storage.loadPendingInv(p.getUniqueId());
        if (pendingInv != null) {
            ItemStack[] cont = pad(pendingInv.contents, p.getInventory().getContents().length);
            p.getInventory().setContents(cont);
            if (pendingInv.armor != null) p.getInventory().setArmorContents(pad(pendingInv.armor, 4));
            if (pendingInv.offhand != null) p.getInventory().setItemInOffHand(pendingInv.offhand);
            storage.clearPendingInv(p.getUniqueId());
        }

        EnderSnapshot pendingEc = storage.loadPendingEnder(p.getUniqueId());
        if (pendingEc != null) {
            p.getEnderChest().setContents(pad(pendingEc.chest, 27));
            storage.clearPendingEnder(p.getUniqueId());
        }
    }

    private static ItemStack[] pad(ItemStack[] src, int size) {
        if (src == null) return new ItemStack[size];
        return Arrays.copyOf(src, size);
    }
}

