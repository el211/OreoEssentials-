package fr.elias.oreoEssentials.modules.economy.ecocommands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.modules.economy.EconomyService;
import fr.elias.oreoEssentials.util.Async;
import fr.elias.oreoEssentials.util.Lang;
import fr.elias.oreoEssentials.util.OreScheduler;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class ChequeCommand implements CommandExecutor, Listener, OreoCommand {

    private final OreoEssentials plugin;
    private final NamespacedKey chequeKey;

    public ChequeCommand(OreoEssentials plugin) {
        this.plugin = plugin;
        this.chequeKey = new NamespacedKey(plugin, "cheque_amount");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Lang.send(sender, "economy.cheque.usage.root",
                    "<yellow>Usage: /cheque <amount> | /cheque create <amount> | /cheque redeem</yellow>");
            return true;
        }

        boolean canCreate = player.hasPermission("oreo.cheque.create") || player.hasPermission("rabbiteconomy.cheque.create");
        boolean canRedeem = player.hasPermission("oreo.cheque.redeem") || player.hasPermission("rabbiteconomy.cheque.redeem");

        if (args.length == 0) {
            Lang.send(player, "economy.cheque.usage.root",
                    "<yellow>Usage: /cheque <amount> | /cheque create <amount> | /cheque redeem</yellow>");
            return true;
        }

        String firstArg = args[0].trim();
        String sub = firstArg.toLowerCase(Locale.ROOT);

        if ("redeem".equals(sub)) {
            if (!canRedeem) {
                Lang.send(player, "economy.errors.no-permission", "<red>You don't have permission.</red>");
                return true;
            }
            return redeemHeldCheque(player);
        }

        if ("create".equals(sub)) {
            if (!canCreate) {
                Lang.send(player, "economy.errors.no-permission", "<red>You don't have permission.</red>");
                return true;
            }
            if (args.length < 2) {
                Lang.send(player, "economy.cheque.usage.create",
                        "<yellow>Usage: /cheque create <amount></yellow>");
                return true;
            }
            return createCheque(player, parseAmount(args[1]));
        }

        Double directAmount = parseAmount(firstArg);
        if (directAmount != null) {
            if (!canCreate) {
                Lang.send(player, "economy.errors.no-permission", "<red>You don't have permission.</red>");
                return true;
            }
            return createCheque(player, directAmount);
        }

        Lang.send(player, "economy.cheque.usage.root",
                "<yellow>Usage: /cheque <amount> | /cheque create <amount> | /cheque redeem</yellow>");
        return true;
    }

    @EventHandler
    public void onUseCheque(PlayerInteractEvent event) {
        if (!event.getAction().name().contains("RIGHT_CLICK")) return;

        Player player = event.getPlayer();
        boolean canRedeem = player.hasPermission("oreo.cheque.redeem") || player.hasPermission("rabbiteconomy.cheque.redeem");
        if (!canRedeem) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        if (readChequeAmount(item) <= 0) return;

        event.setCancelled(true);
        redeemCheque(player, item, readChequeAmount(item));
    }

    private boolean redeemHeldCheque(Player player) {
        ItemStack inHand = player.getInventory().getItemInMainHand();
        double amount = readChequeAmount(inHand);
        if (amount <= 0) {
            Lang.send(player, "economy.cheque.redeem.fail-none",
                    "<red>You must hold a cheque in your main hand to redeem it.</red>");
            return true;
        }
        redeemCheque(player, inHand, amount);
        return true;
    }

    private boolean createCheque(Player player, Double amount) {
        if (amount == null || amount <= 0) {
            Lang.send(player, "economy.cheque.create.fail-amount",
                    "<red>Invalid cheque amount.</red>");
            return true;
        }

        EconomyService eco;
        try {
            eco = plugin.getEcoBootstrap().api();
        } catch (Throwable t) {
            Lang.send(player, "economy.errors.no-economy", "<red>Economy is not available.</red>");
            return true;
        }

        UUID playerId = player.getUniqueId();
        Async.run(() -> {
            double balance = eco.getBalance(playerId);
            if (balance + 1e-9 < amount) {
                OreScheduler.runForEntity(plugin, player, () -> Lang.send(player,
                        "economy.cheque.create.fail-insufficient",
                        "<red>You don't have <white>%currency_symbol%%amount_formatted%</white>.</red>",
                        Map.of("amount_formatted", fmt(amount), "currency_symbol", currencySymbol())));
                return;
            }

            if (!eco.withdraw(playerId, amount)) {
                OreScheduler.runForEntity(plugin, player, () ->
                        Lang.send(player, "economy.errors.no-economy",
                                "<red>Economy is not available.</red>"));
                return;
            }

            OreScheduler.runForEntity(plugin, player, () -> {
                if (!player.isOnline()) {
                    Async.run(() -> eco.deposit(playerId, amount));
                    return;
                }

                if (!giveChequeItem(player, buildChequeItem(amount))) {
                    Lang.send(player, "economy.cheque.create.fail-inventory",
                            "<red>Free a slot in your inventory to receive the cheque.</red>");
                    Async.run(() -> eco.deposit(playerId, amount));
                    return;
                }

                Lang.send(player, "economy.cheque.create.success",
                        "<green>Created a cheque for <white>%currency_symbol%%amount_formatted%</white>.</green>",
                        Map.of("amount_formatted", fmt(amount), "currency_symbol", currencySymbol()));
            });
        });

        return true;
    }

    private void redeemCheque(Player player, ItemStack item, double amount) {
        EconomyService eco;
        try {
            eco = plugin.getEcoBootstrap().api();
        } catch (Throwable t) {
            Lang.send(player, "economy.errors.no-economy", "<red>Economy is not available.</red>");
            return;
        }

        if (!consumeOneCheque(item)) {
            Lang.send(player, "economy.cheque.redeem.fail-none",
                    "<red>You must hold a cheque in your main hand to redeem it.</red>");
            return;
        }

        UUID playerId = player.getUniqueId();
        Async.run(() -> {
            if (!eco.deposit(playerId, amount)) {
                OreScheduler.runForEntity(plugin, player, () -> {
                    if (!player.isOnline()) return;
                    giveChequeItem(player, buildChequeItem(amount));
                    Lang.send(player, "economy.errors.no-economy",
                            "<red>Economy is not available.</red>");
                });
                return;
            }

            double balance = eco.getBalance(playerId);
            OreScheduler.runForEntity(plugin, player, () -> {
                if (!player.isOnline()) return;
                Lang.send(player, "economy.cheque.redeem.success",
                        "<green>Redeemed cheque for <white>%currency_symbol%%amount_formatted%</white>. New balance: <white>%currency_symbol%%balance_formatted%</white>.</green>",
                        Map.of(
                                "amount_formatted", fmt(amount),
                                "balance_formatted", fmt(balance),
                                "currency_symbol", currencySymbol()
                        ));
            });
        });
    }

    private ItemStack buildChequeItem(double amount) {
        ItemStack cheque = new ItemStack(Material.PAPER, 1);
        ItemMeta meta = cheque.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("\u00A76Cheque: \u00A7e" + currencySymbol() + fmt(amount));
            meta.setLore(List.of("\u00A77Right-click to redeem this cheque."));

            int customModelData = plugin.getConfig().getInt("economy.cheque.custom-model-data", 0);
            if (customModelData > 0) {
                try {
                    meta.setCustomModelData(customModelData);
                } catch (Throwable ignored) {
                }
            }

            meta.getPersistentDataContainer().set(chequeKey, PersistentDataType.DOUBLE, amount);
            cheque.setItemMeta(meta);
        }
        return cheque;
    }

    private boolean giveChequeItem(Player player, ItemStack cheque) {
        if (cheque == null) return false;

        PlayerInventory inventory = player.getInventory();
        boolean preferMainHand = plugin.getConfig().getBoolean("economy.cheque.prefer-main-hand", true);
        if (preferMainHand && tryPlaceInMainHand(inventory, cheque)) {
            return true;
        }

        return inventory.addItem(cheque).isEmpty();
    }

    private boolean tryPlaceInMainHand(PlayerInventory inventory, ItemStack cheque) {
        ItemStack inHand = inventory.getItemInMainHand();
        if (isEmpty(inHand)) {
            inventory.setItemInMainHand(cheque);
            return true;
        }

        if (!inventory.addItem(inHand.clone()).isEmpty()) {
            return false;
        }

        inventory.setItemInMainHand(cheque);
        return true;
    }

    private boolean isEmpty(ItemStack item) {
        return item == null || item.getAmount() <= 0 || item.getType().isAir();
    }

    private double readChequeAmount(ItemStack item) {
        if (item == null || item.getType() != Material.PAPER || !item.hasItemMeta()) return 0;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;
        var pdc = meta.getPersistentDataContainer();
        if (!pdc.has(chequeKey, PersistentDataType.DOUBLE)) return 0;
        Double value = pdc.get(chequeKey, PersistentDataType.DOUBLE);
        return value == null ? 0 : value;
    }

    private boolean consumeOneCheque(ItemStack stack) {
        if (stack == null) return false;
        int amount = stack.getAmount();
        if (amount <= 0) return false;
        if (amount == 1) {
            stack.setAmount(0);
        } else {
            stack.setAmount(amount - 1);
        }
        return true;
    }

    private Double parseAmount(String rawInput) {
        try {
            String raw = rawInput.replace(",", "").trim();
            if (raw.startsWith("-") || raw.toLowerCase(Locale.ROOT).contains("e")) return null;
            return Double.parseDouble(raw);
        } catch (Exception e) {
            return null;
        }
    }

    private String fmt(double value) {
        int decimals = (int) Math.round(Lang.getDouble("economy.format.decimals", 2.0));
        String thousands = Lang.get("economy.format.thousands-separator", ",");
        String decimal = Lang.get("economy.format.decimal-separator", ".");
        String pattern = "#,##0" + (decimals > 0 ? "." + "0".repeat(decimals) : "");
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        if (!thousands.isEmpty()) symbols.setGroupingSeparator(thousands.charAt(0));
        if (!decimal.isEmpty()) symbols.setDecimalSeparator(decimal.charAt(0));
        DecimalFormat format = new DecimalFormat(pattern, symbols);
        format.setGroupingUsed(!thousands.isEmpty());
        return format.format(value);
    }

    private String currencySymbol() {
        return Lang.get("economy.currency.symbol", "$");
    }

    @Override public String name() { return "cheque"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return ""; }
    @Override public String usage() { return "<amount|create <amount>|redeem>"; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        return onCommand(sender, null, label, args);
    }
}
