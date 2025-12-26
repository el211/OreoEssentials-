// File: src/main/java/fr/elias/oreoEssentials/commands/core/moderation/BanCommand.java
package fr.elias.oreoEssentials.commands.core.moderation;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.integration.DiscordModerationNotifier;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BanCommand implements OreoCommand {

    @Override public String name() { return "ban"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.ban"; }
    @Override public String usage() { return "<player> [duration] [reason...]"; }
    @Override public boolean playerOnly() { return false; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length < 1) {
            Lang.send(sender, "moderation.ban.usage",
                    "<yellow>Usage: /%label% <player> [duration] [reason]</yellow>",
                    Map.of("label", label));
            Lang.send(sender, "moderation.ban.usage-examples",
                    "<gray>Duration examples: 10m, 2h, 3d4h, 1w</gray>");
            return true;
        }

        String targetName = args[0];
        OfflinePlayer target = resolvePlayer(targetName);
        if (target == null || (target.getName() == null && !target.hasPlayedBefore())) {
            Lang.send(sender, "moderation.ban.player-not-found",
                    "<red>Player not found: %player%</red>",
                    Map.of("player", targetName));
            return true;
        }

        Date expires = null;
        String reason = Lang.msgWithDefault(
                "moderation.ban.default-reason",
                "Banned by an operator.",
                sender instanceof Player ? (Player) sender : null
        );

        if (args.length >= 2) {
            Long ms = parseDurationMillis(args[1]);
            if (ms != null) {
                expires = new Date(System.currentTimeMillis() + ms);
                if (args.length >= 3) {
                    reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                }
            } else {
                reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            }
        }

        // Add to server ban list
        Bukkit.getBanList(BanList.Type.NAME).addBan(target.getName(), reason, expires, sender.getName());

        // If online, kick immediately with reason (and expiry, if any)
        if (target.isOnline()) {
            Player p = target.getPlayer();
            if (p != null) {
                String kickMsg;
                if (expires != null) {
                    kickMsg = Lang.msgWithDefault(
                            "moderation.ban.kick-message-temp",
                            "<red>%reason%</red>\n<gray>Until: %expires%</gray>",
                            Map.of("reason", reason, "expires", expires.toString()),
                            p
                    );
                } else {
                    kickMsg = Lang.msgWithDefault(
                            "moderation.ban.kick-message-perm",
                            "<red>%reason%</red>",
                            Map.of("reason", reason),
                            p
                    );
                }
                p.kickPlayer(kickMsg);
            }
        }

        // Feedback to staff
        if (expires != null) {
            Lang.send(sender, "moderation.ban.success-temp",
                    "<green>Banned <aqua>%player%</aqua> until <yellow>%expires%</yellow>. Reason: <yellow>%reason%</yellow></green>",
                    Map.of("player", target.getName(), "expires", expires.toString(), "reason", reason));
        } else {
            Lang.send(sender, "moderation.ban.success-perm",
                    "<green>Banned <aqua>%player%</aqua> permanently. Reason: <yellow>%reason%</yellow></green>",
                    Map.of("player", target.getName(), "reason", reason));
        }

        // Discord notification
        DiscordModerationNotifier mod = OreoEssentials.get().getDiscordMod();
        if (mod != null && mod.isEnabled()) {
            Long expiresAt = (expires == null) ? null : expires.getTime();
            mod.notifyBan(target.getName(), target.getUniqueId(), reason, sender.getName(), expiresAt);
        }

        return true;
    }

    /* ----------------------------- Helpers ----------------------------- */

    private OfflinePlayer resolvePlayer(String name) {
        // 1) Exact online match
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;

        // 2) Search known offline players by case-insensitive name
        for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
            if (op.getName() != null && op.getName().equalsIgnoreCase(name)) {
                return op;
            }
        }

        // 3) Fallback (may create a profile if not cached)
        return Bukkit.getOfflinePlayer(name);
    }

    private static final Pattern DURATION_TOKEN = Pattern.compile("(\\d+)([smhdw])", Pattern.CASE_INSENSITIVE);

    private static Long parseDurationMillis(String s) {
        if (s == null || s.isEmpty()) return null;
        Matcher m = DURATION_TOKEN.matcher(s);
        long total = 0;
        int found = 0;
        while (m.find()) {
            long val = Long.parseLong(m.group(1));
            switch (Character.toLowerCase(m.group(2).charAt(0))) {
                case 's' -> total += Duration.ofSeconds(val).toMillis();
                case 'm' -> total += Duration.ofMinutes(val).toMillis();
                case 'h' -> total += Duration.ofHours(val).toMillis();
                case 'd' -> total += Duration.ofDays(val).toMillis();
                case 'w' -> total += Duration.ofDays(val * 7).toMillis();
            }
            found++;
        }
        return found == 0 ? null : total;
    }
}