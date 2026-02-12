package fr.elias.oreoEssentials.modules.economy.ecocommands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.modules.economy.EconomyService;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.text.DecimalFormat;
import java.util.List;

public class BalTopCommand implements OreoCommand {
    private final OreoEssentials plugin;

    public BalTopCommand(OreoEssentials plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "baltop";
    }

    @Override
    public List<String> aliases() {
        return List.of("balancetop");
    }

    @Override
    public String permission() {
        return "oreo.baltop";
    }

    @Override
    public String usage() {
        return "";
    }

    @Override
    public boolean playerOnly() {
        return false;
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        int size = Math.max(1, plugin.getConfig().getInt("economy.baltop-size", 10));

        EconomyService eco;
        try {
            eco = plugin.getEcoBootstrap().api();
        } catch (Throwable t) {
            sender.sendMessage(ChatColor.RED + "Economy is not initialized.");
            return true;
        }

        // Now works with ANY EconomyService implementation
        try {
            var rows = eco.topBalances(size);

            if (rows.isEmpty()) {
                sender.sendMessage(ChatColor.YELLOW + "No balances recorded yet.");
                sender.sendMessage(ChatColor.GRAY + "This could mean:");
                sender.sendMessage(ChatColor.GRAY + "  - No players have money");
                sender.sendMessage(ChatColor.GRAY + "  - Economy data is stored elsewhere");
                sender.sendMessage(ChatColor.GRAY + "  - Check your economy configuration");
                return true;
            }

            sender.sendMessage(ChatColor.GOLD + "=== Balance Top " + size + " ===");
            DecimalFormat df = new DecimalFormat("#,##0.00");
            int rank = 1;
            for (var r : rows) {
                sender.sendMessage(ChatColor.AQUA + "" + rank + ". "
                        + ChatColor.GREEN + r.name()
                        + ChatColor.GRAY + " - "
                        + ChatColor.GOLD + df.format(r.balance()));
                rank++;
            }
            return true;

        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Error fetching baltop: " + e.getMessage());
            e.printStackTrace();
            return true;
        }
    }
}