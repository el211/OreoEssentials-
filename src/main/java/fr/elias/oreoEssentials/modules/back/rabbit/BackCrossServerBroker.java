package fr.elias.oreoEssentials.modules.back.rabbit;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.back.BackLocation;
import fr.elias.oreoEssentials.modules.back.rabbit.packets.BackTeleportPacket;
import fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel;
import fr.elias.oreoEssentials.rabbitmq.packet.PacketManager;
import fr.elias.oreoEssentials.rabbitmq.packet.event.PacketSubscriber;
import fr.elias.oreoEssentials.modules.tp.service.TeleportService;
import fr.elias.oreoEssentials.util.Lang;
import fr.elias.oreoEssentials.util.ProxyMessenger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BackCrossServerBroker implements Listener {

    private final OreoEssentials plugin;
    private final TeleportService teleportService;
    private final PacketManager packetManager;
    private final ProxyMessenger proxyMessenger;
    private final String localServer;

    private final Map<UUID, BackLocation> pendingBacks = new ConcurrentHashMap<>();

    public BackCrossServerBroker(OreoEssentials plugin,
                                 TeleportService teleportService,
                                 PacketManager packetManager,
                                 ProxyMessenger proxyMessenger,
                                 String localServer) {
        this.plugin = plugin;
        this.teleportService = teleportService;
        this.packetManager = packetManager;
        this.proxyMessenger = proxyMessenger;
        this.localServer = localServer;

        Bukkit.getPluginManager().registerEvents(this, plugin);

        packetManager.subscribe(
                BackTeleportPacket.class,
                (PacketSubscriber<BackTeleportPacket>) (channel, pkt) -> handleIncomingBack(pkt)
        );
    }


    public void requestCrossServerBack(Player player, BackLocation loc) {
        if (player == null || loc == null) return;
        if (packetManager == null || !packetManager.isInitialized()) {
            Lang.send(player, "teleport.back.cross-server.no-messaging",
                    "<red>Cross-server messaging is not available; cannot /back across servers.</red>");
            return;
        }
        if (loc.getServer() == null || loc.getServer().isBlank()) {
            Lang.send(player, "teleport.back.cross-server.no-server",
                    "<red>Back location has no target server.</red>");
            return;
        }

        BackTeleportPacket pkt = new BackTeleportPacket(
                player.getUniqueId(),
                loc.getServer(),
                loc.getWorldName(),
                loc.getX(),
                loc.getY(),
                loc.getZ(),
                loc.getYaw(),
                loc.getPitch()
        );

        packetManager.sendPacket(
                PacketChannel.individual(loc.getServer()),
                pkt
        );

        proxyMessenger.sendToServer(player, loc.getServer());

        Lang.send(player, "teleport.back.cross-server.jumping",
                "<gray>Teleporting back to your previous location on <aqua>%server%</aqua>…</gray>",
                Map.of("server", loc.getServer()));
    }

    private void handleIncomingBack(BackTeleportPacket pkt) {
        if (pkt == null) return;

        if (!localServer.equalsIgnoreCase(pkt.getServer())) {
            return;
        }

        UUID playerId = pkt.getPlayerUuid();
        if (playerId == null) return;

        BackLocation backLoc = new BackLocation(
                pkt.getServer(),
                pkt.getWorld(),
                pkt.getX(),
                pkt.getY(),
                pkt.getZ(),
                pkt.getYaw(),
                pkt.getPitch()
        );

        Player p = Bukkit.getPlayer(playerId);
        if (p != null && p.isOnline()) {
            teleportToBackNow(p, backLoc);
        } else {
            pendingBacks.put(playerId, backLoc);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        BackLocation loc = pendingBacks.remove(p.getUniqueId());
        if (loc == null) return;

        teleportToBackNow(p, loc);
    }

    private void teleportToBackNow(Player p, BackLocation loc) {
        World w = Bukkit.getWorld(loc.getWorldName());
        if (w == null) {
            Lang.send(p, "teleport.back.cross-server.world-not-loaded",
                    "<red>Teleport failed: world <yellow>%world%</yellow> is not loaded.</red>",
                    Map.of("world", loc.getWorldName()));
            return;
        }

        Location bLoc = new Location(w, loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
        teleportService.teleportSilently(p, bLoc);

        Lang.send(p, "teleport.back.cross-server.success",
                "<green>Teleported back.</green>");
    }
}