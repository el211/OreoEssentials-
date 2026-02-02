// File: src/main/java/fr/elias/oreoEssentials/commands/core/playercommands/WarpConfirmDeleteGui.java
package fr.elias.oreoEssentials.modules.warps.gui;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.warps.rabbit.WarpDirectory;
import fr.elias.oreoEssentials.modules.warps.WarpService;
import fr.elias.oreoEssentials.util.Lang;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import fr.minuskube.inv.content.SlotPos;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;

public class WarpConfirmDeleteGui implements InventoryProvider {

    private final WarpService warps;
    private final String warpName;
    private final Runnable onConfirm, onCancel;

    private WarpConfirmDeleteGui(WarpService warps, String warpName, Runnable onConfirm, Runnable onCancel) {
        this.warps = warps;
        this.warpName = warpName;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
    }

    public static void open(Player p, WarpService warps, String warpName, Runnable onConfirm, Runnable onCancel) {
        String title = Lang.msgWithDefault(
                "warp.delete.title",
                "<dark_red>Delete '%warp%'?</dark_red>",
                Map.of("warp", warpName),
                p
        );

        SmartInventory.builder()
                .id("oreo:warps:confirm:" + warpName)
                .provider(new WarpConfirmDeleteGui(warps, warpName, onConfirm, onCancel))
                .size(3, 9)
                .title(title)
                .manager(OreoEssentials.get().getInvManager())
                .build()
                .open(p);
    }

    @Override
    public void init(Player p, InventoryContents contents) {
        contents.set(0, 4, ClickableItem.empty(infoItem(p, warpName)));

        // Yes button
        String yesName = Lang.msgWithDefault(
                "warp.delete.yes",
                "<green>Yes, delete</green>",
                p
        );

        contents.set(SlotPos.of(1, 3), ClickableItem.of(
                actionItem(Material.GREEN_CONCRETE, yesName),
                e -> {
                    boolean ok = warps.delWarp(warpName);
                    if (ok) {
                        // clear directory metadata too
                        WarpDirectory dir = OreoEssentials.get().getWarpDirectory();
                        if (dir != null) {
                            try {
                                dir.deleteWarp(warpName);
                            } catch (Throwable ignored) {}
                        }

                        Lang.send(p, "warp.delete.success",
                                "<red>Deleted warp <yellow>%warp%</yellow>.</red>",
                                Map.of("warp", warpName));

                        p.closeInventory();
                        if (onConfirm != null) onConfirm.run();
                    } else {
                        Lang.send(p, "warp.delete.failed",
                                "<red>Failed to delete warp <yellow>%warp%</yellow>.</red>",
                                Map.of("warp", warpName));

                        p.closeInventory();
                        if (onCancel != null) onCancel.run();
                    }
                }
        ));

        String noName = Lang.msgWithDefault(
                "warp.delete.no",
                "<red>No, cancel</red>",
                p
        );

        contents.set(SlotPos.of(1, 5), ClickableItem.of(
                actionItem(Material.RED_CONCRETE, noName),
                e -> {
                    p.closeInventory();
                    if (onCancel != null) onCancel.run();
                }
        ));
    }

    @Override
    public void update(Player player, InventoryContents contents) {}

    private ItemStack infoItem(Player p, String name) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta();

        String title = Lang.msgWithDefault(
                "warp.delete.info.title",
                "<gold>Delete Warp</gold>",
                p
        );

        String line1 = Lang.msgWithDefault(
                "warp.delete.info.lore.0",
                "<gray>Are you sure you want to delete</gray>",
                p
        );

        String line2 = Lang.msgWithDefault(
                "warp.delete.info.lore.1",
                "<yellow>%warp%</yellow><gray>?</gray>",
                Map.of("warp", name),
                p
        );

        m.setDisplayName(title);
        m.setLore(List.of(line1, line2));
        it.setItemMeta(m);
        return it;
    }

    private ItemStack actionItem(Material mat, String title) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(title);
        it.setItemMeta(m);
        return it;
    }
}