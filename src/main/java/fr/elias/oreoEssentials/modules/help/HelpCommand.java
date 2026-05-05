package fr.elias.oreoEssentials.modules.help;

import fr.elias.oreoEssentials.commands.OreoCommand;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class HelpCommand implements OreoCommand, TabCompleter {

    private final HelpConfig config;

    public HelpCommand(HelpConfig config) {
        this.config = config;
    }

    // -----------------------------------------------------------------------
    // OreoCommand metadata
    // -----------------------------------------------------------------------

    @Override public String       name()       { return "help"; }
    @Override public List<String> aliases()    { return List.of(); }
    @Override public String       permission() { return "oreo.help"; }
    @Override public String       usage()      { return "[page|reload]"; }
    @Override public boolean      playerOnly() { return false; }

    // -----------------------------------------------------------------------
    // Execution
    // -----------------------------------------------------------------------

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("oreo.help")) {
            sender.sendMessage(color(config.noPermissionMsg()));
            return true;
        }

        // /help reload
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("oreo.help.admin")) {
                sender.sendMessage(color(config.noPermissionMsg()));
                return true;
            }
            config.load();
            sender.sendMessage(color("&aHelp config reloaded."));
            return true;
        }

        // Simple mode: just send the configured text lines, nothing else
        if (config.isSimpleMode()) {
            for (String line : config.simpleText()) {
                sender.sendMessage(color(line));
            }
            return true;
        }

        // Paginated mode
        int page = 1;
        if (args.length >= 1) {
            try {
                page = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                // not a number — show page 1
            }
        }

        int totalPages = config.totalPages(sender);

        if (page < 1 || page > totalPages) {
            String msg = config.invalidPageMsg()
                    .replace("{max}", String.valueOf(totalPages));
            sender.sendMessage(color(msg));
            return true;
        }

        // Header
        sender.sendMessage(color(
                config.header()
                        .replace("{page}", String.valueOf(page))
                        .replace("{total}", String.valueOf(totalPages))
        ));

        // Entries
        List<HelpConfig.HelpEntry> entries = config.page(sender, page);
        for (HelpConfig.HelpEntry entry : entries) {
            sender.sendMessage(color(
                    entry.command() + " &8- " + entry.description()
            ));
        }

        // Footer
        sender.sendMessage(color(
                config.footer()
                        .replace("{page}", String.valueOf(page))
                        .replace("{total}", String.valueOf(totalPages))
        ));

        return true;
    }

    // -----------------------------------------------------------------------
    // Tab completion
    // -----------------------------------------------------------------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> suggestions = new java.util.ArrayList<>();
            if (!config.isSimpleMode()) {
                int total = config.totalPages(sender);
                for (int i = 1; i <= total; i++) suggestions.add(String.valueOf(i));
            }
            if (sender.hasPermission("oreo.help.admin")) suggestions.add("reload");
            String typed = args[0].toLowerCase(Locale.ROOT);
            return suggestions.stream()
                    .filter(s -> s.startsWith(typed))
                    .toList();
        }
        return Collections.emptyList();
    }

    // -----------------------------------------------------------------------

    private static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
