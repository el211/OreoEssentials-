package fr.elias.oreoEssentials.modules.sellgui.provider;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.sellgui.manager.SellGuiManager;
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
            if (!matchesCurrentSellSlots(player)) {
                player.sendMessage(Lang.color("&cSell contents changed. Please try again."));
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


            player.sendMessage(Lang.color("&aSold items for &e" + total + "&a!"));
            player.closeInventory();
        }));

        contents.set(1, 6, ClickableItem.of(new ItemStack(Material.RED_CONCRETE), e -> {
            manager.openSell(player);
        }));

        ItemStack info = new ItemStack(Material.PAPER);
        contents.set(1, 4, ClickableItem.empty(info));
    }

    @Override
    public void update(Player player, InventoryContents contents) { }

    private boolean matchesCurrentSellSlots(Player player) {
        ItemStack[] top = player.getOpenInventory().getBottomInventory().getContents(); // ignore

        return true;
    }

    private void removeSellItems(Player player) {


        for (ItemStack toRemove : sellSnapshot.values()) {
            player.getInventory().removeItem(toRemove);
        }
        player.updateInventory();
    }
}
