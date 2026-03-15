package fr.elias.oreoEssentials.modules.webpanel;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.currency.Currency;
import fr.elias.oreoEssentials.modules.currency.CurrencyService;
import fr.elias.oreoEssentials.modules.homes.home.HomeService;
import fr.elias.oreoEssentials.modules.playtime.PlaytimeTracker;
import fr.elias.oreoEssentials.modules.shop.ShopModule;
import fr.elias.oreoEssentials.modules.shop.models.ShopItem;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Syncs player data to the web panel on join, quit, and periodically.
 * All HTTP calls happen off the main thread.
 */
public class WebPanelSyncService implements Listener {

    private final OreoEssentials plugin;
    private final WebPanelClient client;
    private WebPanelRabbitPublisher rabbitPublisher;
    private BukkitTask periodicTask;
    // Debounce: UUID → scheduled task ID, prevents spamming on rapid inventory changes
    private final Map<UUID, Integer> debounce = new ConcurrentHashMap<>();
    private static final long DEBOUNCE_TICKS = 20L; // 1 second

    public WebPanelSyncService(OreoEssentials plugin, WebPanelClient client) {
        this.plugin = plugin;
        this.client = client;
    }

    public void start() {
        // Connect to RabbitMQ if configured (uses the same rabbitmq.uri as cross-server features)
        WebPanelConfig cfg = new WebPanelConfig(plugin);
        if (cfg.isAmqpEnabled()) {
            try {
                rabbitPublisher = new WebPanelRabbitPublisher(cfg.getAmqpUri(), plugin.getLogger());
                plugin.getLogger().info("[WebPanel] Using RabbitMQ for live player sync.");
                // Subscribe to action messages (SELL / DELETE) sent from the web panel
                rabbitPublisher.startActionConsumer(action -> {
                    try {
                        String uuidStr = action.get("playerUuid").getAsString();
                        String type    = action.get("type").getAsString();
                        String mat     = action.get("material").getAsString();
                        int    amount  = action.get("amount").getAsInt();
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            Player target = Bukkit.getPlayer(UUID.fromString(uuidStr));
                            if (target != null && target.isOnline()) {
                                processWebAction(target, type, mat, amount);
                            }
                        });
                    } catch (Exception e) {
                        plugin.getLogger().warning("[WebPanel] Bad action message: " + e.getMessage());
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().warning("[WebPanel] RabbitMQ connect failed — falling back to HTTP: " + e.getMessage());
                rabbitPublisher = null;
            }
        }

        Bukkit.getPluginManager().registerEvents(this, plugin);
        // Sync all online players every 30 seconds — catches external changes like /give
        periodicTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                syncPlayer(p, true);
            }
        }, 600L, 600L);

        // Poll for pending web-panel actions only when RabbitMQ is NOT available.
        // When AMQP is active, actions are delivered instantly via RabbitMQ — polling
        // the DB would cause double-execution. The onJoin handler covers offline players.
        if (rabbitPublisher == null) {
            Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::pollAndProcessActions, 100L, 100L);
        }
    }

    public void stop() {
        if (periodicTask != null) {
            periodicTask.cancel();
            periodicTask = null;
        }
        if (rabbitPublisher != null) {
            rabbitPublisher.close();
            rabbitPublisher = null;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        // Delay 2 s so economy/homes are fully loaded
        Bukkit.getScheduler().runTaskLater(plugin, () -> syncPlayer(e.getPlayer(), true), 40L);
        // Delay 3 s before polling — ensures inventory + economy are fully loaded by all plugins
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            JsonObject response = client.pollPendingActions();
            if (response == null || !response.has("actions")) return;
            JsonArray actions = response.getAsJsonArray("actions");
            if (actions == null || actions.size() == 0) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (var elem : actions) {
                    try {
                        JsonObject action = elem.getAsJsonObject();
                        String uuidStr = action.get("playerUuid").getAsString();
                        String type    = action.get("type").getAsString();
                        String mat     = action.get("material").getAsString();
                        int    amount  = action.get("amount").getAsInt();
                        Player target  = Bukkit.getPlayer(UUID.fromString(uuidStr));
                        if (target == null || !target.isOnline()) continue;
                        processWebAction(target, type, mat, amount);
                    } catch (Exception ex) {
                        plugin.getLogger().warning("[WebPanel] Failed to process join action: " + ex.getMessage());
                    }
                }
            });
        }, 60L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        debounce.remove(e.getPlayer().getUniqueId());
        syncPlayer(e.getPlayer(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) { debouncedSync(e.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent e) { debouncedSync(e.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof org.bukkit.entity.Player p) debouncedSync(p);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) { debouncedSync(e.getPlayer()); }

    /** Catches inventory changes from chests, shops, crafting, /give, etc. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent e) {
        if (e.getPlayer() instanceof Player p) debouncedSync(p);
    }

    /** Cancels any pending sync for this player and schedules a new one after 1 second. */
    private void debouncedSync(org.bukkit.entity.Player player) {
        UUID uuid = player.getUniqueId();
        Integer prev = debounce.remove(uuid);
        if (prev != null) Bukkit.getScheduler().cancelTask(prev);
        int taskId = Bukkit.getScheduler().runTaskLater(plugin,
                () -> { debounce.remove(uuid); syncPlayer(player, true); },
                DEBOUNCE_TICKS).getTaskId();
        debounce.put(uuid, taskId);
    }

    // ─── Core sync ────────────────────────────────────────────────────────────

    private void syncPlayer(Player player, boolean online) {
        // Gather everything that must be read on the main thread
        final UUID   uuid       = player.getUniqueId();
        final String name       = player.getName();
        final double balance    = vaultBalance(player);
        final long   playtime   = playtime(uuid);
        final int    kills      = stat(player, Statistic.PLAYER_KILLS);
        final int    deaths     = stat(player, Statistic.DEATHS);
        final int    joinCount  = stat(player, Statistic.LEAVE_GAME);
        final int    homes      = homesCount(uuid);
        final String inv        = serializeItems(player.getInventory().getContents());
        final String armor      = serializeItems(player.getInventory().getArmorContents());
        final String shopPrices = serializeShopPrices(player.getInventory().getContents());
        final String lastSeen   = Instant.now().toString();

        CurrencyService cs = plugin.getCurrencyService();
        List<Currency> currencies = cs != null ? cs.getAllCurrencies() : Collections.emptyList();

        if (currencies.isEmpty()) {
            sendAsync(uuid, name, buildJson(uuid, name, online, balance, playtime,
                    kills, deaths, joinCount, homes, inv, armor, shopPrices, lastSeen, "{}"));
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
                    kills, deaths, joinCount, homes, inv, armor, shopPrices, lastSeen, currenciesJson));
        });
    }

    private void sendAsync(UUID uuid, String name, String dataJson) {
        if (rabbitPublisher != null) {
            // AMQP path: publish to RabbitMQ — backend consumes and pushes via WebSocket
            final WebPanelRabbitPublisher pub = rabbitPublisher;
            Bukkit.getScheduler().runTaskAsynchronously(plugin,
                    () -> pub.publish(uuid, name, dataJson));
        } else {
            // HTTP fallback: direct REST POST to backend
            Bukkit.getScheduler().runTaskAsynchronously(plugin,
                    () -> client.syncPlayer(uuid, name, dataJson));
        }
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
                              int homes, String inv, String armor, String shopPrices,
                              String lastSeen, String currencies) {
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
                "\"shopPrices\":" + shopPrices + "," +
                "\"currencies\":" + currencies + "," +
                "\"lastSeen\":\"" + lastSeen + "\"" +
                "}";
    }

    /** Builds a JSON map of { MATERIAL_NAME: pricePerItem } for all sellable items in the inventory. */
    private String serializeShopPrices(ItemStack[] items) {
        ShopModule sm = plugin.getShopModule();
        if (sm == null || !sm.isEnabled()) return "{}";

        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        Set<String> seen = new HashSet<>();

        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) continue;
            String mat = item.getType().name();
            if (seen.contains(mat)) continue;
            seen.add(mat);

            ShopItem si = sm.getShopManager().findBestSellItem(item);
            if (si != null && si.canSell()) {
                double pricePerItem = si.getSellPrice() / Math.max(1, si.getAmount());
                if (!first) sb.append(",");
                sb.append("\"").append(mat).append("\":").append(pricePerItem);
                first = false;
            }
        }
        return sb.append("}").toString();
    }

    // ─── Web action polling ───────────────────────────────────────────────────

    /** Runs async every 5 s — polls the web panel for SELL / DELETE actions. */
    private void pollAndProcessActions() {
        JsonObject response = client.pollPendingActions();
        if (response == null || !response.has("actions")) return;

        JsonArray actions = response.getAsJsonArray("actions");
        if (actions == null || actions.size() == 0) return;

        // Process each action on the Bukkit main thread (required for inventory changes)
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (var elem : actions) {
                try {
                    JsonObject action = elem.getAsJsonObject();
                    String uuidStr = action.get("playerUuid").getAsString();
                    String type    = action.get("type").getAsString();
                    String mat     = action.get("material").getAsString();
                    int    amount  = action.get("amount").getAsInt();

                    Player player = Bukkit.getPlayer(UUID.fromString(uuidStr));
                    if (player == null || !player.isOnline()) continue;

                    processWebAction(player, type, mat, amount);
                } catch (Exception e) {
                    plugin.getLogger().warning("[WebPanel] Failed to process action: " + e.getMessage());
                }
            }
        });
    }

    private void processWebAction(Player player, String type, String materialName, int amount) {
        Material mat;
        try {
            mat = Material.valueOf(materialName);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("[WebPanel] Unknown material in web action: " + materialName);
            return;
        }

        if ("DELETE".equals(type)) {
            int remaining = amount;
            ItemStack[] contents = player.getInventory().getContents();
            for (int i = 0; i < contents.length && remaining > 0; i++) {
                ItemStack slot = contents[i];
                if (slot == null || slot.getType() != mat) continue;
                int take = Math.min(remaining, slot.getAmount());
                if (take >= slot.getAmount()) {
                    player.getInventory().setItem(i, null);
                } else {
                    slot.setAmount(slot.getAmount() - take);
                }
                remaining -= take;
            }
            player.updateInventory();
            // 1-tick delay ensures NMS commits the slot changes before we read them back
            Bukkit.getScheduler().runTaskLater(plugin, () -> syncPlayer(player, true), 1L);

        } else if ("SELL".equals(type)) {
            ShopModule shopModule = plugin.getShopModule();
            if (shopModule == null || !shopModule.isEnabled()) return;

            ShopItem shopItem = shopModule.getShopManager().findBestSellItem(new ItemStack(mat, 1));
            if (shopItem == null || !shopItem.canSell()) return;

            // Count how many of this material the player actually has (by type only, ignoring meta)
            int available = 0;
            for (ItemStack slot : player.getInventory().getContents()) {
                if (slot != null && slot.getType() == mat) available += slot.getAmount();
            }
            int toSell = Math.min(amount, available);
            if (toSell <= 0) return;

            // Remove items from inventory by material type (use setItem to properly persist)
            int remaining = toSell;
            ItemStack[] sellContents = player.getInventory().getContents();
            for (int i = 0; i < sellContents.length && remaining > 0; i++) {
                ItemStack slot = sellContents[i];
                if (slot == null || slot.getType() != mat) continue;
                int take = Math.min(remaining, slot.getAmount());
                if (take >= slot.getAmount()) {
                    player.getInventory().setItem(i, null);
                } else {
                    slot.setAmount(slot.getAmount() - take);
                }
                remaining -= take;
            }
            player.updateInventory();

            // Pay the player using the shop's sell price per item
            double pricePerItem = shopItem.getSellPrice() / Math.max(1, shopItem.getAmount());
            double total = pricePerItem * toSell;

            fr.elias.oreoEssentials.modules.shop.models.Shop shop =
                    shopModule.getShopManager().getShop(shopItem.getShopId());
            String currencyId = shop != null ? shop.getCurrencyId() : null;
            CurrencyService cs = (currencyId != null) ? plugin.getCurrencyService() : null;

            if (cs != null) {
                cs.deposit(player.getUniqueId(), currencyId, total).join();
            } else {
                shopModule.getEconomy().deposit(player, total);
            }

            shopModule.getTransactionLogger().logTransaction(
                    player.getName(), "SOLD", toSell, mat.name(), total,
                    cs != null ? currencyId : shopModule.getEconomy().getEconomyName());

            // 1-tick delay ensures NMS commits the slot changes before we read them back
            Bukkit.getScheduler().runTaskLater(plugin, () -> syncPlayer(player, true), 1L);
        }
    }
}
