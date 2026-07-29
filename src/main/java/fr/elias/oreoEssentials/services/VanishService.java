package fr.elias.oreoEssentials.services;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.vanish.rabbit.VanishSyncPacket;
import fr.elias.oreoEssentials.services.vanish.VanishStateStorage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VanishService {

    private final OreoEssentials plugin;
    private final VanishStateStorage storage;
    private final boolean crossServerSyncEnabled;
    private final String localServerName;
    private final Set<UUID> vanished = ConcurrentHashMap.newKeySet();

    public VanishService(OreoEssentials plugin,
                         VanishStateStorage storage,
                         boolean crossServerSyncEnabled,
                         String localServerName) {
        this.plugin = plugin;
        this.storage = storage;
        this.crossServerSyncEnabled = crossServerSyncEnabled;
        this.localServerName = localServerName != null ? localServerName : "unknown";
    }

    public boolean isVanished(Player p) {
        return p != null && vanished.contains(p.getUniqueId());
    }

    public boolean isVanished(UUID playerId) {
        return playerId != null && vanished.contains(playerId);
    }

    public boolean toggle(Player p) {
        boolean next = !isVanished(p);
        setVanished(p, next);
        return next;
    }

    public boolean setVanished(Player p, boolean vanish) {
        if (p == null) return false;

        boolean changed = applyStateLocal(p.getUniqueId(), vanish, p);
        persistState(p.getUniqueId(), vanish);
        broadcastState(p.getUniqueId(), vanish);
        return changed;
    }

    public void handleJoin(Player joiner) {
        if (joiner == null) return;

        boolean persisted = loadPersistedState(joiner.getUniqueId());
        applyStateLocal(joiner.getUniqueId(), persisted, joiner);

        // V-2: Only hide vanished players from joiners who cannot see them
        if (!joiner.hasPermission("oreo.vanish.see")) {
            for (UUID id : vanished) {
                Player vanishedPlayer = Bukkit.getPlayer(id);
                if (vanishedPlayer != null && vanishedPlayer.isOnline() && !vanishedPlayer.equals(joiner)) {
                    joiner.hidePlayer(plugin, vanishedPlayer);
                }
            }
        }
    }

    public void handleQuit(Player quitter) {
        if (quitter == null) return;
        if (isVanished(quitter)) {
            persistState(quitter.getUniqueId(), true);
        }
        // V-4: Always remove UUID from in-memory vanished set on quit
        vanished.remove(quitter.getUniqueId());
    }

    public void restoreOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            handleJoin(player);
        }
    }

    public void handleRemoteState(UUID playerId, boolean vanish, String sourceServer) {
        if (playerId == null) return;
        if (sourceServer != null && sourceServer.equalsIgnoreCase(localServerName)) return;

        Player online = Bukkit.getPlayer(playerId);
        // V-5: Apply cross-server vanish state in memory only — do NOT persist to local YAML
        // (vanish state is authoritative per-server; persisting would cause cross-server desync)
        applyStateLocal(playerId, vanish, online);
    }

    private boolean applyStateLocal(UUID playerId, boolean vanish, Player onlinePlayer) {
        boolean changed = vanish ? vanished.add(playerId) : vanished.remove(playerId);
        if (onlinePlayer != null && onlinePlayer.isOnline()) {
            if (vanish) {
                hide(onlinePlayer);
            } else {
                show(onlinePlayer);
            }
        }
        // Refresh custom nametag visibility immediately
        fr.elias.oreoEssentials.modules.nametag.PlayerNametagManager nm = plugin.getNametagManager();
        if (nm != null) nm.markOwnerDirty(playerId);
        return changed;
    }

    private void hide(Player p) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(p)) continue;
            // V-2: Hide from tab list for players who cannot see vanished players
            if (!other.hasPermission("oreo.vanish.see")) {
                other.hidePlayer(plugin, p);
            }
        }
    }

    private void show(Player p) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(p)) continue;
            other.showPlayer(plugin, p);
        }
    }

    private boolean loadPersistedState(UUID playerId) {
        try {
            return storage.isVanished(playerId);
        } catch (Exception e) {
            plugin.getLogger().warning("[VANISH] Failed to load persisted state for " + playerId + ": " + e.getMessage());
            return false;
        }
    }

    private void persistState(UUID playerId, boolean vanish) {
        try {
            storage.setVanished(playerId, vanish);
        } catch (Exception e) {
            plugin.getLogger().warning("[VANISH] Failed to persist state for " + playerId + ": " + e.getMessage());
        }
    }

    private void broadcastState(UUID playerId, boolean vanish) {
        if (!crossServerSyncEnabled) return;
        if (plugin.getPacketManager() == null || !plugin.getPacketManager().isInitialized()) return;
        try {
            plugin.getPacketManager().sendPacket(new VanishSyncPacket(playerId, vanish, localServerName));
        } catch (Throwable t) {
            plugin.getLogger().warning("[VANISH] Failed to broadcast cross-server state: " + t.getMessage());
        }
    }
}
