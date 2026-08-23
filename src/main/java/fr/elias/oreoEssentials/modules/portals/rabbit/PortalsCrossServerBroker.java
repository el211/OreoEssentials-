package fr.elias.oreoEssentials.modules.portals.rabbit;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.rabbitmq.PacketChannels;
import fr.elias.oreoEssentials.rabbitmq.packet.PacketManager;
import fr.elias.oreoEssentials.util.OreScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PortalsCrossServerBroker implements Listener {

    private final ConcurrentHashMap<UUID, PendingTeleport> pending = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, String> lastRequestId = new ConcurrentHashMap<>();
    private final OreoEssentials plugin;

    private record PendingTeleport(
            String worldName, double x, double y, double z,
            float yaw, float pitch, boolean keepYawPitch, String requestId) {}

    public PortalsCrossServerBroker(OreoEssentials plugin, PacketManager pm) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);

        pm.subscribe(PortalTeleportPacket.class, (channel, pkt) -> {
            if (pkt.getPlayerId() == null) return;

            String last = lastRequestId.get(pkt.getPlayerId());
            if (pkt.getRequestId() != null && pkt.getRequestId().equals(last)) return;
            lastRequestId.put(pkt.getPlayerId(), pkt.getRequestId());

            PendingTeleport pt = new PendingTeleport(
                    pkt.getWorldName(), pkt.getX(), pkt.getY(), pkt.getZ(),
                    pkt.getYaw(), pkt.getPitch(), pkt.isKeepYawPitch(), pkt.getRequestId());

            Player online = Bukkit.getPlayer(pkt.getPlayerId());
            if (online == null) {
                pending.put(pkt.getPlayerId(), pt);
                return;
            }

            OreScheduler.runForEntity(plugin, online, () -> {
                if (!online.isOnline()) {
                    pending.put(pkt.getPlayerId(), pt);
                    return;
                }
                applyTeleportOnEntityThread(online, pt);
            });
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        PendingTeleport pt = pending.remove(p.getUniqueId());
        if (pt == null) return;

        AtomicBoolean done = new AtomicBoolean(false);
        for (long delay : new long[]{1L, 5L, 20L}) {
            OreScheduler.runLaterForEntity(plugin, p, () -> {
                if (!p.isOnline() || done.get()) return;
                World world = Bukkit.getWorld(pt.worldName());
                if (world == null) return;
                if (done.compareAndSet(false, true)) applyTeleportOnEntityThread(p, pt);
            }, delay);
        }
    }

    private void applyTeleportOnEntityThread(Player p, PendingTeleport pt) {
        World world = Bukkit.getWorld(pt.worldName());
        if (world == null) {
            plugin.getLogger().warning("[Portals] Cross-server teleport: world '" + pt.worldName()
                    + "' not found on this server.");
            return;
        }

        float finalYaw = pt.keepYawPitch() ? p.getLocation().getYaw() : pt.yaw();
        float finalPitch = pt.keepYawPitch() ? p.getLocation().getPitch() : pt.pitch();
        Location dest = new Location(world, pt.x(), pt.y(), pt.z(), finalYaw, finalPitch);

        if (OreScheduler.isFolia()) p.teleportAsync(dest);
        else p.teleport(dest);
    }

    public static void connectToServer(OreoEssentials plugin, Player player, String serverName) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(b);
            out.writeUTF("Connect");
            out.writeUTF(serverName);
            player.sendPluginMessage(plugin, "BungeeCord", b.toByteArray());
        } catch (Exception e) {
            plugin.getLogger().warning("[Portals] Failed to send BungeeCord Connect message: " + e.getMessage());
        }
    }

    public void sendCrossServerPortal(PacketManager pm, Player player,
                                      String destServer, String destWorld,
                                      double x, double y, double z,
                                      float yaw, float pitch, boolean keepYawPitch) {
        PortalTeleportPacket pkt = new PortalTeleportPacket(
                player.getUniqueId(), destWorld, x, y, z, yaw, pitch, keepYawPitch);
        pm.sendPacket(PacketChannels.individual(destServer), pkt);

        OreScheduler.runLaterForEntity(plugin, player, () -> {
            if (player.isOnline()) connectToServer(plugin, player, destServer);
        }, 3L);
    }
}
