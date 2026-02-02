package fr.elias.oreoEssentials.modules.currency.gui;

import fr.elias.oreoEssentials.OreoEssentials;
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
 * GUI for creating a new currency
 * Note: This is a simplified version - full implementation would use chat input
 */
public class CurrencyCreateGUI implements InventoryProvider {

    private final OreoEssentials plugin;

    public CurrencyCreateGUI(OreoEssentials plugin) {
        this.plugin = plugin;
    }

    public static SmartInventory getInventory(OreoEssentials plugin) {
        return SmartInventory.builder()
                .id("currency-create")
                .provider(new CurrencyCreateGUI(plugin))
                .size(5, 9)
                .title("§a§lCreate New Currency")
                .manager(plugin.getInvManager())
                .build();
    }

    @Override
    public void init(Player player, InventoryContents contents) {
        contents.set(1, 4, ClickableItem.empty(
                createItem(Material.EMERALD, "§a§lCreate New Currency",
                        "§7Use the command below to create",
                        "§7a new currency with all options",
                        "",
                        "§e§lCommand:",
                        "§f/oecurrency create <id> <name> <symbol>",
                        "",
                        "§7Example:",
                        "§f/oecurrency create tokens Tokens 🪙")
        ));

        contents.set(2, 2, ClickableItem.empty(
                createItem(Material.GOLD_INGOT, "§e§lExample: Gold Coins",
                        "§f/oecurrency create gold Gold 🪙",
                        "",
                        "§7This creates a currency with:",
                        "§7- ID: §fgold",
                        "§7- Name: §fGold",
                        "§7- Symbol: §f🪙")
        ));

        contents.set(2, 4, ClickableItem.empty(
                createItem(Material.DIAMOND, "§b§lExample: Gems",
                        "§f/oecurrency create gems Gems 💎",
                        "",
                        "§7This creates a currency with:",
                        "§7- ID: §fgems",
                        "§7- Name: §fGems",
                        "§7- Symbol: §f💎")
        ));

        contents.set(2, 6, ClickableItem.empty(
                createItem(Material.EMERALD, "§a§lExample: Tokens",
                        "§f/oecurrency create tokens Tokens ⭐",
                        "",
                        "§7This creates a currency with:",
                        "§7- ID: §ftokens",
                        "§7- Name: §fTokens",
                        "§7- Symbol: §f⭐")
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