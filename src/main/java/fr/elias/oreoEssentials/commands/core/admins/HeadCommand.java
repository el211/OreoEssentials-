package fr.elias.oreoEssentials.commands.core.admins;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.util.Lang;
import fr.elias.oreoEssentials.modules.skin.MojangSkinFetcher;
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
            Lang.send(self, "admin.head.usage",
                    "<yellow>Usage: /<white>" + label + " <player></white></yellow>",
                    Map.of("label", label));
            return true;
        }

        String target = args[0];

        // Check if player is online first
        Player online = Bukkit.getPlayerExact(target);
        if (online != null) {
            giveHead(self, online.getPlayerProfile(), target);
            return true;
        }

        // Not online - fetch from Mojang asynchronously
        Lang.send(self, "admin.head.fetching",
                "<gray>Fetching head for <white>{target}</white>...</gray>",
                Map.of("target", target));

        Bukkit.getScheduler().runTaskAsynchronously(OreoEssentials.get(), () -> {
            // Step 1: Get UUID
            UUID uuid = MojangSkinFetcher.fetchUuid(target);
            if (uuid == null) {
                Bukkit.getScheduler().runTask(OreoEssentials.get(), () -> {
                    Lang.send(self, "admin.head.cannot-resolve",
                            "<red>Player <white>{target}</white> not found.</red>",
                            Map.of("target", target));
                });
                return;
            }

            // Step 2: Fetch profile with textures
            PlayerProfile profile = MojangSkinFetcher.fetchProfileWithTextures(uuid, target);
            if (profile == null) {
                Bukkit.getScheduler().runTask(OreoEssentials.get(), () -> {
                    Lang.send(self, "admin.head.fetch-failed",
                            "<red>Failed to fetch head for <white>{target}</white>.</red>",
                            Map.of("target", target));
                });
                return;
            }

            // Step 3: Give head on main thread
            Bukkit.getScheduler().runTask(OreoEssentials.get(), () -> {
                giveHead(self, profile, target);
            });
        });

        return true;
    }

    /**
     * Give a player head to the player (must be called on main thread).
     */
    private void giveHead(Player player, PlayerProfile profile, String targetName) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD, 1);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();

        if (meta != null) {
            meta.setOwnerProfile(profile);
            String displayName = profile.getName() != null ? profile.getName() : targetName;

            // Use Lang for the display name (supports both MiniMessage and legacy)
            String headName = Lang.msgLegacy("admin.head.item-name",
                    "<aqua>{target}</aqua><gray>'s Head</gray>",
                    Map.of("target", displayName),
                    player);

            meta.setDisplayName(headName);
            skull.setItemMeta(meta);
        }

        // Try to add to inventory
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(skull);

        if (!leftover.isEmpty()) {
            // Inventory full - drop at player's feet
            player.getWorld().dropItemNaturally(player.getLocation(), skull);
            Lang.send(player, "admin.head.dropped",
                    "<yellow>Head given! <gray>(Dropped at your feet - inventory full)</gray></yellow>",
                    Map.of());
        } else {
            // Successfully added to inventory
            Lang.send(player, "admin.head.given",
                    "<green>Given <white>{target}</white>'s head!</green>",
                    Map.of("target", targetName));
        }
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