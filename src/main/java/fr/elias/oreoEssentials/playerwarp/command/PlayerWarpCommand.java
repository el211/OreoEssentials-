package fr.elias.oreoEssentials.playerwarp.command;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.playerwarp.PlayerWarp;
import fr.elias.oreoEssentials.playerwarp.PlayerWarpService;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.stream.Collectors;

public class PlayerWarpCommand implements OreoCommand {

    private final PlayerWarpService service;

    // extra per-player warp allowance (in addition to service.getLimit(player))
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
            Lang.send(sender, "pw.no-permission-base", Map.of(), (sender instanceof Player p) ? p : null);
            return true;
        }

        Player actor = (sender instanceof Player p) ? p : null;

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {

            /* --------------------------------------------------------
             * /pw set <name>
             * -------------------------------------------------------- */
            case "set" -> {
                if (actor == null) {
                    Lang.send(sender, "pw.only-players-sub", Map.of("sub", "set"), null);
                    return true;
                }

                if (!actor.hasPermission("oe.pw.set")) {
                    Lang.send(actor, "pw.no-permission-set", Map.of(), actor);
                    return true;
                }
                if (args.length < 2) {
                    Lang.send(actor, "pw.usage-set",
                            Map.of("label", label),
                            actor);
                    return true;
                }
                String name = args[1];

                int baseLimit = service.getLimit(actor);
                int extra = extraWarps.getOrDefault(actor.getUniqueId(), 0);
                int effectiveLimit = baseLimit + extra;

                if (!service.isUnderLimit(actor, effectiveLimit) &&
                        !actor.hasPermission("oe.pw.limit.bypass")) {
                    Lang.send(actor, "pw.limit-reached",
                            Map.of("limit", String.valueOf(effectiveLimit)),
                            actor);
                    return true;
                }

                Location loc = actor.getLocation();
                PlayerWarp created = service.createWarp(actor, name, loc);
                if (created == null) {
                    Lang.send(actor, "pw.already-exists",
                            Map.of("name", name.toLowerCase(Locale.ROOT)),
                            actor);
                } else {
                    Lang.send(actor, "pw.set-success",
                            Map.of("name", created.getName()),
                            actor);
                }
                return true;
            }

            /* --------------------------------------------------------
             * /pw gui  -> open global browser
             * -------------------------------------------------------- */
            case "gui" -> {
                if (actor == null) {
                    Lang.send(sender, "pw.only-players-sub", Map.of("sub", "gui"), null);
                    return true;
                }

                // optional: permission check
                if (!actor.hasPermission("oe.pw.gui")) {
                    Lang.send(actor, "pw.no-permission-list", Map.of(), actor);
                    return true;
                }
                fr.elias.oreoEssentials.playerwarp.gui.PlayerWarpBrowseMenu.open(actor, service);
                return true;
            }

            /* --------------------------------------------------------
             * /pw mywarps  -> open personal warps & categories
             * -------------------------------------------------------- */
            case "mywarps" -> {
                if (actor == null) {
                    Lang.send(sender, "pw.only-players-sub", Map.of("sub", "mywarps"), null);
                    return true;
                }

                if (!actor.hasPermission("oe.pw.mywarps")) {
                    Lang.send(actor, "pw.no-permission-list", Map.of(), actor);
                    return true;
                }
                fr.elias.oreoEssentials.playerwarp.gui.MyPlayerWarpsMenu.open(actor, service);
                return true;
            }

            /* --------------------------------------------------------
             * /pw remove <warp>
             * -------------------------------------------------------- */
            case "remove" -> {
                if (actor == null) {
                    Lang.send(sender, "pw.only-players-sub", Map.of("sub", "remove"), null);
                    return true;
                }

                if (args.length < 2) {
                    Lang.send(actor, "pw.usage-remove",
                            Map.of("label", label),
                            actor);
                    return true;
                }
                String name = args[1];
                PlayerWarp warp = service.findByOwnerAndName(actor.getUniqueId(), name);
                if (warp == null) {
                    Lang.send(actor, "pw.not-found-owner",
                            Map.of("name", name.toLowerCase(Locale.ROOT)),
                            actor);
                    return true;
                }
                if (!actor.hasPermission("oe.pw.remove") &&
                        !actor.hasPermission("oe.pw.admin.remove")) {
                    Lang.send(actor, "pw.no-permission-remove", Map.of(), actor);
                    return true;
                }
                if (service.deleteWarp(warp)) {
                    Lang.send(actor, "pw.remove-success",
                            Map.of("name", warp.getName()),
                            actor);
                } else {
                    Lang.send(actor, "pw.remove-failed",
                            Map.of("name", warp.getName()),
                            actor);
                }
                return true;
            }

            /* --------------------------------------------------------
             * /pw list [player]
             * -------------------------------------------------------- */
            case "list" -> {
                if (!sender.hasPermission("oe.pw.list")) {
                    Lang.send(sender, "pw.no-permission-list", Map.of(), actor);
                    return true;
                }

                if (args.length >= 2) {
                    String targetName = args[1];
                    Player target = Bukkit.getPlayerExact(targetName);
                    if (target == null) {
                        Lang.send(sender, "pw.list-target-offline",
                                Map.of("player", targetName),
                                actor);
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

            /* --------------------------------------------------------
             * /pw amount [player]
             * -------------------------------------------------------- */
            case "amount" -> {
                if (!sender.hasPermission("oe.pw.amount")) {
                    Lang.send(sender, "pw.no-permission-amount", Map.of(), actor);
                    return true;
                }

                if (args.length >= 2) {
                    String targetName = args[1];
                    Player target = Bukkit.getPlayerExact(targetName);
                    if (target == null) {
                        Lang.send(sender, "pw.amount-target-offline",
                                Map.of("player", targetName),
                                actor);
                        return true;
                    }

                    int count = service.getWarpCount(target);
                    Lang.send(sender, "pw.amount-other",
                            Map.of("player", target.getName(), "amount", String.valueOf(count)),
                            actor);
                } else {
                    if (actor == null) {
                        Lang.send(sender, "pw.amount-console-usage", Map.of(), null);
                        return true;
                    }

                    int count = service.getWarpCount(actor);
                    Lang.send(actor, "pw.amount-self",
                            Map.of("amount", String.valueOf(count)),
                            actor);
                }
                return true;
            }

            /* --------------------------------------------------------
             * /pw rtp
             * -------------------------------------------------------- */
            case "rtp" -> {
                if (actor == null) {
                    Lang.send(sender, "pw.only-players-sub", Map.of("sub", "rtp"), null);
                    return true;
                }

                if (!actor.hasPermission("oe.pw.rtp")) {
                    Lang.send(actor, "pw.no-permission-rtp", Map.of(), actor);
                    return true;
                }

                List<PlayerWarp> all = service.listAll();
                if (all.isEmpty()) {
                    Lang.send(actor, "pw.rtp-none", Map.of(), actor);
                    return true;
                }

                List<PlayerWarp> usable = all.stream()
                        .filter(w -> service.canUse(actor, w))
                        .filter(w -> !isPasswordProtected(w, actor)) // don't rtp into password-protected warps
                        .collect(Collectors.toList());

                if (usable.isEmpty()) {
                    Lang.send(actor, "pw.rtp-none-usable", Map.of(), actor);
                    return true;
                }

                PlayerWarp targetWarp = usable.get(new Random().nextInt(usable.size()));

                teleportWithRouting(plugin, actor, targetWarp);
                return true;
            }

            /* --------------------------------------------------------
             * /pw near [page]
             * -------------------------------------------------------- */
            case "near" -> {
                if (actor == null) {
                    Lang.send(sender, "pw.only-players-sub", Map.of("sub", "near"), null);
                    return true;
                }

                if (!actor.hasPermission("oe.pw.near")) {
                    Lang.send(actor, "pw.no-permission-near", Map.of(), actor);
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
                            Map.of("radius", String.valueOf((int) maxDistance)),
                            actor);
                    return true;
                }

                int perPage = 10;
                int from = (page - 1) * perPage;
                int to = Math.min(from + perPage, nearby.size());
                if (from >= nearby.size()) {
                    Lang.send(actor, "pw.near-page-empty",
                            Map.of("page", String.valueOf(page)),
                            actor);
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

            /* --------------------------------------------------------
             * /pw reset <warp>
             * -------------------------------------------------------- */
            case "reset" -> {
                if (actor == null) {
                    Lang.send(sender, "pw.only-players-sub", Map.of("sub", "reset"), null);
                    return true;
                }

                if (!actor.hasPermission("oe.pw.reset")) {
                    Lang.send(actor, "pw.no-permission-reset", Map.of(), actor);
                    return true;
                }
                if (args.length < 2) {
                    Lang.send(actor, "pw.usage-reset",
                            Map.of("label", label),
                            actor);
                    return true;
                }
                String name = args[1];
                PlayerWarp warp = service.findByOwnerAndName(actor.getUniqueId(), name);
                if (warp == null) {
                    Lang.send(actor, "pw.not-found-owner",
                            Map.of("name", name.toLowerCase(Locale.ROOT)),
                            actor);
                    return true;
                }

                Location newLoc = actor.getLocation();
                service.deleteWarp(warp);
                PlayerWarp recreated = service.createWarp(actor, warp.getName(), newLoc);
                if (recreated != null) {
                    Lang.send(actor, "pw.reset-success",
                            Map.of("name", recreated.getName()),
                            actor);
                } else {
                    Lang.send(actor, "pw.reset-failed",
                            Map.of("name", warp.getName()),
                            actor);
                }
                return true;
            }

            /* --------------------------------------------------------
             * /pw rename <warp> <newName>
             * -------------------------------------------------------- */
            case "rename" -> {
                if (actor == null) {
                    Lang.send(sender, "pw.only-players-sub", Map.of("sub", "rename"), null);
                    return true;
                }


                if (!actor.hasPermission("oe.pw.rename")) {
                    Lang.send(actor, "pw.no-permission-rename", Map.of(), actor);
                    return true;
                }
                if (args.length < 3) {
                    Lang.send(actor, "pw.usage-rename",
                            Map.of("label", label),
                            actor);
                    return true;
                }
                String oldName = args[1];
                String newName = args[2];

                PlayerWarp warp = service.findByOwnerAndName(actor.getUniqueId(), oldName);
                if (warp == null) {
                    Lang.send(actor, "pw.not-found-owner",
                            Map.of("name", oldName.toLowerCase(Locale.ROOT)),
                            actor);
                    return true;
                }

                Location loc = warp.getLocation();
                service.deleteWarp(warp);
                PlayerWarp renamed = service.createWarp(actor, newName, loc);
                if (renamed != null) {
                    Lang.send(actor, "pw.rename-success",
                            Map.of("old", oldName, "name", renamed.getName()),
                            actor);
                } else {
                    Lang.send(actor, "pw.rename-failed",
                            Map.of("old", oldName, "name", newName),
                            actor);
                }
                return true;
            }

            /* --------------------------------------------------------
             * /pw setowner <warp> <player>
             * -------------------------------------------------------- */
            case "setowner" -> {
                if (!sender.hasPermission("oe.pw.setowner")) {
                    Lang.send(sender, "pw.no-permission-setowner", Map.of(), actor);
                    return true;
                }
                if (args.length < 3) {
                    Lang.send(sender, "pw.usage-setowner",
                            Map.of("label", label),
                            actor);
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
                            Map.of("name", warpName.toLowerCase(Locale.ROOT)),
                            actor);
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
                            Map.of("warp", recreated.getName(), "player", newOwner.getName()),
                            actor);
                } else {
                    Lang.send(sender, "pw.setowner-failed",
                            Map.of("warp", warp.getName(), "player", newOwner.getName()),
                            actor);
                }
                return true;
            }

            /* --------------------------------------------------------
             * /pw removeall <player>
             * -------------------------------------------------------- */
            case "removeall" -> {
                if (!sender.hasPermission("oe.pw.admin.removeall")) {
                    Lang.send(sender, "pw.no-permission-removeall", Map.of(), actor);
                    return true;
                }
                if (args.length < 2) {
                    Lang.send(sender, "pw.usage-removeall",
                            Map.of("label", label),
                            actor);
                    return true;
                }
                String targetName = args[1];
                Player target = Bukkit.getPlayerExact(targetName);
                OfflinePlayer off = target != null ? target : Bukkit.getOfflinePlayer(targetName);
                Lang.send(sender, "pw.player-resolve-failed",
                        Map.of("player", targetName),
                        actor);


                List<PlayerWarp> warps = service.listByOwner(off.getUniqueId());
                int count = 0;
                for (PlayerWarp w : warps) {
                    if (service.deleteWarp(w)) count++;
                }

                Lang.send(sender, "pw.removeall-success",
                        Map.of("player", off.getName() == null ? targetName : off.getName(),
                                "amount", String.valueOf(count)),
                        actor);
                return true;
            }

            /* --------------------------------------------------------
             * /pw reload
             * -------------------------------------------------------- */
            case "reload" -> {
                if (!sender.hasPermission("oe.pw.admin.reload")) {
                    Lang.send(sender, "pw.no-permission-reload", Map.of(), actor);
                    return true;
                }

                plugin.reloadConfig();
                Lang.send(sender, "pw.reload-success", Map.of(), actor);
                return true;
            }

            /* --------------------------------------------------------
             * /pw addwarps <player> <amount>
             * -------------------------------------------------------- */
            case "addwarps" -> {
                if (!sender.hasPermission("oe.pw.admin.addwarps")) {
                    Lang.send(sender, "pw.no-permission-addwarps", Map.of(), actor);
                    return true;
                }
                if (args.length < 3) {
                    Lang.send(sender, "pw.usage-addwarps",
                            Map.of("label", label),
                            actor);
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
                    Lang.send(sender, "pw.addwarps-invalid-amount", Map.of(), actor);
                    return true;
                }


                UUID uuid = off.getUniqueId();
                int current = extraWarps.getOrDefault(uuid, 0);
                int newVal = current + amount;
                if (newVal < 0) newVal = 0;
                extraWarps.put(uuid, newVal);

                Lang.send(sender, "pw.addwarps-success",
                        Map.of("player", off.getName() == null ? targetName : off.getName(),
                                "amount", String.valueOf(amount),
                                "total", String.valueOf(newVal)),
                        actor);
                return true;
            }

            /* --------------------------------------------------------
             * /pw whitelist <action> <warp> [player]
             * -------------------------------------------------------- */
            case "whitelist" -> {
                if (actor == null) {
                    Lang.send(sender, "pw.only-players-sub", Map.of("sub", "whitelist"), null);
                    return true;
                }


                if (!actor.hasPermission("oe.pw.whitelist") &&
                        !actor.hasPermission("oe.pw.admin.whitelist")) {
                    Lang.send(actor, "pw.no-permission-whitelist", Map.of(), actor);
                    return true;
                }

                if (args.length < 3) {
                    Lang.send(actor, "pw.usage-whitelist",
                            Map.of("label", label),
                            actor);
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
                            Map.of("name", warpName.toLowerCase(Locale.ROOT)),
                            actor);
                    return true;
                }

                boolean isOwner = warp.getOwner().equals(actor.getUniqueId());
                boolean isAdmin = actor.hasPermission("oe.pw.admin.whitelist");
                if (!isOwner && !isAdmin) {
                    Lang.send(actor, "pw.whitelist-not-owner",
                            Map.of("warp", warp.getName()),
                            actor);
                    return true;
                }

                switch (action) {
                    case "enable" -> {
                        warp.setWhitelistEnabled(true);
                        service.saveWarp(warp);
                        Lang.send(actor, "pw.whitelist-enabled",
                                Map.of("warp", warp.getName()),
                                actor);
                        return true;
                    }

                    case "disable" -> {
                        warp.setWhitelistEnabled(false);
                        service.saveWarp(warp);
                        Lang.send(actor, "pw.whitelist-disabled",
                                Map.of("warp", warp.getName()),
                                actor);
                        return true;
                    }

                    case "list" -> {
                        Set<UUID> wl = warp.getWhitelist();
                        actor.sendMessage("§8§m------------------------");
                        actor.sendMessage("§bWhitelist for warp §e" + warp.getName() + "§7 ("
                                + (warp.isWhitelistEnabled() ? "§aenabled" : "§cdisabled") + "§7):");

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
                                    Map.of("label", label),
                                    actor);
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
                                        Map.of("warp", warp.getName(),
                                                "player", off.getName() == null ? targetName : off.getName()),
                                        actor);
                            } else {
                                Lang.send(actor, "pw.whitelist-already",
                                        Map.of("warp", warp.getName(),
                                                "player", off.getName() == null ? targetName : off.getName()),
                                        actor);
                            }
                        } else {
                            if (wl.remove(targetId)) {
                                service.saveWarp(warp);
                                Lang.send(actor, "pw.whitelist-removed",
                                        Map.of("warp", warp.getName(),
                                                "player", off.getName() == null ? targetName : off.getName()),
                                        actor);
                            } else {
                                Lang.send(actor, "pw.whitelist-not-found",
                                        Map.of("warp", warp.getName(),
                                                "player", off.getName() == null ? targetName : off.getName()),
                                        actor);
                            }
                        }
                        return true;
                    }

                    default -> {
                        Lang.send(actor, "pw.usage-whitelist",
                                Map.of("label", label),
                                actor);
                        return true;
                    }
                }
            }

            /* --------------------------------------------------------
             * /pw desc <warp> [description...]
             * -------------------------------------------------------- */
            case "desc" -> {
                if (actor == null) {
                    Lang.send(sender, "pw.only-players-sub", Map.of("sub", "desc"), null);
                    return true;
                }

                if (!actor.hasPermission("oe.pw.meta.desc")) {
                    Lang.send(actor, "pw.no-permission-desc", Map.of(), actor);
                    return true;
                }
                if (args.length < 2) {
                    Lang.send(actor, "pw.usage-desc", Map.of("label", label), actor);
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
                            Map.of("name", warpName.toLowerCase(Locale.ROOT)),
                            actor);
                    return true;
                }

                if (args.length == 2) {
                    String d = warp.getDescription();
                    if (d == null || d.isEmpty()) {
                        Lang.send(actor, "pw.desc-empty",
                                Map.of("warp", warp.getName()),
                                actor);
                    } else {
                        Lang.send(actor, "pw.desc-show",
                                Map.of("warp", warp.getName(), "desc", d),
                                actor);
                    }
                    return true;
                }

                String newDesc = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                warp.setDescription(newDesc);
                service.saveWarp(warp);

                if (newDesc.isEmpty()) {
                    Lang.send(actor, "pw.desc-cleared",
                            Map.of("warp", warp.getName()),
                            actor);
                } else {
                    Lang.send(actor, "pw.desc-set",
                            Map.of("warp", warp.getName(), "desc", newDesc),
                            actor);
                }
                return true;
            }

            /* --------------------------------------------------------
             * /pw category <warp> [category]
             * -------------------------------------------------------- */
            case "category" -> {
                if (actor == null) {
                    Lang.send(sender, "pw.only-players-sub", Map.of("sub", "category"), null);
                    return true;
                }

                if (!actor.hasPermission("oe.pw.meta.category")) {
                    Lang.send(actor, "pw.no-permission-category", Map.of(), actor);
                    return true;
                }
                if (args.length < 2) {
                    Lang.send(actor, "pw.usage-category", Map.of("label", label), actor);
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
                            Map.of("name", warpName.toLowerCase(Locale.ROOT)),
                            actor);
                    return true;
                }

                if (args.length == 2) {
                    String c = warp.getCategory();
                    if (c == null || c.isEmpty()) {
                        Lang.send(actor, "pw.category-empty",
                                Map.of("warp", warp.getName()),
                                actor);
                    } else {
                        Lang.send(actor, "pw.category-show",
                                Map.of("warp", warp.getName(), "category", c),
                                actor);
                    }
                    return true;
                }

                String cat = args[2];
                if (cat.equalsIgnoreCase("none") || cat.equalsIgnoreCase("clear") || cat.equalsIgnoreCase("-")) {
                    warp.setCategory("");
                    service.saveWarp(warp);
                    Lang.send(actor, "pw.category-cleared",
                            Map.of("warp", warp.getName()),
                            actor);
                } else {
                    warp.setCategory(cat);
                    service.saveWarp(warp);
                    Lang.send(actor, "pw.category-set",
                            Map.of("warp", warp.getName(), "category", cat),
                            actor);
                }
                return true;
            }

            /* --------------------------------------------------------
             * /pw lock <warp> [on|off|toggle]
             * -------------------------------------------------------- */
            case "lock" -> {
                if (actor == null) {
                    Lang.send(sender, "pw.only-players-sub", Map.of("sub", "lock"), null);
                    return true;
                }

                if (!actor.hasPermission("oe.pw.meta.lock")) {
                    Lang.send(actor, "pw.no-permission-lock", Map.of(), actor);
                    return true;
                }
                if (args.length < 2) {
                    Lang.send(actor, "pw.usage-lock", Map.of("label", label), actor);
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
                            Map.of("name", warpName.toLowerCase(Locale.ROOT)),
                            actor);
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
                        Map.of("warp", warp.getName()),
                        actor);
                return true;
            }

            /* --------------------------------------------------------
             * /pw icon <warp> [clear]
             * -------------------------------------------------------- */
            case "icon" -> {
                if (actor == null) {
                    Lang.send(sender, "pw.only-players-sub", Map.of("sub", "icon"), null);
                    return true;
                }

                if (!actor.hasPermission("oe.pw.meta.icon")) {
                    Lang.send(actor, "pw.no-permission-icon", Map.of(), actor);
                    return true;
                }
                if (args.length < 2) {
                    Lang.send(actor, "pw.usage-icon", Map.of("label", label), actor);
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
                            Map.of("name", warpName.toLowerCase(Locale.ROOT)),
                            actor);
                    return true;
                }

                if (args.length >= 3 &&
                        (args[2].equalsIgnoreCase("clear") || args[2].equalsIgnoreCase("none"))) {
                    warp.setIcon(null);
                    service.saveWarp(warp);
                    Lang.send(actor, "pw.icon-cleared",
                            Map.of("warp", warp.getName()),
                            actor);
                    return true;
                }

                ItemStack hand = actor.getInventory().getItemInMainHand();
                if (hand == null || hand.getType().isAir()) {
                    Lang.send(actor, "pw.icon-no-item", Map.of(), actor);
                    return true;
                }

                warp.setIcon(hand.clone());
                service.saveWarp(warp);
                Lang.send(actor, "pw.icon-set",
                        Map.of("warp", warp.getName()),
                        actor);
                return true;
            }

            /* --------------------------------------------------------
             * /pw cost <warp> <amount>
             * -------------------------------------------------------- */
            case "cost" -> {
                if (actor == null) {
                    sender.sendMessage("§cOnly players can set warp cost.");
                    return true;
                }
                if (!actor.hasPermission("oe.pw.meta.cost")) {
                    Lang.send(actor, "pw.no-permission-cost", Map.of(), actor);
                    return true;
                }
                if (args.length < 3) {
                    Lang.send(actor, "pw.usage-cost", Map.of("label", label), actor);
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
                            Map.of("name", warpName.toLowerCase(Locale.ROOT)),
                            actor);
                    return true;
                }

                double cost;
                try {
                    cost = Double.parseDouble(amountStr);
                } catch (NumberFormatException ex) {
                    Lang.send(actor, "pw.cost-invalid",
                            Map.of("input", amountStr),
                            actor);
                    return true;
                }

                if (cost <= 0) {
                    warp.setCost(0.0);
                    service.saveWarp(warp);
                    Lang.send(actor, "pw.cost-cleared",
                            Map.of("warp", warp.getName()),
                            actor);
                } else {
                    warp.setCost(cost);
                    service.saveWarp(warp);
                    Lang.send(actor, "pw.cost-set",
                            Map.of("warp", warp.getName(),
                                    "amount", amountStr),
                            actor);
                }
                return true;
            }

            /* --------------------------------------------------------
             * /pw managers <warp> [player]
             * -------------------------------------------------------- */
            case "managers" -> {
                if (actor == null) {
                    Lang.send(sender, "pw.only-players-sub", Map.of("sub", "managers"), null);
                    return true;
                }

                if (!actor.hasPermission("oe.pw.meta.managers")) {
                    Lang.send(actor, "pw.no-permission-managers", Map.of(), actor);
                    return true;
                }
                if (args.length < 2) {
                    Lang.send(actor, "pw.usage-managers", Map.of("label", label), actor);
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
                            Map.of("name", warpName.toLowerCase(Locale.ROOT)),
                            actor);
                    return true;
                }

                // /pw managers <warp> -> list
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

                // /pw managers <warp> <player> -> toggle
                String targetName = args[2];
                OfflinePlayer off = Bukkit.getOfflinePlayer(targetName);
                if (off == null || off.getUniqueId() == null) {
                    Lang.send(actor, "pw.managers-player-not-found",
                            Map.of("player", targetName),
                            actor);
                    return true;
                }

                UUID id = off.getUniqueId();
                Set<UUID> managers = warp.getManagers();
                if (managers.contains(id)) {
                    managers.remove(id);
                    warp.setManagers(managers);
                    service.saveWarp(warp);
                    Lang.send(actor, "pw.manager-removed",
                            Map.of("warp", warp.getName(),
                                    "player", off.getName() == null ? targetName : off.getName()),
                            actor);
                } else {
                    managers.add(id);
                    warp.setManagers(managers);
                    service.saveWarp(warp);
                    Lang.send(actor, "pw.manager-added",
                            Map.of("warp", warp.getName(),
                                    "player", off.getName() == null ? targetName : off.getName()),
                            actor);
                }
                return true;
            }

            /* --------------------------------------------------------
             * /pw password <warp> <password|off>
             * -------------------------------------------------------- */
            case "password" -> {
                if (actor == null) {
                    Lang.send(sender, "pw.only-players-sub", Map.of("sub", "password"), null);
                    return true;
                }

                if (!actor.hasPermission("oe.pw.meta.password")) {
                    Lang.send(actor, "pw.no-permission-password", Map.of(), actor);
                    return true;
                }
                if (args.length < 3) {
                    Lang.send(actor, "pw.usage-password", Map.of("label", label), actor);
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
                            Map.of("name", warpName.toLowerCase(Locale.ROOT)),
                            actor);
                    return true;
                }

                if (pass.equalsIgnoreCase("off")
                        || pass.equalsIgnoreCase("none")
                        || pass.equalsIgnoreCase("clear")
                        || pass.equals("-")) {
                    warp.setPassword(null);
                    service.saveWarp(warp);
                    Lang.send(actor, "pw.password-cleared",
                            Map.of("warp", warp.getName()),
                            actor);
                } else {
                    warp.setPassword(pass);
                    service.saveWarp(warp);
                    Lang.send(actor, "pw.password-set",
                            Map.of("warp", warp.getName()),
                            actor);
                }
                return true;
            }

            /* --------------------------------------------------------
             * /pw use <warp> [password]
             * -------------------------------------------------------- */
            case "use" -> {
                if (actor == null) {
                    Lang.send(sender, "pw.only-players-sub", Map.of("sub", "use"), null);
                    return true;
                }

                if (!actor.hasPermission("oe.pw.use") && !actor.hasPermission("oe.pw.base")) {
                    Lang.send(actor, "pw.no-permission-warp", Map.of("name", ""), actor);
                    return true;
                }
                if (args.length < 2) {
                    Lang.send(actor, "pw.usage-use",
                            Map.of("label", label),
                            actor);
                    return true;
                }

                String warpName = args[1];
                String givenPassword = (args.length >= 3 ? args[2] : null);

                // EXACT SAME LOGIC AS DEFAULT
                return handleWarpTeleport(plugin, actor, warpName, givenPassword);
            }



            /* --------------------------------------------------------
             * /pw <warp> [password] (fallback)
             * -------------------------------------------------------- */
            /* --------------------------------------------------------
             * /pw <warp> [password] (fallback)
             * -------------------------------------------------------- */
            default -> {
                if (actor == null) {
                    Lang.send(sender, "pw.only-players-sub", Map.of("sub", "<warp>"), null);
                    return true;
                }


                String warpName = args[0];
                String givenPassword = (args.length >= 2 ? args[1] : null);

                // EXACT SAME LOGIC AS /pw use
                return handleWarpTeleport(plugin, actor, warpName, givenPassword);
            }

        }
    }
    /**
     * Shared logic for:
     * - /pw <warp>
     * - /pw <warp> <password>
     * - /pw use <warp>
     * - /pw use <warp> <password>
     */
    private boolean handleWarpTeleport(OreoEssentials plugin, Player actor, String warpName, String givenPassword) {
        plugin.getLogger().info("[OreoEssentials] [PW/DEBUG] handleWarpTeleport: player="
                + actor.getName()
                + " warpName=" + warpName
                + " givenPassword=" + (givenPassword == null ? "null" : "'" + givenPassword + "'"));

        PlayerWarp targetWarp = service.listAll().stream()
                .filter(w -> w.getName().equalsIgnoreCase(warpName))
                .findFirst()
                .orElse(null);

        if (targetWarp == null) {
            plugin.getLogger().info("[OreoEssentials] [PW/DEBUG] handleWarpTeleport: warp not found in listAll() for name=" + warpName);
            Lang.send(actor, "pw.not-found",
                    Map.of("name", warpName.toLowerCase(Locale.ROOT)),
                    actor);
            return true;
        }

        plugin.getLogger().info("[OreoEssentials] [PW/DEBUG] handleWarpTeleport: resolved warp id="
                + targetWarp.getId()
                + " owner=" + targetWarp.getOwner()
                + " name=" + targetWarp.getName());

        // If a password was provided
        if (givenPassword != null) {
            plugin.getLogger().info("[OreoEssentials] [PW/DEBUG] handleWarpTeleport: password branch");

            if (!service.canUse(actor, targetWarp)) {
                plugin.getLogger().info("[OreoEssentials] [PW/DEBUG] handleWarpTeleport: canUse()=false, blocking");
                Lang.send(actor, "pw.no-permission-warp",
                        Map.of("name", targetWarp.getName()),
                        actor);
                return true;
            }

            String realPassword = targetWarp.getPassword();
            if (realPassword == null || realPassword.isEmpty()) {
                plugin.getLogger().info("[OreoEssentials] [PW/DEBUG] handleWarpTeleport: warp has no password, teleporting normally");
                teleportWithRouting(plugin, actor, targetWarp);
                return true;
            }

            if (!realPassword.equals(givenPassword)) {
                plugin.getLogger().info("[OreoEssentials] [PW/DEBUG] handleWarpTeleport: wrong password");
                Lang.send(actor, "pw.password-wrong",
                        Map.of("warp", targetWarp.getName()),
                        actor);
                return true;
            }

            plugin.getLogger().info("[OreoEssentials] [PW/DEBUG] handleWarpTeleport: password OK, teleporting");
            teleportWithRouting(plugin, actor, targetWarp);
            return true;
        }

        // No password given
        plugin.getLogger().info("[OreoEssentials] [PW/DEBUG] handleWarpTeleport: no password branch");

        if (isPasswordProtected(targetWarp, actor)) {
            plugin.getLogger().info("[OreoEssentials] [PW/DEBUG] handleWarpTeleport: warp is password-protected, blocking");
            Lang.send(actor, "pw.password-required",
                    Map.of("warp", targetWarp.getName()),
                    actor);
            return true;
        }

        if (!service.canUse(actor, targetWarp)) {
            plugin.getLogger().info("[OreoEssentials] [PW/DEBUG] handleWarpTeleport: canUse()=false, blocking");
            Lang.send(actor, "pw.no-permission-warp",
                    Map.of("name", targetWarp.getName()),
                    actor);
            return true;
        }

        plugin.getLogger().info("[OreoEssentials] [PW/DEBUG] handleWarpTeleport: all checks OK, calling teleportWithRouting");
        teleportWithRouting(plugin, actor, targetWarp);
        return true;
    }


    // ----------------- Helpers -----------------

    private void teleportWithRouting(OreoEssentials plugin, Player actor, PlayerWarp targetWarp) {
        boolean ok = service.teleportToPlayerWarp(actor, targetWarp.getOwner(), targetWarp.getName());
        if (!ok) {
            Lang.send(actor, "pw.teleport-failed",
                    Map.of("error", "unknown"),
                    actor);
        }
    }

    private void sendHelp(CommandSender s, String label) {
        Player viewer = (s instanceof Player p) ? p : null;

        // Top line
        Lang.send(s, "pw.help-header", Map.of(), viewer);

        // All help lines (multi-line string in lang.yml)
        Lang.send(s, "pw.help-body", Map.of("label", label), viewer);

        // Bottom line
        Lang.send(s, "pw.help-footer", Map.of(), viewer);
    }


    private boolean isPasswordProtected(PlayerWarp warp, Player player) {
        String pwd = warp.getPassword();
        if (pwd == null || pwd.isEmpty()) {
            // No password -> not protected
            return false;
        }

        // Only staff with explicit bypass can ignore password
        if (player.hasPermission("oe.pw.bypass.password")) {
            return false;
        }

        // Everyone else must enter the password
        return true;
    }

    private void sendList(CommandSender sender, String ownerName, List<PlayerWarp> warps) {
        Player viewer = (sender instanceof Player p) ? p : null;

        // Header ("----" + title line)
        Lang.send(sender, "pw.list-header",
                Map.of("owner", ownerName),
                viewer);

        if (warps == null || warps.isEmpty()) {
            Lang.send(sender, "pw.list-empty", Map.of(), viewer);
        } else {
            for (PlayerWarp w : warps) {
                Lang.send(sender, "pw.list-entry",
                        Map.of("name", w.getName()),
                        viewer);
            }
        }

        // Footer line
        Lang.send(sender, "pw.list-footer", Map.of(), viewer);
    }

}
