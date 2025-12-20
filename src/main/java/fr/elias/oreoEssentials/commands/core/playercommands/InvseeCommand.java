package fr.elias.oreoEssentials.commands.core.playercommands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.cross.InvseeService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class InvseeCommand implements OreoCommand, TabCompleter {

    @Override public String name()       { return "invsee"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.invsee"; }
    @Override public String usage()      { return "<player>"; }
    @Override public boolean playerOnly(){ return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player viewer)) return true;

        if (!viewer.hasPermission(permission())) {
            viewer.sendMessage(ChatColor.RED + "You lack permission.");
            return true;
        }
        if (args.length < 1) {
            viewer.sendMessage(ChatColor.RED + "Usage: /invsee <player>");
            return true;
        }

        OreoEssentials plugin = OreoEssentials.get();
        InvseeService invseeService = plugin.getInvseeService();

        if (invseeService == null) {
            viewer.sendMessage(ChatColor.RED + "Invsee service is not available on this server.");
            return true;
        }

        UUID targetId = resolveTargetId(args[0]);
        if (targetId == null) {
            viewer.sendMessage(ChatColor.RED + "Player not found.");
            return true;
        }

        String targetName;
        Player online = Bukkit.getPlayer(targetId);
        if (online != null) {
            targetName = online.getName();
        } else {
            OfflinePlayer off = Bukkit.getOfflinePlayer(targetId);
            targetName = (off.getName() != null ? off.getName() : args[0]);
        }

        // This will:
        //  - send an InvseeOpenRequestPacket to the owner node via broker
        //  - open a SmartInvs GUI locally for the viewer
        //  - fill/update it as InvseeStatePacket / InvseeEditPacket flow
        invseeService.openLocalViewer(viewer, targetId, targetName);

        viewer.sendMessage(ChatColor.GRAY + "Opening live inventory of "
                + ChatColor.AQUA + targetName + ChatColor.GRAY + " (cross-server)…");

        return true;
    }

    /** Minimal, API-safe resolver: exact online → UUID string → PlayerDirectory → fallback. */
    private static UUID resolveTargetId(String arg) {
        // 1) Exact online name on this server
        Player p = Bukkit.getPlayerExact(arg);
        if (p != null) return p.getUniqueId();

        // 2) Try parsing as UUID
        try {
            return UUID.fromString(arg);
        } catch (IllegalArgumentException ignored) { }

        // 3) Network-wide via PlayerDirectory (Mongo-backed)
        try {
            var plugin = OreoEssentials.get();
            var dir = plugin.getPlayerDirectory();
            if (dir != null) {
                UUID global = dir.lookupUuidByName(arg);
                if (global != null) return global;
            }
        } catch (Throwable ignored) { }

        // 4) Final fallback: your old resolver (Floodgate, etc.)
        return fr.elias.oreoEssentials.util.Uuids.resolve(arg);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender,
                                      org.bukkit.command.Command cmd,
                                      String alias,
                                      String[] args) {
        if (args.length != 1) return List.of();

        String partial = args[0];
        String want = partial.toLowerCase(Locale.ROOT);

        Set<String> out = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        // 1) Local online players
        for (Player p : Bukkit.getOnlinePlayers()) {
            String n = p.getName();
            if (n != null && n.toLowerCase(Locale.ROOT).startsWith(want)) {
                out.add(n);
            }
        }

        // 2) Network-wide via PlayerDirectory.suggestOnlineNames()
        var plugin = OreoEssentials.get();
        var dir = plugin.getPlayerDirectory();
        if (dir != null) {
            try {
                var names = dir.suggestOnlineNames(want, 50);
                if (names != null) {
                    for (String n : names) {
                        if (n != null && n.toLowerCase(Locale.ROOT).startsWith(want)) {
                            out.add(n);
                        }
                    }
                }
            } catch (Throwable ignored) { }
        }

        // limit to 50 suggestions to keep tab output sane
        return out.stream().limit(50).collect(Collectors.toList());
    }
}
