package fr.elias.oreoEssentials.modules.enderchest;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.UUID;
import java.util.logging.Logger;

public class YamlEnderChestStorage implements EnderChestStorage {

    private final Logger log;
    private final File file;
    private FileConfiguration cfg;

    public YamlEnderChestStorage(OreoEssentials plugin) {
        this.log  = plugin.getLogger();
        this.file = new File(plugin.getDataFolder(), "enderchests.yml");
        if (!file.exists()) {
            try { file.createNewFile(); } catch (Exception ignored) {}
        }
        reload();
    }

    private void reload() {
        try {
            this.cfg = YamlConfiguration.loadConfiguration(file);
        } catch (Exception e) {
            this.cfg = new YamlConfiguration();
            log.warning("[EC] Failed to load enderchests.yml: " + e.getMessage());
        }
    }

    private void saveFile() {
        try {
            cfg.save(file);
        } catch (Exception e) {
            log.warning("[EC] Failed to save enderchests.yml: " + e.getMessage());
        }
    }

    @Override
    public ItemStack[] load(UUID playerId, int rows) {
        try {
            String base = "players." + playerId;
            String data = cfg.getString(base + ".data", null);
            ItemStack[] items = EnderChestStorage.deserialize(data);
            return EnderChestStorage.clamp(items, rows);
        } catch (Exception e) {
            log.warning("[EC] YAML load failed for " + playerId + ": " + e.getMessage());
            return null;
        }
    }

    @Override
    public void save(UUID playerId, int rows, ItemStack[] contents) {
        try {
            ItemStack[] clipped = EnderChestStorage.clamp(contents, rows);
            String data = EnderChestStorage.serialize(clipped);
            String base = "players." + playerId;
            cfg.set(base + ".rows", rows);
            cfg.set(base + ".data", data);
            cfg.set(base + ".updatedAt", System.currentTimeMillis());
            saveFile();
        } catch (Exception e) {
            log.warning("[EC] YAML save failed for " + playerId + ": " + e.getMessage());
        }
    }
}
