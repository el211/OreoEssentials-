// File: src/main/java/fr/elias/oreoEssentials/playerwarp/gui/PlayerWarpBrowseMenu.java
package fr.elias.oreoEssentials.modules.playerwarp.gui;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.playerwarp.PlayerWarp;
import fr.elias.oreoEssentials.modules.playerwarp.PlayerWarpService;
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
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.stream.Collectors;

/**
 *
 * Features:
 * - Browse all warps
 * - Pagination
 * - Click to teleport
 * - Password protection with chat prompt
 * - Status indicators (locked/whitelist/public)
 *
 * Chat messages use Lang.send():
 * - pw.password-required
 * - pw.teleport-failed
 * - pw.not-found
 * - pw.no-permission-warp
 *
 * Password prompt uses hardcoded text (acceptable for interactive prompt).
 */
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

                if (isPasswordProtectedFor(player, warp)) {
                    Lang.send(player, "pw.password-required",
                            "<red>Warp <yellow>%warp%</yellow> requires a password.</red>",
                            Map.of("warp", warp.getName()));

                    openPasswordChatPrompt(player, warp);
                    return;
                }

                boolean ok = service.teleportToPlayerWarp(player, warp.getOwner(), warp.getName());
                if (!ok) {
                    Lang.send(player, "pw.teleport-failed",
                            "<red>Teleportation failed.</red>",
                            Map.of("error", "unknown"));
                }
            });
        }

        pagination.setItems(items);
        pagination.setItemsPerPage(9 * 4);
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

    private void openPasswordChatPrompt(Player player, PlayerWarp warp) {
        OreoEssentials plugin = OreoEssentials.get();

        // Close the GUI so they clearly see the chat prompt
        InventoryView view = player.getOpenInventory();
        if (view != null) {
            Inventory top = view.getTopInventory();
            if (top != null) {
                player.closeInventory();
            }
        }

        plugin.getLogger().info("[PW/DEBUG] [CHAT] Waiting for password in chat for player "
                + player.getName() + " warp=" + warp.getName());

        player.sendMessage(ChatColor.YELLOW + "Type the password for warp "
                + ChatColor.AQUA + warp.getName()
                + ChatColor.YELLOW + " in chat, or type "
                + ChatColor.RED + "cancel"
                + ChatColor.YELLOW + " to abort.");

        Listener listener = new Listener() {

            @EventHandler
            public void onChat(AsyncPlayerChatEvent event) {
                if (!event.getPlayer().getUniqueId().equals(player.getUniqueId())) {
                    return;
                }

                event.setCancelled(true);
                HandlerList.unregisterAll(this);

                String msg = event.getMessage();
                if (msg == null) msg = "";

                String typed = msg.trim();

                OreoEssentials.get().getLogger().info(
                        "[PW/DEBUG] [CHAT] Player "
                                + player.getName() + " typed password='" + typed + "' for warp=" + warp.getName()
                );

                if (typed.equalsIgnoreCase("cancel")) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.sendMessage(ChatColor.RED + "Password entry cancelled for warp "
                                + ChatColor.AQUA + warp.getName());
                    });
                    return;
                }

                String finalTyped = typed;
                Bukkit.getScheduler().runTask(plugin, () -> handlePasswordResult(player, warp, finalTyped));
            }

            @EventHandler
            public void onClose(InventoryCloseEvent event) {
            }

            @EventHandler
            public void onClick(InventoryClickEvent event) {
            }
        };

        Bukkit.getPluginManager().registerEvents(listener, plugin);
    }


    private void handlePasswordResult(Player player, PlayerWarp warp, String typedPassword) {
        // Refresh warp from service
        PlayerWarp current = service.listAll().stream()
                .filter(w -> w.getOwner().equals(warp.getOwner()))
                .filter(w -> w.getName().equalsIgnoreCase(warp.getName()))
                .findFirst()
                .orElse(null);

        if (current == null) {
            Lang.send(player, "pw.not-found",
                    "<red>Warp <yellow>%name%</yellow> not found.</red>",
                    Map.of("name", warp.getName().toLowerCase(Locale.ROOT)));
            OreoEssentials.get().getLogger().info(
                    "[PW/DEBUG] [CHAT] Warp no longer exists for "
                            + player.getName() + " warp=" + warp.getName()
            );
            return;
        }

        String realPassword = current.getPassword();
        OreoEssentials.get().getLogger().info(
                "[PW/DEBUG] [CHAT] Password check for warp '"
                        + current.getName()
                        + "': typed='" + typedPassword + "' (len=" + typedPassword.length() + ")"
                        + ", stored='" + realPassword + "' (len=" + (realPassword == null ? -1 : realPassword.length()) + ")"
        );

        if (realPassword == null || realPassword.isEmpty()) {
            OreoEssentials.get().getLogger().info(
                    "[PW/DEBUG] [CHAT] Warp '" + current.getName()
                            + "' has no password anymore, teleporting normally."
            );
            boolean ok = service.teleportToPlayerWarp(player, current.getOwner(), current.getName());
            if (!ok) {
                Lang.send(player, "pw.teleport-failed",
                        "<red>Teleportation failed.</red>",
                        Map.of("error", "unknown"));
            }
            return;
        }

        if (!service.canUse(player, current)) {
            Lang.send(player, "pw.no-permission-warp",
                    "<red>You don't have permission to use <yellow>%name%</yellow>.</red>",
                    Map.of("name", current.getName()));
            OreoEssentials.get().getLogger().info(
                    "[PW/DEBUG] [CHAT] Player " + player.getName()
                            + " failed canUse() check for warp='" + current.getName() + "'"
            );
            return;
        }

        String cmd = "pw use " + current.getName() + " " + typedPassword;
        OreoEssentials.get().getLogger().info(
                "[PW/DEBUG] [CHAT] Executing '/" + cmd + "' for " + player.getName()
        );

        player.performCommand(cmd);
    }



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