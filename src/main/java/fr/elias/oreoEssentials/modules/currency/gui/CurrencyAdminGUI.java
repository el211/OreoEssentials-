package fr.elias.oreoEssentials.modules.currency.gui;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.currency.Currency;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import fr.minuskube.inv.content.Pagination;
import fr.minuskube.inv.content.SlotIterator;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Main admin GUI for managing currencies
 * Command: /currencyadmin or /cadmin
 */
public class CurrencyAdminGUI implements InventoryProvider {

    private final OreoEssentials plugin;

    public CurrencyAdminGUI(OreoEssentials plugin) {
        this.plugin = plugin;
    }

    public static SmartInventory getInventory(OreoEssentials plugin) {
        return SmartInventory.builder()
                .id("currency-admin")
                .provider(new CurrencyAdminGUI(plugin))
                .size(6, 9)
                .title("§6§lCurrency Manager")
                .manager(plugin.getInvManager())
                .build();
    }

    @Override
    public void init(Player player, InventoryContents contents) {
        Pagination pagination = contents.pagination();

        List<Currency> currencies = plugin.getCurrencyService().getAllCurrencies();
        List<ClickableItem> items = new ArrayList<>();

        for (Currency currency : currencies) {
            items.add(ClickableItem.of(createCurrencyItem(currency), e -> {
                CurrencyEditGUI.getInventory(plugin, currency).open(player);
            }));
        }

        pagination.setItems(items.toArray(new ClickableItem[0]));
        pagination.setItemsPerPage(28); // 4 rows of 7 items

        SlotIterator iterator = contents.newIterator(SlotIterator.Type.HORIZONTAL, 1, 1);
        iterator = iterator.blacklist(1, 8).blacklist(2, 0).blacklist(2, 8)
                .blacklist(3, 0).blacklist(3, 8).blacklist(4, 0).blacklist(4, 8);

        pagination.addToIterator(iterator);

        if (!pagination.isFirst()) {
            contents.set(5, 3, ClickableItem.of(createItem(Material.ARROW, "§e← Previous Page", "§7Click to go back"), e -> {
                getInventory(plugin).open(player, pagination.previous().getPage());
            }));
        }

        if (!pagination.isLast()) {
            contents.set(5, 5, ClickableItem.of(createItem(Material.ARROW, "§eNext Page →", "§7Click to continue"), e -> {
                getInventory(plugin).open(player, pagination.next().getPage());
            }));
        }

        contents.set(5, 4, ClickableItem.of(
                createItem(Material.EMERALD, "§a§lCreate New Currency",
                        "§7Click to create a new currency"),
                e -> CurrencyCreateGUI.getInventory(plugin).open(player)
        ));

        contents.set(5, 8, ClickableItem.of(
                createItem(Material.BARRIER, "§c§lClose", "§7Click to close"),
                e -> player.closeInventory()
        ));

        contents.set(0, 4, ClickableItem.empty(
                createItem(Material.GOLD_INGOT, "§6§lCurrency Manager",
                        "§7Total Currencies: §e" + currencies.size(),
                        "",
                        "§7Click a currency to edit it",
                        "§7or create a new one below")
        ));

        ItemStack border = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        contents.fillBorders(ClickableItem.empty(border));
    }

    @Override
    public void update(Player player, InventoryContents contents) {
    }

    private ItemStack createCurrencyItem(Currency currency) {
        ItemStack item = new ItemStack(Material.GOLD_NUGGET);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§e" + currency.getName() + " " + currency.getSymbol());

        List<String> lore = new ArrayList<>();
        lore.add("§7ID: §f" + currency.getId());
        lore.add("§7Symbol: §f" + currency.getSymbol());
        lore.add("§7Default Balance: §f" + currency.format(currency.getDefaultBalance()));
        lore.add("§7Tradeable: " + (currency.isTradeable() ? "§aYes" : "§cNo"));
        lore.add("§7Cross-Server: " + (currency.isCrossServer() ? "§aYes" : "§cNo"));
        lore.add("§7Allow Negative: " + (currency.isAllowNegative() ? "§aYes" : "§cNo"));
        lore.add("");
        lore.add("§e§lClick to edit");

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    private ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);

        if (lore.length > 0) {
            List<String> loreList = new ArrayList<>();
            for (String line : lore) {
                loreList.add(line);
            }
            meta.setLore(loreList);
        }

        item.setItemMeta(meta);
        return item;
    }
}