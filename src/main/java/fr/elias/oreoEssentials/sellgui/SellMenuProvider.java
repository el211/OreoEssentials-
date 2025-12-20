package fr.elias.oreoEssentials.sellgui;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.util.Lang;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import fr.minuskube.inv.content.SlotPos;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SellMenuProvider implements InventoryProvider {

    private final OreoEssentials plugin;
    private final SellGuiManager manager;

    // sell area: rows 1..4, cols 1..7  (4x7 = 28 slots)
    private static boolean isSellSlot(int row, int col) {
        return row >= 1 && row <= 4 && col >= 1 && col <= 7;
    }

    public SellMenuProvider(OreoEssentials plugin, SellGuiManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public void init(Player player, InventoryContents contents) {
        // Background
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta gm = glass.getItemMeta();
        if (gm != null) {
            gm.setDisplayName(" ");
            gm.addItemFlags(ItemFlag.values());
            glass.setItemMeta(gm);
        }
        contents.fill(ClickableItem.empty(glass));

        // Mark sell slots editable + empty them visually
        for (int r = 1; r <= 4; r++) {
            for (int c = 1; c <= 7; c++) {
                SlotPos pos = SlotPos.of(r, c);
                contents.set(pos, ClickableItem.empty(null)); // let vanilla stack display
                contents.setEditable(pos, true);
            }
        }

            // SELL
        contents.set(5, 2, ClickableItem.of(
                manager.config().buildButton(
                        "sell",
                        Material.LIME_CONCRETE,
                        "<green><bold>SELL</bold></green>",
                        List.of("<gray>Sell all items placed in the sell area.</gray>")
                ),
                e -> {
                    double total = computeTotal(player);
                    if (total <= 0) {
                        player.sendMessage(manager.config().msgNothingSellable());
                        return;
                    }
                    openConfirm(player, total);
                }
        ));

            // CLOSE
        contents.set(5, 4, ClickableItem.of(
                manager.config().buildButton(
                        "close",
                        Material.BARRIER,
                        "<red><bold>CLOSE</bold></red>",
                        List.of("<gray>Close the menu (items are returned).</gray>")
                ),
                e -> player.closeInventory()
        ));

            // CLEAR
        contents.set(5, 6, ClickableItem.of(
                manager.config().buildButton(
                        "clear",
                        Material.RED_CONCRETE,
                        "<yellow><bold>CLEAR</bold></yellow>",
                        List.of("<gray>Return all items to your inventory.</gray>")
                ),
                e -> {
                    returnAllSellItems(player);
                    player.sendMessage(manager.config().msgReturnedAll());
                }
        ));

    }

    @Override
    public void update(Player player, InventoryContents contents) {
        // optional: could update a "Total" display every tick/second
    }

    private double computeTotal(Player player) {
        ItemStack[] top = player.getOpenInventory().getTopInventory().getContents();
        double total = 0D;

        for (int slot = 0; slot < top.length; slot++) {
            int row = slot / 9;
            int col = slot % 9;
            if (!isSellSlot(row, col)) continue;

            ItemStack item = top[slot];
            if (item == null || item.getType() == Material.AIR) continue;

            double unit = manager.config().getUnitPrice(item);
            if (unit < 0) continue;

            total += unit * item.getAmount();
        }
        return total;
    }

    private void openConfirm(Player player, double total) {
        // Snapshot items in sell area (so confirm GUI sells exactly those)
        Map<Integer, ItemStack> snapshot = snapshotSellSlots(player);

        SmartInventory.builder()
                .id("oreo-sellgui-confirm")
                .manager(plugin.getInventoryManager())
                .size(3, 9)
                .title(manager.config().confirmTitle())
                .provider(new SellConfirmProvider(plugin, manager, total, snapshot))
                .build()
                .open(player);
    }

    private Map<Integer, ItemStack> snapshotSellSlots(Player player) {
        Map<Integer, ItemStack> map = new HashMap<>();
        ItemStack[] top = player.getOpenInventory().getTopInventory().getContents();

        for (int slot = 0; slot < top.length; slot++) {
            int row = slot / 9;
            int col = slot % 9;
            if (!isSellSlot(row, col)) continue;

            ItemStack it = top[slot];
            if (it != null && it.getType() != Material.AIR) {
                map.put(slot, it.clone());
            }
        }
        return map;
    }

    private void returnAllSellItems(Player player) {
        returnSellItemsStatic(player);
    }

    /**
     * Called from SellGuiManager on InventoryCloseEvent (SmartInvs listener)
     */
    public static void returnSellItemsStatic(Player player) {
        ItemStack[] top = player.getOpenInventory().getTopInventory().getContents();

        for (int slot = 0; slot < top.length; slot++) {
            int row = slot / 9;
            int col = slot % 9;
            if (!isSellSlot(row, col)) continue;

            ItemStack it = top[slot];
            if (it == null || it.getType() == Material.AIR) continue;

            top[slot] = null;

            // Return to player inventory (drop leftovers)
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(it);
            leftover.values().forEach(drop -> player.getWorld().dropItemNaturally(player.getLocation(), drop));
        }

        player.getOpenInventory().getTopInventory().setContents(top);
        player.updateInventory();
    }

    private static ItemStack button(Material material, String nameLegacy, List<String> loreLegacy) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Lang.color(nameLegacy));

            if (loreLegacy != null && !loreLegacy.isEmpty()) {
                meta.setLore(loreLegacy.stream().map(Lang::color).toList());
            }

            // Optional: hide extra tooltip noise
            meta.addItemFlags(ItemFlag.values());

            item.setItemMeta(meta);
        }
        return item;
    }
}
