package fr.elias.oreoEssentials.commands.core.playercommands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.modules.shop.ShopModule;
import fr.elias.oreoEssentials.modules.shop.models.Shop;
import fr.elias.oreoEssentials.modules.shop.models.ShopItem;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * /price — check buy/sell prices of the item in your hand from all shops.
 */
public class PriceCommand implements OreoCommand {

    private final OreoEssentials plugin;

    public PriceCommand(OreoEssentials plugin) {
        this.plugin = plugin;
    }

    @Override public String       name()       { return "price"; }
    @Override public List<String> aliases()    { return List.of("worth"); }
    @Override public String       permission() { return "oreo.price"; }
    @Override public String       usage()      { return ""; }
    @Override public boolean      playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        Player p = (Player) sender;

        ShopModule shopModule = plugin.getShopModule();
        if (shopModule == null || !shopModule.isEnabled()) {
            Lang.send(p, "price.shop-unavailable", "<red>The shop module is not available.</red>");
            return true;
        }

        ItemStack held = p.getInventory().getItemInMainHand();
        if (held.getType().isAir()) {
            Lang.send(p, "price.empty-hand", "<red>Hold an item to check its price.</red>");
            return true;
        }

        List<ShopItem> matches = new ArrayList<>();
        for (Shop shop : shopModule.getShopManager().getAllShops().values()) {
            ShopItem match = shop.findMatchingItem(held);
            if (match != null) matches.add(match);
        }

        // Also check items that can be bought (findMatchingItem only checks sellable by default)
        // So do a separate pass for buy-only items
        if (matches.isEmpty()) {
            for (Shop shop : shopModule.getShopManager().getAllShops().values()) {
                for (ShopItem item : shop.getAllItems()) {
                    if (item.canBuy() && item.matches(held) && !matches.contains(item)) {
                        matches.add(item);
                    }
                }
            }
        }

        if (matches.isEmpty()) {
            Lang.send(p, "price.not-found",
                    "<red>No price found for <white>%item%</white> in any shop.</red>",
                    Map.of("item", held.getType().name().toLowerCase().replace('_', ' ')));
            return true;
        }

        String itemName = held.hasItemMeta() && held.getItemMeta().hasDisplayName()
                ? held.getItemMeta().getDisplayName()
                : ChatColor.YELLOW + held.getType().name().toLowerCase().replace('_', ' ');

        p.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&8&m    &r &6Price: &f" + itemName + " &8&m    "));

        for (ShopItem item : matches) {
            String buy  = item.canBuy()  ? "&a$" + String.format("%.2f", item.getBuyPrice())  : "&7N/A";
            String sell = item.canSell() ? "&e$" + String.format("%.2f", item.getSellPrice()) : "&7N/A";
            p.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "  &8[&b" + item.getShopId() + "&8] &7Buy: " + buy + " &8| &7Sell: " + sell));
        }
        return true;
    }
}
