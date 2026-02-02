package fr.elias.oreoEssentials.modules.tp.rabbit.brokers;

import fr.elias.oreoEssentials.rabbitmq.packet.PacketManager;
import fr.elias.oreoEssentials.modules.spawn.rabbit.packets.SpawnTeleportRequestPacket;
import fr.elias.oreoEssentials.modules.warps.rabbit.packets.WarpTeleportRequestPacket;
import fr.elias.oreoEssentials.modules.spawn.SpawnService;
import fr.elias.oreoEssentials.modules.warps.WarpService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public final class CrossServerTeleportBroker implements Listener {
    private final Plugin plugin;
    private final SpawnService spawnService;
    private final WarpService  warpService;
    private final PacketManager pm;
    private final String thisServer;
    private final Logger log;

    private final Map<UUID, String> lastIntent   = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> pendingJoin = new ConcurrentHashMap<>();

    public CrossServerTeleportBroker(Plugin plugin,
                                     SpawnService spawnService,
                                     WarpService warpService,
                                     PacketManager pm,
                                     String thisServer) {
        this.plugin = plugin;
        this.spawnService = spawnService;
        this.warpService  = warpService;
        this.pm = pm;
        this.thisServer = thisServer;
        this.log = plugin.getLogger();

        pm.subscribe(WarpTeleportRequestPacket.class, (channel, pkt) -> {
            if (!thisServer.equalsIgnoreCase(pkt.getTargetServer())) {
                log.info("[WARP/REQ] ignoring (not my server)");
                return;
            }
            UUID id = pkt.getPlayerId();
            String warp = pkt.getWarpName();

            lastIntent.put(id, "WARP:" + warp);
            pendingJoin.put(id, true);

            Player p = Bukkit.getPlayer(id);
            log.info("[WARP/REQ] recv target=" + thisServer + " warp=" + warp + " player=" + id
                    + " online=" + (p != null && p.isOnline()));

            if (p != null && p.isOnline()) {
                applyWithRetries(id, () -> warpService.getWarp(warp), "WARP", warp);
            }
        });

        pm.subscribe(SpawnTeleportRequestPacket.class, (channel, pkt) -> {
            if (!thisServer.equalsIgnoreCase(pkt.getTargetServer())) {
                log.info("[SPAWN/REQ] ignoring (not my server)");
                return;
            }
            UUID id = pkt.getPlayerId();

            lastIntent.put(id, "SPAWN");
            pendingJoin.put(id, true);

            Player p = Bukkit.getPlayer(id);
            log.info("[SPAWN/REQ] recv target=" + thisServer + " player=" + id
                    + " online=" + (p != null && p.isOnline()));

            if (p != null && p.isOnline()) {
                applyWithRetries(id, spawnService::getSpawn, "SPAWN", null);
            }
        });

        Bukkit.getPluginManager().registerEvents(this, plugin);
        log.info("[BROKER] CrossServerTeleportBroker listening (server=" + thisServer + ")");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        final UUID id = e.getPlayer().getUniqueId();
        if (!pendingJoin.containsKey(id)) return;
        String intent = lastIntent.get(id);
        if (intent == null) return;

        if ("SPAWN".equals(intent)) {
            applyWithRetries(id, spawnService::getSpawn, "SPAWN", null);
            return;
        }

        if (intent.startsWith("WARP:")) {
            String warp = intent.substring("WARP:".length());
            applyWithRetries(id, () -> warpService.getWarp(warp), "WARP", warp);
        }
    }


    private void applyWithRetries(UUID playerId,
                                  java.util.function.Supplier<Location> targetSupplier,
                                  String tag,
                                  String warpNameOrNull) {
        // now
        Bukkit.getScheduler().runTask(plugin, () -> runOnce(playerId, targetSupplier, tag, warpNameOrNull, 0));
        // +2 ticks
        Bukkit.getScheduler().runTaskLater(plugin, () -> runOnce(playerId, targetSupplier, tag, warpNameOrNull, 2), 2L);
        // +10 ticks
        Bukkit.getScheduler().runTaskLater(plugin, () -> runOnce(playerId, targetSupplier, tag, warpNameOrNull, 10), 10L);
    }

    private void runOnce(UUID id,
                         java.util.function.Supplier<Location> supplier,
                         String tag,
                         String warpNameOrNull,
                         int tick) {
        Player p = Bukkit.getPlayer(id);
        if (p == null || !p.isOnline()) return;

        // verify current intent still matches this run
        String expect = lastIntent.get(id);
        if (expect == null) {
            log.info("[" + tag + "/Retry] tick=" + tick + " no-intent-left for " + id);
            return;
        }
        if (!intentMatches(expect, tag, warpNameOrNull)) {
            log.info("[" + tag + "/Retry] tick=" + tick + " intent-changed (have='" + expect + "') for " + id);
            return;
        }

        Location loc = supplier.get();
        if (loc == null) {
            log.warning("[" + tag + "/Retry] tick=" + tick + " target=null"
                    + (warpNameOrNull != null ? " (warp='" + warpNameOrNull + "')" : ""));
            if (tick == 10) {
                pendingJoin.remove(id);
                lastIntent.remove(id);
            }
            return;
        }

        boolean ok = p.teleport(loc);
        log.info("[" + tag + "/Retry] tick=" + tick + " teleported=" + ok
                + " player=" + p.getName() + " -> " + shortLoc(loc)
                + (warpNameOrNull != null ? " (warp='" + warpNameOrNull + "')" : ""));

        if (tick == 10) {
            pendingJoin.remove(id);
            lastIntent.remove(id);
        }
    }

    private boolean intentMatches(String stored, String tag, String warpNameOrNull) {
        if ("SPAWN".equals(tag)) return "SPAWN".equals(stored);
        if ("WARP".equals(tag))  return stored.equals("WARP:" + warpNameOrNull);
        return false;
    }

    private static String shortLoc(Location l) {
        return "loc=" + (l.getWorld() != null ? l.getWorld().getName() : "null")
                + "(" + round(l.getX()) + ", " + round(l.getY()) + ", " + round(l.getZ()) + ")"
                + " yaw=" + round(l.getYaw()) + " pitch=" + round(l.getPitch());
    }
    private static String round(double d) { return String.format(java.util.Locale.ROOT, "%.2f", d); }
    private static String round(float f)  { return String.format(java.util.Locale.ROOT, "%.2f", f); }
}
