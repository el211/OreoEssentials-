package fr.elias.oreoEssentials.modules.playervaults.storage;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.playervaults.PlayerVaultsStorage;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class YamlPlayerVaultsStorage implements PlayerVaultsStorage {
    private final File dir;
    private final OreoEssentials plugin;

    // PV-6: Per-UUID lock objects to prevent concurrent read-modify-write races
    private final ConcurrentHashMap<UUID, Object> locks = new ConcurrentHashMap<>();

    private Object getLock(UUID uuid) {
        return locks.computeIfAbsent(uuid, k -> new Object());
    }

    public YamlPlayerVaultsStorage(OreoEssentials plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "vaults");
        if (!dir.exists()) dir.mkdirs();
    }

    private File file(UUID id) { return new File(dir, id.toString() + ".yml"); }

    @Override
    public VaultSnapshot load(UUID playerId, int vaultId) {
        synchronized (getLock(playerId)) {
            File f = file(playerId);
            if (!f.exists()) return null;
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            int rows = y.getInt("vaults." + vaultId + ".rows", 3);
            ItemStack[] items = ((java.util.List<ItemStack>) y.getList("vaults." + vaultId + ".contents", java.util.List.of()))
                    .toArray(new ItemStack[0]);
            return new VaultSnapshot(rows, items);
        }
    }

    @Override
    public void save(UUID playerId, int vaultId, int rows, ItemStack[] contents) {
        // PV-6: Synchronize on per-UUID lock to prevent concurrent save races
        synchronized (getLock(playerId)) {
            try {
                File f = file(playerId);
                YamlConfiguration y = f.exists() ? YamlConfiguration.loadConfiguration(f) : new YamlConfiguration();
                y.set("vaults." + vaultId + ".rows", rows);
                y.set("vaults." + vaultId + ".contents", java.util.Arrays.asList(contents));
                y.save(f);
            } catch (IOException e) {
                // PV-1: Log at SEVERE so item loss is never silent
                plugin.getLogger().log(Level.SEVERE, "CRITICAL: Failed to save vault for " + playerId, e);
            }
        }
    }
}
