package fr.elias.oreoEssentials.rtp;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.*;
import java.util.function.Predicate;

public final class RtpConfig {
    private final OreoEssentials plugin;
    private File file;
    private volatile FileConfiguration cfg;

    // Master toggle
    private volatile boolean enabled = true;

    // cached lookups
    private Set<String> allowedWorlds = Collections.emptySet();
    private Set<String> unsafeBlocks  = Collections.emptySet();

    // ✅ NEW: cross-server RTP
    private boolean crossServerEnabled = false;
    private Map<String, String> worldServers = Collections.emptyMap(); // world -> server
    private String defaultTargetWorld; // optional

    public RtpConfig(OreoEssentials plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        try {
            if (file == null) file = new File(plugin.getDataFolder(), "rtp.yml");
            if (!file.exists()) plugin.saveResource("rtp.yml", false);
            cfg = YamlConfiguration.loadConfiguration(file);
        } catch (Exception e) {
            plugin.getLogger().warning("[RTP] Failed to load rtp.yml: " + e.getMessage());
            cfg = new YamlConfiguration();
        }

        // Master toggle (defaults to true)
        enabled = cfg.getBoolean("enabled", true);

        // refresh caches
        List<String> aw = cfg.getStringList("allowed-worlds");
        allowedWorlds = new HashSet<>(aw == null ? Collections.emptyList() : aw);

        List<String> ub = cfg.getStringList("unsafe-blocks");
        unsafeBlocks = new HashSet<>(ub == null ? Collections.emptyList() : ub);

        // ✅ NEW: cross-server section
        var cs = cfg.getConfigurationSection("cross-server");
        if (cs != null) {
            crossServerEnabled = cs.getBoolean("enabled", false);
            defaultTargetWorld = cs.getString("default-target-world", null);

            Map<String, String> ws = new HashMap<>();
            var wsSec = cs.getConfigurationSection("world-servers");
            if (wsSec != null) {
                for (String worldName : wsSec.getKeys(false)) {
                    String serverName = wsSec.getString(worldName);
                    if (serverName != null && !serverName.isBlank()) {
                        ws.put(worldName, serverName);
                    }
                }
            }
            worldServers = ws;
        } else {
            crossServerEnabled = false;
            defaultTargetWorld = null;
            worldServers = Collections.emptyMap();
        }
    }

    /** Persist ONLY the toggle so we don't rewrite the user's YAML layout. */
    public void save() {
        if (cfg == null || file == null) return;
        try {
            cfg.set("enabled", enabled);
            cfg.save(file);
        } catch (Exception e) {
            plugin.getLogger().warning("[RTP] Failed to save rtp.yml: " + e.getMessage());
        }
    }

    /* ----------------- Basic getters ----------------- */

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { this.enabled = v; }
    public boolean toggleEnabled() { this.enabled = !this.enabled; return this.enabled; }

    public int attempts() { return cfg.getInt("attempts", 30); }
    public int minY()     { return cfg.getInt("min-y", 50); }
    public int maxY()     { return cfg.getInt("max-y", 200); }

    public Set<String> allowedWorlds() { return allowedWorlds; }
    public Set<String> unsafeBlocks()  { return unsafeBlocks; }

    /** True if world is allowed (empty list means all). */
    public boolean isWorldAllowed(World w) {
        return allowedWorlds.isEmpty() || allowedWorlds.contains(w.getName());
    }

    /* ----------------- ✅ Cross-server helpers ----------------- */

    public boolean isCrossServerEnabled() {
        return crossServerEnabled;
    }

    /** Returns the server name hosting this world, or null if unknown. */
    public String serverForWorld(String worldName) {
        if (worldName == null) return null;
        return worldServers.get(worldName);
    }

    /** Returns the default target world for RTP, or null if not configured. */
    public String getDefaultTargetWorld() {
        return defaultTargetWorld;
    }

    /** All configured world -> server mappings (read-only copy). */
    public Map<String, String> worldServerMappings() {
        return Collections.unmodifiableMap(worldServers);
    }

    /**
     * Decide the *logical* target world for this player:
     * - if current world is allowed: keep it
     * - else, if default-target-world is set and allowed: use that
     * - else, null (no valid world)
     */
    public String chooseTargetWorld(Player p) {
        String current = p.getWorld().getName();

        if (isWorldAllowed(p.getWorld())) {
            return current;
        }

        if (defaultTargetWorld != null && !defaultTargetWorld.isBlank()) {
            if (allowedWorlds.isEmpty() || allowedWorlds.contains(defaultTargetWorld)) {
                return defaultTargetWorld;
            }
        }

        return null;
    }
    public int minRadiusFor(Player p, String worldName) {
        // 1) Base global min-radius
        int base = Math.max(0, cfg.getInt("min-radius", 0));

        // 2) Global per-tier overrides (optional)
        base = applyTierOverrides(p, base, "min-radius-tiers");

        // 3) Per-world overrides
        if (worldName != null && cfg.isConfigurationSection("worlds." + worldName)) {
            String root = "worlds." + worldName;

            base = Math.max(0, cfg.getInt(root + ".min-radius", base));

            // 4) Per-world per-tier overrides (optional)
            base = applyTierOverrides(p, base, root + ".min-radius-tiers");
        }

        return base;
    }

    private int applyTierOverrides(Player p, int current, String sectionPath) {
        if (!cfg.isConfigurationSection(sectionPath)) return current;

        // Keys are permission strings like "oreo.tier.vip"
        var sec = cfg.getConfigurationSection(sectionPath);
        if (sec == null) return current;

        int best = current;

        for (String perm : sec.getKeys(false)) {
            if (p.hasPermission(perm)) {
                best = Math.max(best, sec.getInt(perm, current));
            }
        }
        return best;
    }

    /**
     * Version qui priorise un monde demandé explicitement.
     */
    public String chooseTargetWorld(Player p, String requestedWorld) {
        // 1) Si un monde est demandé
        if (requestedWorld != null && !requestedWorld.isBlank()) {
            if (allowedWorlds.isEmpty() || allowedWorlds.contains(requestedWorld)) {
                return requestedWorld;
            }
        }

        // 2) Sinon, fallback sur l’ancienne logique
        return chooseTargetWorld(p);
    }

    /* ----------------- Radius logic (inchangé) ----------------- */

    /**
     * Get the best radius for a player, honoring per-world overrides and tier permissions.
     * Falls back to global "default" when no world override applies.
     */
    public int radiusFor(Player p, Collection<String> tierPermissionKeys) {
        // We ignore tierPermissionKeys for now and always check real permission nodes.
        Predicate<String> hasPerm = p::hasPermission;

        // Check per-world section first
        String worldKey = "worlds." + p.getWorld().getName();
        if (cfg.isConfigurationSection(worldKey)) {
            int worldDefault = cfg.getInt(worldKey + ".default", cfg.getInt("default", 200));
            int best = worldDefault;

            for (String key : cfg.getConfigurationSection(worldKey).getKeys(false)) {
                if ("default".equalsIgnoreCase(key)) continue;

                int val = cfg.getInt(worldKey + "." + key, -1);
                if (val <= 0) continue;

                // 🔴 IMPORTANT: keys like "vip" → permission "oreo.tier.vip"
                String permNode = "oreo.tier." + key;
                if (hasPerm.test(permNode) && val > best) {
                    best = val;
                }
            }
            return best;
        }

        // Fallback: global section
        return bestRadiusFor(permNode -> p.hasPermission(permNode));
    }

    /** Backward-compatible global best-radius calculator. */
    public int bestRadiusFor(Predicate<String> hasTierPerm) {
        int best = cfg.getInt("default", 200);

        for (String key : cfg.getKeys(false)) {
            if (isNonTierKey(key)) continue;

            int val = cfg.getInt(key, -1);
            if (val <= 0) continue;

            // 🔴 IMPORTANT: keys like "vip" → permission "oreo.tier.vip"
            String permNode = "oreo.tier." + key;
            if (hasTierPerm.test(permNode) && val > best) {
                best = val;
            }
        }
        return best;
    }

    /* ----------------- ✅ Cooldown logic (NOUVEAU) ----------------- */

    /**
     * Cooldown RTP en secondes pour un joueur, basé sur:
     * - cooldown.worlds.<world>.default
     * - cooldown.worlds.<world>.<tier>
     * - cooldown.default
     * - cooldown.<tier>
     *
     * Tiers = mêmes clés que pour le radius :
     *   "vip"  -> permission "oreo.tier.vip"
     *   "legend" -> "oreo.tier.legend"
     *
     * On prend le *plus petit* cooldown parmi les perms que le joueur a.
     */
    public int cooldownFor(Player p) {
        String base = "cooldown";
        // cooldown.global default
        int globalDefault = cfg.getInt(base + ".default", 0);

        // 1) Per-world override : cooldown.worlds.<world>.*
        String worldPath = base + ".worlds." + p.getWorld().getName();
        if (cfg.isConfigurationSection(worldPath)) {
            int best = cfg.getInt(worldPath + ".default", globalDefault);

            var worldSec = cfg.getConfigurationSection(worldPath);
            if (worldSec != null) {
                for (String key : worldSec.getKeys(false)) {
                    if ("default".equalsIgnoreCase(key)) continue;

                    int val = cfg.getInt(worldPath + "." + key, -1);
                    if (val < 0) continue;

                    String permNode = "oreo.tier." + key;
                    if (p.hasPermission(permNode)) {
                        if (best <= 0 || val < best) {
                            best = val;
                        }
                    }
                }
            }
            return best;
        }

        // 2) Sinon, global cooldown: cooldown.*
        int best = globalDefault;
        var cooldownSec = cfg.getConfigurationSection(base);
        if (cooldownSec != null) {
            for (String key : cooldownSec.getKeys(false)) {
                if ("default".equalsIgnoreCase(key) || "worlds".equalsIgnoreCase(key)) continue;

                int val = cooldownSec.getInt(key, -1);
                if (val < 0) continue;

                String permNode = "oreo.tier." + key;
                if (p.hasPermission(permNode)) {
                    if (best <= 0 || val < best) {
                        best = val;
                    }
                }
            }
        }

        return best;
    }

    /* ----------------- internal helpers ----------------- */

    private boolean isNonTierKey(String key) {
        return "enabled".equalsIgnoreCase(key)
                || "default".equalsIgnoreCase(key)
                || "attempts".equalsIgnoreCase(key)
                || "unsafe-blocks".equalsIgnoreCase(key)
                || "allowed-worlds".equalsIgnoreCase(key)
                || "min-y".equalsIgnoreCase(key)
                || "max-y".equalsIgnoreCase(key)
                || "worlds".equalsIgnoreCase(key)
                || "cross-server".equalsIgnoreCase(key)
                || "cooldown".equalsIgnoreCase(key); // ✅ important: ignorer la section cooldown pour le radius
    }
}
