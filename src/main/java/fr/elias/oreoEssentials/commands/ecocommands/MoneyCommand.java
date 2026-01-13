package fr.elias.oreoEssentials.commands.ecocommands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand; // NEW
import java.util.List;                                // NEW

import fr.elias.oreoEssentials.util.Async;
import fr.elias.oreoEssentials.util.Lang;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class MoneyCommand implements CommandExecutor, OreoCommand { // UPDATED

    private final OreoEssentials plugin;
    private final Economy vault;

    public MoneyCommand(OreoEssentials plugin) {
        this.plugin = plugin;
        Economy found = null;
        try {
            var rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp != null) found = rsp.getProvider();
        } catch (Throwable ignored) {}
        this.vault = found;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (args.length == 0) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(Lang.msg("economy.money.usage.view", null));
                return true;
            }
            showSelfBalance(p);
            return true;
        }

        if (args.length == 1) {
            showOtherBalance(sender, args[0]);
            return true;
        }

        if (args.length >= 3) {
            final String sub = args[0].toLowerCase(Locale.ROOT);
            if (!sub.equals("give") && !sub.equals("take") && !sub.equals("set")) {
                sender.sendMessage(Lang.msg("economy.money.usage.admin", sender instanceof Player ? (Player) sender : null));
                return true;
            }

            if (!sender.hasPermission("oreo.money." + sub)) {
                sender.sendMessage(Lang.msg("economy.errors.no-permission", sender instanceof Player ? (Player) sender : null));
                return true;
            }

            final String targetName = args[1];
            final Double amount = parsePositiveAmount(args[2]);
            if (amount == null) {
                sender.sendMessage(Lang.msg("economy.errors.not-a-number", sender instanceof Player ? (Player) sender : null));
                return true;
            }
            if (amount <= 0 && !sub.equals("set")) {
                sender.sendMessage(Lang.msg("economy.errors.amount-not-positive", sender instanceof Player ? (Player) sender : null));
                return true;
            }

            if (plugin.getDatabase() != null) {
                Async.run(() -> {
                    UUID targetId = plugin.getOfflinePlayerCache().getId(targetName);
                    if (targetId == null) {
                        sendSync(sender, Lang.msg("economy.errors.player-not-found",
                                Map.of("target", targetName), sender instanceof Player ? (Player) sender : null));
                        return;
                    }

                    switch (sub) {
                        case "give" -> plugin.getDatabase().giveBalance(targetId, targetName, amount);
                        case "take" -> plugin.getDatabase().takeBalance(targetId, targetName, amount);
                        case "set"  -> plugin.getDatabase().setBalance(targetId, targetName, amount);
                    }

                    double newBal = plugin.getDatabase().getBalance(targetId);

                    Map<String, String> sVars = new HashMap<>();
                    sVars.put("target", targetName);
                    sVars.put("amount_formatted", fmt(amount));
                    sVars.put("currency_symbol", currencySymbol());
                    String senderPath = switch (sub) {
                        case "give" -> "economy.money.give.sender";
                        case "take" -> "economy.money.take.sender";
                        default -> "economy.money.set.sender";
                    };
                    sendSync(sender, Lang.msg(senderPath, sVars, sender instanceof Player ? (Player) sender : null));

                    var p = Bukkit.getPlayer(targetId);
                    if (p != null) {
                        Map<String, String> rVars = new HashMap<>();
                        rVars.put("amount_formatted", fmt(amount));
                        rVars.put("balance_formatted", fmt(newBal));
                        rVars.put("currency_symbol", currencySymbol());
                        String receiverPath = switch (sub) {
                            case "give" -> "economy.money.give.receiver";
                            case "take" -> "economy.money.take.receiver";
                            default -> "economy.money.set.receiver";
                        };
                        sendSync(p, Lang.msg(receiverPath, rVars, p));
                    }
                });
                return true;
            }


            if (vault == null) {
                sender.sendMessage(Lang.msg("economy.errors.no-economy", sender instanceof Player ? (Player) sender : null));
                return true;
            }
            OfflinePlayer off = Bukkit.getOfflinePlayer(targetName);
            switch (sub) {
                case "give" -> vault.depositPlayer(off, amount);
                case "take" -> vault.withdrawPlayer(off, amount);
                case "set" -> {
                    double cur = safeVaultBalance(off);
                    double diff = amount - cur;
                    if (Math.abs(diff) > 1e-9) {
                        if (diff > 0) vault.depositPlayer(off, diff);
                        else vault.withdrawPlayer(off, -diff);
                    }
                }
            }

            Map<String, String> sVars = new HashMap<>();
            sVars.put("target", targetName);
            sVars.put("amount_formatted", fmt(amount));
            sVars.put("currency_symbol", currencySymbol());
            String senderPath = switch (sub) {
                case "give" -> "economy.money.give.sender";
                case "take" -> "economy.money.take.sender";
                default -> "economy.money.set.sender";
            };
            sender.sendMessage(Lang.msg(senderPath, sVars, sender instanceof Player ? (Player) sender : null));

            // receiver (if online)
            if (off.isOnline() && off.getPlayer() != null) {
                Map<String, String> rVars = new HashMap<>();
                rVars.put("amount_formatted", fmt(amount));
                rVars.put("balance_formatted", fmt(safeVaultBalance(off)));
                rVars.put("currency_symbol", currencySymbol());
                String receiverPath = switch (sub) {
                    case "give" -> "economy.money.give.receiver";
                    case "take" -> "economy.money.take.receiver";
                    default -> "economy.money.set.receiver";
                };
                off.getPlayer().sendMessage(Lang.msg(receiverPath, rVars, off.getPlayer()));
            }
            return true;
        }

        sender.sendMessage(Lang.msg("economy.money.usage.view", sender instanceof Player ? (Player) sender : null));
        return true;
    }


    private void showSelfBalance(Player player) {
        if (plugin.getDatabase() != null) {
            Async.run(() -> {
                double bal = plugin.getDatabase().getBalance(player.getUniqueId());
                Map<String, String> vars = new HashMap<>();
                vars.put("balance_formatted", fmt(bal));
                vars.put("currency_symbol", currencySymbol());
                sendSync(player, Lang.msg("economy.money.view-self", vars, player));
            });
            return;
        }
        if (vault != null) {
            Map<String, String> vars = new HashMap<>();
            vars.put("balance_formatted", fmt(safeVaultBalance(player)));
            vars.put("currency_symbol", currencySymbol());
            player.sendMessage(Lang.msg("economy.money.view-self", vars, player));
            return;
        }
        player.sendMessage(Lang.msg("economy.errors.no-economy", player));
    }

    private void showOtherBalance(CommandSender sender, String targetName) {
        if (plugin.getDatabase() != null) {
            Async.run(() -> {
                UUID id = plugin.getOfflinePlayerCache().getId(targetName);
                if (id == null) {
                    sendSync(sender, Lang.msg("economy.errors.player-not-found",
                            Map.of("target", targetName), sender instanceof Player ? (Player) sender : null));
                    return;
                }
                double bal = plugin.getDatabase().getBalance(id);
                Map<String, String> vars = new HashMap<>();
                vars.put("target", targetName);
                vars.put("balance_formatted", fmt(bal));
                vars.put("currency_symbol", currencySymbol());
                sendSync(sender, Lang.msg("economy.money.view-other", vars, sender instanceof Player ? (Player) sender : null));
            });
            return;
        }
        if (vault != null) {
            OfflinePlayer off = Bukkit.getOfflinePlayer(targetName);
            Map<String, String> vars = new HashMap<>();
            vars.put("target", targetName);
            vars.put("balance_formatted", fmt(safeVaultBalance(off)));
            vars.put("currency_symbol", currencySymbol());
            sender.sendMessage(Lang.msg("economy.money.view-other", vars, sender instanceof Player ? (Player) sender : null));
            return;
        }
        sender.sendMessage(Lang.msg("economy.errors.no-economy", sender instanceof Player ? (Player) sender : null));
    }

    private double safeVaultBalance(OfflinePlayer player) {
        try { return vault.getBalance(player); }
        catch (Throwable ignored) { return 0.0; }
    }

    private void sendSync(CommandSender who, String text) {
        if (Bukkit.isPrimaryThread()) who.sendMessage(text);
        else Bukkit.getScheduler().runTask(plugin, () -> who.sendMessage(text));
    }


    private String fmt(double v) {
        int decimals = (int) Math.round(Lang.getDouble("economy.format.decimals", 2.0));
        String th = Lang.get("economy.format.thousands-separator", ",");
        String dec = Lang.get("economy.format.decimal-separator", ".");
        String pattern = "#,##0" + (decimals > 0 ? "." + "0".repeat(decimals) : "");
        DecimalFormatSymbols sym = new DecimalFormatSymbols(Locale.US);
        if (!th.isEmpty()) sym.setGroupingSeparator(th.charAt(0));
        if (!dec.isEmpty()) sym.setDecimalSeparator(dec.charAt(0));
        java.text.DecimalFormat df = new java.text.DecimalFormat(pattern, sym);
        df.setGroupingUsed(!th.isEmpty());
        return df.format(v);
    }

    private String currencySymbol() {
        return Lang.get("economy.currency.symbol", "$");
    }

    private Double parsePositiveAmount(String s) {
        try {
            String raw = s.replace(",", "").trim();
            if (raw.startsWith("-") || raw.toLowerCase(Locale.ROOT).contains("e")) return null;
            return Double.parseDouble(raw);
        } catch (Exception e) { return null; }
    }


    @Override
    public String name() {
        // Command label handled by CommandManager -> /money
        return "money";
    }

    @Override
    public List<String> aliases() {
        return List.of("bal");
    }

    @Override
    public String permission() {
        return "oreo.money";
    }

    @Override
    public String usage() {
        return "[player|give|take|set]";
    }

    @Override
    public boolean playerOnly() {
        return false;
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        return onCommand(sender, null, label, args);
    }
}


