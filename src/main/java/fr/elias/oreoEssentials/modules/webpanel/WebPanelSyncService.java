package fr.elias.oreoEssentials.modules.webpanel;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.currency.Currency;
import fr.elias.oreoEssentials.modules.currency.CurrencyService;
import fr.elias.oreoEssentials.modules.homes.home.HomeService;
import fr.elias.oreoEssentials.modules.playtime.PlaytimeTracker;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Syncs player data to the web panel on join, quit, and periodically.
 * All HTTP calls happen off the main thread.
 */
public class WebPanelSyncService implements Listener {

    private final OreoEssentials plugin;
    private final WebPanelClient client;
    private BukkitTask periodicTask;

    public WebPanelSyncService(OreoEssentials plugin, WebPanelClient client) {
        this.plugin = plugin;
        this.client = client;
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        // Sync all online players every 5 minutes
        periodicTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                syncPlayer(p, true);
            }
        }, 6000L, 6000L);
    }

    public void stop() {
        if (periodicTask != null) {
            periodicTask.cancel();
            periodicTask = null;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        // Delay 2 s so economy/homes are fully loaded
        Bukkit.getScheduler().runTaskLater(plugin, () -> syncPlayer(e.getPlayer(), true), 40L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        syncPlayer(e.getPlayer(), false);
    }

    // ─── Core sync ────────────────────────────────────────────────────────────

    private void syncPlayer(Player player, boolean online) {
        // Gather everything that must be read on the main thread
        final UUID   uuid      = player.getUniqueId();
        final String name      = player.getName();
        final double balance   = vaultBalance(player);
        final long   playtime  = playtime(uuid);
        final int    kills     = stat(player, Statistic.PLAYER_KILLS);
        final int    deaths    = stat(player, Statistic.DEATHS);
        final int    joinCount = stat(player, Statistic.LEAVE_GAME);
        final int    homes     = homesCount(uuid);
        final String inv       = serializeItems(player.getInventory().getContents());
        final String armor     = serializeItems(player.getInventory().getArmorContents());
        final String lastSeen  = Instant.now().toString();

        CurrencyService cs = plugin.getCurrencyService();
        List<Currency> currencies = cs != null ? cs.getAllCurrencies() : Collections.emptyList();

        if (currencies.isEmpty()) {
            sendAsync(uuid, name, buildJson(uuid, name, online, balance, playtime,
                    kills, deaths, joinCount, homes, inv, armor, lastSeen, "{}"));
            return;
        }

        // Fetch balances (may be async), then fire HTTP off main thread
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        Map<String, Double> balances = new LinkedHashMap<>();
        for (Currency c : currencies) {
            futures.add(cs.getBalance(uuid, c.getId())
                    .thenAccept(b -> balances.put(c.getId(), b)));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenRun(() -> {
            String currenciesJson = buildCurrenciesJson(currencies, balances);
            sendAsync(uuid, name, buildJson(uuid, name, online, balance, playtime,
                    kills, deaths, joinCount, homes, inv, armor, lastSeen, currenciesJson));
        });
    }

    private void sendAsync(UUID uuid, String name, String dataJson) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin,
                () -> client.syncPlayer(uuid, name, dataJson));
    }

    // ─── Data helpers ─────────────────────────────────────────────────────────

    private double vaultBalance(Player player) {
        Economy eco = plugin.getVaultEconomy();
        if (eco == null) return 0.0;
        try { return eco.getBalance(player); } catch (Exception e) { return 0.0; }
    }

    private long playtime(UUID uuid) {
        PlaytimeTracker t = plugin.getPlaytimeTracker();
        if (t == null) return 0L;
        try { return t.getSeconds(uuid); } catch (Exception e) { return 0L; }
    }

    private int stat(Player player, Statistic stat) {
        try { return player.getStatistic(stat); } catch (Exception e) { return 0; }
    }

    private int homesCount(UUID uuid) {
        HomeService hs = plugin.getHomeService();
        if (hs == null) return 0;
        try { return hs.listHomes(uuid).size(); } catch (Exception e) { return 0; }
    }

    private String serializeItems(ItemStack[] items) {
        if (items == null || items.length == 0) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.length; i++) {
            ItemStack item = items[i];
            if (item == null || item.getType().isAir()) {
                sb.append("null");
            } else {
                String mat = item.getType().name();
                String display = mat;
                ItemMeta meta = item.getItemMeta();
                if (meta != null && meta.hasDisplayName()) {
                    display = meta.getDisplayName().replace("\\", "\\\\").replace("\"", "\\\"");
                }
                sb.append("{\"material\":\"").append(mat)
                  .append("\",\"amount\":").append(item.getAmount())
                  .append(",\"displayName\":\"").append(display).append("\"}");
            }
            if (i < items.length - 1) sb.append(",");
        }
        return sb.append("]").toString();
    }

    private String buildCurrenciesJson(List<Currency> currencies, Map<String, Double> balances) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Currency c : currencies) {
            double bal = balances.getOrDefault(c.getId(), 0.0);
            if (!first) sb.append(",");
            first = false;
            String sym = c.getSymbol() != null
                    ? c.getSymbol().replace("\\", "\\\\").replace("\"", "\\\"") : "$";
            sb.append("\"").append(c.getId()).append("\":{")
              .append("\"symbol\":\"").append(sym).append("\",")
              .append("\"amount\":").append(bal).append(",")
              .append("\"formatted\":\"").append(c.format(bal)).append("\"")
              .append("}");
        }
        return sb.append("}").toString();
    }

    private String buildJson(UUID uuid, String name, boolean online, double balance,
                              long playtime, int kills, int deaths, int joinCount,
                              int homes, String inv, String armor, String lastSeen,
                              String currencies) {
        return "{" +
                "\"uuid\":\"" + uuid + "\"," +
                "\"playerName\":\"" + name.replace("\\", "\\\\").replace("\"", "\\\"") + "\"," +
                "\"online\":" + online + "," +
                "\"balance\":" + balance + "," +
                "\"playtime\":" + playtime + "," +
                "\"homesCount\":" + homes + "," +
                "\"stats\":{" +
                    "\"kills\":" + kills + "," +
                    "\"deaths\":" + deaths + "," +
                    "\"joinCount\":" + joinCount +
                "}," +
                "\"inventory\":" + inv + "," +
                "\"armor\":" + armor + "," +
                "\"currencies\":" + currencies + "," +
                "\"lastSeen\":\"" + lastSeen + "\"" +
                "}";
    }
}
