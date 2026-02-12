// File: src/main/java/fr/elias/oreoEssentials/commands/core/playercommands/WarpAdminActionsProvider.java
package fr.elias.oreoEssentials.modules.warps.provider;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.warps.rabbit.WarpDirectory;
import fr.elias.oreoEssentials.modules.warps.WarpService;
import fr.elias.oreoEssentials.modules.warps.commands.WarpsAdminCommand;
import fr.elias.oreoEssentials.modules.warps.gui.WarpConfirmDeleteGui;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import fr.minuskube.inv.content.SlotPos;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WarpAdminActionsProvider implements InventoryProvider, Listener {

    private static final String TITLE_PREFIX = ChatColor.DARK_AQUA + "Warp: " + ChatColor.AQUA;

    private final WarpService warps;
    private final String warpName;

    private static final Map<UUID, PendingPrompt> prompts = new ConcurrentHashMap<>();

    private record PendingPrompt(Type type, String warp) {
        enum Type { SET_PERMISSION, RENAME }
    }

    public WarpAdminActionsProvider(WarpService warps, String warpName) {
        this.warps = warps;
        this.warpName = warpName.toLowerCase(Locale.ROOT);
        Bukkit.getPluginManager().registerEvents(this, OreoEssentials.get());
    }

    @Override
    public void init(Player p, InventoryContents contents) {
        final OreoEssentials plugin = OreoEssentials.get();
        final WarpDirectory dir = plugin.getWarpDirectory();
        final String server = (dir != null ? dir.getWarpServer(warpName) : plugin.getConfigService().serverName());
        final String perm = (dir != null ? nullSafe(dir.getWarpPermission(warpName)) : null);

        contents.set(0, 4, ClickableItem.empty(infoItem(
                TITLE_PREFIX + warpName,
                List.of(
                        ChatColor.GRAY + "Server: " + ChatColor.YELLOW + (server == null ? "?" : server),
                        ChatColor.GRAY + "Permission: " + (perm == null || perm.isBlank()
                                ? ChatColor.GREEN + "OPEN (everyone)"
                                : ChatColor.YELLOW + perm)
                )
        )));

        contents.set(SlotPos.of(1, 2), ClickableItem.of(button(Material.ENDER_PEARL, ChatColor.GREEN + "Teleport"),
                e -> WarpsAdminCommand.crossServerTeleport(warps, p, warpName)));

        contents.set(SlotPos.of(1, 4), ClickableItem.of(button(Material.PAPER, ChatColor.AQUA + "Set/Change Permission"),
                e -> {
                    p.closeInventory();
                    p.sendMessage(ChatColor.YELLOW + "Type the permission in chat for warp "
                            + ChatColor.AQUA + warpName + ChatColor.YELLOW + " (or type "
                            + ChatColor.WHITE + "cancel" + ChatColor.YELLOW + ").");
                    prompts.put(p.getUniqueId(), new PendingPrompt(PendingPrompt.Type.SET_PERMISSION, warpName));
                }));

        contents.set(SlotPos.of(1, 6), ClickableItem.of(button(Material.LIME_DYE, ChatColor.GREEN + "Make Public (clear perm)"),
                e -> {
                    if (dir != null) {
                        dir.setWarpPermission(warpName, null);
                        p.sendMessage(ChatColor.GREEN + "Warp " + ChatColor.AQUA + warpName
                                + ChatColor.GREEN + " is now public (no permission needed).");
                    } else {
                        p.sendMessage(ChatColor.RED + "WarpDirectory not available; cannot clear permission.");
                    }
                    reopen(p);
                }));

        contents.set(SlotPos.of(2, 3), ClickableItem.of(button(Material.NAME_TAG, ChatColor.GOLD + "Rename Warp"),
                e -> {
                    p.closeInventory();
                    p.sendMessage(ChatColor.YELLOW + "Type the new name in chat for warp "
                            + ChatColor.AQUA + warpName + ChatColor.YELLOW + " (or type "
                            + ChatColor.WHITE + "cancel" + ChatColor.YELLOW + ").");
                    prompts.put(p.getUniqueId(), new PendingPrompt(PendingPrompt.Type.RENAME, warpName));
                }));

        contents.set(SlotPos.of(2, 5), ClickableItem.of(button(Material.RED_CONCRETE, ChatColor.RED + "Delete Warp"),
                e -> WarpConfirmDeleteGui.open(p, warps, warpName, () -> {
                    p.sendMessage(ChatColor.RED + "Deleted warp " + ChatColor.YELLOW + warpName + ChatColor.RED + ".");
                    // After deletion, go back to admin list (use helper)
                    WarpsAdminCommand.openAdmin(p, warps, 0);
                }, () -> reopen(p))));

        contents.set(SlotPos.of(3, 4), ClickableItem.of(button(Material.ARROW, ChatColor.RED + "← Back"),
                e -> WarpsAdminCommand.openAdmin(p, warps, 0)));
    }

    @Override public void update(Player player, InventoryContents contents) {}

    private void reopen(Player p) {
        SmartInventory.builder()
                .id("oreo:warps_admin_actions:" + warpName)
                .provider(new WarpAdminActionsProvider(warps, warpName))
                .size(4, 9)
                .title(TITLE_PREFIX + warpName)
                .manager(OreoEssentials.get().getInvManager())
                .build()
                .open(p);
    }

    private ItemStack infoItem(String title, List<String> lore) {
        ItemStack it = new ItemStack(Material.BOOK);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(title);
        m.setLore(lore);
        it.setItemMeta(m);
        return it;
    }

    private ItemStack button(Material type, String name) {
        ItemStack it = new ItemStack(type);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(name);
        it.setItemMeta(m);
        return it;
    }

    private String nullSafe(String s) { return (s == null ? "" : s); }


    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        final UUID id = e.getPlayer().getUniqueId();
        PendingPrompt pp = prompts.get(id);
        if (pp == null) return;

        e.setCancelled(true);
        String msg = e.getMessage().trim();
        Player p = e.getPlayer();

        if (msg.equalsIgnoreCase("cancel")) {
            prompts.remove(id);
            p.sendMessage(ChatColor.GRAY + "Operation cancelled.");
            Bukkit.getScheduler().runTask(OreoEssentials.get(), () -> reopen(p));
            return;
        }

        switch (pp.type()) {
            case SET_PERMISSION -> handleSetPermission(p, pp.warp(), msg);
            case RENAME -> handleRename(p, pp.warp(), msg);
        }
        prompts.remove(id);
    }

    private void handleSetPermission(Player p, String warp, String permText) {
        final WarpDirectory dir = OreoEssentials.get().getWarpDirectory();
        if (dir == null) {
            p.sendMessage(ChatColor.RED + "WarpDirectory not available; cannot set permission.");
            Bukkit.getScheduler().runTask(OreoEssentials.get(), () -> reopen(p));
            return;
        }
        if (permText.equalsIgnoreCase("none")) {
            dir.setWarpPermission(warp, null);
            p.sendMessage(ChatColor.GREEN + "Permission cleared. Warp is now public.");
        } else {
            dir.setWarpPermission(warp, permText);
            p.sendMessage(ChatColor.GREEN + "Permission set to " + ChatColor.YELLOW + permText + ChatColor.GREEN + ".");
        }
        Bukkit.getScheduler().runTask(OreoEssentials.get(), () -> reopen(p));
    }

    private void handleRename(Player p, String oldName, String newNameRaw) {
        String newName = newNameRaw.trim().toLowerCase(Locale.ROOT);
        if (newName.isEmpty()) {
            p.sendMessage(ChatColor.RED + "Name cannot be empty.");
            Bukkit.getScheduler().runTask(OreoEssentials.get(), () -> reopen(p));
            return;
        }

        boolean ok = warps.renameWarp(oldName, newName);
        if (!ok) {
            p.sendMessage(ChatColor.RED + "Failed to rename warp.");
            Bukkit.getScheduler().runTask(OreoEssentials.get(), () -> reopen(p));
            return;
        }

        final WarpDirectory dir = OreoEssentials.get().getWarpDirectory();
        if (dir != null) {
            try {
                String server = dir.getWarpServer(oldName);
                String perm   = dir.getWarpPermission(oldName);
                if (server != null) dir.setWarpServer(newName, server);
                if (perm != null && !perm.isBlank()) dir.setWarpPermission(newName, perm);
                dir.deleteWarp(oldName);
            } catch (Throwable ignored) {}
        }

        p.sendMessage(ChatColor.GREEN + "Warp renamed to " + ChatColor.AQUA + newName + ChatColor.GREEN + ".");
        final String openName = newName;
        Bukkit.getScheduler().runTask(OreoEssentials.get(), () -> {
            SmartInventory.builder()
                    .id("oreo:warps_admin_actions:" + openName)
                    .provider(new WarpAdminActionsProvider(warps, openName))
                    .size(4, 9)
                    .title(TITLE_PREFIX + openName)
                    .manager(OreoEssentials.get().getInvManager())
                    .build()
                    .open(p);
        });
    }
}
