package fr.elias.oreoEssentials.modules.currency.commands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.modules.currency.Currency;
import fr.elias.oreoEssentials.modules.currency.CurrencyBalance;
import fr.elias.oreoEssentials.playerdirectory.PlayerDirectory;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Command to view top currency balances
 * Usage: /currencytop <currency> [page]
 */
public class CurrencyTopCommand implements OreoCommand {

    private final OreoEssentials plugin;

    public CurrencyTopCommand(OreoEssentials plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "currencytop";
    }

    @Override
    public List<String> aliases() {
        return Arrays.asList("ctop", "currencyleaderboard", "cbalancetop");
    }

    @Override
    public String permission() {
        return "oreo.currency.top";
    }

    @Override
    public String usage() {
        return "<currency> [page]";
    }

    @Override
    public boolean playerOnly() {
        return false;
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§cUsage: /currencytop <currency> [page]");
            sender.sendMessage("§7Example: §e/ctop gems 1");
            return true;
        }

        String currencyId = args[0].toLowerCase(Locale.ROOT);
        Currency currency = plugin.getCurrencyService().getCurrency(currencyId);

        if (currency == null) {
            sender.sendMessage("§c✖ Currency not found: " + currencyId);
            sender.sendMessage("§7Use §e/oecurrency list §7to see all currencies");
            return true;
        }

        final int requestedPage;
        if (args.length >= 2) {
            try {
                requestedPage = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                sender.sendMessage("§c✖ Invalid page number: " + args[1]);
                return true;
            }
        } else {
            requestedPage = 1;
        }

        final int limit = 10;

        plugin.getCurrencyService().getTopBalances(currencyId, 100).thenAccept(balances -> {
            if (balances.isEmpty()) {
                sender.sendMessage("§c✖ No balances found for " + currency.getName());
                return;
            }

            int totalPages = (int) Math.ceil(balances.size() / (double) limit);

            int page = Math.max(1, Math.min(requestedPage, totalPages));
            int offset = (page - 1) * limit;

            int start = offset;
            int end = Math.min(start + limit, balances.size());

            sender.sendMessage("§6§l════════════════════════════════");
            sender.sendMessage("§6§l    " + currency.getName() + " Top Balances");
            sender.sendMessage("§7Page " + page + " of " + totalPages);
            sender.sendMessage("§6§l════════════════════════════════");

            PlayerDirectory dir = null;
            try {
                dir = plugin.getPlayerDirectory();
            } catch (Throwable ignored) {}

            for (int i = start; i < end; i++) {
                CurrencyBalance balance = balances.get(i);
                UUID uuid = balance.getPlayerId();

                String playerName = resolvePlayerName(dir, uuid);

                int rank = i + 1;
                String rankStr = getRankColor(rank) + "#" + rank;

                sender.sendMessage(rankStr + " §f" + playerName + " §8- §a" +
                        currency.format(balance.getBalance()));
            }

            sender.sendMessage("§6§l════════════════════════════════");

            if (page < totalPages) {
                sender.sendMessage("§7Next page: §e/ctop " + currencyId + " " + (page + 1));
            }
        });

        return true;
    }

    private String resolvePlayerName(PlayerDirectory dir, UUID uuid) {
        if (uuid == null) return "Unknown";

        if (dir != null) {
            try {
                String name = dir.lookupNameByUuid(uuid);
                if (name != null && !name.isBlank()) return name;
            } catch (Throwable ignored) {}
        }

        try {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
            if (offlinePlayer.getName() != null && !offlinePlayer.getName().isBlank()) {
                return offlinePlayer.getName();
            }
        } catch (Throwable ignored) {}

        String raw = uuid.toString();
        return "Unknown (" + raw.substring(0, 8) + ")";
    }

    private String getRankColor(int rank) {
        return switch (rank) {
            case 1 -> "§6";
            case 2 -> "§7";
            case 3 -> "§c";
            default -> "§e";
        };
    }
}
