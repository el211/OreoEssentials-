package fr.elias.oreoEssentials.modules.homes;

import fr.elias.oreoEssentials.modules.homes.home.HomeService;
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
        String title = Lang.msgLegacy("homes.delete.title",
                "<dark_red>Delete '%home%'?</dark_red>",
                Map.of("home", homeName),
                p);

        SmartInventory.builder()
                .id("oreo:homes:confirm")
                .provider(new ConfirmDeleteGui(homes, homeName, onConfirm, onCancel))
                .size(3, 9)
                .title(title)
                .manager(fr.elias.oreoEssentials.OreoEssentials.get().getInvManager())
                .build()
                .open(p);
    }

    @Override
    public void init(Player p, InventoryContents contents) {
        contents.set(0, 4, fr.minuskube.inv.ClickableItem.empty(infoItem(p, homeName)));

        contents.set(SlotPos.of(1, 3), fr.minuskube.inv.ClickableItem.of(
                actionItem(p, Material.GREEN_CONCRETE,
                        Lang.msgLegacy("homes.delete.yes", "<green>Yes, delete</green>", p)),
                e -> {
                    boolean ok = homes.delHome(p.getUniqueId(), homeName.toLowerCase());

                    if (ok) {
                        Lang.send(p, "homes.delete.success",
                                "<red>Deleted home <yellow>%home%</yellow>.</red>",
                                Map.of("home", homeName));
                    } else {
                        Lang.send(p, "homes.delete.failed",
                                "<red>Failed to delete home <yellow>%home%</yellow>.</red>",
                                Map.of("home", homeName));
                    }

                    p.closeInventory();
                    if (onConfirm != null) onConfirm.run();
                }));

        contents.set(SlotPos.of(1, 5), fr.minuskube.inv.ClickableItem.of(
                actionItem(p, Material.RED_CONCRETE,
                        Lang.msgLegacy("homes.delete.no", "<red>No, cancel</red>", p)),
                e -> {
                    p.closeInventory();
                    if (onCancel != null) onCancel.run();
                }));
    }

    @Override
    public void update(Player player, InventoryContents contents) {
        // No updates needed for this static confirmation GUI
    }


    private ItemStack infoItem(Player p, String name) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            // Use Lang for display name
            String displayName = Lang.msgLegacy("homes.delete.info.title",
                    "<gold>Delete Home</gold>", p);
            meta.setDisplayName(displayName);

            // Use Lang for lore - note the variable replacement for home name
            List<String> lore = List.of(
                    Lang.msgLegacy("homes.delete.info.lore.0",
                            "<gray>Are you sure you want to delete</gray>", p),
                    Lang.msgLegacy("homes.delete.info.lore.1",
                            "<yellow>%home%</yellow><gray>?</gray>",
                            Map.of("home", name),
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