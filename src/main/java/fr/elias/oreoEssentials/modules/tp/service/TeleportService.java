// File: src/main/java/fr/elias/oreoEssentials/services/TeleportService.java
package fr.elias.oreoEssentials.modules.tp.service;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.back.BackLocation;
import fr.elias.oreoEssentials.modules.back.service.BackService;
import fr.elias.oreoEssentials.config.ConfigService;
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

        Bukkit.getScheduler().runTaskTimer(plugin, this::cleanup, 20L * 30, 20L * 30);
    }

    public boolean request(Player from, Player to) {
        if (from == null || to == null || !from.isOnline() || !to.isOnline()) return false;

        long exp = System.currentTimeMillis() + timeoutSec * 1000L;
        pendingToTarget.put(to.getUniqueId(), new TpaRequest(from.getUniqueId(), exp));

        // Notify target
        Lang.send(
                to,
                "tpa.request-target",
                "<yellow><bold>%player%</bold></yellow> <gray>wants to teleport to you.</gray> "
                        + "<dark_gray>(expires in</dark_gray> <white>%timeout%</white><dark_gray>s)</dark_gray>",
                Map.of(
                        "player", from.getName(),
                        "timeout", String.valueOf(timeoutSec)
                )
        );

        Lang.send(
                from,
                "tpa.sent.local",
                "<green>Teleport request sent to <yellow>%target%</yellow>.</green>",
                Map.of("target", to.getName())
        );
        return true;
    }

    public boolean accept(Player target) {
        if (target == null || !target.isOnline()) return false;

        TpaRequest req = pendingToTarget.remove(target.getUniqueId());
        long now = System.currentTimeMillis();

        if (req == null || req.expiresAt < now) {
            Lang.send(
                    target,
                    "tpa.accept.none",
                    "<red>No pending teleport requests.</red>"
            );
            return false;
        }

        Player from = Bukkit.getPlayer(req.from);
        if (from == null || !from.isOnline()) {
            Lang.send(
                    target,
                    "tpa.accept.requester-offline",
                    "<red>The requester is no longer online.</red>"
            );
            return false;
        }

        // record /back
        try { if (back != null) back.setLast(from.getUniqueId(), from.getLocation()); } catch (Throwable ignored) {}

        // do TP
        Location dest = target.getLocation();
        if (dest != null) {
            if (Bukkit.isPrimaryThread()) {
                from.teleport(dest);
            } else {
                Bukkit.getScheduler().runTask(plugin, () -> from.teleport(dest));
            }
        }

        // messages
        Lang.send(
                from,
                "tpa.teleported",
                "<green>Teleported to <yellow>%target%</yellow>.</green>",
                Map.of("target", target.getName())
        );

        Lang.send(
                target,
                "tpa.accept.accepted",
                "<green>Accepted teleport request from <yellow>%player%</yellow>.</green>",
                Map.of("player", from.getName())
        );

        return true;
    }

    public boolean deny(Player target) {
        if (target == null || !target.isOnline()) return false;

        TpaRequest req = pendingToTarget.remove(target.getUniqueId());
        if (req == null) {
            Lang.send(
                    target,
                    "tpa.accept.none",
                    "<red>No pending teleport requests.</red>"
            );
            return false;
        }

        Player from = Bukkit.getPlayer(req.from);
        if (from != null && from.isOnline()) {
            Lang.send(
                    from,
                    "tpa.deny.requester",
                    "<red>Your teleport request to <yellow>%target%</yellow> was denied.</red>",
                    Map.of("target", target.getName())
            );
        }

        Lang.send(
                target,
                "tpa.deny.target",
                "<yellow>Denied the teleport request.</yellow>"
        );
        return true;
    }

    private void cleanup() {
        long now = System.currentTimeMillis();
        pendingToTarget.entrySet().removeIf(e -> e.getValue().expiresAt < now);
    }


    public void teleportSilently(Player who, Location to) {
        if (who == null || to == null) return;
        try { if (back != null) back.setLast(who.getUniqueId(), who.getLocation()); } catch (Throwable ignored) {}

        Runnable tp = () -> { try { who.teleport(to); } catch (Throwable ignored) {} };

        if (Bukkit.isPrimaryThread()) tp.run();
        else Bukkit.getScheduler().runTask(plugin, tp);
    }

    public void teleportSilently(Player who, Player target) {
        if (who == null || target == null || !target.isOnline()) return;
        teleportSilently(who, target.getLocation());
    }

    public Player getRequester(Player target) {
        if (target == null) return null;
        TpaRequest req = pendingToTarget.get(target.getUniqueId());
        return (req == null) ? null : Bukkit.getPlayer(req.from);
    }

    public boolean cancelRequestDueToMovement(Player target, Player requester) {
        if (target == null) return false;

        TpaRequest req = pendingToTarget.remove(target.getUniqueId());
        if (req == null) return false;

        if (requester != null && requester.isOnline()) {
            Lang.send(
                    requester,
                    "tpa.cancelled-moved-requester",
                    "<red>Your teleport request was cancelled because you moved.</red>"
            );
        }

        if (target.isOnline()) {
            String name = requester != null ? requester.getName() : "unknown";
            Lang.send(
                    target,
                    "tpa.cancelled-moved-target",
                    "<yellow>Teleport request from <white>%requester%</white> was cancelled (requester moved).</yellow>",
                    Map.of("requester", name)
            );
        }

        return true;
    }


    public boolean teleportToServerLocation(Player who, BackLocation loc) {
        if (who == null || loc == null) return false;

        String localServer = plugin.getConfigService().serverName();

        // same server → local tp
        if (loc.getServer() == null
                || loc.getServer().isBlank()
                || loc.getServer().equalsIgnoreCase(localServer)) {

            Location dest = loc.toLocalLocation();
            if (dest == null) return false;

            teleportSilently(who, dest);
            return true;
        }

        var backBroker = plugin.getBackBroker();
        if (backBroker != null && plugin.isMessagingAvailable()) {
            backBroker.requestCrossServerBack(who, loc);
            return true;
        }

        return false;
    }

    public void shutdown() {
        pendingToTarget.clear();
    }
}
