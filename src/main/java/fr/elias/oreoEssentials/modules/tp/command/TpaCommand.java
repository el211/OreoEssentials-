package fr.elias.oreoEssentials.modules.tp.command;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.modules.tp.service.TeleportService;
import fr.elias.oreoEssentials.util.Lang;
import fr.elias.oreoEssentials.util.OreScheduler;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public class TpaCommand implements OreoCommand {

    private final TeleportService teleportService;

    public TpaCommand(TeleportService teleportService) {
        this.teleportService = teleportService;
    }

    @Override public String name() { return "tpa"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.tpa"; }
    @Override public String usage() { return "<player>"; }
    @Override public boolean playerOnly() { return true; }

    private static String traceId() {
        return Long.toString(ThreadLocalRandom.current().nextLong(2176782336L), 36).toUpperCase(Locale.ROOT);
    }

    private static String ms(long startNanos) {
        return ((System.nanoTime() - startNanos) / 1_000_000L) + "ms";
    }

    private boolean dbg() {
        try {
            var c = OreoEssentials.get().getConfig();
            return c.getBoolean("features.tpa.debug", c.getBoolean("debug", false));
        } catch (Throwable ignored) { return false; }
    }

    private boolean echo() {
        try {
            return OreoEssentials.get().getConfig().getBoolean("features.tpa.debug-echo-to-player", false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void D(String id, String msg) {
        if (dbg()) OreoEssentials.get().getLogger().info("[TPA " + id + "] " + msg);
    }

    private void E(String id, String msg, Throwable t) {
        if (dbg()) OreoEssentials.get().getLogger().log(Level.WARNING, "[TPA " + id + "] " + msg, t);
    }

    private void P(Player p, String id, String msg) {
        if (dbg() && echo()) {
            Lang.send(p, "tpa.debug.echo",
                    "<dark_gray>[<aqua>TPA</aqua>/<gray>%id%</gray>]</dark_gray> <gray>%message%</gray>",
                    Map.of("id", id, "message", msg));
        }
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player requester)) return true;

        if (args.length < 1) {
            Lang.send(requester, "tpa.usage",
                    "<red>Usage: /%label% <player></red>", Map.of("label", label));
            return true;
        }

        final String id = traceId();
        final long t0 = System.nanoTime();
        final String input = args[0].trim();
        final OreoEssentials plugin = OreoEssentials.get();
        final String localServer = plugin.getConfigService().serverName();

        D(id, "enter by=" + requester.getName() + " input='" + input + "' server=" + localServer);
        P(requester, id, "start");

        if (input.isEmpty()) {
            Lang.send(requester, "tpa.usage",
                    "<red>Usage: /%label% <player></red>", Map.of("label", label));
            return true;
        }

        // Fast local path: no database involved.
        Player local = resolveOnline(input);
        if (local != null) {
            if (local.equals(requester)) {
                Lang.send(requester, "tpa.self", "<red>You cannot TPA to yourself.</red>");
                return true;
            }
            queueLocalRequest(requester, local);
            D(id, "same-server request queued in " + ms(t0));
            P(requester, id, "same server ✓");
            return true;
        }

        final var dir = plugin.getPlayerDirectory();
        if (dir == null) {
            Lang.send(requester, "tpa.not-found.generic",
                    "<red>Player <white>%input%</white> not found online. <gray>(They may be offline or on another proxy cluster.)</gray></red>",
                    Map.of("input", input));
            return true;
        }

        // PlayerDirectory uses the synchronous Mongo driver. Never perform these lookups
        // on the player's region thread.
        OreScheduler.runAsync(plugin, () -> {
            UUID targetUuid = null;
            String where = null;
            String shownName = input;

            try {
                targetUuid = dir.lookupUuidByName(input);
                if (targetUuid == null) {
                    try { targetUuid = UUID.fromString(input); } catch (Exception ignored) {}
                }

                if (targetUuid != null) {
                    where = dir.lookupCurrentServer(targetUuid);
                    String resolved = dir.lookupNameByUuid(targetUuid);
                    if (resolved != null && !resolved.isBlank()) shownName = resolved;
                }
            } catch (Throwable t) {
                E(id, "directory lookup error", t);
            }

            final UUID resolvedUuid = targetUuid;
            final String resolvedWhere = where;
            final String resolvedName = shownName;

            OreScheduler.runForEntity(plugin, requester, () -> {
                if (!requester.isOnline()) return;

                if (resolvedUuid == null) {
                    Lang.send(requester, "tpa.not-found.name-hint",
                            "<red>Player <white>%input%</white> is not online. <gray>(If they're on another server, use their exact Minecraft name.)</gray></red>",
                            Map.of("input", input));
                    return;
                }

                if (resolvedWhere != null && resolvedWhere.equalsIgnoreCase(localServer)) {
                    Player byId = Bukkit.getPlayer(resolvedUuid);
                    if (byId != null && byId.isOnline()) {
                        if (byId.equals(requester)) {
                            Lang.send(requester, "tpa.self", "<red>You cannot TPA to yourself.</red>");
                            return;
                        }
                        queueLocalRequest(requester, byId);
                        D(id, "same-server via directory in " + ms(t0));
                        return;
                    }
                }

                var broker = plugin.getTpaBroker();
                if (resolvedWhere != null && !resolvedWhere.isBlank()
                        && !resolvedWhere.equalsIgnoreCase(localServer)) {
                    if (broker == null) {
                        Lang.send(requester, "tpa.broker-unavailable",
                                "<red>Cross-server TPA broker is currently unavailable. <gray>Ask </gray><white>%target%</white><gray> to join your server directly.</gray></red>",
                                Map.of("target", resolvedName));
                        return;
                    }

                    // TpaCrossServerBroker is the single notification path. Do not also send a
                    // SendRemoteMessagePacket here, otherwise the target receives the request twice.
                    broker.sendRequestToServer(requester, resolvedUuid, resolvedName, resolvedWhere);
                    Lang.send(requester, "tpa.sent.cross-server",
                            "<gray>Request sent to <aqua>%target%</aqua> on <aqua>%server%</aqua>. They can </gray><green>/tpaccept</green><gray>.</gray>",
                            Map.of("target", resolvedName, "server", resolvedWhere));
                    D(id, "cross-server request sent in " + ms(t0));
                    return;
                }

                if (broker != null) {
                    broker.sendRequestGlobal(requester, resolvedUuid, resolvedName);
                    Lang.send(requester, "tpa.sent.global",
                            "<gray>We broadcast your request to <white>%target%</white> across the network. If they're online, they can </gray><green>/tpaccept</green><gray>.</gray>",
                            Map.of("target", resolvedName));
                } else {
                    Lang.send(requester, "tpa.not-found.generic",
                            "<red>Player <white>%input%</white> not found online. <gray>(They may be offline or on another proxy cluster.)</gray></red>",
                            Map.of("input", input));
                }
                D(id, "directory path done in " + ms(t0));
            });
        });

        return true;
    }

    private void queueLocalRequest(Player requester, Player target) {
        teleportService.request(requester, target);
        OreoEssentials plugin = OreoEssentials.get();
        if (plugin.getDialogManager() != null) {
            OreScheduler.runForEntity(plugin, target, () -> {
                if (target.isOnline() && plugin.getDialogManager() != null) {
                    plugin.getDialogManager().sendTpaRequestDialog(target, requester, false);
                }
            });
        }
    }

    private Player resolveOnline(String input) {
        Player p = Bukkit.getPlayerExact(input);
        if (p != null) return p;

        try {
            UUID id = UUID.fromString(input);
            Player byId = Bukkit.getPlayer(id);
            if (byId != null) return byId;
        } catch (Exception ignored) {}

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getName().equalsIgnoreCase(input)) return online;
            String dn = online.getDisplayName();
            if (dn != null) {
                String stripped = org.bukkit.ChatColor.stripColor(dn);
                if (stripped != null && stripped.equalsIgnoreCase(input)) return online;
            }
        }
        return null;
    }
}
