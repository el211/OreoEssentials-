package fr.elias.oreoEssentials.modules.rules;

import fr.elias.oreoEssentials.commands.OreoCommand;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * /rules — sends configurable rules text from server/rules.yml.
 * Supports a list of lines, reloads in-place with /rules reload (oreo.rules.admin).
 */
public class RulesCommand implements OreoCommand, TabCompleter {

    private final Plugin plugin;
    private final File   configFile;
    private List<String> lines = Collections.emptyList();

    public RulesCommand(Plugin plugin) {
        this.plugin     = plugin;
        this.configFile = new File(plugin.getDataFolder(), "server/rules.yml");
        load();
    }

    public void load() {
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            plugin.saveResource("server/rules.yml", false);
        }
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(configFile);
        lines = cfg.getStringList("rules.lines");
    }

    @Override public String       name()       { return "rules"; }
    @Override public List<String> aliases()    { return List.of(); }
    @Override public String       permission() { return "oreo.rules"; }
    @Override public String       usage()      { return "[reload]"; }
    @Override public boolean      playerOnly() { return false; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("oreo.rules.admin")) {
                sender.sendMessage(color("&cNo permission."));
                return true;
            }
            load();
            sender.sendMessage(color("&aRules config reloaded."));
            return true;
        }

        if (!sender.hasPermission("oreo.rules")) {
            sender.sendMessage(color("&cNo permission."));
            return true;
        }

        for (String line : lines) {
            sender.sendMessage(color(line));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("oreo.rules.admin")
                && "reload".startsWith(args[0].toLowerCase(Locale.ROOT))) {
            return List.of("reload");
        }
        return Collections.emptyList();
    }

    private static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
