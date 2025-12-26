// File: src/main/java/fr/elias/oreoEssentials/teleport/TpCrossServerBroker.java
package fr.elias.oreoEssentials.teleport;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.rabbitmq.PacketChannels;
import fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel;
import fr.elias.oreoEssentials.rabbitmq.packet.PacketManager;
import fr.elias.oreoEssentials.rabbitmq.packet.impl.tp.TpJumpPacket;
import fr.elias.oreoEssentials.services.TeleportService;
import fr.elias.oreoEssentials.util.Lang;
import fr.elias.oreoEssentials.util.ProxyMessenger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import fr.elias.oreoEssentials.rabbitmq.packet.event.PacketSubscriber;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cross-server TP broker (admin /tp). Does NOT affect /tpa logic.
 *
 * Flow:
 *  - Source server:
 *      requestCrossServerTp(admin, targetUuid, targetName, destServer)
 *        -> sends TpJumpPacket to destServer
 *        -> uses ProxyMessenger to move admin to destServer
 *
 *  - Destination server:
 *      on TpJumpPacket: remember admin->target in pending map
 *      on PlayerJoin(admin): if pending, snap admin to target using TeleportService.
 */
public class TpCrossServerBroker implements Listener {

    private final OreoEssentials plugin;
    private final TeleportService teleportService;
    private final PacketManager packetManager;
    private final ProxyMessenger proxyMessenger;
    private final String localServer;

    // adminUuid -> targetUuid
    private final Map<UUID, UUID> pendingJumps = new ConcurrentHashMap<>();

    public TpCrossServerBroker(OreoEssentials plugin,
                               TeleportService teleportService,
                               PacketManager packetManager,
                               ProxyMessenger proxyMessenger,
                               String localServer) {
        this.plugin = plugin;
        this.teleportService = teleportService;
        this.packetManager = packetManager;
        this.proxyMessenger = proxyMessenger;
        this.localServer = localServer;

        // listen to join events on this server
        Bukkit.getPluginManager().registerEvents(this, plugin);

        // subscribe to TpJumpPacket (for this server)
        packetManager.subscribe(
                TpJumpPacket.class,
                (PacketSubscriber<TpJumpPacket>) (channel, pkt) -> handleIncomingJump(pkt)
        );
    }

    /** Called by /tp on the source server. */
    public void requestCrossServerTp(Player admin,
                                     UUID targetUuid,
                                     String targetName,
                                     String destServer) {
        if (admin == null || targetUuid == null || destServer == null || destServer.isBlank()) return;
        if (packetManager == null || !packetManager.isInitialized()) {
            Lang.send(admin, "teleport.cross-server.no-messaging",
                    "<red>Cross-server messaging is not available; cannot /tp across servers.</red>");
            return;
        }

        // send packet to destination server
        TpJumpPacket pkt = new TpJumpPacket(
                admin.getUniqueId(),
                targetUuid,
                targetName,
                localServer
        );
        packetManager.sendPacket(
                PacketChannel.individual(destServer),
                pkt
        );

        // switch admin to that server via proxy
        proxyMessenger.sendToServer(admin, destServer);

        Lang.send(admin, "teleport.cross-server.jumping",
                "<gray>Teleporting to <aqua>%target%</aqua> on <aqua>%server%</aqua>…</gray>",
                Map.of("target", targetName, "server", destServer));
    }

    /** Handles packet on destination server. */
    private void handleIncomingJump(TpJumpPacket pkt) {
        if (pkt == null) return;

        UUID adminId = pkt.getAdminUuid();
        UUID targetId = pkt.getTargetUuid();

        if (adminId == null || targetId == null) return;

        Player admin = Bukkit.getPlayer(adminId);
        Player target = Bukkit.getPlayer(targetId);

        if (admin != null && admin.isOnline() && target != null && target.isOnline()) {
            // Already both here: teleport immediately
            teleportService.teleportSilently(admin, target);
            Lang.send(admin, "teleport.cross-server.arrived",
                    "<green>Teleported to <aqua>%target%</aqua>.</green>",
                    Map.of("target", target.getName()));
        } else {
            // Wait for admin to join this server
            pendingJumps.put(adminId, targetId);
        }
    }

    /** When any player joins this server, check if we have a pending /tp jump for them. */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        UUID adminId = p.getUniqueId();

        UUID targetId = pendingJumps.remove(adminId);
        if (targetId == null) return;

        Player target = Bukkit.getPlayer(targetId);
        if (target == null || !target.isOnline()) {
            Lang.send(p, "teleport.cross-server.target-offline",
                    "<red>Teleport failed: target player is no longer online.</red>");
            return;
        }

        teleportService.teleportSilently(p, target);
        Lang.send(p, "teleport.cross-server.arrived",
                "<green>Teleported to <aqua>%target%</aqua>.</green>",
                Map.of("target", target.getName()));
    }
}