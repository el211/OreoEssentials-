package fr.elias.oreoEssentials.services;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.vanish.rabbit.VanishSyncPacket;
import fr.elias.oreoEssentials.services.vanish.VanishStateStorage;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
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

    /** Returns a snapshot of the vanished UUID set — for diagnostic/admin use only. */
    public Set<UUID> getVanishedSnapshot() {
        return java.util.Collections.unmodifiableSet(vanished);
    }

    /**
     * Wipe ALL persisted vanish state from storage and clear in-memory set.
     * Also un-vanishes every currently online player.
     * Used by /vanish clearall to recover from mass-contamination of vanish state.
     */
    public void clearAllPersisted() {
        // Un-vanish everyone currently in-memory
        for (UUID id : new java.util.HashSet<>(vanished)) {
            Player online = Bukkit.getPlayer(id);
            if (online != null && online.isOnline()) {
                show(online);
            }
        }
        vanished.clear();
        try {
            storage.clearAll();
        } catch (Exception e) {
            plugin.getLogger().warning("[VANISH] Failed to clearAll persisted state: " + e.getMessage());
        }
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

        if (plugin.getConfig().getBoolean("debug", false)) {
            if (persisted) {
                plugin.getLogger().info(
                    "[VANISH-DEBUG] Player " + joiner.getName() + " joined with persisted vanish=TRUE."
                    + " To fix one player: /vanish off " + joiner.getName()
                    + "  |  To fix everyone at once: /vanish clearall");
            } else {
                plugin.getLogger().info(
                    "[VANISH-DEBUG] Player " + joiner.getName() + " joined with persisted vanish=false (normal).");
            }
        }

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
        // V-4: Always remove UUID from in-memory vanished set on quit.
        // We deliberately do NOT re-persist vanish=true here — vanish state is already
        // written to storage the moment setVanished() is called. Re-persisting on quit
        // caused all players to accumulate vanish=true across restarts (especially with
        // shared MongoDB storage on a network), making mobs unable to target anyone.
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

        // V-6: If the player is currently online on THIS server and the remote packet says
        // "vanished=true", only honour it when our local persisted state also says vanished.
        // This prevents a stale RabbitMQ packet (sent before an un-vanish was ACKed) from
        // re-adding an online player to the vanished set and silently blocking mob targeting.
        if (vanish && online != null && online.isOnline()) {
            boolean localState = loadPersistedState(playerId);
            if (!localState) return; // local authority wins — player is NOT vanished here
        }

        // V-5: Apply cross-server vanish state in memory only — do NOT persist to local YAML
        // (vanish state is authoritative per-server; persisting would cause cross-server desync)
        applyStateLocal(playerId, vanish, online);
    }

    private boolean applyStateLocal(UUID playerId, boolean vanish, Player onlinePlayer) {
        boolean changed = vanish ? vanished.add(playerId) : vanished.remove(playerId);
        // Only call hide/show when the state actually changed — avoids fake join/quit messages
        // and unnecessary showPlayer() calls on every join for non-vanished players.
        if (changed && onlinePlayer != null && onlinePlayer.isOnline()) {
            if (vanish) {
                hide(onlinePlayer);
            } else {
                show(onlinePlayer);
            }
        }
        // Refresh custom nametag visibility immediately — bypass the periodic sweep
        // so the nametag appears/disappears in the same tick as the vanish toggle.
        fr.elias.oreoEssentials.modules.nametag.PlayerNametagManager nm = plugin.getNametagManager();
        if (nm != null) {
            if (onlinePlayer != null && onlinePlayer.isOnline()) {
                nm.refreshOwnerVisibility(onlinePlayer);
            } else {
                nm.markOwnerDirty(playerId);
            }
        }
        return changed;
    }

    private void hide(Player p) {
        String fakeQuit = Lang.get(
                "moderation.vanish.fake-quit",
                "<yellow>%player% left the game</yellow>");
        boolean sendQuit = !fakeQuit.isBlank();
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(p)) continue;
            if (!other.hasPermission("oreo.vanish.see")) {
                // Remove from world view AND tab list
                other.hidePlayer(plugin, p);
                // Fake quit message so non-admins think the player disconnected
                if (sendQuit) {
                    Lang.send(other, "moderation.vanish.fake-quit",
                            "<yellow>%player% left the game</yellow>",
                            Map.of("player", p.getName()));
                }
            }
        }
    }

    private void show(Player p) {
        String fakeJoin = Lang.get(
                "moderation.vanish.fake-join",
                "<yellow>%player% joined the game</yellow>");
        boolean sendJoin = !fakeJoin.isBlank();
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(p)) continue;
            other.showPlayer(plugin, p);
            // Fake join message only for non-admins — admins already knew the player was there
            if (!other.hasPermission("oreo.vanish.see") && sendJoin) {
                Lang.send(other, "moderation.vanish.fake-join",
                        "<yellow>%player% joined the game</yellow>",
                        Map.of("player", p.getName()));
            }
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
