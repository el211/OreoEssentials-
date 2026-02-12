// File: src/main/java/fr/elias/oreoEssentials/playerwarp/command/PlayerWarpCommand.java
package fr.elias.oreoEssentials.modules.playerwarp.command;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.modules.playerwarp.PlayerWarp;
import fr.elias.oreoEssentials.modules.playerwarp.PlayerWarpService;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.stream.Collectors;

/**
 *
 * Commands:
 * - /pw set <name> - Create warp
 * - /pw remove <warp> - Delete warp
 * - /pw list [player] - List warps
 * - /pw gui - Browse all warps GUI
 * - /pw mywarps - Personal warps GUI
 * - /pw <warp> [password] - Teleport to warp
 * - /pw use <warp> [password] - Teleport with password
 * - /pw whitelist <action> <warp> [player] - Manage whitelist
 * - /pw desc <warp> [text] - Set description
 * - /pw category <warp> [category] - Set category
 * - /pw lock <warp> [on|off] - Toggle lock
 * - /pw icon <warp> [clear] - Set icon from hand
 * - /pw cost <warp> <amount> - Set cost
 * - /pw password <warp> <pwd|off> - Set password
 * - /pw managers <warp> [player] - Manage managers
 * - And many more admin commands...
 *
 * Password System:
 * - Warps can be password-protected
 * - Owner/managers/bypass perm skip password
 * - Others must use: /pw use <warp> <password>
 * - GUI prompts for password via chat
 */
public class PlayerWarpCommand implements OreoCommand {

    private final PlayerWarpService service;
    private final Map<UUID, Integer> extraWarps = new HashMap<>();

    public PlayerWarpCommand(PlayerWarpService service) {
        this.service = service;
    }

    @Override public String name() { return "pw"; }
    @Override public List<String> aliases() { return List.of("playerwarp", "pwarp"); }
    @Override public String permission() { return "oe.pw.base"; }

    @Override
    public String usage() {
        return "help|set|remove|list|amount|rtp|near|reset|rename|setowner|removeall|reload|addwarps|whitelist|desc|category|lock|icon|cost|managers|password|use|gui|mywarps|<warp> [password]";
    }

    @Override public boolean playerOnly() { return false; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        final OreoEssentials plugin = OreoEssentials.get();

        if (!(sender.hasPermission("oe.pw.base") || sender.isOp())) {
            Lang.send(sender, "pw.no-permission-base",
                    "<red>You don't have permission to use player warps.</red>",
                    Map.of());
            return true;
        }

        Player actor = (sender instanceof Player p) ? p : null;

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {

            case "set" -> {
                if (actor == null) {
                    Lang.send(sender, "pw.only-players-sub",
                            "<red>Only players can use /%sub%.</red>",
                            Map.of("sub", "set"));
                    return true;
                }

                if (!actor.hasPermission("oe.pw.set")) {
                    Lang.send(actor, "pw.no-permission-set",
                            "<red>You don't have permission to set warps.</red>",
                            Map.of());
                    return true;
                }
                if (args.length < 2) {
                    Lang.send(actor, "pw.usage-set",
                            "<yellow>Usage: /%label% set <n></yellow>",
                            Map.of("label", label));
                    return true;
                }
                String name = args[1];

                int baseLimit = service.getLimit(actor);
                int extra = extraWarps.getOrDefault(actor.getUniqueId(), 0);
                int effectiveLimit = baseLimit + extra;

                if (!service.isUnderLimit(actor, effectiveLimit) &&
                        !actor.hasPermission("oe.pw.limit.bypass")) {
                    Lang.send(actor, "pw.limit-reached",
                            "<red>You've reached your warp limit (<white>%limit%</white>).</red>",
                            Map.of("limit", String.valueOf(effectiveLimit)));
                    return true;
                }

                Location loc = actor.getLocation();
                PlayerWarp created = service.createWarp(actor, name, loc);
                if (created == null) {
                    Lang.send(actor, "pw.already-exists",
                            "<red>You already have a warp named <yellow>%name%</yellow>.</red>",
                            Map.of("name", name.toLowerCase(Locale.ROOT)));
                } else {
                    Lang.send(actor, "pw.set-success",
                            "<green>Created warp <aqua>%name%</aqua>.</green>",
                            Map.of("name", created.getName()));
                }
                return true;
            }

            case "gui" -> {
                if (actor == null) {
                    Lang.send(sender, "pw.only-players-sub",
                            "<red>Only players can use /%sub%.</red>",
                            Map.of("sub", "gui"));
                    return true;
                }

                if (!actor.hasPermission("oe.pw.gui")) {
                    Lang.send(actor, "pw.no-permission-list",
                            "<red>You don't have permission to list warps.</red>",
                            Map.of());
                    return true;
                }
                fr.elias.oreoEssentials.modules.playerwarp.gui.PlayerWarpBrowseMenu.open(actor, service);
                return true;
            }

            case "mywarps" -> {
                if (actor == null) {
                    Lang.send(sender, "pw.only-players-sub",
                            "<red>Only players can use /%sub%.</red>",
                            Map.of("sub", "mywarps"));
                    return true;
                }

                if (!actor.hasPermission("oe.pw.mywarps")) {
                    Lang.send(actor, "pw.no-permission-list",
                            "<red>You don't have permission to list warps.</red>",
                            Map.of());
                    return true;
                }
                fr.elias.oreoEssentials.modules.playerwarp.gui.MyPlayerWarpsMenu.open(actor, service);
                return true;
            }

            case "remove" -> {
                if (actor == null) {
                    Lang.send(sender, "pw.only-players-sub",
                            "<red>Only players can use /%sub%.</red>",
                            Map.of("sub", "remove"));
                    return true;
                }

                if (args.length < 2) {
                    Lang.send(actor, "pw.usage-remove",
                            "<yellow>Usage: /%label% remove <warp></yellow>",
                            Map.of("label", label));
                    return true;
                }
                String name = args[1];
                PlayerWarp warp = service.findByOwnerAndName(actor.getUniqueId(), name);
                if (warp == null) {
                    Lang.send(actor, "pw.not-found-owner",
                            "<red>You don't have a warp named <yellow>%name%</yellow>.</red>",
                            Map.of("name", name.toLowerCase(Locale.ROOT)));
                    return true;
                }
                if (!actor.hasPermission("oe.pw.remove") &&
                        !actor.hasPermission("oe.pw.admin.remove")) {
                    Lang.send(actor, "pw.no-permission-remove",
                            "<red>You don't have permission to remove warps.</red>",
                            Map.of());
                    return true;
                }
                if (service.deleteWarp(warp)) {
                    Lang.send(actor, "pw.remove-success",
                            "<green>Removed warp <aqua>%name%</aqua>.</green>",
                            Map.of("name", warp.getName()));
                } else {
                    Lang.send(actor, "pw.remove-failed",
                            "<red>Failed to remove warp <yellow>%name%</yellow>.</red>",
                            Map.of("name", warp.getName()));
                }
                return true;
            }

            case "list" -> {
                if (!sender.hasPermission("oe.pw.list")) {
                    Lang.send(sender, "pw.no-permission-list",
                            "<red>You don't have permission to list warps.</red>",
                            Map.of());
                    return true;
                }

                if (args.length >= 2) {
                    String targetName = args[1];
                    Player target = Bukkit.getPlayerExact(targetName);
                    if (target == null) {
                        Lang.send(sender, "pw.list-target-offline",
                                "<red>Player <yellow>%player%</yellow> is not online.</red>",
                                Map.of("player", targetName));
                        return true;
                    }

                    var warps = service.listByOwner(target.getUniqueId());
                    sendList(sender, target.getName(), warps);
                } else {
                    if (actor == null) {
                        var warps = service.listAll();
                        sendList(sender, "ALL", warps);
                    } else {
                        var warps = service.listByOwner(actor.getUniqueId());
                        sendList(sender, actor.getName(), warps);
                    }
                }
                return true;
            }

            case "amount" -> {
                if (!sender.hasPermission("oe.pw.amount")) {
                    Lang.send(sender, "pw.no-permission-amount",
                            "<red>You don't have permission.</red>",
                            Map.of());
                    return true;
                }

                if (args.length >= 2) {
                    String targetName = args[1];
                    Player target = Bukkit.getPlayerExact(targetName);
                    if (target == null) {
                        Lang.send(sender, "pw.amount-target-offline",
                                "<red>Player <yellow>%player%</yellow> is not online.</red>",
                                Map.of("player", targetName));
                        return true;
                    }

                    int count = service.getWarpCount(target);
                    Lang.send(sender, "pw.amount-other",
                            "<green><yellow>%player%</yellow> has <white>%amount%</white> warp(s).</green>",
                            Map.of("player", target.getName(), "amount", String.valueOf(count)));
                } else {
                    if (actor == null) {
                        Lang.send(sender, "pw.amount-console-usage",
                                "<red>Usage: /pw amount <player></red>",
                                Map.of());
                        return true;
                    }

                    int count = service.getWarpCount(actor);
                    Lang.send(actor, "pw.amount-self",
                            "<green>You have <white>%amount%</white> warp(s).</green>",
                            Map.of("amount", String.valueOf(count)));
                }
                return true;
            }

            case "rtp" -> {
                if (actor == null) {
                    Lang.send(sender, "pw.only-players-sub",
                            "<red>Only players can use /%sub%.</red>",
                            Map.of("sub", "rtp"));
                    return true;
                }

                if (!actor.hasPermission("oe.pw.rtp")) {
                    Lang.send(actor, "pw.no-permission-rtp",
                            "<red>You don't have permission.</red>",
                            Map.of());
                    return true;
                }

                List<PlayerWarp> all = service.listAll();
                if (all.isEmpty()) {
                    Lang.send(actor, "pw.rtp-none",
                            "<red>No warps available for random teleport.</red>",
                            Map.of());
                    return true;
                }

                List<PlayerWarp> usable = all.stream()
                        .filter(w -> service.canUse(actor, w))
                        .filter(w -> !isPasswordProtected(w, actor))
                        .collect(Collectors.toList());

                if (usable.isEmpty()) {
                    Lang.send(actor, "pw.rtp-none-usable",
                            "<red>No warps you can access.</red>",
                            Map.of());
                    return true;
                }

                PlayerWarp targetWarp = usable.get(new Random().nextInt(usable.size()));

                teleportWithRouting(plugin, actor, targetWarp);
                return true;
            }

            case "near" -> {
                if (actor == null) {
                    Lang.send(sender, "pw.only-players-sub",
                            "<red>Only players can use /%sub%.</red>",
                            Map.of("sub", "near"));
                    return true;
                }

                if (!actor.hasPermission("oe.pw.near")) {
                    Lang.send(actor, "pw.no-permission-near",
                            "<red>You don't have permission.</red>",
                            Map.of());
                    return true;
                }

                int page = 1;
                if (args.length >= 2) {
                    try {
                        page = Math.max(1, Integer.parseInt(args[1]));
                    } catch (NumberFormatException ignored) {
                    }
                }

                double maxDistance = 200.0;
                Location loc = actor.getLocation();
                List<PlayerWarp> nearby = service.listAll().stream()
                        .filter(w -> w.getLocation() != null
                                && w.getLocation().getWorld() != null
                                && w.getLocation().getWorld().equals(loc.getWorld())
                                && w.getLocation().distanceSquared(loc) <= maxDistance * maxDistance)
                        .sorted(Comparator.comparingDouble(w -> w.getLocation().distanceSquared(loc)))
                        .collect(Collectors.toList());

                if (nearby.isEmpty()) {
                    Lang.send(actor, "pw.near-none",
                            "<gray>No warps found within <white>%radius%</white> blocks.</gray>",
                            Map.of("radius", String.valueOf((int) maxDistance)));
                    return true;
                }

                int perPage = 10;
                int from = (page - 1) * perPage;
                int to = Math.min(from + perPage, nearby.size());
                if (from >= nearby.size()) {
                    Lang.send(actor, "pw.near-page-empty",
                            "<red>Page <yellow>%page%</yellow> is empty.</red>",
                            Map.of("page", String.valueOf(page)));
                    return true;
                }

                actor.sendMessage("§8§m------------------------");
                actor.sendMessage("§bNearby player warps (page " + page + "):");
                for (int i = from; i < to; i++) {
                    PlayerWarp w = nearby.get(i);
                    double dist = Math.sqrt(w.getLocation().distanceSquared(loc));
                    actor.sendMessage("§7- §a" + w.getName() + " §7(" + String.format(Locale.US, "%.1f", dist) + "m)");
                }
                actor.sendMessage("§8§m------------------------");
                return true;
            }

            case "reset" -> {
                if (actor == null) {
                    Lang.send(sender, "pw.only-players-sub",
                            "<red>Only players can use /%sub%.</red>",
                            Map.of("sub", "reset"));
                    return true;
                }

                if (!actor.hasPermission("oe.pw.reset")) {
                    Lang.send(actor, "pw.no-permission-reset",
                            "<red>You don't have permission.</red>",
                            Map.of());
                    return true;
                }
                if (args.length < 2) {
                    Lang.send(actor, "pw.usage-reset",
                            "<yellow>Usage: /%label% reset <warp></yellow>",
                            Map.of("label", label));
                    return true;
                }
                String name = args[1];
                PlayerWarp warp = service.findByOwnerAndName(actor.getUniqueId(), name);
                if (warp == null) {
                    Lang.send(actor, "pw.not-found-owner",
                            "<red>You don't have a warp named <yellow>%name%</yellow>.</red>",
                            Map.of("name", name.toLowerCase(Locale.ROOT)));
                    return true;
                }

                Location newLoc = actor.getLocation();
                service.deleteWarp(warp);
                PlayerWarp recreated = service.createWarp(actor, warp.getName(), newLoc);
                if (recreated != null) {
                    Lang.send(actor, "pw.reset-success",
                            "<green>Reset warp <aqua>%name%</aqua> to your current location.</green>",
                            Map.of("name", recreated.getName()));
                } else {
                    Lang.send(actor, "pw.reset-failed",
                            "<red>Failed to reset warp <yellow>%name%</yellow>.</red>",
                            Map.of("name", warp.getName()));
                }
                return true;
            }

            case "rename" -> {
                if (actor == null) {
                    Lang.send(sender, "pw.only-players-sub",
                            "<red>Only players can use /%sub%.</red>",
                            Map.of("sub", "rename"));
                    return true;
                }

                if (!actor.hasPermission("oe.pw.rename")) {
                    Lang.send(actor, "pw.no-permission-rename",
                            "<red>You don't have permission.</red>",
                            Map.of());
                    return true;
                }
                if (args.length < 3) {
                    Lang.send(actor, "pw.usage-rename",
                            "<yellow>Usage: /%label% rename <warp> <newName></yellow>",
                            Map.of("label", label));
                    return true;
                }
                String oldName = args[1];
                String newName = args[2];

                PlayerWarp warp = service.findByOwnerAndName(actor.getUniqueId(), oldName);
                if (warp == null) {
                    Lang.send(actor, "pw.not-found-owner",
                            "<red>You don't have a warp named <yellow>%name%</yellow>.</red>",
                            Map.of("name", oldName.toLowerCase(Locale.ROOT)));
                    return true;
                }

                Location loc = warp.getLocation();
                service.deleteWarp(warp);
                PlayerWarp renamed = service.createWarp(actor, newName, loc);
                if (renamed != null) {
                    Lang.send(actor, "pw.rename-success",
                            "<green>Renamed <yellow>%old%</yellow> to <aqua>%name%</aqua>.</green>",
                            Map.of("old", oldName, "name", renamed.getName()));
                } else {
                    Lang.send(actor, "pw.rename-failed",
                            "<red>Failed to rename <yellow>%old%</yellow> to <yellow>%name%</yellow>.</red>",
                            Map.of("old", oldName, "name", newName));
                }
                return true;
            }

            case "setowner" -> {
                if (!sender.hasPermission("oe.pw.setowner")) {
                    Lang.send(sender, "pw.no-permission-setowner",
                            "<red>You don't have permission.</red>",
                            Map.of());
                    return true;
                }
                if (args.length < 3) {
                    Lang.send(sender, "pw.usage-setowner",
                            "<yellow>Usage: /%label% setowner <warp> <player></yellow>",
                            Map.of("label", label));
                    return true;
                }

                String warpName = args[1];
                String targetName = args[2];

                PlayerWarp warp = service.listAll().stream()
                        .filter(w -> w.getName().equalsIgnoreCase(warpName))
                        .findFirst()
                        .orElse(null);

                if (warp == null) {
                    Lang.send(sender, "pw.not-found",
                            "<red>Warp <yellow>%name%</yellow> not found.</red>",
                            Map.of("name", warpName.toLowerCase(Locale.ROOT)));
                    return true;
                }

                Player newOwner = Bukkit.getPlayerExact(targetName);
                if (newOwner == null) {
                    sender.sendMessage("§cPlayer '" + targetName + "' must be online for setowner (simplified implementation).");
                    return true;
                }

                Location loc = warp.getLocation();
                service.deleteWarp(warp);
                PlayerWarp recreated = service.createWarp(newOwner, warp.getName(), loc);
                if (recreated != null) {
                    Lang.send(sender, "pw.setowner-success",
                            "<green>Transferred warp <aqua>%warp%</aqua> to <yellow>%player%</yellow>.</green>",
                            Map.of("warp", recreated.getName(), "player", newOwner.getName()));
                } else {
                    Lang.send(sender, "pw.setowner-failed",
                            "<red>Failed to transfer warp <yellow>%warp%</yellow>.</red>",
                            Map.of("warp", warp.getName(), "player", newOwner.getName()));
                }
                return true;
            }

            case "removeall" -> {
                if (!sender.hasPermission("oe.pw.admin.removeall")) {
                    Lang.send(sender, "pw.no-permission-removeall",
                            "<red>You don't have permission.</red>",
                            Map.of());
                    return true;
                }
                if (args.length < 2) {
                    Lang.send(sender, "pw.usage-removeall",
                            "<yellow>Usage: /%label% removeall <player></yellow>",
                            Map.of("label", label));
                    return true;
                }
                String targetName = args[1];
                Player target = Bukkit.getPlayerExact(targetName);
                OfflinePlayer off = target != null ? target : Bukkit.getOfflinePlayer(targetName);
                if (off == null || off.getUniqueId() == null) {
                    Lang.send(sender, "pw.player-resolve-failed",
                            "<red>Could not resolve player <yellow>%player%</yellow>.</red>",
                            Map.of("player", targetName));
                    return true;
                }

                List<PlayerWarp> warps = service.listByOwner(off.getUniqueId());
                int count = 0;
                for (PlayerWarp w : warps) {
                    if (service.deleteWarp(w)) count++;
                }

                Lang.send(sender, "pw.removeall-success",
                        "<green>Removed <white>%amount%</white> warp(s) from <yellow>%player%</yellow>.</green>",
                        Map.of(
                                "player", off.getName() == null ? targetName : off.getName(),
                                "amount", String.valueOf(count)));
                return true;
            }

            case "reload" -> {
                if (!sender.hasPermission("oe.pw.admin.reload")) {
                    Lang.send(sender, "pw.no-permission-reload",
                            "<red>You don't have permission.</red>",
                            Map.of());
                    return true;
                }

                plugin.reloadConfig();
                Lang.send(sender, "pw.reload-success",
                        "<green>Configuration reloaded.</green>",
                        Map.of());
                return true;
            }

            case "addwarps" -> {
                if (!sender.hasPermission("oe.pw.admin.addwarps")) {
                    Lang.send(sender, "pw.no-permission-addwarps",
                            "<red>You don't have permission.</red>",
                            Map.of());
                    return true;
                }
                if (args.length < 3) {
                    Lang.send(sender, "pw.usage-addwarps",
                            "<yellow>Usage: /%label% addwarps <player> <amount></yellow>",
                            Map.of("label", label));
                    return true;
                }

                String targetName = args[1];
                String amountStr = args[2];

                Player target = Bukkit.getPlayerExact(targetName);
                OfflinePlayer off = target != null ? target : Bukkit.getOfflinePlayer(targetName);
                if (off == null || off.getUniqueId() == null) {
                    sender.sendMessage("§cCould not resolve player '" + targetName + "'.");
                    return true;
                }

                int amount;
                try {
                    amount = Integer.parseInt(amountStr);
                } catch (NumberFormatException ex) {
                    Lang.send(sender, "pw.addwarps-invalid-amount",
                            "<red>Invalid amount.</red>",
                            Map.of());
                    return true;
                }

                UUID uuid = off.getUniqueId();
                int current = extraWarps.getOrDefault(uuid, 0);
                int newVal = current + amount;
                if (newVal < 0) newVal = 0;
                extraWarps.put(uuid, newVal);

                Lang.send(sender, "pw.addwarps-success",
                        "<green>Added <white>%amount%</white> extra warp slot(s) to <yellow>%player%</yellow>. Total extra: <white>%total%</white>.</green>",
                        Map.of(
                                "player", off.getName() == null ? targetName : off.getName(),
                                "amount", String.valueOf(amount),
                                "total", String.valueOf(newVal)));
                return true;
            }

            case "whitelist" -> {
                if (actor == null) {
                    Lang.send(sender, "pw.only-players-sub",
                            "<red>Only players can use /%sub%.</red>",
                            Map.of("sub", "whitelist"));
                    return true;
                }

                if (!actor.hasPermission("oe.pw.whitelist") &&
                        !actor.hasPermission("oe.pw.admin.whitelist")) {
                    Lang.send(actor, "pw.no-permission-whitelist",
                            "<red>You don't have permission.</red>",
                            Map.of());
                    return true;
                }

                if (args.length < 3) {
                    Lang.send(actor, "pw.usage-whitelist",
                            "<yellow>Usage: /%label% whitelist <enable|disable|list|set|remove> <warp> [player]</yellow>",
                            Map.of("label", label));
                    return true;
                }

                String action = args[1].toLowerCase(Locale.ROOT);
                String warpName = args[2];

                PlayerWarp warp = service.findByOwnerAndName(actor.getUniqueId(), warpName);
                if (warp == null && actor.hasPermission("oe.pw.admin.whitelist")) {
                    warp = service.listAll().stream()
                            .filter(w -> w.getName().equalsIgnoreCase(warpName))
                            .findFirst()
                            .orElse(null);
                }

                if (warp == null) {
                    Lang.send(actor, "pw.not-found-owner",
                            "<red>You don't have a warp named <yellow>%name%</yellow>.</red>",
                            Map.of("name", warpName.toLowerCase(Locale.ROOT)));
                    return true;
                }

                boolean isOwner = warp.getOwner().equals(actor.getUniqueId());
                boolean isAdmin = actor.hasPermission("oe.pw.admin.whitelist");
                if (!isOwner && !isAdmin) {
                    Lang.send(actor, "pw.whitelist-not-owner",
                            "<red>You don't own <yellow>%warp%</yellow>.</red>",
                            Map.of("warp", warp.getName()));
                    return true;
                }

                switch (action) {
                    case "enable" -> {
                        warp.setWhitelistEnabled(true);
                        service.saveWarp(warp);
                        Lang.send(actor, "pw.whitelist-enabled",
                                "<green>Whitelist enabled for <white>%warp%</white>.</green>",
                                Map.of("warp", warp.getName()));
                        return true;
                    }

                    case "disable" -> {
                        warp.setWhitelistEnabled(false);
                        service.saveWarp(warp);
                        Lang.send(actor, "pw.whitelist-disabled",
                                "<yellow>Whitelist disabled for <white>%warp%</white>.</yellow>",
                                Map.of("warp", warp.getName()));
                        return true;
                    }

                    case "list" -> {
                        Set<UUID> wl = warp.getWhitelist();
                        actor.sendMessage("§8§m------------------------");
                        actor.sendMessage("§bWhitelist for warp §e" + warp.getName() + "§7 (" +
                                (warp.isWhitelistEnabled() ? "§aenabled" : "§cdisabled") + "§7):");

                        if (wl == null || wl.isEmpty()) {
                            actor.sendMessage("§7  (empty)");
                        } else {
                            for (UUID u : wl) {
                                OfflinePlayer off = Bukkit.getOfflinePlayer(u);
                                String name = (off.getName() == null ? u.toString() : off.getName());
                                actor.sendMessage("§7- §a" + name);
                            }
                        }
                        actor.sendMessage("§8§m------------------------");
                        return true;
                    }

                    case "set", "remove" -> {
                        if (args.length < 4) {
                            Lang.send(actor, "pw.usage-whitelist-" + action,
                                    "<yellow>Usage: /%label% whitelist " + action + " <warp> <player></yellow>",
                                    Map.of("label", label));
                            return true;
                        }

                        String targetName = args[3];
                        OfflinePlayer off = Bukkit.getOfflinePlayer(targetName);
                        if (off == null || off.getUniqueId() == null) {
                            actor.sendMessage("§cCould not resolve player '" + targetName + "'.");
                            return true;
                        }

                        UUID targetId = off.getUniqueId();
                        Set<UUID> wl = warp.getWhitelist();
                        if (wl == null) {
                            wl = new HashSet<>();
                            warp.setWhitelist(wl);
                        }

                        if (action.equals("set")) {
                            if (wl.add(targetId)) {
                                warp.setWhitelistEnabled(true);
                                service.saveWarp(warp);
                                Lang.send(actor, "pw.whitelist-added",
                                        "<green>Added <yellow>%player%</yellow> to whitelist of <white>%warp%</white>.</green>",
                                        Map.of(
                                                "warp", warp.getName(),
                                                "player", off.getName() == null ? targetName : off.getName()));
                            } else {
                                Lang.send(actor, "pw.whitelist-already",
                                        "<yellow>%player%</yellow> is already whitelisted on <white>%warp%</white>.",
                                        Map.of(
                                                "warp", warp.getName(),
                                                "player", off.getName() == null ? targetName : off.getName()));
                            }
                        } else {
                            if (wl.remove(targetId)) {
                                service.saveWarp(warp);
                                Lang.send(actor, "pw.whitelist-removed",
                                        "<green>Removed <yellow>%player%</yellow> from whitelist of <white>%warp%</white>.</green>",
                                        Map.of(
                                                "warp", warp.getName(),
                                                "player", off.getName() == null ? targetName : off.getName()));
                            } else {
                                Lang.send(actor, "pw.whitelist-not-found",
                                        "<red><yellow>%player%</yellow> is not on the whitelist of <white>%warp%</white>.</red>",
                                        Map.of(
                                                "warp", warp.getName(),
                                                "player", off.getName() == null ? targetName : off.getName()));
                            }
                        }
                        return true;
                    }

                    default -> {
                        Lang.send(actor, "pw.usage-whitelist",
                                "<yellow>Usage: /%label% whitelist <enable|disable|list|set|remove> <warp> [player]</yellow>",
                                Map.of("label", label));
                        return true;
                    }
                }
            }

            case "desc" -> {
                if (actor == null) {
                    Lang.send(sender, "pw.only-players-sub",
                            "<red>Only players can use /%sub%.</red>",
                            Map.of("sub", "desc"));
                    return true;
                }

                if (!actor.hasPermission("oe.pw.meta.desc")) {
                    Lang.send(actor, "pw.no-permission-desc",
                            "<red>You don't have permission.</red>",
                            Map.of());
                    return true;
                }
                if (args.length < 2) {
                    Lang.send(actor, "pw.usage-desc",
                            "<yellow>Usage: /%label% desc <warp> [description...]</yellow>",
                            Map.of("label", label));
                    return true;
                }

                String warpName = args[1];
                PlayerWarp warp = service.findByOwnerAndName(actor.getUniqueId(), warpName);
                if (warp == null && actor.hasPermission("oe.pw.admin.meta")) {
                    warp = service.listAll().stream()
                            .filter(w -> w.getName().equalsIgnoreCase(warpName))
                            .findFirst().orElse(null);
                }
                if (warp == null) {
                    Lang.send(actor, "pw.not-found-owner",
                            "<red>You don't have a warp named <yellow>%name%</yellow>.</red>",
                            Map.of("name", warpName.toLowerCase(Locale.ROOT)));
                    return true;
                }

                if (args.length == 2) {
                    String d = warp.getDescription();
                    if (d == null || d.isEmpty()) {
                        Lang.send(actor, "pw.desc-empty",
                                "<gray>Warp <white>%warp%</white> has no description.</gray>",
                                Map.of("warp", warp.getName()));
                    } else {
                        Lang.send(actor, "pw.desc-show",
                                "<gold>Description of <aqua>%warp%</aqua>:</gold> <white>%desc%</white>",
                                Map.of("warp", warp.getName(), "desc", d));
                    }
                    return true;
                }

                String newDesc = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                warp.setDescription(newDesc);
                service.saveWarp(warp);

                if (newDesc.isEmpty()) {
                    Lang.send(actor, "pw.desc-cleared",
                            "<green>Cleared description of <aqua>%warp%</aqua>.</green>",
                            Map.of("warp", warp.getName()));
                } else {
                    Lang.send(actor, "pw.desc-set",
                            "<green>Set description of <aqua>%warp%</aqua> to: <white>%desc%</white></green>",
                            Map.of("warp", warp.getName(), "desc", newDesc));
                }
                return true;
            }

            case "category" -> {
                if (actor == null) {
                    Lang.send(sender, "pw.only-players-sub",
                            "<red>Only players can use /%sub%.</red>",
                            Map.of("sub", "category"));
                    return true;
                }

                if (!actor.hasPermission("oe.pw.meta.category")) {
                    Lang.send(actor, "pw.no-permission-category",
                            "<red>You don't have permission.</red>",
                            Map.of());
                    return true;
                }
                if (args.length < 2) {
                    Lang.send(actor, "pw.usage-category",
                            "<yellow>Usage: /%label% category <warp> [category]</yellow>",
                            Map.of("label", label));
                    return true;
                }

                String warpName = args[1];
                PlayerWarp warp = service.findByOwnerAndName(actor.getUniqueId(), warpName);
                if (warp == null && actor.hasPermission("oe.pw.admin.meta")) {
                    warp = service.listAll().stream()
                            .filter(w -> w.getName().equalsIgnoreCase(warpName))
                            .findFirst().orElse(null);
                }
                if (warp == null) {
                    Lang.send(actor, "pw.not-found-owner",
                            "<red>You don't have a warp named <yellow>%name%</yellow>.</red>",
                            Map.of("name", warpName.toLowerCase(Locale.ROOT)));
                    return true;
                }

                if (args.length == 2) {
                    String c = warp.getCategory();
                    if (c == null || c.isEmpty()) {
                        Lang.send(actor, "pw.category-empty",
                                "<gray>Warp <white>%warp%</white> has no category.</gray>",
                                Map.of("warp", warp.getName()));
                    } else {
                        Lang.send(actor, "pw.category-show",
                                "<gold>Category of <aqua>%warp%</aqua>:</gold> <white>%category%</white>",
                                Map.of("warp", warp.getName(), "category", c));
                    }
                    return true;
                }

                String cat = args[2];
                if (cat.equalsIgnoreCase("none") || cat.equalsIgnoreCase("clear") || cat.equalsIgnoreCase("-")) {
                    warp.setCategory("");
                    service.saveWarp(warp);
                    Lang.send(actor, "pw.category-cleared",
                            "<green>Cleared category of <aqua>%warp%</aqua>.</green>",
                            Map.of("warp", warp.getName()));
                } else {
                    warp.setCategory(cat);
                    service.saveWarp(warp);
                    Lang.send(actor, "pw.category-set",
                            "<green>Set category of <aqua>%warp%</aqua> to: <white>%category%</white></green>",
                            Map.of("warp", warp.getName(), "category", cat));
                }
                return true;
            }

            case "lock" -> {
                if (actor == null) {
                    Lang.send(sender, "pw.only-players-sub",
                            "<red>Only players can use /%sub%.</red>",
                            Map.of("sub", "lock"));
                    return true;
                }

                if (!actor.hasPermission("oe.pw.meta.lock")) {
                    Lang.send(actor, "pw.no-permission-lock",
                            "<red>You don't have permission.</red>",
                            Map.of());
                    return true;
                }
                if (args.length < 2) {
                    Lang.send(actor, "pw.usage-lock",
                            "<yellow>Usage: /%label% lock <warp> [on|off|toggle]</yellow>",
                            Map.of("label", label));
                    return true;
                }

                String warpName = args[1];
                PlayerWarp warp = service.findByOwnerAndName(actor.getUniqueId(), warpName);
                if (warp == null && actor.hasPermission("oe.pw.admin.meta")) {
                    warp = service.listAll().stream()
                            .filter(w -> w.getName().equalsIgnoreCase(warpName))
                            .findFirst().orElse(null);
                }
                if (warp == null) {
                    Lang.send(actor, "pw.not-found-owner",
                            "<red>You don't have a warp named <yellow>%name%</yellow>.</red>",
                            Map.of("name", warpName.toLowerCase(Locale.ROOT)));
                    return true;
                }

                boolean newState;
                if (args.length >= 3) {
                    String mode = args[2].toLowerCase(Locale.ROOT);
                    if (mode.equals("on") || mode.equals("true")) {
                        newState = true;
                    } else if (mode.equals("off") || mode.equals("false")) {
                        newState = false;
                    } else {
                        newState = !warp.isLocked();
                    }
                } else {
                    newState = !warp.isLocked();
                }

                warp.setLocked(newState);
                service.saveWarp(warp);

                Lang.send(actor, newState ? "pw.locked" : "pw.unlocked",
                        newState ? "<green>Locked warp <aqua>%warp%</aqua>.</green>" : "<green>Unlocked warp <aqua>%warp%</aqua>.</green>",
                        Map.of("warp", warp.getName()));
                return true;
            }

            case "icon" -> {
                if (actor == null) {
                    Lang.send(sender, "pw.only-players-sub",
                            "<red>Only players can use /%sub%.</red>",
                            Map.of("sub", "icon"));
                    return true;
                }

                if (!actor.hasPermission("oe.pw.meta.icon")) {
                    Lang.send(actor, "pw.no-permission-icon",
                            "<red>You don't have permission.</red>",
                            Map.of());
                    return true;
                }
                if (args.length < 2) {
                    Lang.send(actor, "pw.usage-icon",
                            "<yellow>Usage: /%label% icon <warp> [clear]</yellow>",
                            Map.of("label", label));
                    return true;
                }

                String warpName = args[1];
                PlayerWarp warp = service.findByOwnerAndName(actor.getUniqueId(), warpName);
                if (warp == null && actor.hasPermission("oe.pw.admin.meta")) {
                    warp = service.listAll().stream()
                            .filter(w -> w.getName().equalsIgnoreCase(warpName))
                            .findFirst().orElse(null);
                }
                if (warp == null) {
                    Lang.send(actor, "pw.not-found-owner",
                            "<red>You don't have a warp named <yellow>%name%</yellow>.</red>",
                            Map.of("name", warpName.toLowerCase(Locale.ROOT)));
                    return true;
                }

                if (args.length >= 3 &&
                        (args[2].equalsIgnoreCase("clear") || args[2].equalsIgnoreCase("none"))) {
                    warp.setIcon(null);
                    service.saveWarp(warp);
                    Lang.send(actor, "pw.icon-cleared",
                            "<green>Cleared icon for <aqua>%warp%</aqua>.</green>",
                            Map.of("warp", warp.getName()));
                    return true;
                }

                ItemStack hand = actor.getInventory().getItemInMainHand();
                if (hand == null || hand.getType().isAir()) {
                    Lang.send(actor, "pw.icon-no-item",
                            "<red>You must hold an item in your main hand.</red>",
                            Map.of());
                    return true;
                }

                warp.setIcon(hand.clone());
                service.saveWarp(warp);
                Lang.send(actor, "pw.icon-set",
                        "<green>Set icon for <aqua>%warp%</aqua>.</green>",
                        Map.of("warp", warp.getName()));
                return true;
            }

            case "cost" -> {
                if (actor == null) {
                    sender.sendMessage("§cOnly players can set warp cost.");
                    return true;
                }
                if (!actor.hasPermission("oe.pw.meta.cost")) {
                    Lang.send(actor, "pw.no-permission-cost",
                            "<red>You don't have permission.</red>",
                            Map.of());
                    return true;
                }
                if (args.length < 3) {
                    Lang.send(actor, "pw.usage-cost",
                            "<yellow>Usage: /%label% cost <warp> <amount></yellow>",
                            Map.of("label", label));
                    return true;
                }

                String warpName = args[1];
                String amountStr = args[2];

                PlayerWarp warp = service.findByOwnerAndName(actor.getUniqueId(), warpName);
                if (warp == null && actor.hasPermission("oe.pw.admin.meta")) {
                    warp = service.listAll().stream()
                            .filter(w -> w.getName().equalsIgnoreCase(warpName))
                            .findFirst().orElse(null);
                }
                if (warp == null) {
                    Lang.send(actor, "pw.not-found-owner",
                            "<red>You don't have a warp named <yellow>%name%</yellow>.</red>",
                            Map.of("name", warpName.toLowerCase(Locale.ROOT)));
                    return true;
                }

                double cost;
                try {
                    cost = Double.parseDouble(amountStr);
                } catch (NumberFormatException ex) {
                    Lang.send(actor, "pw.cost-invalid",
                            "<red>Invalid amount: <yellow>%input%</yellow></red>",
                            Map.of("input", amountStr));
                    return true;
                }

                if (cost <= 0) {
                    warp.setCost(0.0);
                    service.saveWarp(warp);
                    Lang.send(actor, "pw.cost-cleared",
                            "<green>Cleared cost for <aqua>%warp%</aqua> (now free).</green>",
                            Map.of("warp", warp.getName()));
                } else {
                    warp.setCost(cost);
                    service.saveWarp(warp);
                    Lang.send(actor, "pw.cost-set",
                            "<green>Set cost for <aqua>%warp%</aqua> to <yellow>$%amount%</yellow>.</green>",
                            Map.of("warp", warp.getName(), "amount", amountStr));
                }
                return true;
            }

            case "managers" -> {
                if (actor == null) {
                    Lang.send(sender, "pw.only-players-sub",
                            "<red>Only players can use /%sub%.</red>",
                            Map.of("sub", "managers"));
                    return true;
                }

                if (!actor.hasPermission("oe.pw.meta.managers")) {
                    Lang.send(actor, "pw.no-permission-managers",
                            "<red>You don't have permission.</red>",
                            Map.of());
                    return true;
                }
                if (args.length < 2) {
                    Lang.send(actor, "pw.usage-managers",
                            "<yellow>Usage: /%label% managers <warp> [player]</yellow>",
                            Map.of("label", label));
                    return true;
                }

                String warpName = args[1];
                PlayerWarp warp = service.findByOwnerAndName(actor.getUniqueId(), warpName);
                if (warp == null && actor.hasPermission("oe.pw.admin.meta")) {
                    warp = service.listAll().stream()
                            .filter(w -> w.getName().equalsIgnoreCase(warpName))
                            .findFirst().orElse(null);
                }
                if (warp == null) {
                    Lang.send(actor, "pw.not-found-owner",
                            "<red>You don't have a warp named <yellow>%name%</yellow>.</red>",
                            Map.of("name", warpName.toLowerCase(Locale.ROOT)));
                    return true;
                }

                if (args.length == 2) {
                    Set<UUID> managers = warp.getManagers();
                    actor.sendMessage("§8§m------------------------");
                    actor.sendMessage("§bManagers for warp §e" + warp.getName() + "§7:");
                    if (managers == null || managers.isEmpty()) {
                        actor.sendMessage("§7  (none)");
                    } else {
                        for (UUID u : managers) {
                            OfflinePlayer off = Bukkit.getOfflinePlayer(u);
                            String name = off.getName() != null ? off.getName() : u.toString();
                            actor.sendMessage("§7- §a" + name);
                        }
                    }
                    actor.sendMessage("§8§m------------------------");
                    return true;
                }

                String targetName = args[2];
                OfflinePlayer off = Bukkit.getOfflinePlayer(targetName);
                if (off == null || off.getUniqueId() == null) {
                    Lang.send(actor, "pw.managers-player-not-found",
                            "<red>Player <yellow>%player%</yellow> not found.</red>",
                            Map.of("player", targetName));
                    return true;
                }

                UUID id = off.getUniqueId();
                Set<UUID> managers = warp.getManagers();
                if (managers.contains(id)) {
                    managers.remove(id);
                    warp.setManagers(managers);
                    service.saveWarp(warp);
                    Lang.send(actor, "pw.manager-removed",
                            "<green>Removed <yellow>%player%</yellow> as manager of <white>%warp%</white>.</green>",
                            Map.of(
                                    "warp", warp.getName(),
                                    "player", off.getName() == null ? targetName : off.getName()));
                } else {
                    managers.add(id);
                    warp.setManagers(managers);
                    service.saveWarp(warp);
                    Lang.send(actor, "pw.manager-added",
                            "<green>Added <yellow>%player%</yellow> as manager of <white>%warp%</white>.</green>",
                            Map.of(
                                    "warp", warp.getName(),
                                    "player", off.getName() == null ? targetName : off.getName()));
                }
                return true;
            }

            case "password" -> {
                if (actor == null) {
                    Lang.send(sender, "pw.only-players-sub",
                            "<red>Only players can use /%sub%.</red>",
                            Map.of("sub", "password"));
                    return true;
                }

                if (!actor.hasPermission("oe.pw.meta.password")) {
                    Lang.send(actor, "pw.no-permission-password",
                            "<red>You don't have permission.</red>",
                            Map.of());
                    return true;
                }
                if (args.length < 3) {
                    Lang.send(actor, "pw.usage-password",
                            "<yellow>Usage: /%label% password <warp> <password|off></yellow>",
                            Map.of("label", label));
                    return true;
                }

                String warpName = args[1];
                String pass = args[2];

                PlayerWarp warp = service.findByOwnerAndName(actor.getUniqueId(), warpName);
                if (warp == null && actor.hasPermission("oe.pw.admin.meta")) {
                    warp = service.listAll().stream()
                            .filter(w -> w.getName().equalsIgnoreCase(warpName))
                            .findFirst().orElse(null);
                }
                if (warp == null) {
                    Lang.send(actor, "pw.not-found-owner",
                            "<red>You don't have a warp named <yellow>%name%</yellow>.</red>",
                            Map.of("name", warpName.toLowerCase(Locale.ROOT)));
                    return true;
                }

                if (pass.equalsIgnoreCase("off")
                        || pass.equalsIgnoreCase("none")
                        || pass.equalsIgnoreCase("clear")
                        || pass.equals("-")) {
                    warp.setPassword(null);
                    service.saveWarp(warp);
                    Lang.send(actor, "pw.password-cleared",
                            "<green>Cleared password for <aqua>%warp%</aqua>.</green>",
                            Map.of("warp", warp.getName()));
                } else {
                    warp.setPassword(pass);
                    service.saveWarp(warp);
                    Lang.send(actor, "pw.password-set",
                            "<green>Set password for <aqua>%warp%</aqua>.</green>",
                            Map.of("warp", warp.getName()));
                }
                return true;
            }

            case "use" -> {
                if (actor == null) {
                    Lang.send(sender, "pw.only-players-sub",
                            "<red>Only players can use /%sub%.</red>",
                            Map.of("sub", "use"));
                    return true;
                }

                if (!actor.hasPermission("oe.pw.use") && !actor.hasPermission("oe.pw.base")) {
                    Lang.send(actor, "pw.no-permission-warp",
                            "<red>You don't have permission to use <yellow>%name%</yellow>.</red>",
                            Map.of("name", ""));
                    return true;
                }
                if (args.length < 2) {
                    Lang.send(actor, "pw.usage-use",
                            "<yellow>Usage: /%label% use <warp> [password]</yellow>",
                            Map.of("label", label));
                    return true;
                }

                String warpName = args[1];
                String givenPassword = (args.length >= 3 ? args[2] : null);

                return handleWarpTeleport(plugin, actor, warpName, givenPassword);
            }

            default -> {
                if (actor == null) {
                    Lang.send(sender, "pw.only-players-sub",
                            "<red>Only players can use /%sub%.</red>",
                            Map.of("sub", "<warp>"));
                    return true;
                }

                String warpName = args[0];
                String givenPassword = (args.length >= 2 ? args[1] : null);

                return handleWarpTeleport(plugin, actor, warpName, givenPassword);
            }

        }
    }

    /**
     * Shared logic for warp teleportation with password support.
     * Handles:
     * - /pw <warp>
     * - /pw <warp> <password>
     * - /pw use <warp>
     * - /pw use <warp> <password>
     */
    private boolean handleWarpTeleport(OreoEssentials plugin, Player actor, String warpName, String givenPassword) {
        plugin.getLogger().info("[PW/DEBUG] handleWarpTeleport: player="
                + actor.getName()
                + " warpName=" + warpName
                + " givenPassword=" + (givenPassword == null ? "null" : "'" + givenPassword + "'"));

        PlayerWarp targetWarp = service.listAll().stream()
                .filter(w -> w.getName().equalsIgnoreCase(warpName))
                .findFirst()
                .orElse(null);

        if (targetWarp == null) {
            plugin.getLogger().info("[PW/DEBUG] handleWarpTeleport: warp not found for name=" + warpName);
            Lang.send(actor, "pw.not-found",
                    "<red>Warp <yellow>%name%</yellow> not found.</red>",
                    Map.of("name", warpName.toLowerCase(Locale.ROOT)));
            return true;
        }

        plugin.getLogger().info("[PW/DEBUG] handleWarpTeleport: resolved warp id="
                + targetWarp.getId()
                + " owner=" + targetWarp.getOwner()
                + " name=" + targetWarp.getName());

        // Password branch
        if (givenPassword != null) {
            plugin.getLogger().info("[PW/DEBUG] handleWarpTeleport: password branch");

            if (!service.canUse(actor, targetWarp)) {
                plugin.getLogger().info("[PW/DEBUG] handleWarpTeleport: canUse()=false, blocking");
                Lang.send(actor, "pw.no-permission-warp",
                        "<red>You don't have permission to use <yellow>%name%</yellow>.</red>",
                        Map.of("name", targetWarp.getName()));
                return true;
            }

            String realPassword = targetWarp.getPassword();
            if (realPassword == null || realPassword.isEmpty()) {
                plugin.getLogger().info("[PW/DEBUG] handleWarpTeleport: warp has no password, teleporting normally");
                teleportWithRouting(plugin, actor, targetWarp);
                return true;
            }

            if (!realPassword.equals(givenPassword)) {
                plugin.getLogger().info("[PW/DEBUG] handleWarpTeleport: wrong password");
                Lang.send(actor, "pw.password-wrong",
                        "<red>Wrong password for <yellow>%warp%</yellow>.</red>",
                        Map.of("warp", targetWarp.getName()));
                return true;
            }

            plugin.getLogger().info("[PW/DEBUG] handleWarpTeleport: password OK, teleporting");
            teleportWithRouting(plugin, actor, targetWarp);
            return true;
        }

        // No password branch
        plugin.getLogger().info("[PW/DEBUG] handleWarpTeleport: no password branch");

        if (isPasswordProtected(targetWarp, actor)) {
            plugin.getLogger().info("[PW/DEBUG] handleWarpTeleport: warp is password-protected, blocking");
            Lang.send(actor, "pw.password-required",
                    "<red>Warp <yellow>%warp%</yellow> requires a password.</red> <gray>Use: <white>/pw use %warp% <password></white></gray>",
                    Map.of("warp", targetWarp.getName()));
            return true;
        }

        if (!service.canUse(actor, targetWarp)) {
            plugin.getLogger().info("[PW/DEBUG] handleWarpTeleport: canUse()=false, blocking");
            Lang.send(actor, "pw.no-permission-warp",
                    "<red>You don't have permission to use <yellow>%name%</yellow>.</red>",
                    Map.of("name", targetWarp.getName()));
            return true;
        }

        plugin.getLogger().info("[PW/DEBUG] handleWarpTeleport: all checks OK, calling teleportWithRouting");
        teleportWithRouting(plugin, actor, targetWarp);
        return true;
    }

    private void teleportWithRouting(OreoEssentials plugin, Player actor, PlayerWarp targetWarp) {
        boolean ok = service.teleportToPlayerWarp(actor, targetWarp.getOwner(), targetWarp.getName());
        if (!ok) {
            Lang.send(actor, "pw.teleport-failed",
                    "<red>Teleportation failed.</red>",
                    Map.of("error", "unknown"));
        }
    }

    private void sendHelp(CommandSender s, String label) {
        Lang.send(s, "pw.help-header",
                "<gold><bold>PlayerWarp Help</bold></gold>",
                Map.of());
        Lang.send(s, "pw.help-body",
                "<yellow>/%label% set <n></yellow> <gray>- Create a warp</gray>\n" +
                        "<yellow>/%label% remove <n></yellow> <gray>- Delete your warp</gray>\n" +
                        "<yellow>/%label% list [player]</yellow> <gray>- List warps</gray>\n" +
                        "<yellow>/%label% gui</yellow> <gray>- Browse all warps</gray>\n" +
                        "<yellow>/%label% mywarps</yellow> <gray>- Manage your warps</gray>\n" +
                        "<yellow>/%label% <warp></yellow> <gray>- Teleport to warp</gray>",
                Map.of("label", label));
        Lang.send(s, "pw.help-footer",
                "<gray>Use <yellow>/%label% help</yellow> for more info.</gray>",
                Map.of("label", label));
    }

    private boolean isPasswordProtected(PlayerWarp warp, Player player) {
        String pwd = warp.getPassword();
        if (pwd == null || pwd.isEmpty()) return false;
        if (player.hasPermission("oe.pw.bypass.password")) return false;
        return true;
    }

    private void sendList(CommandSender sender, String ownerName, List<PlayerWarp> warps) {
        Lang.send(sender, "pw.list-header",
                "<gold>Warps of <yellow>%owner%</yellow>:</gold>",
                Map.of("owner", ownerName));

        if (warps == null || warps.isEmpty()) {
            Lang.send(sender, "pw.list-empty",
                    "<gray>No warps found.</gray>",
                    Map.of());
        } else {
            for (PlayerWarp w : warps) {
                Lang.send(sender, "pw.list-entry",
                        " <dark_gray>-</dark_gray> <aqua>%name%</aqua>",
                        Map.of("name", w.getName()));
            }
        }

        Lang.send(sender, "pw.list-footer",
                "<gray>───────────────────────</gray>",
                Map.of());
    }
}