package fr.elias.oreoEssentials.modules.enderchest;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.util.Lang;
import fr.elias.oreoEssentials.util.OreScheduler;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.logging.Logger;

public class EcSeeCommand implements OreoCommand, TabCompleter {

    @Override public String name() { return "ecsee"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.ecsee"; }
    @Override public String usage() { return "<player>"; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player viewer)) {
            sender.sendMessage("§cThis command is player-only.");
            return true;
        }
        if (!viewer.hasPermission(permission())) {
            Lang.send(viewer, "ecsee.no-permission",
                    "<red>You lack permission.</red>");
            return true;
        }
        if (args.length < 1) {
            Lang.send(viewer, "ecsee.usage",
                    "<red>Usage: /ecsee <player></red>");
            return true;
        }

        final var plugin = OreoEssentials.get();
        final Logger log = plugin.getLogger();
        final boolean debug = plugin.getConfig().getBoolean("debug", false);
        EnderChestService svc = plugin.getEnderChestService();
        if (svc == null) {
            Lang.send(viewer, "ecsee.not-configured",
                    "<red>Ender chest storage is not configured.</red>");
            if (debug) {
                log.warning("[ECSEE] EnderChestService is null! Service not initialized.");
            }
            return true;
        }
        UUID targetId = resolveTargetId(args[0]);
        if (targetId == null) {
            Lang.send(viewer, "ecsee.not-found",
                    "<red>Player not found.</red>");
            return true;
        }

        String targetName;
        Player online = Bukkit.getPlayer(targetId);
        if (online != null) {
            targetName = online.getName();
        } else {
            OfflinePlayer off = Bukkit.getOfflinePlayer(targetId);
            targetName = (off.getName() != null ? off.getName() : args[0]);
        }
        int targetSlots;
        Player live = Bukkit.getPlayer(targetId);
        if (live != null && live.isOnline()) {
            targetSlots = svc.resolveSlots(live);
        } else {
            targetSlots = svc.resolveSlotsOffline(targetId);
        }

        int targetRows = Math.max(1, (int) Math.ceil(targetSlots / 9.0));
        int guiSize = targetRows * 9;

        if (debug) {
            log.info("[ECSEE] Opening EC for " + targetName + " (" + targetSlots + " slots, " + targetRows + " rows)");
        }

        // If the target player currently has their virtual EC open, read live from that
        // inventory — items aren't persisted to storage until they close it.
        // Use color-stripped title comparison: Paper 1.21 may serialize the title
        // differently from the raw §-coded string, causing a direct equals() to fail.
        ItemStack[] rawContents;
        boolean loadedFromLive;
        Player liveForLoad = Bukkit.getPlayer(targetId);
        if (liveForLoad != null && liveForLoad.isOnline()
                && isVirtualEcOpen(liveForLoad)) {
            rawContents = Arrays.copyOf(
                    liveForLoad.getOpenInventory().getTopInventory().getContents(), guiSize);
            // Strip any lock-barrier sentinels so they don't bleed into the admin view
            for (int i = 0; i < rawContents.length; i++) {
                if (svc.isLockItem(rawContents[i])) rawContents[i] = null;
            }
            loadedFromLive = true;
        } else {
            rawContents = svc.loadFor(targetId, targetRows);
            loadedFromLive = false;
        }
        // shouldSaveOnClose: only persist the admin's edits if we loaded real data.
        // If rawContents == null (no storage entry) AND we didn't load from live,
        // the admin saw an empty GUI because we had no data — saving would corrupt
        // items that exist in a live EC we failed to read.
        final boolean shouldSaveOnClose = loadedFromLive || rawContents != null;

        ItemStack[] contents = EnderChestStorage.clamp(rawContents, targetRows);
        if (contents == null) {
            contents = new ItemStack[guiSize];
        }

        String guiTitle = Lang.msgLegacy("ecsee.gui.title",
                "<dark_purple>Ender Chest</dark_purple> <gray>(%player%)</gray>",
                Map.of("player", targetName),
                viewer);

        Inventory gui = Bukkit.createInventory(null, guiSize, guiTitle);
        gui.setContents(contents);
        OreScheduler.runForEntity(plugin, viewer, () -> viewer.openInventory(gui));

        UUID viewerId = viewer.getUniqueId();
        final int finalTargetRows = targetRows;
        final int finalGuiSize = guiSize;
        final String finalTargetName = targetName;

        Listener closeListener = new Listener() {
            @EventHandler(priority = EventPriority.MONITOR)
            public void onClose(InventoryCloseEvent e) {
                if (!(e.getPlayer() instanceof Player p)) return;
                if (!p.getUniqueId().equals(viewerId)) return;
                if (e.getInventory() != gui) return;

                ItemStack[] edited = Arrays.copyOf(gui.getContents(), finalGuiSize);

                // Only persist if we loaded real data on open. If we had no stored data
                // AND no live inventory to read, saving would overwrite items that exist
                // in a live EC we couldn't access (e.g. title check race).
                if (shouldSaveOnClose) {
                    svc.saveFor(targetId, finalTargetRows, edited);

                    // Push changes to the player's live virtual EC only when we originally
                    // loaded from that same live inventory — pushing from a stale/storage
                    // load could overwrite items the player placed after we opened ecsee.
                    if (loadedFromLive) {
                        Player liveNow = Bukkit.getPlayer(targetId);
                        if (liveNow != null && liveNow.isOnline()) {
                            try {
                                if (isVirtualEcOpen(liveNow)) {
                                    Inventory targetGui = liveNow.getOpenInventory().getTopInventory();
                                    for (int i = 0; i < Math.min(finalGuiSize, targetGui.getSize()); i++) {
                                        targetGui.setItem(i, edited[i]);
                                    }
                                    svc.saveFromInventory(liveNow, targetGui);
                                    if (debug) log.info("[ECSEE] Updated " + finalTargetName + "'s open EC GUI");
                                }
                            } catch (Throwable t) {
                                log.warning("[ECSEE] Failed to update live EC for " + finalTargetName + ": " + t.getMessage());
                            }
                        }
                    }
                } else {
                    if (debug) log.warning("[ECSEE] Skipped save for " + finalTargetName
                            + " — no data was loaded (live EC unreadable and no storage entry). "
                            + "Player's items are safe.");
                }

                if (debug) {
                    int nonEmpty = 0;
                    for (ItemStack it : edited) {
                        if (it != null && !it.getType().isAir()) nonEmpty++;
                    }
                    log.info("[ECSEE] Saved " + nonEmpty + " items for " + finalTargetName);
                }

                HandlerList.unregisterAll(this);

                Lang.send(p, "ecsee.saved",
                        "<green>Saved changes to <aqua>%player%</aqua>'s ender chest.</green>",
                        Map.of("player", finalTargetName));
            }
        };

        Bukkit.getPluginManager().registerEvents(closeListener, plugin);

        Lang.send(viewer, "ecsee.opening",
                "<gray>Opening ender chest of <aqua>%player%</aqua>...</gray>",
                Map.of("player", targetName));

        return true;
    }

    /**
     * Returns true if the player currently has the plugin's virtual ender chest open.
     * Uses color-stripped title comparison because Paper 1.21 may serialize the
     * inventory title differently from the raw §-coded string stored in TITLE.
     */
    private static boolean isVirtualEcOpen(Player p) {
        try {
            if (p.getOpenInventory() == null) return false;
            String viewTitle = org.bukkit.ChatColor.stripColor(p.getOpenInventory().getTitle());
            String ecTitle   = org.bukkit.ChatColor.stripColor(EnderChestService.TITLE);
            return ecTitle != null && ecTitle.equalsIgnoreCase(viewTitle);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static UUID resolveTargetId(String arg) {
        Player p = Bukkit.getPlayerExact(arg);
        if (p != null) return p.getUniqueId();

        try {
            return UUID.fromString(arg);
        } catch (IllegalArgumentException ignored) { }

        try {
            var plugin = OreoEssentials.get();
            var dir = plugin.getPlayerDirectory();
            if (dir != null) {
                UUID global = dir.lookupUuidByName(arg);
                if (global != null) return global;
            }
        } catch (Throwable ignored) { }

        return fr.elias.oreoEssentials.util.Uuids.resolve(arg);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender,
                                      org.bukkit.command.Command cmd,
                                      String alias,
                                      String[] args) {
        if (args.length != 1) return List.of();

        String partial = args[0];
        String want = partial.toLowerCase(Locale.ROOT);

        Set<String> out = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        for (Player p : Bukkit.getOnlinePlayers()) {
            String n = p.getName();
            if (n != null && n.toLowerCase(Locale.ROOT).startsWith(want)) {
                out.add(n);
            }
        }

        var plugin = OreoEssentials.get();
        var dir = plugin.getPlayerDirectory();
        if (dir != null) {
            try {
                var names = dir.suggestOnlineNames(want, 50);
                if (names != null) {
                    for (String n : names) {
                        if (n != null && n.toLowerCase(Locale.ROOT).startsWith(want)) {
                            out.add(n);
                        }
                    }
                }
            } catch (Throwable ignored) { }
        }

        return out.stream().limit(50).toList();
    }
}
