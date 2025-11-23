package fr.elias.oreoEssentials.services;


import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.Location;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TeleportService {
    private final OreoEssentials plugin;
    private final BackService back;
    private final int timeoutSec;

    private static class TpaRequest { final UUID from; final long expiresAt; TpaRequest(UUID from, long expiresAt){this.from=from;this.expiresAt=expiresAt;} }
    private final Map<UUID, TpaRequest> pendingToTarget = new ConcurrentHashMap<>();

    public TeleportService(OreoEssentials plugin, BackService back, ConfigService config) {
        this.plugin = plugin;
        this.back = back;
        this.timeoutSec = config.tpaTimeoutSeconds();
        // why: periodic cleanup avoids stale requests if server uptime is long
        Bukkit.getScheduler().runTaskTimer(plugin, this::cleanup, 20L*30, 20L*30);
    }

    public boolean request(Player from, Player to) {
        long exp = System.currentTimeMillis() + timeoutSec*1000L;
        pendingToTarget.put(to.getUniqueId(), new TpaRequest(from.getUniqueId(), exp));
        to.sendMessage("§e" + from.getName() + " wants to teleport to you. §a/tpaccept §eor §c/tpdeny §e(" + timeoutSec + "s).");
        from.sendMessage("§eTPA sent to §b" + to.getName() + "§e.");
        return true;
    }

    public boolean accept(Player target) {
        TpaRequest req = pendingToTarget.remove(target.getUniqueId());
        if (req == null || req.expiresAt < System.currentTimeMillis()) {
            target.sendMessage("§cNo pending teleport requests.");
            return false;
        }
        Player from = Bukkit.getPlayer(req.from);
        if (from == null || !from.isOnline()) {
            target.sendMessage("§cRequester is no longer online.");
            return false;
        }
        back.setLast(from.getUniqueId(), from.getLocation());
        from.teleport(target.getLocation());
        from.sendMessage("§aTeleported to §b" + target.getName() + "§a.");
        target.sendMessage("§aAccepted teleport request.");
        return true;
    }

    public boolean deny(Player target) {
        TpaRequest req = pendingToTarget.remove(target.getUniqueId());
        if (req == null) {
            target.sendMessage("§cNo pending teleport requests.");
            return false;
        }
        Player from = Bukkit.getPlayer(req.from);
        if (from != null && from.isOnline()) from.sendMessage("§cYour teleport request was denied.");
        target.sendMessage("§aDenied teleport request.");
        return true;
    }

    private void cleanup() {
        long now = System.currentTimeMillis();
        pendingToTarget.entrySet().removeIf(e -> e.getValue().expiresAt < now);
    }
// paste inside TeleportService (anywhere under other methods)

    /** Teleport without chat messages, but still record /back. */
    public void teleportSilently(Player who, Location to) {
        if (who == null || to == null) return;
        try {
            // record /back safely
            if (back != null) back.setLast(who.getUniqueId(), who.getLocation());
        } catch (Throwable ignored) {}

        // ensure main thread for the actual teleport
        Runnable tp = () -> {
            try { who.teleport(to); } catch (Throwable ignored) {}
        };
        if (Bukkit.isPrimaryThread()) {
            tp.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, tp);
        }
    }

    /** Convenience: teleport 'who' to the current location of 'target' silently. */
    public void teleportSilently(Player who, Player target) {
        if (who == null || target == null) return;
        teleportSilently(who, target.getLocation());
    }
    /**
     * Returns the requester who issued /tpa <target>, or null.
     */
    public Player getRequester(Player target) {
        if (target == null) return null;

        TpaRequest req = pendingToTarget.get(target.getUniqueId());
        if (req == null) return null;

        return Bukkit.getPlayer(req.from);
    }
    /**
     * Cancel a pending TPA request because the requester moved during the countdown.
     */
    public boolean cancelRequestDueToMovement(Player target, Player requester) {
        if (target == null) return false;

        TpaRequest req = pendingToTarget.remove(target.getUniqueId());
        if (req == null) {
            // no pending request for this target
            return false;
        }

        // Optional: ensure it's the same requester
        if (requester != null && !req.from.equals(requester.getUniqueId())) {
            // Not the same requester, but we already removed it anyway.
        }

        // Message to the requester (the one who did /tpa)
        if (requester != null && requester.isOnline()) {
            requester.sendMessage("§cYour teleport request was cancelled because you moved.");
        }

        // Message to the target (the one who did /tpaccept)
        if (target.isOnline()) {
            String name = (requester != null ? requester.getName() : "a player");
            target.sendMessage("§cThe teleport request from §e" + name + "§c was cancelled because they moved.");
        }

        return true;
    }

    public void shutdown() {
        pendingToTarget.clear();
    }
}

