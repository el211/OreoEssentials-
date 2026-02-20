package fr.elias.oreoEssentials.modules.invlook.commands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.modules.invsee.InvseeService;
import fr.elias.oreoEssentials.util.Lang;
import fr.elias.oreoEssentials.util.Uuids;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * /invlook <player>
 *
 * Opens a live, cross-server inventory viewer in READ-ONLY mode.
 * Uses the same InvseeService pipeline as /invsee, but locks editing.
 */
public final class InvlookCommand implements OreoCommand, TabCompleter {

    @Override
    public String name() {
        return "invlook";
    }

    @Override
    public List<String> aliases() {
        return List.of("invview");
    }

    @Override
    public String permission() {
        return "oreo.invlook";
    }

    @Override
    public String usage() {
        return "<player>";
    }

    @Override
    public boolean playerOnly() {
        return true;
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player viewer)) {
            return true;
        }

        // Permission
        if (!viewer.hasPermission(permission())) {
            Lang.send(viewer, "invlook.no-permission",
                    "<red>You lack permission.</red>");
            return true;
        }

        // Args
        if (args.length != 1) {
            Lang.send(viewer, "invlook.usage",
                    "<red>Usage: /invlook <player></red>");
            return true;
        }

        final OreoEssentials plugin = OreoEssentials.get();
        final InvseeService invseeService = plugin.getInvseeService();

        if (invseeService == null) {
            Lang.send(viewer, "invlook.service-unavailable",
                    "<red>Invsee service is not available on this server.</red>");
            return true;
        }

        // Resolve target UUID
        final UUID targetId = resolveTargetId(args[0]);
        if (targetId == null) {
            Lang.send(viewer, "invlook.player-not-found",
                    "<red>Player not found.</red>");
            return true;
        }

        // Resolve display name
        final String targetName = resolveTargetName(targetId, args[0]);

        // Mark viewer as READ-ONLY (invlook)
        plugin.getInvlookManager().markReadOnly(viewer.getUniqueId());

        // Open the same live cross-server viewer as /invsee
        invseeService.openLocalViewer(viewer, targetId, targetName);

        Lang.send(viewer, "invlook.opening",
                "<gray>Opening read-only inventory of <aqua>%player%</aqua> (cross-server)...</gray>",
                Map.of("player", targetName));

        return true;
    }

    /* ---------------------------------------------------------------------- */
    /* Target resolution                                                       */
    /* ---------------------------------------------------------------------- */

    private static UUID resolveTargetId(String input) {
        // 1) Exact online match (local server)
        Player online = Bukkit.getPlayerExact(input);
        if (online != null) {
            return online.getUniqueId();
        }

        // 2) Raw UUID
        try {
            return UUID.fromString(input);
        } catch (IllegalArgumentException ignored) {
        }

        // 3) Network-wide directory (Mongo / proxy)
        try {
            OreoEssentials plugin = OreoEssentials.get();
            var dir = plugin.getPlayerDirectory();
            if (dir != null) {
                UUID uuid = dir.lookupUuidByName(input);
                if (uuid != null) {
                    return uuid;
                }
            }
        } catch (Throwable ignored) {
        }

        // 4) Final fallback (Floodgate / legacy resolver)
        return Uuids.resolve(input);
    }

    private static String resolveTargetName(UUID uuid, String fallback) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            return online.getName();
        }

        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        if (offline.getName() != null) {
            return offline.getName();
        }

        return fallback;
    }

    /* ---------------------------------------------------------------------- */
    /* Tab completion                                                          */
    /* ---------------------------------------------------------------------- */

    @Override
    public List<String> onTabComplete(CommandSender sender,
                                      org.bukkit.command.Command command,
                                      String alias,
                                      String[] args) {

        if (args.length != 1) {
            return List.of();
        }

        String input = args[0].toLowerCase(Locale.ROOT);
        Set<String> results = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        // Local online players
        for (Player p : Bukkit.getOnlinePlayers()) {
            String name = p.getName();
            if (name != null && name.toLowerCase(Locale.ROOT).startsWith(input)) {
                results.add(name);
            }
        }

        // Network-wide suggestions
        try {
            OreoEssentials plugin = OreoEssentials.get();
            var dir = plugin.getPlayerDirectory();
            if (dir != null) {
                Collection<String> names = dir.suggestOnlineNames(input, 50);
                if (names != null) {
                    for (String name : names) {
                        if (name != null && name.toLowerCase(Locale.ROOT).startsWith(input)) {
                            results.add(name);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        return results.stream().limit(50).collect(Collectors.toList());
    }
}