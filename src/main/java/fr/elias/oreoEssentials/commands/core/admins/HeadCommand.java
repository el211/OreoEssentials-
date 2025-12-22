// File: src/main/java/fr/elias/oreoEssentials/commands/core/admins/HeadCommand.java
package fr.elias.oreoEssentials.commands.core.admins;

import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.util.Lang;
import fr.elias.oreoEssentials.util.MojangSkinFetcher;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class HeadCommand implements OreoCommand, org.bukkit.command.TabCompleter {
    @Override public String name() { return "head"; }
    @Override public List<String> aliases() { return List.of("playerhead"); }
    @Override public String permission() { return "oreo.head"; }
    @Override public String usage() { return "<player>"; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        Player self = (Player) sender;

        if (args.length < 1) {
            Lang.send(self, "admin.head.usage", null, Map.of("label", label));
            return true;
        }

        String target = args[0];
        PlayerProfile prof = null;

        Player online = Bukkit.getPlayerExact(target);
        if (online != null) prof = online.getPlayerProfile();
        if (prof == null) {
            UUID u = MojangSkinFetcher.fetchUuid(target);
            if (u != null) prof = MojangSkinFetcher.fetchProfileWithTextures(u, target);
        }
        if (prof == null) {
            Lang.send(self, "admin.head.cannot-resolve", null, Map.of("target", target));
            return true;
        }

        ItemStack skull = new ItemStack(Material.PLAYER_HEAD, 1);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta != null) {
            meta.setOwnerProfile(prof);
            String shown = prof.getName() != null ? prof.getName() : target;
            meta.setDisplayName(Lang.color("§b" + shown + "§7's Head"));
            skull.setItemMeta(meta);
        }

        HashMap<Integer, ItemStack> leftover = self.getInventory().addItem(skull);
        if (!leftover.isEmpty()) {
            self.getWorld().dropItemNaturally(self.getLocation(), skull);
            Lang.send(self, "admin.head.dropped", null, null);
        } else {
            Lang.send(self, "admin.head.given", null, Map.of("target", target));
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
