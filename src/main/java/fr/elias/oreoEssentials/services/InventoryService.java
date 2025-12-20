package fr.elias.oreoEssentials.services;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public interface InventoryService {

    final class Snapshot {
        public ItemStack[] contents; // 0..40
        public ItemStack[] armor;    // 4
        public ItemStack   offhand;  // 1

        //  XP FIELDS
        /**
         * Player level (green number above the hotbar).
         */
        public int level;

        /**
         * Progress bar 0.0f–1.0f for the current level.
         */
        public float exp;

        /**
         * Optional: total experience points.
         * Good to keep if you already care about exact exp.
         */
        public int totalExp;
    }

    Snapshot load(UUID uuid);                 // return null => empty

    void save(UUID uuid, Snapshot snapshot);  // persist + mark pending if target offline

    // 👇 This helper really shouldn't be here as a private interface method,
    //     but if you want to keep it, make it static utility instead:
    static void clearPersistentInventory(InventoryService invService, UUID uuid) {
        Snapshot snap = new Snapshot();
        snap.contents = new ItemStack[41];
        snap.armor    = new ItemStack[4];
        snap.offhand  = null;

        // XP reset as well, if you *really* want to clear everything:
        snap.level    = 0;
        snap.exp      = 0.0f;
        snap.totalExp = 0;

        invService.save(uuid, snap);
    }
}
