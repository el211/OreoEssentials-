package fr.elias.oreoEssentials.modules.shop.commands;

import fr.elias.oreoEssentials.modules.shop.ShopModule;
import fr.elias.oreoEssentials.modules.shop.models.PriceModifier;
import fr.elias.oreoEssentials.modules.shop.models.Shop;
import fr.elias.oreoEssentials.modules.shop.models.ShopItem;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public final class ShopCommand implements CommandExecutor, TabCompleter {

    private final ShopModule module;

    public ShopCommand(ShopModule module) {
        this.module = module;
    }


    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Players only.");
                return true;
            }
            if (!player.hasPermission("oshopgui.shop")) {
                send(player, module.getShopConfig().getMessage("no-permission"));
                return true;
            }
            module.getMainMenuGUI().open(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("reroll")) {
            if (!sender.hasPermission("oshopgui.reroll")) {
                send(sender, module.getShopConfig().getMessage("no-permission"));
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(color("&cUsage: /shop reroll <shopId>"));
                return true;
            }
            Shop targetShop = module.getShopManager().getShop(args[1]);
            if (targetShop == null) {
                send(sender, module.getShopConfig().getMessage("shop-not-found").replace("{shop}", args[1]));
                return true;
            }
            if (!targetShop.isRotating()) {
                send(sender, color("&cShop &e" + targetShop.getId() + " &cdoes not have rotation enabled."));
                return true;
            }
            module.getRotationManager().forceReroll(targetShop);
            send(sender, color("&aRotation rerolled for shop &e" + targetShop.getId() + "&a."));
            return true;
        }

        if (sub.equals("reload")) {
            if (!sender.hasPermission("oshopgui.reload")) {
                send(sender, module.getShopConfig().getMessage("no-permission"));
                return true;
            }
            try {
                module.reload();
                send(sender, module.getShopConfig().getMessage("reload-success"));
            } catch (Exception e) {
                send(sender, module.getShopConfig().getMessage("reload-fail"));
                module.getPlugin().getLogger().severe("[Shop] Reload failed: " + e.getMessage());
                e.printStackTrace();
            }
            return true;
        }

        if (sub.equals("check")) {
            if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
            if (!player.hasPermission("oshopgui.check")) {
                send(player, module.getShopConfig().getMessage("no-permission"));
                return true;
            }
            ItemStack hand = player.getInventory().getItemInMainHand();
            ShopItem found = module.getShopManager().findBestSellItem(hand);
            if (found == null) {
                send(player, module.getShopConfig().getMessage("check-item-not-in-shop"));
            } else {
                String sym = module.getShopConfig().getCurrencySymbol();
                send(player, module.getShopConfig().getMessage("check-item-header"));
                send(player, module.getShopConfig().getMessage("check-item-material")
                        .replace("{material}", found.getMaterial().name()));
                send(player, module.getShopConfig().getMessage("check-item-name")
                        .replace("{name}", found.getDisplayName().isEmpty()
                                ? found.getMaterial().name()
                                : found.getDisplayName()));
                if (found.canBuy())
                    send(player, module.getShopConfig().getMessage("check-item-buy")
                            .replace("{price}", sym + String.format("%.2f",
                                    module.getPriceModifierManager().getEffectiveBuyPrice(player.getUniqueId(), found))));
                if (found.canSell())
                    send(player, module.getShopConfig().getMessage("check-item-sell")
                            .replace("{price}", sym + String.format("%.2f",
                                    module.getPriceModifierManager().getEffectiveSellPrice(player.getUniqueId(), found))));
            }
            return true;
        }

        if (sub.equals("addmodifier")) {
            if (!sender.hasPermission("oshopgui.shop.addmodifier")) {
                send(sender, module.getShopConfig().getMessage("no-permission"));
                return true;
            }
            return handleAddModifier(sender, args);
        }

        if (sub.equals("resetmodifier")) {
            if (!sender.hasPermission("oshopgui.shop.resetmodifier")) {
                send(sender, module.getShopConfig().getMessage("no-permission"));
                return true;
            }
            return handleResetModifier(sender, args);
        }

        if (sub.equals("checkmodifiers")) {
            if (!sender.hasPermission("oshopgui.shop.checkmodifiers")) {
                send(sender, module.getShopConfig().getMessage("no-permission"));
                return true;
            }
            if (args.length < 2) { sender.sendMessage("Usage: /shop checkmodifiers <player>"); return true; }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) { sendNotFound(sender, args[1]); return true; }

            send(sender, module.getShopConfig().getMessage("modifier-check-header")
                    .replace("{player}", target.getName()));
            PriceModifier global = module.getPriceModifierManager().getGlobalModifier(target.getUniqueId());
            send(sender, module.getShopConfig().getMessage("modifier-global")
                    .replace("{buy}",  String.format("%.1f", global.getBuyModifier()  * 100))
                    .replace("{sell}", String.format("%.1f", global.getSellModifier() * 100)));
            module.getPriceModifierManager().getShopModifiers(target.getUniqueId())
                    .forEach((shopId, mod) -> send(sender,
                            module.getShopConfig().getMessage("modifier-shop")
                                    .replace("{shop}", shopId)
                                    .replace("{buy}",  String.format("%.1f", mod.getBuyModifier()  * 100))
                                    .replace("{sell}", String.format("%.1f", mod.getSellModifier() * 100))));
            module.getPriceModifierManager().getItemModifiers(target.getUniqueId())
                    .forEach((key, mod) -> {
                        String[] parts = key.split(":", 2);
                        send(sender, module.getShopConfig().getMessage("modifier-item")
                                .replace("{shop}", parts.length > 0 ? parts[0] : key)
                                .replace("{item}", parts.length > 1 ? parts[1] : "?")
                                .replace("{buy}",  String.format("%.1f", mod.getBuyModifier()  * 100))
                                .replace("{sell}", String.format("%.1f", mod.getSellModifier() * 100)));
                    });
            return true;
        }

        if (args.length == 1) {
            if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
            if (!player.hasPermission("oshopgui.shop")) {
                send(player, module.getShopConfig().getMessage("no-permission"));
                return true;
            }
            Shop shop = module.getShopManager().getShop(args[0]);
            if (shop != null) {
                module.getShopGUI().open(player, shop, 1);
            } else {
                send(player, module.getShopConfig().getMessage("shop-not-found").replace("{shop}", args[0]));
            }
            return true;
        }

        if (args.length == 2) {
            if (!sender.hasPermission("oshopgui.others")) {
                send(sender, module.getShopConfig().getMessage("no-permission"));
                return true;
            }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) { sendNotFound(sender, args[0]); return true; }
            Shop shop = module.getShopManager().getShop(args[1]);
            if (shop == null) {
                send(sender, module.getShopConfig().getMessage("shop-not-found").replace("{shop}", args[1]));
                return true;
            }
            module.getShopGUI().open(target, shop, 1);
            sender.sendMessage(color("&aOpened shop &e" + shop.getId() + "&a for &e" + target.getName()));
            return true;
        }

        return true;
    }


    private boolean handleAddModifier(CommandSender sender, String[] args) {
        if (args.length < 2) { sender.sendMessage("Usage: /shop addmodifier <global|shop|item> ..."); return true; }
        try {
            switch (args[1].toLowerCase()) {
                case "global" -> {
                    if (args.length < 4) { sender.sendMessage("Usage: /shop addmodifier global <player> <value> [buy|sell]"); return true; }
                    Player t = Bukkit.getPlayer(args[2]);
                    if (t == null) { sendNotFound(sender, args[2]); return true; }
                    double val   = Double.parseDouble(args[3]) / 100.0;
                    boolean isBuy = args.length < 5 || !args[4].equalsIgnoreCase("sell");
                    module.getPriceModifierManager().setGlobalModifier(t.getUniqueId(), val, isBuy);
                    send(sender, module.getShopConfig().getMessage("modifier-added")
                            .replace("{type}", "global").replace("{value}", args[3]).replace("{player}", t.getName()));
                }
                case "shop" -> {
                    if (args.length < 5) { sender.sendMessage("Usage: /shop addmodifier shop <player> <shop> <value> [buy|sell]"); return true; }
                    Player t = Bukkit.getPlayer(args[2]);
                    if (t == null) { sendNotFound(sender, args[2]); return true; }
                    double val   = Double.parseDouble(args[4]) / 100.0;
                    boolean isBuy = args.length < 6 || !args[5].equalsIgnoreCase("sell");
                    module.getPriceModifierManager().setShopModifier(t.getUniqueId(), args[3], val, isBuy);
                    send(sender, module.getShopConfig().getMessage("modifier-added")
                            .replace("{type}", "shop").replace("{value}", args[4]).replace("{player}", t.getName()));
                }
                case "item" -> {
                    if (args.length < 6) { sender.sendMessage("Usage: /shop addmodifier item <player> <shop> <item> <value> [buy|sell]"); return true; }
                    Player t = Bukkit.getPlayer(args[2]);
                    if (t == null) { sendNotFound(sender, args[2]); return true; }
                    double val   = Double.parseDouble(args[5]) / 100.0;
                    boolean isBuy = args.length < 7 || !args[6].equalsIgnoreCase("sell");
                    module.getPriceModifierManager().setItemModifier(t.getUniqueId(), args[3], args[4], val, isBuy);
                    send(sender, module.getShopConfig().getMessage("modifier-added")
                            .replace("{type}", "item").replace("{value}", args[5]).replace("{player}", t.getName()));
                }
                default -> sender.sendMessage("Unknown type: " + args[1] + ". Use global, shop, or item.");
            }
        } catch (NumberFormatException e) {
            sender.sendMessage("Invalid number. Example: 110 = 10% markup, 90 = 10% discount.");
        }
        return true;
    }

    private boolean handleResetModifier(CommandSender sender, String[] args) {
        if (args.length < 3) { sender.sendMessage("Usage: /shop resetmodifier <global|shop|item> <player> ..."); return true; }
        Player t = Bukkit.getPlayer(args[2]);
        if (t == null) { sendNotFound(sender, args[2]); return true; }
        switch (args[1].toLowerCase()) {
            case "global" -> module.getPriceModifierManager().resetGlobalModifier(t.getUniqueId());
            case "shop"   -> {
                if (args.length < 4) { sender.sendMessage("Specify shop name."); return true; }
                module.getPriceModifierManager().resetShopModifier(t.getUniqueId(), args[3]);
            }
            case "item"   -> {
                if (args.length < 5) { sender.sendMessage("Specify shop and item."); return true; }
                module.getPriceModifierManager().resetItemModifier(t.getUniqueId(), args[3], args[4]);
            }
        }
        send(sender, module.getShopConfig().getMessage("modifier-reset")
                .replace("{type}", args[1]).replace("{player}", t.getName()));
        return true;
    }


    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(List.of("reload", "check", "reroll", "addmodifier", "resetmodifier", "checkmodifiers"));
            completions.addAll(module.getShopManager().getAllShops().keySet());
            Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("addmodifier") || args[0].equalsIgnoreCase("resetmodifier")) {
                completions.addAll(List.of("global", "shop", "item"));
            } else if (args[0].equalsIgnoreCase("checkmodifiers")) {
                Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
            } else if (args[0].equalsIgnoreCase("reroll")) {
                // Only suggest rotating shops
                module.getShopManager().getAllShops().forEach((id, s) -> {
                    if (s.isRotating()) completions.add(id);
                });
            } else {
                completions.addAll(module.getShopManager().getAllShops().keySet());
            }
        }

        String partial = args[args.length - 1].toLowerCase();
        completions.removeIf(s -> !s.toLowerCase().startsWith(partial));
        return completions;
    }

    // -------------------------------------------------------------------------

    private void send(CommandSender sender, String msg) {
        sender.sendMessage(color(msg));
    }

    private void sendNotFound(CommandSender sender, String name) {
        send(sender, module.getShopConfig().getMessage("player-not-found").replace("{player}", name));
    }

    private static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}