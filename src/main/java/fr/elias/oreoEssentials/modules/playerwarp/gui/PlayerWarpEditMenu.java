package fr.elias.oreoEssentials.modules.playerwarp.gui;

import fr.elias.oreoEssentials.modules.playerwarp.PlayerWarp;
import fr.elias.oreoEssentials.modules.playerwarp.PlayerWarpService;
import fr.elias.oreoEssentials.util.Lang;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 *
 * Features:
 * - Teleport to warp
 * - Reset location
 * - Toggle lock
 * - Toggle whitelist
 * - Set icon from hand
 * - Delete warp
 * - Password toggle (set default/clear)
 * - Command hints for advanced settings
 *
 * Chat messages use Lang.send():
 * - pw.teleport-failed
 * - pw.reset-success
 * - pw.locked / pw.unlocked
 * - pw.whitelist-enabled / pw.whitelist-disabled
 * - pw.icon-no-item
 * - pw.icon-set
 * - pw.remove-success
 * - pw.password-placeholder-set
 * - pw.password-cleared
 */
public class PlayerWarpEditMenu implements InventoryProvider {

    private final PlayerWarpService service;
    private final PlayerWarp warp;
    private final String categoryFilter; // to reopen MyPlayerWarpsMenu with same filter

    public PlayerWarpEditMenu(PlayerWarpService service, PlayerWarp warp, String categoryFilter) {
        this.service = service;
        this.warp = warp;
        this.categoryFilter = categoryFilter;
    }

    public static void open(Player player, PlayerWarpService service, PlayerWarp warp, String categoryFilter) {
        SmartInventory.builder()
                .id("playerwarps_edit_" + warp.getId())
                .provider(new PlayerWarpEditMenu(service, warp, categoryFilter))
                .size(3, 9)
                .title("§bWarp: §a" + warp.getName())
                .manager(fr.elias.oreoEssentials.OreoEssentials.get().getInvManager())
                .build()
                .open(player);
    }

    @Override
    public void init(Player player, InventoryContents contents) {
        ItemStack info = warp.getIcon() != null
                ? warp.getIcon().clone()
                : new ItemStack(Material.OAK_SIGN);

        ItemMeta meta = info.getItemMeta();
        meta.setDisplayName("§a" + warp.getName());

        double cost = warp.getCost();
        boolean locked = warp.isLocked();
        String category = warp.getCategory();
        String desc = warp.getDescription();

        List<String> lore = new ArrayList<>();
        OfflinePlayer ownerOff = Bukkit.getOfflinePlayer(warp.getOwner());
        String ownerName = (ownerOff != null && ownerOff.getName() != null)
                ? ownerOff.getName()
                : warp.getOwner().toString();

        lore.add("§7Owner: §e" + ownerName);
        if (category != null && !category.isEmpty()) {
            lore.add("§7Category: §b" + category);
        }
        lore.add("§7Cost: " + (cost > 0 ? "§e" + cost : "§e0 (free)"));
        lore.add("§7Locked: " + (locked ? "§cYes" : "§aNo"));
        if (desc != null && !desc.isEmpty()) {
            lore.add("§7Description:");
            for (String line : splitColored(desc, 35)) {
                lore.add("§f" + line);
            }
        }
        meta.setLore(lore);
        info.setItemMeta(meta);

        contents.set(1, 4, ClickableItem.empty(info));

        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setDisplayName("§cBack to My Warps");
        backMeta.setLore(List.of("§7Click to return"));
        back.setItemMeta(backMeta);

        contents.set(2, 0, ClickableItem.of(back, e -> {
            MyPlayerWarpsMenu.open(player, service, categoryFilter);
        }));

        ItemStack tp = new ItemStack(Material.ENDER_PEARL);
        ItemMeta tpMeta = tp.getItemMeta();
        tpMeta.setDisplayName("§aTeleport");
        tpMeta.setLore(List.of(
                "§7Teleport to this warp.",
                "",
                "§a» Click to teleport"
        ));
        tp.setItemMeta(tpMeta);

        contents.set(1, 1, ClickableItem.of(tp, e -> {
            boolean ok = service.teleportToPlayerWarp(player, warp.getOwner(), warp.getName());
            if (!ok) {
                Lang.send(player, "pw.teleport-failed",
                        "<red>Teleportation failed.</red>",
                        Map.of("error", "unknown"));
            }
        }));

        ItemStack reset = new ItemStack(Material.COMPASS);
        ItemMeta resetMeta = reset.getItemMeta();
        resetMeta.setDisplayName("§eReset to current location");
        resetMeta.setLore(List.of(
                "§7Sets this warp to your",
                "§7current position.",
                "",
                "§a» Click to reset"
        ));
        reset.setItemMeta(resetMeta);

        contents.set(1, 2, ClickableItem.of(reset, e -> {
            warp.setLocation(player.getLocation().clone());
            service.saveWarp(warp);
            // ✅ Chat message uses Lang
            Lang.send(player, "pw.reset-success",
                    "<green>Reset warp <aqua>%name%</aqua> to your current location.</green>",
                    Map.of("name", warp.getName()));
            open(player, service, warp, categoryFilter);
        }));

        ItemStack lockItem = new ItemStack(Material.IRON_DOOR);
        ItemMeta lockMeta = lockItem.getItemMeta();
        boolean lockedNow = warp.isLocked();
        lockMeta.setDisplayName(lockedNow ? "§cUnlock warp" : "§aLock warp");
        lockMeta.setLore(List.of(
                "§7Current state: " + (lockedNow ? "§cLocked" : "§aUnlocked"),
                "",
                "§a» Click to toggle lock"
        ));
        lockItem.setItemMeta(lockMeta);

        contents.set(1, 3, ClickableItem.of(lockItem, e -> {
            boolean newState = !warp.isLocked();
            warp.setLocked(newState);
            service.saveWarp(warp);
            // ✅ Chat message uses Lang
            Lang.send(player, newState ? "pw.locked" : "pw.unlocked",
                    newState ? "<green>Locked warp <aqua>%warp%</aqua>.</green>" : "<green>Unlocked warp <aqua>%warp%</aqua>.</green>",
                    Map.of("warp", warp.getName()));
            open(player, service, warp, categoryFilter);
        }));

        ItemStack wlItem = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta wlMeta = wlItem.getItemMeta();
        boolean wlEnabled = warp.isWhitelistEnabled();
        wlMeta.setDisplayName(wlEnabled ? "§cDisable whitelist" : "§aEnable whitelist");
        wlMeta.setLore(List.of(
                "§7Current: " + (wlEnabled ? "§aenabled" : "§cdisabled"),
                "§7Players on whitelist stay saved.",
                "",
                "§a» Click to toggle whitelist"
        ));
        wlItem.setItemMeta(wlMeta);

        contents.set(1, 5, ClickableItem.of(wlItem, e -> {
            boolean newState = !warp.isWhitelistEnabled();
            warp.setWhitelistEnabled(newState);
            service.saveWarp(warp);
            if (newState) {
                Lang.send(player, "pw.whitelist-enabled",
                        "<green>Whitelist enabled for <white>%warp%</white>.</green>",
                        Map.of("warp", warp.getName()));
            } else {
                Lang.send(player, "pw.whitelist-disabled",
                        "<yellow>Whitelist disabled for <white>%warp%</white>.</yellow>",
                        Map.of("warp", warp.getName()));
            }
            open(player, service, warp, categoryFilter);
        }));

        ItemStack iconItem = new ItemStack(Material.ITEM_FRAME);
        ItemMeta iconMeta = iconItem.getItemMeta();
        iconMeta.setDisplayName("§eSet icon from hand");
        iconMeta.setLore(List.of(
                "§7Use the item in your hand",
                "§7as this warp's icon.",
                "",
                "§a» Click to apply"
        ));
        iconItem.setItemMeta(iconMeta);

        contents.set(1, 6, ClickableItem.of(iconItem, e -> {
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand == null || hand.getType().isAir()) {
                Lang.send(player, "pw.icon-no-item",
                        "<red>You must hold an item in your main hand.</red>",
                        Map.of());
                return;
            }
            warp.setIcon(hand.clone());
            service.saveWarp(warp);
            Lang.send(player, "pw.icon-set",
                    "<green>Set icon for <aqua>%warp%</aqua>.</green>",
                    Map.of("warp", warp.getName()));
            open(player, service, warp, categoryFilter);
        }));

        ItemStack delete = new ItemStack(Material.BARRIER);
        ItemMeta deleteMeta = delete.getItemMeta();
        deleteMeta.setDisplayName("§cDelete warp");
        deleteMeta.setLore(List.of(
                "§7Remove this warp permanently.",
                "",
                "§c» Click to delete"
        ));
        delete.setItemMeta(deleteMeta);

        contents.set(1, 7, ClickableItem.of(delete, e -> {
            service.deleteWarp(warp);
            Lang.send(player, "pw.remove-success",
                    "<green>Removed warp <aqua>%name%</aqua>.</green>",
                    Map.of("name", warp.getName()));
            player.closeInventory();
        }));

        boolean hasPwd = warp.getPassword() != null && !warp.getPassword().isEmpty();

        ItemStack pwdItem = new ItemStack(Material.TRIPWIRE_HOOK);
        ItemMeta pwdMeta = pwdItem.getItemMeta();
        pwdMeta.setDisplayName("§ePassword protection");

        List<String> pwdLore = new ArrayList<>();
        pwdLore.add("§7Current: " + (hasPwd ? "§aEnabled" : "§cDisabled"));
        pwdLore.add("");
        if (hasPwd) {
            pwdLore.add("§7Players must use:");
            pwdLore.add("§f/pw use " + warp.getName() + " <password>");
            pwdLore.add("");
            pwdLore.add("§7Left-click: §cClear password");
        } else {
            pwdLore.add("§7No password set.");
            pwdLore.add("§7Use:");
            pwdLore.add("§f/pw password " + warp.getName() + " <password>");
            pwdLore.add("");
            pwdLore.add("§7Left-click: §aSet default password 'changeme'");
        }
        pwdMeta.setLore(pwdLore);
        pwdItem.setItemMeta(pwdMeta);

        contents.set(2, 8, ClickableItem.of(pwdItem, e -> {
            if (warp.getPassword() == null || warp.getPassword().isEmpty()) {
                warp.setPassword("changeme");
                service.saveWarp(warp);
                Lang.send(player, "pw.password-placeholder-set",
                        "<green>Set default password '<white>%password%</white>' for <aqua>%warp%</aqua>. Change it with <yellow>/pw password %warp% <your-password></yellow></green>",
                        Map.of("warp", warp.getName(), "password", "changeme"));
            } else {
                warp.setPassword(null);
                service.saveWarp(warp);
                Lang.send(player, "pw.password-cleared",
                        "<green>Cleared password for <aqua>%warp%</aqua>.</green>",
                        Map.of("warp", warp.getName()));
            }
            open(player, service, warp, categoryFilter);
        }));

        ItemStack metaItem = new ItemStack(Material.BOOK);
        ItemMeta metaMeta = metaItem.getItemMeta();
        metaMeta.setDisplayName("§bAdvanced settings");
        metaMeta.setLore(List.of(
                "§7Rename, description, category, cost,",
                "§7password and managers are edited",
                "§7via commands:",
                "§f/pw rename " + warp.getName() + " <newName>",
                "§f/pw desc " + warp.getName() + " <text>",
                "§f/pw category " + warp.getName() + " <category>",
                "§f/pw cost " + warp.getName() + " <amount>",
                "§f/pw password " + warp.getName() + " <pwd|off>",
                "§f/pw managers " + warp.getName() + " [player]"
        ));
        metaItem.setItemMeta(metaMeta);

        contents.set(0, 8, ClickableItem.empty(metaItem));
    }

    @Override
    public void update(Player player, InventoryContents contents) {
    }

    private List<String> splitColored(String text, int maxLength) {
        List<String> result = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder current = new StringBuilder();

        for (String w : words) {
            if (current.length() + w.length() + 1 > maxLength) {
                result.add(current.toString());
                current.setLength(0);
            }
            if (current.length() > 0) current.append(" ");
            current.append(w);
        }
        if (current.length() > 0) {
            result.add(current.toString());
        }
        return result;
    }
}