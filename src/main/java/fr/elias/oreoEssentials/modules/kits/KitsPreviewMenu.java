// File: src/main/java/fr/elias/oreoEssentials/kits/KitsPreviewMenu.java
package fr.elias.oreoEssentials.modules.kits;

import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import fr.minuskube.inv.content.SlotPos;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;


public class KitsPreviewMenu implements InventoryProvider {

    private final KitsManager manager;
    private final Kit kit;

    public KitsPreviewMenu(KitsManager manager, Kit kit) {
        this.manager = manager;
        this.kit = kit;
    }

    public static void open(KitsManager manager, Player p, Kit kit) {
        int rows = Math.max(1, Math.min(6, manager.kitsCfg()
                .getInt("menu.preview.rows", 6)));
        String title = manager.kitsCfg()
                .getString("menu.preview.title", "&6Preview: &e%kit_name%")
                .replace("&", "§")
                .replace("%kit_name%", kit.getDisplayName());

        SmartInventory.builder()
                .id("oreo_kits_preview_" + kit.getId())
                .size(rows, 9)
                .title(title)
                .provider(new KitsPreviewMenu(manager, kit))
                .manager(manager.getPlugin().getInvManager())
                .build()
                .open(p);
    }

    @Override
    public void init(Player p, InventoryContents contents) {
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta bm = back.getItemMeta();
        if (bm != null) {
            bm.setDisplayName("§c← Back");
            back.setItemMeta(bm);
        }
        contents.set(SlotPos.of(0, 0), ClickableItem.of(back, e ->
                KitsMenuSI.open(manager.getPlugin(), manager, p)));

        // Optional commands book
        if (manager.kitsCfg().getBoolean("menu.preview.show-commands", true)
                && kit.getCommands() != null && !kit.getCommands().isEmpty()) {
            ItemStack book = new ItemStack(Material.BOOK);
            ItemMeta im = book.getItemMeta();
            if (im != null) {
                im.setDisplayName("§bThis kit runs:");
                List<String> lore = new ArrayList<>();
                for (String c : kit.getCommands()) lore.add("§7• §f" + c);
                im.setLore(lore);
                book.setItemMeta(im);
            }
            contents.set(SlotPos.of(0, 8), ClickableItem.empty(book));
        }

        int rows = contents.inventory().getRows();
        int cols = contents.inventory().getColumns();
        int r = 1, c = 0;
        for (ItemStack it : kit.getItems()) {
            if (it == null) continue;
            contents.set(SlotPos.of(r, c), ClickableItem.empty(it.clone()));
            c++;
            if (c >= cols) { c = 0; r++; if (r >= rows) break; }
        }
    }

    @Override
    public void update(Player player, InventoryContents contents) {}
}