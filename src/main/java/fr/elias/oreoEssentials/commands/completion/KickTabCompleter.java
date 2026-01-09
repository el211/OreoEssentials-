package fr.elias.oreoEssentials.commands.completion;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.playerdirectory.PlayerDirectory;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;

public class KickTabCompleter implements TabCompleter {

    private final OreoEssentials plugin;

    public KickTabCompleter(OreoEssentials plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {

        if (!cmd.getName().equalsIgnoreCase("kick"))
            return Collections.emptyList();

        if (args.length != 1)
            return Collections.emptyList();

        final String want = args[0].toLowerCase(Locale.ROOT);
        Set<String> out = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        for (Player p : Bukkit.getOnlinePlayers()) {
            String n = p.getName();
            if (n != null && n.toLowerCase(Locale.ROOT).startsWith(want)) {
                out.add(n);
            }
        }

        PlayerDirectory dir = plugin.getPlayerDirectory();
        if (dir != null) {
            try {
                Collection<String> names = dir.suggestOnlineNames(want, 50); // ← changed type here
                if (names != null) {
                    for (String n : names) {
                        if (n != null && n.toLowerCase(Locale.ROOT).startsWith(want)) {
                            out.add(n);
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        return out.stream().limit(50).toList();
    }
}
