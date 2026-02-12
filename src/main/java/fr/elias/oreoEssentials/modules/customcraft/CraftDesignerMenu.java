package fr.elias.oreoEssentials.modules.customcraft;

import fr.elias.oreoEssentials.util.Lang;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.InventoryListener;
import fr.minuskube.inv.InventoryManager;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import fr.minuskube.inv.content.SlotPos;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.*;

public final class CraftDesignerMenu implements InventoryProvider {
    private final CustomCraftingService service;
    private final String recipeName;
    private final ItemStack[] grid = new ItemStack[9];
    private ItemStack result = null;
    private boolean shapeless = false;
    private String permission = null;
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SEC = LegacyComponentSerializer.legacySection();

    private CraftDesignerMenu(Plugin plugin, CustomCraftingService service, String recipeName) {
        this.service = service;
        this.recipeName = recipeName;
        Arrays.fill(grid, null);
    }

    public static SmartInventory build(Plugin plugin, InventoryManager invMgr,
                                       CustomCraftingService service, String recipeName) {
        CraftDesignerMenu menu = new CraftDesignerMenu(plugin, service, recipeName);

        String title = i18nLegacy("customcraft.gui.title",
                "<#00bcd4><bold>OreoCraft</bold></#00bcd4> <gray>—</gray> <white>%name%</white>",
                Map.of("name", recipeName), null);

        return SmartInventory.builder()
                .id("oecraft:" + recipeName)
                .provider(menu)
                .size(5, 9)
                .title(title)
                .manager(invMgr)
                .closeable(true)
                .listener(new InventoryListener<>(InventoryCloseEvent.class, e -> {
                    if (e.getPlayer() instanceof Player p) menu.saveOnClose(p);
                }))
                .build();
    }

    @Override
    public void init(Player player, InventoryContents contents) {
        var filler = ui(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int r = 0; r < 5; r++) {
            for (int c = 0; c < 9; c++) {
                contents.set(r, c, ClickableItem.empty(filler));
            }
        }

        drawDeleteButton(contents, player);

        contents.set(0, 2, ClickableItem.empty(ui(Material.BOOK,
                i18nLegacy("customcraft.gui.labels.ingredients",
                        "<yellow>Ingredients (3×3)</yellow>",
                        Map.of(), player))));
        contents.set(0, 6, ClickableItem.empty(ui(Material.EMERALD,
                i18nLegacy("customcraft.gui.labels.result",
                        "<green>Result →</green>",
                        Map.of(), player))));

        service.get(recipeName).ifPresent(r -> {
            ItemStack[] g = r.getGrid();
            for (int i = 0; i < 9; i++) {
                grid[i] = (g[i] == null || g[i].getType().isAir()) ? null : g[i].clone();
            }
            result = (r.getResult() == null || r.getResult().getType().isAir()) ? null : r.getResult().clone();
            shapeless = r.isShapeless();
            permission = r.getPermission();
        });

        drawModeToggle(contents);
        drawPermissionToggle(contents);

        for (int rr = 0; rr < 3; rr++) {
            for (int cc = 0; cc < 3; cc++) {
                int idx = rr * 3 + cc;
                SlotPos pos = SlotPos.of(1 + rr, 2 + cc);
                redrawGridCell(contents, pos, idx);
            }
        }

        redrawResult(contents);
    }

    @Override
    public void update(Player player, InventoryContents contents) {
    }


    private void drawDeleteButton(InventoryContents contents, Player player) {
        ItemStack it = new ItemStack(Material.BARRIER);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.setDisplayName(i18nLegacy("customcraft.gui.delete.name",
                    "<red>Delete this recipe</red>",
                    Map.of(), player));

            List<String> lore = langList("customcraft.gui.delete.lore",
                    List.of(
                            "<gray>Left-Click:</gray> <white>Info</white>",
                            "<gray>Right-Click:</gray> <red>CONFIRM deletion</red>"
                    ));
            m.setLore(lore.stream().map(CraftDesignerMenu::mmInlineToLegacy).toList());
            it.setItemMeta(m);
        }

        contents.set(0, 0, ClickableItem.of(it, (InventoryClickEvent e) -> {
            Player p = (Player) e.getWhoClicked();

            if (e.isRightClick()) {
                boolean ok = service.delete(recipeName);
                if (ok) {
                    Component msg = MM.deserialize(
                            Lang.get("customcraft.messages.deleted",
                                            "<green>Deleted recipe <yellow>%name%</yellow>.</green>")
                                    .replace("%name%", recipeName)
                    );
                    p.sendMessage(msg);
                } else {
                    Component msg = MM.deserialize(
                            Lang.get("customcraft.messages.invalid",
                                    "<red>Invalid recipe.</red>")
                    );
                    p.sendMessage(msg);
                }
                p.closeInventory();
            } else {
                Component msg = MM.deserialize(
                        Lang.get("customcraft.messages.delete-hint",
                                        "<yellow>Tip:</yellow> <bold>Right-click</bold> the barrier to delete <red>%name%</red>.")
                                .replace("%name%", recipeName)
                );
                p.sendMessage(msg);
            }
        }));
    }

    private void drawModeToggle(InventoryContents contents) {
        ItemStack it = new ItemStack(shapeless ? Material.SLIME_BALL : Material.REDSTONE);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            String nameKey = shapeless
                    ? "customcraft.gui.mode.shapeless-name"
                    : "customcraft.gui.mode.shaped-name";
            String nameDef = shapeless
                    ? "<green>Mode: SHAPELESS</green>"
                    : "<aqua>Mode: SHAPED</aqua>";
            String name = i18nLegacy(nameKey, nameDef, Map.of(), null);

            List<String> lore = new ArrayList<>();
            lore.add(mmInlineToLegacy(
                    Lang.get("customcraft.gui.mode.lore-common",
                            "<gray>Click to toggle.</gray>")
            ));
            lore.add(mmInlineToLegacy(
                    Lang.get(shapeless
                                    ? "customcraft.gui.mode.lore-shapeless"
                                    : "customcraft.gui.mode.lore-shaped",
                            shapeless
                                    ? "<gray>Order doesn't matter.</gray>"
                                    : "<gray>Layout matters.</gray>")
            ));

            m.setDisplayName(name);
            m.setLore(lore);
            it.setItemMeta(m);
        }

        contents.set(0, 4, ClickableItem.of(it, (InventoryClickEvent e) -> {
            shapeless = !shapeless;
            drawModeToggle(contents);
        }));
    }

    private void drawPermissionToggle(InventoryContents contents) {
        boolean hasPerm = permission != null && !permission.isBlank();
        ItemStack it = new ItemStack(hasPerm ? Material.NAME_TAG : Material.PAPER);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            if (hasPerm) {
                m.setDisplayName(i18nLegacy("customcraft.gui.permission.required-name",
                        "<gold>Permission required</gold>",
                        Map.of(), null));

                List<String> lore = langList("customcraft.gui.permission.required-lore",
                        List.of(
                                "<gray>Node:</gray> <yellow>%permission%</yellow>",
                                "<gray>Click to make it</gray> <green>Public</green>"
                        ));
                final String p = permission;
                m.setLore(lore.stream()
                        .map(s -> mmInlineToLegacy(s.replace("%permission%", p)))
                        .toList());
            } else {
                m.setDisplayName(i18nLegacy("customcraft.gui.permission.public-name",
                        "<green>Public</green>",
                        Map.of(), null));

                List<String> lore = langList("customcraft.gui.permission.public-lore",
                        List.of(
                                "<gray>No permission required.</gray>",
                                "<gray>Click to require a default node.</gray>"
                        ));
                m.setLore(lore.stream().map(CraftDesignerMenu::mmInlineToLegacy).toList());
            }
            it.setItemMeta(m);
        }

        contents.set(0, 8, ClickableItem.of(it, (InventoryClickEvent e) -> {
            boolean nowHas = permission != null && !permission.isBlank();
            permission = nowHas ? null : "oreo.craft.use." + recipeName.toLowerCase(Locale.ROOT);
            drawPermissionToggle(contents);
        }));
    }

    private void redrawGridCell(InventoryContents contents, SlotPos pos, int idx) {
        ItemStack display = (grid[idx] == null) ? new ItemStack(Material.AIR) : grid[idx].clone();

        contents.set(pos, ClickableItem.of(display, (InventoryClickEvent e) -> {
            ItemStack cursor = e.getWhoClicked().getItemOnCursor();
            ItemStack previous = grid[idx];

            grid[idx] = (cursor == null || cursor.getType().isAir()) ? null : cursor.clone();

            ItemStack newDisplay = (grid[idx] == null) ? new ItemStack(Material.AIR) : grid[idx].clone();
            contents.set(pos, ClickableItem.of(newDisplay, (InventoryClickEvent ev) -> {
                redrawGridCell(contents, pos, idx);
            }));

            e.getWhoClicked().setItemOnCursor(previous == null ? new ItemStack(Material.AIR) : previous.clone());
        }));
    }

    private void redrawResult(InventoryContents contents) {
        ItemStack display = (result == null) ? new ItemStack(Material.AIR) : result.clone();

        contents.set(2, 7, ClickableItem.of(display, (InventoryClickEvent e) -> {
            ItemStack cursor = e.getWhoClicked().getItemOnCursor();
            ItemStack old = result;

            result = (cursor == null || cursor.getType().isAir()) ? null : cursor.clone();

            ItemStack newDisplay = (result == null) ? new ItemStack(Material.AIR) : result.clone();
            contents.set(2, 7, ClickableItem.of(newDisplay, (InventoryClickEvent ev) -> {
                redrawResult(contents);
            }));

            e.getWhoClicked().setItemOnCursor(old == null ? new ItemStack(Material.AIR) : old.clone());
        }));
    }

    private ItemStack[] snapshotGrid() {
        ItemStack[] out = new ItemStack[9];
        for (int i = 0; i < 9; i++) {
            out[i] = (grid[i] == null) ? null : grid[i].clone();
        }
        return out;
    }

    private void saveOnClose(Player player) {
        CustomRecipe rec = new CustomRecipe(
                recipeName,
                snapshotGrid(),
                result == null ? null : result.clone(),
                shapeless,
                permission
        );

        boolean ok = service.saveAndRegister(rec);
        if (ok) {
            String mode = shapeless
                    ? Lang.get("customcraft.format.mode.shapeless", "Shapeless")
                    : Lang.get("customcraft.format.mode.shaped", "Shaped");

            String permNote = (permission == null || permission.isBlank())
                    ? Lang.get("customcraft.format.permission.note-public", "<gray>(public)</gray>")
                    : Lang.get("customcraft.format.permission.note-required", "<gold>perm</gold> <yellow>%permission%</yellow>")
                    .replace("%permission%", permission);

            String msg = Lang.get("customcraft.messages.saved",
                            "<green>Recipe <yellow>%name%</yellow> has been saved (<white>%mode%</white>) %perm_note%.</green>")
                    .replace("%name%", recipeName)
                    .replace("%mode%", mode)
                    .replace("%perm_note%", permNote);

            player.sendMessage(MM.deserialize(applyPapi(player, msg)));
        } else {
            player.sendMessage(MM.deserialize(
                    Lang.get("customcraft.messages.invalid",
                            "<red>Invalid recipe. You need a result item and at least one ingredient.</red>")
            ));
        }
    }


    private static ItemStack ui(Material m, String name) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            it.setItemMeta(meta);
        }
        return it;
    }

    private static List<String> langList(String basePath, List<String> def) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            String v = Lang.get(basePath + "." + i, null);
            if (v == null) break;
            out.add(v);
        }
        if (out.isEmpty() && def != null) {
            out.addAll(def);
        }
        return out;
    }

    private static String i18nLegacy(String key, String def, Map<String, String> vars, Player p) {
        String raw = Lang.get(key, def);
        if (vars != null) {
            for (var e : vars.entrySet()) {
                raw = raw.replace("%" + e.getKey() + "%", e.getValue());
            }
        }
        raw = applyPapi(p, raw);
        return LEGACY_SEC.serialize(MM.deserialize(raw));
    }

    private static String mmInlineToLegacy(String raw) {
        return LEGACY_SEC.serialize(MM.deserialize(raw == null ? "" : raw));
    }

    private static String applyPapi(Player p, String raw) {
        if (raw == null) return "";
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                return PlaceholderAPI.setPlaceholders(p, raw);
            }
        } catch (Throwable ignored) {}
        return raw;
    }
}