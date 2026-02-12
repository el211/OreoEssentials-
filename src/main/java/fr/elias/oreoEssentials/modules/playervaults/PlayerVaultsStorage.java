package fr.elias.oreoEssentials.modules.playervaults;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public interface PlayerVaultsStorage {
    /** Load a vault's rows and contents. If none exists, return null to let service default it. */
    VaultSnapshot load(UUID playerId, int vaultId);

    /** Save a vault's rows and contents. */
    void save(UUID playerId, int vaultId, int rows, ItemStack[] contents);

    /** Optional bulk save on shutdown; default no-op. */
    default void flush() {}

    record VaultSnapshot(int rows, ItemStack[] contents) {}
}
