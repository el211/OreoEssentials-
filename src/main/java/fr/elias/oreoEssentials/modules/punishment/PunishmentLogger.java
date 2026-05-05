package fr.elias.oreoEssentials.modules.punishment;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Logs all punishments (ban, tempban, kick, mute, tempmute, warn) for /history.
 * Stored in plugins/OreoEssentials/punishment_history.yml
 */
public class PunishmentLogger {

    public enum PunishType { BAN, TEMPBAN, UNBAN, KICK, MUTE, TEMPMUTE, UNMUTE, WARN, UNWARN }

    public record PunishEntry(
        String id,
        UUID targetUuid,
        String targetName,
        UUID staffUuid,
        String staffName,
        PunishType type,
        String reason,
        long issuedAt,
        long expiresAt   // -1 = permanent/no expiry
    ) {}

    private final Plugin plugin;
    private final File   file;
    private FileConfiguration cfg;

    /** playerUuid -> list of history entries */
    private final Map<UUID, List<PunishEntry>> history = new ConcurrentHashMap<>();

    public PunishmentLogger(Plugin plugin) {
        this.plugin = plugin;
        this.file   = new File(plugin.getDataFolder(), "punishment_history.yml");
        if (!file.exists()) {
            try { plugin.getDataFolder().mkdirs(); file.createNewFile(); }
            catch (IOException e) { plugin.getLogger().severe("[History] Cannot create file: " + e.getMessage()); }
        }
        this.cfg = YamlConfiguration.loadConfiguration(file);
        loadAll();
    }

    // ---------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------

    public void log(UUID targetUuid, String targetName,
                    UUID staffUuid, String staffName,
                    PunishType type, String reason, long expiresAt) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        PunishEntry entry = new PunishEntry(id, targetUuid, targetName,
                staffUuid, staffName, type, reason, System.currentTimeMillis(), expiresAt);
        history.computeIfAbsent(targetUuid, k -> new ArrayList<>()).add(entry);
        saveEntry(targetUuid, entry);
    }

    /** Convenience overload with no expiry. */
    public void log(UUID targetUuid, String targetName,
                    UUID staffUuid, String staffName,
                    PunishType type, String reason) {
        log(targetUuid, targetName, staffUuid, staffName, type, reason, -1);
    }

    public List<PunishEntry> getHistory(UUID player) {
        return Collections.unmodifiableList(history.getOrDefault(player, Collections.emptyList()));
    }

    public void clearHistory(UUID player) {
        history.remove(player);
        cfg.set("history." + player, null);
        saveFile();
    }

    // ---------------------------------------------------------------
    // Persistence
    // ---------------------------------------------------------------

    private void loadAll() {
        var root = cfg.getConfigurationSection("history");
        if (root == null) return;
        for (String uuidStr : root.getKeys(false)) {
            try {
                UUID uuid  = UUID.fromString(uuidStr);
                var section = cfg.getConfigurationSection("history." + uuidStr);
                if (section == null) continue;
                List<PunishEntry> list = new ArrayList<>();
                for (String key : section.getKeys(false)) {
                    String p = "history." + uuidStr + "." + key + ".";
                    try {
                        UUID targetUuid  = UUID.fromString(cfg.getString(p + "targetUuid", uuidStr));
                        String targetName = cfg.getString(p + "targetName", "Unknown");
                        UUID staffUuid   = UUID.fromString(cfg.getString(p + "staffUuid",
                                "00000000-0000-0000-0000-000000000000"));
                        String staffName = cfg.getString(p + "staffName", "Console");
                        PunishType type  = PunishType.valueOf(cfg.getString(p + "type", "KICK"));
                        String reason    = cfg.getString(p + "reason", "");
                        long issuedAt    = cfg.getLong(p + "issuedAt", 0);
                        long expiresAt   = cfg.getLong(p + "expiresAt", -1);
                        list.add(new PunishEntry(key, targetUuid, targetName, staffUuid, staffName,
                                type, reason, issuedAt, expiresAt));
                    } catch (Exception ignored) {}
                }
                if (!list.isEmpty()) history.put(uuid, list);
            } catch (Exception ignored) {}
        }
    }

    private void saveEntry(UUID player, PunishEntry e) {
        String base = "history." + player + "." + e.id() + ".";
        cfg.set(base + "targetUuid",  e.targetUuid().toString());
        cfg.set(base + "targetName",  e.targetName());
        cfg.set(base + "staffUuid",   e.staffUuid().toString());
        cfg.set(base + "staffName",   e.staffName());
        cfg.set(base + "type",        e.type().name());
        cfg.set(base + "reason",      e.reason());
        cfg.set(base + "issuedAt",    e.issuedAt());
        cfg.set(base + "expiresAt",   e.expiresAt());
        saveFile();
    }

    private void saveFile() {
        try { cfg.save(file); }
        catch (IOException e) { plugin.getLogger().severe("[History] Failed to save: " + e.getMessage()); }
    }
}
