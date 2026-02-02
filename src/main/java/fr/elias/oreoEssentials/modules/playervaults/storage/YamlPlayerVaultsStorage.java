package fr.elias.oreoEssentials.modules.playervaults.storage;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.playervaults.PlayerVaultsStorage;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public final class YamlPlayerVaultsStorage implements PlayerVaultsStorage {
    private final File dir;

    public YamlPlayerVaultsStorage(OreoEssentials plugin) {
        this.dir = new File(plugin.getDataFolder(), "vaults");
        if (!dir.exists()) dir.mkdirs();
    }

    private File file(UUID id) { return new File(dir, id.toString() + ".yml"); }

    @Override
    public VaultSnapshot load(UUID playerId, int vaultId) {
        File f = file(playerId);
        if (!f.exists()) return null;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        int rows = y.getInt("vaults."+vaultId+".rows", 3);
        ItemStack[] items = ((java.util.List<ItemStack>) y.getList("vaults."+vaultId+".contents", java.util.List.of()))
                .toArray(new ItemStack[0]);
        return new VaultSnapshot(rows, items);
    }

    @Override
    public void save(UUID playerId, int vaultId, int rows, ItemStack[] contents) {
        try {
            File f = file(playerId);
            YamlConfiguration y = f.exists() ? YamlConfiguration.loadConfiguration(f) : new YamlConfiguration();
            y.set("vaults."+vaultId+".rows", rows);
            y.set("vaults."+vaultId+".contents", java.util.Arrays.asList(contents));
            y.save(f);
        } catch (IOException ignored) {}
    }
}
