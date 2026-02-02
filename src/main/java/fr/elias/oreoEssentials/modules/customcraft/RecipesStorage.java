package fr.elias.oreoEssentials.modules.customcraft;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RecipesStorage {
    private final File file;
    private final FileConfiguration cfg;

    public RecipesStorage(Plugin plugin) {
        this.file = new File(plugin.getDataFolder(), "recipes.yml");
        this.cfg = YamlConfiguration.loadConfiguration(file);
    }

    public Map<String, CustomRecipe> loadAll() {
        Map<String, CustomRecipe> out = new LinkedHashMap<>();
        ConfigurationSection root = cfg.getConfigurationSection("recipes");
        if (root == null) return out;

        for (String name : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(name);
            if (sec == null) continue;

            ItemStack result = sec.getItemStack("result");
            List<?> gridListRaw = sec.getList("grid");
            if (gridListRaw == null || gridListRaw.size() != 9) continue;

            ItemStack[] grid = new ItemStack[9];
            for (int i = 0; i < 9; i++) grid[i] = (ItemStack) gridListRaw.get(i);

            boolean shapeless = sec.getBoolean("shapeless", false);
            String permission = sec.getString("permission", null);

            CustomRecipe r = new CustomRecipe(name, grid, result, shapeless, permission);
            if (r.isValid()) out.put(name, r);
        }
        return out;
    }

    public void save(CustomRecipe r) throws IOException {
        String base = "recipes." + r.getName();
        cfg.set(base + ".result", r.getResult());
        cfg.set(base + ".grid", r.asList());
        cfg.set(base + ".shapeless", r.isShapeless());
        cfg.set(base + ".permission", r.getPermission());
        cfg.save(file);
    }

    public void delete(String name) throws IOException {
        cfg.set("recipes." + name, null);
        cfg.save(file);
    }
}
