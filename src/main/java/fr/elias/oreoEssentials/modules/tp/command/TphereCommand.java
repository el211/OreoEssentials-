// File: src/main/java/fr/elias/oreoEssentials/commands/core/admins/TphereCommand.java
package fr.elias.oreoEssentials.modules.tp.command;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class TphereCommand implements OreoCommand, org.bukkit.command.TabCompleter {

    private final OreoEssentials plugin;

    public TphereCommand(OreoEssentials plugin) {
        this.plugin = plugin;
    }

    @Override public String name() { return "tphere"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.tphere"; }
    @Override public String usage() { return "<player>"; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        Player self = (Player) sender;

        if (args.length < 1) {
            Lang.send(self, "admin.tphere.usage",
                    "<yellow>Usage: /%label% <player></yellow>",
                    Map.of("label", label));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            Lang.send(self, "admin.tphere.not-found",
                    "<red>Player not found: <yellow>%target%</yellow>.</red>",
                    Map.of("target", args[0]));
            return true;
        }

        target.teleport(self.getLocation());

        Lang.send(self, "admin.tphere.brought",
                "<green>Brought <aqua>%target%</aqua> to you.</green>",
                Map.of("target", target.getName()));

        if (!target.equals(self)) {
            Lang.send(target, "admin.tphere.notice",
                    "<yellow>You were teleported to <aqua>%player%</aqua>.</yellow>",
                    Map.of("player", self.getName()));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender,
                                      org.bukkit.command.Command cmd,
                                      String alias,
                                      String[] args) {
        if (args.length != 1) {
            return List.of();
        }

        final String partial = args[0];
        final String want = partial.toLowerCase(Locale.ROOT);

        // Sorted, case-insensitive
        Set<String> out = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        // 1) Local online players
        for (Player p : Bukkit.getOnlinePlayers()) {
            String n = p.getName();
            if (n != null && n.toLowerCase(Locale.ROOT).startsWith(want)) {
                out.add(n);
            }
        }

        // 2) Network-wide suggestions (if directory available)
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
            } catch (Throwable ignored) {}
        }

        return out.stream().limit(50).collect(Collectors.toList());
    }
}