package fr.elias.oreoEssentials.modules.trade.pending;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;


public interface PendingGrantsDao {

    void storePending(UUID target, UUID sessionId, ItemStack[] items);


    PendingItems fetchAndDelete(UUID target);

    final class PendingItems {
        public final UUID sessionId; // optional
        public final ItemStack[] items;

        public PendingItems(UUID sessionId, ItemStack[] items) {
            this.sessionId = sessionId;
            this.items = (items == null ? new ItemStack[0] : items);
        }
    }
}
