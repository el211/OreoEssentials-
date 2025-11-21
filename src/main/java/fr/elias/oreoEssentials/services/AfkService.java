package fr.elias.oreoEssentials.services;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple AFK service:
 * - Tracks AFK players
 * - Adds "[AFK]" prefix ONCE in tablist
 * - Restores original tablist name when AFK is removed
 */
public class AfkService {

    // Who is AFK right now
    private final Set<UUID> afkPlayers = ConcurrentHashMap.newKeySet();

    // Original tablist names (before AFK)
    private final Map<UUID, String> originalTabNames = new ConcurrentHashMap<>();

    public boolean isAfk(Player player) {
        return afkPlayers.contains(player.getUniqueId());
    }

    /**
     * Toggle AFK state for a player.
     *
     * @return true if now AFK, false if no longer AFK.
     */
    public boolean toggleAfk(Player player) {
        boolean nowAfk = !isAfk(player);
        setAfk(player, nowAfk);
        return nowAfk;
    }

    /**
     * Explicitly set AFK state.
     */
    public void setAfk(Player player, boolean afk) {
        UUID id = player.getUniqueId();

        if (afk) {
            // First time going AFK: store original tab name if not stored yet
            originalTabNames.putIfAbsent(id, safeTabName(player));
            afkPlayers.add(id);
        } else {
            afkPlayers.remove(id);
        }

        refreshTabName(player);
    }

    /**
     * Should be called on quit to clean memory.
     */
    public void handleQuit(Player player) {
        UUID id = player.getUniqueId();
        afkPlayers.remove(id);
        originalTabNames.remove(id);
    }
    public void clearAfk(Player player) {
        // Simply mark them as not AFK and refresh tab name
        setAfk(player, false);
    }

    /**
     * Applies the correct tablist name according to AFK state.
     */
    private void refreshTabName(Player player) {
        UUID id = player.getUniqueId();
        String base = originalTabNames.getOrDefault(id, safeTabName(player));

        if (afkPlayers.contains(id)) {
            // ONE clean prefix, not stacking
            player.setPlayerListName(ChatColor.GRAY + "[AFK] " + ChatColor.RESET + base);
        } else {
            player.setPlayerListName(base);
        }
    }

    /**
     * Some plugins / forks can return null for player list name.
     */
    private String safeTabName(Player player) {
        String listName = player.getPlayerListName();
        if (listName == null || listName.isEmpty()) {
            return player.getName();
        }
        return listName;
    }
}
