package fr.elias.oreoEssentials.config;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public class ConfigService {
    private final OreoEssentials plugin;

    public ConfigService(OreoEssentials plugin) {
        this.plugin = plugin;
        FileConfiguration c = plugin.getConfig();
        c.addDefault("homes.maxPerPlayer", 5);
        c.addDefault("homes.permissionBased", true);
        c.addDefault("tpa.timeoutSeconds", 60);

        c.addDefault("storage.useMongo", false);
        c.addDefault("storage.mongo.uri", "mongodb://localhost:27017");
        c.addDefault("storage.mongo.database", "oreo");
        c.addDefault("storage.mongo.collectionPrefix", "oreo_");

        c.addDefault("server.name", Bukkit.getServer().getName());

        c.options().copyDefaults(true);
    }

    public String serverName() {
        return plugin.getConfig().getString("server.name", Bukkit.getServer().getName());
    }

    public int tpaTimeoutSeconds() { return plugin.getConfig().getInt("tpa.timeoutSeconds", 60); }

    public boolean mongoEnabled() { return plugin.getConfig().getBoolean("storage.useMongo", false); }
    public String mongoUri() { return plugin.getConfig().getString("storage.mongo.uri"); }
    public String mongoDatabase() { return plugin.getConfig().getString("storage.mongo.database"); }
    public String mongoPrefix() { return plugin.getConfig().getString("storage.mongo.collectionPrefix", "oreo_"); }

    public int defaultMaxHomes() { return plugin.getConfig().getInt("homes.maxPerPlayer", 5); }
    public boolean permissionBased() { return plugin.getConfig().getBoolean("homes.permissionBased", true); }

    public int getMaxHomesFor(CommandSender sender) {
        if (!(sender instanceof Player p)) return defaultMaxHomes();
        int max = defaultMaxHomes();
        if (!permissionBased()) return max;

        for (var ep : p.getEffectivePermissions()) {
            if (!ep.getValue()) continue;
            String perm = ep.getPermission().toLowerCase();
            if (!perm.startsWith("oreo.homes.")) continue;
            String tail = perm.substring("oreo.homes.".length());
            try {
                int n = Integer.parseInt(tail);
                if (n > max) max = n;
            } catch (NumberFormatException ignored) {}
        }
        return max;
    }


}
