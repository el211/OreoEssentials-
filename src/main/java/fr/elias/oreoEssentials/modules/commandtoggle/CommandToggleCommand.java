package fr.elias.oreoEssentials.modules.commandtoggle;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class CommandToggleCommand implements CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private final CommandToggleConfig config;
    private final CommandToggleService service;

    public CommandToggleCommand(JavaPlugin plugin, CommandToggleConfig config, CommandToggleService service) {
        this.plugin = plugin;
        this.config = config;
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("oreo.commandtoggle.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);

        switch (subCommand) {
            case "list":
                listCommands(sender);
                break;

            case "enable":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /commandtoggle enable <command>");
                    return true;
                }
                toggleCommand(sender, args[1], true);
                break;

            case "disable":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /commandtoggle disable <command>");
                    return true;
                }
                toggleCommand(sender, args[1], false);
                break;

            case "reload":
                service.reload();
                sender.sendMessage(ChatColor.GREEN + "[CommandToggle] Configuration reloaded and toggles reapplied!");
                break;

            case "status":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /commandtoggle status <command>");
                    return true;
                }
                checkStatus(sender, args[1]);
                break;

            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "╔════════════════════════════════════════╗");
        sender.sendMessage(ChatColor.GOLD + "║     " + ChatColor.YELLOW + "Command Toggle Management" + ChatColor.GOLD + "      ║");
        sender.sendMessage(ChatColor.GOLD + "╚════════════════════════════════════════╝");
        sender.sendMessage(ChatColor.YELLOW + "/commandtoggle list " + ChatColor.GRAY + "- List all commands");
        sender.sendMessage(ChatColor.YELLOW + "/commandtoggle enable <cmd> " + ChatColor.GRAY + "- Enable a command");
        sender.sendMessage(ChatColor.YELLOW + "/commandtoggle disable <cmd> " + ChatColor.GRAY + "- Disable a command");
        sender.sendMessage(ChatColor.YELLOW + "/commandtoggle status <cmd> " + ChatColor.GRAY + "- Check command status");
        sender.sendMessage(ChatColor.YELLOW + "/commandtoggle reload " + ChatColor.GRAY + "- Reload configuration");
    }

    private void listCommands(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "╔════════════════════════════════════════╗");
        sender.sendMessage(ChatColor.GOLD + "║        " + ChatColor.YELLOW + "Command Toggle Status" + ChatColor.GOLD + "        ║");
        sender.sendMessage(ChatColor.GOLD + "╚════════════════════════════════════════╝");

        List<String> enabled = new ArrayList<>();
        List<String> disabled = new ArrayList<>();

        for (var entry : config.getAllCommands().entrySet()) {
            if (entry.getValue().isEnabled()) {
                enabled.add(entry.getKey());
            } else {
                disabled.add(entry.getKey());
            }
        }

        sender.sendMessage(ChatColor.GREEN + "✓ Enabled (" + enabled.size() + "): " + ChatColor.GRAY + String.join(", ", enabled));
        sender.sendMessage(ChatColor.RED + "✗ Disabled (" + disabled.size() + "): " + ChatColor.GRAY + String.join(", ", disabled));
    }

    private void toggleCommand(CommandSender sender, String commandName, boolean enable) {
        String lower = commandName.toLowerCase(Locale.ROOT);

        if (!config.getAllCommands().containsKey(lower)) {
            sender.sendMessage(ChatColor.RED + "Command '" + commandName + "' not found in commands-toggle.yml");
            return;
        }

        config.setCommandEnabled(lower, enable);
        service.applyToggles();

        String status = enable ? ChatColor.GREEN + "enabled" : ChatColor.RED + "disabled";
        sender.sendMessage(ChatColor.YELLOW + "Command '" + ChatColor.WHITE + commandName + ChatColor.YELLOW + "' has been " + status);
    }

    private void checkStatus(CommandSender sender, String commandName) {
        String lower = commandName.toLowerCase(Locale.ROOT);

        if (!config.getAllCommands().containsKey(lower)) {
            sender.sendMessage(ChatColor.RED + "Command '" + commandName + "' not found in commands-toggle.yml");
            return;
        }

        boolean enabled = config.isCommandEnabled(lower);
        String status = enabled ? ChatColor.GREEN + "ENABLED ✓" : ChatColor.RED + "DISABLED ✗";

        sender.sendMessage(ChatColor.GOLD + "╔════════════════════════════════════════╗");
        sender.sendMessage(ChatColor.GOLD + "║     " + ChatColor.YELLOW + "Command: " + ChatColor.WHITE + commandName + ChatColor.GOLD + "                  ║");
        sender.sendMessage(ChatColor.GOLD + "║     " + ChatColor.YELLOW + "Status: " + status + ChatColor.GOLD + "                 ║");
        sender.sendMessage(ChatColor.GOLD + "╚════════════════════════════════════════╝");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("oreo.commandtoggle.admin")) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            return Arrays.asList("list", "enable", "disable", "status", "reload")
                    .stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("enable")
                || args[0].equalsIgnoreCase("disable")
                || args[0].equalsIgnoreCase("status"))) {

            return config.getAllCommands().keySet()
                    .stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .sorted()
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}