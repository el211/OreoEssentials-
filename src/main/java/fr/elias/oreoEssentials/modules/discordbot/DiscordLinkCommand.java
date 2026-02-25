package fr.elias.oreoEssentials.modules.discordbot;

import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.List;

public class DiscordLinkCommand implements CommandExecutor, TabCompleter {

    private final DiscordLinkManager linkManager;

    public DiscordLinkCommand(DiscordLinkManager linkManager) {
        this.linkManager = linkManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used in-game.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage("§6Discord Linking:");
            player.sendMessage("§e/discord link <code> §7- Link your Discord account");
            player.sendMessage("§e/discord status §7- Check your link status");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "link" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /discord link <code>");
                    player.sendMessage("§7Get your code by running §e/mc link §7in Discord.");
                    return true;
                }
                String result = linkManager.confirmLink(player, args[1]);
                player.sendMessage(result);
            }
            case "status" -> {
                if (linkManager.isLinked(player.getUniqueId())) {
                    String discordId = linkManager.getLinkedDiscordId(player.getUniqueId());
                    player.sendMessage("§a✔ Your account is linked. Discord ID: §e" + discordId);
                } else {
                    player.sendMessage("§7Your account is §cnot linked§7 to Discord.");
                    player.sendMessage("§7Use §e/mc link §7in Discord to get a code, then run §e/discord link <code>§7.");
                }
            }
            default -> player.sendMessage("§cUnknown subcommand. Use: link, status");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("link", "status").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}