package fr.elias.oreoEssentials.modules.sellgui.provider;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.sellgui.manager.SellGuiManager;
import fr.elias.oreoEssentials.util.Lang;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;

public final class SellConfirmProvider implements InventoryProvider {

    private final OreoEssentials plugin;
    private final SellGuiManager manager;
    private final double total;
    private final Map<Integer, ItemStack> sellSnapshot;

    public SellConfirmProvider(OreoEssentials plugin, SellGuiManager manager, double total, Map<Integer, ItemStack> snapshot) {
        this.plugin       = plugin;
        this.manager      = manager;
        this.total        = total;
        this.sellSnapshot = snapshot;
    }

    @Override
    public void init(Player player, InventoryContents contents) {
        // Background filler
        ItemStack pane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta pm = pane.getItemMeta();
        if (pm != null) {
            pm.setDisplayName(" ");
            pm.addItemFlags(ItemFlag.values());
            pane.setItemMeta(pm);
        }
        contents.fill(ClickableItem.empty(pane));

        String sym = plugin.getVaultEconomy() != null
                ? plugin.getVaultEconomy().currencyNamePlural()
                : "";

        // ── Confirm button ────────────────────────────────────────────────────
        contents.set(1, 2, ClickableItem.of(
                manager.config().buildButton(
                        "confirm",
                        Material.LIME_CONCRETE,
                        "<green><bold>CONFIRM</bold></green>",
                        List.of(
                                "<gray>You will receive:</gray>",
                                "<green><bold>" + String.format("%.2f", total) + " " + sym + "</bold></green>",
                                "",
                                "<yellow>Click to confirm the sale.</yellow>"
                        )
                ),
                e -> {
                    if (!matchesPlayerInventory(player)) {
                        player.sendMessage(Lang.color("&cSome items changed. Please try again."));
                        player.closeInventory();
                        return;
                    }

                    removeSellItems(player);

                    if (plugin.getVaultEconomy() != null) {
                        plugin.getVaultEconomy().depositPlayer(player, total);
                    } else {
                        player.sendMessage(Lang.color("&cEconomy is not available."));
                        return;
                    }

                    player.sendMessage(Lang.color("&aSold items for &e" + String.format("%.2f", total) + " " + sym + "&a!"));
                    player.closeInventory();
                }
        ));

        // ── Info item (center) ────────────────────────────────────────────────
        ItemStack info = manager.config().buildButton(
                "info",
                Material.PAPER,
                "<gold><bold>Sale Summary</bold></gold>",
                List.of(
                        "<gray>Items to sell: <white>" + sellSnapshot.size() + "</white></gray>",
                        "<gray>Total: <green><bold>" + String.format("%.2f", total) + " " + sym + "</bold></green></gray>"
                )
        );
        contents.set(1, 4, ClickableItem.empty(info));

        // ── Cancel button ─────────────────────────────────────────────────────
        contents.set(1, 6, ClickableItem.of(
                manager.config().buildButton(
                        "cancel",
                        Material.RED_CONCRETE,
                        "<red><bold>CANCEL</bold></red>",
                        List.of("<gray>Go back to the sell menu.</gray>")
                ),
                e -> manager.openSell(player)
        ));
    }

    @Override
    public void update(Player player, InventoryContents contents) {}

    // ── Validation ────────────────────────────────────────────────────────────

    /**
     * Verifies that the player still has every item from the snapshot
     * in their personal inventory (items were returned there when the
     * sell GUI closed to make room for this confirm dialog).
     */
    private boolean matchesPlayerInventory(Player player) {
        for (ItemStack expected : sellSnapshot.values()) {
            int needed = expected.getAmount();
            for (ItemStack inv : player.getInventory().getContents()) {
                if (inv == null || !inv.isSimilar(expected)) continue;
                needed -= inv.getAmount();
                if (needed <= 0) break;
            }
            if (needed > 0) return false;
        }
        return true;
    }

    // ── Removal ──────────────────────────────────────────────────────────────

    /**
     * Removes the snapshot items from the player's personal inventory.
     * Items were returned there automatically when the sell GUI closed.
     */
    private void removeSellItems(Player player) {
        for (ItemStack toRemove : sellSnapshot.values()) {
            player.getInventory().removeItem(toRemove);
        }
        player.updateInventory();
    }
}
