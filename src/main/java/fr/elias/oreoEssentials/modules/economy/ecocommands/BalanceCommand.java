package fr.elias.oreoEssentials.modules.economy.ecocommands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.modules.economy.EconomyService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;
import java.util.List;
import java.util.UUID;

public class BalanceCommand implements OreoCommand {
    private final OreoEssentials plugin;

    public BalanceCommand(OreoEssentials plugin) {
        this.plugin = plugin;
    }

    @Override public String name() { return "balance"; }
    @Override public List<String> aliases() { return List.of("bal"); }
    @Override public String permission() { return "oreo.balance"; }
    @Override public String usage() { return "[player]"; }
    @Override public boolean playerOnly() { return false; }

    @Override
    @SuppressWarnings("deprecation")
    public boolean execute(CommandSender sender, String label, String[] args) {
        EconomyService eco;
        try {
            eco = plugin.getEcoBootstrap().api();
        } catch (Throwable t) {
            sender.sendMessage(ChatColor.RED + "Economy is not available.");
            return true;
        }

        if (args.length == 0) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(ChatColor.RED + "Usage: /" + label + " <player>");
                return true;
            }
            double bal = eco.getBalance(p.getUniqueId());
            sender.sendMessage(ChatColor.GOLD + "Your balance: " + ChatColor.AQUA + format(bal));
            return true;
        }

        if (!sender.hasPermission("oreo.balance.others")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to view other players' balances.");
            return true;
        }

        String targetName = args[0];
        OfflinePlayer op = Bukkit.getOfflinePlayer(targetName);
        if (op == null || (op.getName() == null && !op.hasPlayedBefore())) {
            sender.sendMessage(ChatColor.RED + "Unknown player.");
            return true;
        }

        double bal = eco.getBalance(op.getUniqueId());
        sender.sendMessage(ChatColor.GOLD + (op.getName() == null ? targetName : op.getName())
                + ChatColor.GRAY + "'s balance: " + ChatColor.AQUA + format(bal));
        return true;
    }

    private String format(double amount) {
        DecimalFormat df = new DecimalFormat("#,##0.00");
        return "$" + df.format(amount);
    }
}