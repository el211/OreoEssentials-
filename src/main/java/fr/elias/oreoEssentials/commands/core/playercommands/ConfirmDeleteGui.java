// File: src/main/java/fr/elias/oreoEssentials/commands/core/playercommands/ConfirmDeleteGui.java
package fr.elias.oreoEssentials.commands.core.playercommands;

import fr.elias.oreoEssentials.services.HomeService;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import fr.minuskube.inv.content.SlotPos;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import static org.bukkit.ChatColor.*;

import java.util.List;

public class ConfirmDeleteGui implements InventoryProvider {

    private final HomeService homes;
    private final String homeName;
    private final Runnable onConfirm;
    private final Runnable onCancel;

    private ConfirmDeleteGui(HomeService homes, String homeName, Runnable onConfirm, Runnable onCancel) {
        this.homes = homes;
        this.homeName = homeName;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
    }

    public static void open(Player p, HomeService homes, String homeName, Runnable onConfirm, Runnable onCancel) {
        SmartInventory.builder()
                .id("oreo:homes:confirm")
                .provider(new ConfirmDeleteGui(homes, homeName, onConfirm, onCancel))
                .size(3, 9)
                .title(ChatColor.DARK_RED + "Delete '" + homeName + "'?")
                .manager(fr.elias.oreoEssentials.OreoEssentials.get().getInvManager())
                .build()
                .open(p);

    }

    @Override
    public void init(Player p, InventoryContents contents) {
        // Text item center top
        contents.set(0, 4, fr.minuskube.inv.ClickableItem.empty(infoItem(homeName)));

        // YES (green concrete) at (1,3)
        contents.set(SlotPos.of(1, 3), fr.minuskube.inv.ClickableItem.of(
                actionItem(Material.GREEN_CONCRETE, GREEN + "Yes, delete"),
                e -> {
                    boolean ok = homes.delHome(p.getUniqueId(), homeName.toLowerCase());
                    if (ok) {
                        p.sendMessage(ChatColor.RED + "Deleted home " + ChatColor.YELLOW + homeName + ChatColor.RED + ".");
                    } else {
                        p.sendMessage(ChatColor.RED + "Failed to delete home " + ChatColor.YELLOW + homeName + ChatColor.RED + ".");
                    }
                    p.closeInventory();
                    if (onConfirm != null) onConfirm.run();
                }));

        // NO (red concrete) at (1,5)
        contents.set(SlotPos.of(1, 5), fr.minuskube.inv.ClickableItem.of(
                actionItem(Material.RED_CONCRETE, RED + "No, cancel"),
                e -> {
                    p.closeInventory();
                    if (onCancel != null) onCancel.run();
                }));
    }

    @Override
    public void update(Player player, InventoryContents contents) {}

    /* --------------- items --------------- */

    private ItemStack infoItem(String name) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {                                   // <-- add
            meta.setDisplayName(ChatColor.GOLD + "Delete Home");
            meta.setLore(List.of(ChatColor.GRAY + "Are you sure you want to delete",
                    ChatColor.YELLOW + name + ChatColor.GRAY + "?"));
            it.setItemMeta(meta);
        }                                                     // <-- add
        return it;
    }

    private ItemStack actionItem(Material mat, String title) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {                                   // <-- add
            meta.setDisplayName(title);
            it.setItemMeta(meta);
        }                                                     // <-- add
        return it;
    }

}
