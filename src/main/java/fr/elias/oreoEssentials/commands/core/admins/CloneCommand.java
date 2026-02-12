package fr.elias.oreoEssentials.commands.core.admins;

import com.destroystokyo.paper.profile.PlayerProfile;
import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.modules.skin.MojangSkinFetcher;
import fr.elias.oreoEssentials.modules.skin.SkinDebug;
import fr.elias.oreoEssentials.modules.skin.SkinRefresh;
import fr.elias.oreoEssentials.modules.skin.SkinUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Command to clone another player's identity (skin + name).
 * This allows an admin to impersonate another player for testing or moderation.
 * Requires Paper 1.18.2+ for full functionality.
 */
public class CloneCommand implements OreoCommand, org.bukkit.command.TabCompleter {

    @Override
    public String name() {
        return "clone";
    }

    @Override
    public List<String> aliases() {
        return List.of();
    }

    @Override
    public String permission() {
        return "oreo.clone";
    }

    @Override
    public String usage() {
        return "<player>";
    }

    @Override
    public boolean playerOnly() {
        return true;
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        Player self = (Player) sender;

        // Show usage if no arguments
        if (args.length < 1) {
            self.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " <player>");
            self.sendMessage(ChatColor.GRAY + "Clone another player's skin and name.");
            return true;
        }

        String target = args[0];
        SkinDebug.p(self, "Cloning identity of '" + target + "'…");

        // Try to get profile from online player first
        PlayerProfile sourceProfile = SkinUtil.onlineProfileOf(target);

        // If not online, fetch from Mojang API
        if (sourceProfile == null) {
            SkinDebug.p(self, "Target not online. Fetching from Mojang API…");
            self.sendMessage(ChatColor.GRAY + "Fetching profile for " + ChatColor.WHITE + target + ChatColor.GRAY + "...");

            Bukkit.getScheduler().runTaskAsynchronously(OreoEssentials.get(), () -> {
                // Step 1: Get UUID
                UUID uuid = MojangSkinFetcher.fetchUuid(target);
                SkinDebug.p(self, "fetchUuid returned: " + uuid);

                if (uuid == null) {
                    self.sendMessage(ChatColor.RED + "Player '" + target + "' not found.");
                    self.sendMessage(ChatColor.GRAY + "Make sure you typed the name correctly.");
                    return;
                }

                // Step 2: Get profile with textures
                PlayerProfile fetchedProfile = MojangSkinFetcher.fetchProfileWithTextures(uuid, target);
                SkinDebug.p(self, "fetchProfileWithTextures returned: " + (fetchedProfile != null));

                if (fetchedProfile == null) {
                    self.sendMessage(ChatColor.RED + "Failed to fetch profile for " + target);
                    self.sendMessage(ChatColor.GRAY + "The Mojang API may be down. Try again later.");
                    return;
                }

                // Step 3: Apply on main thread
                Bukkit.getScheduler().runTask(OreoEssentials.get(), () -> {
                    applyClone(self, fetchedProfile, target);
                });
            });

            return true;
        }

        // Online player - apply immediately
        applyClone(self, sourceProfile, target);
        return true;
    }

    /**
     * Applies the cloned identity to the player.
     * Copies skin, textures, and attempts to change the display name.
     *
     * @param player The player receiving the clone
     * @param sourceProfile The profile to clone from
     * @param sourceName The name to display in messages
     */
    private void applyClone(Player player, PlayerProfile sourceProfile, String sourceName) {
        // Validate source has textures
        if (!sourceProfile.hasTextures() || sourceProfile.getTextures().getSkin() == null) {
            player.sendMessage(ChatColor.RED + "Player " + sourceName + " has no skin texture.");
            return;
        }

        // Get player's current profile
        PlayerProfile myProfile = player.getPlayerProfile();

        // Copy textures (skin + cape)
        SkinUtil.copyTextures(sourceProfile, myProfile);

        // Try to change the profile name (may not be supported on all builds)
        String targetName = sourceProfile.getName() != null ? sourceProfile.getName() : sourceName;
        boolean nameSet = SkinUtil.setProfileName(myProfile, targetName);

        // Apply the modified profile
        boolean applied = SkinUtil.applyProfile(player, myProfile);

        SkinDebug.p(player, "applyProfile=" + applied + ", nameSet=" + nameSet + " ; refreshing…");

        // Refresh appearance for all viewers
        if (applied) {
            SkinRefresh.refresh(player);
        }

        // Send feedback based on what succeeded
        if (!applied) {
            player.sendMessage(ChatColor.YELLOW + "⚠ Your server doesn't support live identity changes.");
            player.sendMessage(ChatColor.GRAY + "The clone will apply after you rejoin.");
        } else {
            StringBuilder message = new StringBuilder();
            message.append(ChatColor.GREEN).append("Cloned ");
            message.append(ChatColor.AQUA).append(targetName);
            message.append(ChatColor.GREEN).append(" (skin");

            if (nameSet) {
                message.append(" + name");
            }

            message.append(").");
            player.sendMessage(message.toString());

            if (!nameSet) {
                player.sendMessage(ChatColor.GRAY + "Note: Name change requires additional setup.");
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, org.bukkit.command.Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase(Locale.ROOT);

            // Suggest online player names
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(partial))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());
        }

        return List.of();
    }
}