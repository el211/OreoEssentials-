package fr.elias.oreoEssentials.commands.core.playercommands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.enderchest.EnderChestService;
import fr.elias.oreoEssentials.enderchest.EnderChestStorage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
import java.util.stream.Collectors;

public class EcSeeCommand implements OreoCommand, TabCompleter {
    @Override public String name() { return "ecsee"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.ecsee"; }
    @Override public String usage() { return "<player>"; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player viewer)) return true;

        if (!viewer.hasPermission(permission())) {
            viewer.sendMessage(ChatColor.RED + "You lack permission.");
            return true;
        }
        if (args.length < 1) {
            viewer.sendMessage(ChatColor.RED + "Usage: /ecsee <player>");
            return true;
        }

        final var plugin = OreoEssentials.get();
        final Logger log = plugin.getLogger();
        final boolean debug = plugin.getConfig().getBoolean("debug", false);

        UUID targetId = resolveTargetId(args[0]);
        if (targetId == null) {
            viewer.sendMessage(ChatColor.RED + "Player not found.");
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

        EnderChestService svc = Bukkit.getServicesManager().load(EnderChestService.class);
        if (svc == null) {
            viewer.sendMessage(ChatColor.RED + "Ender chest storage is not configured.");
            return true;
        }

        // ---- FIXED: Determine target's actual slot count ----
        int targetSlots;
        Player live = Bukkit.getPlayer(targetId);
        if (live != null && live.isOnline()) {
            // If player is online, use their actual permissions
            targetSlots = svc.resolveSlots(live);
        } else {
            // If offline, use best estimate
            targetSlots = svc.resolveSlotsOffline(targetId);
        }

        // Calculate rows needed for target's slot count
        int targetRows = Math.max(1, (int) Math.ceil(targetSlots / 9.0));
        int guiSize = targetRows * 9;

        if (debug) {
            log.info("[ECSEE] Target=" + targetName + " has " + targetSlots + " slots (" + targetRows + " rows)");
        }

        // ---- Decide best source for contents ----
        ItemStack[] contents;
        String source;

        if (live != null && live.isOnline()) {
            boolean viewingVirtual = live.getOpenInventory() != null
                    && EnderChestService.TITLE.equals(live.getOpenInventory().getTitle());

            if (viewingVirtual) {
                contents = Arrays.copyOf(live.getOpenInventory().getTopInventory().getContents(), guiSize);
                source = "LIVE_VIRTUAL_GUI";
            } else {
                contents = EnderChestStorage.clamp(svc.loadFor(targetId, targetRows), targetRows);
                source = "SERVICE_SNAPSHOT_ONLINE";
            }

            if (debug) {
                ItemStack[] vanilla = Arrays.copyOf(live.getEnderChest().getContents(), Math.min(27, guiSize));
                int vanillaNonEmpty = countNonEmpty(vanilla);
                log.info("[ECSEE] Online target=" + targetName
                        + " source=" + source
                        + " snapshotNonEmpty=" + countNonEmpty(contents)
                        + " vanillaNonEmpty=" + vanillaNonEmpty);
            }
        } else {
            contents = EnderChestStorage.clamp(svc.loadFor(targetId, targetRows), targetRows);
            source = "SERVICE_SNAPSHOT_OFFLINE";
            if (debug) {
                log.info("[ECSEE] Offline target=" + targetName
                        + " source=" + source
                        + " snapshotNonEmpty=" + countNonEmpty(contents));
            }
        }

        // ---- Open proxy GUI with correct size ----
        Inventory gui = Bukkit.createInventory(
                null,
                guiSize,
                ChatColor.DARK_PURPLE + "Ender Chest " + ChatColor.GRAY + "(" + targetName + ")"
        );
        gui.setContents(EnderChestStorage.clamp(contents, targetRows));
        viewer.openInventory(gui);

        // ---- Save on close ----
        UUID viewerId = viewer.getUniqueId();
        String finalSource = source;
        final int finalTargetRows = targetRows;
        final int finalGuiSize = guiSize;

        Listener l = new Listener() {
            @EventHandler(priority = EventPriority.MONITOR)
            public void onClose(InventoryCloseEvent e) {
                if (!(e.getPlayer() instanceof Player p)) return;
                if (!p.getUniqueId().equals(viewerId)) return;
                if (e.getInventory() != gui) return;

                ItemStack[] edited = Arrays.copyOf(gui.getContents(), finalGuiSize);

                // Persist (so cross-server & future joins see changes)
                svc.saveFor(targetId, finalTargetRows, edited);

                // If target is online on THIS server, try to mirror live view
                Player liveNow = Bukkit.getPlayer(targetId);
                if (liveNow != null && liveNow.isOnline()) {
                    try {
                        boolean viewingVirtual =
                                liveNow.getOpenInventory() != null
                                        && EnderChestService.TITLE.equals(liveNow.getOpenInventory().getTitle());

                        if (viewingVirtual) {
                            Inventory targetGui = liveNow.getOpenInventory().getTopInventory();
                            for (int i = 0; i < Math.min(finalGuiSize, targetGui.getSize()); i++) {
                                targetGui.setItem(i, edited[i]);
                            }
                            svc.saveFromInventory(liveNow, targetGui);
                            if (debug) log.info("[ECSEE] Mirrored changes to target's VIRTUAL GUI. target=" + targetName);
                        } else {
                            // Only write to vanilla enderchest if it can fit (vanilla EC is max 27 slots)
                            ItemStack[] vanillaContents = Arrays.copyOf(edited, Math.min(27, edited.length));
                            liveNow.getEnderChest().setContents(vanillaContents);
                            if (debug) log.info("[ECSEE] Wrote changes to target's VANILLA EC. target=" + targetName);
                        }
                    } catch (Throwable t) {
                        log.warning("[ECSEE] Failed to push live changes for " + targetName + ": " + t.getMessage());
                    }
                }

                if (debug) {
                    log.info("[ECSEE] Saved " + countNonEmpty(edited) + " non-empty slots for " + targetName
                            + " (initialSource=" + finalSource + ")");
                }
                HandlerList.unregisterAll(this);
                p.sendMessage(ChatColor.GREEN + "Saved changes to " + ChatColor.AQUA + targetName + ChatColor.GREEN + "'s ender chest.");
            }
        };
        Bukkit.getPluginManager().registerEvents(l, OreoEssentials.get());
        return true;
    }

    private static int countNonEmpty(ItemStack[] arr) {
        int c = 0;
        if (arr == null) return 0;
        for (ItemStack it : arr) if (it != null && it.getType() != org.bukkit.Material.AIR) c++;
        return c;
    }

    /** Minimal, API-safe resolver: exact online → UUID string → plugin resolver. */
    private static UUID resolveTargetId(String arg) {
        // 1) Exact online name on this server
        Player p = Bukkit.getPlayerExact(arg);
        if (p != null) return p.getUniqueId();

        // 2) Try parsing as UUID
        try {
            return UUID.fromString(arg);
        } catch (IllegalArgumentException ignored) { }

        // 3) Network-wide via PlayerDirectory (Mongo-backed)
        try {
            var plugin = OreoEssentials.get();
            var dir = plugin.getPlayerDirectory();
            if (dir != null) {
                UUID global = dir.lookupUuidByName(arg);
                if (global != null) return global;
            }
        } catch (Throwable ignored) { }

        // 4) Final fallback: your old resolver (Floodgate, etc.)
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

        // 1) Local online players
        for (Player p : Bukkit.getOnlinePlayers()) {
            String n = p.getName();
            if (n != null && n.toLowerCase(Locale.ROOT).startsWith(want)) {
                out.add(n);
            }
        }

        // 2) Network-wide via PlayerDirectory.suggestOnlineNames()
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

        // Limit to 50 suggestions for sanity
        return out.stream().limit(50).toList();
    }

}