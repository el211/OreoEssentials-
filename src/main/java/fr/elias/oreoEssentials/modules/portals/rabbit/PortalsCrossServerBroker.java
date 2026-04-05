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

/**
 * Handles incoming PortalTeleportPacket messages and pending cross-server teleports.
 *
 * Flow:
 *  1. Server A detects player in cross-server portal
 *  2. Server A sends PortalTeleportPacket(playerId, destWorld, x/y/z/yaw/pitch) → Server B
 *  3. Server A sends BungeeCord "Connect" plugin message to transfer the player to Server B
 *  4. This broker on Server B receives the packet and stores a pending teleport
 *  5. On PlayerJoinEvent, the pending teleport is executed
 */
public final class PortalsCrossServerBroker implements Listener {

    /** playerId → pending teleport info */
    private final ConcurrentHashMap<UUID, PendingTeleport> pending = new ConcurrentHashMap<>();
    /** requestId dedup — prevents double-teleports on redelivery */
    private final ConcurrentHashMap<UUID, String> lastRequestId = new ConcurrentHashMap<>();

    private final OreoEssentials plugin;

    private static final record PendingTeleport(
            String worldName, double x, double y, double z,
            float yaw, float pitch, boolean keepYawPitch, String requestId) {}

    public PortalsCrossServerBroker(OreoEssentials plugin, PacketManager pm) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);

        pm.subscribe(PortalTeleportPacket.class, (channel, pkt) -> {
            if (pkt.getPlayerId() == null) return;

            // Dedup
            String last = lastRequestId.get(pkt.getPlayerId());
            if (pkt.getRequestId() != null && pkt.getRequestId().equals(last)) return;
            lastRequestId.put(pkt.getPlayerId(), pkt.getRequestId());

            Player online = Bukkit.getPlayer(pkt.getPlayerId());
            if (online != null && online.isOnline()) {
                applyTeleport(online, pkt.getWorldName(), pkt.getX(), pkt.getY(), pkt.getZ(),
                        pkt.getYaw(), pkt.getPitch(), pkt.isKeepYawPitch());
            } else {
                pending.put(pkt.getPlayerId(), new PendingTeleport(
                        pkt.getWorldName(), pkt.getX(), pkt.getY(), pkt.getZ(),
                        pkt.getYaw(), pkt.getPitch(), pkt.isKeepYawPitch(), pkt.getRequestId()));
            }
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        PendingTeleport pt = pending.remove(p.getUniqueId());
        if (pt == null) return;

        // Try at 0, 5, 20 ticks to catch slow world loads
        long[] delays = {1L, 5L, 20L};
        for (long delay : delays) {
            OreScheduler.runLater(plugin, () -> {
                if (!p.isOnline()) return;
                if (!pending.containsKey(p.getUniqueId())) {
                    // still valid — try teleport
                    applyTeleport(p, pt.worldName(), pt.x(), pt.y(), pt.z(),
                            pt.yaw(), pt.pitch(), pt.keepYawPitch());
                }
            }, delay);
        }
    }

    private void applyTeleport(Player p, String worldName, double x, double y, double z,
                                float yaw, float pitch, boolean keepYawPitch) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("[Portals] Cross-server teleport: world '" + worldName
                    + "' not found on this server.");
            return;
        }

        float finalYaw   = keepYawPitch ? p.getLocation().getYaw()   : yaw;
        float finalPitch = keepYawPitch ? p.getLocation().getPitch() : pitch;
        Location dest = new Location(world, x, y, z, finalYaw, finalPitch);

        OreScheduler.runForEntity(plugin, p, () -> {
            if (!p.isOnline()) return;
            if (OreScheduler.isFolia()) {
                p.teleportAsync(dest);
            } else {
                p.teleport(dest);
            }
        });
    }

    /**
     * Sends a BungeeCord "Connect" plugin message to transfer a player to another server.
     * Requires "BungeeCord" channel registered in plugin.yml (bungeecord: true in spigot.yml).
     */
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

    /**
     * Sends a cross-server portal teleport request:
     *  - Queues the destination teleport on the target server via RabbitMQ
     *  - Then transfers the player via BungeeCord plugin message
     */
    public void sendCrossServerPortal(PacketManager pm, Player player,
                                      String destServer, String destWorld,
                                      double x, double y, double z,
                                      float yaw, float pitch, boolean keepYawPitch) {
        PortalTeleportPacket pkt = new PortalTeleportPacket(
                player.getUniqueId(), destWorld, x, y, z, yaw, pitch, keepYawPitch);

        // Send the destination info to the target server first, then switch the player
        pm.sendPacket(PacketChannels.individual(destServer), pkt);

        // Small delay to give RabbitMQ time to deliver before the player arrives
        OreScheduler.runLater(plugin, () ->
                connectToServer(plugin, player, destServer), 3L);
    }
}
