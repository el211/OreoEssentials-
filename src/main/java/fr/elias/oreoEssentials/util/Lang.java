package fr.elias.oreoEssentials.util;

import fr.elias.oreoEssentials.OreoEssentials;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class Lang {
    private static OreoEssentials plugin;
    private static YamlConfiguration cfg;
    private static String prefix = "";

    private Lang() {}

    public static void init(OreoEssentials pl) {
        plugin = pl;

        // lang.yml file on disk
        File f = new File(plugin.getDataFolder(), "lang.yml");
        if (!f.exists()) {
            // First time: copy full default file
            plugin.saveResource("lang.yml", false);
        }

        // Load current server lang.yml
        cfg = YamlConfiguration.loadConfiguration(f);

        // Load default lang.yml from inside the JAR
        YamlConfiguration defCfg = new YamlConfiguration();
        try (InputStream is = plugin.getResource("lang.yml")) {
            if (is != null) {
                defCfg = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(is, StandardCharsets.UTF_8)
                );
            } else {
                plugin.getLogger().warning("[Lang] Could not find default lang.yml in JAR.");
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("[Lang] Failed to load default lang.yml from JAR: " + ex.getMessage());
        }

        // Merge: add any missing keys from default into server file
        if (defCfg != null) {
            boolean changed = false;

            for (String key : defCfg.getKeys(true)) {
                // Skip sections; only copy actual values
                Object defVal = defCfg.get(key);
                if (defVal instanceof ConfigurationSection) {
                    continue;
                }

                if (!cfg.contains(key)) {
                    cfg.set(key, defVal);
                    changed = true;
                }
            }

            if (changed) {
                try {
                    cfg.save(f);
                    plugin.getLogger().info("[Lang] Added missing lang.yml keys from default.");
                } catch (IOException e) {
                    plugin.getLogger().warning("[Lang] Failed to save merged lang.yml: " + e.getMessage());
                }
            }
        }

        // Initialize prefix after we have the merged config
        prefix = color(get("general.prefix", ""));
    }

    public static String get(String path, String def) {
        return cfg.getString(path, def);
    }

    public static java.util.List<String> getList(String path) {
        return cfg.getStringList(path);
    }

    public static String msg(String path, Map<String, String> vars, Player papiFor) {
        String raw = get(path, "");
        if (raw.isEmpty()) return "";

        // inject %prefix%
        raw = raw.replace("%prefix%", prefix);

        // simple variables (%kit_name%, %seconds%, etc.)
        if (vars != null) {
            for (var e : vars.entrySet()) {
                raw = raw.replace("%" + e.getKey() + "%", String.valueOf(e.getValue()));
            }
        }

        // PAPI (optional)
        if (papiFor != null && plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                raw = PlaceholderAPI.setPlaceholders(papiFor, raw);
            } catch (Throwable ignored) {}
        }

        return color(raw);
    }

    public static void send(CommandSender to, String path, Map<String, String> vars, Player papiFor) {
        String s = msg(path, vars, papiFor);
        if (!s.isEmpty()) to.sendMessage(s);
    }

    public static String color(String s) {
        return s == null ? "" : s.replace('&', '§').replace("\\n", "\n");
    }

    public static boolean getBool(String path, boolean def) {
        return cfg.getBoolean(path, def);
    }

    public static double getDouble(String path, double def) {
        return cfg.getDouble(path, def);
    }

    public static String timeHuman(long seconds) {
        // lang-driven humanizer
        boolean humanize = cfg.getBoolean("kits.time.humanize", true);
        if (!humanize) return seconds + "s";

        long days = seconds / 86400; seconds %= 86400;
        long hours = seconds / 3600; seconds %= 3600;
        long minutes = seconds / 60; seconds %= 60;
        long secs = seconds;

        String ud = get("kits.time.units.day", "d");
        String uh = get("kits.time.units.hour", "h");
        String um = get("kits.time.units.minute", "m");
        String us = get("kits.time.units.second", "s");
        int maxChunks = cfg.getInt("kits.time.max-chunks", 3);

        StringBuilder out = new StringBuilder();
        int chunks = 0;
        if (days > 0 && chunks < maxChunks) { out.append(days).append(ud).append(' '); chunks++; }
        if (hours > 0 && chunks < maxChunks) { out.append(hours).append(uh).append(' '); chunks++; }
        if (minutes > 0 && chunks < maxChunks) { out.append(minutes).append(um).append(' '); chunks++; }
        if (secs > 0 && chunks < maxChunks) { out.append(secs).append(us).append(' '); chunks++; }
        String s = out.toString().trim();
        return s.isEmpty() ? "0" + us : s;
    }

    // ------------------------------------------------------------------------
    // TITLE HELPERS
    // ------------------------------------------------------------------------

    /**
     * Sends a title/subtitle using lang.yml keys.
     *
     * @param player   target player
     * @param titleKey lang.yml path for the title (can be null/empty)
     * @param subKey   lang.yml path for the subtitle (can be null/empty)
     * @param vars     placeholders like "warp" -> "spawn"
     * @param fadeIn   fade in ticks
     * @param stay     stay ticks
     * @param fadeOut  fade out ticks
     */
    public static void sendTitle(Player player,
                                 String titleKey,
                                 String subKey,
                                 Map<String, String> vars,
                                 int fadeIn,
                                 int stay,
                                 int fadeOut) {
        if (player == null) return;

        String title = "";
        String subtitle = "";

        if (titleKey != null && !titleKey.isEmpty()) {
            title = msg(titleKey, vars, player);
        }
        if (subKey != null && !subKey.isEmpty()) {
            subtitle = msg(subKey, vars, player);
        }

        player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
    }

    /**
     * Overload with default fade timings (10,40,10).
     */
    public static void sendTitle(Player player,
                                 String titleKey,
                                 String subKey,
                                 Map<String, String> vars) {
        sendTitle(player, titleKey, subKey, vars, 10, 40, 10);
    }
}
