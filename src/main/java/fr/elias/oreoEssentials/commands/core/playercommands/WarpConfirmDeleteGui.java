// File: src/main/java/fr/elias/oreoEssentials/commands/core/playercommands/WarpConfirmDeleteGui.java
package fr.elias.oreoEssentials.commands.core.playercommands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.services.WarpDirectory;
import fr.elias.oreoEssentials.services.WarpService;
import fr.elias.oreoEssentials.util.Lang;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import fr.minuskube.inv.content.SlotPos;
import org.bukkit.ChatColor;
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
        SmartInventory.builder()
                .id("oreo:warps:confirm:" + warpName)
                .provider(new WarpConfirmDeleteGui(warps, warpName, onConfirm, onCancel))
                .size(3, 9)
                .title(ChatColor.DARK_RED + "Delete '" + warpName + "'?")
                .manager(OreoEssentials.get().getInvManager())
                .build()
                .open(p);
    }

    @Override
    public void init(Player p, InventoryContents contents) {
        contents.set(0, 4, ClickableItem.empty(infoItem(warpName)));

        contents.set(SlotPos.of(1, 3), ClickableItem.of(
                actionItem(Material.GREEN_CONCRETE, ChatColor.GREEN + "Yes, delete"),
                e -> {
                    boolean ok = warps.delWarp(warpName);
                    if (ok) {
                        // clear directory metadata too
                        WarpDirectory dir = OreoEssentials.get().getWarpDirectory();
                        if (dir != null) {
                            try { dir.deleteWarp(warpName); } catch (Throwable ignored) {}
                        }

                        Lang.send(
                                p,
                                "warp.delete.success",
                                null,
                                Map.of("warp", warpName)
                        );

                        p.closeInventory();
                        if (onConfirm != null) onConfirm.run();
                    } else {
                        Lang.send(
                                p,
                                "warp.delete.failed",
                                null,
                                Map.of("warp", warpName)
                        );

                        p.closeInventory();
                        if (onCancel != null) onCancel.run();
                    }
                }
        ));

        contents.set(SlotPos.of(1, 5), ClickableItem.of(
                actionItem(Material.RED_CONCRETE, ChatColor.RED + "No, cancel"),
                e -> {
                    p.closeInventory();
                    if (onCancel != null) onCancel.run();
                }
        ));
    }

    @Override public void update(Player player, InventoryContents contents) {}

    private ItemStack infoItem(String name) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(ChatColor.GOLD + "Delete Warp");
        m.setLore(List.of(
                ChatColor.GRAY + "Are you sure you want to delete",
                ChatColor.YELLOW + name + ChatColor.GRAY + "?"
        ));
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
