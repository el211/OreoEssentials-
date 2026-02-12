package fr.elias.oreoEssentials.modules.tp.completer;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.playerdirectory.PlayerDirectory;
import fr.elias.oreoEssentials.offline.OfflinePlayerCache;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;

public class TpTabCompleter implements TabCompleter {
    private final OreoEssentials plugin;

    public TpTabCompleter(OreoEssentials plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        String cmdName = cmd.getName().toLowerCase(Locale.ROOT);
        if (!cmdName.equals("tp")) {
            return Collections.emptyList();
        }

        if (args.length != 1) return Collections.emptyList();

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

        OfflinePlayerCache cache = plugin.getOfflinePlayerCache();
        if (cache != null) {
            try {
                for (String name : cache.getNames()) {
                    if (name != null && name.toLowerCase(Locale.ROOT).startsWith(want)) {
                        out.add(name);
                    }
                }
            } catch (Throwable ignored) {}
        }

        return out.stream().limit(100).toList();
    }
}
