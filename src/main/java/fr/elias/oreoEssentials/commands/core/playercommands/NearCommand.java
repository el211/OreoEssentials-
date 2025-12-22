// File: src/main/java/fr/elias/oreoEssentials/commands/core/playercommands/NearCommand.java
package fr.elias.oreoEssentials.commands.core.playercommands;

import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class NearCommand implements OreoCommand {
    @Override public String name() { return "near"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.near"; }
    @Override public String usage() { return "[radius]"; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;

        int radius = 100;
        if (args.length >= 1) {
            try {
                radius = Math.max(1, Math.min(1000, Integer.parseInt(args[0])));
            } catch (NumberFormatException e) {
                Lang.send(
                        p,
                        "near.radius-not-number",
                        null,
                        Map.of("input", args[0])
                );
                return true;
            }
        }

        Location me = p.getLocation();
        int finalRadius = radius;
        var list = Bukkit.getOnlinePlayers().stream()
                .filter(other -> other != p && other.getWorld().equals(p.getWorld()))
                .map(other -> new Entry(other.getName(), other.getLocation().distance(me)))
                .filter(e -> e.dist <= finalRadius)
                .sorted(Comparator.comparingDouble(e -> e.dist))
                .collect(Collectors.toList());

        if (list.isEmpty()) {
            Lang.send(
                    p,
                    "near.none",
                    null,
                    Map.of("radius", String.valueOf(radius))
            );
            return true;
        }

        // Build the colored list; keep the sentence in lang.yml
        String formattedList = list.stream()
                .map(e -> ChatColor.AQUA + e.name
                        + ChatColor.GRAY + " ("
                        + ChatColor.YELLOW + String.format("%.1f", e.dist) + "m"
                        + ChatColor.GRAY + ")")
                .collect(Collectors.joining(ChatColor.GRAY + ", "));

        Lang.send(
                p,
                "near.list",
                null,
                Map.of(
                        "radius", String.valueOf(radius),
                        "list", formattedList
                )
        );
        return true;
    }

    private static final class Entry {
        final String name; final double dist;
        Entry(String n, double d) { name = n; dist = d; }
    }
}
