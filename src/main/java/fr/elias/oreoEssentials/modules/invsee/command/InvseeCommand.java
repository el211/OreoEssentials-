package fr.elias.oreoEssentials.modules.invsee.command;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.modules.invsee.InvseeService;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.stream.Collectors;

public class InvseeCommand implements OreoCommand, TabCompleter {

    @Override public String name()       { return "invsee"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.invsee"; }
    @Override public String usage()      { return "<player>"; }
    @Override public boolean playerOnly(){ return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player viewer)) return true;

        if (!viewer.hasPermission(permission())) {
            Lang.send(viewer, "invsee.no-permission",
                    "<red>You lack permission.</red>");
            return true;
        }

        if (args.length < 1) {
            Lang.send(viewer, "invsee.usage",
                    "<red>Usage: /invsee <player></red>");
            return true;
        }

        OreoEssentials plugin = OreoEssentials.get();
        InvseeService invseeService = plugin.getInvseeService();

        if (invseeService == null) {
            Lang.send(viewer, "invsee.not-available",
                    "<red>Invsee service is not available on this server.</red>");
            return true;
        }

        UUID targetId = resolveTargetId(args[0]);
        if (targetId == null) {
            Lang.send(viewer, "invsee.not-found",
                    "<red>Player not found.</red>");
            return true;
        }


        Player targetPlayer = Bukkit.getPlayer(targetId);

        String targetName;

        if (targetPlayer != null && targetPlayer.isOnline()) {
            targetName = targetPlayer.getName();

            openLocalInventory(viewer, targetPlayer, plugin);

            Lang.send(viewer, "invsee.opening",
                    "<gray>Opening live inventory of <aqua>%player%</aqua>...</gray>",
                    Map.of("player", targetName));

        } else {
            OfflinePlayer off = Bukkit.getOfflinePlayer(targetId);
            targetName = (off.getName() != null ? off.getName() : args[0]);

            invseeService.openLocalViewer(viewer, targetId, targetName);

            Lang.send(viewer, "invsee.opening",
                    "<gray>Opening live inventory of <aqua>%player%</aqua> (cross-server)...</gray>",
                    Map.of("player", targetName));
        }

        return true;
    }


    private void openLocalInventory(Player viewer, Player target, OreoEssentials plugin) {
        String title = Lang.msgLegacy("invsee.gui.title",
                "<dark_purple>Inventory</dark_purple> <gray>(%player%)</gray>",
                Map.of("player", target.getName()),
                viewer);

        Inventory gui = Bukkit.createInventory(null, 54, title);

        ItemStack[] contents = target.getInventory().getContents();
        ItemStack[] armor = target.getInventory().getArmorContents();
        ItemStack offhand = target.getInventory().getItemInOffHand();

        for (int i = 0; i < 36 && i < contents.length; i++) {
            gui.setItem(i, contents[i]);
        }

        if (armor.length >= 4) {
            gui.setItem(36, armor[3]); // Boots
            gui.setItem(37, armor[2]); // Leggings
            gui.setItem(38, armor[1]); // Chestplate
            gui.setItem(39, armor[0]); // Helmet
        }

        gui.setItem(40, offhand);

        viewer.openInventory(gui);

        InvseeListener listener = new InvseeListener(viewer, target, gui);
        Bukkit.getPluginManager().registerEvents(listener, plugin);
    }


    private static class InvseeListener implements Listener {
        private final UUID viewerId;
        private final UUID targetId;
        private final Inventory gui;

        public InvseeListener(Player viewer, Player target, Inventory gui) {
            this.viewerId = viewer.getUniqueId();
            this.targetId = target.getUniqueId();
            this.gui = gui;
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onClick(InventoryClickEvent e) {
            if (!(e.getWhoClicked() instanceof Player p)) return;
            if (!p.getUniqueId().equals(viewerId)) return;
            if (e.getInventory() != gui) return;

        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onDrag(InventoryDragEvent e) {
            if (!(e.getWhoClicked() instanceof Player p)) return;
            if (!p.getUniqueId().equals(viewerId)) return;
            if (e.getInventory() != gui) return;

        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onClose(InventoryCloseEvent e) {
            if (!(e.getPlayer() instanceof Player p)) return;
            if (!p.getUniqueId().equals(viewerId)) return;
            if (e.getInventory() != gui) return;

            Player target = Bukkit.getPlayer(targetId);
            if (target != null && target.isOnline()) {
                try {
                    ItemStack[] contents = new ItemStack[36];
                    for (int i = 0; i < 36; i++) {
                        contents[i] = gui.getItem(i);
                    }
                    target.getInventory().setContents(contents);

                    ItemStack[] armor = new ItemStack[4];
                    armor[0] = gui.getItem(39); // Helmet
                    armor[1] = gui.getItem(38); // Chestplate
                    armor[2] = gui.getItem(37); // Leggings
                    armor[3] = gui.getItem(36); // Boots
                    target.getInventory().setArmorContents(armor);

                    ItemStack offhand = gui.getItem(40);
                    target.getInventory().setItemInOffHand(offhand);

                    target.updateInventory();

                    Lang.send(p, "invsee.saved",
                            "<green>Saved changes to <aqua>%player%</aqua>'s inventory.</green>",
                            Map.of("player", target.getName()));

                } catch (Exception ex) {
                    OreoEssentials.get().getLogger().warning(
                            "[INVSEE] Failed to sync inventory for " + target.getName() + ": " + ex.getMessage()
                    );
                }
            }

            HandlerList.unregisterAll(this);
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

        return out.stream().limit(50).collect(Collectors.toList());
    }
}