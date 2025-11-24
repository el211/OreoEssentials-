// src/main/java/fr/elias/oreoEssentials/playerwarp/gui/PlayerWarpBrowseMenu.java
package fr.elias.oreoEssentials.playerwarp.gui;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.playerwarp.PlayerWarp;
import fr.elias.oreoEssentials.playerwarp.PlayerWarpService;
import fr.elias.oreoEssentials.util.Lang;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import fr.minuskube.inv.content.Pagination;
import fr.minuskube.inv.content.SlotIterator;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.stream.Collectors;

public class PlayerWarpBrowseMenu implements InventoryProvider {

    private final PlayerWarpService service;

    public PlayerWarpBrowseMenu(PlayerWarpService service) {
        this.service = service;
    }

    public static void open(Player player, PlayerWarpService service) {
        SmartInventory.builder()
                .id("playerwarps_browse")
                .provider(new PlayerWarpBrowseMenu(service))
                .size(6, 9)
                .title("§bPlayer Warps")
                .manager(OreoEssentials.get().getInventoryManager())
                .build()
                .open(player);
    }

    @Override
    public void init(Player player, InventoryContents contents) {
        update(player, contents);
    }

    @Override
    public void update(Player player, InventoryContents contents) {
        Pagination pagination = contents.pagination();

        List<PlayerWarp> allWarps = service.listAll().stream()
                .sorted(Comparator.comparing(PlayerWarp::getName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());

        ClickableItem[] items = new ClickableItem[allWarps.size()];

        for (int i = 0; i < allWarps.size(); i++) {
            PlayerWarp warp = allWarps.get(i);
            items[i] = ClickableItem.of(buildWarpItem(player, warp), e -> {

                // If password-protected for this player → open anvil password prompt
                if (isPasswordProtectedFor(player, warp)) {
                    Lang.send(player, "pw.password-required",
                            Map.of("warp", warp.getName()),
                            player);

                    openPasswordAnvil(player, warp);
                    return;
                }

                // Otherwise, normal teleport
                boolean ok = service.teleportToPlayerWarp(player, warp.getOwner(), warp.getName());
                if (!ok) {
                    Lang.send(player, "pw.teleport-failed",
                            Map.of("error", "unknown"),
                            player);
                }
            });
        }

        pagination.setItems(items);
        pagination.setItemsPerPage(9 * 4); // rows 1-4

        pagination.addToIterator(contents.newIterator(SlotIterator.Type.HORIZONTAL, 1, 0)
                .allowOverride(true));

        // Navigation buttons
        ItemStack prev = new ItemStack(Material.ARROW);
        ItemMeta prevMeta = prev.getItemMeta();
        prevMeta.setDisplayName("§ePrevious page");
        prev.setItemMeta(prevMeta);

        ItemStack next = new ItemStack(Material.ARROW);
        ItemMeta nextMeta = next.getItemMeta();
        nextMeta.setDisplayName("§eNext page");
        next.setItemMeta(nextMeta);

        contents.set(5, 2, ClickableItem.of(prev, e -> {
            if (!pagination.isFirst()) {
                pagination.previous();
                update(player, contents);
            }
        }));

        contents.set(5, 6, ClickableItem.of(next, e -> {
            if (!pagination.isLast()) {
                pagination.next();
                update(player, contents);
            }
        }));
    }

    // ------------------------------------------------------------------------
    // PASSWORD ANVIL PROMPT
    // ------------------------------------------------------------------------

    /**
     * Opens an anvil GUI where the player types the password by renaming a paper
     * and confirming by clicking the result item.
     */
    private void openPasswordAnvil(Player player, PlayerWarp warp) {
        OreoEssentials plugin = OreoEssentials.get();

        // Create an anvil inventory for this player
        Inventory anvil = Bukkit.createInventory(player, InventoryType.ANVIL,
                ChatColor.AQUA + "Enter password for " + warp.getName());

        // Left input: paper with a hint name
        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta meta = paper.getItemMeta();
        meta.setDisplayName(ChatColor.GRAY + "Type password here");
        paper.setItemMeta(meta);
        anvil.setItem(0, paper);

        // Open it
        InventoryView view = player.openInventory(anvil);

        // Temporary listener bound to this inventory + player
        Listener listener = new Listener() {

            @EventHandler
            public void onClick(InventoryClickEvent event) {
                if (!(event.getWhoClicked() instanceof Player p)) return;
                if (!p.getUniqueId().equals(player.getUniqueId())) return;
                if (event.getInventory() == null) return;
                if (!event.getInventory().equals(anvil)) return;

                int rawSlot = event.getRawSlot();

                // Only treat result slot (2) as confirm
                if (rawSlot != 2) {
                    // Allow normal typing/renaming on input
                    event.setCancelled(false);
                    return;
                }

                // Result slot clicked: confirm password
                event.setCancelled(true);

                ItemStack resultItem = event.getInventory().getItem(2);
                if (resultItem == null || !resultItem.hasItemMeta() ||
                        !resultItem.getItemMeta().hasDisplayName()) {

                    p.closeInventory();
                    // Use lang-based title for "no password entered"
                    Lang.sendTitle(p,
                            "pw.password-title-empty",
                            "pw.password-subtitle-empty",
                            Map.of("warp", warp.getName()));
                    HandlerList.unregisterAll(this);
                    return;
                }

                String typed = ChatColor.stripColor(resultItem.getItemMeta().getDisplayName());
                if (typed == null) typed = "";

                p.closeInventory();
                HandlerList.unregisterAll(this);

                handlePasswordResult(p, warp, typed);
            }

            @EventHandler
            public void onClose(InventoryCloseEvent event) {
                if (!(event.getPlayer() instanceof Player p)) return;
                if (!p.getUniqueId().equals(player.getUniqueId())) return;
                if (!event.getInventory().equals(anvil)) return;

                // When player closes the anvil manually, unregister listener
                HandlerList.unregisterAll(this);
            }
        };

        Bukkit.getPluginManager().registerEvents(listener, plugin);
    }

    /**
     * Called after the player confirmed the password via the anvil.
     */
    private void handlePasswordResult(Player player, PlayerWarp warp, String typedPassword) {
        // Refresh warp from service in case it was changed meanwhile
        PlayerWarp current = service.listAll().stream()
                .filter(w -> w.getOwner().equals(warp.getOwner()))
                .filter(w -> w.getName().equalsIgnoreCase(warp.getName()))
                .findFirst()
                .orElse(null);

        if (current == null) {
            Lang.send(player, "pw.not-found",
                    Map.of("name", warp.getName().toLowerCase(Locale.ROOT)),
                    player);
            return;
        }

        String realPassword = current.getPassword();
        if (realPassword == null || realPassword.isEmpty()) {
            // No password anymore → behave like normal teleport
            boolean ok = service.teleportToPlayerWarp(player, current.getOwner(), current.getName());
            if (!ok) {
                Lang.send(player, "pw.teleport-failed",
                        Map.of("error", "unknown"),
                        player);
            }
            return;
        }

        if (!realPassword.equals(typedPassword)) {
            // WRONG PASSWORD
            Lang.send(player, "pw.password-wrong",
                    Map.of("warp", current.getName()),
                    player);

            // Title from lang.yml for wrong password
            Lang.sendTitle(player,
                    "pw.password-title-wrong",
                    "pw.password-subtitle-wrong",
                    Map.of("warp", current.getName()));
            return;
        }

        // Extra access checks (lock, whitelist, cost, perms, etc.)
        if (!service.canUse(player, current)) {
            Lang.send(player, "pw.no-permission-warp",
                    Map.of("name", current.getName()),
                    player);
            return;
        }

        // Correct password → teleport using full routing logic
        boolean ok = service.teleportToPlayerWarp(player, current.getOwner(), current.getName());
        if (!ok) {
            Lang.send(player, "pw.teleport-failed",
                    Map.of("error", "unknown"),
                    player);
        }
    }

    // ------------------------------------------------------------------------
    // HELPERS
    // ------------------------------------------------------------------------

    private boolean isPasswordProtectedFor(Player viewer, PlayerWarp warp) {
        String pwd = warp.getPassword();
        if (pwd == null || pwd.isEmpty()) return false;

        UUID uuid = viewer.getUniqueId();
        if (warp.getOwner().equals(uuid)) return false;
        if (warp.getManagers() != null && warp.getManagers().contains(uuid)) return false;
        if (viewer.hasPermission("oe.pw.bypass.password")) return false;

        return true;
    }

    private ItemStack buildWarpItem(Player viewer, PlayerWarp warp) {
        ItemStack base = warp.getIcon() != null
                ? warp.getIcon().clone()
                : new ItemStack(Material.ENDER_PEARL);

        ItemMeta meta = base.getItemMeta();

        String name = warp.getName();
        String desc = warp.getDescription();
        String category = warp.getCategory();
        double cost = warp.getCost();

        OfflinePlayer ownerOff = Bukkit.getOfflinePlayer(warp.getOwner());
        String ownerName = ownerOff != null && ownerOff.getName() != null
                ? ownerOff.getName()
                : warp.getOwner().toString();

        meta.setDisplayName("§a" + name);

        List<String> lore = new ArrayList<>();
        lore.add("§7Owner: §e" + ownerName);
        if (category != null && !category.isEmpty()) {
            lore.add("§7Category: §b" + category);
        }
        if (desc != null && !desc.isEmpty()) {
            lore.add("§7Description:");
            for (String line : splitColored(desc, 35)) {
                lore.add("§f" + line);
            }
        }
        if (cost > 0) {
            lore.add("§7Cost: §e" + cost);
        }

        // ---------- STATUS (locked / whitelist / public + your state) ----------
        lore.add("");
        UUID viewerId = viewer.getUniqueId();

        boolean locked = warp.isLocked();
        boolean wlEnabled = warp.isWhitelistEnabled();
        Set<UUID> managers = warp.getManagers();
        Set<UUID> wl = warp.getWhitelist();

        boolean isOwner = warp.getOwner().equals(viewerId);
        boolean isManager = managers != null && managers.contains(viewerId);
        boolean bypass = viewer.hasPermission("oe.pw.bypass.lock");

        if (locked) {
            lore.add("§7Status: §cLocked");
            if (isOwner || isManager || bypass) {
                lore.add("§7You can still use this warp.");
            } else {
                lore.add("§7You §cCANNOT§7 use this warp.");
            }
        } else if (wlEnabled) {
            boolean isWhitelisted = wl != null && wl.contains(viewerId);
            lore.add("§7Status: §eWhitelist");
            if (isOwner || isManager || bypass) {
                lore.add("§7You bypass whitelist (owner/manager/staff).");
            } else if (isWhitelisted) {
                lore.add("§aYou are whitelisted.");
            } else {
                lore.add("§cYou are NOT whitelisted.");
            }
        } else {
            lore.add("§7Status: §aPublic");
        }

        // Password indicator
        String pwd = warp.getPassword();
        if (pwd != null && !pwd.isEmpty()) {
            lore.add("§7Protection: §cPassword");
        }

        lore.add("");
        lore.add("§a» Click to teleport");

        meta.setLore(lore);
        base.setItemMeta(meta);
        return base;
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
