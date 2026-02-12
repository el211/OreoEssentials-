package fr.elias.oreoEssentials.modules.chat.chatservices;

import com.mongodb.lang.Nullable;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MuteService {

    public static final class MuteData {
        public final UUID uuid;
        public final long until; // epoch millis; <=0 for permanent
        public final String reason;
        public final String by;

        public MuteData(UUID uuid, long until, String reason, String by) {
            this.uuid = uuid;
            this.until = until;
            this.reason = reason == null ? "" : reason;
            this.by = by == null ? "" : by;
        }

        public boolean expired() {
            return until > 0 && System.currentTimeMillis() > until;
        }

        public long remainingMs() {
            return until <= 0 ? -1 : Math.max(0, until - System.currentTimeMillis());
        }
    }

    private final Plugin plugin;
    private final File file;
    private final FileConfiguration cfg;
    private final Map<UUID, MuteData> mutes = new ConcurrentHashMap<>();

    public MuteService(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "mutes.yml");
        if (!file.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to create mutes.yml: " + e.getMessage());
            }
        }
        this.cfg = YamlConfiguration.loadConfiguration(file);
        loadAll();
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::cleanupExpired, 20L*60, 20L*60);
    }

    public boolean isMuted(UUID uuid) {
        MuteData md = mutes.get(uuid);
        if (md == null) return false;
        if (md.expired()) {
            unmute(uuid);
            return false;
        }
        return true;
    }

    public @Nullable MuteData get(UUID uuid) {
        MuteData md = mutes.get(uuid);
        if (md != null && md.expired()) {
            unmute(uuid);
            return null;
        }
        return md;
    }

    public void mute(UUID uuid, long untilEpochMillis, String reason, String by) {
        MuteData md = new MuteData(uuid, untilEpochMillis, reason, by);
        mutes.put(uuid, md);
        saveOne(md);
        saveFile();
    }

    public boolean unmute(UUID uuid) {
        MuteData removed = mutes.remove(uuid);
        cfg.set("mutes." + uuid, null);
        saveFile();
        return removed != null;
    }

    public Set<UUID> allMuted() {
        return new HashSet<>(mutes.keySet());
    }

    private void loadAll() {
        mutes.clear();
        var root = cfg.getConfigurationSection("mutes");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                long until = cfg.getLong("mutes."+key+".until", 0);
                String reason = cfg.getString("mutes."+key+".reason", "");
                String by = cfg.getString("mutes."+key+".by", "");
                MuteData md = new MuteData(uuid, until, reason, by);
                if (!md.expired()) mutes.put(uuid, md);
            } catch (Throwable t) {
                plugin.getLogger().warning("Failed to load mute for key " + key + ": " + t.getMessage());
            }
        }
        saveFile();
    }

    private void saveOne(MuteData md) {
        String base = "mutes." + md.uuid + ".";
        cfg.set(base + "until", md.until);
        cfg.set(base + "reason", md.reason);
        cfg.set(base + "by", md.by);
    }

    private void saveFile() {
        try { cfg.save(file); }
        catch (IOException e) { plugin.getLogger().severe("Failed to save mutes.yml: " + e.getMessage()); }
    }

    private void cleanupExpired() {
        boolean changed = false;
        for (UUID u : new ArrayList<>(mutes.keySet())) {
            MuteData md = mutes.get(u);
            if (md != null && md.expired()) {
                mutes.remove(u);
                cfg.set("mutes."+u, null);
                changed = true;
            }
        }
        if (changed) saveFile();
    }

    public static String friendlyRemaining(long ms) {
        if (ms < 0) return "permanent";
        long s = ms/1000;
        long d = s/86400; s %= 86400;
        long h = s/3600;  s %= 3600;
        long m = s/60;    s %= 60;
        var parts = new ArrayList<String>();
        if (d>0) parts.add(d+"d");
        if (h>0) parts.add(h+"h");
        if (m>0) parts.add(m+"m");
        if (s>0 || parts.isEmpty()) parts.add(s+"s");
        return String.join(" ", parts);
    }

    public static long parseDurationToMillis(String token) {
        // Accept: 30s, 10m, 2h, 1d — also plain seconds like "120"
        try {
            token = token.trim().toLowerCase(Locale.ROOT);
            if (token.matches("\\d+")) return Long.parseLong(token) * 1000L;
            long mult = switch (token.charAt(token.length()-1)) {
                case 's' -> 1000L;
                case 'm' -> 60_000L;
                case 'h' -> 3_600_000L;
                case 'd' -> 86_400_000L;
                default -> 1000L;
            };
            long val = Long.parseLong(token.substring(0, token.length()-1));
            return val * mult;
        } catch (Exception e) {
            return -1;
        }
    }

    public static String nameOf(UUID uuid) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        return op != null && op.getName() != null ? op.getName() : uuid.toString();
    }
}
