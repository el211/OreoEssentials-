package fr.elias.oreoEssentials.commands.core.moderation;

import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.services.GodService;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class GodCommand implements OreoCommand {
    private final GodService god;

    public GodCommand(GodService god) {
        this.god = god;
    }

    @Override public String name() { return "god"; }
    @Override public List<String> aliases() { return List.of(".god"); }
    @Override public String permission() { return "oreo.god"; }
    @Override public String usage() { return "[player]"; }
    @Override public boolean playerOnly() { return false; } // allow console for others

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player p)) {
                Lang.send(sender, "moderation.god.usage",
                        "<yellow>Usage: /%label% <player></yellow>",
                        Map.of("label", label));
                return true;
            }

            boolean enabled = toggleAndApply(p, p.getUniqueId());

            if (enabled) {
                Lang.send(p, "moderation.god.self-enabled",
                        "<green>God mode <aqua>enabled</aqua>. You are now unbeatable.</green>");
            } else {
                Lang.send(p, "moderation.god.self-disabled",
                        "<red>God mode <aqua>disabled</aqua>.</red>");
            }
            return true;
        }

        if (!sender.hasPermission("oreo.god.others")) {
            Lang.send(sender, "moderation.god.no-permission-others",
                    "<red>You don't have permission to toggle god for others.</red>");
            return true;
        }

        Player target = findOnlinePlayer(args[0]);
        if (target == null) {
            Lang.send(sender, "moderation.god.player-not-found",
                    "<red>Player not found or not online: <yellow>%player%</yellow></red>",
                    Map.of("player", args[0]));
            return true;
        }

        boolean enabled = toggleAndApply(target, target.getUniqueId());

        if (enabled) {
            Lang.send(target, "moderation.god.target-enabled",
                    "<green>An admin enabled your god mode.</green>");
        } else {
            Lang.send(target, "moderation.god.target-disabled",
                    "<red>An admin disabled your god mode.</red>");
        }

        if (enabled) {
            Lang.send(sender, "moderation.god.other-enabled",
                    "<green>Enabled god mode for <aqua>%player%</aqua>.</green>",
                    Map.of("player", target.getName()));
        } else {
            Lang.send(sender, "moderation.god.other-disabled",
                    "<red>Disabled god mode for <aqua>%player%</aqua>.</red>",
                    Map.of("player", target.getName()));
        }

        return true;
    }

    private boolean toggleAndApply(Player target, UUID uuid) {
        boolean enabled = god.toggle(uuid);
        try {
            target.setInvulnerable(enabled);
        } catch (Throwable ignored) {}

        if (enabled) {
            target.setHealth(Math.min(target.getMaxHealth(), target.getMaxHealth()));
            target.setFoodLevel(20);
            target.setSaturation(20f);
            target.setFireTicks(0);
        }
        return enabled;
    }

    private Player findOnlinePlayer(String input) {
        Player p = Bukkit.getPlayerExact(input);
        if (p != null) return p;

        String lower = input.toLowerCase(Locale.ROOT);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getName().toLowerCase(Locale.ROOT).startsWith(lower)) {
                return online;
            }
        }
        return null;
    }
}