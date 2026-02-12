package fr.elias.oreoEssentials.modules.economy.ecocommands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.modules.economy.EconomyService;
import fr.elias.oreoEssentials.util.Async;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;

public class MoneyCommand implements CommandExecutor, OreoCommand {

    private final OreoEssentials plugin;

    public MoneyCommand(OreoEssentials plugin) {
        this.plugin = plugin;
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

            // Use EconomyService
            EconomyService eco;
            try {
                eco = plugin.getEcoBootstrap().api();
            } catch (Throwable t) {
                sender.sendMessage(Lang.msg("economy.errors.no-economy", sender instanceof Player ? (Player) sender : null));
                return true;
            }

            Async.run(() -> {
                UUID targetId = plugin.getOfflinePlayerCache().getId(targetName);
                if (targetId == null) {
                    OfflinePlayer op = Bukkit.getOfflinePlayer(targetName);
                    if (op != null && op.hasPlayedBefore()) {
                        targetId = op.getUniqueId();
                    }
                }

                if (targetId == null) {
                    sendSync(sender, Lang.msg("economy.errors.player-not-found",
                            Map.of("target", targetName), sender instanceof Player ? (Player) sender : null));
                    return;
                }

                final UUID finalTargetId = targetId;

                switch (sub) {
                    case "give" -> {
                        if (!eco.deposit(finalTargetId, amount)) {
                            sendSync(sender, Lang.msg("economy.errors.no-economy", sender instanceof Player ? (Player) sender : null));
                            return;
                        }
                    }
                    case "take" -> {
                        if (!eco.withdraw(finalTargetId, amount)) {
                            sendSync(sender, Lang.msg("economy.money.take.insufficient",
                                    Map.of("target", targetName), sender instanceof Player ? (Player) sender : null));
                            return;
                        }
                    }
                    case "set" -> {
                        double current = eco.getBalance(finalTargetId);
                        double diff = amount - current;
                        if (diff > 0) {
                            eco.deposit(finalTargetId, diff);
                        } else if (diff < 0) {
                            eco.withdraw(finalTargetId, -diff);
                        }
                    }
                }

                double newBal = eco.getBalance(finalTargetId);

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

                Map<String, String> rVars = new HashMap<>();
                rVars.put("amount_formatted", fmt(amount));
                rVars.put("balance_formatted", fmt(newBal));
                rVars.put("currency_symbol", currencySymbol());
                String receiverPath = switch (sub) {
                    case "give" -> "economy.money.give.receiver";
                    case "take" -> "economy.money.take.receiver";
                    default -> "economy.money.set.receiver";
                };

                String receiverMsg = Lang.msg(receiverPath, rVars, null);
                notifyReceiverCrossServer(finalTargetId, receiverMsg);
            });
            return true;
        }

        sender.sendMessage(Lang.msg("economy.money.usage.view", sender instanceof Player ? (Player) sender : null));
        return true;
    }

    private void notifyReceiverCrossServer(UUID targetUuid, String message) {
        if (targetUuid == null || message == null || message.isBlank()) return;

        Player local = Bukkit.getPlayer(targetUuid);
        if (local != null) {
            sendSync(local, message);
            return;
        }

        try {
            var pm = plugin.getPacketManager();
            var dir = plugin.getPlayerDirectory();
            if (pm == null || !pm.isInitialized() || dir == null) return;

            String server = dir.lookupCurrentServer(targetUuid);
            if (server == null || server.isBlank()) return;

            pm.sendPacket(
                    fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel.individual(server),
                    new fr.elias.oreoEssentials.rabbitmq.packet.impl.SendRemoteMessagePacket(targetUuid, message)
            );

            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info("[MONEY][DBG] Remote notify -> uuid=" + targetUuid + " server=" + server);
            }
        } catch (Throwable t) {
            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().warning("[MONEY][DBG] Remote notify failed: " + t.getMessage());
            }
        }
    }

    private void showSelfBalance(Player player) {
        EconomyService eco;
        try {
            eco = plugin.getEcoBootstrap().api();
        } catch (Throwable t) {
            player.sendMessage(Lang.msg("economy.errors.no-economy", player));
            return;
        }

        Async.run(() -> {
            double bal = eco.getBalance(player.getUniqueId());
            Map<String, String> vars = new HashMap<>();
            vars.put("balance_formatted", fmt(bal));
            vars.put("currency_symbol", currencySymbol());
            sendSync(player, Lang.msg("economy.money.view-self", vars, player));
        });
    }

    private void showOtherBalance(CommandSender sender, String targetName) {
        EconomyService eco;
        try {
            eco = plugin.getEcoBootstrap().api();
        } catch (Throwable t) {
            sender.sendMessage(Lang.msg("economy.errors.no-economy", sender instanceof Player ? (Player) sender : null));
            return;
        }

        Async.run(() -> {
            UUID id = plugin.getOfflinePlayerCache().getId(targetName);
            if (id == null) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(targetName);
                if (op != null && op.hasPlayedBefore()) {
                    id = op.getUniqueId();
                }
            }

            if (id == null) {
                sendSync(sender, Lang.msg("economy.errors.player-not-found",
                        Map.of("target", targetName), sender instanceof Player ? (Player) sender : null));
                return;
            }

            double bal = eco.getBalance(id);
            Map<String, String> vars = new HashMap<>();
            vars.put("target", targetName);
            vars.put("balance_formatted", fmt(bal));
            vars.put("currency_symbol", currencySymbol());
            sendSync(sender, Lang.msg("economy.money.view-other", vars, sender instanceof Player ? (Player) sender : null));
        });
    }

    private void sendSync(CommandSender who, String text) {
        if (text == null) return;
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
        DecimalFormat df = new DecimalFormat(pattern, sym);
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