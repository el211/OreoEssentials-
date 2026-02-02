package fr.elias.oreoEssentials.modules.chat;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.modules.integration.DiscordModerationNotifier;
import fr.elias.oreoEssentials.playerdirectory.PlayerDirectory;
import fr.elias.oreoEssentials.modules.chat.chatservices.MuteService;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class MuteCommand implements OreoCommand, org.bukkit.command.TabCompleter {

    private final MuteService mutes;
    private final ChatSyncManager sync; // may be null if cross-chat disabled

    public MuteCommand(MuteService mutes, ChatSyncManager sync) {
        this.mutes = mutes;
        this.sync = sync;
    }

    @Override public String name() { return "mute"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.mute"; }
    @Override public String usage() { return "<player> <duration> [reason]"; }
    @Override public boolean playerOnly() { return false; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            Lang.send(sender, "moderation.mute.usage",
                    "<yellow>Usage: /%label% <player> <duration> [reason]</yellow>",
                    Map.of("label", label));
            return true;
        }

        String targetArg = args[0];

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetArg);

        if (target == null || (target.getName() == null && !target.hasPlayedBefore())) {
            PlayerDirectory dir = OreoEssentials.get().getPlayerDirectory();
            if (dir != null) {
                try {
                    UUID id = dir.lookupUuidByName(targetArg);
                    if (id != null) {
                        target = Bukkit.getOfflinePlayer(id);
                    }
                } catch (Throwable ignored) { }
            }
        }

        if (target == null || (target.getName() == null && !target.hasPlayedBefore())) {
            Lang.send(sender, "moderation.mute.player-not-found",
                    "<red>Player not found: %target%</red>",
                    Map.of("target", targetArg));
            return true;
        }

        long durMs = MuteService.parseDurationToMillis(args[1]);
        if (durMs <= 0) {
            Lang.send(sender, "moderation.mute.invalid-duration",
                    "<red>Invalid duration. Use like 30s, 10m, 2h, 1d or seconds.</red>");
            return true;
        }

        String reason = "";
        if (args.length > 2) {
            reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        }

        long untilEpochMillis = System.currentTimeMillis() + durMs;

        mutes.mute(target.getUniqueId(), untilEpochMillis, reason, sender.getName());

        DiscordModerationNotifier mod = OreoEssentials.get().getDiscordMod();
        if (mod != null && mod.isEnabled()) {
            mod.notifyMute(target.getName(), target.getUniqueId(), reason, sender.getName(), untilEpochMillis);
        }

        if (sync != null) {
            try {
                sync.broadcastMute(target.getUniqueId(), untilEpochMillis, reason, sender.getName());
            } catch (Throwable t) {
                Lang.send(sender, "moderation.mute.broadcast-failed",
                        "<red>Warning: failed to broadcast mute to other servers.</red>");
            }
        }

        String durationStr = MuteService.friendlyRemaining(durMs);
        Map<String, String> vars = new HashMap<>();
        vars.put("target", target.getName());
        vars.put("staff", sender.getName());
        vars.put("duration", durationStr);
        vars.put("reason", reason);

        String reasonSuffix;
        if (reason.isEmpty()) {
            reasonSuffix = "";
        } else {
            reasonSuffix = Lang.msgWithDefault(
                    "moderation.mute.reason-suffix",
                    " <gray>| Reason: <yellow>%reason%</yellow></gray>",
                    Map.of("reason", reason),
                    sender instanceof Player ? (Player) sender : null
            );
        }
        vars.put("reason_suffix", reasonSuffix);

        Lang.send(sender, "moderation.mute.executor.success",
                "<green>Muted <yellow>%target%</yellow> for <white>%duration%</white>%reason_suffix%.</green>",
                vars);

        Player onlineTarget = target.getPlayer();
        if (onlineTarget != null && onlineTarget.isOnline()) {
            Lang.send(onlineTarget, "moderation.mute.target.notified",
                    "<red>You have been muted for <white>%duration%</white>.%reason_suffix%</red>",
                    vars);
        }

        for (Player pl : Bukkit.getOnlinePlayers()) {
            Lang.send(pl, "moderation.mute.broadcast",
                    "<gold>[Moderation]</gold> <yellow>%target%</yellow> <gray>was muted by</gray> <aqua>%staff%</aqua> <gray>for</gray> <white>%duration%</white>%reason_suffix%<gray>.</gray>",
                    vars);
        }

        String consoleMsg = Lang.msgWithDefault(
                "moderation.mute.broadcast-console",
                "[Moderation] %target% was muted by %staff% for %duration%%reason_suffix%.",
                vars,
                null
        );
        Bukkit.getConsoleSender().sendMessage(consoleMsg);

        if (sync != null) {
            try {
                String networkDefault = Lang.msgWithDefault(
                        "moderation.mute.broadcast",
                        "<gold>[Moderation]</gold> <yellow>%target%</yellow> <gray>was muted by</gray> <aqua>%staff%</aqua> <gray>for</gray> <white>%duration%</white>%reason_suffix%<gray>.</gray>",
                        vars,
                        null
                );
                sync.broadcastSystemMessage(networkDefault);
            } catch (Throwable ignored) { }
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(org.bukkit.command.CommandSender sender,
                                      org.bukkit.command.Command command,
                                      String label,
                                      String[] args) {
        if (!sender.hasPermission(permission())) return Collections.emptyList();

        if (args.length == 1) {
            final String want = args[0].toLowerCase(Locale.ROOT);
            Set<String> out = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

            Bukkit.getOnlinePlayers().forEach(p -> {
                String n = p.getName();
                if (n != null && n.toLowerCase(Locale.ROOT).startsWith(want)) {
                    out.add(n);
                }
            });

            PlayerDirectory dir = OreoEssentials.get().getPlayerDirectory();
            if (dir != null) {
                try {
                    Collection<String> names = dir.suggestOnlineNames(want, 50);
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

        if (args.length == 2) {
            String partial = args[1].toLowerCase(Locale.ROOT);
            return List.of("30s", "1m", "5m", "10m", "30m", "1h", "2h", "1d").stream()
                    .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(partial))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}