// File: src/main/java/fr/elias/oreoEssentials/homes/TeleportBroker.java
package fr.elias.oreoEssentials.modules.homes;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.rabbitmq.PacketChannels;
import fr.elias.oreoEssentials.rabbitmq.packet.PacketManager;
import fr.elias.oreoEssentials.modules.homes.rabbit.packet.HomeTeleportRequestPacket;
import fr.elias.oreoEssentials.modules.homes.rabbit.packet.OtherHomeTeleportRequestPacket;
import fr.elias.oreoEssentials.modules.spawn.rabbit.packets.SpawnTeleportRequestPacket;
import fr.elias.oreoEssentials.modules.warps.rabbit.packets.WarpTeleportRequestPacket;
import fr.elias.oreoEssentials.modules.homes.home.HomeService;
import fr.elias.oreoEssentials.modules.spawn.SpawnService;
import fr.elias.oreoEssentials.modules.warps.WarpService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TeleportBroker {
    private final OreoEssentials plugin;
    private final String local;

    private final HomeService  homes;
    private final WarpService  warps;
    private final SpawnService spawns;

    private final PacketManager pm;

    // pending teleports if player hasn’t joined yet
    private final Map<UUID, Runnable> pending = new ConcurrentHashMap<>();

    public TeleportBroker(OreoEssentials plugin,
                          HomeService homes,
                          WarpService warps,
                          SpawnService spawns,
                          PacketManager pm) {
        this.plugin = plugin;
        this.homes  = homes;
        this.warps  = warps;
        this.spawns = spawns;
        this.pm     = pm;
        this.local  = homes != null ? homes.localServer() : Bukkit.getServer().getName();

        // ===== SUBSCRIBE: existing packets =====
        pm.subscribe(HomeTeleportRequestPacket.class, (ch, pkt) -> {
            if (!local.equalsIgnoreCase(pkt.getTargetServer())) return;
            queueOrRun(pkt.getPlayerId(), () -> {
                Location loc = homes.getHome(pkt.getPlayerId(), pkt.getHomeName());
                teleport(pkt.getPlayerId(), loc, "home " + pkt.getHomeName());
            });
        });

        pm.subscribe(WarpTeleportRequestPacket.class, (ch, pkt) -> {
            if (!local.equalsIgnoreCase(pkt.getTargetServer())) return;
            queueOrRun(pkt.getPlayerId(), () -> {
                Location loc = warps.getWarp(pkt.getWarpName());
                teleport(pkt.getPlayerId(), loc, "warp " + pkt.getWarpName());
            });
        });

        pm.subscribe(SpawnTeleportRequestPacket.class, (ch, pkt) -> {
            if (!local.equalsIgnoreCase(pkt.getTargetServer())) return;
            queueOrRun(pkt.getPlayerId(), () -> {
                Location loc = spawns.getSpawn();
                teleport(pkt.getPlayerId(), loc, "spawn");
            });
        });

        // ===== SUBSCRIBE: NEW other-home packet =====
        pm.subscribe(OtherHomeTeleportRequestPacket.class, (ch, pkt) -> {
            if (!local.equalsIgnoreCase(pkt.getTargetServer())) return;
            UUID subject = pkt.getSubjectId();
            UUID owner   = pkt.getOwnerId();
            String home  = pkt.getHomeName();

            queueOrRun(subject, () -> {
                Location loc = homes.getHome(owner, home); // NOTE: owner here!
                teleport(subject, loc, "home " + home + " (owner=" + owner + ")");
            });
        });
    }

    private void queueOrRun(UUID id, Runnable action) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player p = Bukkit.getPlayer(id);
            if (p == null) {
                pending.put(id, action);
            } else {
                action.run();
            }
        });
    }

    public void onJoin(UUID id) {
        Runnable r = pending.remove(id);
        if (r != null) Bukkit.getScheduler().runTask(plugin, r);
    }

    private void teleport(UUID id, Location loc, String label) {
        Player p = Bukkit.getPlayer(id);
        if (p == null) return;
        if (loc == null) {
            p.sendMessage("§cTarget " + label + " not found on this server.");
            return;
        }
        p.teleport(loc);
        p.sendMessage("§aTeleported to §b" + label + "§a.");
    }

    /* ===================== PUBLISH HELPERS ===================== */

    /** Existing helper you might already have for self /home (optional) */
    public boolean requestTeleportSelfHome(UUID playerId, String homeName, String targetServer) {
        try {
            pm.sendPacket(PacketChannels.individual(targetServer),
                    new HomeTeleportRequestPacket(playerId, homeName, targetServer));
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning("[TeleportBroker] publish self-home failed: " + t.getMessage());
            return false;
        }
    }

    /**  admin wants to go to someone else’s home */
    public boolean requestTeleportOtherHome(UUID subjectAdmin, UUID owner, String homeName) {
        String target = resolveHomeServer(owner, homeName);
        if (target == null || target.isBlank()) return false;
        try {
            pm.sendPacket(PacketChannels.individual(target),
                    new OtherHomeTeleportRequestPacket(subjectAdmin, owner, homeName, target, null));
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning("[TeleportBroker] publish other-home failed: " + t.getMessage());
            return false;
        }
    }

    /** Resolve which server holds the owner’s home. Tries direct API, then reflection, then locality. */
    private String resolveHomeServer(UUID owner, String homeName) {
        // 1) If HomeService exposes a direct method (preferred)
        try {
            // e.g., String getHomeServer(UUID, String)
            Method m = homes.getClass().getMethod("getHomeServer", UUID.class, String.class);
            Object r = m.invoke(homes, owner, homeName);
            if (r instanceof String s && !s.isBlank()) return s;
        } catch (NoSuchMethodException ignore) {
        } catch (Throwable t) {
            plugin.getLogger().fine("[TeleportBroker] getHomeServer reflect failed: " + t.getMessage());
        }

        // 2) Some builds expose a descriptor with getServer()
        try {
            Method m = homes.getClass().getMethod("getHomeDescriptor", UUID.class, String.class);
            Object desc = m.invoke(homes, owner, homeName);
            if (desc != null) {
                Method getServer = desc.getClass().getMethod("getServer");
                Object sv = getServer.invoke(desc);
                if (sv instanceof String s && !s.isBlank()) return s;
            }
        } catch (NoSuchMethodException ignore) {
        } catch (Throwable t) {
            plugin.getLogger().fine("[TeleportBroker] getHomeDescriptor reflect failed: " + t.getMessage());
        }

        // 3) If the home exists locally, it's this server
        if (homes.getHome(owner, homeName) != null) return local;

        return null;
    }
}
