package fr.elias.oreoEssentials.modules.portals;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.portals.rabbit.PortalsCrossServerBroker;
import fr.elias.oreoEssentials.rabbitmq.packet.PacketManager;
import fr.elias.oreoEssentials.util.Lang;
import fr.elias.oreoEssentials.util.OreScheduler;
import fr.elias.oreoEssentials.util.OreTask;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.util.BoundingBox;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PortalsManager implements Listener {

    // ── Portal data model ─────────────────────────────────────────────────────

    public static class Portal {
        public String name;
        public World world;
        public BoundingBox box;
        public Location destination;
        /** null = same server, "server_name" = cross-server (BungeeCord/Velocity) */
        public String destServer;
        /** If set, destination is resolved from warp at teleport time instead of using destination coords */
        public String destWarp;
        public boolean keepYawPitch;
        public String permission;
        public PortalParticleConfig particles;

        public Portal(String name, World world, BoundingBox box, Location destination,
                      String destServer, String destWarp, boolean keepYawPitch, String permission,
                      PortalParticleConfig particles) {
            this.name         = name;
            this.world        = world;
            this.box          = box;
            this.destination  = destination;
            this.destServer   = destServer;
            this.destWarp     = destWarp;
            this.keepYawPitch = keepYawPitch;
            this.permission   = permission;
            this.particles    = particles != null ? particles : new PortalParticleConfig();
        }

        public boolean contains(Location loc) {
            return loc != null
                    && loc.getWorld() != null
                    && loc.getWorld().equals(world)
                    && box.contains(loc.toVector());
        }

        public boolean hasPermission(Player p) {
            return permission == null || permission.isEmpty() || p.hasPermission(permission);
        }
    }

    // ── State ─────────────────────────────────────────────────────────────────

    private final OreoEssentials plugin;
    private final PortalConfig config;
    private final boolean enabled;

    private final Map<String, Portal> portals   = new ConcurrentHashMap<>(64);
    private final Map<UUID, Location> pos1       = new ConcurrentHashMap<>();
    private final Map<UUID, Location> pos2       = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cooldown       = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastDenied   = new ConcurrentHashMap<>();

    /** Players waiting to type a permission string in chat */
    private final Map<UUID, String> awaitingPermInput  = new ConcurrentHashMap<>();
    /** Players waiting to type a server name in chat */
    private final Map<UUID, String> awaitingServerInput = new ConcurrentHashMap<>();
    /** Players waiting to type a warp name in chat */
    private final Map<UUID, String> awaitingWarpInput   = new ConcurrentHashMap<>();

    private YamlConfiguration portalsYml;
    private File portalsFile;

    /** Cross-server broker — null if RabbitMQ not available */
    private PortalsCrossServerBroker crossServerBroker;

    private OreTask ambientTask;

    // ── Constructor ───────────────────────────────────────────────────────────

    public PortalsManager(OreoEssentials plugin) {
        this.plugin  = plugin;
        this.config  = new PortalConfig(plugin.getDataFolder());
        this.enabled = plugin.getSettings().portalsEnabled();

        this.portalsFile = config.getPortalsFile();
        if (!portalsFile.exists()) {
            try { portalsFile.createNewFile(); }
            catch (IOException e) { plugin.getLogger().severe("Failed to create portals.yml: " + e.getMessage()); }
        }
        this.portalsYml = YamlConfiguration.loadConfiguration(portalsFile);

        loadAll();

        if (enabled) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
            startAmbientTask();
        }
    }

    // ── Cross-server setup ────────────────────────────────────────────────────

    public void initCrossServer(PacketManager pm) {
        if (pm == null) return;
        this.crossServerBroker = new PortalsCrossServerBroker(plugin, pm);
        plugin.getLogger().info("[Portals] Cross-server support enabled.");
    }

    // ── Pos1 / Pos2 ───────────────────────────────────────────────────────────

    public void setPos1(Player p, Location l) { pos1.put(p.getUniqueId(), l.clone()); }
    public void setPos2(Player p, Location l) { pos2.put(p.getUniqueId(), l.clone()); }
    public Location getPos1(Player p)         { return pos1.get(p.getUniqueId()); }
    public Location getPos2(Player p)         { return pos2.get(p.getUniqueId()); }

    // ── CRUD ──────────────────────────────────────────────────────────────────

    public Set<String> listNames() { return new TreeSet<>(portals.keySet()); }

    public Portal get(String name) { return portals.get(name.toLowerCase(Locale.ROOT)); }

    /**
     * Creates a portal from pos1/pos2 selections.
     * @return null on success, error message on failure
     */
    public String create(String name, Player creator, Location dest,
                         String destServer, boolean keepYawPitch, String permission) {
        Location a = pos1.get(creator.getUniqueId());
        Location b = pos2.get(creator.getUniqueId());

        if (a == null || b == null)             return "Both pos1 and pos2 must be set (use /portal wand or /portal pos1|pos2)";
        if (a.getWorld() == null || b.getWorld() == null) return "Invalid world for pos1 or pos2";
        if (!a.getWorld().equals(b.getWorld())) return "pos1 and pos2 must be in the same world";

        World w      = a.getWorld();
        BoundingBox  box = BoundingBox.of(a, b);
        double volume    = box.getVolume();

        if (volume > config.getMaxPortalVolume()) return "Portal too large (max: " + config.getMaxPortalVolume() + " blocks)";
        if (volume < 1)                           return "Portal too small (minimum 1 block)";

        String key = name.toLowerCase(Locale.ROOT);
        if (portals.containsKey(key))             return "A portal with that name already exists";

        Portal portal = new Portal(name, w, box, dest.clone(),
                destServer, null, keepYawPitch, permission, new PortalParticleConfig());
        portals.put(key, portal);
        savePortal(portal);
        saveFile();

        pos1.remove(creator.getUniqueId());
        pos2.remove(creator.getUniqueId());
        return null;
    }

    public boolean remove(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        if (!portals.containsKey(key)) return false;
        portals.remove(key);
        portalsYml.set("portals." + key, null);
        saveFile();
        return true;
    }

    // ── Edit methods (called by GUI) ──────────────────────────────────────────

    public void updateDestination(String name, Location loc) {
        Portal p = get(name); if (p == null) return;
        p.destination = loc.clone();
        savePortal(p); saveFile();
    }

    public void updateDestServer(String name, String server) {
        Portal p = get(name); if (p == null) return;
        p.destServer = (server == null || server.equalsIgnoreCase("none")) ? null : server;
        savePortal(p); saveFile();
    }

    public void updatePermission(String name, String perm) {
        Portal p = get(name); if (p == null) return;
        p.permission = (perm == null || perm.equalsIgnoreCase("none")) ? null : perm;
        savePortal(p); saveFile();
    }

    public void toggleKeepYawPitch(String name) {
        Portal p = get(name); if (p == null) return;
        p.keepYawPitch = !p.keepYawPitch;
        savePortal(p); saveFile();
    }

    public void updateParticleTeleportType(String name, String type) {
        Portal p = get(name); if (p == null) return;
        p.particles.teleportType = type;
        savePortal(p); saveFile();
    }

    public void updateParticleTeleportCount(String name, int count) {
        Portal p = get(name); if (p == null) return;
        p.particles.teleportCount = count;
        savePortal(p); saveFile();
    }

    public void toggleAmbientParticles(String name) {
        Portal p = get(name); if (p == null) return;
        p.particles.ambientEnabled = !p.particles.ambientEnabled;
        savePortal(p); saveFile();
    }

    public void updateParticleAmbientType(String name, String type) {
        Portal p = get(name); if (p == null) return;
        p.particles.ambientType = type;
        savePortal(p); saveFile();
    }

    public void updateDestWarp(String name, String warp) {
        Portal p = get(name); if (p == null) return;
        p.destWarp = (warp == null || warp.equalsIgnoreCase("none")) ? null : warp;
        savePortal(p); saveFile();
    }

    // ── Chat input awaiting (for GUI permission/server editing) ───────────────

    public void awaitPermissionInput(UUID player, String portalName) {
        awaitingPermInput.put(player, portalName);
    }

    public void awaitServerInput(UUID player, String portalName) {
        awaitingServerInput.put(player, portalName);
    }

    public void awaitWarpInput(UUID player, String portalName) {
        awaitingWarpInput.put(player, portalName);
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        UUID pid = event.getPlayer().getUniqueId();

        String pendingPerm = awaitingPermInput.remove(pid);
        if (pendingPerm != null) {
            event.setCancelled(true);
            String input = event.getMessage().trim();
            OreScheduler.run(plugin, () -> {
                updatePermission(pendingPerm, input);
                event.getPlayer().sendMessage(ChatColor.GREEN + "Permission for portal "
                        + ChatColor.AQUA + pendingPerm + ChatColor.GREEN + " set to: "
                        + ChatColor.YELLOW + (input.equalsIgnoreCase("none") ? "(none)" : input));
            });
            return;
        }

        String pendingServer = awaitingServerInput.remove(pid);
        if (pendingServer != null) {
            event.setCancelled(true);
            String input = event.getMessage().trim();
            OreScheduler.run(plugin, () -> {
                updateDestServer(pendingServer, input);
                event.getPlayer().sendMessage(ChatColor.GREEN + "Destination server for portal "
                        + ChatColor.AQUA + pendingServer + ChatColor.GREEN + " set to: "
                        + ChatColor.YELLOW + (input.equalsIgnoreCase("none") ? "(same server)" : input));
            });
            return;
        }

        String pendingWarp = awaitingWarpInput.remove(pid);
        if (pendingWarp != null) {
            event.setCancelled(true);
            String input = event.getMessage().trim();
            OreScheduler.run(plugin, () -> {
                if (!input.equalsIgnoreCase("none")) {
                    fr.elias.oreoEssentials.modules.warps.WarpService ws = plugin.getWarpService();
                    if (ws != null && ws.getWarp(input) == null) {
                        event.getPlayer().sendMessage(ChatColor.RED + "Warp '" + input + "' does not exist.");
                        return;
                    }
                }
                updateDestWarp(pendingWarp, input);
                event.getPlayer().sendMessage(ChatColor.GREEN + "Destination warp for portal "
                        + ChatColor.AQUA + pendingWarp + ChatColor.GREEN + " set to: "
                        + ChatColor.YELLOW + (input.equalsIgnoreCase("none") ? "(none)" : input));
            });
        }
    }

    // ── Movement detection ────────────────────────────────────────────────────

    public void tickMove(Player p, Location to) {
        if (!enabled || to == null || p == null) return;

        long now = System.currentTimeMillis();
        UUID pid  = p.getUniqueId();
        Long cd   = cooldown.get(pid);
        if (cd != null && now < cd) return;

        for (Portal portal : portals.values()) {
            if (!portal.contains(to)) continue;

            if (!portal.hasPermission(p)) {
                String last = lastDenied.get(pid);
                if (!portal.name.equals(last)) {
                    Lang.send(p, "portals.no-portal-permission",
                            "<red>You don't have permission to use this portal.</red>");
                    lastDenied.put(pid, portal.name);
                    OreScheduler.runLater(plugin, () -> lastDenied.remove(pid, portal.name), 100L);
                }
                return;
            }

            lastDenied.remove(pid);
            cooldown.put(pid, now + Math.max(0, config.getCooldownMs()));
            playTeleportEffects(p, to, portal);

            // ── Resolve warp destination if set ───────────────────────────
            Location resolvedDest = portal.destination;
            if (portal.destWarp != null && !portal.destWarp.isEmpty()) {
                fr.elias.oreoEssentials.modules.warps.WarpService ws = plugin.getWarpService();
                Location warpLoc = ws != null ? ws.getWarp(portal.destWarp) : null;
                if (warpLoc == null) {
                    Lang.send(p, "portals.warp-not-found",
                            "<red>This portal's warp destination '<yellow>%warp%</yellow>' does not exist.</red>",
                            Map.of("warp", portal.destWarp));
                    return;
                }
                resolvedDest = warpLoc;
            }

            // ── Cross-server portal ────────────────────────────────────────
            if (portal.destServer != null && !portal.destServer.isEmpty()) {
                if (crossServerBroker == null) {
                    Lang.send(p, "portals.cross-server-disabled",
                            "<red>Cross-server portals require RabbitMQ + BungeeCord/Velocity.</red>");
                    return;
                }
                PacketManager pm = plugin.getPacketManager();
                if (pm == null) { return; }

                Location dest = resolvedDest.clone();
                crossServerBroker.sendCrossServerPortal(pm, p,
                        portal.destServer,
                        dest.getWorld() != null ? dest.getWorld().getName() : portal.world.getName(),
                        dest.getX(), dest.getY(), dest.getZ(),
                        dest.getYaw(), dest.getPitch(), portal.keepYawPitch);
                return;
            }

            // ── Same-server portal ─────────────────────────────────────────
            Location dest = resolvedDest.clone();
            if (config.isAllowKeepYawPitch() && portal.keepYawPitch) {
                dest.setYaw(p.getLocation().getYaw());
                dest.setPitch(p.getLocation().getPitch());
            }

            if (config.isTeleportAsync() || OreScheduler.isFolia()) {
                OreScheduler.runForEntity(plugin, p, () -> {
                    if (OreScheduler.isFolia()) p.teleportAsync(dest);
                    else p.teleport(dest);
                });
            } else {
                p.teleport(dest);
            }
            return;
        }
    }

    // ── Particles ─────────────────────────────────────────────────────────────

    private void playTeleportEffects(Player p, Location loc, Portal portal) {
        try {
            // Sound (global default — per-portal sound support can be added later)
            String snd = config.getDefaultSound();
            if (snd != null && !snd.isEmpty()) {
                try {
                    NamespacedKey soundKey = snd.contains(":") ? NamespacedKey.fromString(snd.toLowerCase()) : NamespacedKey.minecraft(snd.toLowerCase());
                    if (soundKey != null) {
                        Sound sound = Registry.SOUNDS.get(soundKey);
                        if (sound != null) p.getWorld().playSound(loc, sound, 1f, 1f);
                    }
                } catch (IllegalArgumentException ignored) {}
            }

            // Particle
            PortalParticleConfig pc = portal.particles;
            String pType  = pc.hasTeleportOverride() ? pc.teleportType  : config.getDefaultParticleType();
            int    pCount = pc.teleportCount > 0      ? pc.teleportCount : config.getDefaultParticleCount();

            if (pType != null && !pType.isEmpty() && pCount > 0) {
                try {
                    NamespacedKey particleKey = pType.contains(":") ? NamespacedKey.fromString(pType.toLowerCase()) : NamespacedKey.minecraft(pType.toLowerCase());
                    if (particleKey != null) {
                        Particle particle = Registry.PARTICLE_TYPE.get(particleKey);
                        if (particle != null) p.getWorld().spawnParticle(particle, loc, pCount, 0.25, 0.5, 0.25, 0.01);
                    }
                } catch (IllegalArgumentException ignored) {}
            }
        } catch (Throwable t) { /* never crash on effects */ }
    }

    private void startAmbientTask() {
        if (ambientTask != null) { ambientTask.cancel(); ambientTask = null; }
        if (!config.isAmbientEnabled()) return;

        int interval = config.getAmbientIntervalTicks();
        ambientTask = OreScheduler.runTimer(plugin, () -> {
            for (Portal portal : portals.values()) {
                if (!portal.particles.ambientEnabled && !config.isAmbientEnabled()) continue;

                String pType  = portal.particles.ambientEnabled && !portal.particles.ambientType.isEmpty()
                        ? portal.particles.ambientType : config.getAmbientParticleType();
                int    pCount = portal.particles.ambientCount > 0
                        ? portal.particles.ambientCount : config.getAmbientParticleCount();

                try {
                    NamespacedKey particleKey = pType.contains(":") ? NamespacedKey.fromString(pType.toLowerCase()) : NamespacedKey.minecraft(pType.toLowerCase());
                    if (particleKey == null) continue;
                    Particle particle = Registry.PARTICLE_TYPE.get(particleKey);
                    if (particle == null) continue;
                    Random rng = new Random();
                    BoundingBox b = portal.box;
                    double rx = b.getMinX() + rng.nextDouble() * b.getWidthX();
                    double ry = b.getMinY() + rng.nextDouble() * b.getHeight();
                    double rz = b.getMinZ() + rng.nextDouble() * b.getWidthZ();
                    Location ambLoc = new Location(portal.world, rx, ry, rz);
                    portal.world.spawnParticle(particle, ambLoc, pCount, 0.15, 0.15, 0.15, 0.02);
                } catch (Throwable ignored) {}
            }
        }, interval, interval);
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    public void loadAll() {
        portals.clear();
        ConfigurationSection root = portalsYml.getConfigurationSection("portals");
        if (root == null) return;

        int loaded = 0, failed = 0;
        for (String key : root.getKeys(false)) {
            try {
                ConfigurationSection s = root.getConfigurationSection(key);
                if (s == null) { failed++; continue; }

                String worldName = s.getString("world");
                World  w         = Bukkit.getWorld(worldName);
                if (w == null) {
                    plugin.getLogger().warning("[Portals] Portal '" + key + "' world not loaded: " + worldName);
                    failed++; continue;
                }

                double x1 = s.getDouble("box.x1"), y1 = s.getDouble("box.y1"), z1 = s.getDouble("box.z1");
                double x2 = s.getDouble("box.x2"), y2 = s.getDouble("box.y2"), z2 = s.getDouble("box.z2");
                BoundingBox box = BoundingBox.of(new Location(w, x1, y1, z1), new Location(w, x2, y2, z2));

                String dWorldName = s.getString("dest.world");
                World  dWorld     = Bukkit.getWorld(dWorldName);
                // dest world may be on another server — allowed if destServer is set
                String destSrv = s.getString("destServer", null);
                if (dWorld == null && (destSrv == null || destSrv.isEmpty())) {
                    plugin.getLogger().warning("[Portals] Portal '" + key + "' dest world not loaded: " + dWorldName);
                    failed++; continue;
                }

                double dx  = s.getDouble("dest.x"),   dy  = s.getDouble("dest.y"),   dz  = s.getDouble("dest.z");
                float  yaw = (float) s.getDouble("dest.yaw", 0), pitch = (float) s.getDouble("dest.pitch", 0);
                Location dest = new Location(dWorld != null ? dWorld : w, dx, dy, dz, yaw, pitch);

                boolean keep     = s.getBoolean("keepYawPitch", false);
                String  perm     = s.getString("permission", null);
                String  destWarp = s.getString("destWarp", null);

                // Per-portal particles
                PortalParticleConfig pc = new PortalParticleConfig();
                ConfigurationSection ps = s.getConfigurationSection("particles");
                if (ps != null) {
                    pc.teleportType    = ps.getString("teleport-type", "");
                    pc.teleportCount   = ps.getInt("teleport-count", -1);
                    pc.ambientEnabled  = ps.getBoolean("ambient-enabled", false);
                    pc.ambientType     = ps.getString("ambient-type", "");
                    pc.ambientCount    = ps.getInt("ambient-count", 3);
                }

                portals.put(key.toLowerCase(Locale.ROOT),
                        new Portal(s.getString("name", key), w, box, dest, destSrv, destWarp, keep, perm, pc));
                loaded++;
            } catch (Throwable t) {
                plugin.getLogger().warning("[Portals] Failed to load '" + key + "': " + t.getMessage());
                failed++;
            }
        }
        plugin.getLogger().info("[Portals] Loaded " + loaded + " portal(s)" + (failed > 0 ? " (" + failed + " failed)" : ""));
    }

    private void savePortal(Portal p) {
        String key  = p.name.toLowerCase(Locale.ROOT);
        String base = "portals." + key + ".";

        portalsYml.set(base + "name",        p.name);
        portalsYml.set(base + "world",       p.world.getName());
        portalsYml.set(base + "box.x1",      p.box.getMinX());
        portalsYml.set(base + "box.y1",      p.box.getMinY());
        portalsYml.set(base + "box.z1",      p.box.getMinZ());
        portalsYml.set(base + "box.x2",      p.box.getMaxX());
        portalsYml.set(base + "box.y2",      p.box.getMaxY());
        portalsYml.set(base + "box.z2",      p.box.getMaxZ());
        portalsYml.set(base + "dest.world",  p.destination.getWorld().getName());
        portalsYml.set(base + "dest.x",      p.destination.getX());
        portalsYml.set(base + "dest.y",      p.destination.getY());
        portalsYml.set(base + "dest.z",      p.destination.getZ());
        portalsYml.set(base + "dest.yaw",    p.destination.getYaw());
        portalsYml.set(base + "dest.pitch",  p.destination.getPitch());
        portalsYml.set(base + "keepYawPitch",p.keepYawPitch);
        portalsYml.set(base + "permission",  p.permission);
        portalsYml.set(base + "destServer",  p.destServer);
        portalsYml.set(base + "destWarp",    p.destWarp);

        // Particles
        PortalParticleConfig pc = p.particles;
        portalsYml.set(base + "particles.teleport-type",   pc.teleportType);
        portalsYml.set(base + "particles.teleport-count",  pc.teleportCount);
        portalsYml.set(base + "particles.ambient-enabled", pc.ambientEnabled);
        portalsYml.set(base + "particles.ambient-type",    pc.ambientType);
        portalsYml.set(base + "particles.ambient-count",   pc.ambientCount);
    }

    private void saveFile() {
        try { portalsYml.save(portalsFile); }
        catch (IOException e) { plugin.getLogger().severe("[Portals] Failed to save portals.yml: " + e.getMessage()); }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public boolean isEnabled()       { return enabled; }
    public OreoEssentials getPlugin(){ return plugin; }
    public PortalConfig getConfig()  { return config; }
}
