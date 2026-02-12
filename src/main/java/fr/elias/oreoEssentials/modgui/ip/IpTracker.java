// package: fr.elias.oreoEssentials.modgui.ip
package fr.elias.oreoEssentials.modgui.ip;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class IpTracker implements Listener {

    private final OreoEssentials plugin;
    private final File file;
    private final FileConfiguration cfg;

    public IpTracker(OreoEssentials plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "ips.yml");
        if (!file.exists()) try { file.createNewFile(); } catch (Exception ignored) {}
        this.cfg = YamlConfiguration.loadConfiguration(file);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        String ip = p.getAddress() == null ? null : p.getAddress().getAddress().getHostAddress();
        if (ip == null) return;

        String uid = p.getUniqueId().toString();

        // player last ip
        cfg.set("players." + uid + ".last-ip", ip);

        // ip -> uuids
        List<String> list = cfg.getStringList("ips." + ip);
        if (!list.contains(uid)) list.add(uid);
        cfg.set("ips." + ip, list);
        save();
    }

    public String getLastIp(UUID uuid) {
        return cfg.getString("players." + uuid.toString() + ".last-ip", "unknown");
    }

    public List<UUID> getAlts(UUID uuid) {
        String ip = getLastIp(uuid);
        if (ip == null || ip.equals("unknown")) return List.of();

        List<String> list = cfg.getStringList("ips." + ip);
        return list.stream()
                .map(s -> {
                    try { return UUID.fromString(s); } catch (Exception e) { return null; }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private void save() {
        try { cfg.save(file); } catch (Exception e) {
            plugin.getLogger().warning("Failed to save ips.yml: " + e.getMessage());
        }
    }
}
