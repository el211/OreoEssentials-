

package fr.elias.oreoEssentials.modules.nametag;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.scoreboard.FoliaScoreboard;
import fr.elias.oreoEssentials.util.OreScheduler;
import fr.elias.oreoEssentials.util.OreTask;
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
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;


public class PlayerNametagManager implements Listener {

    private final OreoEssentials plugin;
    private final FileConfiguration config;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacySection();

    private boolean enabled;
    private String teamPrefix;
    private String teamSuffix;
    private int updateInterval;

    private OreTask updateTask;

    /** Folia path: NMS PlayerTeam objects (as Object) keyed by team name. All access via reflection. */
    private final ConcurrentHashMap<String, Object> foliaTeams = new ConcurrentHashMap<>();

    public PlayerNametagManager(OreoEssentials plugin, FileConfiguration config) {
        Bukkit.getLogger().info("[Nametag] Initializing SCOREBOARD-INTEGRATED system...");

        this.plugin = plugin;
        this.config = config;

        loadConfig();

        if (enabled) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
            startUpdateTask();

            OreScheduler.runLater(plugin, () -> {
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

        updateTask = OreScheduler.runTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                try {
                    updateNametag(player);
                } catch (Exception ignored) {}
            }
        }, 20L, updateInterval);
    }


    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!enabled) return;

        Player player = event.getPlayer();

        OreScheduler.runLater(plugin, () -> {
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
        // On Folia updateTeamFolia already broadcasts to all viewers in one pass
        if (OreScheduler.isFolia()) return;
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
        if (OreScheduler.isFolia()) {
            updateTeamFolia(player);
            return;
        }
        if (scoreboard == null) return;

        try {
            String teamName = getTeamName(player);

            org.bukkit.scoreboard.Team team = scoreboard.getTeam(teamName);
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

            team.setOption(org.bukkit.scoreboard.Team.Option.NAME_TAG_VISIBILITY,
                    org.bukkit.scoreboard.Team.OptionStatus.ALWAYS);

        } catch (Exception e) {
            Bukkit.getLogger().warning("[Nametag] Failed to update team for " + player.getName()
                    + " on scoreboard: " + e.getMessage());
        }
    }

    /**
     * Folia path: Bukkit Scoreboard API is fully blocked on Folia.
     * Build NMS PlayerTeam via reflection and send ClientboundSetPlayerTeamPacket
     * directly to every viewer's connection.send() — which IS thread-safe in Folia.
     */
    private void updateTeamFolia(Player player) {
        try {
            String teamName = getTeamName(player);

            String prefix = ChatColor.translateAlternateColorCodes('&',
                    replacePlaceholders(this.teamPrefix, player));
            String suffix = ChatColor.translateAlternateColorCodes('&',
                    replacePlaceholders(this.teamSuffix, player));

            // ── Reflect NMS classes ───────────────────────────────────────────
            Class<?> cPlayerTeam = Class.forName("net.minecraft.world.scores.PlayerTeam");
            Class<?> cSb = Class.forName("net.minecraft.world.scores.Scoreboard");
            Class<?> cVisibility = Class.forName("net.minecraft.world.scores.Team$Visibility");
            Class<?> cNmsComp = Class.forName("net.minecraft.network.chat.Component");
            Class<?> cTeamPkt = Class.forName("net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket");

            // ── Get / create the PlayerTeam ───────────────────────────────────
            boolean isNew = !foliaTeams.containsKey(teamName);
            Object team = foliaTeams.computeIfAbsent(teamName, n -> {
                try {
                    Object t = cPlayerTeam.getDeclaredConstructor(cSb, String.class)
                            .newInstance(FoliaScoreboard.DUMMY_SCOREBOARD, n);
                    return t;
                } catch (Throwable ex) { return null; }
            });
            if (team == null) return;

            // ── Set prefix / suffix ───────────────────────────────────────────
            Object nmsPrefix = FoliaScoreboard.toNms(
                    LegacyComponentSerializer.legacySection().deserialize(prefix));
            Object nmsSuffix = FoliaScoreboard.toNms(
                    LegacyComponentSerializer.legacySection().deserialize(suffix));

            Method setPrefix = cPlayerTeam.getDeclaredMethod("setPlayerPrefix", cNmsComp);
            Method setSuffix = cPlayerTeam.getDeclaredMethod("setPlayerSuffix", cNmsComp);
            setPrefix.setAccessible(true);
            setSuffix.setAccessible(true);
            setPrefix.invoke(team, nmsPrefix);
            setSuffix.invoke(team, nmsSuffix);

            // ── Set visibility to ALWAYS ──────────────────────────────────────
            Method setVis = cPlayerTeam.getDeclaredMethod("setNameTagVisibility", cVisibility);
            setVis.setAccessible(true);
            Object visAlways = cVisibility.getField("ALWAYS").get(null);
            setVis.invoke(team, visAlways);

            // ── Add player to the team's player set ───────────────────────────
            Method getPlayers = cPlayerTeam.getDeclaredMethod("getPlayers");
            getPlayers.setAccessible(true);
            @SuppressWarnings("unchecked")
            Collection<String> players = (Collection<String>) getPlayers.invoke(team);
            if (!players.contains(player.getName())) {
                players.add(player.getName());
                isNew = true;
            }

            // ── Build and broadcast the packet ────────────────────────────────
            Method createPkt = cTeamPkt.getDeclaredMethod("createAddOrModifyPacket", cPlayerTeam, boolean.class);
            createPkt.setAccessible(true);
            Object packet = createPkt.invoke(null, team, isNew);

            for (Player viewer : Bukkit.getOnlinePlayers()) {
                try { FoliaScoreboard.sendPacketToPlayer(viewer, packet); } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {
            // Nametag is purely cosmetic — never crash on it
        }
    }


    private static String getTeamName(Player player) {
        String name = "nt_" + player.getName().toLowerCase();
        return name.length() > 16 ? name.substring(0, 16) : name;
    }

    private void removePlayerFromAllScoreboards(Player player) {
        String teamName = getTeamName(player);

        if (OreScheduler.isFolia()) {
            Object team = foliaTeams.remove(teamName);
            if (team != null) {
                try {
                    Class<?> cPlayerTeam = Class.forName("net.minecraft.world.scores.PlayerTeam");
                    Class<?> cTeamPkt = Class.forName("net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket");
                    Method createRemove = cTeamPkt.getDeclaredMethod("createRemovePacket", cPlayerTeam);
                    createRemove.setAccessible(true);
                    Object pkt = createRemove.invoke(null, team);
                    for (Player viewer : Bukkit.getOnlinePlayers()) {
                        try { FoliaScoreboard.sendPacketToPlayer(viewer, pkt); } catch (Throwable ignored) {}
                    }
                } catch (Throwable ignored) {}
            }
            return;
        }

        for (Player online : Bukkit.getOnlinePlayers()) {
            try {
                Scoreboard sb = online.getScoreboard();
                if (sb == null) continue;

                org.bukkit.scoreboard.Team team = sb.getTeam(teamName);
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

        // Copy all values from the new config into this.config
        for (String key : newConfig.getKeys(true)) {
            this.config.set(key, newConfig.get(key));
        }
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