// File: src/main/java/fr/elias/oreoEssentials/trade/TradeCommand.java
package fr.elias.oreoEssentials.trade;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

public final class TradeCommand implements CommandExecutor {

    private final OreoEssentials plugin;
    private final TradeService service;

    public TradeCommand(OreoEssentials plugin, TradeService service) {
        this.plugin = plugin;
        this.service = service;
    }

    /* ----------------------------- Debug Helpers ----------------------------- */

    private boolean dbg() {
        try {
            return service != null && service.getConfig() != null && service.getConfig().debugDeep;
        } catch (Throwable t) {
            return false;
        }
    }

    private void log(String msg) {
        if (dbg()) plugin.getLogger().info(msg);
    }

    /* ----------------------------- Command ----------------------------- */

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String lbl, String[] args) {
        if (!(sender instanceof Player p)) {
            Lang.send(sender, "trade.player-only",
                    "<red>Players only.</red>");
            return true;
        }

        if (plugin.getTradeService() == null) {
            Lang.send(p, "trade.disabled",
                    "<red>Trading is currently disabled.</red>");
            log("[TRADE] /trade denied: service null");
            return true;
        }

        if (!p.hasPermission("oreo.trade")) {
            Lang.send(p, "trade.no-permission",
                    "<red>You don't have permission.</red>");
            log("[TRADE] /trade denied: no permission for " + p.getName());
            return true;
        }

        if (args.length != 1) {
            Lang.send(p, "trade.usage",
                    "<yellow>Usage: /trade <player></yellow>");
            log("[TRADE] /trade bad usage by " + p.getName());
            return true;
        }

        final String targetName = args[0];
        log("[TRADE] /trade invoked by=" + p.getName() + " arg=" + targetName);

        // 1) If this player has an invite FROM <targetName>, accept it.
        boolean accepted = service.tryAcceptInvite(p, targetName);
        if (!accepted && service.tryAcceptInviteAny(p)) return true;

        log("[TRADE] tryAcceptInvite result=" + accepted + " acceptor=" + p.getName() + " requesterName=" + targetName);
        if (accepted) return true;

        // 2) Otherwise this is a NEW invite.
        Player localTarget = Bukkit.getPlayerExact(targetName);
        log("[TRADE] resolve local target -> " + (localTarget != null ? ("FOUND uuid=" + localTarget.getUniqueId()) : "not found"));

        // Local self-check
        if (localTarget != null && localTarget.getUniqueId().equals(p.getUniqueId())) {
            Lang.send(p, "trade.self",
                    "<red>You cannot trade with yourself.</red>");
            log("[TRADE] /trade self-invite blocked for " + p.getName());
            return true;
        }

        // If local target exists and is online → local invite
        if (localTarget != null && localTarget.isOnline()) {
            log("[TRADE] sending LOCAL invite: " + p.getName() + " -> " + localTarget.getName());
            service.sendInvite(p, localTarget); // just sends an invite; GUI opens on accept
            return true;
        }

        // 3) Cross-server path (remote)
        var broker = plugin.getTradeBroker();
        boolean msgAvail = plugin.isMessagingAvailable();
        log("[TRADE] cross-server path: broker=" + (broker != null) + " messagingAvailable=" + msgAvail);

        if (broker != null && msgAvail) {
            // resolve UUID using PlayerDirectory
            UUID targetId = null;
            try {
                var dir = plugin.getPlayerDirectory();
                if (dir != null) {
                    targetId = dir.lookupUuidByName(targetName);
                    log("[TRADE] PlayerDirectory lookup name=" + targetName + " -> " + targetId);
                } else {
                    log("[TRADE] PlayerDirectory is null; cannot resolve remote UUID.");
                }
            } catch (Throwable t) {
                log("[TRADE] PlayerDirectory lookup error: " + t.getMessage());
            }

            if (targetId != null && !targetId.equals(p.getUniqueId())) {
                log("[TRADE] sending REMOTE invite: " + p.getName() + " -> " + targetName + " (" + targetId + ")");
                broker.sendInvite(p, targetId, targetName);  // no GUI yet; receiver gets an invite
                return true;
            } else {
                log("[TRADE] remote invite aborted: targetId=" + targetId + " (self? " + p.getUniqueId().equals(targetId) + ")");
            }
        } else {
            log("[TRADE] remote invite unavailable: broker or messaging missing.");
        }

        Lang.send(p, "trade.not-found-cross-server",
                "<red>Player not found here.</red> <gray>If this is a cross-server trade, type <aqua>/trade %player%</aqua> on the server where you received the invite.</gray>",
                Map.of("player", targetName));
        return true;
    }
}