package fr.elias.oreoEssentials.commands.core.admins;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;
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
            Lang.send(self, "admin.tphere.usage", java.util.Map.of("label", label), self);
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            Lang.send(self, "admin.tphere.not-found", java.util.Map.of("target", args[0]), self);
            return true;
        }

        target.teleport(self.getLocation());
        Lang.send(self, "admin.tphere.brought", java.util.Map.of("target", target.getName()), self);
        if (!target.equals(self)) {
            Lang.send(target, "admin.tphere.notice", java.util.Map.of("player", self.getName()), target);
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

        // On garde la même structure que TpaTabCompleter :
        // Set trié, insensible à la casse
        Set<String> out = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        // 1) Joueurs locaux en ligne
        for (Player p : Bukkit.getOnlinePlayers()) {
            String n = p.getName();
            if (n != null && n.toLowerCase(Locale.ROOT).startsWith(want)) {
                out.add(n);
            }
        }

        // 2) Joueurs réseau via PlayerDirectory.suggestOnlineNames()
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
