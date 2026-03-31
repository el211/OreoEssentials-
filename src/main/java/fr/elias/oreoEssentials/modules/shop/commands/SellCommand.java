package fr.elias.oreoEssentials.modules.shop.commands;

import fr.elias.oreoEssentials.modules.shop.ShopModule;
import fr.elias.oreoEssentials.modules.shop.models.ShopItem;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;

public final class SellCommand implements CommandExecutor, TabCompleter {

    private final ShopModule module;

    public SellCommand(ShopModule module) {
        this.module = module;
    }


    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (args.length == 0) {
            send(player, "&6Usage: /sell <hand|handall|all> [quantity]");
            return true;
        }

        switch (args[0].toLowerCase()) {

            case "hand" -> {
                if (!player.hasPermission("oshopgui.sell.hand")) {
                    send(player, module.getShopConfig().getMessage("no-permission"));
                    return true;
                }
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand.getType() == Material.AIR) {
                    send(player, module.getShopConfig().getMessage("sell-hand-empty"));
                    return true;
                }
                ShopItem shopItem = module.getShopManager().findBestSellItem(hand);
                if (shopItem == null || !shopItem.canSell()) {
                    send(player, module.getShopConfig().getMessage("sell-hand-not-sellable"));
                    return true;
                }
                int qty = 1;
                if (args.length > 1) {
                    try { qty = Math.max(1, Integer.parseInt(args[1])); }
                    catch (NumberFormatException e) {
                        send(player, module.getShopConfig().getMessage("buy-invalid-amount"));
                        return true;
                    }
                }
                module.getTransactionProcessor().processSell(player, shopItem, qty);
            }

            case "handall" -> {
                if (!player.hasPermission("oshopgui.sell.hand.all")) {
                    send(player, module.getShopConfig().getMessage("no-permission"));
                    return true;
                }
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand.getType() == Material.AIR) {
                    send(player, module.getShopConfig().getMessage("sell-hand-empty"));
                    return true;
                }
                ShopItem shopItem = module.getShopManager().findBestSellItem(hand);
                if (shopItem == null || !shopItem.canSell()) {
                    send(player, module.getShopConfig().getMessage("sell-hand-not-sellable"));
                    return true;
                }
                module.getTransactionProcessor().processSellAll(player, hand);
            }

            case "all" -> {
                if (!player.hasPermission("oshopgui.sell.all")) {
                    send(player, module.getShopConfig().getMessage("no-permission"));
                    return true;
                }
                module.getTransactionProcessor().processSellAll(player, null);
            }

            default -> send(player, "&6Usage: /sell <hand|handall|all> [quantity]");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) return Arrays.asList("hand", "handall", "all");
        return List.of();
    }


    private void send(Player player, String msg) {
        Lang.sendRaw(player, msg);
    }
}
