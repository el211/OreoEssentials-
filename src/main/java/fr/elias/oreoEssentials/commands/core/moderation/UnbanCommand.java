package fr.elias.oreoEssentials.commands.core.moderation;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.BanEntry;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

import java.util.*;

public class UnbanCommand implements OreoCommand, org.bukkit.command.TabCompleter {

    @Override public String name() { return "unban"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.unban"; }
    @Override public String usage() { return "<player>"; }
    @Override public boolean playerOnly() { return false; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length < 1) {
            Lang.send(sender, "moderation.unban.usage",
                    "<yellow>Usage: /%label% <player></yellow>",
                    Map.of("label", label));
            return true;
        }

        String query = args[0];
        BanList nameBans = Bukkit.getBanList(BanList.Type.NAME);

        BanEntry match = findBanEntry(nameBans, query);
        if (match == null) {
            Lang.send(sender, "moderation.unban.not-banned",
                    "<gray>%player% is not banned.</gray>",
                    Map.of("player", query));
            return true;
        }

        String exactName = match.getTarget();
        nameBans.pardon(exactName);

        Lang.send(sender, "moderation.unban.success",
                "<green>Unbanned <aqua>%player%</aqua>.</green>",
                Map.of("player", exactName));

        var mod = OreoEssentials.get().getDiscordMod();
        if (mod != null && mod.isEnabled()) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(exactName);
            UUID id = (op != null) ? op.getUniqueId() : null;
            mod.notifyUnban(exactName, id, sender.getName());
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender,
                                      org.bukkit.command.Command command,
                                      String label,
                                      String[] args) {
        if (!sender.hasPermission(permission())) return Collections.emptyList();

        if (args.length == 1) {
            BanList nameBans = Bukkit.getBanList(BanList.Type.NAME);
            String prefix = args[0].toLowerCase(Locale.ROOT);

            List<String> names = new ArrayList<>();
            for (Object o : nameBans.getBanEntries()) {
                if (o instanceof BanEntry be) {
                    String t = be.getTarget();
                    if (t != null) names.add(t);
                } else if (o instanceof String s) {
                    names.add(s);
                }
            }

            names.removeIf(n -> !n.toLowerCase(Locale.ROOT).startsWith(prefix));
            names.sort(String.CASE_INSENSITIVE_ORDER);
            return names;
        }

        return Collections.emptyList();
    }

    private BanEntry findBanEntry(BanList banList, String name) {
        String q = name.toLowerCase(Locale.ROOT);
        for (Object o : banList.getBanEntries()) {
            if (o instanceof BanEntry be) {
                String target = be.getTarget();
                if (target != null && target.toLowerCase(Locale.ROOT).equals(q)) {
                    return be;
                }
            } else if (o instanceof String s) {
                if (s.toLowerCase(Locale.ROOT).equals(q)) {

                }
            }
        }
        return null;
    }
}