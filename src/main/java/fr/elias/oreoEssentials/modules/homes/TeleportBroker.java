package fr.elias.oreoEssentials.modules.homes;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.homes.home.HomeService;
import fr.elias.oreoEssentials.modules.homes.rabbit.packet.HomeTeleportRequestPacket;
import fr.elias.oreoEssentials.modules.homes.rabbit.packet.OtherHomeTeleportRequestPacket;
import fr.elias.oreoEssentials.modules.spawn.SpawnService;
import fr.elias.oreoEssentials.modules.spawn.rabbit.packets.SpawnTeleportRequestPacket;
import fr.elias.oreoEssentials.modules.warps.WarpService;
import fr.elias.oreoEssentials.modules.warps.rabbit.packets.WarpTeleportRequestPacket;
import fr.elias.oreoEssentials.rabbitmq.PacketChannels;
import fr.elias.oreoEssentials.rabbitmq.packet.PacketManager;
import fr.elias.oreoEssentials.util.OreScheduler;
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

    private final HomeService homes;
    private final WarpService warps;
    private final SpawnService spawns;

    private final PacketManager pm;
    private final Map<UUID, Runnable> pending = new ConcurrentHashMap<>();

    public TeleportBroker(OreoEssentials plugin,
                          HomeService homes,
                          WarpService warps,
                          SpawnService spawns,
                          PacketManager pm) {
        this.plugin = plugin;
        this.homes = homes;
        this.warps = warps;
        this.spawns = spawns;
        this.pm = pm;
        this.local = homes != null ? homes.localServer() : Bukkit.getServer().getName();

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
                Location loc = spawns.getLocalSpawn();
                teleport(pkt.getPlayerId(), loc, "spawn");
            });
        });

        pm.subscribe(OtherHomeTeleportRequestPacket.class, (ch, pkt) -> {
            if (!local.equalsIgnoreCase(pkt.getTargetServer())) return;
            UUID subject = pkt.getSubjectId();
            UUID owner = pkt.getOwnerId();
            String home = pkt.getHomeName();

            queueOrRun(subject, () -> {
                Location loc = homes.getHome(owner, home);
                teleport(subject, loc, "home " + home + " (owner=" + owner + ")");
            });
        });
    }

    private void queueOrRun(UUID id, Runnable action) {
        OreScheduler.run(plugin, () -> {
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
        if (r != null) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                OreScheduler.runForEntity(plugin, p, r);
            } else {
                OreScheduler.run(plugin, r);
            }
        }
    }

    private void teleport(UUID id, Location loc, String label) {
        Player p = Bukkit.getPlayer(id);
        if (p == null) return;
        if (loc == null) {
            p.sendMessage("\u00A7cTarget " + label + " not found on this server.");
            return;
        }
        OreScheduler.runForEntity(plugin, p, () -> {
            if (OreScheduler.isFolia()) {
                p.teleportAsync(loc).thenRun(() ->
                        p.sendMessage("\u00A7aTeleported to \u00A7b" + label + "\u00A7a."));
            } else {
                p.teleport(loc);
                p.sendMessage("\u00A7aTeleported to \u00A7b" + label + "\u00A7a.");
            }
        });
    }

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

    private String resolveHomeServer(UUID owner, String homeName) {
        try {
            Method m = homes.getClass().getMethod("getHomeServer", UUID.class, String.class);
            Object r = m.invoke(homes, owner, homeName);
            if (r instanceof String s && !s.isBlank()) return s;
        } catch (NoSuchMethodException ignore) {
        } catch (Throwable t) {
            plugin.getLogger().fine("[TeleportBroker] getHomeServer reflect failed: " + t.getMessage());
        }

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

        if (homes.getHome(owner, homeName) != null) return local;
        return null;
    }
}
