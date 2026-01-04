// PlayerNametagManager.java - TEAMS ONLY VERSION
// WORKAROUND: Don't use customName when scoreboard sidebar is active
// This fixes the issue where sidebar scoreboards hide customName

package fr.elias.oreoEssentials.nametag;

import fr.elias.oreoEssentials.OreoEssentials;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
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
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.List;

/**
 * TEAMS-ONLY Nametag Manager
 *
 * CRITICAL FIX: When scoreboard sidebar is active, Minecraft HIDES customName!
 * This version uses ONLY scoreboard teams for nametags.
 *
 * LIMITATION: Teams can only have prefix (16 chars) + name + suffix (16 chars)
 * No multi-line nametags possible with this approach, but it WORKS with scoreboards!
 */
public class PlayerNametagManager implements Listener {

    private final OreoEssentials plugin;
    private final FileConfiguration config;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacySection();

    // Configuration
    private boolean enabled;
    private List<String> nametagLines;
    private int updateInterval;

    // Team settings
    private String teamPrefix;
    private String teamSuffix;

    // Update task
    private BukkitRunnable updateTask;

    public PlayerNametagManager(OreoEssentials plugin, FileConfiguration config) {
        Bukkit.getLogger().info("[Nametag] Initializing TEAMS-ONLY nametag system...");
        Bukkit.getLogger().info("[Nametag] (CustomName disabled due to scoreboard conflicts)");

        this.plugin = plugin;
        this.config = config;

        loadConfig();

        if (enabled) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
            startUpdateTask();

            // Update all online players (delayed)
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    updateNametag(player);
                }
            }, 20L);

            Bukkit.getLogger().info("[Nametag] Teams-only system enabled!");
            Bukkit.getLogger().info("[Nametag] - Prefix: " + teamPrefix);
            Bukkit.getLogger().info("[Nametag] - Suffix: " + teamSuffix);
        }
    }

    private void loadConfig() {
        this.enabled = config.getBoolean("nametag.enabled", true);
        this.nametagLines = config.getStringList("nametag.lines");
        this.updateInterval = config.getInt("nametag.update-interval-ticks", 100);

        // Team prefix/suffix
        this.teamPrefix = config.getString("nametag.team-prefix", "&6[%luckperms_primary_group%] &f");
        this.teamSuffix = config.getString("nametag.team-suffix", "");

        // Convert first line to team prefix if configured
        if (!nametagLines.isEmpty() && teamPrefix.equals("&6[%luckperms_primary_group%] &f")) {
            // Try to convert MiniMessage to legacy for team prefix
            String firstLine = nametagLines.get(0);
            try {
                Component comp = mm.deserialize(firstLine
                        .replace("%luckperms_primary_group%", "GROUP")
                        .replace("%player_name%", "NAME"));
                String legacyStr = legacy.serialize(comp);
                // Keep it under 16 chars
                if (legacyStr.length() <= 16) {
                    teamPrefix = legacyStr.replace("GROUP", "%luckperms_primary_group%")
                            .replace("NAME", "%player_name%");
                }
            } catch (Exception ignored) {}
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

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!enabled) return;

        Player player = event.getPlayer();

        // Delay to let ScoreboardService create scoreboard first
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                updateNametag(player);
            }
        }, 10L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!enabled) return;

        Player player = event.getPlayer();

        // Remove from team
        try {
            removeFromTeam(player);
        } catch (Exception ignored) {}
    }

    /**
     * Update nametag using ONLY teams
     * CustomName is NOT used because it conflicts with scoreboard sidebar
     */
    public void updateNametag(Player player) {
        if (!enabled || player == null || !player.isOnline()) return;

        try {
            updateTeamNametag(player);
        } catch (Exception e) {
            Bukkit.getLogger().warning("[Nametag] Failed to update nametag for " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Set team prefix/suffix for nametag
     * This works even when scoreboard sidebar is active!
     */
    private void updateTeamNametag(Player player) {
        try {
            // Get player's CURRENT scoreboard (from ScoreboardService)
            Scoreboard sb = player.getScoreboard();

            if (sb == null) {
                sb = Bukkit.getScoreboardManager().getMainScoreboard();
            }

            // Check if player has sidebar active
            boolean hasSidebar = false;
            for (Objective obj : sb.getObjectives()) {
                if (obj.getDisplaySlot() == DisplaySlot.SIDEBAR) {
                    hasSidebar = true;
                    break;
                }
            }

            // Create unique team name
            String teamName = "nt_" + player.getName().toLowerCase();
            if (teamName.length() > 16) {
                teamName = teamName.substring(0, 16);
            }

            // Get or create team ON THE PLAYER'S CURRENT SCOREBOARD
            Team team = sb.getTeam(teamName);
            if (team == null) {
                team = sb.registerNewTeam(teamName);
            }

            // Add player to team
            if (!team.hasEntry(player.getName())) {
                team.addEntry(player.getName());
            }

            // Replace placeholders and convert to legacy color codes
            String prefix = replacePlaceholders(this.teamPrefix, player);
            String suffix = replacePlaceholders(this.teamSuffix, player);

            // Convert MiniMessage to legacy if needed
            prefix = convertToLegacy(prefix, player);
            suffix = convertToLegacy(suffix, player);

            // Apply legacy color codes
            prefix = ChatColor.translateAlternateColorCodes('&', prefix);
            suffix = ChatColor.translateAlternateColorCodes('&', suffix);

            // Truncate to 16 chars (Minecraft limitation)
            if (prefix.length() > 16) {
                prefix = prefix.substring(0, 16);
            }
            if (suffix.length() > 16) {
                suffix = suffix.substring(0, 16);
            }

            team.setPrefix(prefix);
            team.setSuffix(suffix);

        } catch (Exception e) {
            Bukkit.getLogger().warning("[Nametag] Team update failed for " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Try to convert MiniMessage tags to legacy color codes
     */
    private String convertToLegacy(String text, Player player) {
        if (text == null || text.isEmpty()) return "";

        // If it contains MiniMessage tags, try to convert
        if (text.contains("<") && text.contains(">")) {
            try {
                Component comp = mm.deserialize(text);
                text = legacy.serialize(comp);
            } catch (Exception e) {
                // Failed to parse MiniMessage, keep original
            }
        }

        return text;
    }

    /**
     * Remove player from their team
     */
    private void removeFromTeam(Player player) {
        try {
            Scoreboard sb = player.getScoreboard();
            if (sb == null) return;

            String teamName = "nt_" + player.getName().toLowerCase();
            if (teamName.length() > 16) {
                teamName = teamName.substring(0, 16);
            }

            Team team = sb.getTeam(teamName);
            if (team == null) return;

            team.removeEntry(player.getName());

            if (team.getEntries().isEmpty()) {
                team.unregister();
            }
        } catch (Exception e) {
            // Silently fail
        }
    }

    /**
     * Replace placeholders in text
     */
    private String replacePlaceholders(String text, Player player) {
        if (text == null || text.isEmpty()) return "";

        // PlaceholderAPI
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                text = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
            } catch (Throwable ignored) {}
        }

        // Basic placeholders
        text = text.replace("%player_name%", player.getName());
        text = text.replace("%player_displayname%", player.getDisplayName());

        try {
            text = text.replace("%player_health%", String.valueOf((int) Math.ceil(player.getHealth())));
            text = text.replace("%player_max_health%", String.valueOf((int) Math.ceil(player.getMaxHealth())));
        } catch (Exception ignored) {}

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
        Bukkit.getLogger().info("[Nametag] Reloading...");

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

            Bukkit.getLogger().info("[Nametag] Reload complete!");
        } else {
            for (Player player : Bukkit.getOnlinePlayers()) {
                try {
                    removeFromTeam(player);
                } catch (Exception ignored) {}
            }

            Bukkit.getLogger().info("[Nametag] Disabled.");
        }
    }

    public void forceUpdate(Player player) {
        if (player != null && player.isOnline()) {
            updateNametag(player);
        }
    }

    public void forceUpdateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            updateNametag(player);
        }
    }

    public void disableNametag(Player player) {
        try {
            removeFromTeam(player);
        } catch (Exception ignored) {}
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void shutdown() {
        Bukkit.getLogger().info("[Nametag] Shutting down...");

        if (updateTask != null) {
            updateTask.cancel();
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                removeFromTeam(player);
            } catch (Exception ignored) {}
        }

        Bukkit.getLogger().info("[Nametag] Shutdown complete.");
    }
}