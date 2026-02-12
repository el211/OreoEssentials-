package fr.elias.oreoEssentials.modules.maintenance;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;


public class MaintenanceCommand implements CommandExecutor, TabCompleter {
    private final OreoEssentials plugin;
    private final MaintenanceService service;
    private final MaintenanceConfig config;

    public MaintenanceCommand(OreoEssentials plugin, MaintenanceService service) {
        this.plugin = plugin;
        this.service = service;
        this.config = service.getConfig();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("oreo.maintenance.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "on":
            case "enable":
                handleEnable(sender);
                break;

            case "off":
            case "disable":
                handleDisable(sender);
                break;

            case "toggle":
                handleToggle(sender);
                break;

            case "status":
                handleStatus(sender);
                break;

            case "whitelist":
                handleWhitelist(sender, Arrays.copyOfRange(args, 1, args.length));
                break;

            case "clock":
            case "timer":
                handleTimer(sender, Arrays.copyOfRange(args, 1, args.length));
                break;

            case "motd":
                handleMotd(sender, Arrays.copyOfRange(args, 1, args.length));
                break;

            case "reload":
                handleReload(sender);
                break;

            case "serverlist":
            case "display":
                handleServerList(sender, Arrays.copyOfRange(args, 1, args.length));
                break;

            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void handleEnable(CommandSender sender) {
        if (service.isEnabled()) {
            sender.sendMessage(ChatColor.YELLOW + "Maintenance mode is already enabled!");
            return;
        }

        service.enable();
        sender.sendMessage(ChatColor.GREEN + "✓ Maintenance mode enabled!");

        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                "&c&l⚠ MAINTENANCE MODE ENABLED ⚠\n" +
                        "&7The server is entering maintenance mode.\n" +
                        "&7Non-whitelisted players will be kicked."));
    }

    private void handleDisable(CommandSender sender) {
        if (!service.isEnabled()) {
            sender.sendMessage(ChatColor.YELLOW + "Maintenance mode is already disabled!");
            return;
        }

        service.disable();
        sender.sendMessage(ChatColor.GREEN + "✓ Maintenance mode disabled!");

        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                "&a&l✓ MAINTENANCE MODE DISABLED ✓\n" +
                        "&7The server is now open to all players."));
    }

    private void handleToggle(CommandSender sender) {
        boolean newState = service.toggle();

        if (newState) {
            sender.sendMessage(ChatColor.GREEN + "✓ Maintenance mode enabled!");
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                    "&c&l⚠ MAINTENANCE MODE ENABLED ⚠\n" +
                            "&7The server is entering maintenance mode."));
        } else {
            sender.sendMessage(ChatColor.GREEN + "✓ Maintenance mode disabled!");
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                    "&a&l✓ MAINTENANCE MODE DISABLED ✓\n" +
                            "&7The server is now open to all players."));
        }
    }

    private void handleStatus(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "═══ Maintenance Status ═══");
        sender.sendMessage(ChatColor.YELLOW + "Status: " +
                (service.isEnabled() ? ChatColor.RED + "ENABLED" : ChatColor.GREEN + "DISABLED"));

        if (config.isUseTimer() && config.getRemainingTime() > 0) {
            sender.sendMessage(ChatColor.YELLOW + "Timer: " + ChatColor.WHITE +
                    service.getFormattedTimeRemaining() + " remaining");
        } else {
            sender.sendMessage(ChatColor.YELLOW + "Timer: " + ChatColor.GRAY + "Not set");
        }

        sender.sendMessage(ChatColor.YELLOW + "Whitelisted players: " + ChatColor.WHITE +
                config.getWhitelist().size());
        sender.sendMessage(ChatColor.GOLD + "═════════════════════════");
    }

    private void handleWhitelist(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Usage: /maintenance whitelist <add|remove|list|clear> [player]");
            return;
        }

        String action = args[0].toLowerCase();

        switch (action) {
            case "add":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /maintenance whitelist add <player>");
                    return;
                }
                handleWhitelistAdd(sender, args[1]);
                break;

            case "remove":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /maintenance whitelist remove <player>");
                    return;
                }
                handleWhitelistRemove(sender, args[1]);
                break;

            case "list":
                handleWhitelistList(sender);
                break;

            case "clear":
                handleWhitelistClear(sender);
                break;

            default:
                sender.sendMessage(ChatColor.RED + "Usage: /maintenance whitelist <add|remove|list|clear> [player]");
                break;
        }
    }

    private void handleWhitelistAdd(CommandSender sender, String playerName) {
        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);

        if (target != null && target.hasPlayedBefore()) {
            service.addToWhitelist(target.getUniqueId());
            sender.sendMessage(ChatColor.GREEN + "✓ Added " + playerName + " to maintenance whitelist (UUID)");
        } else {
            service.addToWhitelist(playerName);
            sender.sendMessage(ChatColor.GREEN + "✓ Added " + playerName + " to maintenance whitelist (Name)");
        }
    }

    private void handleWhitelistRemove(CommandSender sender, String playerName) {
        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);

        boolean removed = false;

        if (target != null && target.hasPlayedBefore()) {
            service.removeFromWhitelist(target.getUniqueId());
            removed = true;
        }

        service.removeFromWhitelist(playerName);

        sender.sendMessage(ChatColor.GREEN + "✓ Removed " + playerName + " from maintenance whitelist");
    }

    private void handleWhitelistList(CommandSender sender) {
        List<String> whitelist = config.getWhitelist();

        if (whitelist.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "Maintenance whitelist is empty.");
            return;
        }

        sender.sendMessage(ChatColor.GOLD + "═══ Maintenance Whitelist ═══");
        for (String entry : whitelist) {
            try {
                UUID uuid = UUID.fromString(entry);
                OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
                sender.sendMessage(ChatColor.YELLOW + "• " + ChatColor.WHITE +
                        (player.getName() != null ? player.getName() : "Unknown") +
                        ChatColor.GRAY + " (" + uuid + ")");
            } catch (IllegalArgumentException e) {
                sender.sendMessage(ChatColor.YELLOW + "• " + ChatColor.WHITE + entry +
                        ChatColor.GRAY + " (Name)");
            }
        }
        sender.sendMessage(ChatColor.GOLD + "════════════════════════════");
    }

    private void handleWhitelistClear(CommandSender sender) {
        service.clearWhitelist();
        sender.sendMessage(ChatColor.GREEN + "✓ Cleared maintenance whitelist");
    }

    private void handleTimer(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Usage: /maintenance clock <set|add|remove|status>");
            return;
        }

        String action = args[0].toLowerCase();

        switch (action) {
            case "set":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /maintenance clock set <duration>");
                    sender.sendMessage(ChatColor.GRAY + "Example: /maintenance clock set 1d 2h 30m");
                    return;
                }
                handleTimerSet(sender, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
                break;

            case "add":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /maintenance clock add <duration>");
                    sender.sendMessage(ChatColor.GRAY + "Example: /maintenance clock add 1h 30m");
                    return;
                }
                handleTimerAdd(sender, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
                break;

            case "remove":
                handleTimerRemove(sender);
                break;

            case "status":
                handleTimerStatus(sender);
                break;

            default:
                sender.sendMessage(ChatColor.RED + "Usage: /maintenance clock <set|add|remove|status>");
                break;
        }
    }

    private void handleTimerSet(CommandSender sender, String duration) {
        try {
            long millis = MaintenanceService.parseDuration(duration);
            service.setTimer(millis);

            sender.sendMessage(ChatColor.GREEN + "✓ Maintenance timer set to " +
                    service.getFormattedTimeRemaining());

            if (!service.isEnabled()) {
                sender.sendMessage(ChatColor.YELLOW + "Note: Maintenance mode is currently disabled. " +
                        "Enable it with /maintenance on");
            }
        } catch (IllegalArgumentException e) {
            sender.sendMessage(ChatColor.RED + "Invalid duration format: " + e.getMessage());
            sender.sendMessage(ChatColor.GRAY + "Example: 1d 2h 30m (days, hours, minutes, seconds)");
        }
    }

    private void handleTimerAdd(CommandSender sender, String duration) {
        try {
            long millis = MaintenanceService.parseDuration(duration);
            service.addTime(millis);

            sender.sendMessage(ChatColor.GREEN + "✓ Added " +
                    MaintenanceService.formatDuration(millis) + " to maintenance timer");
            sender.sendMessage(ChatColor.YELLOW + "New end time: " +
                    service.getFormattedTimeRemaining() + " remaining");
        } catch (IllegalArgumentException e) {
            sender.sendMessage(ChatColor.RED + "Invalid duration format: " + e.getMessage());
            sender.sendMessage(ChatColor.GRAY + "Example: 1h 30m (hours, minutes, seconds)");
        }
    }

    private void handleTimerRemove(CommandSender sender) {
        service.removeTimer();
        sender.sendMessage(ChatColor.GREEN + "✓ Maintenance timer removed");
    }

    private void handleTimerStatus(CommandSender sender) {
        if (!config.isUseTimer() || config.getRemainingTime() < 0) {
            sender.sendMessage(ChatColor.YELLOW + "No maintenance timer is set.");
            return;
        }

        sender.sendMessage(ChatColor.GOLD + "═══ Timer Status ═══");
        sender.sendMessage(ChatColor.YELLOW + "Time remaining: " + ChatColor.WHITE +
                service.getFormattedTimeRemaining());
        sender.sendMessage(ChatColor.YELLOW + "Show in MOTD: " + ChatColor.WHITE +
                (config.isShowTimerInMotd() ? "Yes" : "No"));
        sender.sendMessage(ChatColor.GOLD + "══════════════════════");
    }

    private void handleMotd(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Usage: /maintenance motd <line1|line2> <message>");
            return;
        }

        String line = args[0].toLowerCase();

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /maintenance motd <line1|line2> <message>");
            return;
        }

        String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

        switch (line) {
            case "line1":
            case "1":
                config.setMotdLine1(message);
                sender.sendMessage(ChatColor.GREEN + "✓ MOTD line 1 updated to: " +
                        ChatColor.translateAlternateColorCodes('&', message));
                break;

            case "line2":
            case "2":
                config.setMotdLine2(message);
                sender.sendMessage(ChatColor.GREEN + "✓ MOTD line 2 updated to: " +
                        ChatColor.translateAlternateColorCodes('&', message));
                break;

            default:
                sender.sendMessage(ChatColor.RED + "Invalid line. Use 'line1' or 'line2'");
                break;
        }
    }

    private void handleReload(CommandSender sender) {
        config.reload();
        sender.sendMessage(ChatColor.GREEN + "✓ Maintenance configuration reloaded!");
    }

    private void handleServerList(CommandSender sender, String[] args) {
        if (args.length == 0) {
            // Show current settings
            sender.sendMessage(ChatColor.GOLD + "═══ Server List Display ═══");
            sender.sendMessage(ChatColor.YELLOW + "Show as full: " + ChatColor.WHITE +
                    (config.isShowServerAsFull() ? "Yes" : "No"));
            sender.sendMessage(ChatColor.YELLOW + "Hide player count: " + ChatColor.WHITE +
                    (config.isHidePlayerCount() ? "Yes" : "No"));
            sender.sendMessage(ChatColor.GOLD + "═══════════════════════════");
            return;
        }

        String option = args[0].toLowerCase();

        switch (option) {
            case "full":
            case "showfull":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /maintenance serverlist full <true|false>");
                    return;
                }
                handleServerListFull(sender, args[1]);
                break;

            case "hide":
            case "hidecount":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /maintenance serverlist hide <true|false>");
                    return;
                }
                handleServerListHide(sender, args[1]);
                break;

            default:
                sender.sendMessage(ChatColor.RED + "Usage: /maintenance serverlist <full|hide> <true|false>");
                break;
        }
    }

    private void handleServerListFull(CommandSender sender, String value) {
        boolean enable = value.equalsIgnoreCase("true") || value.equalsIgnoreCase("on") ||
                value.equalsIgnoreCase("yes") || value.equalsIgnoreCase("1");

        config.setShowServerAsFull(enable);
        sender.sendMessage(ChatColor.GREEN + "✓ Show server as full: " +
                (enable ? ChatColor.GREEN + "ENABLED" : ChatColor.RED + "DISABLED"));
    }

    private void handleServerListHide(CommandSender sender, String value) {
        boolean enable = value.equalsIgnoreCase("true") || value.equalsIgnoreCase("on") ||
                value.equalsIgnoreCase("yes") || value.equalsIgnoreCase("1");

        config.setHidePlayerCount(enable);
        sender.sendMessage(ChatColor.GREEN + "✓ Hide player count: " +
                (enable ? ChatColor.GREEN + "ENABLED" : ChatColor.RED + "DISABLED"));

        if (enable) {
            sender.sendMessage(ChatColor.GRAY + "Note: Modern clients will show '???' for player count");
        }
    }


    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "═══ Maintenance Commands ═══");
        sender.sendMessage(ChatColor.YELLOW + "/maintenance on" + ChatColor.GRAY + " - Enable maintenance");
        sender.sendMessage(ChatColor.YELLOW + "/maintenance off" + ChatColor.GRAY + " - Disable maintenance");
        sender.sendMessage(ChatColor.YELLOW + "/maintenance toggle" + ChatColor.GRAY + " - Toggle maintenance");
        sender.sendMessage(ChatColor.YELLOW + "/maintenance status" + ChatColor.GRAY + " - View status");
        sender.sendMessage(ChatColor.YELLOW + "/maintenance whitelist add <player>" + ChatColor.GRAY + " - Add to whitelist");
        sender.sendMessage(ChatColor.YELLOW + "/maintenance whitelist remove <player>" + ChatColor.GRAY + " - Remove from whitelist");
        sender.sendMessage(ChatColor.YELLOW + "/maintenance whitelist list" + ChatColor.GRAY + " - View whitelist");
        sender.sendMessage(ChatColor.YELLOW + "/maintenance whitelist clear" + ChatColor.GRAY + " - Clear whitelist");
        sender.sendMessage(ChatColor.YELLOW + "/maintenance clock set <duration>" + ChatColor.GRAY + " - Set timer");
        sender.sendMessage(ChatColor.YELLOW + "/maintenance clock add <duration>" + ChatColor.GRAY + " - Add time");
        sender.sendMessage(ChatColor.YELLOW + "/maintenance clock remove" + ChatColor.GRAY + " - Remove timer");
        sender.sendMessage(ChatColor.YELLOW + "/maintenance clock status" + ChatColor.GRAY + " - Timer status");
        sender.sendMessage(ChatColor.YELLOW + "/maintenance motd <line1|line2> <msg>" + ChatColor.GRAY + " - Set MOTD");
        sender.sendMessage(ChatColor.YELLOW + "/maintenance serverlist" + ChatColor.GRAY + " - Server list settings");
        sender.sendMessage(ChatColor.YELLOW + "/maintenance reload" + ChatColor.GRAY + " - Reload config");
        sender.sendMessage(ChatColor.GOLD + "═══════════════════════════");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("oreo.maintenance.admin")) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            return Arrays.asList("on", "off", "toggle", "status", "whitelist", "clock", "timer", "motd", "serverlist", "reload")
                    .stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("whitelist")) {
                return Arrays.asList("add", "remove", "list", "clear")
                        .stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }

            if (args[0].equalsIgnoreCase("clock") || args[0].equalsIgnoreCase("timer")) {
                return Arrays.asList("set", "add", "remove", "status")
                        .stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }

            if (args[0].equalsIgnoreCase("motd")) {
                return Arrays.asList("line1", "line2")
                        .stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }

            if (args[0].equalsIgnoreCase("serverlist") || args[0].equalsIgnoreCase("display")) {
                return Arrays.asList("full", "hide")
                        .stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("whitelist") &&
                    (args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("remove"))) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            }

            if ((args[0].equalsIgnoreCase("serverlist") || args[0].equalsIgnoreCase("display")) &&
                    (args[1].equalsIgnoreCase("full") || args[1].equalsIgnoreCase("hide"))) {
                return Arrays.asList("true", "false")
                        .stream()
                        .filter(s -> s.startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        return new ArrayList<>();
    }
}