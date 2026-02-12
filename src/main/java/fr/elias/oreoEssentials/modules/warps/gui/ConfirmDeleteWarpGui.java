package fr.elias.oreoEssentials.modules.warps.gui;

import fr.elias.oreoEssentials.modules.warps.WarpService;
import fr.elias.oreoEssentials.util.Lang;
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

public class ConfirmDeleteWarpGui implements InventoryProvider {

    private final WarpService warps;
    private final String warpName;
    private final Runnable onConfirm;
    private final Runnable onCancel;

    public ConfirmDeleteWarpGui(WarpService warps, String warpName, Runnable onConfirm, Runnable onCancel) {
        this.warps = warps;
        this.warpName = warpName;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
    }

    public static void open(Player p, WarpService warps, String warpName, Runnable onConfirm, Runnable onCancel) {
        String title = Lang.msgLegacy("warps.delete.title",
                "<dark_red>Delete '%warp%'?</dark_red>",
                Map.of("warp", warpName),
                p);

        SmartInventory.builder()
                .id("oreo:warps:confirm")
                .provider(new ConfirmDeleteWarpGui(warps, warpName, onConfirm, onCancel))
                .size(3, 9)
                .title(title)
                .manager(fr.elias.oreoEssentials.OreoEssentials.get().getInvManager())
                .build()
                .open(p);
    }

    @Override
    public void init(Player p, InventoryContents contents) {
        contents.set(0, 4, fr.minuskube.inv.ClickableItem.empty(infoItem(p, warpName)));

        contents.set(SlotPos.of(1, 3), fr.minuskube.inv.ClickableItem.of(
                actionItem(p, Material.GREEN_CONCRETE,
                        Lang.msgLegacy("warps.delete.yes", "<green>Yes, delete</green>", p)),
                e -> {
                    boolean ok = warps.delWarp(warpName.toLowerCase());

                    if (ok) {
                        Lang.send(p, "warps.delete.success",
                                "<red>Deleted warp <yellow>%warp%</yellow>.</red>",
                                Map.of("warp", warpName));
                    } else {
                        Lang.send(p, "warps.delete.failed",
                                "<red>Failed to delete warp <yellow>%warp%</yellow>.</red>",
                                Map.of("warp", warpName));
                    }

                    p.closeInventory();
                    if (onConfirm != null) onConfirm.run();
                }));

        contents.set(SlotPos.of(1, 5), fr.minuskube.inv.ClickableItem.of(
                actionItem(p, Material.RED_CONCRETE,
                        Lang.msgLegacy("warps.delete.no", "<red>No, cancel</red>", p)),
                e -> {
                    p.closeInventory();
                    if (onCancel != null) onCancel.run();
                }));
    }

    @Override
    public void update(Player player, InventoryContents contents) {
    }


    private ItemStack infoItem(Player p, String name) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            String displayName = Lang.msgLegacy("warps.delete.info.title",
                    "<gold>Delete Warp</gold>", p);
            meta.setDisplayName(displayName);

            List<String> lore = List.of(
                    Lang.msgLegacy("warps.delete.info.lore.0",
                            "<gray>Are you sure you want to delete</gray>", p),
                    Lang.msgLegacy("warps.delete.info.lore.1",
                            "<yellow>%warp%</yellow><gray>?</gray>",
                            Map.of("warp", name),
                            p)
            );
            meta.setLore(lore);
            it.setItemMeta(meta);
        }
        return it;
    }


    private ItemStack actionItem(Player p, Material mat, String title) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(title);
            it.setItemMeta(meta);
        }
        return it;
    }
}