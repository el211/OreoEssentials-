// File: src/main/java/fr/elias/oreoEssentials/homes/HomeTeleportBroker.java
package fr.elias.oreoEssentials.modules.homes;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.rabbitmq.packet.PacketManager;
import fr.elias.oreoEssentials.modules.homes.rabbit.packet.HomeTeleportRequestPacket;
import fr.elias.oreoEssentials.modules.homes.rabbit.packet.OtherHomeTeleportRequestPacket;
import fr.elias.oreoEssentials.modules.homes.home.HomeService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public final class HomeTeleportBroker implements Listener {

    private final Plugin plugin;
    private final HomeService homes;
    private final PacketManager pm;
    private final String thisServer;
    private final Logger log;

    /** latest requested home per subject (guards against stale packets) */
    private final Map<UUID, String> lastRequestedHome = new ConcurrentHashMap<>();
    /** latest requestId per subject (to disambiguate multiple close requests) */
    private final Map<UUID, String> lastRequestId = new ConcurrentHashMap<>();
    /** when present, subject is being teleported to SOMEONE ELSE’s home (ownerId) */
    private final Map<UUID, UUID> lastRequestedOwner = new ConcurrentHashMap<>();
    /** presence means we owe a teleport on join / while online */
    private final Map<UUID, Boolean> pending = new ConcurrentHashMap<>();

    public HomeTeleportBroker(Plugin plugin, HomeService homes, PacketManager pm) {
        this.plugin = plugin;
        this.homes = homes;
        this.pm = pm;
        this.thisServer = OreoEssentials.get().getConfigService().serverName();
        this.log = plugin.getLogger();

        log.info("[HOME/BROKER] up on server=" + thisServer + " pm.init=" + pm.isInitialized());

        /* -------- Self-home requests (existing) -------- */
        pm.subscribe(HomeTeleportRequestPacket.class, (channel, pkt) -> {
            if (!thisServer.equalsIgnoreCase(pkt.getTargetServer())) {
                log.info("[HOME/REQ] ignoring (not my server). this=" + thisServer
                        + " target=" + pkt.getTargetServer());
                return;
            }

            final UUID subject = pkt.getPlayerId();     // subject == owner for /home
            final String hm    = pkt.getHomeName();
            final String rid   = pkt.getRequestId();

            lastRequestedHome.put(subject, hm);
            lastRequestId.put(subject, rid);
            lastRequestedOwner.remove(subject);         // self-home: clear any “other” owner
            pending.put(subject, Boolean.TRUE);

            final Player online = Bukkit.getPlayer(subject);
            log.info("[HOME/REQ] recv server=" + thisServer
                    + " player=" + subject + " home=" + hm + " requestId=" + rid
                    + " online=" + (online != null && online.isOnline()));

            if (online != null && online.isOnline()) applyWithRetries(subject);
        });

        /* -------- Other-home requests (NEW, parallel to the above) -------- */
        pm.subscribe(OtherHomeTeleportRequestPacket.class, (channel, pkt) -> {
            if (!thisServer.equalsIgnoreCase(pkt.getTargetServer())) {
                log.info("[HOME/REQ-OTHER] ignoring (not my server). this=" + thisServer
                        + " target=" + pkt.getTargetServer());
                return;
            }

            final UUID subject = pkt.getSubjectId();    // who will be teleported (admin)
            final UUID owner   = pkt.getOwnerId();      // whose home we’ll use
            final String hm    = pkt.getHomeName();
            final String rid   = pkt.getRequestId();

            lastRequestedHome.put(subject, hm);
            lastRequestId.put(subject, rid);
            lastRequestedOwner.put(subject, owner);
            pending.put(subject, Boolean.TRUE);

            final Player online = Bukkit.getPlayer(subject);
            log.info("[HOME/REQ-OTHER] recv server=" + thisServer
                    + " subject=" + subject + " owner=" + owner
                    + " home=" + hm + " requestId=" + rid
                    + " online=" + (online != null && online.isOnline()));

            if (online != null && online.isOnline()) applyWithRetries(subject);
        });

        // Events
        Bukkit.getPluginManager().registerEvents(this, plugin);
        log.info("[BROKER/HOME] listening. server=" + thisServer + " pm.init=" + pm.isInitialized());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        final UUID id = e.getPlayer().getUniqueId();
        if (!pending.containsKey(id)) return;
        log.info("[HOME/JOIN] player=" + id + " intent=" + lastRequestedHome.get(id)
                + " requestId=" + lastRequestId.get(id) + " owner=" + lastRequestedOwner.get(id));
        applyWithRetries(id);
    }

    /* ---------------- PUBLIC SENDER (used by /otherhome command) ---------------- */

    /**
     * Request that {@code adminId} be teleported to {@code ownerId}'s {@code homeName}
     * on whatever server stores that home. Returns false if we can’t resolve the server or publish fails.
     */
    public boolean requestTeleportOtherHome(UUID adminId, UUID ownerId, String homeName) {
        String server = resolveHomeServer(ownerId, homeName);
        if (server == null || server.isBlank()) {
            log.warning("[HOME/SEND-OTHER] cannot resolve target server for owner=" + ownerId + " home=" + homeName);
            return false;
        }
        try {
            OtherHomeTeleportRequestPacket pkt = new OtherHomeTeleportRequestPacket(
                    adminId,
                    ownerId,
                    homeName.toLowerCase(java.util.Locale.ROOT),
                    server,
                    null // requestId auto-generated if null
            );

            // Send on GLOBAL; broker filters by targetServer
            pm.sendPacket(fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel.individual(server), pkt);

            log.info("[HOME/SEND-OTHER] queued -> server=" + server
                    + " subject=" + adminId + " owner=" + ownerId
                    + " home=" + homeName + " reqId=" + pkt.getRequestId());
            return true;
        } catch (Throwable t) {
            log.warning("[HOME/SEND-OTHER] send failed: " + t.getMessage());
            return false;
        }
    }

    /* ---------------- helpers ---------------- */

    private void applyWithRetries(UUID subject) {
        // try at 0t, +2t, +10t, +20t
        runOnce(subject, 0);
        runOnce(subject, 2);
        runOnce(subject, 10);
        runOnce(subject, 20);
    }

    private void runOnce(UUID subject, int tick) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            final Player p = Bukkit.getPlayer(subject);
            if (p == null || !p.isOnline()) {
                log.info("[HOME/Retry] tick=" + tick + " player offline: " + subject);
                return;
            }

            final String expectHome = lastRequestedHome.get(subject);
            final String reqId      = lastRequestId.get(subject);
            final UUID owner        = lastRequestedOwner.getOrDefault(subject, subject); // self for /home

            if (expectHome == null || reqId == null) {
                log.info("[HOME/Retry] tick=" + tick + " no-intent for " + subject);
                return;
            }

            final Location loc = homes.getHome(owner, expectHome);
            if (loc == null) {
                log.warning("[HOME/Retry] tick=" + tick + " home not found here. subject=" + subject
                        + " owner=" + owner + " home=" + expectHome + " requestId=" + reqId);
                // stop trying for this intent
                pending.remove(subject);
                lastRequestedHome.remove(subject);
                lastRequestedOwner.remove(subject);
                lastRequestId.remove(subject);
                return;
            }

            final boolean ok = p.teleport(loc);
            log.info("[HOME/Retry] tick=" + tick
                    + " teleported=" + ok
                    + " subject=" + p.getName()
                    + " -> " + expectHome + " (owner=" + owner + ")"
                    + " requestId=" + reqId
                    + " " + shortLoc(loc));

            if (tick == 20) {
                // final attempt done; clear flags
                pending.remove(subject);
                lastRequestedHome.remove(subject);
                lastRequestedOwner.remove(subject);
                lastRequestId.remove(subject);
            }
        }, tick);
    }

    private static String shortLoc(Location l) {
        return "loc=" + (l.getWorld() != null ? l.getWorld().getName() : "null")
                + "(" + fmt(l.getX()) + "," + fmt(l.getY()) + "," + fmt(l.getZ()) + ")"
                + " yaw=" + fmt(l.getYaw()) + " pitch=" + fmt(l.getPitch());
    }

    private static String fmt(double d) { return String.format(java.util.Locale.ROOT, "%.2f", d); }
    private static String fmt(float f)  { return String.format(java.util.Locale.ROOT, "%.2f", f); }

    /** Resolve owner’s server for (ownerId, homeName) via HomeService (listHomes/getHomes + getServer()). */
    private String resolveHomeServer(UUID ownerId, String homeName) {
        final String key = homeName.toLowerCase(java.util.Locale.ROOT);

        // Prefer listHomes(UUID)
        try {
            Method m = homes.getClass().getMethod("listHomes", UUID.class);
            Object mapObj = m.invoke(homes, ownerId);
            String s = tryReadServerFromMap(mapObj, key);
            if (s != null) return s;
        } catch (NoSuchMethodException ignored) {
            // fall through to getHomes
        } catch (Throwable t) {
            log.warning("[HOME/SEND-OTHER] listHomes reflect failed: " + t.getMessage());
        }

        // Fallback: getHomes(UUID)
        try {
            Method m = homes.getClass().getMethod("getHomes", UUID.class);
            Object mapObj = m.invoke(homes, ownerId);
            String s = tryReadServerFromMap(mapObj, key);
            if (s != null) return s;
        } catch (Throwable t) {
            log.warning("[HOME/SEND-OTHER] getHomes reflect failed: " + t.getMessage());
        }

        return null;
    }

    private String tryReadServerFromMap(Object maybeMap, String key) {
        if (!(maybeMap instanceof Map<?, ?> map)) return null;
        Object dto = map.get(key);
        if (dto == null) return null;
        try {
            Method gs = dto.getClass().getMethod("getServer");
            Object v  = gs.invoke(dto);
            return (v == null) ? null : v.toString();
        } catch (NoSuchMethodException ignored) {
            return null;
        } catch (Throwable t) {
            log.warning("[HOME/SEND-OTHER] read server failed: " + t.getMessage());
            return null;
        }
    }
}
