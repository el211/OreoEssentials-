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
            case "list" -> listCommands(sender);

            case "enable" -> {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /commandtoggle enable <command|alias>");
                    return true;
                }
                toggleCommand(sender, args[1], true);
            }

            case "disable" -> {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /commandtoggle disable <command|alias>");
                    return true;
                }
                toggleCommand(sender, args[1], false);
            }

            case "reload" -> {
                service.reload();
                sender.sendMessage(ChatColor.GREEN + "[CommandToggle] Configuration reloaded, aliases rebuilt and toggles reapplied!");
            }

            case "status" -> {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /commandtoggle status <command|alias>");
                    return true;
                }
                checkStatus(sender, args[1]);
            }

            default -> sendHelp(sender);
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "╔════════════════════════════════════════╗");
        sender.sendMessage(ChatColor.GOLD + "║     " + ChatColor.YELLOW + "Command Toggle Management" + ChatColor.GOLD + "      ║");
        sender.sendMessage(ChatColor.GOLD + "╚════════════════════════════════════════╝");
        sender.sendMessage(ChatColor.YELLOW + "/commandtoggle list " + ChatColor.GRAY + "- List all commands");
        sender.sendMessage(ChatColor.YELLOW + "/commandtoggle enable <cmd|alias> " + ChatColor.GRAY + "- Enable a command");
        sender.sendMessage(ChatColor.YELLOW + "/commandtoggle disable <cmd|alias> " + ChatColor.GRAY + "- Disable a command");
        sender.sendMessage(ChatColor.YELLOW + "/commandtoggle status <cmd|alias> " + ChatColor.GRAY + "- Check command status");
        sender.sendMessage(ChatColor.YELLOW + "/commandtoggle reload " + ChatColor.GRAY + "- Reload config and rebuild aliases");
    }

    private void listCommands(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "╔════════════════════════════════════════╗");
        sender.sendMessage(ChatColor.GOLD + "║        " + ChatColor.YELLOW + "Command Toggle Status" + ChatColor.GOLD + "        ║");
        sender.sendMessage(ChatColor.GOLD + "╚════════════════════════════════════════╝");

        List<String> enabled = new ArrayList<>();
        List<String> disabled = new ArrayList<>();

        for (var entry : config.getAllCommands().entrySet()) {
            if (entry.getValue().isEnabled()) enabled.add(entry.getKey());
            else disabled.add(entry.getKey());
        }

        sender.sendMessage(ChatColor.GREEN + "✓ Enabled (" + enabled.size() + "): " + ChatColor.GRAY + String.join(", ", enabled));
        sender.sendMessage(ChatColor.RED + "✗ Disabled (" + disabled.size() + "): " + ChatColor.GRAY + String.join(", ", disabled));
    }

    private void toggleCommand(CommandSender sender, String label, boolean enable) {
        String resolved = config.resolveCommandName(label);
        if (resolved == null) {
            sender.sendMessage(ChatColor.RED + "Command or alias '" + label + "' was not found in commands-toggle.yml");
            return;
        }

        config.setCommandEnabled(resolved, enable);
        service.applyToggles();

        String status = enable ? ChatColor.GREEN + "enabled" : ChatColor.RED + "disabled";
        String normalized = CommandToggleConfig.normalizeLabel(label);
        String suffix = normalized.equals(resolved)
                ? ""
                : ChatColor.GRAY + " (alias of /" + resolved + ")";

        sender.sendMessage(
                ChatColor.YELLOW + "Command '" + ChatColor.WHITE + "/" + resolved
                        + ChatColor.YELLOW + "' has been " + status + suffix
        );
    }

    private void checkStatus(CommandSender sender, String label) {
        String resolved = config.resolveCommandName(label);
        if (resolved == null) {
            sender.sendMessage(ChatColor.RED + "Command or alias '" + label + "' was not found in commands-toggle.yml");
            return;
        }

        boolean enabled = config.isCommandEnabled(resolved);
        String status = enabled ? ChatColor.GREEN + "ENABLED ✓" : ChatColor.RED + "DISABLED ✗";
        CommandToggleConfig.CommandToggleEntry entry = config.getCommand(resolved);

        sender.sendMessage(ChatColor.GOLD + "╔════════════════════════════════════════╗");
        sender.sendMessage(ChatColor.YELLOW + "Command: " + ChatColor.WHITE + "/" + resolved);
        sender.sendMessage(ChatColor.YELLOW + "Status: " + status);
        if (entry != null && !entry.getAliases().isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "Configured aliases: " + ChatColor.GRAY
                    + entry.getAliases().stream().map(a -> "/" + a).collect(Collectors.joining(", ")));
        }
        String normalized = CommandToggleConfig.normalizeLabel(label);
        if (!normalized.equals(resolved)) {
            sender.sendMessage(ChatColor.YELLOW + "Requested label: " + ChatColor.GRAY + "/" + normalized
                    + " → /" + resolved);
        }
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

            String prefix = args[1].toLowerCase(Locale.ROOT);
            return config.getAllCommands().keySet()
                    .stream()
                    .filter(s -> s.startsWith(prefix))
                    .sorted()
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}
