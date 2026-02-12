package fr.elias.oreoEssentials.modules.customcraft;

import fr.elias.oreoEssentials.util.Lang;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.InventoryManager;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import fr.minuskube.inv.content.Pagination;
import fr.minuskube.inv.content.SlotIterator;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.*;

public final class RecipeListMenu implements InventoryProvider {
    private final Plugin plugin;
    private final InventoryManager invMgr;
    private final CustomCraftingService service;

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SEC = LegacyComponentSerializer.legacySection();

    private RecipeListMenu(Plugin plugin, InventoryManager invMgr, CustomCraftingService service) {
        this.plugin = plugin;
        this.invMgr = invMgr;
        this.service = service;
    }

    public static SmartInventory open(Player p, Plugin plugin, InventoryManager invMgr, CustomCraftingService service) {
        String title = i18nLegacy("customcraft.browse.title",
                "<#00bcd4><bold>OreoCraft</bold></#00bcd4> <gray>—</gray> <white>Recipes</white>",
                Map.of(), p);

        SmartInventory inv = SmartInventory.builder()
                .id("oecraft:browse")
                .provider(new RecipeListMenu(plugin, invMgr, service))
                .size(6, 9)
                .title(title)
                .manager(invMgr)
                .build();
        inv.open(p);
        return inv;
    }

    @Override
    public void init(Player player, InventoryContents contents) {
        ItemStack border = ui(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int r = 0; r < 6; r++) {
            for (int c = 0; c < 9; c++) {
                contents.set(r, c, ClickableItem.empty(border));
            }
        }

        contents.set(0, 4, ClickableItem.empty(ui(
                Material.BOOK,
                i18nLegacy("customcraft.browse.hint",
                        "<yellow>Click a recipe to edit</yellow>",
                        Map.of(), player)
        )));

        Pagination pag = contents.pagination();
        List<ClickableItem> items = new ArrayList<>();

        for (String name : service.allNames()) {
            ItemStack icon = new ItemStack(Material.CRAFTING_TABLE);
            ItemMeta m = icon.getItemMeta();
            if (m != null) {
                String mode = service.get(name)
                        .map(r -> r.isShapeless()
                                ? Lang.get("customcraft.format.mode.shapeless", "Shapeless")
                                : Lang.get("customcraft.format.mode.shaped", "Shaped"))
                        .orElse("?");

                String perm = service.getPermissionFor(name)
                        .orElse(Lang.get("customcraft.format.permission.none", "<gray>None</gray>"));

                m.setDisplayName(i18nLegacyInline("<aqua>" + name + "</aqua>"));

                List<String> loreTemplate = langList("customcraft.browse.icon-lore",
                        List.of(
                                "<gray>Mode:</gray> <white>%mode%</white>",
                                "<gray>Perm:</gray> <white>%permission_or_none%</white>",
                                "<green>Click to edit</green>"
                        ));

                List<String> lore = new ArrayList<>(loreTemplate.size());
                for (String line : loreTemplate) {
                    line = line.replace("%mode%", mode)
                            .replace("%permission_or_none%", perm);
                    lore.add(i18nLegacyInline(line));
                }
                m.setLore(lore);
                icon.setItemMeta(m);
            }

            items.add(ClickableItem.of(icon, (InventoryClickEvent e) -> {
                Player clicker = (Player) e.getWhoClicked();
                CraftDesignerMenu.build(plugin, invMgr, service, name).open(clicker);
            }));
        }

        pag.setItems(items.toArray(new ClickableItem[0]));
        pag.setItemsPerPage(28);

        SlotIterator si = contents.newIterator(SlotIterator.Type.HORIZONTAL, 1, 1);
        si.blacklist(1, 0); si.blacklist(1, 8);
        si.blacklist(2, 0); si.blacklist(2, 8);
        si.blacklist(3, 0); si.blacklist(3, 8);
        si.blacklist(4, 0); si.blacklist(4, 8);
        pag.addToIterator(si);

        contents.set(5, 3, ClickableItem.of(
                ui(Material.ARROW,
                        i18nLegacy("customcraft.browse.prev",
                                "<yellow>Previous</yellow>",
                                Map.of(), player)),
                (InventoryClickEvent e) -> {
                    Player p = (Player) e.getWhoClicked();
                    if (!pag.isFirst()) {
                        invMgr.getInventory(p).ifPresent(inv -> inv.open(p, pag.getPage() - 1));
                    }
                }));

        contents.set(5, 5, ClickableItem.of(
                ui(Material.ARROW,
                        i18nLegacy("customcraft.browse.next",
                                "<yellow>Next</yellow>",
                                Map.of(), player)),
                (InventoryClickEvent e) -> {
                    Player p = (Player) e.getWhoClicked();
                    if (!pag.isLast()) {
                        invMgr.getInventory(p).ifPresent(inv -> inv.open(p, pag.getPage() + 1));
                    }
                }));
    }

    @Override
    public void update(Player player, InventoryContents contents) {
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

    private static String i18nLegacyInline(String raw) {
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