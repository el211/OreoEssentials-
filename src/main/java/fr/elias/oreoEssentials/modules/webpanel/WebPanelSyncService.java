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
import fr.elias.oreoEssentials.util.OreScheduler;
import fr.elias.oreoEssentials.util.OreTask;

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
    private OreTask periodicTask;
    // Debounce: UUID → scheduled task, prevents spamming on rapid inventory changes
    private final Map<UUID, OreTask> debounce = new ConcurrentHashMap<>();
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
                        String type = action.has("type") ? action.get("type").getAsString() : "";

                        // ── LuckPerms group permission update (no playerUuid) ──────────────
                        if ("luckperms_group_permission_update".equals(type)) {
                            applyLuckPermsUpdate(action);
                            return;
                        }

                        // ── Player-targeted actions ────────────────────────────────────────
                        String uuidStr = action.get("playerUuid").getAsString();
                        String mat     = action.has("material") ? action.get("material").getAsString() : null;
                        int    amount  = action.has("amount")   ? action.get("amount").getAsInt()   : 0;

                        if ("DELIVER_ITEM".equals(type)) {
                            // Instant delivery attempt for the recipient (if currently online)
                            String senderName = action.has("senderName") ? action.get("senderName").getAsString() : "Someone";
                            long   deliveryId = action.has("deliveryId") ? action.get("deliveryId").getAsLong()   : -1L;
                            Player recipient = Bukkit.getPlayer(UUID.fromString(uuidStr));
                            if (recipient != null && recipient.isOnline()) {
                                OreScheduler.runForEntity(plugin, recipient, () ->
                                        deliverItem(recipient, mat, amount, senderName, deliveryId));
                            }
                            // If offline, the onJoin HTTP poll will pick it up next login
                        } else {
                            Player target = Bukkit.getPlayer(UUID.fromString(uuidStr));
                            if (target != null && target.isOnline()) {
                                // Must run on the entity's region thread (Folia-safe) for inventory ops
                                OreScheduler.runForEntity(plugin, target, () -> {
                                    boolean executed = processWebAction(target, type, mat, amount);
                                    // Only mark executed if the action actually ran successfully.
                                    // If it failed (e.g. unknown material), leave the DB record so
                                    // the HTTP poll can log and drain it separately.
                                    if (executed) markExecuted(uuidStr, type, mat, amount);
                                });
                            }
                            // If offline: don't mark executed — the DB entry stays unprocessed
                            // and will be delivered by the periodic poll once the player comes online.
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

        // Push the current LuckPerms group state to the backend so the panel starts with fresh data.
        // Runs async so startup is not delayed. Delayed 5 s to let all plugins finish enabling.
        OreScheduler.runAsyncLater(plugin, this::syncLuckPermsGroupsToBackend, 100L);

        Bukkit.getPluginManager().registerEvents(this, plugin);
        // Sync all online players every 30 seconds — catches external changes like /give
        periodicTask = OreScheduler.runTimer(plugin, () -> {
            java.util.List<Player> online = new java.util.ArrayList<>(Bukkit.getOnlinePlayers());
            for (Player p : online) {
                syncPlayer(p, true);
            }
            // Push heartbeat so the backend can mark anyone NOT in this list as offline.
            // Fixes stale flags from server crashes or failed quit syncs.
            java.util.List<String> onlineUuids = online.stream()
                    .map(p -> p.getUniqueId().toString())
                    .collect(java.util.stream.Collectors.toList());
            OreScheduler.runAsync(plugin, () -> client.pushHeartbeat(onlineUuids));
        }, 600L, 600L);

        // Sync all active market orders every 30 seconds (same period as player sync)
        OreScheduler.runAsyncTimer(plugin, this::syncAllOrders, 200L, 600L);

        // Always poll HTTP for pending actions as a safety net.
        // RabbitMQ is the fast path (instant delivery), but if the player is already
        // online when the action is submitted and AMQP delivery misses for any reason,
        // the periodic poll ensures the action is never silently lost.
        // Double-execution of DELETE is safe (no-op when items already removed).
        OreScheduler.runAsyncTimer(plugin, this::pollAndProcessActions, 100L, 100L);
    }

    /**
     * Publishes an AFK enter/exit event to the web panel backend.
     * Uses RabbitMQ if available, otherwise falls back to the REST endpoint.
     * Safe to call from any thread.
     */
    public void publishAfkStatus(UUID uuid, String playerName, String serverName,
                                  String world, double x, double y, double z,
                                  long afkSinceMs, boolean entering) {
        final WebPanelRabbitPublisher pub = rabbitPublisher;
        if (pub != null) {
            OreScheduler.runAsync(plugin, () ->
                    pub.publishAfkStatus(uuid, playerName, serverName, world, x, y, z, afkSinceMs, entering));
        } else {
            OreScheduler.runAsync(plugin, () ->
                    client.pushAfkStatus(uuid, playerName, serverName, world, x, y, z, afkSinceMs, entering));
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
        Player joined = e.getPlayer();
        // Delay 2 s so economy/homes are fully loaded
        OreScheduler.runLater(plugin, () -> syncPlayer(joined, true), 40L);
        // Delay 3 s before polling — ensures inventory + economy are fully loaded by all plugins
        OreScheduler.runAsyncLater(plugin, () -> {
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
                    OreScheduler.run(plugin, () -> {
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
                    OreScheduler.run(plugin, () -> {
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
                            OreScheduler.runAsync(plugin,
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
        OreTask prev = debounce.remove(uuid);
        if (prev != null) prev.cancel();
        OreTask task = OreScheduler.runLater(plugin,
                () -> { debounce.remove(uuid); syncPlayer(player, true); },
                DEBOUNCE_TICKS);
        debounce.put(uuid, task);
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
            OreScheduler.runAsync(plugin,
                    () -> pub.publish(uuid, name, dataJson));
        } else {
            // HTTP fallback: direct REST POST to backend
            OreScheduler.runAsync(plugin,
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
        OreScheduler.run(plugin, () -> {
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
            OreScheduler.runAsync(plugin,
                    () -> client.confirmDeliveries(java.util.List.of(deliveryId)));
        }

        // Re-sync inventory after 1 tick
        OreScheduler.runLaterForEntity(plugin, player, () -> syncPlayer(player, true), 1L);
    }

    private boolean processWebAction(Player player, String type, String materialName, int amount) {
        // CANCEL_ORDER uses materialName as order ID (not a Material enum) — handle before Material.valueOf
        if ("CANCEL_ORDER".equals(type)) {
            fr.elias.oreoEssentials.modules.orders.OrdersModule om = plugin.getOrdersModule();
            if (om != null && om.enabled()) {
                om.getService().cancelOrder(player, materialName);
                OreScheduler.runAsyncLater(plugin, this::syncAllOrders, 20L);
            }
            return true;
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
                        OreScheduler.runForEntity(plugin, player, () -> {
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
                                OreScheduler.runAsyncLater(plugin, this::syncAllOrders, 20L);
                            } else {
                                String reason = result != null ? result.getOutcome().name() : "ERROR";
                                player.sendMessage("§c[Web Panel] §fOrder fill failed: §e"
                                        + reason.replace("_", " ").toLowerCase());
                            }
                        });
                    })
                    .exceptionally(e -> {
                        OreScheduler.runForEntity(plugin, player, () ->
                                player.sendMessage("§c[Web Panel] §fFill order error."));
                        plugin.getLogger().warning("[WebPanel] fillOrder error: " + e.getMessage());
                        return null;
                    });
            }
            return true;
        }

        // KICK uses materialName as the kick message
        if ("KICK".equals(type)) {
            String message = materialName != null ? materialName.replace("_", " ") : "Kicked by server admin via web panel";
            player.kickPlayer("§c" + message);
            return true;
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
            return true;
        }

        Material mat;
        try {
            mat = Material.valueOf(materialName);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("[WebPanel] Unknown material in web action: " + materialName);
            return false;
        }

        if ("DELETE".equals(type)) {
            int totalBefore = 0;
            for (ItemStack s : player.getInventory().getContents()) {
                if (s != null && s.getType() == mat) totalBefore += s.getAmount();
            }
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
            OreScheduler.runLaterForEntity(plugin, player, () -> syncPlayer(player, true), 1L);

        } else if ("SELL".equals(type)) {
            ShopModule shopModule = plugin.getShopModule();
            if (shopModule == null || !shopModule.isEnabled()) return false;

            ShopItem shopItem = shopModule.getShopManager().findBestSellItem(new ItemStack(mat, 1));
            if (shopItem == null || !shopItem.canSell()) return false;

            // Count how many of this material the player actually has (by type only, ignoring meta)
            int available = 0;
            for (ItemStack slot : player.getInventory().getContents()) {
                if (slot != null && slot.getType() == mat) available += slot.getAmount();
            }
            int toSell = Math.min(amount, available);
            if (toSell <= 0) return false;

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
                cs.deposit(player.getUniqueId(), currencyId, total)
                        .thenRun(() -> OreScheduler.runForEntity(plugin, player,
                                () -> OreScheduler.runLaterForEntity(plugin, player, () -> syncPlayer(player, true), 1L)))
                        .exceptionally(ex -> {
                            plugin.getLogger().warning("[WebPanel] Failed custom-currency SELL payout: " + ex.getMessage());
                            return null;
                        });
            } else {
                shopModule.getEconomy().deposit(player, total);
                OreScheduler.runLaterForEntity(plugin, player, () -> syncPlayer(player, true), 1L);
            }

            shopModule.getTransactionLogger().logTransaction(
                    player.getName(), "SOLD", toSell, mat.name(), total,
                    cs != null ? currencyId : shopModule.getEconomy().getEconomyName());
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
            OreScheduler.runLaterForEntity(plugin, player, () -> syncPlayer(player, true), 1L);
        }
        return true;
    }

    // ─── LuckPerms integration ────────────────────────────────────────────────

    /**
     * Applies a {@code luckperms_group_permission_update} message received from the web panel
     * via RabbitMQ. Runs entirely on the RabbitMQ consumer thread (async-safe for LP API).
     *
     * Expected payload:
     * <pre>
     * {
     *   "type": "luckperms_group_permission_update",
     *   "group": "vip",
     *   "changes": [
     *     { "kind": "boolean", "node": "oreo.vault", "value": true },
     *     { "kind": "choice",  "baseNode": "oreo.vault.rows.global", "oldValue": "3", "newValue": "5" },
     *     { "kind": "select",  "baseNode": "oreo.kit.cooldown",      "oldValue": "300s", "newValue": "600s" }
     *   ]
     * }
     * </pre>
     */
    private void applyLuckPermsUpdate(JsonObject action) {
        try {
            net.luckperms.api.LuckPerms lp;
            try {
                lp = net.luckperms.api.LuckPermsProvider.get();
            } catch (IllegalStateException e) {
                plugin.getLogger().warning("[WebPanel] LuckPerms not available — cannot apply permission update.");
                return;
            }

            String groupName = action.get("group").getAsString();
            JsonArray changes = action.getAsJsonArray("changes");
            if (changes == null || changes.size() == 0) return;

            // Ensure the group is loaded
            net.luckperms.api.model.group.Group group = lp.getGroupManager().getGroup(groupName);
            if (group == null) {
                lp.getGroupManager().loadGroup(groupName).get();
                group = lp.getGroupManager().getGroup(groupName);
            }
            if (group == null) {
                plugin.getLogger().warning("[WebPanel] LuckPerms group not found: " + groupName);
                return;
            }

            for (var elem : changes) {
                JsonObject change = elem.getAsJsonObject();
                String kind = change.get("kind").getAsString();
                switch (kind) {
                    case "boolean" -> {
                        String node = change.get("node").getAsString();
                        boolean value = change.has("value") && !change.get("value").isJsonNull()
                                && change.get("value").getAsBoolean();
                        if (value) {
                            group.data().add(net.luckperms.api.node.Node.builder(node).value(true).build());
                        } else {
                            group.data().remove(net.luckperms.api.node.Node.builder(node).value(true).build());
                        }
                    }
                    case "choice", "select" -> {
                        String baseNode = change.get("baseNode").getAsString();
                        String oldValue = change.has("oldValue") && !change.get("oldValue").isJsonNull()
                                ? change.get("oldValue").getAsString() : null;
                        String newValue = change.has("newValue") && !change.get("newValue").isJsonNull()
                                ? change.get("newValue").getAsString() : null;
                        if (oldValue != null) {
                            group.data().remove(net.luckperms.api.node.Node.builder(baseNode + "." + oldValue).value(true).build());
                        }
                        if (newValue != null) {
                            group.data().add(net.luckperms.api.node.Node.builder(baseNode + "." + newValue).value(true).build());
                        }
                    }
                    default -> plugin.getLogger().warning("[WebPanel] Unknown LP change kind: " + kind);
                }
            }

            lp.getGroupManager().saveGroup(group).get();
            plugin.getLogger().info("[WebPanel] Applied " + changes.size() + " LuckPerms change(s) to group '" + groupName + "'");

        } catch (Exception e) {
            plugin.getLogger().warning("[WebPanel] Failed to apply LuckPerms update: " + e.getMessage());
        }
    }

    /**
     * Reads all groups from LuckPerms and POSTs a full snapshot to the backend.
     * Runs asynchronously — do not call from the main thread.
     */
    private void syncLuckPermsGroupsToBackend() {
        try {
            net.luckperms.api.LuckPerms lp;
            try {
                lp = net.luckperms.api.LuckPermsProvider.get();
            } catch (IllegalStateException e) {
                return; // LuckPerms not installed — silently skip
            }

            // Load all groups into memory (blocks this async thread until done)
            lp.getGroupManager().loadAllGroups().get();
            java.util.Collection<net.luckperms.api.model.group.Group> groups =
                    lp.getGroupManager().getLoadedGroups();

            StringBuilder sb = new StringBuilder("{\"groups\":[");
            boolean firstGroup = true;
            for (net.luckperms.api.model.group.Group group : groups) {
                if (!firstGroup) sb.append(",");
                firstGroup = false;
                sb.append("{\"groupName\":\"")
                  .append(group.getName().replace("\"", "\\\""))
                  .append("\",\"permissions\":{");

                boolean firstPerm = true;
                for (net.luckperms.api.node.Node node : group.data().toCollection()) {
                    // Only include plain permission nodes (skip group-inheritance, meta, etc.)
                    if (node.getType() != net.luckperms.api.node.NodeType.PERMISSION) continue;
                    if (!firstPerm) sb.append(",");
                    firstPerm = false;
                    sb.append("\"")
                      .append(node.getKey().replace("\\", "\\\\").replace("\"", "\\\""))
                      .append("\":").append(node.getValue());
                }
                sb.append("}}");
            }
            sb.append("]}");

            boolean ok = client.syncLuckPermsGroups(sb.toString());
            if (ok) {
                plugin.getLogger().info("[WebPanel] Synced " + groups.size() + " LuckPerms group(s) to backend.");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[WebPanel] LuckPerms group sync failed: " + e.getMessage());
        }
    }
}
