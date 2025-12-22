// File: src/main/java/fr/elias/oreoEssentials/kits/KitsMenuSI.java
package fr.elias.oreoEssentials.kits;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.util.Lang;
import fr.elias.oreoEssentials.util.Sounds; // use string-based sound helper
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import fr.minuskube.inv.content.Pagination;
import fr.minuskube.inv.content.SlotIterator;
import fr.minuskube.inv.content.SlotPos;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Kits GUI using SmartInvs.
 * - Left-click: claim
 * - Right-click: preview (works even when kits feature is disabled)
 */
public class KitsMenuSI implements InventoryProvider {

    // Simple toggles (no config dependency)
    private static final boolean PREVIEW_ENABLED      = true;
    private static final boolean SHOW_CMD_IN_PREVIEW  = true;
    private static final int     PREVIEW_ROWS         = 6;

    private final OreoEssentials plugin;
    private final KitsManager manager;

    public KitsMenuSI(OreoEssentials plugin, KitsManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    /** Open the SmartInvs menu for a player. */
    public static void open(OreoEssentials plugin, KitsManager manager, Player player) {
        int rows = Math.max(1, Math.min(6, manager.getMenuRows()));
        boolean on = manager.isEnabled();

        SmartInventory.builder()
                .id("oreo_kits_menu")
                .title(on ? manager.getMenuTitle() : "§8" + manager.getMenuTitle())
                .size(rows, 9)
                .provider(new KitsMenuSI(plugin, manager))
                .manager(plugin.getInvManager())
                .build()
                .open(player);
    }

    @Override
    public void init(Player player, InventoryContents contents) {
        final boolean featureOn = manager.isEnabled();

        // Background fill
        if (manager.isMenuFill()) {
            Material m = Material.matchMaterial(manager.getFillMaterial());
            if (m == null) m = Material.GRAY_STAINED_GLASS_PANE;
            ItemStack filler = new ItemStack(m);
            ItemMeta fim = filler.getItemMeta();
            if (fim != null) { fim.setDisplayName(" "); filler.setItemMeta(fim); }
            contents.fill(ClickableItem.empty(filler));
        }

        // Build kit buttons
        List<ClickableItem> buttons = new ArrayList<>();
        List<Kit> kitList = new ArrayList<>(manager.getKits().values()); // stable ordering

        for (Kit kit : kitList) {
            ItemStack icon = kit.getIcon() != null ? kit.getIcon().clone() : new ItemStack(Material.CHEST);
            ItemMeta meta = icon.getItemMeta();

            long left = manager.getSecondsLeft(player, kit);

            // Lore text from lang.yml
            String loreKey = (left > 0 ? "kits.gui.lore.on-cooldown" : "kits.gui.lore.claimable");
            List<String> loreLines = new ArrayList<>();

            if (!featureOn) {
                loreLines.add("§7Status: §cDISABLED");
                if (PREVIEW_ENABLED) loreLines.add("§7Right-click: §bPreview");
            } else {
                String loreStr = Lang.msg(
                        loreKey,
                        Map.of(
                                "kit_name", kit.getDisplayName(),
                                "cooldown_left", Lang.timeHuman(left),
                                "cooldown_left_raw", String.valueOf(left)
                        ),
                        player
                );
                if (!loreStr.isEmpty()) {
                    for (String line : loreStr.split("\n")) loreLines.add(line);
                }
                loreLines.add(" ");
                loreLines.add("§7Left-click: §aClaim");
                if (PREVIEW_ENABLED) loreLines.add("§7Right-click: §bPreview");
            }

            if (meta != null) {
                meta.setDisplayName(featureOn ? kit.getDisplayName() : "§8" + kit.getDisplayName());
                meta.setLore(loreLines);
                icon.setItemMeta(meta);
            }

            buttons.add(ClickableItem.of(icon, e -> {
                ClickType type = e.getClick();

                // Right-click → preview (even when disabled)
                if (PREVIEW_ENABLED && (type == ClickType.RIGHT || type == ClickType.SHIFT_RIGHT)) {
                    openPreview(player, kit);
                    return;
                }

                // Left-click → claim
                if (!manager.isEnabled()) {
                    player.sendMessage("§cKits are disabled.");
                    if (player.hasPermission("oreo.kits.admin")) {
                        player.sendMessage("§7Use §f/kits toggle §7to enable it.");
                    }
                    contents.inventory().open(player);
                    return;
                }

                long cdLeft = manager.getSecondsLeft(player, kit);

                // Cooldown gate (unless bypass)
                if (cdLeft > 0 && !player.hasPermission("oreo.kit.bypasscooldown")) {
                    if (Lang.getBool("kits.gui.sounds.denied.enabled", true)) {
                        final String raw = Lang.get("kits.gui.sounds.denied.sound", "minecraft:block.note_block.bass");
                        final float vol = (float) Lang.getDouble("kits.gui.sounds.denied.volume", 0.7);
                        final float pit = (float) Lang.getDouble("kits.gui.sounds.denied.pitch", 0.7);
                        Sounds.play(player, raw, vol, pit); // modern, no deprecations
                    }

                    Lang.send(
                            player,
                            "kits.cooldown",
                            "",
                            Map.of(
                                    "kit_name", kit.getDisplayName(),
                                    "cooldown_left", Lang.timeHuman(cdLeft),
                                    "cooldown_left_raw", String.valueOf(cdLeft)
                            )
                    );

                    contents.inventory().open(player); // keep UI responsive
                    return; // stop claim flow on cooldown
                }

                boolean handled = manager.claim(player, kit.getId());

                if (handled && Lang.getBool("kits.gui.sounds.claim.enabled", true)) {
                    final String raw = Lang.get("kits.gui.sounds.claim.sound", "minecraft:entity.experience_orb.pickup");
                    final float vol = (float) Lang.getDouble("kits.gui.sounds.claim.volume", 0.8);
                    final float pit = (float) Lang.getDouble("kits.gui.sounds.claim.pitch", 1.2);
                    Sounds.play(player, raw, vol, pit); // modern, no deprecations
                }

                contents.inventory().open(player);
            }));
        }

        int rows = contents.inventory().getRows();
        int cols = contents.inventory().getColumns();

        boolean anyFixed = manager.getKits().values().stream().anyMatch(k -> k.getSlot() != null);

        if (anyFixed) {
            // 1) Place fixed-slot kits first
            for (int i = 0; i < kitList.size(); i++) {
                Kit k = kitList.get(i);
                if (k.getSlot() == null) continue;

                int slot = k.getSlot();
                if (slot < 0 || slot >= rows * cols) continue;

                int r = slot / cols;
                int c = slot % cols;

                contents.set(SlotPos.of(r, c), buttons.get(i));
            }

            // 2) Flow the rest into any empty positions
            int next = 0;
            outer:
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    if (contents.get(SlotPos.of(r, c)).isPresent()) continue;

                    while (next < kitList.size()) {
                        Kit k = kitList.get(next);
                        ClickableItem ci = buttons.get(next);
                        next++;
                        if (k.getSlot() == null) {
                            contents.set(SlotPos.of(r, c), ci);
                            break;
                        }
                    }
                    if (next >= kitList.size()) break outer;
                }
            }
        } else {
            // No fixed slots: use Pagination + SlotIterator to auto-flow
            Pagination pagination = contents.pagination();
            pagination.setItems(buttons.toArray(new ClickableItem[0]));
            pagination.setItemsPerPage(rows * cols);

            SlotIterator it = contents.newIterator(SlotIterator.Type.HORIZONTAL, 0, 0);
            it.allowOverride(true);
            pagination.addToIterator(it);
        }

        // Admin toggle lever (bottom row, slot 5)
        if (rows >= 2 && player.hasPermission("oreo.kits.admin")) {
            final int bottom = rows - 1;
            ItemStack lever = new ItemStack(Material.LEVER);
            ItemMeta lm = lever.getItemMeta();
            boolean on = manager.isEnabled();
            if (lm != null) {
                lm.setDisplayName((on ? "§3Kits: §aENABLED" : "§3Kits: §cDISABLED"));
                lm.setLore(List.of(
                        on ? "§7Click to §cDISABLE" : "§7Click to §aENABLE",
                        "§8(Admin only)"
                ));
                lever.setItemMeta(lm);
            }
            contents.set(bottom, 5, ClickableItem.of(lever, e -> {
                boolean now = manager.toggleEnabled();
                player.sendMessage("§7Kits feature is now " + (now ? "§aENABLED" : "§cDISABLED"));
                KitsMenuSI.open(plugin, manager, player); // refresh
            }));
        }
    }

    @Override
    public void update(Player player, InventoryContents contents) {
        // Reopen on click; nothing needed per tick.
    }

    /* ------------------------ Preview GUI ------------------------ */

    private void openPreview(Player p, Kit kit) {
        if (!PREVIEW_ENABLED) return;

        SmartInventory.builder()
                .id("oreo_kits_preview_" + kit.getId())
                .title(("§6Preview: §e" + kit.getDisplayName()))
                .size(Math.max(1, Math.min(6, PREVIEW_ROWS)), 9)
                .provider(new PreviewProvider(kit))
                .manager(plugin.getInvManager())
                .build()
                .open(p);
    }

    private class PreviewProvider implements InventoryProvider {
        private final Kit kit;
        PreviewProvider(Kit kit) { this.kit = kit; }

        @Override
        public void init(Player p, InventoryContents contents) {
            // Back button
            ItemStack back = new ItemStack(Material.ARROW);
            ItemMeta bm = back.getItemMeta();
            if (bm != null) { bm.setDisplayName("§c← Back"); back.setItemMeta(bm); }
            contents.set(SlotPos.of(0, 0), ClickableItem.of(back, e -> KitsMenuSI.open(plugin, manager, p)));

            // Optional commands book
            if (SHOW_CMD_IN_PREVIEW && kit.getCommands() != null && !kit.getCommands().isEmpty()) {
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

            // Dump items into the grid (read-only)
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

        @Override public void update(Player player, InventoryContents contents) {}
    }
}
