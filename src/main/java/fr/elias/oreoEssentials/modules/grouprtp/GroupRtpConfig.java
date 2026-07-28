package fr.elias.oreoEssentials.modules.grouprtp;

import fr.elias.oreoEssentials.modules.grouprtp.model.GroupRtpPortalDef;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Logger;

/**
 * Loads and holds all Group-RTP portal definitions from
 * {@code plugins/OreoEssentials/group-rtp.yml}.
 */
public final class GroupRtpConfig {

    private static final String FILE_NAME = "group-rtp.yml";

    private final Plugin   plugin;
    private final Logger   log;
    private final File     file;
    private boolean        enabled;
    private final Map<String, GroupRtpPortalDef> portals = new LinkedHashMap<>();

    public GroupRtpConfig(Plugin plugin) {
        this.plugin = plugin;
        this.log    = plugin.getLogger();
        this.file   = new File(plugin.getDataFolder(), FILE_NAME);
        reload();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public boolean isEnabled() { return enabled; }

    public Map<String, GroupRtpPortalDef> getPortals() {
        return Collections.unmodifiableMap(portals);
    }

    public Optional<GroupRtpPortalDef> get(String id) {
        return Optional.ofNullable(portals.get(id.toLowerCase(Locale.ROOT)));
    }

    public void reload() {
        portals.clear();
        FileConfiguration cfg = loadFile();
        this.enabled = cfg.getBoolean("enabled", true);
        if (!enabled) return;

        ConfigurationSection sec = cfg.getConfigurationSection("portals");
        if (sec == null) return;

        for (String id : sec.getKeys(false)) {
            try {
                GroupRtpPortalDef def = parsePortal(id.toLowerCase(Locale.ROOT), sec.getConfigurationSection(id));
                if (def != null) portals.put(def.getId(), def);
            } catch (Exception e) {
                log.warning("[GroupRTP] Failed to load portal '" + id + "': " + e.getMessage());
            }
        }
        log.info("[GroupRTP] Loaded " + portals.size() + " portal(s).");
    }

    // ── Persistence helpers (for /grouprtp create & delete) ───────────────────

    public void addPortalToFile(String id, GroupRtpPortalDef def) {
        FileConfiguration cfg = loadFile();
        String path = "portals." + id;
        cfg.set(path + ".name",            def.getDisplayName());
        cfg.set(path + ".world",           def.getWorldName());
        cfg.set(path + ".region.pos1",     vecList(def.getBox().getMin()));
        cfg.set(path + ".region.pos2",     vecList(def.getBox().getMax()));
        cfg.set(path + ".rtp.world",       def.getRtpWorld());
        cfg.set(path + ".rtp.server",      def.getRtpServer());
        cfg.set(path + ".rtp.radius",      def.getRtpRadius());
        cfg.set(path + ".rtp.min-radius",  def.getRtpMinRadius());
        cfg.set(path + ".rtp.center-x",    def.getRtpCenterX());
        cfg.set(path + ".rtp.center-z",    def.getRtpCenterZ());
        cfg.set(path + ".rtp.min-y",       def.getRtpMinY());
        cfg.set(path + ".rtp.max-y",       def.getRtpMaxY());
        cfg.set(path + ".rtp.attempts",    def.getRtpAttempts());
        cfg.set(path + ".rtp.unsafe-blocks",      new ArrayList<>(def.getUnsafeBlocks()));
        cfg.set(path + ".rtp.blacklisted-biomes",  new ArrayList<>(def.getBlacklistedBiomes()));
        cfg.set(path + ".group.min-players",      def.getMinPlayers());
        cfg.set(path + ".group.max-players",      def.getMaxPlayers());
        cfg.set(path + ".group.countdown",         def.getCountdownSeconds());
        cfg.set(path + ".group.cluster-radius",    def.getClusterRadius());
        cfg.set(path + ".cooldown",                def.getCooldownMs());
        cfg.set(path + ".permission",              def.getPermission());

        // Messages — write raw (un-translated) values so & codes stay editable in the file
        for (Map.Entry<String, String> e : def.getMessages().entrySet()) {
            cfg.set(path + ".messages." + e.getKey(), e.getValue());
        }

        // Effects
        cfg.set(path + ".effects.ambient-particle",  def.getAmbientParticle());
        cfg.set(path + ".effects.ambient-count",     def.getAmbientCount());
        cfg.set(path + ".effects.teleport-particle", def.getTeleportParticle());
        cfg.set(path + ".effects.teleport-count",    def.getTeleportCount());
        cfg.set(path + ".effects.sound-enter",       def.getSoundEnter());
        cfg.set(path + ".effects.sound-teleport",    def.getSoundTeleport());

        saveFile(cfg);
        portals.put(id, def);
    }

    public void removePortalFromFile(String id) {
        FileConfiguration cfg = loadFile();
        cfg.set("portals." + id, null);
        saveFile(cfg);
        portals.remove(id);
    }

    // ── Parsing ───────────────────────────────────────────────────────────────

    private GroupRtpPortalDef parsePortal(String id, ConfigurationSection s) {
        if (s == null) return null;

        String displayName = c(s.getString("name", id));
        String worldName   = s.getString("world", "world");

        // Region
        List<?> p1l = s.getList("region.pos1");
        List<?> p2l = s.getList("region.pos2");
        if (p1l == null || p2l == null || p1l.size() < 3 || p2l.size() < 3) {
            log.warning("[GroupRTP] Portal '" + id + "' missing region.pos1/pos2 — skipped.");
            return null;
        }
        Vector pos1 = toVec(p1l);
        Vector pos2 = toVec(p2l);
        BoundingBox box = BoundingBox.of(pos1, pos2);

        // RTP
        ConfigurationSection rtp = s.getConfigurationSection("rtp");
        String rtpWorld     = (rtp != null) ? rtp.getString("world", worldName) : worldName;
        String rtpServer    = (rtp != null) ? rtp.getString("server", "")       : "";
        double rtpRadius    = (rtp != null) ? rtp.getDouble("radius",    3000)  : 3000;
        double rtpMinRadius = (rtp != null) ? rtp.getDouble("min-radius", 100)  : 100;
        double rtpCenterX   = (rtp != null) ? rtp.getDouble("center-x",  0)    : 0;
        double rtpCenterZ   = (rtp != null) ? rtp.getDouble("center-z",  0)    : 0;
        int    rtpMinY      = (rtp != null) ? rtp.getInt("min-y",        50)   : 50;
        int    rtpMaxY      = (rtp != null) ? rtp.getInt("max-y",        250)  : 250;
        int    rtpAttempts  = (rtp != null) ? rtp.getInt("attempts",     50)   : 50;

        Set<String> unsafeBlocks = new LinkedHashSet<>(DEFAULT_UNSAFE);
        if (rtp != null && rtp.isList("unsafe-blocks")) {
            unsafeBlocks = new LinkedHashSet<>();
            for (String m : rtp.getStringList("unsafe-blocks")) unsafeBlocks.add(m.toUpperCase());
        }
        Set<String> blacklistedBiomes = new LinkedHashSet<>();
        if (rtp != null && rtp.isList("blacklisted-biomes")) {
            for (String b : rtp.getStringList("blacklisted-biomes")) blacklistedBiomes.add(b.toUpperCase());
        }

        // Group
        ConfigurationSection grp = s.getConfigurationSection("group");
        int    minPlayers      = (grp != null) ? grp.getInt("min-players",  2)    : 2;
        int    maxPlayers      = (grp != null) ? grp.getInt("max-players",  10)   : 10;
        int    countdown       = (grp != null) ? grp.getInt("countdown",    10)   : 10;
        double clusterRadius   = (grp != null) ? grp.getDouble("cluster-radius", 5) : 5;

        long   cooldownMs  = s.getLong("cooldown",    300_000L);
        String permission  = s.getString("permission", "");

        // Messages
        Map<String, String> messages = new LinkedHashMap<>(DEFAULT_MESSAGES);
        ConfigurationSection msgSec = s.getConfigurationSection("messages");
        if (msgSec != null) {
            for (String k : msgSec.getKeys(false)) {
                messages.put(k, c(msgSec.getString(k, "")));
            }
        } else {
            messages.replaceAll((k, v) -> c(v));
        }

        // Effects
        ConfigurationSection fx = s.getConfigurationSection("effects");
        String ambientParticle  = (fx != null) ? fx.getString("ambient-particle",  "PORTAL")            : "PORTAL";
        int    ambientCount     = (fx != null) ? fx.getInt("ambient-count",        3)                   : 3;
        String teleportParticle = (fx != null) ? fx.getString("teleport-particle", "END_ROD")           : "END_ROD";
        int    teleportCount    = (fx != null) ? fx.getInt("teleport-count",       30)                  : 30;
        String soundEnter       = (fx != null) ? fx.getString("sound-enter",       "BLOCK_PORTAL_AMBIENT") : "BLOCK_PORTAL_AMBIENT";
        String soundTeleport    = (fx != null) ? fx.getString("sound-teleport",    "ENTITY_ENDERMAN_TELEPORT") : "ENTITY_ENDERMAN_TELEPORT";

        return new GroupRtpPortalDef(
                id, displayName, worldName, box,
                rtpWorld, rtpServer,
                rtpRadius, rtpMinRadius, rtpCenterX, rtpCenterZ,
                rtpMinY, rtpMaxY, rtpAttempts,
                unsafeBlocks, blacklistedBiomes,
                minPlayers, maxPlayers, countdown, clusterRadius,
                cooldownMs, permission, messages,
                ambientParticle, ambientCount,
                teleportParticle, teleportCount,
                soundEnter, soundTeleport);
    }

    // ── File I/O ──────────────────────────────────────────────────────────────

    private FileConfiguration loadFile() {
        if (!file.exists()) {
            plugin.getDataFolder().mkdirs();
            InputStream resource = plugin.getResource(FILE_NAME);
            if (resource != null) {
                try { plugin.saveResource(FILE_NAME, false); }
                catch (Exception ignored) { writeEmptyFile(); }
            } else {
                writeEmptyFile();
            }
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    private void writeEmptyFile() {
        try {
            file.getParentFile().mkdirs();
            file.createNewFile();
            FileConfiguration cfg = new YamlConfiguration();
            cfg.set("enabled", true);
            cfg.set("portals", new HashMap<>());
            cfg.save(file);
        } catch (IOException ex) {
            log.severe("[GroupRTP] Cannot create group-rtp.yml: " + ex.getMessage());
        }
    }

    private void saveFile(FileConfiguration cfg) {
        try { cfg.save(file); }
        catch (IOException ex) { log.severe("[GroupRTP] Cannot save group-rtp.yml: " + ex.getMessage()); }
    }

    // ── Util ──────────────────────────────────────────────────────────────────

    private static String c(String s) {
        return s == null ? "" : ChatColor.translateAlternateColorCodes('&', s);
    }

    private static Vector toVec(List<?> list) {
        return new Vector(
                ((Number) list.get(0)).doubleValue(),
                ((Number) list.get(1)).doubleValue(),
                ((Number) list.get(2)).doubleValue());
    }

    private static List<Double> vecList(Vector v) {
        return List.of(v.getX(), v.getY(), v.getZ());
    }

    private static final Set<String> DEFAULT_UNSAFE = Set.of(
            "LAVA", "FIRE", "CACTUS", "MAGMA_BLOCK", "POWDER_SNOW",
            "WITHER_ROSE", "SWEET_BERRY_BUSH", "CAMPFIRE", "SOUL_CAMPFIRE");

    private static final Map<String, String> DEFAULT_MESSAGES = new LinkedHashMap<>(Map.of(
            "join",       "&a&l+1 &7Player entered! &e{current}&7/&e{max} &7| Need &e{min}&7 to launch",
            "leave",      "&c&l-1 &7Player left. &e{current}&7/&e{min} &7needed",
            "countdown",  "&6Teleporting in &e{seconds}s &7\u2014 &e{current} &7players ready",
            "cancelled",  "&cTeleportation cancelled \u2014 not enough players",
            "searching",  "&e&lFinding safe wilderness location...",
            "success",    "&aYou have been teleported to the wilderness!",
            "full",       "&cThis portal is full (&e{max} &cplayers max). Try again later",
            "cooldown",   "&cYou must wait &e{time} &cbefore using this portal again",
            "no-location","&cCould not find a safe location. Please try again later",
            "no-permission","&cYou don't have permission to use this portal"
    ));
}
