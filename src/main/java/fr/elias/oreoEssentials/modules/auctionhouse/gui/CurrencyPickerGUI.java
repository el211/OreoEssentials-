package fr.elias.oreoEssentials.modules.auctionhouse.gui;

import fr.elias.oreoEssentials.modules.auctionhouse.AuctionHouseModule;
import fr.elias.oreoEssentials.modules.currency.Currency;
import fr.elias.oreoEssentials.modules.currency.CurrencyService;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

import static fr.elias.oreoEssentials.modules.auctionhouse.gui.BrowseGUI.*;

/**
 * Shown when a player does /ahs with no arguments.
 * Lets them pick which currency to use for their listing (Vault or a custom currency),
 * then prompts them to type the price in chat.
 */
public class CurrencyPickerGUI implements InventoryProvider {

    private final AuctionHouseModule module;
    private final ItemStack itemToSell;
    private final long durationHours;

    private CurrencyPickerGUI(AuctionHouseModule module, ItemStack item, long durationHours) {
        this.module = module;
        this.itemToSell = item.clone();
        this.durationHours = durationHours;
    }

    public static SmartInventory getInventory(AuctionHouseModule module, ItemStack item, long durationHours) {
        return SmartInventory.builder()
                .id("oe_ah_currency_picker")
                .provider(new CurrencyPickerGUI(module, item, durationHours))
                .manager(module.getPlugin().getInvManager())
                .size(3, 9)
                .title(c("&6&lSelect Currency"))
                .build();
    }

    @Override
    public void init(Player player, InventoryContents contents) {
        contents.fillBorders(ClickableItem.empty(
                glass(module.getConfig().guiBorder("sell", Material.ORANGE_STAINED_GLASS_PANE))));

        // Preview of the item being sold
        ItemStack preview = itemToSell.clone();
        ItemMeta pm = preview.getItemMeta();
        if (pm != null) {
            List<String> lore = pm.hasLore() ? new ArrayList<>(pm.getLore()) : new ArrayList<>();
            lore.add("");
            lore.add(c("&7Duration: &e" + durationHours + "h"));
            lore.add(c("&eSelect a currency below!"));
            pm.setLore(lore);
            preview.setItemMeta(pm);
        }
        contents.set(1, 4, ClickableItem.empty(preview));

        int nextSlot = 10; // row 1, col 1

        // ── Vault option ─────────────────────────────────────────────────────
        if (module.getEconomy() != null) {
            String vaultName = module.getEconomy().getName();
            ItemStack vaultBtn = new ItemStack(Material.GOLD_INGOT);
            ItemMeta vm = vaultBtn.getItemMeta();
            vm.setDisplayName(c("&6&l" + vaultName + " &7(Vault)"));
            vm.setLore(List.of(
                    c("&7Use the server's main economy."),
                    "",
                    c("&a&lClick to select!")));
            vaultBtn.setItemMeta(vm);
            contents.set(nextSlot / 9, nextSlot % 9, ClickableItem.of(vaultBtn, e -> {
                click(player);
                player.closeInventory();
                module.addPendingSell(player, itemToSell, null, durationHours);
                player.sendMessage(c("&6&lAuction House &8» &eType the &6listing price &ein chat:"));
            }));
            nextSlot++;
        }

        // ── Custom currencies ─────────────────────────────────────────────────
        CurrencyService cs = module.getPlugin().getCurrencyService();
        if (cs != null) {
            List<Currency> currencies = cs.getAllCurrencies();
            for (Currency currency : currencies) {
                if (nextSlot >= 17) break;

                String displayName = currency.getDisplayName() != null
                        ? currency.getDisplayName() : currency.getName();
                String symbol = currency.getSymbol() != null ? currency.getSymbol() : "";

                ItemStack btn = new ItemStack(Material.SUNFLOWER);
                ItemMeta bm = btn.getItemMeta();
                bm.setDisplayName(c("&e&l" + displayName + " &7(" + symbol + ")"));
                bm.setLore(List.of(
                        c("&7Currency ID: &f" + currency.getId()),
                        "",
                        c("&a&lClick to select!")));
                btn.setItemMeta(bm);

                final String cid = currency.getId();
                contents.set(nextSlot / 9, nextSlot % 9, ClickableItem.of(btn, e -> {
                    click(player);
                    player.closeInventory();
                    module.addPendingSell(player, itemToSell, cid, durationHours);
                    player.sendMessage(c("&6&lAuction House &8» &eType the &6listing price &7(in &e"
                            + displayName + "&7) &ein chat:"));
                }));
                nextSlot++;
            }
        }
    }

    @Override
    public void update(Player player, InventoryContents contents) {}
}
