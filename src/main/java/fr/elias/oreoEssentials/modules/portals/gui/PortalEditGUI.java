package fr.elias.oreoEssentials.modules.portals.gui;

import fr.elias.oreoEssentials.modules.portals.PortalsManager;
import fr.elias.oreoEssentials.modules.portals.PortalsManager.Portal;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Edit GUI for a single portal.
 *
 * Buttons:
 *  [Info]          — summary (no click)
 *  [Set Dest Here] — set destination to player's current location
 *  [Particles]     — open PortalParticleGUI
 *  [Toggle KeepYaw]— toggle keepYawPitch
 *  [Set Permission]— (chat input — opens a prompt via anvil or message)
 *  [Set Server]    — (chat input — set destServer for cross-server)
 *  [Delete]        — remove portal (confirm with shift-click)
 *  [Back]          — return to PortalListGUI
 */
public final class PortalEditGUI implements InventoryProvider {

    private final PortalsManager manager;
    private final String portalName;

    private PortalEditGUI(PortalsManager manager, String portalName) {
        this.manager    = manager;
        this.portalName = portalName;
    }

    public static SmartInventory getInventory(PortalsManager manager, String portalName) {
        return SmartInventory.builder()
                .id("oe_portal_edit_" + portalName)
                .provider(new PortalEditGUI(manager, portalName))
                .manager(manager.getPlugin().getInvManager())
                .size(4, 9)
                .title(ChatColor.AQUA + "Edit: " + portalName)
                .build();
    }

    @Override
    public void init(Player player, InventoryContents contents) {
        Portal portal = manager.get(portalName);
        if (portal == null) {
            player.sendMessage(ChatColor.RED + "Portal not found: " + portalName);
            player.closeInventory();
            return;
        }

        // ── Row 0: border ──────────────────────────────────────────────────
        ItemStack border = glass(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < 9; i++) contents.set(0, i, ClickableItem.empty(border));
        for (int i = 0; i < 9; i++) contents.set(3, i, ClickableItem.empty(border));

        // ── Info panel (col 0-1) ───────────────────────────────────────────
        contents.set(1, 0, ClickableItem.empty(infoItem(portal)));
        contents.set(2, 0, ClickableItem.empty(infoItem(portal)));

        // ── Set destination to current location ───────────────────────────
        contents.set(1, 2, ClickableItem.of(
                named(Material.COMPASS, "&a&lSet Dest Here",
                        List.of("&7Teleports this portal to", "&7&oyour current location.")),
                e -> {
                    manager.updateDestination(portalName, player.getLocation());
                    player.sendMessage(ChatColor.GREEN + "Destination updated to your location.");
                    refresh(player);
                }));

        // ── Particles button ───────────────────────────────────────────────
        contents.set(1, 4, ClickableItem.of(
                named(Material.BLAZE_POWDER, "&d&lParticles",
                        List.of("&7Configure per-portal", "&7teleport & ambient particles.")),
                e -> PortalParticleGUI.getInventory(manager, portalName).open(player)));

        // ── Toggle keepYawPitch ───────────────────────────────────────────
        boolean keep = portal.keepYawPitch;
        contents.set(1, 6, ClickableItem.of(
                named(keep ? Material.LIME_DYE : Material.GRAY_DYE,
                        "&e&lKeep Yaw/Pitch: " + (keep ? "&aON" : "&cOFF"),
                        List.of("&7If ON, players keep their", "&7look direction after teleporting.")),
                e -> {
                    manager.toggleKeepYawPitch(portalName);
                    refresh(player);
                }));

        // ── Set permission (chat input) ────────────────────────────────────
        String perm = portal.permission != null ? portal.permission : "none";
        contents.set(2, 1, ClickableItem.of(
                named(Material.NAME_TAG, "&6&lSet Permission",
                        List.of("&7Current: &e" + perm,
                                "&7Close GUI and type the permission",
                                "&7in chat (or 'none' to clear).")),
                e -> {
                    player.closeInventory();
                    player.sendMessage(ChatColor.GOLD + "Type the permission node for portal "
                            + ChatColor.AQUA + portalName + ChatColor.GOLD
                            + " in chat (or 'none' to clear):");
                    manager.awaitPermissionInput(player.getUniqueId(), portalName);
                }));

        // ── Set destination warp ──────────────────────────────────────────
        String warp = portal.destWarp != null ? portal.destWarp : "none";
        contents.set(2, 3, ClickableItem.of(
                named(Material.WARPED_FUNGUS_ON_A_STICK, "&a&lDest Warp",
                        List.of("&7Current: &a" + warp,
                                "&7Close GUI and type a warp name",
                                "&7(or 'none' to use coordinates).")),
                e -> {
                    player.closeInventory();
                    player.sendMessage(ChatColor.GREEN + "Type the destination warp name for portal "
                            + ChatColor.AQUA + portalName + ChatColor.GREEN
                            + " in chat (or 'none' to clear):");
                    manager.awaitWarpInput(player.getUniqueId(), portalName);
                }));

        // ── Set destination server (cross-server) ─────────────────────────
        String srv = portal.destServer != null ? portal.destServer : "none";
        contents.set(2, 5, ClickableItem.of(
                named(Material.ENDER_EYE, "&b&lDest Server",
                        List.of("&7Current: &a" + srv,
                                "&7Close GUI and type the target",
                                "&7server name in chat (or 'none').")),
                e -> {
                    player.closeInventory();
                    player.sendMessage(ChatColor.AQUA + "Type the destination server name for portal "
                            + ChatColor.GOLD + portalName + ChatColor.AQUA
                            + " in chat (or 'none' for same-server):");
                    manager.awaitServerInput(player.getUniqueId(), portalName);
                }));

        // ── Delete ────────────────────────────────────────────────────────
        contents.set(2, 7, ClickableItem.of(
                named(Material.TNT, "&c&lDelete Portal",
                        List.of("&7&oShift-click to confirm deletion.")),
                e -> {
                    if (e.isShiftClick()) {
                        manager.remove(portalName);
                        player.sendMessage(ChatColor.RED + "Portal " + ChatColor.YELLOW
                                + portalName + ChatColor.RED + " deleted.");
                        PortalListGUI.getInventory(manager).open(player);
                    } else {
                        player.sendMessage(ChatColor.YELLOW + "Shift-click to confirm deletion of " + portalName);
                    }
                }));

        // ── Back ──────────────────────────────────────────────────────────
        contents.set(3, 0, ClickableItem.of(
                named(Material.BARRIER, "&c&lBack"),
                e -> PortalListGUI.getInventory(manager).open(player)));
    }

    @Override
    public void update(Player player, InventoryContents contents) {}

    private void refresh(Player player) {
        getInventory(manager, portalName).open(player);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ItemStack infoItem(Portal portal) {
        ItemStack item = new ItemStack(Material.ENDER_PEARL);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(c("&b&l" + portal.name));
        List<String> lore = new ArrayList<>();
        lore.add(c("&7World: &f" + portal.world.getName()));
        lore.add(c("&7Box min: &f" + fmt(portal.box.getMinX()) + ", "
                + fmt(portal.box.getMinY()) + ", " + fmt(portal.box.getMinZ())));
        lore.add(c("&7Box max: &f" + fmt(portal.box.getMaxX()) + ", "
                + fmt(portal.box.getMaxY()) + ", " + fmt(portal.box.getMaxZ())));
        if (portal.destWarp != null && !portal.destWarp.isEmpty())
            lore.add(c("&7Dest Warp: &a" + portal.destWarp));
        else
            lore.add(c("&7Dest: &f" + shortLoc(portal.destination)));
        if (portal.destServer != null && !portal.destServer.isEmpty())
            lore.add(c("&7Dest Server: &a" + portal.destServer));
        if (portal.permission != null && !portal.permission.isEmpty())
            lore.add(c("&7Permission: &e" + portal.permission));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack named(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(c(name));
            meta.setLore(lore.stream().map(PortalEditGUI::c).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack named(Material mat, String name) {
        return named(mat, name, List.of());
    }

    private static ItemStack glass(Material mat) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) { meta.setDisplayName(" "); item.setItemMeta(meta); }
        return item;
    }

    private static String c(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private static String shortLoc(org.bukkit.Location l) {
        return l.getWorld().getName() + " ("
                + (int) l.getX() + ", " + (int) l.getY() + ", " + (int) l.getZ() + ")";
    }

    private static String fmt(double v) {
        return String.format("%.1f", v);
    }
}
