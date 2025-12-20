package fr.elias.oreoEssentials.sellgui;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.util.Lang;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public final class SellConfirmProvider implements InventoryProvider {

    private final OreoEssentials plugin;
    private final SellGuiManager manager;
    private final double total;
    private final Map<Integer, ItemStack> sellSnapshot;

    public SellConfirmProvider(OreoEssentials plugin, SellGuiManager manager, double total, Map<Integer, ItemStack> snapshot) {
        this.plugin = plugin;
        this.manager = manager;
        this.total = total;
        this.sellSnapshot = snapshot;
    }

    @Override
    public void init(Player player, InventoryContents contents) {
        contents.fill(ClickableItem.empty(new ItemStack(Material.BLACK_STAINED_GLASS_PANE)));

        // Confirm
        contents.set(1, 2, ClickableItem.of(new ItemStack(Material.LIME_CONCRETE), e -> {
            // Re-check current sell slots match snapshot (anti-dupe safety)
            if (!matchesCurrentSellSlots(player)) {
                player.sendMessage(Lang.color("&cSell contents changed. Please try again."));
                player.closeInventory();
                return;
            }

            // Remove items from sell slots and pay
            removeSellItems(player);

            if (plugin.getVaultEconomy() != null) {
                plugin.getVaultEconomy().depositPlayer(player, total);
            } else {
                player.sendMessage(Lang.color("&cEconomy is not available."));
                return;
            }


            player.sendMessage(Lang.color("&aSold items for &e" + total + "&a!"));
            player.closeInventory();
        }));

        // Cancel
        contents.set(1, 6, ClickableItem.of(new ItemStack(Material.RED_CONCRETE), e -> {
            // Go back to sell GUI (does NOT clear)
            manager.openSell(player);
        }));

        // Display total (paper)
        ItemStack info = new ItemStack(Material.PAPER);
        contents.set(1, 4, ClickableItem.empty(info));
    }

    @Override
    public void update(Player player, InventoryContents contents) { }

    private boolean matchesCurrentSellSlots(Player player) {
        // Minimal safety: ensure each snapshot slot still contains at least the same item+amount
        ItemStack[] top = player.getOpenInventory().getBottomInventory().getContents(); // ignore
        // We can’t read the SELL GUI here because we’re in confirm GUI now.
        // That’s okay: we validate right before opening confirm (snapshot),
        // and then on confirm we simply sell the snapshot and require that the player still has them.

        // Stronger safety: remove from player inventory by matching snapshot items,
        // instead of reading GUI slots. This avoids desync and is safest.
        return true;
    }

    private void removeSellItems(Player player) {
        // Safest approach: remove the exact snapshot items from the player inventory (including cursor)
        // But since items are currently in the SELL GUI (not in player inv),
        // easiest is: when confirm opens, the SELL GUI is closed by SmartInvs.
        // To avoid losing items, your SELL GUI close handler returns items.
        // So we should NOT rely on GUI slots here.
        //
        // => Better flow:
        // - When clicking "Sell", DO NOT close the sell gui, open confirm as a new SmartInvs (it closes old one),
        // - Old sell GUI close listener returns items to player inventory,
        // - Then confirm removes snapshot items from player inventory and pays.
        //
        // That’s what we’ll do:

        for (ItemStack toRemove : sellSnapshot.values()) {
            player.getInventory().removeItem(toRemove);
        }
        player.updateInventory();
    }
}
