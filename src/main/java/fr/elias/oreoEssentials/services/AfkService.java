package fr.elias.oreoEssentials.services;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


public class AfkService {

    // Who is AFK right now
    private final Set<UUID> afkPlayers = ConcurrentHashMap.newKeySet();

    // Original tablist names (before AFK)
    private final Map<UUID, String> originalTabNames = new ConcurrentHashMap<>();

    public boolean isAfk(Player player) {
        return afkPlayers.contains(player.getUniqueId());
    }


    public boolean toggleAfk(Player player) {
        boolean nowAfk = !isAfk(player);
        setAfk(player, nowAfk);
        return nowAfk;
    }

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


    public void handleQuit(Player player) {
        UUID id = player.getUniqueId();
        afkPlayers.remove(id);
        originalTabNames.remove(id);
    }
    public void clearAfk(Player player) {
        // Simply mark them as not AFK and refresh tab name
        setAfk(player, false);
    }


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

    private String safeTabName(Player player) {
        String listName = player.getPlayerListName();
        if (listName == null || listName.isEmpty()) {
            return player.getName();
        }
        return listName;
    }
}
