// File: src/main/java/fr/elias/oreoEssentials/commands/ecocommands/ChequeCommand.java
package fr.elias.oreoEssentials.commands.ecocommands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.util.Async;
import fr.elias.oreoEssentials.util.Lang;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;

/**
 * Creates and redeems paper "cheques" for money.
 * Works with your Database first; falls back to Vault if present.
 *
 * Lang usage:
 * - Send strings with Lang.msg(key, vars, viewer) to get PAPI + MiniMessage/& support.
 * - For console/plain usage, pass viewer = null.
 */
public class ChequeCommand implements CommandExecutor, Listener, OreoCommand {

    private final OreoEssentials plugin;
    private final Economy vault; // may be null
    private final NamespacedKey chequeKey;

    public ChequeCommand(OreoEssentials plugin) {
        this.plugin = plugin;
        var rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        this.vault = (rsp != null ? rsp.getProvider() : null);
        this.chequeKey = new NamespacedKey(plugin, "cheque_amount");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /* ================= Bukkit CommandExecutor ================= */

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Lang.msg("economy.cheque.usage.root", null));
            return true;
        }

        // accept both new and legacy perms
        boolean canCreate = player.hasPermission("oreo.cheque.create") || player.hasPermission("rabbiteconomy.cheque.create");
        boolean canRedeem = player.hasPermission("oreo.cheque.redeem") || player.hasPermission("rabbiteconomy.cheque.redeem");

        if (args.length == 0) {
            player.sendMessage(Lang.msg("economy.cheque.usage.root", player));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("create")) {
            if (!canCreate) {
                player.sendMessage(Lang.msg("economy.errors.no-permission", player));
                return true;
            }
            if (args.length < 2) {
                player.sendMessage(Lang.msg("economy.cheque.usage.create", player));
                return true;
            }

            Double amount = parseAmount(args[1]);
            if (amount == null || amount <= 0) {
                player.sendMessage(Lang.msg("economy.cheque.create.fail-amount", player));
                return true;
            }

            // Prefer your database
            if (plugin.getDatabase() != null) {
                UUID id = player.getUniqueId();
                Async.run(() -> {
                    double bal = plugin.getDatabase().getBalance(id);
                    if (bal + 1e-9 < amount) {
                        runSync(() -> player.sendMessage(
                                Lang.msg("economy.cheque.create.fail-insufficient",
                                        Map.of("amount_formatted", fmt(amount),
                                                "currency_symbol", currencySymbol()),
                                        player)));
                        return;
                    }
                    plugin.getDatabase().setBalance(id, player.getName(), bal - amount);
                    runSync(() -> {
                        giveChequeItem(player, amount);
                        player.sendMessage(Lang.msg("economy.cheque.create.success",
                                Map.of("amount_formatted", fmt(amount),
                                        "currency_symbol", currencySymbol()), player));
                    });
                });
                return true;
            }

            // Vault fallback
            if (vault != null) {
                double bal = vault.getBalance(player);
                if (bal + 1e-9 < amount) {
                    player.sendMessage(Lang.msg("economy.cheque.create.fail-insufficient",
                            Map.of("amount_formatted", fmt(amount),
                                    "currency_symbol", currencySymbol()), player));
                    return true;
                }
                vault.withdrawPlayer(player, amount);
                giveChequeItem(player, amount);
                player.sendMessage(Lang.msg("economy.cheque.create.success",
                        Map.of("amount_formatted", fmt(amount),
                                "currency_symbol", currencySymbol()), player));
                return true;
            }

            player.sendMessage(Lang.msg("economy.errors.no-economy", player));
            return true;
        }

        if (sub.equals("redeem")) {
            if (!canRedeem) {
                player.sendMessage(Lang.msg("economy.errors.no-permission", player));
                return true;
            }
            ItemStack inHand = player.getInventory().getItemInMainHand();
            double amt = readChequeAmount(inHand);
            if (amt <= 0) {
                player.sendMessage(Lang.msg("economy.cheque.redeem.fail-none", player));
                return true;
            }
            redeemCheque(player, inHand, amt);
            return true;
        }

        player.sendMessage(Lang.msg("economy.cheque.usage.root", player));
        return true;
    }

    /* ================= Right-click redeem ================= */

    @EventHandler
    public void onUseCheque(PlayerInteractEvent e) {
        if (!e.getAction().name().contains("RIGHT_CLICK")) return;
        Player p = e.getPlayer();

        boolean canRedeem = p.hasPermission("oreo.cheque.redeem") || p.hasPermission("rabbiteconomy.cheque.redeem");
        if (!canRedeem) return;

        ItemStack item = p.getInventory().getItemInMainHand();
        double amt = readChequeAmount(item);
        if (amt <= 0) return;

        e.setCancelled(true);
        redeemCheque(p, item, amt);
    }

    /* ================= Core logic ================= */

    private void redeemCheque(Player player, ItemStack item, double amount) {
        // DB preferred
        if (plugin.getDatabase() != null) {
            Async.run(() -> {
                UUID id = player.getUniqueId();
                double bal = plugin.getDatabase().getBalance(id);
                plugin.getDatabase().setBalance(id, player.getName(), bal + amount);
                runSync(() -> {
                    removeOne(item);
                    player.sendMessage(Lang.msg("economy.cheque.redeem.success",
                            Map.of("amount_formatted", fmt(amount),
                                    "balance_formatted", fmt(bal + amount),
                                    "currency_symbol", currencySymbol()), player));
                });
            });
            return;
        }

        // Vault fallback
        if (vault != null) {
            vault.depositPlayer(player, amount);
            removeOne(item);
            player.sendMessage(Lang.msg("economy.cheque.redeem.success",
                    Map.of("amount_formatted", fmt(amount),
                            "balance_formatted", fmt(vault.getBalance(player)),
                            "currency_symbol", currencySymbol()), player));
            return;
        }

        player.sendMessage(Lang.msg("economy.errors.no-economy", player));
    }

    private void giveChequeItem(Player player, double amount) {
        ItemStack cheque = new ItemStack(Material.PAPER, 1);
        ItemMeta meta = cheque.getItemMeta();
        if (meta != null) {
            // Display name/lore left as legacy colors; items don't render MiniMessage
            meta.setDisplayName("§6Cheque: §e" + currencySymbol() + fmt(amount));
            meta.setLore(List.of("§7Right-click to redeem this cheque."));
            meta.getPersistentDataContainer().set(chequeKey, PersistentDataType.DOUBLE, amount);
            cheque.setItemMeta(meta);
        }
        player.getInventory().addItem(cheque);
    }

    private double readChequeAmount(ItemStack item) {
        if (item == null || item.getType() != Material.PAPER || !item.hasItemMeta()) return 0;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;
        var pdc = meta.getPersistentDataContainer();
        if (!pdc.has(chequeKey, PersistentDataType.DOUBLE)) return 0;
        Double v = pdc.get(chequeKey, PersistentDataType.DOUBLE);
        return v == null ? 0 : v;
    }

    private void removeOne(ItemStack stack) {
        if (stack == null) return;
        int amt = stack.getAmount();
        stack.setAmount(Math.max(0, amt - 1));
    }

    /* ================= Util ================= */

    private void runSync(Runnable r) {
        if (Bukkit.isPrimaryThread()) r.run();
        else Bukkit.getScheduler().runTask(plugin, r);
    }

    private Double parseAmount(String s) {
        try {
            String raw = s.replace(",", "").trim();
            if (raw.startsWith("-") || raw.toLowerCase(Locale.ROOT).contains("e")) return null;
            return Double.parseDouble(raw);
        } catch (Exception e) { return null; }
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

    /* ================= OreoCommand ================= */

    @Override public String name() { return "cheque"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.cheque.create"; }
    @Override public String usage() { return "<create|redeem> [amount]"; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        // Delegate to Bukkit's path to preserve behavior
        return onCommand(sender, null, label, args);
    }
}
