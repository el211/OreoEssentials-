package fr.elias.oreoEssentials.modules.warnings;

import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.BanList;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class WarnCommand implements OreoCommand, TabCompleter {

    private final WarnService warnService;

    public WarnCommand(WarnService warnService) {
        this.warnService = warnService;
    }

    @Override public String       name()       { return "warn"; }
    @Override public List<String> aliases()    { return List.of(); }
    @Override public String       permission() { return "oreo.warn"; }
    @Override public String       usage()      { return "<player> <reason...>"; }
    @Override public boolean      playerOnly() { return false; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            Lang.send(sender, "warn.usage", "<red>Usage: /warn <player> <reason...></red>");
            return true;
        }

        String targetName = args[0];
        String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

        Player target = Bukkit.getPlayerExact(targetName);
        OfflinePlayer offTarget = null;
        if (target == null) {
            for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
                if (op.getName() != null && op.getName().equalsIgnoreCase(targetName)) {
                    offTarget = op;
                    break;
                }
            }
        }

        if (target == null && (offTarget == null || !offTarget.hasPlayedBefore())) {
            Lang.send(sender, "warn.not-found",
                    "<red>Player <white>%player%</white> not found.</red>",
                    Map.of("player", targetName));
            return true;
        }

        UUID targetUuid = target != null ? target.getUniqueId() : offTarget.getUniqueId();
        String displayName = target != null ? target.getName() : (offTarget.getName() != null ? offTarget.getName() : targetName);
        UUID issuerUuid   = sender instanceof Player p ? p.getUniqueId() : UUID.fromString("00000000-0000-0000-0000-000000000000");

        int count = warnService.warn(targetUuid, issuerUuid, sender.getName(), reason);
        int max   = warnService.getMaxWarnings();

        // Notify staff
        Lang.send(sender, "warn.success",
                "<yellow>Warned <aqua>%player%</aqua>: <white>%reason%</white> <gray>(%count%/%max% warnings)</gray></yellow>",
                Map.of("player", displayName, "reason", reason,
                       "count", String.valueOf(count), "max", String.valueOf(max)));

        // Notify target if online
        if (target != null) {
            Lang.send(target, "warn.received",
                    "<red>You have been warned by <aqua>%staff%</aqua>: <white>%reason%</white>\n" +
                    "<gray>Total warnings: <yellow>%count%/%max%</yellow></gray></red>",
                    Map.of("staff", sender.getName(), "reason", reason,
                           "count", String.valueOf(count), "max", String.valueOf(max)));
        }

        // Broadcast if configured
        if (warnService.isBroadcastOnWarn()) {
            Bukkit.broadcast("§c[Warning] §b" + displayName + " §7was warned: §f" + reason,
                    "oreo.warn.see-broadcasts");
        }

        // Max warnings action
        if (max > 0 && count >= max) {
            applyMaxAction(sender, target, offTarget, targetUuid, displayName);
        }

        return true;
    }

    private void applyMaxAction(CommandSender staff, Player onlineTarget, OfflinePlayer offTarget,
                                UUID targetUuid, String displayName) {
        String action = warnService.getMaxAction();
        long durationMs = warnService.getMaxActionDurationMs();

        switch (action) {
            case "kick" -> {
                if (onlineTarget != null) {
                    onlineTarget.kickPlayer(Lang.color("&cYou have reached the maximum number of warnings."));
                    Lang.send(staff, "warn.auto-kick",
                            "<yellow>Auto-kicked <aqua>%player%</aqua> (max warnings reached).</yellow>",
                            Map.of("player", displayName));
                }
            }
            case "tempban" -> {
                String reason = "Reached maximum warnings";
                java.util.Date expires = new java.util.Date(System.currentTimeMillis() + durationMs);
                Bukkit.getBanList(BanList.Type.NAME).addBan(displayName, reason, expires, "OreoEssentials");
                if (onlineTarget != null) {
                    onlineTarget.kickPlayer(Lang.color("&cYou have been temporarily banned.\n&7Duration: &e"
                            + WarnService.friendlyDuration(durationMs)));
                }
                Lang.send(staff, "warn.auto-tempban",
                        "<yellow>Auto-tempbanned <aqua>%player%</aqua> for <gold>%duration%</gold> (max warnings).</yellow>",
                        Map.of("player", displayName, "duration", WarnService.friendlyDuration(durationMs)));
                warnService.clearAll(targetUuid);
            }
            case "ban" -> {
                String reason = "Reached maximum warnings";
                Bukkit.getBanList(BanList.Type.NAME).addBan(displayName, reason, null, "OreoEssentials");
                if (onlineTarget != null) {
                    onlineTarget.kickPlayer(Lang.color("&cYou have been permanently banned."));
                }
                Lang.send(staff, "warn.auto-ban",
                        "<yellow>Auto-banned <aqua>%player%</aqua> permanently (max warnings).</yellow>",
                        Map.of("player", displayName));
                warnService.clearAll(targetUuid);
            }
            // "none" — do nothing
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!sender.hasPermission(permission())) return Collections.emptyList();
        if (args.length == 1) {
            String typed = args[0].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(typed))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
