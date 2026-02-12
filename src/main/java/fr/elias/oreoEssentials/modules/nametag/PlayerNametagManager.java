

package fr.elias.oreoEssentials.modules.nametag;

import fr.elias.oreoEssentials.OreoEssentials;
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
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;


public class PlayerNametagManager implements Listener {

    private final OreoEssentials plugin;
    private final FileConfiguration config;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacySection();

    private boolean enabled;
    private String teamPrefix;
    private String teamSuffix;
    private int updateInterval;

    private BukkitRunnable updateTask;

    public PlayerNametagManager(OreoEssentials plugin, FileConfiguration config) {
        Bukkit.getLogger().info("[Nametag] Initializing SCOREBOARD-INTEGRATED system...");

        this.plugin = plugin;
        this.config = config;

        loadConfig();

        if (enabled) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
            startUpdateTask();

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    updateNametag(player);
                }
            }, 40L);

            Bukkit.getLogger().info("[Nametag] Team-based nametags enabled!");
            Bukkit.getLogger().info("[Nametag] - Prefix: " + teamPrefix);
            Bukkit.getLogger().info("[Nametag] - Suffix: " + teamSuffix);
        }
    }

    private void loadConfig() {
        this.enabled = config.getBoolean("nametag.enabled", true);
        this.updateInterval = config.getInt("nametag.update-interval-ticks", 100);
        this.teamPrefix = config.getString("nametag.team-prefix", "&6[%luckperms_primary_group%] &f");
        this.teamSuffix = config.getString("nametag.team-suffix", " &c❤%player_health%");
    }

    private void startUpdateTask() {
        if (updateTask != null) {
            updateTask.cancel();
        }

        updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    try {
                        updateNametag(player);
                    } catch (Exception ignored) {}
                }
            }
        };

        updateTask.runTaskTimer(plugin, 20L, updateInterval);
    }


    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!enabled) return;

        Player player = event.getPlayer();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                updateNametag(player);

                updatePlayerForAllViewers(player);
            }
        }, 25L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!enabled) return;

        removePlayerFromAllScoreboards(event.getPlayer());
    }


    public void updateNametag(Player player) {
        if (!enabled || player == null || !player.isOnline()) return;

        try {
            if (player.customName() != null) {
                player.customName(null);
                player.setCustomNameVisible(false);
            }

            updateTeamOnScoreboard(player, player.getScoreboard());

            updatePlayerForAllViewers(player);

        } catch (Exception e) {
            Bukkit.getLogger().warning("[Nametag] Failed to update for " + player.getName() + ": " + e.getMessage());
        }
    }


    private void updatePlayerForAllViewers(Player target) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer == target) continue; // Already updated above

            try {
                Scoreboard viewerBoard = viewer.getScoreboard();
                if (viewerBoard != null) {
                    updateTeamOnScoreboard(target, viewerBoard);
                }
            } catch (Exception ignored) {}
        }
    }


    private void updateTeamOnScoreboard(Player player, Scoreboard scoreboard) {
        if (scoreboard == null) return;

        try {
            String teamName = "nt_" + player.getName().toLowerCase();
            if (teamName.length() > 16) {
                teamName = teamName.substring(0, 16);
            }

            Team team = scoreboard.getTeam(teamName);
            if (team == null) {
                team = scoreboard.registerNewTeam(teamName);
            }

            if (!team.hasEntry(player.getName())) {
                team.addEntry(player.getName());
            }

            String prefix = replacePlaceholders(this.teamPrefix, player);
            String suffix = replacePlaceholders(this.teamSuffix, player);

            prefix = ChatColor.translateAlternateColorCodes('&', prefix);
            suffix = ChatColor.translateAlternateColorCodes('&', suffix);

            if (prefix.length() > 16) {
                prefix = prefix.substring(0, 16);
            }
            if (suffix.length() > 16) {
                suffix = suffix.substring(0, 16);
            }

            team.setPrefix(prefix);
            team.setSuffix(suffix);

            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);

        } catch (Exception e) {
            Bukkit.getLogger().warning("[Nametag] Failed to update team for " + player.getName()
                    + " on scoreboard: " + e.getMessage());
        }
    }


    private void removePlayerFromAllScoreboards(Player player) {
        String teamName = "nt_" + player.getName().toLowerCase();
        if (teamName.length() > 16) {
            teamName = teamName.substring(0, 16);
        }

        for (Player online : Bukkit.getOnlinePlayers()) {
            try {
                Scoreboard sb = online.getScoreboard();
                if (sb == null) continue;

                Team team = sb.getTeam(teamName);
                if (team != null) {
                    team.removeEntry(player.getName());
                    if (team.getEntries().isEmpty()) {
                        team.unregister();
                    }
                }
            } catch (Exception ignored) {}
        }
    }


    private String replacePlaceholders(String text, Player player) {
        if (text == null || text.isEmpty()) return "";

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                text = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
            } catch (Throwable ignored) {}
        }

        text = text.replace("%player_name%", player.getName());
        text = text.replace("%player_displayname%", player.getDisplayName());

        try {
            double health = player.getHealth();
            String healthStr = String.format("%.1f", health);
            text = text.replace("%player_health%", healthStr);

            double maxHealth = player.getMaxHealth();
            String maxHealthStr = String.format("%.1f", maxHealth);
            text = text.replace("%player_max_health%", maxHealthStr);
        } catch (Exception ignored) {}

        text = text.replace("%player_level%", String.valueOf(player.getLevel()));
        text = text.replace("%player_ping%", String.valueOf(getPing(player)));
        text = text.replace("%player_world%", player.getWorld().getName());
        text = text.replace("%player_gamemode%", player.getGameMode().name());

        text = text.replace("%luckperms_prefix%", getLuckPermsPrefix(player));
        text = text.replace("%luckperms_suffix%", getLuckPermsSuffix(player));
        text = text.replace("%luckperms_primary_group%", getPrimaryGroup(player));

        return text;
    }


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
                removePlayerFromAllScoreboards(player);
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
        removePlayerFromAllScoreboards(player);
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
            removePlayerFromAllScoreboards(player);
        }

        Bukkit.getLogger().info("[Nametag] Shutdown complete.");
    }
}