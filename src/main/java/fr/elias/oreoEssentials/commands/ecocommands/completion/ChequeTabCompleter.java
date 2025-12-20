package fr.elias.oreoEssentials.commands.ecocommands.completion;


import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class ChequeTabCompleter implements TabCompleter {

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (!(sender instanceof Player)) {
            return completions; // Console can't autocomplete numbers
        }

        Player player = (Player) sender;

        if (args.length == 1) {
            double balance = getPlayerBalance(player);

            //  Suggest amounts dynamically based on balance
            if (balance >= 100) completions.add("100");
            if (balance >= 500) completions.add("500");
            if (balance >= 1000) completions.add("1000");
            if (balance >= 5000) completions.add("5000");
            if (balance >= 10000) completions.add("10000");

            //  Allow any number they started typing (keeps what they wrote)
            if (!args[0].isEmpty() && args[0].matches("\\d+")) { // Potential improvement: Precompile this regex
                completions.add(args[0]);
            }
        }

        return completions;
    }

    private double getPlayerBalance(Player player) {
        // Placeholder function, replace this with actual Vault or Economy API call
        return 10000; // Example: assume they have $10,000
    }
}
