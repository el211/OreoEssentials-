// File: src/main/java/fr/elias/oreoEssentials/services/TeleportService.java
package fr.elias.oreoEssentials.modules.tp.service;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.back.BackLocation;
import fr.elias.oreoEssentials.modules.back.service.BackService;
import fr.elias.oreoEssentials.config.ConfigService;
import fr.elias.oreoEssentials.util.Lang;
import fr.elias.oreoEssentials.util.OreScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TeleportService implements Listener {
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

        OreScheduler.runTimer(plugin, this::cleanup, 20L * 30, 20L * 30);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public boolean request(Player from, Player to) {
        if (from == null || to == null || !from.isOnline() || !to.isOnline()) return false;

        final UUID fromId = from.getUniqueId();
        final UUID toId = to.getUniqueId();
        final String fromName = from.getName();
        final String toName = to.getName();
        long exp = System.currentTimeMillis() + timeoutSec * 1000L;
        pendingToTarget.put(toId, new TpaRequest(fromId, exp));

        // Each player is messaged on their own entity scheduler. This matters on Folia when
        // requester and target are in different regions.
        OreScheduler.runForEntity(plugin, to, () -> {
            if (!to.isOnline()) return;
            Lang.send(
                    to,
                    "tpa.request-target",
                    "<yellow><bold>%player%</bold></yellow> <gray>wants to teleport to you.</gray> "
                            + "<dark_gray>(expires in</dark_gray> <white>%timeout%</white><dark_gray>s)</dark_gray>",
                    Map.of("player", fromName, "timeout", String.valueOf(timeoutSec))
            );
        });

        OreScheduler.runForEntity(plugin, from, () -> {
            if (!from.isOnline()) return;
            Lang.send(
                    from,
                    "tpa.sent.local",
                    "<green>Teleport request sent to <yellow>%target%</yellow>.</green>",
                    Map.of("target", toName)
            );
        });
        return true;
    }

    public boolean accept(Player target) {
        if (target == null || !target.isOnline()) return false;

        TpaRequest req = pendingToTarget.remove(target.getUniqueId());
        long now = System.currentTimeMillis();

        if (req == null || req.expiresAt < now) {
            Lang.send(target, "tpa.accept.none", "<red>No pending teleport requests.</red>");
            return false;
        }

        Player from = Bukkit.getPlayer(req.from);
        if (from == null || !from.isOnline()) {
            Lang.send(target, "tpa.accept.requester-offline", "<red>The requester is no longer online.</red>");
            return false;
        }

        // accept() is invoked from the target's entity thread, so capture the destination here.
        final Location dest = target.getLocation().clone();
        final String targetName = target.getName();
        final String requesterName = from.getName();

        OreScheduler.runForEntity(plugin, from, () -> {
            if (!from.isOnline()) return;
            try { if (back != null) back.setLast(from.getUniqueId(), from.getLocation()); } catch (Throwable ignored) {}

            try {
                if (OreScheduler.isFolia()) {
                    from.teleportAsync(dest);
                } else {
                    from.teleport(dest);
                }
            } catch (Throwable ignored) {}

            Lang.send(
                    from,
                    "tpa.teleported",
                    "<green>Teleported to <yellow>%target%</yellow>.</green>",
                    Map.of("target", targetName)
            );
        });

        Lang.send(
                target,
                "tpa.accept.accepted",
                "<green>Accepted teleport request from <yellow>%player%</yellow>.</green>",
                Map.of("player", requesterName)
        );
        return true;
    }

    public boolean deny(Player target) {
        if (target == null || !target.isOnline()) return false;

        TpaRequest req = pendingToTarget.remove(target.getUniqueId());
        if (req == null) {
            Lang.send(target, "tpa.accept.none", "<red>No pending teleport requests.</red>");
            return false;
        }

        final String targetName = target.getName();
        Player from = Bukkit.getPlayer(req.from);
        if (from != null && from.isOnline()) {
            OreScheduler.runForEntity(plugin, from, () -> {
                if (!from.isOnline()) return;
                Lang.send(
                        from,
                        "tpa.deny.requester",
                        "<red>Your teleport request to <yellow>%target%</yellow> was denied.</red>",
                        Map.of("target", targetName)
                );
            });
        }

        Lang.send(target, "tpa.deny.target", "<yellow>Denied the teleport request.</yellow>");
        return true;
    }

    private void cleanup() {
        long now = System.currentTimeMillis();
        pendingToTarget.entrySet().removeIf(e -> e.getValue().expiresAt < now);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        pendingToTarget.remove(uuid);
        pendingToTarget.entrySet().removeIf(e -> e.getValue().from.equals(uuid));
    }

    public void teleportSilently(Player who, Location to) {
        if (who == null || to == null) return;
        final Location destination = to.clone();

        OreScheduler.runForEntity(plugin, who, () -> {
            if (!who.isOnline()) return;
            try { if (back != null) back.setLast(who.getUniqueId(), who.getLocation()); } catch (Throwable ignored) {}
            try {
                if (OreScheduler.isFolia()) {
                    who.teleportAsync(destination);
                } else {
                    who.teleport(destination);
                }
            } catch (Throwable ignored) {}
        });
    }

    public void teleportSilently(Player who, Player target) {
        if (who == null || target == null || !target.isOnline()) return;
        // Read the target's location on the target's owning region, then hand the immutable
        // Location snapshot to the requester's entity scheduler.
        OreScheduler.runForEntity(plugin, target, () -> {
            if (!target.isOnline()) return;
            teleportSilently(who, target.getLocation().clone());
        });
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

        final String requesterName = requester != null ? requester.getName() : "unknown";
        if (requester != null && requester.isOnline()) {
            Lang.send(requester, "tpa.cancelled-moved-requester",
                    "<red>Your teleport request was cancelled because you moved.</red>");
        }

        if (target.isOnline()) {
            OreScheduler.runForEntity(plugin, target, () -> {
                if (!target.isOnline()) return;
                Lang.send(
                        target,
                        "tpa.cancelled-moved-target",
                        "<yellow>Teleport request from <white>%requester%</white> was cancelled (requester moved).</yellow>",
                        Map.of("requester", requesterName)
                );
            });
        }
        return true;
    }

    public boolean teleportToServerLocation(Player who, BackLocation loc) {
        if (who == null || loc == null) return false;

        String localServer = plugin.getConfigService().serverName();
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
