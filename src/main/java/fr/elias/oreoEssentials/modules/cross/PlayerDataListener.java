package fr.elias.oreoEssentials.modules.cross;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class PlayerDataListener implements Listener {
    private final OreoEssentials plugin;

    public PlayerDataListener(OreoEssentials plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(AsyncPlayerPreLoginEvent event) {
        UUID playerUUID = event.getUniqueId();
        String playerName = event.getName();

        CompletableFuture.supplyAsync(() -> plugin.getRedis().getBalance(playerUUID)).thenAccept(cachedBalance -> {
            if (cachedBalance != null) {
                plugin.getLogger().info("[OreoEssentials] Loaded cached balance for " + playerName + ": $" + cachedBalance);
                return;
            }
            CompletableFuture.supplyAsync(() -> plugin.getDatabase().getOrCreateBalance(playerUUID, playerName)).thenAccept(balance -> {
                plugin.getRedis().setBalance(playerUUID, balance);
                plugin.getLogger().info("[OreoEssentials] Cached balance for " + playerName + ": $" + balance);
            });
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerUUID = event.getPlayer().getUniqueId();
        double balance = plugin.getDatabase().getBalance(playerUUID);
        plugin.getRedis().setBalance(playerUUID, balance);
        plugin.getLogger().info("[OreoEssentials] Updated cached balance for " + event.getPlayer().getName());
    }
}
