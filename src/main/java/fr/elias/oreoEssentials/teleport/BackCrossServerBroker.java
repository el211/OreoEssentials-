// src/main/java/fr/elias/oreoEssentials/teleport/BackCrossServerBroker.java
package fr.elias.oreoEssentials.teleport;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.core.playercommands.back.BackLocation;
import fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel;
import fr.elias.oreoEssentials.rabbitmq.packet.PacketManager;
import fr.elias.oreoEssentials.rabbitmq.packet.event.PacketSubscriber;
import fr.elias.oreoEssentials.rabbitmq.packet.impl.tp.BackTeleportPacket;
import fr.elias.oreoEssentials.services.TeleportService;
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

    // playerUuid -> BackLocation à appliquer dès qu'il est sur ce serveur
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

        // écoute join
        Bukkit.getPluginManager().registerEvents(this, plugin);

        // subscribe au packet dédié au /back
        packetManager.subscribe(
                BackTeleportPacket.class,
                (PacketSubscriber<BackTeleportPacket>) (channel, pkt) -> handleIncomingBack(pkt)
        );
    }

    /**
     * Appelé par TeleportService.teleportToServerLocation quand le BackLocation est sur un autre serveur.
     */
    public void requestCrossServerBack(Player player, BackLocation loc) {
        if (player == null || loc == null) return;
        if (packetManager == null || !packetManager.isInitialized()) {
            player.sendMessage("§cCross-server messaging is not available; cannot /back across servers.");
            return;
        }
        if (loc.getServer() == null || loc.getServer().isBlank()) {
            player.sendMessage("§cBack location has no target server.");
            return;
        }

        // On crée le packet avec les coordonnées
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

        // on l'envoie sur le canal individuel du serveur cible
        packetManager.sendPacket(
                PacketChannel.individual(loc.getServer()),
                pkt
        );

        // on switch le joueur sur ce serveur via proxy
        proxyMessenger.sendToServer(player, loc.getServer());

        player.sendMessage("§7Teleporting back to your previous location on §b" + loc.getServer() + "§7…");
    }

    // appelé sur le serveur DESTINATION quand le packet est reçu
    private void handleIncomingBack(BackTeleportPacket pkt) {
        if (pkt == null) return;

        if (!localServer.equalsIgnoreCase(pkt.getServer())) {
            // sécurité : si jamais le packet arrive sur le mauvais serveur
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
            // le joueur est déjà là (cas rare), on TP direct
            teleportToBackNow(p, backLoc);
        } else {
            // sinon on stocke et on attend son join
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
            p.sendMessage("§cTeleport failed: world §e" + loc.getWorldName() + "§c is not loaded.");
            return;
        }
        Location bLoc = new Location(w, loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
        teleportService.teleportSilently(p, bLoc);
        p.sendMessage("§aTeleported back.");
    }
}
