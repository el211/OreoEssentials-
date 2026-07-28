package fr.elias.oreoEssentials.modules.economy.ecocommands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.util.Async;
import fr.elias.oreoEssentials.util.OreScheduler;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;
import java.util.List;

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
        Economy eco = resolveVaultEconomy();
        if (eco == null) {
            sender.sendMessage(ChatColor.RED + "Economy is not available.");
            return true;
        }

        if (args.length == 0) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(ChatColor.RED + "Usage: /" + label + " <player>");
                return true;
            }
            Async.run(() -> {
                double bal = eco.getBalance(p);
                OreScheduler.runForEntity(plugin, p,
                        () -> p.sendMessage(ChatColor.GOLD + "Your balance: " + ChatColor.AQUA + format(bal)));
            });
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

        final OfflinePlayer target = op;
        final String displayName = op.getName() == null ? targetName : op.getName();
        Async.run(() -> {
            double bal = eco.getBalance(target);
            OreScheduler.run(plugin, () ->
                    sender.sendMessage(ChatColor.GOLD + displayName
                            + ChatColor.GRAY + "'s balance: " + ChatColor.AQUA + format(bal)));
        });
        return true;
    }

    private Economy resolveVaultEconomy() {
        Economy eco = plugin.getVaultEconomy();
        if (eco != null) return eco;

        try {
            var rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
            return rsp == null ? null : rsp.getProvider();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String format(double amount) {
        DecimalFormat df = new DecimalFormat("#,##0.00");
        return "$" + df.format(amount);
    }
}
