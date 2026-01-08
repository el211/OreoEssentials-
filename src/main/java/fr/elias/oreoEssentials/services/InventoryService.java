package fr.elias.oreoEssentials.services;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public interface InventoryService {

    final class Snapshot {
        public ItemStack[] contents; // 0..40
        public ItemStack[] armor;    // 4
        public ItemStack   offhand;  // 1

        public int level;

        public float exp;

        public int totalExp;
    }

    Snapshot load(UUID uuid);

    void save(UUID uuid, Snapshot snapshot);


    static void clearPersistentInventory(InventoryService invService, UUID uuid) {
        Snapshot snap = new Snapshot();
        snap.contents = new ItemStack[41];
        snap.armor    = new ItemStack[4];
        snap.offhand  = null;

        snap.level    = 0;
        snap.exp      = 0.0f;
        snap.totalExp = 0;

        invService.save(uuid, snap);
    }
}
