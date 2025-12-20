// File: src/main/java/fr/elias/oreoEssentials/teleport/TpaCrossServerBroker.java
package fr.elias.oreoEssentials.teleport;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.rabbitmq.PacketChannels;
import fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel;
import fr.elias.oreoEssentials.rabbitmq.packet.PacketManager;
import fr.elias.oreoEssentials.rabbitmq.packet.impl.TpaRequestPacket;
import fr.elias.oreoEssentials.rabbitmq.packet.impl.TpaSummonPacket;
import fr.elias.oreoEssentials.services.TeleportService;
import fr.elias.oreoEssentials.util.Lang;
import fr.elias.oreoEssentials.util.ProxyMessenger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cross-server /tpa handshake (requester -> target):
 * 1) REQUESTER server sends TpaRequestPacket to TARGET server (GLOBAL or per-server).
 * 2) TARGET runs /tpaccept -> send TpaSummonPacket to REQUESTER server to move requester to TARGET server.
 * 3) On PlayerJoin at TARGET: finalize by teleporting requester to target's live location (delayed a few ticks).
 *
 * Robust to mixed online/offline/cracked UUIDs by also matching the target by name.
 */
public final class TpaCrossServerBroker implements Listener {

    private final OreoEssentials plugin;
    private final TeleportService teleportService;
    private final PacketManager pm;
    private final ProxyMessenger proxy;
    private final String localServer;

    /** targetUuid(actual on this server) -> pending meta (who wants to TP to this target) */
    private final Map<UUID, Pending> pendingForTarget = new ConcurrentHashMap<>();
    /** requesterUuid -> targetUuid (arrival marker ON THIS SERVER after server switch) */
    private final Map<UUID, UUID> pendingArrival = new ConcurrentHashMap<>();

    /** Default expiry window; configurable: features.tpa.expire-seconds (default 60s) */
    private final long expireMs;

    /** Enable offline/cracked UUID compatibility fallback (name-based matching). */
    private final boolean offlineUuidCompat;

    public TpaCrossServerBroker(
            OreoEssentials plugin,
            TeleportService teleportService,
            PacketManager pm,
            ProxyMessenger proxy,
            String localServer
    ) {
        this.plugin = plugin;
        this.teleportService = teleportService;
        this.pm = pm;
        this.proxy = proxy;
        this.localServer = localServer;

        long cfgSec = 60L;
        try {
            cfgSec = plugin.getConfig().getLong("features.tpa.expire-seconds",
                    plugin.getConfig().getLong("tpa.expire-seconds", 60L));
            if (cfgSec <= 0) cfgSec = 60L;
        } catch (Throwable ignored) {}
        this.expireMs = cfgSec * 1000L;

        boolean compat = true; // default ON
        try {
            compat = plugin.getConfig().getBoolean("features.tpa.offline-uuid-compat", true);
        } catch (Throwable ignored) {}
        this.offlineUuidCompat = compat;

        // Subscribe to packets
        if (pm != null && pm.isInitialized()) {
            pm.subscribe(TpaRequestPacket.class, this::onTpaRequest);
            pm.subscribe(TpaSummonPacket.class, this::onTpaSummon);
            dbg("Subscribed TpaRequestPacket + TpaSummonPacket (expire=" + cfgSec + "s, offlineCompat=" + offlineUuidCompat + ")");
        } else {
            dbg("PacketManager not initialized; cross-server TPA disabled on this node.");
        }

        // Bukkit listeners
        Bukkit.getPluginManager().registerEvents(this, plugin);

        // Periodic cleanup of expired entries
        Bukkit.getScheduler().runTaskTimer(plugin, this::purgeExpired, 20L * 30, 20L * 30);
    }

    /* ====================================================================== */
    /*                               PUBLIC API                               */
    /* ====================================================================== */

    // ---- Backwards-compatible overloads (old call sites keep working) ----
    public void sendRequestToServer(Player requester, UUID targetUuid, String destServer) {
        sendRequestToServer(requester, targetUuid, "", destServer);
    }

    public void sendRequestGlobal(Player requester, UUID targetUuid) {
        sendRequestGlobal(requester, targetUuid, "");
    }

    // ---- New API that also carries the target's name (fixes cracked/offline mixes) ----
    public void sendRequestToServer(Player requester, UUID targetUuid, String targetName, String destServer) {
        if (!isMessagingReady() || requester == null) return;
        long now = System.currentTimeMillis();
        TpaRequestPacket pkt = new TpaRequestPacket(
                requester.getUniqueId(),
                requester.getName(),
                targetUuid,
                targetName != null ? targetName : "",
                localServer,
                now + expireMs
        );
        pm.sendPacket(PacketChannel.individual(destServer), pkt);
        dbg("Send TpaRequest -> server=" + destServer + " requester=" + requester.getName()
                + " target=" + targetUuid + (targetName != null && !targetName.isBlank() ? " (" + targetName + ")" : ""));
    }

    public void sendRequestGlobal(Player requester, UUID targetUuid, String targetName) {
        if (!isMessagingReady() || requester == null) return;
        long now = System.currentTimeMillis();
        TpaRequestPacket pkt = new TpaRequestPacket(
                requester.getUniqueId(),
                requester.getName(),
                targetUuid,
                targetName != null ? targetName : "",
                localServer,
                now + expireMs
        );
        pm.sendPacket(PacketChannels.GLOBAL, pkt);
        dbg("Send TpaRequest (GLOBAL) requester=" + requester.getName()
                + " target=" + targetUuid + (targetName != null && !targetName.isBlank() ? " (" + targetName + ")" : ""));
    }

    /**
     * Called by /tpaccept on the TARGET server.
     * @return true if a cross-server pending was handled.
     */
    public boolean acceptCrossServer(Player target) {
        if (target == null) return false;

        Pending p = pendingForTarget.remove(target.getUniqueId());
        if (p == null) {
            dbg("No cross-server pending for " + target.getName());
            return false;
        }

        long now = System.currentTimeMillis();
        if (p.expiresAt > 0 && p.expiresAt < now) {
            target.sendMessage("§cThat teleport request expired.");
            dbg("Pending expired (target=" + target.getUniqueId() + ")");
            return true;
        }

        // Mark arrival expectation on THIS server
        pendingArrival.put(p.requesterUuid, target.getUniqueId());

        // Ask requester's current server to move them here
        if (isMessagingReady()) {
            pm.sendPacket(
                    PacketChannel.individual(p.fromServer),
                    new TpaSummonPacket(p.requesterUuid, localServer)
            );
        }

        target.sendMessage("§aTeleport request accepted. Summoning §b"
                + p.requesterName + "§7…");
        dbg("Accept -> summon requester=" + p.requesterUuid
                + " from=" + p.fromServer + " to=" + localServer
                + " (target=" + target.getName() + ")");

        // Safety: clean up if they never arrive
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> pendingArrival.remove(p.requesterUuid),
                20L * 60);

        return true;
    }

    public boolean denyCrossServer(Player target) {
        Pending p = pendingForTarget.remove(target.getUniqueId());
        if (p == null) return false;
        target.sendMessage("§cYou denied the teleport request from §b" + p.requesterName + "§c.");
        dbg("Denied pending for target=" + target.getName() + " requester=" + p.requesterUuid);
        return true;
    }

    public boolean hasPendingFor(Player target) {
        return pendingForTarget.containsKey(target.getUniqueId());
    }

    /* ====================================================================== */
    /*                              PACKET HANDLERS                           */
    /* ====================================================================== */

    /** Handles incoming request ON THE TARGET'S SERVER. */
    private void onTpaRequest(PacketChannel channel, TpaRequestPacket pkt) {
        if (pkt == null) return;

        long now = System.currentTimeMillis();
        if (pkt.getExpiresAtEpochMs() > 0 && pkt.getExpiresAtEpochMs() < now) {
            dbg("TpaRequestPacket expired; ignoring. from=" + pkt.getFromServer());
            return;
        }

        // Resolve target: UUID -> fallback by name (for cracked/offline/floodgate cases)
        Player target = resolveTarget(pkt.getTargetUuid(), pkt.getTargetName());
        if (target == null) {
            // Not our server OR we couldn't reconcile UUID/name — ignore silently
            return;
        }

        // Store pending
        Pending p = new Pending();
        p.requesterUuid = pkt.getRequesterUuid();
        p.requesterName = pkt.getRequesterName();
        p.fromServer    = pkt.getFromServer();
        p.expiresAt     = (pkt.getExpiresAtEpochMs() > 0 ? pkt.getExpiresAtEpochMs() : now + expireMs);

        pendingForTarget.put(target.getUniqueId(), p);

        // Notify target
        target.sendMessage("§b" + p.requesterName + " §7requested to teleport to you. Type §a/tpaccept §7or §c/tpdeny§7.");
        dbg("Saved pending for target=" + target.getUniqueId() + " name=" + target.getName()
                + " fromServer=" + p.fromServer + " expiresAt=" + p.expiresAt);
    }

    /** Handles "summon the requester to target's server" ON THE REQUESTER'S SERVER. */
    private void onTpaSummon(PacketChannel channel, TpaSummonPacket pkt) {
        if (pkt == null) return;

        Player requester = Bukkit.getPlayer(pkt.getRequesterUuid());
        if (requester == null) {
            dbg("Summon received, but requester not online here: " + pkt.getRequesterUuid());
            return;
        }

        // Read cooldown from settings.yml
        var root = plugin.getSettingsConfig().getRoot();
        var sec  = root.getConfigurationSection("features.tpa");

        boolean enabled = sec != null && sec.getBoolean("cooldown", false);
        int seconds     = (sec != null ? sec.getInt("cooldown-amount", 0) : 0);

        // If no cooldown configured -> old instant behavior
        if (!enabled || seconds <= 0) {
            boolean ok = connectToServer(requester, pkt.getDestServer());
            if (ok) {
                requester.sendMessage("§7Téléportation vers §b" + pkt.getDestServer() + "§7…");
            } else {
                plugin.getLogger().warning("[TPA-X] Could not send " + requester.getName()
                        + " to server " + pkt.getDestServer() + " (no ProxyMessenger method matched)");
            }
            return;
        }

        //  Cross-server cooldown for the REQUESTER
        final String destServer = pkt.getDestServer();
        final Location origin = requester.getLocation().clone();

        dbg("Starting cross-server TPA countdown for requester=" + requester.getName()
                + " destServer=" + destServer + " seconds=" + seconds);

        new org.bukkit.scheduler.BukkitRunnable() {
            int remain = seconds;

            @Override
            public void run() {
                if (!requester.isOnline()) {
                    dbg("Requester went offline during cross-server countdown; cancel.");
                    cancel();
                    return;
                }

                // Cancel if requester moved (allow head movement only)
                if (hasBodyMoved(requester, origin)) {
                    requester.sendMessage("§cTéléportation annulée: vous avez bougé.");
                    dbg("Requester moved; cancelling cross-server countdown.");
                    cancel();
                    return;
                }

                if (remain <= 0) {
                    cancel();
                    dbg("Cross-server countdown finished; connecting " + requester.getName()
                            + " -> " + destServer);
                    boolean ok = connectToServer(requester, destServer);
                    if (ok) {
                        requester.sendMessage("§7Téléportation vers §b" + destServer + "§7…");
                    } else {
                        plugin.getLogger().warning("[TPA-X] Failed to connect "
                                + requester.getName() + " to " + destServer);
                    }
                    return;
                }

                // Show countdown to the REQUESTER — title/subtitle from lang.yml
                String title = Lang.msg("teleport.countdown.title", null, requester);
                String subtitle = Lang.msg(
                        "teleport.countdown.subtitle",
                        Map.of("seconds", String.valueOf(remain)),
                        requester
                );
                requester.sendTitle(title, subtitle, 0, 20, 0);

                remain--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private boolean hasBodyMoved(Player p, Location origin) {
        Location now = p.getLocation();
        // Same block X/Z = we consider this "no movement" (can move head)
        return now.getBlockX() != origin.getBlockX()
                || now.getBlockZ() != origin.getBlockZ();
    }

    /* ====================================================================== */
    /*                              ARRIVAL FINALIZE                          */
    /* ====================================================================== */

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        UUID targetUuid = pendingArrival.remove(e.getPlayer().getUniqueId());
        if (targetUuid == null) return; // not a summoned requester

        Player target = Bukkit.getPlayer(targetUuid);
        if (target == null) {
            e.getPlayer().sendMessage("§cTeleport target went offline.");
            dbg("Arrival: target offline for requester=" + e.getPlayer().getName());
            return;
        }

        // Delay a few ticks so the player fully spawns and the target chunk is loaded
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                Location to = target.getLocation();
                // Keep it simple & compatible with your current TeleportService:
                e.getPlayer().teleport(to);
                dbg("Arrival: snapped " + e.getPlayer().getName() + " -> " + target.getName());
            } catch (Throwable t) {
                plugin.getLogger().warning("[TPA-X] Arrival teleport failed: " + t.getMessage());
            }
        }, 3L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        pendingArrival.remove(e.getPlayer().getUniqueId());
    }

    /* ====================================================================== */
    /*                                   UTIL                                 */
    /* ====================================================================== */

    private boolean isMessagingReady() {
        return pm != null && pm.isInitialized();
    }

    /** Resolve the target player on THIS server, handling offline/cracked UUID and name fallback. */
    private Player resolveTarget(UUID targetUuid, String targetName) {
        // 1) UUID direct (works in online-mode & when UUIDs match)
        Player p = Bukkit.getPlayer(targetUuid);
        if (p != null) return p;

        if (!offlineUuidCompat) return null;

        // 2) Name from the packet (best signal for cracked/offline mixes)
        if (targetName != null && !targetName.isBlank()) {
            Player byExact = Bukkit.getPlayerExact(targetName);
            if (byExact != null) return byExact;
            String want = targetName.toLowerCase(java.util.Locale.ROOT);
            for (Player op : Bukkit.getOnlinePlayers()) {
                if (op.getName().equalsIgnoreCase(want)) return op;
            }
        }

        // 3) Directory fallback (UUID -> lastKnownName -> online match)
        try {
            var dir = plugin.getPlayerDirectory();
            if (dir != null) {
                String lastKnownName = dir.lookupNameByUuid(targetUuid);
                if (lastKnownName != null && !lastKnownName.isBlank()) {
                    Player byExact = Bukkit.getPlayerExact(lastKnownName);
                    if (byExact != null) return byExact;
                    String want = lastKnownName.toLowerCase(java.util.Locale.ROOT);
                    for (Player op : Bukkit.getOnlinePlayers()) {
                        if (op.getName().equalsIgnoreCase(want)) return op;
                    }
                }
            }
        } catch (Throwable ignored) {}

        // Not found on this node.
        return null;
    }

    /** Try common method names on ProxyMessenger via reflection. */
    private boolean connectToServer(Player player, String server) {
        try {
            for (String m : new String[]{"connect", "send", "sendToServer"}) {
                Method method = find(proxy.getClass(), m, Player.class, String.class);
                if (method != null) {
                    method.invoke(proxy, player, server);
                    return true;
                }
            }
        } catch (Throwable ignored) {}

        try {
            for (String m : new String[]{"connect", "send", "sendToServer"}) {
                Method method = find(proxy.getClass(), m, String.class);
                if (method != null) {
                    method.invoke(proxy, server);
                    return true;
                }
            }
        } catch (Throwable ignored) {}

        return false;
    }

    private static Method find(Class<?> cls, String name, Class<?>... params) {
        try {
            Method m = cls.getMethod(name, params);
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    /** Remove expired entries from pendingForTarget. */
    private void purgeExpired() {
        long now = System.currentTimeMillis();
        int removed = 0;
        Iterator<Map.Entry<UUID, Pending>> it = pendingForTarget.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Pending> e = it.next();
            Pending p = e.getValue();
            if (p == null) {
                it.remove();
                removed++;
                continue;
            }
            if (p.expiresAt > 0 && p.expiresAt < now) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) dbg("Purged " + removed + " expired pending requests.");
    }

    private void dbg(String msg) {
        try {
            if (plugin.getConfig().getBoolean("features.tpa.debug",
                    plugin.getConfig().getBoolean("debug", false))) {
                plugin.getLogger().info("[TPA-X@" + localServer + "] " + msg);
            }
        } catch (Throwable ignored) {}
    }

    /* ================================ DTO ================================= */

    private static class Pending {
        UUID  requesterUuid;
        String requesterName;
        String fromServer;
        long   expiresAt;
    }
}
