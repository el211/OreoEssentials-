package fr.elias.oreoEssentials.modules.tempfly;

import fr.elias.oreoEssentials.commands.OreoCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class TempFlyCommand implements OreoCommand, TabCompleter {

    private final TempFlyService tempFlyService;

    public TempFlyCommand(TempFlyService tempFlyService) {
        this.tempFlyService = tempFlyService;
    }

    @Override
    public String name() {
        return "tempfly";
    }

    @Override
    public List<String> aliases() {
        return List.of("tfly", "temporaryflight");
    }

    @Override
    public String permission() {
        return "oe.tempfly.use";
    }

    @Override
    public String usage() {
        return "[on|off|time]";
    }

    @Override
    public boolean playerOnly() {
        return true;
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;

        if (args.length == 0) {
            tempFlyService.enableFly(player);
            return true;
        }

        String action = args[0].toLowerCase();

        switch (action) {
            case "on":
            case "enable":
                tempFlyService.enableFly(player);
                break;

            case "off":
            case "disable":
                tempFlyService.disableFly(player);
                break;

            case "time":
            case "remaining":
                int remaining = tempFlyService.getTimeRemaining(player);
                if (remaining > 0) {
                    player.sendMessage("§aTime remaining: §e" + formatTime(remaining));
                } else {
                    player.sendMessage("§cYou don't have temporary fly active.");
                }
                break;

            default:
                player.sendMessage("§cUsage: /" + label + " [on|off|time]");
                break;
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, org.bukkit.command.Command cmd, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return List.of();
        }

        if (args.length == 1) {
            String partial = args[0].toLowerCase(Locale.ROOT);
            List<String> options = new ArrayList<>();
            options.add("on");
            options.add("enable");
            options.add("off");
            options.add("disable");
            options.add("time");
            options.add("remaining");

            return options.stream()
                    .filter(opt -> opt.startsWith(partial))
                    .sorted()
                    .collect(Collectors.toList());
        }

        return List.of();
    }

    private String formatTime(int seconds) {
        if (seconds < 60) {
            return seconds + "s";
        } else if (seconds < 3600) {
            int minutes = seconds / 60;
            int secs = seconds % 60;
            return secs > 0 ? minutes + "m " + secs + "s" : minutes + "m";
        } else {
            int hours = seconds / 3600;
            int minutes = (seconds % 3600) / 60;
            return minutes > 0 ? hours + "h " + minutes + "m" : hours + "h";
        }
    }
}