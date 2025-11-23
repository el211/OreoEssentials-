// File: src/main/java/fr/elias/oreoEssentials/commands/completion/ClearTabCompleter.java
package fr.elias.oreoEssentials.commands.completion;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.playerdirectory.PlayerDirectory;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;

public class ClearTabCompleter implements TabCompleter {

    private final OreoEssentials plugin;

    public ClearTabCompleter(OreoEssentials plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        String cmdName = cmd.getName().toLowerCase(Locale.ROOT);

        // Handle /clear and /ci
        if (!(cmdName.equals("clear") || cmdName.equals("ci"))) {
            return Collections.emptyList();
        }

        // Only complete the first argument
        if (args.length != 1) {
            return Collections.emptyList();
        }

        // Must be allowed to clear others
        if (!sender.hasPermission("oreo.clear.others")) {
            return Collections.emptyList();
        }

        final String partial = args[0];
        final String want = partial.toLowerCase(Locale.ROOT);

        // Use TreeSet to keep results sorted & unique
        Set<String> out = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        // 1) Local online players
        for (Player p : Bukkit.getOnlinePlayers()) {
            String n = p.getName();
            if (n != null && n.toLowerCase(Locale.ROOT).startsWith(want)) {
                out.add(n);
            }
        }

        // 2) Network-wide via PlayerDirectory.suggestOnlineNames()
        PlayerDirectory dir = plugin.getPlayerDirectory();
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
            } catch (Throwable ignored) {
            }
        }

        return out.stream().limit(50).toList();
    }
}
