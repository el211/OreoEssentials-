package fr.elias.oreoEssentials.modules.tp.rabbit.brokers;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel;
import fr.elias.oreoEssentials.rabbitmq.packet.PacketManager;
import fr.elias.oreoEssentials.modules.tp.rabbit.packets.TpJumpPacket;
import fr.elias.oreoEssentials.modules.tp.service.TeleportService;
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

public class TpCrossServerBroker implements Listener {

    private final OreoEssentials plugin;
    private final TeleportService teleportService;
    private final PacketManager packetManager;
    private final ProxyMessenger proxyMessenger;
    private final String localServer;

    private final Map<UUID, UUID> pendingJumps = new ConcurrentHashMap<>();

    public TpCrossServerBroker(OreoEssentials plugin, TeleportService teleportService,
                               PacketManager packetManager, ProxyMessenger proxyMessenger,
                               String localServer) {
        this.plugin = plugin;
        this.teleportService = teleportService;
        this.packetManager = packetManager;
        this.proxyMessenger = proxyMessenger;
        this.localServer = localServer;

        Bukkit.getPluginManager().registerEvents(this, plugin);

        packetManager.subscribe(TpJumpPacket.class,
                (PacketSubscriber<TpJumpPacket>) (channel, pkt) -> handleIncomingJump(pkt));
    }

    public void requestCrossServerTp(Player admin, UUID targetUuid, String targetName, String destServer) {
        if (admin == null || targetUuid == null || destServer == null || destServer.isBlank()) return;
        if (packetManager == null || !packetManager.isInitialized()) {
            Lang.send(admin, "teleport.cross-server.no-messaging",
                    "<red>Cross-server messaging is not available; cannot /tp across servers.</red>");
            return;
        }

        TpJumpPacket pkt = new TpJumpPacket(admin.getUniqueId(), targetUuid, targetName, localServer);
        packetManager.sendPacket(PacketChannel.individual(destServer), pkt);

        proxyMessenger.sendToServer(admin, destServer);

        Lang.send(admin, "teleport.cross-server.jumping",
                "<gray>Teleporting to <aqua>%target%</aqua> on <aqua>%server%</aqua>…</gray>",
                Map.of("target", targetName, "server", destServer));
    }

    private void handleIncomingJump(TpJumpPacket pkt) {
        if (pkt == null) return;

        UUID adminId = pkt.getAdminUuid();
        UUID targetId = pkt.getTargetUuid();

        if (adminId == null || targetId == null) return;

        Player admin = Bukkit.getPlayer(adminId);
        Player target = Bukkit.getPlayer(targetId);

        if (admin != null && admin.isOnline() && target != null && target.isOnline()) {
            teleportService.teleportSilently(admin, target);
            Lang.send(admin, "teleport.cross-server.arrived",
                    "<green>Teleported to <aqua>%target%</aqua>.</green>",
                    Map.of("target", target.getName()));
        } else {
            pendingJumps.put(adminId, targetId);
        }
    }

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