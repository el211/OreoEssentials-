package fr.elias.oreoEssentials.services;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

// Example interface (put in fr.elias.oreoEssentials.inventory)
public interface InventoryService {
    final class Snapshot {
        public ItemStack[] contents; // 0..40 (41 slots: 0..8 hotbar, 9..35 main, 36..40 unused)
        public ItemStack[] armor;    // 4 (boots, leggings, chest, helmet)
        public ItemStack offhand;  // 1
    }

    Snapshot load(UUID uuid);                 // return null => empty

    void save(UUID uuid, Snapshot snapshot);  // persist + mark pending if target offline


    private void clearPersistentInventory(InventoryService invService, UUID uuid) {
        InventoryService.Snapshot snap = new InventoryService.Snapshot();
        snap.contents = new ItemStack[41]; // 41 slots, tous à null
        snap.armor = new ItemStack[4];  // 4 slots, tous à null
        snap.offhand = null;

        invService.save(uuid, snap);
    }
}