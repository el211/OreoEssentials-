package fr.elias.oreoEssentials.modules.rtp.listeners;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.spawn.SpawnService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.Locale;

public final class DeathRespawnListener implements Listener {
    private final OreoEssentials plugin;

    public DeathRespawnListener(OreoEssentials plugin) {
        this.plugin = plugin;
    }

    enum Mode { NORMAL, GLOBAL_SPAWN, WORLD_SPAWN, CUSTOM }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        ConfigurationSection root = plugin.getSettingsConfig().getRoot();
        if (root == null) return;

        ConfigurationSection sec = root.getConfigurationSection("features.death-respawn");
        if (sec == null || !sec.getBoolean("enabled", false)) return;

        final boolean respectBedAnchor = sec.getBoolean("respect-bed-anchor", true);
        final Mode mode = parseMode(sec.getString("mode", "NORMAL"));

        Player p = event.getPlayer();

        // If respecting bed/anchor, skip override when a bed or respawn anchor is valid.
        if (respectBedAnchor && (event.isAnchorSpawn() || p.getBedSpawnLocation() != null)) return;

        Location target = null;

        switch (mode) {
            case NORMAL:
                return; // vanilla
            case GLOBAL_SPAWN: {
                // Use your SpawnService (same as /spawn) to keep behavior consistent.
                SpawnService spawn = plugin.getSpawnService();
                if (spawn != null) target = spawn.getSpawn(); // may be null
                break;
            }
            case WORLD_SPAWN: {
                World w = p.getWorld();
                if (w != null) target = w.getSpawnLocation();
                break;
            }
            case CUSTOM: {
                // Per-world override takes precedence; else use the default custom location.
                Location perWorld = readCustomForWorld(sec.getConfigurationSection("per-world"), p.getWorld());
                target = (perWorld != null) ? perWorld : readCustom(sec.getConfigurationSection("custom"));
                break;
            }
        }

        if (target == null) return;
        // Minimal safety: ensure world is loaded; if not, try to load by name for CUSTOM
        if (target.getWorld() == null && sec.isConfigurationSection("custom")) {
            String worldName = sec.getConfigurationSection("custom").getString("world", "");
            World w = Bukkit.getWorld(worldName);
            if (w != null) target.setWorld(w);
        }
        if (target.getWorld() == null) return;

        event.setRespawnLocation(target);
    }

    private Mode parseMode(String s) {
        try { return Mode.valueOf(s.toUpperCase(Locale.ROOT)); }
        catch (Exception ignored) { return Mode.NORMAL; }
    }

    private Location readCustom(ConfigurationSection cs) {
        if (cs == null) return null;
        String world = cs.getString("world", "");
        World w = Bukkit.getWorld(world);
        if (w == null) return null;

        double x = cs.getDouble("x", w.getSpawnLocation().getX());
        double y = cs.getDouble("y", w.getSpawnLocation().getY());
        double z = cs.getDouble("z", w.getSpawnLocation().getZ());
        float yaw = (float) cs.getDouble("yaw", 0.0);
        float pitch = (float) cs.getDouble("pitch", 0.0);
        return new Location(w, x, y, z, yaw, pitch);
    }

    private Location readCustomForWorld(ConfigurationSection perWorld, World current) {
        if (perWorld == null || current == null) return null;
        ConfigurationSection cs = perWorld.getConfigurationSection(current.getName());
        return readCustom(cs);
    }
}
