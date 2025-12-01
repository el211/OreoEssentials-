package fr.elias.oreoEssentials.commands.core.moderation;

import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.services.GodService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class GodCommand implements OreoCommand {
    private final GodService god;

    public GodCommand(GodService god) {
        this.god = god;
    }

    @Override public String name() { return "god"; }
    @Override public List<String> aliases() { return List.of(".god"); }
    @Override public String permission() { return "oreo.god"; }
    @Override public String usage() { return "[player]"; }
    @Override public boolean playerOnly() { return false; } // allow console for others

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        // /god -> self (must be a player)
        if (args.length == 0) {
            if (!(sender instanceof Player p)) {



                sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " <player>");
                return true;
            }
            boolean enabled = toggleAndApply(p, p.getUniqueId());
            p.sendMessage(enabled
                    ? ChatColor.GREEN + "God mode " + ChatColor.AQUA + "enabled" + ChatColor.GREEN + ". You are now unbeatable."
                    : ChatColor.RED + "God mode " + ChatColor.AQUA + "disabled" + ChatColor.RED + ".");
            return true;
        }

        // /god <player> -> others
        if (!sender.hasPermission("oreo.god.others")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to toggle god for others.");
            return true;
        }

        Player target = findOnlinePlayer(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found or not online: " + ChatColor.YELLOW + args[0]);
            return true;
        }

        boolean enabled = toggleAndApply(target, target.getUniqueId());
        target.sendMessage(enabled
                ? ChatColor.GREEN + "An admin enabled your god mode."
                : ChatColor.RED + "An admin disabled your god mode.");
        sender.sendMessage(enabled
                ? ChatColor.GREEN + "Enabled god mode for " + ChatColor.AQUA + target.getName() + ChatColor.GREEN + "."
                : ChatColor.RED + "Disabled god mode for " + ChatColor.AQUA + target.getName() + ChatColor.RED + ".");
        return true;
    }

    private boolean toggleAndApply(Player target, UUID uuid) {
        boolean enabled = god.toggle(uuid);
        try { target.setInvulnerable(enabled); } catch (Throwable ignored) {}

        if (enabled) {
            target.setHealth(Math.min(target.getMaxHealth(), target.getMaxHealth()));
            target.setFoodLevel(20);
            target.setSaturation(20f);
            target.setFireTicks(0);
        }
        return enabled;
    }

    private Player findOnlinePlayer(String input) {
        // exact first
        Player p = Bukkit.getPlayerExact(input);
        if (p != null) return p;

        // case-insensitive partial fallback
        String lower = input.toLowerCase(Locale.ROOT);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getName().toLowerCase(Locale.ROOT).startsWith(lower)) {
                return online;
            }
        }
        return null;
    }
}
