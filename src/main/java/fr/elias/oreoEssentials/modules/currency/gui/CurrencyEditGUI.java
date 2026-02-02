package fr.elias.oreoEssentials.modules.currency.gui;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.currency.Currency;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI for editing an existing currency
 */
public class CurrencyEditGUI implements InventoryProvider {

    private final OreoEssentials plugin;
    private final Currency currency;

    public CurrencyEditGUI(OreoEssentials plugin, Currency currency) {
        this.plugin = plugin;
        this.currency = currency;
    }

    public static SmartInventory getInventory(OreoEssentials plugin, Currency currency) {
        return SmartInventory.builder()
                .id("currency-edit-" + currency.getId())
                .provider(new CurrencyEditGUI(plugin, currency))
                .size(5, 9)
                .title("§6Editing: " + currency.getName())
                .manager(plugin.getInvManager())
                .build();
    }

    @Override
    public void init(Player player, InventoryContents contents) {
        contents.set(1, 4, ClickableItem.empty(
                createItem(Material.GOLD_INGOT, "§6" + currency.getName() + " " + currency.getSymbol(),
                        "§7ID: §f" + currency.getId(),
                        "§7Display Name: §f" + currency.getDisplayName(),
                        "",
                        "§7Click options below to edit")
        ));

        contents.set(2, 1, ClickableItem.of(
                createItem(Material.NAME_TAG, "§e§lEdit Symbol",
                        "§7Current: §f" + currency.getSymbol(),
                        "",
                        "§7Type in chat to change symbol",
                        "§e§lComing Soon: Chat input"),
                e -> {
                    player.sendMessage("§cChat input not implemented yet");
                    player.sendMessage("§7Use §e/oecurrency §7command instead");
                }
        ));

        contents.set(2, 3, ClickableItem.of(
                createItem(
                        currency.isTradeable() ? Material.LIME_DYE : Material.GRAY_DYE,
                        "§e§lTradeable",
                        "§7Current: " + (currency.isTradeable() ? "§aEnabled" : "§cDisabled"),
                        "",
                        "§7Players can send this currency",
                        "§7to each other with /csend",
                        "",
                        "§e§lClick to toggle"
                ),
                e -> {
                    Currency updated = Currency.builder()
                            .id(currency.getId())
                            .name(currency.getName())
                            .symbol(currency.getSymbol())
                            .displayName(currency.getDisplayName())
                            .defaultBalance(currency.getDefaultBalance())
                            .tradeable(!currency.isTradeable()) // Toggle
                            .crossServer(currency.isCrossServer())
                            .allowNegative(currency.isAllowNegative())
                            .build();

                    plugin.getCurrencyService().createCurrency(updated).thenRun(() -> {
                        player.sendMessage("§a✔ Toggled tradeable to: " + !currency.isTradeable());
                        getInventory(plugin, updated).open(player);
                    });
                }
        ));

        contents.set(2, 5, ClickableItem.of(
                createItem(
                        currency.isAllowNegative() ? Material.REDSTONE : Material.GRAY_DYE,
                        "§e§lAllow Negative Balance",
                        "§7Current: " + (currency.isAllowNegative() ? "§aEnabled" : "§cDisabled"),
                        "",
                        "§7If enabled, players can have",
                        "§7negative balances (like debt)",
                        "",
                        "§e§lClick to toggle"
                ),
                e -> {
                    Currency updated = Currency.builder()
                            .id(currency.getId())
                            .name(currency.getName())
                            .symbol(currency.getSymbol())
                            .displayName(currency.getDisplayName())
                            .defaultBalance(currency.getDefaultBalance())
                            .tradeable(currency.isTradeable())
                            .crossServer(currency.isCrossServer())
                            .allowNegative(!currency.isAllowNegative()) // Toggle
                            .build();

                    plugin.getCurrencyService().createCurrency(updated).thenRun(() -> {
                        player.sendMessage("§a✔ Toggled allow negative to: " + !currency.isAllowNegative());
                        getInventory(plugin, updated).open(player);
                    });
                }
        ));

        contents.set(2, 7, ClickableItem.of(
                createItem(
                        currency.isCrossServer() ? Material.ENDER_PEARL : Material.GRAY_DYE,
                        "§e§lCross-Server",
                        "§7Current: " + (currency.isCrossServer() ? "§aEnabled" : "§cDisabled"),
                        "",
                        "§7If enabled, balances sync",
                        "§7across all servers",
                        "",
                        "§c§lRequires MongoDB",
                        "§e§lClick to toggle"
                ),
                e -> {
                    if (!plugin.getCurrencyConfig().isCrossServerEnabled()) {
                        player.sendMessage("§c✖ Cross-server requires MongoDB!");
                        player.sendMessage("§7Enable it in currency-config.yml");
                        return;
                    }

                    Currency updated = Currency.builder()
                            .id(currency.getId())
                            .name(currency.getName())
                            .symbol(currency.getSymbol())
                            .displayName(currency.getDisplayName())
                            .defaultBalance(currency.getDefaultBalance())
                            .tradeable(currency.isTradeable())
                            .crossServer(!currency.isCrossServer()) // Toggle
                            .allowNegative(currency.isAllowNegative())
                            .build();

                    plugin.getCurrencyService().createCurrency(updated).thenRun(() -> {
                        player.sendMessage("§a✔ Toggled cross-server to: " + !currency.isCrossServer());
                        getInventory(plugin, updated).open(player);
                    });
                }
        ));

        contents.set(3, 4, ClickableItem.of(
                createItem(Material.BARRIER, "§c§lDelete Currency",
                        "§7This will permanently delete",
                        "§7this currency and ALL balances",
                        "",
                        "§c§l§nWARNING: IRREVERSIBLE",
                        "",
                        "§e§lClick to confirm"),
                e -> {
                    plugin.getCurrencyService().deleteCurrency(currency.getId()).thenRun(() -> {
                        player.sendMessage("§c✖ Deleted currency: " + currency.getName());
                        CurrencyAdminGUI.getInventory(plugin).open(player);
                    });
                }
        ));

        contents.set(4, 0, ClickableItem.of(
                createItem(Material.ARROW, "§e← Back to List"),
                e -> CurrencyAdminGUI.getInventory(plugin).open(player)
        ));

        contents.set(4, 8, ClickableItem.of(
                createItem(Material.BARRIER, "§c§lClose"),
                e -> player.closeInventory()
        ));

        ItemStack border = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        contents.fillBorders(ClickableItem.empty(border));
    }

    @Override
    public void update(Player player, InventoryContents contents) {
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