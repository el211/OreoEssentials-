package fr.elias.oreoEssentials.services;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.core.playercommands.back.BackLocation;
import fr.elias.oreoEssentials.commands.core.playercommands.back.BackService;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TeleportService {
    private final OreoEssentials plugin;
    private final BackService back;
    private final int timeoutSec;

    private static class TpaRequest {
        final UUID from;
        final long expiresAt;
        TpaRequest(UUID from, long expiresAt) {
            this.from = from;
            this.expiresAt = expiresAt;
        }
    }

    private final Map<UUID, TpaRequest> pendingToTarget = new ConcurrentHashMap<>();

    public TeleportService(OreoEssentials plugin, BackService back, ConfigService config) {
        this.plugin = plugin;
        this.back = back;
        this.timeoutSec = config.tpaTimeoutSeconds();

        // periodic cleanup avoids stale requests if server uptime is long
        Bukkit.getScheduler().runTaskTimer(plugin, this::cleanup, 20L * 30, 20L * 30);
    }

    /**
     * Crée une requête TPA de "from" vers "to".
     */
    public boolean request(Player from, Player to) {
        long exp = System.currentTimeMillis() + timeoutSec * 1000L;
        pendingToTarget.put(to.getUniqueId(), new TpaRequest(from.getUniqueId(), exp));

        // Message au target: "<player> wants to teleport to you..."
        Lang.send(to,
                "tpa.request-target",
                Map.of(
                        "player", from.getName(),
                        "timeout", String.valueOf(timeoutSec)
                ),
                to
        );

        // Message au requester: "TPA sent to ..."
        Lang.send(from,
                "tpa.sent.local",
                Map.of("target", to.getName()),
                from
        );
        return true;
    }

    /**
     * /tpaccept
     */
    public boolean accept(Player target) {
        TpaRequest req = pendingToTarget.remove(target.getUniqueId());
        long now = System.currentTimeMillis();

        if (req == null || req.expiresAt < now) {
            // "No pending teleport requests."
            Lang.send(target, "tpa.accept.none", null, target);
            return false;
        }

        Player from = Bukkit.getPlayer(req.from);
        if (from == null || !from.isOnline()) {
            // "Requester is no longer online."
            Lang.send(target, "tpa.accept.requester-offline", null, target);
            return false;
        }

        // Enregistrer /back
        back.setLast(from.getUniqueId(), from.getLocation());

        // TP vers le target
        from.teleport(target.getLocation());

        // Message pour le requester: "Teleported to <target>"
        Lang.send(from,
                "tpa.teleported",
                Map.of("target", target.getName()),
                from
        );

        // Message pour le target: "Accepted teleport request."
        Lang.send(target,
                "tpa.accept.accepted",
                Map.of("player", from.getName()),
                target
        );
        return true;
    }

    /**
     * /tpdeny
     */
    public boolean deny(Player target) {
        TpaRequest req = pendingToTarget.remove(target.getUniqueId());
        if (req == null) {
            // Réutilise le même message que pour /tpaccept quand rien n'est en attente
            Lang.send(target, "tpa.accept.none", null, target);
            return false;
        }

        Player from = Bukkit.getPlayer(req.from);
        if (from != null && from.isOnline()) {
            // "Your teleport request to X was denied."
            Lang.send(from,
                    "tpa.deny.requester",
                    Map.of("target", target.getName()),
                    from
            );
        }

        // "Denied teleport request."
        Lang.send(target, "tpa.deny.target", null, target);
        return true;
    }

    private void cleanup() {
        long now = System.currentTimeMillis();
        pendingToTarget.entrySet().removeIf(e -> e.getValue().expiresAt < now);
    }

    // ------------------------------------------------------------------------
    // TP utilitaires
    // ------------------------------------------------------------------------

    /** Teleport sans messages, mais enregistre /back. */
    public void teleportSilently(Player who, Location to) {
        if (who == null || to == null) return;
        try {
            if (back != null) back.setLast(who.getUniqueId(), who.getLocation());
        } catch (Throwable ignored) {}

        Runnable tp = () -> {
            try {
                who.teleport(to);
            } catch (Throwable ignored) {}
        };

        if (Bukkit.isPrimaryThread()) {
            tp.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, tp);
        }
    }

    /** Teleport 'who' vers la position actuelle de 'target' silencieusement. */
    public void teleportSilently(Player who, Player target) {
        if (who == null || target == null) return;
        teleportSilently(who, target.getLocation());
    }

    /**
     * Retourne le requester qui a fait /tpa <target>, ou null.
     */
    public Player getRequester(Player target) {
        if (target == null) return null;

        TpaRequest req = pendingToTarget.get(target.getUniqueId());
        if (req == null) return null;

        return Bukkit.getPlayer(req.from);
    }

    /**
     * Annuler une TPA car le requester a bougé pendant le countdown.
     */
    public boolean cancelRequestDueToMovement(Player target, Player requester) {
        if (target == null) return false;

        TpaRequest req = pendingToTarget.remove(target.getUniqueId());
        if (req == null) {
            return false;
        }

        // Message au requester
        if (requester != null && requester.isOnline()) {
            Lang.send(requester,
                    "tpa.cancelled-moved-requester",
                    null,
                    requester
            );
        }

        // Message au target
        if (target.isOnline()) {
            String name = (requester != null ? requester.getName() : "unknown");
            Lang.send(target,
                    "tpa.cancelled-moved-target",
                    Map.of("requester", name),
                    target
            );
        }

        return true;
    }

    /**
     * Téléporte vers un BackLocation.
     * - Si même serveur : TP Bukkit normal (avec /back enregistré).
     * - Si autre serveur : on passe par BackCrossServerBroker + ProxyMessenger.
     */
    public boolean teleportToServerLocation(Player who, BackLocation loc) {
        if (who == null || loc == null) return false;

        String localServer = plugin.getConfigService().serverName();

        // même serveur -> TP local
        if (loc.getServer() == null
                || loc.getServer().isBlank()
                || loc.getServer().equalsIgnoreCase(localServer)) {

            Location dest = loc.toLocalLocation();
            if (dest == null) {
                return false;
            }

            teleportSilently(who, dest);
            return true;
        }

        // autre serveur -> broker cross-serveur
        var backBroker = plugin.getBackBroker();
        if (backBroker != null && plugin.isMessagingAvailable()) {
            backBroker.requestCrossServerBack(who, loc);
            return true;
        }

        // pas de broker / Rabbit down
        return false;
    }

    public void shutdown() {
        pendingToTarget.clear();
    }
}
