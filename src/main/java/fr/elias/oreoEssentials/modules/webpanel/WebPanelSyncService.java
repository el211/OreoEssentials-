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

    // Dedup: tracks actions recently delivered via RabbitMQ to prevent the HTTP fallback
    // poll from executing the same action a second time for the same online player.
    // Key = "playerUuid|type|material|amount", value = expiry timestamp (ms).
    private final ConcurrentHashMap<String, Long> actionDedup = new ConcurrentHashMap<>();
    private static final long DEDUP_TTL_MS = 15_000; // 15 seconds — longer than the 5s poll interval

    public WebPanelSyncService(OreoEssentials plugin, WebPanelClient client) {
        this.plugin = plugin;
        this.client = client;
    }

    public void start() {
        // Connect to RabbitMQ if configured (uses the same rabbitmq.uri as cross-server features)d
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
                        String mat     = action.has("material") ? action.get("material").getAsString() : null;
                        int    amount  = action.has("amount")   ? action.get("amount").getAsInt()   : 0;

                        if ("DELIVER_ITEM".equals(type)) {
                            // Instant delivery attempt for the recipient (if currently online)
                            String senderName = action.has("senderName") ? action.get("senderName").getAsString() : "Someone";
                            long   deliveryId = action.has("deliveryId") ? action.get("deliveryId").getAsLong()   : -1L;
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                Player recipient = Bukkit.getPlayer(UUID.fromString(uuidStr));
                                if (recipient != null && recipient.isOnline()) {
                                    deliverItem(recipient, mat, amount, senderName, deliveryId);
                                }
                                // If offline, the onJoin HTTP poll will pick it up next login
                            });
                        } else {
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                Player target = Bukkit.getPlayer(UUID.fromString(uuidStr));
                                if (target != null && target.isOnline()) {
                                    processWebAction(target, type, mat, amount);
                                    // Mark executed so the HTTP fallback poll doesn't double-execute
                                    markExecuted(uuidStr, type, mat, amount);
                                }
                                // If offline: don't mark executed — the DB entry stays unprocessed
                                // and will be delivered by the periodic poll once the player comes online.
                            });
                        }
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

        // Sync all active market orders every 30 seconds (same period as player sync)
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::syncAllOrders, 200L, 600L);

        // Always poll HTTP for pending actions as a safety net.
        // RabbitMQ is the fast path (instant delivery), but if the player is already
        // online when the action is submitted and AMQP delivery misses for any reason,
        // the periodic poll ensures the action is never silently lost.
        // Double-execution of DELETE is safe (no-op when items already removed).
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::pollAndProcessActions, 100L, 100L);
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
        Player joined = e.getPlayer();
        // Delay 2 s so economy/homes are fully loaded
        Bukkit.getScheduler().runTaskLater(plugin, () -> syncPlayer(joined, true), 40L);
        // Delay 3 s before polling — ensures inventory + economy are fully loaded by all plugins
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            // 1. Poll pending actions (SELL / DELETE) for all online players
            // Include the joining player in the UUID set so their queued actions are delivered now.
            java.util.Set<String> onlineUuids = Bukkit.getOnlinePlayers().stream()
                    .map(p -> p.getUniqueId().toString())
                    .collect(java.util.stream.Collectors.toCollection(java.util.HashSet::new));
            onlineUuids.add(joined.getUniqueId().toString());

            JsonObject response = client.pollPendingActions(onlineUuids);
            if (response != null && response.has("actions")) {
                JsonArray actions = response.getAsJsonArray("actions");
                if (actions != null && actions.size() > 0) {
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
                }
            }

            // 2. Poll pending item deliveries for the joining player
            String uuidStr = joined.getUniqueId().toString();
            JsonObject deliveries = client.pollPendingDeliveries(uuidStr);
            if (deliveries != null && deliveries.has("deliveries")) {
                JsonArray items = deliveries.getAsJsonArray("deliveries");
                if (items != null && items.size() > 0) {
                    java.util.List<Long> confirmedIds = new java.util.ArrayList<>();
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        for (var elem : items) {
                            try {
                                JsonObject d = elem.getAsJsonObject();
                                String mat      = d.get("material").getAsString();
                                int    amount   = d.get("amount").getAsInt();
                                String sender   = d.has("senderName") && !d.get("senderName").isJsonNull()
                                                  ? d.get("senderName").getAsString() : "Someone";
                                long   id       = d.get("id").getAsLong();
                                deliverItem(joined, mat, amount, sender, id);
                                confirmedIds.add(id);
                            } catch (Exception ex) {
                                plugin.getLogger().warning("[WebPanel] Failed to deliver item on join: " + ex.getMessage());
                            }
                        }
                        if (!confirmedIds.isEmpty()) {
                            Bukkit.getScheduler().runTaskAsynchronously(plugin,
                                    () -> client.confirmDeliveries(confirmedIds));
                        }
                    });
                }
            }
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
        // Capture IP on the main thread (getAddress() must be called here)
        final String ip;
        try {
            ip = (player.getAddress() != null && player.getAddress().getAddress() != null)
                    ? player.getAddress().getAddress().getHostAddress() : "";
        } catch (Exception e) {
            // fallthrough to empty
            final String ipFallback = "";
            // We need effectively-final, so re-use separate path below
            CurrencyService cs2 = plugin.getCurrencyService();
            List<Currency> currencies2 = cs2 != null ? cs2.getAllCurrencies() : Collections.emptyList();
            final String orders2 = serializeOrders(uuid);
            if (currencies2.isEmpty()) {
                sendAsync(uuid, name, buildJson(uuid, name, online, balance, playtime,
                        kills, deaths, joinCount, homes, inv, armor, shopPrices, lastSeen, "{}", ipFallback, orders2));
                return;
            }
            List<CompletableFuture<Void>> futures2 = new ArrayList<>();
            Map<String, Double> balances2 = new LinkedHashMap<>();
            for (Currency c : currencies2) {
                futures2.add(cs2.getBalance(uuid, c.getId())
                        .thenAccept(b -> balances2.put(c.getId(), b)));
            }
            CompletableFuture.allOf(futures2.toArray(new CompletableFuture[0])).thenRun(() -> {
                String currenciesJson2 = buildCurrenciesJson(currencies2, balances2);
                sendAsync(uuid, name, buildJson(uuid, name, online, balance, playtime,
                        kills, deaths, joinCount, homes, inv, armor, shopPrices, lastSeen, currenciesJson2, ipFallback, orders2));
            });
            return;
        }
        final String orders = serializeOrders(uuid);

        CurrencyService cs = plugin.getCurrencyService();
        List<Currency> currencies = cs != null ? cs.getAllCurrencies() : Collections.emptyList();

        if (currencies.isEmpty()) {
            sendAsync(uuid, name, buildJson(uuid, name, online, balance, playtime,
                    kills, deaths, joinCount, homes, inv, armor, shopPrices, lastSeen, "{}", ip, orders));
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
                    kills, deaths, joinCount, homes, inv, armor, shopPrices, lastSeen, currenciesJson, ip, orders));
        });
    }

    /**
     * Serializes the player's active orders to a JSON array string.
     * Calls the OrdersModule synchronously (in-memory cache, no DB hit).
     * Returns "[]" if the module is unavailable or throws.
     */
    private String serializeOrders(UUID uuid) {
        try {
            fr.elias.oreoEssentials.modules.orders.OrdersModule om = plugin.getOrdersModule();
            if (om == null || !om.enabled()) return "[]";
            java.util.List<fr.elias.oreoEssentials.modules.orders.model.Order> orders =
                    om.getService().getActiveOrdersByPlayer(uuid);
            if (orders == null || orders.isEmpty()) return "[]";
            return serializeOrderList(orders);
        } catch (Exception e) {
            plugin.getLogger().warning("[WebPanel] serializeOrders failed: " + e.getMessage());
            return "[]";
        }
    }

    /** Syncs ALL active market orders to the backend. Called periodically off main thread. */
    private void syncAllOrders() {
        try {
            fr.elias.oreoEssentials.modules.orders.OrdersModule om = plugin.getOrdersModule();
            if (om == null || !om.enabled()) return;
            java.util.List<fr.elias.oreoEssentials.modules.orders.model.Order> orders =
                    om.getService().getActiveOrders();
            if (orders == null) return;
            client.syncMarketOrders(serializeOrderList(orders));
        } catch (Exception e) {
            plugin.getLogger().warning("[WebPanel] syncAllOrders failed: " + e.getMessage());
        }
    }

    private String serializeOrderList(java.util.List<fr.elias.oreoEssentials.modules.orders.model.Order> orders) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (fr.elias.oreoEssentials.modules.orders.model.Order o : orders) {
            if (!first) sb.append(",");
            first = false;
            String currencyId = o.getCurrencyId() != null
                    ? "\"" + o.getCurrencyId().replace("\"", "\\\"") + "\"" : "null";
            String displayName = o.getDisplayItemName() != null
                    ? o.getDisplayItemName().replace("\\", "\\\\").replace("\"", "\\\"") : "";
            String requesterName = o.getRequesterName() != null
                    ? o.getRequesterName().replace("\\", "\\\\").replace("\"", "\\\"") : "";
            String requesterUuid = o.getRequesterUuid() != null ? o.getRequesterUuid().toString() : "";
            String material = extractMaterial(o.getItemData());
            sb.append("{")
              .append("\"id\":\"").append(o.getId()).append("\",")
              .append("\"requesterUuid\":\"").append(requesterUuid).append("\",")
              .append("\"requesterName\":\"").append(requesterName).append("\",")
              .append("\"displayName\":\"").append(displayName).append("\",")
              .append("\"material\":\"").append(material).append("\",")
              .append("\"totalQty\":").append(o.getTotalQty()).append(",")
              .append("\"remainingQty\":").append(o.getRemainingQty()).append(",")
              .append("\"unitPrice\":").append(o.getUnitPrice()).append(",")
              .append("\"escrowTotal\":").append(o.getEscrowTotal()).append(",")
              .append("\"escrowRemaining\":").append(o.getEscrowRemaining()).append(",")
              .append("\"currencyId\":").append(currencyId).append(",")
              .append("\"status\":\"").append(o.getStatus().name()).append("\",")
              .append("\"createdAt\":").append(o.getCreatedAt())
              .append("}");
        }
        return sb.append("]").toString();
    }

    /** Deserializes a base64 ItemStack to get the Minecraft material name. Returns "" on failure. */
    private String extractMaterial(String itemData) {
        if (itemData == null || itemData.isEmpty()) return "";
        try {
            byte[] bytes = java.util.Base64.getDecoder().decode(itemData);
            try (org.bukkit.util.io.BukkitObjectInputStream in =
                         new org.bukkit.util.io.BukkitObjectInputStream(
                                 new java.io.ByteArrayInputStream(bytes))) {
                org.bukkit.inventory.ItemStack item = (org.bukkit.inventory.ItemStack) in.readObject();
                return item != null ? item.getType().name() : "";
            }
        } catch (Exception e) {
            return "";
        }
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
                              String lastSeen, String currencies, String ip, String orders) {
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
                "\"ip\":\"" + (ip != null ? ip.replace("\"", "\\\"") : "") + "\"," +
                "\"orders\":" + (orders != null ? orders : "[]") + "," +
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

    // ─── Dedup helpers ────────────────────────────────────────────────────────

    private String dedupKey(String playerUuid, String type, String mat, int amount) {
        return playerUuid + "|" + type + "|" + mat + "|" + amount;
    }

    private boolean isDuplicate(String playerUuid, String type, String mat, int amount) {
        Long expiry = actionDedup.get(dedupKey(playerUuid, type, mat, amount));
        if (expiry == null) return false;
        if (System.currentTimeMillis() > expiry) {
            actionDedup.remove(dedupKey(playerUuid, type, mat, amount));
            return false;
        }
        return true;
    }

    private void markExecuted(String playerUuid, String type, String mat, int amount) {
        actionDedup.put(dedupKey(playerUuid, type, mat, amount),
                System.currentTimeMillis() + DEDUP_TTL_MS);
    }

    // ─── Web action polling ───────────────────────────────────────────────────

    /** Runs async every 5 s — polls the web panel for SELL / DELETE actions for online players. */
    private void pollAndProcessActions() {
        // Only request actions for currently online players — server filters by this list
        // so actions for offline senders are never marked processed and silently lost.
        java.util.Set<String> onlineUuids = Bukkit.getOnlinePlayers().stream()
                .map(p -> p.getUniqueId().toString())
                .collect(java.util.stream.Collectors.toSet());

        JsonObject response = client.pollPendingActions(onlineUuids);
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

                    // Skip if already handled via RabbitMQ in the last 15 seconds
                    if (isDuplicate(uuidStr, type, mat, amount)) continue;

                    processWebAction(player, type, mat, amount);
                } catch (Exception e) {
                    plugin.getLogger().warning("[WebPanel] Failed to process action: " + e.getMessage());
                }
            }
        });
    }

    /**
     * Gives {@code amount} of {@code materialName} to {@code player} and confirms the delivery
     * to the backend so it is not re-delivered on next login.
     * Items that do not fit in the inventory drop at the player's feet.
     */
    private void deliverItem(Player player, String materialName, int amount, String senderName, long deliveryId) {
        Material mat;
        try {
            mat = Material.valueOf(materialName);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("[WebPanel] Unknown material in delivery: " + materialName);
            return;
        }

        // Give items in stacks of maxStackSize
        int maxStack = new org.bukkit.inventory.ItemStack(mat).getMaxStackSize();
        int remaining = amount;
        while (remaining > 0) {
            int give = Math.min(remaining, maxStack);
            org.bukkit.inventory.ItemStack stack = new org.bukkit.inventory.ItemStack(mat, give);
            // Add to inventory; overflow drops naturally
            player.getInventory().addItem(stack).values()
                    .forEach(drop -> player.getWorld().dropItemNaturally(player.getLocation(), drop));
            remaining -= give;
        }
        player.updateInventory();
        player.sendMessage("§a[Web Panel] §fYou received §e" + amount + "x "
                + materialName.replace("_", " ").toLowerCase() + " §ffrom §b" + senderName + "§f.");

        // Confirm to backend
        if (deliveryId > 0) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin,
                    () -> client.confirmDeliveries(java.util.List.of(deliveryId)));
        }

        // Re-sync inventory after 1 tick
        Bukkit.getScheduler().runTaskLater(plugin, () -> syncPlayer(player, true), 1L);
    }

    private void processWebAction(Player player, String type, String materialName, int amount) {
        // CANCEL_ORDER uses materialName as order ID (not a Material enum) — handle before Material.valueOf
        if ("CANCEL_ORDER".equals(type)) {
            fr.elias.oreoEssentials.modules.orders.OrdersModule om = plugin.getOrdersModule();
            if (om != null && om.enabled()) {
                om.getService().cancelOrder(player, materialName);
                Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, this::syncAllOrders, 20L);
            }
            return;
        }

        // FILL_ORDER: materialName = orderId, amount = qty to fill
        // fillOrder() does its inventory check + removal on the main thread, then
        // dispatches the DB write asynchronously — NEVER call .join() on the main thread.
        if ("FILL_ORDER".equals(type)) {
            fr.elias.oreoEssentials.modules.orders.OrdersModule om = plugin.getOrdersModule();
            if (om != null && om.enabled()) {
                om.getService().fillOrder(player, materialName, amount)
                    .thenAccept(result -> {
                        // Back onto the main thread for player messaging
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (result != null && result.isSuccess()) {
                                player.sendMessage("§a[Web Panel] §fOrder filled! You sold §e"
                                        + result.getFilledQty() + " §fitem(s) and received §a"
                                        + String.format("%.2f", result.getPaidToSeller()) + "§f.");
                                // Sync filler so the panel reflects their updated inventory/cash
                                syncPlayer(player, true);
                                // Sync requester if online so their "My Orders" reflects the updated order
                                fr.elias.oreoEssentials.modules.orders.model.Order updatedOrder = result.getUpdatedOrder();
                                if (updatedOrder != null && updatedOrder.getRequesterUuid() != null) {
                                    Player requester = Bukkit.getPlayer(updatedOrder.getRequesterUuid());
                                    if (requester != null && requester.isOnline()) {
                                        syncPlayer(requester, true);
                                    }
                                }
                                Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, this::syncAllOrders, 20L);
                            } else {
                                String reason = result != null ? result.getOutcome().name() : "ERROR";
                                player.sendMessage("§c[Web Panel] §fOrder fill failed: §e"
                                        + reason.replace("_", " ").toLowerCase());
                            }
                        });
                    })
                    .exceptionally(e -> {
                        Bukkit.getScheduler().runTask(plugin, () ->
                                player.sendMessage("§c[Web Panel] §fFill order error."));
                        plugin.getLogger().warning("[WebPanel] fillOrder error: " + e.getMessage());
                        return null;
                    });
            }
            return;
        }

        // KICK uses materialName as the kick message
        if ("KICK".equals(type)) {
            String message = materialName != null ? materialName.replace("_", " ") : "Kicked by server admin via web panel";
            player.kickPlayer("§c" + message);
            return;
        }

        // BAN uses materialName as reason, amount as duration in seconds
        if ("BAN".equals(type)) {
            String reason = materialName != null ? materialName.replace("_", " ") : "Banned via web panel";
            long durationSeconds = amount;
            java.util.Date expiry = durationSeconds > 0
                    ? java.util.Date.from(java.time.Instant.now().plusSeconds(durationSeconds))
                    : null;
            Bukkit.getBanList(org.bukkit.BanList.Type.NAME).addBan(
                    player.getName(), reason, expiry, "Web Panel");
            player.kickPlayer("§cYou have been banned.\n§fReason: §e" + reason);
            return;
        }

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

        } else if ("ADMIN_DELETE_ITEM".equals(type)) {
            // Admin-initiated removal: same logic as DELETE but invoked for any player
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
            Bukkit.getScheduler().runTaskLater(plugin, () -> syncPlayer(player, true), 1L);
        }
    }
}
