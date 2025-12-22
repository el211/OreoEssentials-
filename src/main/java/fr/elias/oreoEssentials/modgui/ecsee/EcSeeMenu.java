package fr.elias.oreoEssentials.modgui.ecsee;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.enderchest.EnderChestService;
import fr.elias.oreoEssentials.modgui.util.ItemBuilder;
import fr.elias.oreoEssentials.util.Lang;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.UUID;

public class EcSeeMenu implements InventoryProvider {

    private final OreoEssentials plugin;
    private final UUID targetId;

    public EcSeeMenu(OreoEssentials plugin, UUID targetId) {
        this.plugin = plugin;
        this.targetId = targetId;
    }

// File: src/main/java/your/package/YourClass.java

    @Override
    public void init(Player viewer, InventoryContents c) {
        EnderChestService ecs = plugin.getEnderChestService();
        if (ecs == null) {
            viewer.closeInventory();

            // ✅ FIX: use a valid overload (no bogus 4th-arg Player)
            // Option 1: rely on lang.yml only
            Lang.send(viewer, "modgui.ecsee.service-missing");

            // Option 2 (if you want a hardcoded fallback too), pick ONE of these:
            // Lang.send(viewer, "modgui.ecsee.service-missing", "§cEnderChest service is missing.");
            // Lang.send(viewer, "modgui.ecsee.service-missing", "", Map.of()); // placeholders supported if needed

            return;
        }

        // Resolve target name (network-wide)
        String targetName = resolveName(targetId);

        // Determine how many slots this player has (offline aware)
        int allowed = ecs.resolveSlotsOffline(targetId);
        int rows    = Math.max(1, (int) Math.ceil(allowed / 9.0));

        // Load global/cross-server content
        ItemStack[] stored = ecs.loadFor(targetId, rows);

        // Fill allowed slots
        for (int i = 0; i < allowed; i++) {
            ItemStack item = (stored != null && i < stored.length) ? stored[i] : null;
            int row = i / 9;
            int col = i % 9;

            if (item == null || item.getType() == Material.AIR) {
                c.set(row, col, ClickableItem.empty(new ItemStack(Material.AIR)));
            } else {
                c.set(row, col, ClickableItem.of(item, e -> {
                    // staff can modify directly; sync on close
                }));
            }
        }

        // Fill locked slots with a generic barrier (visual only)
        ItemStack locked = createLockedBarrierItem(allowed);
        for (int i = allowed; i < 54; i++) {
            int row = i / 9;
            int col = i % 9;
            c.set(row, col, ClickableItem.empty(locked));
        }

        String titleName = Lang.get(
                "modgui.ecsee.title",
                "&bEditing EnderChest of &e%target%"
        ).replace("%target%", targetName);

        c.set(5, 4, ClickableItem.empty(
                new ItemBuilder(Material.ENDER_CHEST)
                        .name(titleName)
                        .build()
        ));
    }


    @Override
    public void update(Player player, InventoryContents contents) {
        // No live updates for now
    }

    /* =========================================================
       Sync back to cross-server EC (NOT vanilla!!) + log
       Called from PlayerActionsMenu InventoryClose listener.
       ========================================================= */

    public static void syncAndLog(OreoEssentials plugin,
                                  Player viewer,
                                  UUID targetId,
                                  Inventory inv) {
        EnderChestService ecs = plugin.getEnderChestService();
        if (ecs == null) return;

        int allowed = ecs.resolveSlotsOffline(targetId);
        int rows    = Math.max(1, (int) Math.ceil(allowed / 9.0));

        // Snapshot "before" from storage once
        ItemStack[] beforeAll = ecs.loadFor(targetId, rows);
        if (beforeAll == null) beforeAll = new ItemStack[allowed];

        ItemStack[] toSave = new ItemStack[allowed];

        String targetName = resolveNameStatic(targetId);

        for (int i = 0; i < allowed; i++) {
            ItemStack before = (i < beforeAll.length ? beforeAll[i] : null);
            ItemStack after  = inv.getItem(i);

            toSave[i] = after;

            if (!equalsItem(before, after)) {
                logChange(plugin, viewer, targetName, i, before, after);
            }
        }

        ecs.saveFor(targetId, rows, toSave);
    }

    /* =========================================================
       Helpers
       ========================================================= */

    private static boolean equalsItem(ItemStack a, ItemStack b) {
        if (a == null || a.getType() == Material.AIR) {
            return b == null || b.getType() == Material.AIR;
        }
        if (b == null || b.getType() == Material.AIR) return false;
        return a.isSimilar(b) && a.getAmount() == b.getAmount();
    }

    private static void logChange(OreoEssentials plugin,
                                  Player staff,
                                  String targetName,
                                  int slot,
                                  ItemStack before,
                                  ItemStack after) {
        try {
            var file = new java.io.File(plugin.getDataFolder(), "playeractions.log");
            try (PrintWriter out = new PrintWriter(new FileWriter(file, true))) {
                out.println(LocalDateTime.now() + " [ECSEE] "
                        + staff.getName() + " modified " + targetName
                        + " slot " + slot + " from "
                        + (before == null ? "AIR" : before.getType() + "x" + before.getAmount())
                        + " to "
                        + (after == null ? "AIR" : after.getType() + "x" + after.getAmount()));
            }
        } catch (Exception ignored) {}
    }

    private static String resolveNameStatic(UUID targetId) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(targetId);
        String name = (op != null && op.getName() != null) ? op.getName() : null;
        return (name == null || name.isBlank()) ? targetId.toString() : name;
    }

    private static String resolveName(UUID targetId) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(targetId);
        String name = (op != null && op.getName() != null) ? op.getName() : null;
        return (name == null || name.isBlank()) ? targetId.toString() : name;
    }

    private ItemStack createLockedBarrierItem(int allowedSlots) {
        ItemStack b = new ItemStack(Material.BARRIER);
        ItemMeta m = b.getItemMeta();
        if (m != null) {
            String name = Lang.get("enderchest.gui.locked-slot-name", "&cLocked slot");
            m.setDisplayName(org.bukkit.ChatColor.translateAlternateColorCodes('&', name));

            java.util.List<String> rawLore = Lang.getList("enderchest.gui.locked-slot-lore");
            java.util.List<String> lore = new java.util.ArrayList<>();
            for (String line : rawLore) {
                line = line.replace("%slots%", String.valueOf(allowedSlots));
                lore.add(org.bukkit.ChatColor.translateAlternateColorCodes('&', line));
            }
            m.setLore(lore);
            b.setItemMeta(m);
        }
        return b;
    }
}
