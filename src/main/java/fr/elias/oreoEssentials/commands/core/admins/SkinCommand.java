// src/main/java/fr/elias/oreoEssentials/commands/core/admins/SkinCommand.java
package fr.elias.oreoEssentials.commands.core.admins;

import fr.elias.oreoEssentials.OreoEssentials;
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
import java.util.UUID;
import java.util.stream.Collectors;

public class SkinCommand implements OreoCommand, org.bukkit.command.TabCompleter {
    @Override public String name() { return "skin"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.skin"; }
    @Override public String usage() { return "<player>"; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        Player self = (Player) sender;

        if (args.length < 1) {
            self.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " <player>");
            self.sendMessage(ChatColor.GRAY + "Examples:");
            self.sendMessage(ChatColor.GRAY + "  /skin Notch");
            self.sendMessage(ChatColor.GRAY + "  /skin reset (to restore your own skin)");
            return true;
        }

        String target = args[0];

        // Special case: reset to own skin
        if (target.equalsIgnoreCase("reset")) {
            resetSkin(self);
            return true;
        }

        // Check if target is online first
        PlayerProfile onlineProfile = SkinUtil.onlineProfileOf(target);
        if (onlineProfile != null) {
            // Online player - apply immediately
            applySkin(self, onlineProfile, target);
            return true;
        }

        // Not online - fetch from Mojang asynchronously
        self.sendMessage(ChatColor.GRAY + "Fetching skin for " + ChatColor.WHITE + target + ChatColor.GRAY + "...");

        Bukkit.getScheduler().runTaskAsynchronously(OreoEssentials.get(), () -> {
            // Step 1: Get UUID
            UUID uuid = MojangSkinFetcher.fetchUuid(target);
            if (uuid == null) {
                self.sendMessage(ChatColor.RED + "Player '" + target + "' not found.");
                self.sendMessage(ChatColor.GRAY + "Make sure you typed the name correctly.");
                return;
            }

            // Step 2: Fetch profile with textures
            PlayerProfile profile = MojangSkinFetcher.fetchProfileWithTextures(uuid, target);
            if (profile == null) {
                self.sendMessage(ChatColor.RED + "Failed to fetch skin for " + target);
                self.sendMessage(ChatColor.GRAY + "The Mojang API may be down. Try again later.");
                return;
            }

            // Step 3: Apply on main thread
            Bukkit.getScheduler().runTask(OreoEssentials.get(), () -> {
                applySkin(self, profile, target);
            });
        });

        return true;
    }

    private void applySkin(Player player, PlayerProfile sourceProfile, String sourceName) {
        // Verify textures exist
        if (sourceProfile.getTextures() == null || sourceProfile.getTextures().getSkin() == null) {
            player.sendMessage(ChatColor.RED + "Player " + sourceName + " has no skin texture.");
            return;
        }

        // Copy textures to player's profile
        PlayerProfile myProfile = player.getPlayerProfile();
        SkinUtil.copyTextures(sourceProfile, myProfile);

        // Apply the profile
        boolean applied = SkinUtil.applyProfile(player, myProfile);

        if (!applied) {
            player.sendMessage(ChatColor.YELLOW + "⚠ Your server doesn't support live skin updates.");
            player.sendMessage(ChatColor.GRAY + "The skin will apply after you rejoin.");
            return;
        }

        // Refresh for other players
        SkinRefresh.refresh(player);

        player.sendMessage(ChatColor.GREEN + "✓ Your skin is now " + ChatColor.AQUA + sourceName + ChatColor.GREEN + "!");
        player.sendMessage(ChatColor.GRAY + "Use /skin reset to restore your original skin.");
    }

    private void resetSkin(Player player) {
        player.sendMessage(ChatColor.GRAY + "Restoring your original skin...");

        Bukkit.getScheduler().runTaskAsynchronously(OreoEssentials.get(), () -> {
            try {
                // Fetch fresh profile from Mojang
                PlayerProfile original = Bukkit.createPlayerProfile(player.getUniqueId(), player.getName());

                // Update() returns CompletableFuture - wait for it
                PlayerProfile updated = original.update().get(10, java.util.concurrent.TimeUnit.SECONDS);

                // Apply on main thread
                Bukkit.getScheduler().runTask(OreoEssentials.get(), () -> {
                    boolean applied = SkinUtil.applyProfile(player, updated);

                    if (applied) {
                        SkinRefresh.refresh(player);
                        player.sendMessage(ChatColor.GREEN + "✓ Your original skin has been restored!");
                    } else {
                        player.sendMessage(ChatColor.YELLOW + "⚠ Skin restored, but you may need to rejoin.");
                    }
                });

            } catch (Exception e) {
                Bukkit.getScheduler().runTask(OreoEssentials.get(), () -> {
                    player.sendMessage(ChatColor.RED + "Failed to reset skin: " + e.getMessage());
                    SkinDebug.log("Reset skin error: " + e);
                });
            }
        });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, org.bukkit.command.Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            String p = args[0].toLowerCase(Locale.ROOT);
            List<String> suggestions = Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(p))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());

            // Add "reset" option
            if ("reset".startsWith(p)) {
                suggestions.add(0, "reset");
            }

            return suggestions;
        }
        return List.of();
    }
}
