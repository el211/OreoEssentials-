// PlayerNametagManager.java - HYBRID VERSION
// Works with BOTH Feather Client (scoreboard teams) AND Vanilla (customName)
// Add this to: fr.elias.oreoEssentials.nametag package

package fr.elias.oreoEssentials.nametag;

import fr.elias.oreoEssentials.OreoEssentials;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.*;

/**
 * HYBRID Nametag Manager - Works with ALL clients!
 *
 * DUAL SYSTEM:
 * 1. Scoreboard Teams (for Feather/Lunar/Badlion) - 1 line, basic colors
 * 2. CustomName (for Vanilla) - Multi-line, full MiniMessage formatting
 *
 * This ensures EVERYONE sees nametags, regardless of client!
 */
public class PlayerNametagManager implements Listener {

    private final OreoEssentials plugin;
    private final FileConfiguration config;
    private final MiniMessage mm = MiniMessage.miniMessage();

    // Configuration
    private boolean enabled;
    private List<String> nametagLines;
    private int updateInterval;
    private boolean useMiniMessage;

    // Team-based settings (for client compatibility)
    private boolean useTeams;
    private String teamPrefix;
    private String teamSuffix;

    // Update task
    private BukkitRunnable updateTask;

    // Scoreboard for teams
    private Scoreboard scoreboard;

    public PlayerNametagManager(OreoEssentials plugin, FileConfiguration config) {
        Bukkit.getLogger().info("[Nametag] Initializing HYBRID nametag system...");

        this.plugin = plugin;
        this.config = config;
        this.scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();

        loadConfig();

        if (enabled) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
            startUpdateTask();

            // Update all online players
            for (Player player : Bukkit.getOnlinePlayers()) {
                updateNametag(player);
            }

            Bukkit.getLogger().info("[Nametag] HYBRID system enabled!");
            Bukkit.getLogger().info("[Nametag] - CustomName: " + nametagLines.size() + " lines (vanilla clients)");
            Bukkit.getLogger().info("[Nametag] - Teams: " + (useTeams ? "ENABLED" : "DISABLED") + " (modded clients)");
        }
    }

    private void loadConfig() {
        this.enabled = config.getBoolean("nametag.enabled", true);
        this.nametagLines = config.getStringList("nametag.lines");
        this.updateInterval = config.getInt("nametag.update-interval-ticks", 100);
        this.useMiniMessage = config.getBoolean("nametag.use-minimessage", true);

        // Team-based compatibility mode (for Feather/Lunar/Badlion)
        this.useTeams = config.getBoolean("nametag.team-compatibility", true);
        this.teamPrefix = config.getString("nametag.team-prefix", "&6[%luckperms_primary_group%] &f");
        this.teamSuffix = config.getString("nametag.team-suffix", "");

        // Default lines if none configured
        if (nametagLines.isEmpty()) {
            nametagLines.add("<gold>%luckperms_primary_group%</gold>");
            nametagLines.add("<white>%player_name%</white>");
        }
    }

    private void startUpdateTask() {
        if (updateTask != null) {
            updateTask.cancel();
        }

        updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    updateNametag(player);
                }
            }
        };

        updateTask.runTaskTimer(plugin, 20L, updateInterval);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!enabled) return;

        Player player = event.getPlayer();

        // Delay update to ensure player is fully loaded
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            updateNametag(player);
        }, 1L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!enabled) return;

        Player player = event.getPlayer();

        // Reset customName
        player.customName(null);
        player.setCustomNameVisible(false);

        // Remove from team
        if (useTeams) {
            removeFromTeam(player);
        }
    }

    /**
     * Update BOTH team-based AND customName nametags
     * This ensures compatibility with ALL clients!
     */
    public void updateNametag(Player player) {
        if (!enabled || player == null) return;

        try {
            // METHOD 1: Set CustomName (vanilla clients see this - multi-line, full formatting)
            updateCustomName(player);

            // METHOD 2: Set Team prefix/suffix (Feather/Lunar/Badlion see this - 1 line, basic colors)
            if (useTeams) {
                updateTeamNametag(player);
            }

        } catch (Exception e) {
            Bukkit.getLogger().warning("[Nametag] Failed to update nametag for " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * METHOD 1: CustomName (for vanilla clients)
     * Full multi-line MiniMessage formatting
     */
    private void updateCustomName(Player player) {
        try {
            // Build multi-line nametag
            StringBuilder nametagText = new StringBuilder();

            for (int i = 0; i < nametagLines.size(); i++) {
                String line = nametagLines.get(i);
                line = replacePlaceholders(line, player);
                nametagText.append(line);

                if (i < nametagLines.size() - 1) {
                    nametagText.append("\n");
                }
            }

            // Parse with MiniMessage
            Component nametagComponent;
            if (useMiniMessage) {
                nametagComponent = mm.deserialize(
                        nametagText.toString(),
                        createTagResolvers(player)
                );
            } else {
                nametagComponent = Component.text(nametagText.toString());
            }

            // Set customName
            player.customName(nametagComponent);
            player.setCustomNameVisible(true);

        } catch (Exception e) {
            Bukkit.getLogger().warning("[Nametag] CustomName update failed for " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * METHOD 2: Team prefix/suffix (for Feather/Lunar/Badlion)
     * 1 line, legacy color codes only
     */
    private void updateTeamNametag(Player player) {
        try {
            // Get or create team for this player
            String teamName = "nt_" + player.getName().toLowerCase();
            Team team = scoreboard.getTeam(teamName);

            if (team == null) {
                team = scoreboard.registerNewTeam(teamName);
            }

            // Add player to team
            if (!team.hasEntry(player.getName())) {
                team.addEntry(player.getName());
            }

            // Build prefix and suffix with placeholders
            String prefix = replacePlaceholders(this.teamPrefix, player);
            String suffix = replacePlaceholders(this.teamSuffix, player);

            // Convert color codes
            prefix = ChatColor.translateAlternateColorCodes('&', prefix);
            suffix = ChatColor.translateAlternateColorCodes('&', suffix);

            // Truncate if too long (Minecraft limits)
            if (prefix.length() > 16) {
                prefix = prefix.substring(0, 16);
            }
            if (suffix.length() > 16) {
                suffix = suffix.substring(0, 16);
            }

            // Set team properties
            team.setPrefix(prefix);
            team.setSuffix(suffix);

            // Optional: Set team color for the player name
            try {
                String group = getPrimaryGroup(player);
                ChatColor color = getColorForGroup(group);
                team.setColor(color);
            } catch (Exception ignored) {}

        } catch (Exception e) {
            Bukkit.getLogger().warning("[Nametag] Team update failed for " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Remove player from their team
     */
    private void removeFromTeam(Player player) {
        try {
            String teamName = "nt_" + player.getName().toLowerCase();
            Team team = scoreboard.getTeam(teamName);

            if (team != null) {
                team.removeEntry(player.getName());

                // Unregister team if empty
                if (team.getEntries().isEmpty()) {
                    team.unregister();
                }
            }
        } catch (Exception e) {
            Bukkit.getLogger().warning("[Nametag] Failed to remove team for " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Get color for a rank/group
     */
    private ChatColor getColorForGroup(String group) {
        return switch (group.toLowerCase()) {
            case "owner" -> ChatColor.DARK_RED;
            case "admin" -> ChatColor.RED;
            case "mod", "moderator" -> ChatColor.GOLD;
            case "helper" -> ChatColor.BLUE;
            case "vip" -> ChatColor.GREEN;
            case "mvp" -> ChatColor.AQUA;
            default -> ChatColor.GRAY;
        };
    }

    /**
     * Replace placeholders in text
     */
    private String replacePlaceholders(String text, Player player) {
        // PlaceholderAPI
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                text = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
            } catch (Throwable ignored) {}
        }

        // Basic placeholders
        text = text.replace("%player_name%", player.getName());
        text = text.replace("%player_displayname%", player.getDisplayName());
        text = text.replace("%player_health%", String.valueOf((int) Math.ceil(player.getHealth())));
        text = text.replace("%player_max_health%", String.valueOf((int) Math.ceil(player.getMaxHealth())));
        text = text.replace("%player_level%", String.valueOf(player.getLevel()));
        text = text.replace("%player_ping%", String.valueOf(getPing(player)));
        text = text.replace("%player_world%", player.getWorld().getName());
        text = text.replace("%player_gamemode%", player.getGameMode().name());

        // LuckPerms
        text = text.replace("%luckperms_prefix%", getLuckPermsPrefix(player));
        text = text.replace("%luckperms_suffix%", getLuckPermsSuffix(player));
        text = text.replace("%luckperms_primary_group%", getPrimaryGroup(player));

        return text;
    }

    /**
     * Create MiniMessage tag resolvers
     */
    private TagResolver createTagResolvers(Player player) {
        return TagResolver.resolver(
                Placeholder.unparsed("player_name", player.getName()),
                Placeholder.unparsed("player_displayname", player.getDisplayName()),
                Placeholder.unparsed("player_health", String.valueOf((int) Math.ceil(player.getHealth()))),
                Placeholder.unparsed("player_max_health", String.valueOf((int) Math.ceil(player.getMaxHealth()))),
                Placeholder.unparsed("player_level", String.valueOf(player.getLevel())),
                Placeholder.unparsed("player_ping", String.valueOf(getPing(player))),
                Placeholder.unparsed("player_world", player.getWorld().getName()),
                Placeholder.unparsed("player_gamemode", player.getGameMode().name()),
                Placeholder.unparsed("luckperms_primary_group", getPrimaryGroup(player))
        );
    }

    // ========== LuckPerms Integration ==========

    private String getLuckPermsPrefix(Player player) {
        try {
            LuckPerms lp = LuckPermsProvider.get();
            CachedMetaData meta = lp.getPlayerAdapter(Player.class).getMetaData(player);
            String prefix = meta.getPrefix();
            return prefix != null ? prefix : "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    private String getLuckPermsSuffix(Player player) {
        try {
            LuckPerms lp = LuckPermsProvider.get();
            CachedMetaData meta = lp.getPlayerAdapter(Player.class).getMetaData(player);
            String suffix = meta.getSuffix();
            return suffix != null ? suffix : "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    private String getPrimaryGroup(Player player) {
        try {
            LuckPerms lp = LuckPermsProvider.get();
            User user = lp.getPlayerAdapter(Player.class).getUser(player);
            if (user == null) return "default";

            String primary = user.getPrimaryGroup();
            return primary != null ? primary : "default";
        } catch (Throwable ignored) {
            return "default";
        }
    }

    private int getPing(Player player) {
        try {
            return player.getPing();
        } catch (Throwable ignored) {
            return -1;
        }
    }

    // ========== Public API ==========

    public void reload(FileConfiguration newConfig) {
        if (updateTask != null) {
            updateTask.cancel();
        }

        this.config.setDefaults(newConfig.getDefaults());
        loadConfig();

        if (enabled) {
            startUpdateTask();
            for (Player player : Bukkit.getOnlinePlayers()) {
                updateNametag(player);
            }
        } else {
            // Disable everything
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.customName(null);
                player.setCustomNameVisible(false);
                if (useTeams) {
                    removeFromTeam(player);
                }
            }
        }
    }

    public void forceUpdate(Player player) {
        updateNametag(player);
    }

    public void forceUpdateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            updateNametag(player);
        }
    }

    public void disableNametag(Player player) {
        player.customName(null);
        player.setCustomNameVisible(false);
        if (useTeams) {
            removeFromTeam(player);
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void shutdown() {
        if (updateTask != null) {
            updateTask.cancel();
        }

        // Clean up everything
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.customName(null);
            player.setCustomNameVisible(false);
            if (useTeams) {
                removeFromTeam(player);
            }
        }
    }
}