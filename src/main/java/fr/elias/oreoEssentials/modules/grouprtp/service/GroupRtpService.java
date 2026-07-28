package fr.elias.oreoEssentials.modules.grouprtp.service;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.grouprtp.GroupRtpConfig;
import fr.elias.oreoEssentials.modules.grouprtp.model.GroupRtpPortalDef;
import fr.elias.oreoEssentials.modules.grouprtp.model.GroupSession;
import fr.elias.oreoEssentials.modules.grouprtp.rabbit.GroupRtpSyncPacket;
import fr.elias.oreoEssentials.rabbitmq.PacketChannels;
import fr.elias.oreoEssentials.rabbitmq.packet.PacketManager;
import fr.elias.oreoEssentials.util.OreScheduler;
import org.bukkit.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Core service: tracks which players are inside each portal,
 * manages countdown sessions, finds safe locations, and teleports groups.
 *
 * <h3>Threading model</h3>
 * <ul>
 *   <li>All state mutations run on the global-region scheduler (main thread) via
 *       {@code OreScheduler.run / runTimer / runLater}.</li>
 *   <li>Safe-location search runs async via {@code OreScheduler.runAsync}.</li>
 *   <li>Per-player teleportation uses {@code OreScheduler.runForEntity} so it is
 *       safe under Folia's regional thread model.</li>
 * </ul>
 */
public final class GroupRtpService {

    private final OreoEssentials plugin;
    private final GroupRtpConfig config;
    private final Logger         log;

    /** portalId → active session */
    private final Map<String, GroupSession> sessions  = new ConcurrentHashMap<>();
    /** playerId → portalId they are currently inside */
    private final Map<UUID, String>         inside    = new ConcurrentHashMap<>();
    /** playerId → cooldown expiry (ms epoch) */
    private final Map<UUID, Long>           cooldowns = new ConcurrentHashMap<>();

    public GroupRtpService(OreoEssentials plugin, GroupRtpConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.log    = plugin.getLogger();
    }

    // ── Entry / Exit (called from listener, main thread) ─────────────────────

    public void onPlayerMove(Player player, Location to) {
        for (GroupRtpPortalDef def : config.getPortals().values()) {
            boolean wasInside = inside.containsKey(player.getUniqueId())
                    && def.getId().equals(inside.get(player.getUniqueId()));
            boolean isNowInside = def.contains(to);

            if (!wasInside && isNowInside) {
                handleEnter(player, def);
            } else if (wasInside && !isNowInside) {
                handleLeave(player, def);
            }
        }
    }

    public void onPlayerQuit(Player player) {
        String portalId = inside.remove(player.getUniqueId());
        if (portalId == null) return;
        config.get(portalId).ifPresent(def -> {
            GroupSession session = sessions.get(portalId);
            if (session != null) removeFromSession(session, def, player.getUniqueId());
        });
    }

    // ── Enter / Leave logic ───────────────────────────────────────────────────

    private void handleEnter(Player player, GroupRtpPortalDef def) {
        // Permission check
        if (!def.hasPermission(player)) {
            player.sendMessage(def.msg("no-permission", "&cYou don't have permission to use this portal."));
            return;
        }
        // Cooldown check
        long cd = cooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (cd > System.currentTimeMillis()) {
            long remainSec = (cd - System.currentTimeMillis()) / 1000L + 1;
            player.sendMessage(def.msg("cooldown", "&cCooldown: &e{time}")
                    .replace("{time}", remainSec + "s"));
            return;
        }

        // Get or create session
        GroupSession session = sessions.computeIfAbsent(def.getId(), GroupSession::new);

        // Max players cap
        if (def.getMaxPlayers() > 0 && session.size() >= def.getMaxPlayers()) {
            player.sendMessage(def.msg("full", "&cPortal full ({max} max)")
                    .replace("{max}", String.valueOf(def.getMaxPlayers())));
            return;
        }
        // If already teleporting, deny entry
        if (session.isTeleporting()) return;

        inside.put(player.getUniqueId(), def.getId());
        session.add(player.getUniqueId());

        // Notify portal
        broadcastPortal(session, def.msg("join", "&aPlayer joined! {current}/{max} | Need {min}")
                .replace("{current}", String.valueOf(session.size()))
                .replace("{max}",     String.valueOf(def.getMaxPlayers()))
                .replace("{min}",     String.valueOf(def.getMinPlayers())));

        playSound(player, def.getSoundEnter());
        spawnParticleAt(player.getLocation(), def.getAmbientParticle(), def.getAmbientCount());

        // Start countdown if we just reached min
        if (session.isIdle() && session.size() >= def.getMinPlayers()) {
            startCountdown(def, session);
        }
    }

    private void handleLeave(Player player, GroupRtpPortalDef def) {
        inside.remove(player.getUniqueId());
        GroupSession session = sessions.get(def.getId());
        if (session == null) return;
        removeFromSession(session, def, player.getUniqueId());
    }

    private void removeFromSession(GroupSession session, GroupRtpPortalDef def, UUID playerId) {
        if (session.isTeleporting()) {
            // Player disconnected mid-teleport — remove from the session's inside set
            // and reset to IDLE if the session is now empty.
            session.removeFromTeleporting(playerId);
            return;
        }
        session.remove(playerId);

        if (session.size() == 0) {
            session.cancelCountdown();
            sessions.remove(def.getId());
            return;
        }

        // Not enough players remain — cancel countdown
        if (session.isCountdown() && session.size() < def.getMinPlayers()) {
            session.cancelCountdown();
            broadcastPortal(session, def.msg("cancelled",
                    "&cTeleportation cancelled — not enough players."));
        } else {
            // Update waiting message
            broadcastPortal(session, def.msg("leave", "&c-1 &7Player left. &e{current}&7/{min}")
                    .replace("{current}", String.valueOf(session.size()))
                    .replace("{min}",     String.valueOf(def.getMinPlayers())));
        }
    }

    // ── Countdown ─────────────────────────────────────────────────────────────

    private void startCountdown(GroupRtpPortalDef def, GroupSession session) {
        int seconds = Math.max(1, def.getCountdownSeconds());
        // Repeating global task — fires every 20 ticks (1 second)
        var task = OreScheduler.runTimer(plugin, () -> tickCountdown(def, session), 20L, 20L);
        session.startCountdown(seconds, task);
    }

    private void tickCountdown(GroupRtpPortalDef def, GroupSession session) {
        if (session.isTeleporting()) return;

        // Re-check minimum players on every tick
        if (session.size() < def.getMinPlayers()) {
            session.cancelCountdown();
            broadcastPortal(session, def.msg("cancelled", "&cTeleportation cancelled — not enough players."));
            return;
        }

        boolean done = session.tickDown();
        int sec = session.getSecondsLeft();

        // Send countdown actionbar + title to all inside
        Set<UUID> snapshot = session.snapshot();
        for (UUID uuid : snapshot) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) continue;
            sendCountdownUI(p, def, sec + 1); // +1 since tickDown decrements before we read
        }

        if (done) {
            executeGroupTeleport(def, session);
        }
    }

    private void sendCountdownUI(Player p, GroupRtpPortalDef def, int sec) {
        // Actionbar
        String bar = def.msg("countdown", "&6Teleporting in &e{seconds}s &7\u2014 &e{current} players")
                .replace("{seconds}", String.valueOf(sec))
                .replace("{current}", String.valueOf(sessions.getOrDefault(def.getId(),
                        new GroupSession(def.getId())).size()));
        OreScheduler.runForEntity(plugin, p, () -> {
            if (!p.isOnline()) return;
            p.sendActionBar(ChatColor.translateAlternateColorCodes('&', bar));
            // Title for final 3 seconds
            if (sec <= 3) {
                String color = sec == 1 ? "&c" : sec == 2 ? "&e" : "&a";
                p.sendTitle(
                        ChatColor.translateAlternateColorCodes('&', color + "&l" + sec),
                        ChatColor.translateAlternateColorCodes('&', "&7Teleporting with &e"
                                + sessions.getOrDefault(def.getId(), new GroupSession(def.getId())).size()
                                + " &7players..."),
                        0, 25, 5);
            }
        });
    }

    // ── Group teleport execution ───────────────────────────────────────────────

    private void executeGroupTeleport(GroupRtpPortalDef def, GroupSession session) {
        if (session.isTeleporting()) return;

        Set<UUID> group = session.snapshot();
        session.beginTeleporting(); // locks session, clears inside set
        sessions.remove(def.getId());

        // Clean up inside map for all group members
        for (UUID uid : group) inside.remove(uid);

        // Notify players that we're searching
        for (UUID uid : group) {
            Player p = Bukkit.getPlayer(uid);
            if (p != null) OreScheduler.runForEntity(plugin, p, () -> {
                if (p.isOnline()) p.sendMessage(def.msg("searching", "&e&lFinding safe location..."));
            });
        }

        // ── Cross-server branch ───────────────────────────────────────────────
        String rtpServer = def.getRtpServer();
        if (rtpServer != null && !rtpServer.isBlank()) {
            // Apply cooldowns before dispatching cross-server so players cannot
            // immediately re-enter the portal if they switch back to this server.
            for (UUID uid : group) {
                cooldowns.put(uid, System.currentTimeMillis() + def.getCooldownMs());
            }
            // Reset session state now that the teleport is being handed off
            session.resetToIdle();
            dispatchCrossServerRtp(def, group);
            return;
        }

        // ── Same-server: find safe location ASYNC ─────────────────────────────
        OreScheduler.runAsync(plugin, () -> {
            World world = Bukkit.getWorld(def.getRtpWorld());
            if (world == null) {
                log.warning("[GroupRTP] RTP world '" + def.getRtpWorld() + "' not found for portal " + def.getId());
                for (UUID uid : group) {
                    Player p = Bukkit.getPlayer(uid);
                    if (p != null) OreScheduler.runForEntity(plugin, p, () -> {
                        if (p.isOnline()) p.sendMessage(def.msg("no-location", "&cCould not find a safe location."));
                    });
                }
                return;
            }

            Location base = SafeLocationFinder.find(world, def);
            if (base == null) {
                log.warning("[GroupRTP] Could not find safe location for portal " + def.getId());
                for (UUID uid : group) {
                    Player p = Bukkit.getPlayer(uid);
                    if (p != null) OreScheduler.runForEntity(plugin, p, () -> {
                        if (p.isOnline()) p.sendMessage(def.msg("no-location", "&cCould not find a safe location."));
                    });
                }
                return;
            }

            // Teleport each player on their own entity thread
            for (UUID uid : group) {
                Player p = Bukkit.getPlayer(uid);
                if (p == null || !p.isOnline()) continue; // skip — don't apply cooldown to disconnected players

                // Scatter players within clusterRadius of base location
                Location dest = SafeLocationFinder.scatter(base, def.getClusterRadius(),
                        world, def.getUnsafeBlocks());

                // Apply cooldown only after confirming the player is online
                cooldowns.put(uid, System.currentTimeMillis() + def.getCooldownMs());

                OreScheduler.runForEntity(plugin, p, () -> {
                    if (!p.isOnline()) {
                        // Player went offline between the online-check and task execution — remove cooldown
                        cooldowns.remove(uid);
                        return;
                    }
                    p.teleportAsync(dest).thenRun(() ->
                            OreScheduler.runForEntity(plugin, p, () -> {
                                if (!p.isOnline()) return;
                                p.sendMessage(def.msg("success", "&aYou have been teleported to the wilderness!"));
                                sendTitle(p, "&a&lTeleported!", "&7Explore the wilderness");
                                spawnParticleAt(p.getLocation(), def.getTeleportParticle(), def.getTeleportCount());
                                playSound(p, def.getSoundTeleport());
                            }));
                });
            }

            // Teleport sequence dispatched — reset session so it can be reused
            session.resetToIdle();
        });
    }

    // ── Cross-server dispatch ─────────────────────────────────────────────────

    private void dispatchCrossServerRtp(GroupRtpPortalDef def, Set<UUID> group) {
        PacketManager pm = plugin.getPacketManager();
        if (pm == null || !pm.isInitialized()) {
            log.warning("[GroupRTP] Cross-server RTP configured but PacketManager is unavailable for portal " + def.getId());
            for (UUID uid : group) {
                Player p = Bukkit.getPlayer(uid);
                if (p != null) OreScheduler.runForEntity(plugin, p, () -> {
                    if (p.isOnline()) p.sendMessage(def.msg("no-location",
                            "&cCross-server RTP is unavailable. Please try again later."));
                });
            }
            return;
        }

        String requestId = UUID.randomUUID().toString();
        GroupRtpSyncPacket pkt = new GroupRtpSyncPacket(
                requestId, def.getId(), def.getRtpWorld(),
                new ArrayList<>(group),
                def.getRtpCenterX(), def.getRtpCenterZ(),
                def.getRtpRadius(), def.getRtpMinRadius(),
                def.getRtpMinY(), def.getRtpMaxY(), def.getRtpAttempts(),
                def.getClusterRadius(),
                new ArrayList<>(def.getUnsafeBlocks()),
                new ArrayList<>(def.getBlacklistedBiomes())
        );

        try {
            pm.sendPacket(PacketChannels.individual(def.getRtpServer()), pkt);
        } catch (Throwable t) {
            log.warning("[GroupRTP] Failed to send cross-server packet for portal "
                    + def.getId() + ": " + t.getMessage());
            for (UUID uid : group) {
                Player p = Bukkit.getPlayer(uid);
                if (p != null) OreScheduler.runForEntity(plugin, p, () -> {
                    if (p.isOnline()) p.sendMessage(def.msg("no-location",
                            "&cCross-server RTP failed. Please try again later."));
                });
            }
            return;
        }

        // Give RabbitMQ enough time to deliver the packet and the target server time to
        // begin the async location search before players arrive.
        // The broker handles late-arriving players via the pending map, so this delay is
        // just a best-effort head-start, not a hard requirement.
        OreScheduler.runLater(plugin, () -> {
            for (UUID uid : group) {
                Player p = Bukkit.getPlayer(uid);
                if (p != null && p.isOnline()) {
                    plugin.getProxyMessenger().sendToServer(p, def.getRtpServer());
                }
            }
        }, 20L); // 1 second
    }

    // ── Ambient particles (called from module tick) ───────────────────────────

    public void tickAmbient() {
        for (GroupRtpPortalDef def : config.getPortals().values()) {
            GroupSession session = sessions.get(def.getId());
            if (session == null) continue;
            World world = Bukkit.getWorld(def.getWorldName());
            if (world == null) continue;

            // Spawn particles randomly inside the bounding box
            java.util.concurrent.ThreadLocalRandom rnd = java.util.concurrent.ThreadLocalRandom.current();
            double x = def.getBox().getMinX() + rnd.nextDouble(def.getBox().getWidthX());
            double y = def.getBox().getMinY() + rnd.nextDouble(def.getBox().getHeight());
            double z = def.getBox().getMinZ() + rnd.nextDouble(def.getBox().getWidthZ());
            Location pLoc = new Location(world, x, y, z);
            spawnParticleAt(pLoc, def.getAmbientParticle(), def.getAmbientCount());
        }
    }

    // ── Admin helpers ─────────────────────────────────────────────────────────

    public Map<String, GroupSession> getSessions() { return Collections.unmodifiableMap(sessions); }

    public void clearCooldown(UUID uuid) { cooldowns.remove(uuid); }

    public void reload() {
        // Cancel active sessions before reload
        for (GroupSession s : sessions.values()) s.cancelCountdown();
        sessions.clear();
        inside.clear();
        config.reload();
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private void broadcastPortal(GroupSession session, String msg) {
        if (msg == null || msg.isBlank()) return;
        for (UUID uid : session.snapshot()) {
            Player p = Bukkit.getPlayer(uid);
            if (p != null) OreScheduler.runForEntity(plugin, p, () -> {
                if (p.isOnline()) p.sendMessage(msg);
            });
        }
    }

    private static void playSound(Player p, String soundName) {
        if (soundName == null || soundName.isBlank()) return;
        NamespacedKey key = NamespacedKey.fromString(soundName.toLowerCase(java.util.Locale.ROOT));
        if (key == null) key = NamespacedKey.minecraft(soundName.toLowerCase(java.util.Locale.ROOT));
        Sound s = org.bukkit.Registry.SOUNDS.get(key);
        if (s == null) return;
        p.playSound(p.getLocation(), s, 0.8f, 1f);
    }

    private static void spawnParticleAt(Location loc, String particleName, int count) {
        if (particleName == null || particleName.isBlank() || loc.getWorld() == null) return;
        NamespacedKey key = NamespacedKey.fromString(particleName.toLowerCase(java.util.Locale.ROOT));
        if (key == null) key = NamespacedKey.minecraft(particleName.toLowerCase(java.util.Locale.ROOT));
        Particle pt = org.bukkit.Registry.PARTICLE_TYPE.get(key);
        if (pt == null) return;
        loc.getWorld().spawnParticle(pt, loc, count, 0.3, 0.3, 0.3, 0.05);
    }

    private static void sendTitle(Player p, String title, String subtitle) {
        p.sendTitle(
                ChatColor.translateAlternateColorCodes('&', title),
                ChatColor.translateAlternateColorCodes('&', subtitle),
                5, 40, 10);
    }
}
