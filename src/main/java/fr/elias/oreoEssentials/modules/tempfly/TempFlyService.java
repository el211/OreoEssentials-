package fr.elias.oreoEssentials.modules.tempfly;

import fr.elias.oreoEssentials.OreoEssentials;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TempFlyService implements Listener {

    private final OreoEssentials plugin;
    private final TempFlyConfig config;
    private final Map<UUID, TempFlySession> activeSessions = new ConcurrentHashMap<>();
    private int taskId = -1;

    public TempFlyService(OreoEssentials plugin, TempFlyConfig config) {
        this.plugin = plugin;
        this.config = config;

        Bukkit.getPluginManager().registerEvents(this, plugin);

        this.taskId = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L).getTaskId();
    }

    public void shutdown() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }

        for (TempFlySession session : activeSessions.values()) {
            Player p = Bukkit.getPlayer(session.playerId);
            if (p != null && p.isOnline()) {
                p.setAllowFlight(false);
                p.setFlying(false);
            }
        }
        activeSessions.clear();
    }

    public void enableFly(Player player) {
        if (player == null) return;

        int duration = getDurationForPlayer(player);
        if (duration <= 0) {
            player.sendMessage("§cYou don't have permission to use temporary fly.");
            return;
        }

        if (activeSessions.containsKey(player.getUniqueId())) {
            player.sendMessage("§cYou already have temporary fly active.");
            return;
        }

        TempFlySession session = new TempFlySession(player.getUniqueId(), duration);
        activeSessions.put(player.getUniqueId(), session);

        player.setAllowFlight(true);
        player.sendMessage("§aTemporary fly enabled for §e" + formatTime(duration) + "§a.");
    }

    public void disableFly(Player player) {
        if (player == null) return;

        TempFlySession session = activeSessions.remove(player.getUniqueId());
        if (session == null) {
            player.sendMessage("§cYou don't have temporary fly active.");
            return;
        }

        player.setAllowFlight(false);
        player.setFlying(false);
        player.sendMessage("§cTemporary fly disabled.");
    }

    public int getTimeRemaining(Player player) {
        if (player == null) return 0;
        TempFlySession session = activeSessions.get(player.getUniqueId());
        return session != null ? session.remainingSeconds : 0;
    }

    private void tick() {
        for (Map.Entry<UUID, TempFlySession> entry : activeSessions.entrySet()) {
            UUID playerId = entry.getKey();
            TempFlySession session = entry.getValue();

            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                activeSessions.remove(playerId);
                continue;
            }

            session.remainingSeconds--;

            if (session.remainingSeconds <= 0) {
                activeSessions.remove(playerId);
                player.setAllowFlight(false);
                player.setFlying(false);
                player.sendMessage("§cYour temporary fly time has expired.");
                continue;
            }

            int[] warnings = {60, 30, 10, 5};
            for (int warn : warnings) {
                if (session.remainingSeconds == warn) {
                    player.sendMessage("§eTemporary fly expires in §c" + formatTime(warn) + "§e.");
                    break;
                }
            }
        }
    }

    private int getDurationForPlayer(Player player) {
        String group = null;
        try {
            group = LuckPermsProvider.get().getUserManager()
                    .getUser(player.getUniqueId())
                    .getPrimaryGroup();
        } catch (Throwable ignored) {
        }

        if (config.usePermissionGroups()) {
            for (String permGroup : config.getPermissionGroups().keySet()) {
                if (player.hasPermission("oe.tempfly." + permGroup)) {
                    return config.getPermissionGroups().get(permGroup);
                }
            }
        }

        if (config.useLuckPermsGroups() && group != null) {
            Integer duration = config.getLuckPermsGroups().get(group);
            if (duration != null) {
                return duration;
            }
        }

        return 0;
    }

    private String formatTime(int seconds) {
        if (seconds < 60) {
            return seconds + "s";
        } else if (seconds < 3600) {
            int minutes = seconds / 60;
            int secs = seconds % 60;
            return secs > 0 ? minutes + "m " + secs + "s" : minutes + "m";
        } else {
            int hours = seconds / 3600;
            int minutes = (seconds % 3600) / 60;
            return minutes > 0 ? hours + "h " + minutes + "m" : hours + "h";
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("oe.tempfly.infinite")) {
            player.setAllowFlight(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        activeSessions.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        if (event.getNewGameMode() == GameMode.CREATIVE || event.getNewGameMode() == GameMode.SPECTATOR) {
            activeSessions.remove(event.getPlayer().getUniqueId());
        }
    }

    private static class TempFlySession {
        final UUID playerId;
        int remainingSeconds;

        TempFlySession(UUID playerId, int duration) {
            this.playerId = playerId;
            this.remainingSeconds = duration;
        }
    }
}