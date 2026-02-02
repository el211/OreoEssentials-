package fr.elias.oreoEssentials.modules.daily;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

public final class RewardsConfig {

    public static final class DayDef {
        public final int day;
        public final String name;
        public final Material icon;
        public final boolean enchanted;
        public final List<String> commands;
        public final String message;
        public final Integer guiPos;
        public final Integer guiPage;
        public final Integer customModelData;

        DayDef(int day, String name, Material icon, boolean enchanted,
               List<String> commands, String message,
               Integer guiPos, Integer guiPage, Integer customModelData) {
            this.day = day;
            this.name = name;
            this.icon = icon;
            this.enchanted = enchanted;
            this.commands = commands;
            this.message = message;
            this.guiPos = guiPos;
            this.guiPage = guiPage;
            this.customModelData = customModelData;
        }
    }

    private final OreoEssentials plugin;
    private final Map<Integer, DayDef> days = new LinkedHashMap<>();
    private int maxDay = 0;

    public RewardsConfig(OreoEssentials plugin) { this.plugin = plugin; }

    public void load() {
        days.clear();
        maxDay = 0;

        File f = new File(plugin.getDataFolder(), "dailyrewards.yml");
        if (!f.exists()) plugin.saveResource("dailyrewards.yml", false);

        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        ConfigurationSection root = y.getConfigurationSection("Rewards");
        if (root == null) {
            plugin.getLogger().warning("[Daily] 'Rewards' section not found in dailyrewards.yml.");
            return;
        }

        for (String key : root.getKeys(false)) {
            if (!key.startsWith("Day ")) continue;
            try {
                int day = Integer.parseInt(key.substring(4).trim());
                ConfigurationSection s = root.getConfigurationSection(key);
                if (s == null) continue;

                String name = s.getString("RewardName", "");
                String iconStr = s.getString("RewardIcon", "SUNFLOWER");
                Material mat = Material.matchMaterial(
                        iconStr == null ? "SUNFLOWER" : iconStr.toUpperCase(Locale.ROOT)
                );
                if (mat == null) mat = Material.SUNFLOWER;

                List<String> cmds = s.getStringList("RewardCommands");
                if (cmds == null) cmds = Collections.emptyList();

                String msg = s.getString("RewardMessage", null);

                boolean enchanted = false;
                Integer guiPos = null, guiPage = null, cmd = null;

                if (s.isConfigurationSection("Extras")) {
                    ConfigurationSection ex = s.getConfigurationSection("Extras");
                    enchanted = ex.getBoolean("Enchanted", false);
                    if (ex.isSet("GUI Position")) guiPos = ex.getInt("GUI Position");
                    if (ex.isSet("GUI Page"))     guiPage = ex.getInt("GUI Page");
                    if (ex.isSet("CustomModelData")) {
                        int v = ex.getInt("CustomModelData", 0);
                        if (v > 0) cmd = v;
                    }
                }

                days.put(day, new DayDef(day, name, mat, enchanted, cmds, msg, guiPos, guiPage, cmd));
                maxDay = Math.max(maxDay, day);
            } catch (Exception ex) {
                plugin.getLogger().warning("[Daily] Failed to parse rewards key '" + key + "': " + ex.getMessage());
            }
        }

        plugin.getLogger().info("[Daily] Loaded " + days.size() + " rewards (max day=" + maxDay + ").");
    }

    public int maxDay() { return maxDay; }
    public DayDef day(int d) { return days.get(d); }
    public Collection<DayDef> all() { return days.values(); }
}
