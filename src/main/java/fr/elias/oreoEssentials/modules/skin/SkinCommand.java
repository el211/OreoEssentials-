package fr.elias.oreoEssentials.modules.skin;

import com.destroystokyo.paper.profile.PlayerProfile;
import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Command to change player skins to match other players.
 * Supports both online and offline player lookups via Mojang API.
 * Requires Paper 1.18.2+ for live skin updates.
 */
public class SkinCommand implements OreoCommand, org.bukkit.command.TabCompleter {

    @Override
    public String name() {
        return "skin";
    }

    @Override
    public List<String> aliases() {
        return List.of();
    }

    @Override
    public String permission() {
        return "oreo.skin";
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

        if (args.length < 1) {
            self.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " <player>");
            self.sendMessage(ChatColor.GRAY + "Examples:");
            self.sendMessage(ChatColor.GRAY + "  /skin Notch");
            self.sendMessage(ChatColor.GRAY + "  /skin reset (to restore your own skin)");
            return true;
        }

        String target = args[0];

        if (target.equalsIgnoreCase("reset")) {
            resetSkin(self);
            return true;
        }

        PlayerProfile onlineProfile = SkinUtil.onlineProfileOf(target);
        if (onlineProfile != null) {
            applySkin(self, onlineProfile, target);
            return true;
        }

        self.sendMessage(ChatColor.GRAY + "Fetching skin for " + ChatColor.WHITE + target + ChatColor.GRAY + "...");

        Bukkit.getScheduler().runTaskAsynchronously(OreoEssentials.get(), () -> {
            UUID uuid = MojangSkinFetcher.fetchUuid(target);
            if (uuid == null) {
                self.sendMessage(ChatColor.RED + "Player '" + target + "' not found.");
                self.sendMessage(ChatColor.GRAY + "Make sure you typed the name correctly.");
                return;
            }

            PlayerProfile profile = MojangSkinFetcher.fetchProfileWithTextures(uuid, target);
            if (profile == null) {
                self.sendMessage(ChatColor.RED + "Failed to fetch skin for " + target);
                self.sendMessage(ChatColor.GRAY + "The Mojang API may be down. Try again later.");
                return;
            }

            // Step 3: Apply the skin on main thread
            Bukkit.getScheduler().runTask(OreoEssentials.get(), () -> {
                applySkin(self, profile, target);
            });
        });

        return true;
    }

    /**
     * Applies a skin from sourceProfile to the target player.
     *
     * @param player The player receiving the new skin
     * @param sourceProfile The profile containing the skin to copy
     * @param sourceName Name to display in success message
     */
    private void applySkin(Player player, PlayerProfile sourceProfile, String sourceName) {
        if (!sourceProfile.hasTextures() || sourceProfile.getTextures().getSkin() == null) {
            player.sendMessage(ChatColor.RED + "Player " + sourceName + " has no skin texture.");
            return;
        }

        PlayerProfile myProfile = player.getPlayerProfile();
        SkinUtil.copyTextures(sourceProfile, myProfile);

        boolean applied = SkinUtil.applyProfile(player, myProfile);

        if (!applied) {
            player.sendMessage(ChatColor.YELLOW + "⚠ Your server doesn't support live skin updates.");
            player.sendMessage(ChatColor.GRAY + "The skin will apply after you rejoin.");
            return;
        }

        SkinRefresh.refresh(player);

        player.sendMessage(ChatColor.GREEN + "✓ Your skin is now " + ChatColor.AQUA + sourceName + ChatColor.GREEN + "!");
        player.sendMessage(ChatColor.GRAY + "Use /skin reset to restore your original skin.");
    }

    /**
     * Resets a player's skin to their original Mojang skin.
     * Fetches the latest profile data from Mojang servers.
     *
     * @param player The player to reset
     */
    private void resetSkin(Player player) {
        player.sendMessage(ChatColor.GRAY + "Restoring your original skin...");

        Bukkit.getScheduler().runTaskAsynchronously(OreoEssentials.get(), () -> {
            try {
                PlayerProfile original = Bukkit.createProfile(player.getUniqueId(), player.getName());

                PlayerProfile updated = original.update().get(10, java.util.concurrent.TimeUnit.SECONDS);

                Bukkit.getScheduler().runTask(OreoEssentials.get(), () -> {
                    boolean applied = SkinUtil.applyProfile(player, updated);

                    if (applied) {
                        SkinRefresh.refresh(player);
                        player.sendMessage(ChatColor.GREEN + "✓ Your original skin has been restored!");
                    } else {
                        player.sendMessage(ChatColor.YELLOW + "⚠ Skin restored, but you may need to rejoin.");
                    }
                });

            } catch (java.util.concurrent.TimeoutException e) {
                Bukkit.getScheduler().runTask(OreoEssentials.get(), () -> {
                    player.sendMessage(ChatColor.RED + "Failed to reset skin: Request timed out.");
                    player.sendMessage(ChatColor.GRAY + "The Mojang API may be slow. Try again later.");
                    SkinDebug.log("Reset skin timeout for " + player.getName());
                });
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(OreoEssentials.get(), () -> {
                    player.sendMessage(ChatColor.RED + "Failed to reset skin: " + e.getMessage());
                    SkinDebug.log("Reset skin error for " + player.getName() + ": " + e);
                    e.printStackTrace();
                });
            }
        });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, org.bukkit.command.Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase(Locale.ROOT);

            List<String> suggestions = Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(partial))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());

            if ("reset".startsWith(partial)) {
                suggestions.add(0, "reset");
            }

            return suggestions;
        }

        return List.of();
    }
}