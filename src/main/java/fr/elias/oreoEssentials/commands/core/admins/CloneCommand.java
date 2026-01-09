package fr.elias.oreoEssentials.commands.core.admins;

import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.util.MojangSkinFetcher;
import fr.elias.oreoEssentials.util.SkinDebug;
import fr.elias.oreoEssentials.util.SkinRefresh;
import fr.elias.oreoEssentials.util.SkinUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.profile.PlayerProfile;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class CloneCommand implements OreoCommand, org.bukkit.command.TabCompleter {
    @Override public String name() { return "clone"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.clone"; }
    @Override public String usage() { return "<player>"; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        Player self = (Player) sender;

        if (args.length < 1) {
            self.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " <player>");
            return true;
        }
        String target = args[0];
        SkinDebug.p(self, "Cloning identity of '" + target + "'…");

        PlayerProfile src = SkinUtil.onlineProfileOf(target);
        if (src == null) {
            SkinDebug.p(self, "Target not online. Fetching UUID from Mojang…");
            UUID u = MojangSkinFetcher.fetchUuid(target);
            SkinDebug.p(self, "fetchUuid returned: " + u);
            if (u != null) {
                src = MojangSkinFetcher.fetchProfileWithTextures(u, target);
                SkinDebug.p(self, "fetchProfileWithTextures returned: " + (src != null));
            }
        }
        if (src == null) {
            self.sendMessage(ChatColor.RED + "Could not resolve " + target + " to clone.");
            return true;
        }

        PlayerProfile mine = self.getPlayerProfile();
        SkinUtil.copyTextures(src, mine);
        boolean nameSet = SkinUtil.setProfileName(mine, src.getName());
        boolean applied = SkinUtil.applyProfile(self, mine);

        SkinDebug.p(self, "applyProfile=" + applied + ", nameSet=" + nameSet + " ; refreshing…");
        SkinRefresh.refresh(self);

        if (!applied) {
            self.sendMessage(ChatColor.GREEN + "Skin copied. " +
                    ChatColor.GRAY + "(Live name/skin change may require PacketEvents or LibsDisguises.)");
        } else {
            self.sendMessage(ChatColor.GREEN + "Cloned " + ChatColor.AQUA + (src.getName() != null ? src.getName() : target)
                    + ChatColor.GREEN + " (skin" + (nameSet ? " + name" : "") + ").");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, org.bukkit.command.Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            String p = args[0].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(p))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
