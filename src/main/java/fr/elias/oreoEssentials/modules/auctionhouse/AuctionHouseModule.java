package fr.elias.oreoEssentials.modules.auctionhouse;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.auctionhouse.gui.BrowseGUI;
import fr.elias.oreoEssentials.modules.currency.CurrencyService;
import fr.elias.oreoEssentials.modules.auctionhouse.models.Auction;
import fr.elias.oreoEssentials.modules.auctionhouse.models.AuctionCategory;
import fr.elias.oreoEssentials.modules.auctionhouse.models.AuctionStatus;
import fr.elias.oreoEssentials.modules.auctionhouse.rabbitmq.AuctionSyncPacket;
import fr.elias.oreoEssentials.modules.auctionhouse.storage.AuctionStorage;
import fr.elias.oreoEssentials.modules.auctionhouse.storage.AuctionStorage.AuctionSnapshot;
import fr.elias.oreoEssentials.modules.auctionhouse.storage.JsonAuctionStorage;
import fr.elias.oreoEssentials.modules.auctionhouse.storage.MongoAuctionStorage;
import fr.elias.oreoEssentials.modules.auctionhouse.utils.DiscordWebhook;
import fr.elias.oreoEssentials.modules.auctionhouse.utils.ItemSerializer;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;


public final class AuctionHouseModule {

    private final OreoEssentials plugin;
    private AuctionHouseConfig cfg;
    private AuctionStorage storage;
    private Economy economy;

    private final List<Auction> activeAuctions  = new CopyOnWriteArrayList<>();
    private final List<Auction> expiredAuctions = new CopyOnWriteArrayList<>();
    private final List<Auction> soldAuctions    = new CopyOnWriteArrayList<>();

    /** Players waiting to type a price in chat after selecting a currency. */
    private final Map<UUID, PendingSell> pendingSells = new ConcurrentHashMap<>();

    public record PendingSell(ItemStack item, String currencyId, long durationHours) {}

    // ─── Browse-GUI viewer registry ───────────────────────────────────────────
    /** Tracks every player who has a BrowseGUI open so we can push live updates. */
    public record ViewerContext(AuctionCategory category, String searchQuery, int page) {}
    private final Map<UUID, ViewerContext> browseViewers = new ConcurrentHashMap<>();

    public void registerBrowseViewer(UUID uuid, AuctionCategory cat, String query, int page) {
        browseViewers.put(uuid, new ViewerContext(cat, query, page));
    }

    public void unregisterBrowseViewer(UUID uuid) {
        browseViewers.remove(uuid);
    }

    public void updateBrowseViewerPage(UUID uuid, int page) {
        browseViewers.computeIfPresent(uuid, (k, v) -> new ViewerContext(v.category(), v.searchQuery(), page));
    }

    private static AuctionHouseModule instance;
    public static AuctionHouseModule getInstance() { return instance; }

    private int expirationTaskId = -1;
    private int autoSaveTaskId   = -1;

    public AuctionHouseModule(OreoEssentials plugin) {
        this.plugin = plugin;
        reload();
    }


    public synchronized void reload() {
        if (expirationTaskId != -1) Bukkit.getScheduler().cancelTask(expirationTaskId);
        if (autoSaveTaskId   != -1) Bukkit.getScheduler().cancelTask(autoSaveTaskId);

        cfg = new AuctionHouseConfig(plugin);
        if (!cfg.enabled()) {
            if (storage != null) { try { storage.flush(); } catch (Throwable ignored) {} }
            storage = null;
            plugin.getLogger().info("[AuctionHouse] Disabled by config.");
            return;
        }

        instance = this;

        setupEconomy();

        setupStorage();

        loadAuctions();

        startTasks();

        Bukkit.getPluginManager().registerEvents(new AHGuiListener(this), plugin);

        plugin.getLogger().info("[AuctionHouse] Reloaded (storage=" +
                (storage instanceof MongoAuctionStorage ? "mongodb" : "json") + ", " +
                activeAuctions.size() + " active auctions).");
    }

    public void stop() {
        instance = null;
        pendingSells.clear();
        if (expirationTaskId != -1) Bukkit.getScheduler().cancelTask(expirationTaskId);
        if (autoSaveTaskId   != -1) Bukkit.getScheduler().cancelTask(autoSaveTaskId);
        saveAuctions();
        if (storage != null) try { storage.flush(); } catch (Throwable ignored) {}
    }

    public boolean enabled() { return cfg != null && cfg.enabled() && storage != null; }


    public void openBrowse(Player p) {
        if (!enabled()) return;
        BrowseGUI.getInventory(this).open(p);
    }

    /** Backward-compat overload — defaults to Vault economy. */
    public boolean createAuction(Player seller, ItemStack item, double price,
                                 long durationHours, AuctionCategory category) {
        return createAuction(seller, item, price, durationHours, category, null);
    }

    public boolean createAuction(Player seller, ItemStack item, double price,
                                 long durationHours, AuctionCategory category, String currencyId) {
        if (!enabled()) return false;

        // normalize currencyId
        if (currencyId != null && currencyId.isBlank()) currencyId = null;

        if (price < cfg.minPrice() || price > cfg.maxPrice()) {
            seller.sendMessage(cfg.getMessage("errors.invalid-price",
                    Map.of("min", String.valueOf(cfg.minPrice()), "max", String.valueOf(cfg.maxPrice()))));
            return false;
        }
        if (durationHours > cfg.maxDurationHours()) durationHours = cfg.maxDurationHours();
        if (getPlayerActiveListings(seller.getUniqueId()).size() >= cfg.maxListingsPerPlayer()) {
            seller.sendMessage(cfg.getMessage("errors.max-listings",
                    Map.of("max", String.valueOf(cfg.maxListingsPerPlayer()))));
            return false;
        }

        double fee = (price * cfg.listingFeePercent() / 100.0) + cfg.listingFeeFlat();
        if (fee > 0) {
            CurrencyService cs = (currencyId != null) ? plugin.getCurrencyService() : null;
            if (cs != null) {
                double balance = cs.getBalance(seller.getUniqueId(), currencyId).join();
                if (balance < fee) {
                    seller.sendMessage(cfg.getMessage("errors.not-enough-money"));
                    return false;
                }
                cs.withdraw(seller.getUniqueId(), currencyId, fee).join();
            } else if (economy != null) {
                if (!economy.has(seller, fee)) {
                    seller.sendMessage(cfg.getMessage("errors.not-enough-money"));
                    return false;
                }
                economy.withdrawPlayer(seller, fee);
            }
        }

        Auction auction = new Auction(seller.getUniqueId(), seller.getName(), item,
                price, durationHours * 3_600_000L, category);
        auction.setCurrencyId(currencyId);
        detectCustomItem(auction, item);

        activeAuctions.add(auction);
        broadcastSync(AuctionSyncPacket.create(serverName(), auction));
        seller.sendMessage(cfg.getMessage("listing.created",
                Map.of("price", formatMoney(price), "duration", durationHours + "h")));

        if (cfg.discordEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
                    new DiscordWebhook(cfg.discordWebhookUrl())
                            .setBotName(cfg.discordBotName())
                            .setBotAvatarUrl(cfg.discordBotAvatar())
                            .setTitle("New Listing")
                            .setDescription("**" + seller.getName() + "** listed **" +
                                    item.getType().name() + " x" + item.getAmount() +
                                    "** for **" + formatMoney(price) + "**")
                            .setColor(0x00FF00)
                            .execute());
        }
        return true;
    }

    public boolean purchaseAuction(Player buyer, String auctionId) {
        Auction auction = activeAuctions.stream()
                .filter(a -> a.getId().equals(auctionId))
                .findFirst().orElse(null);
        if (auction == null) {
            buyer.sendMessage(cfg.getMessage("errors.auction-not-found"));
            return false;
        }
        if (auction.getSeller().equals(buyer.getUniqueId())) {
            buyer.sendMessage(cfg.getMessage("errors.cannot-buy-own"));
            return false;
        }
        if (auction.isExpired()) {
            buyer.sendMessage(cfg.getMessage("errors.auction-expired"));
            return false;
        }
        double price = auction.getPrice();
        String auctionCurrencyId = auction.getCurrencyId(); // per-listing currency
        CurrencyService cs = (auctionCurrencyId != null) ? plugin.getCurrencyService() : null;
        double tax = price * cfg.taxPercent() / 100.0;
        double sellerReceives = price - tax;

        if (cs != null) {
            double balance = cs.getBalance(buyer.getUniqueId(), auctionCurrencyId).join();
            if (balance < price) {
                buyer.sendMessage(cfg.getMessage("errors.not-enough-money"));
                return false;
            }
            cs.withdraw(buyer.getUniqueId(), auctionCurrencyId, price).join();
            cs.deposit(auction.getSeller(), auctionCurrencyId, sellerReceives).join();
        } else {
            if (economy == null) return false;
            if (!economy.has(buyer, price)) {
                buyer.sendMessage(cfg.getMessage("errors.not-enough-money"));
                return false;
            }
            economy.withdrawPlayer(buyer, price);
            economy.depositPlayer(Bukkit.getOfflinePlayer(auction.getSeller()), sellerReceives);
        }

        Map<Integer, ItemStack> overflow = buyer.getInventory().addItem(auction.getItem());
        if (!overflow.isEmpty()) {
            overflow.values().forEach(i ->
                    buyer.getWorld().dropItemNaturally(buyer.getLocation(), i));
        }

        auction.markAsSold(buyer.getUniqueId(), buyer.getName());
        activeAuctions.remove(auction);
        soldAuctions.add(auction);
        broadcastSync(AuctionSyncPacket.purchase(serverName(), auction.getId(),
                buyer.getUniqueId(), buyer.getName()));

        buyer.sendMessage(cfg.getMessage("purchase.success",
                Map.of("item", auction.getItem().getType().name(), "price", formatMoney(price))));

        Player sellerOnline = Bukkit.getPlayer(auction.getSeller());
        if (sellerOnline != null) {
            sellerOnline.sendMessage(cfg.getMessage("listing.sold",
                    Map.of("item", auction.getItem().getType().name(),
                            "price", formatMoney(sellerReceives),
                            "buyer", buyer.getName())));
            playSound(sellerOnline, Sound.ENTITY_PLAYER_LEVELUP);
        }

        if (cfg.discordEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
                    new DiscordWebhook(cfg.discordWebhookUrl())
                            .setBotName(cfg.discordBotName())
                            .setTitle("Item Sold!")
                            .setDescription("**" + buyer.getName() + "** purchased **" +
                                    auction.getItem().getType().name() + "** from **" +
                                    auction.getSellerName() + "** for **" + formatMoney(price) + "**")
                            .setColor(0xFFD700)
                            .execute());
        }
        return true;
    }

    public boolean cancelAuction(Player owner, String auctionId) {
        Auction auction = activeAuctions.stream()
                .filter(a -> a.getId().equals(auctionId) && a.getSeller().equals(owner.getUniqueId()))
                .findFirst().orElse(null);
        if (auction == null) return false;

        auction.markAsCancelled();
        activeAuctions.remove(auction);
        broadcastSync(AuctionSyncPacket.remove(serverName(), AuctionSyncPacket.Action.CANCEL, auction.getId()));

        Map<Integer, ItemStack> overflow = owner.getInventory().addItem(auction.getItem());
        if (!overflow.isEmpty()) {
            overflow.values().forEach(i ->
                    owner.getWorld().dropItemNaturally(owner.getLocation(), i));
        }
        owner.sendMessage(cfg.getMessage("listing.cancelled"));
        return true;
    }

    public boolean reclaimExpired(Player owner, String auctionId) {
        Auction auction = expiredAuctions.stream()
                .filter(a -> a.getId().equals(auctionId) && a.getSeller().equals(owner.getUniqueId()))
                .findFirst().orElse(null);
        if (auction == null) return false;

        Map<Integer, ItemStack> overflow = owner.getInventory().addItem(auction.getItem());
        if (!overflow.isEmpty()) {
            owner.sendMessage(cfg.getMessage("errors.inventory-full"));
            return false;
        }
        expiredAuctions.remove(auction);
        owner.sendMessage(cfg.getMessage("listing.reclaimed"));
        return true;
    }


    public List<Auction> getAllActiveAuctions() {
        return Collections.unmodifiableList(activeAuctions);
    }

    public List<Auction> getAuctionsByCategory(AuctionCategory cat) {
        if (cat == AuctionCategory.ALL) return getAllActiveAuctions();
        return activeAuctions.stream().filter(a -> a.getCategory() == cat).collect(Collectors.toList());
    }

    public List<Auction> getPlayerActiveListings(UUID playerId) {
        return activeAuctions.stream().filter(a -> a.getSeller().equals(playerId)).collect(Collectors.toList());
    }

    public List<Auction> getPlayerExpired(UUID playerId) {
        return expiredAuctions.stream().filter(a -> a.getSeller().equals(playerId)).collect(Collectors.toList());
    }

    public List<Auction> getPlayerSold(UUID playerId) {
        return soldAuctions.stream().filter(a -> a.getSeller().equals(playerId)).collect(Collectors.toList());
    }


    public int clearAllAuctions() {
        int count = activeAuctions.size();

        activeAuctions.clear();
        expiredAuctions.clear();
        soldAuctions.clear();

        saveAuctions();
        return count;
    }


    public void checkExpiredAuctions() {
        List<Auction> nowExpired = activeAuctions.stream()
                .filter(Auction::isExpired)
                .collect(Collectors.toList());

        for (Auction a : nowExpired) {
            a.markAsExpired();
            activeAuctions.remove(a);
            expiredAuctions.add(a);
            broadcastSync(AuctionSyncPacket.remove(serverName(), AuctionSyncPacket.Action.EXPIRE, a.getId()));

            Player p = Bukkit.getPlayer(a.getSeller());
            if (p != null) {
                p.sendMessage(cfg.getMessage("listing.expired",
                        Map.of("item", a.getItem().getType().name())));
            }
        }
    }


    public void loadAuctions() {
        if (storage == null) return;
        AuctionSnapshot snap = storage.loadAll();
        activeAuctions.clear();  activeAuctions.addAll(snap.active());
        expiredAuctions.clear(); expiredAuctions.addAll(snap.expired());
        soldAuctions.clear();    soldAuctions.addAll(snap.sold());
    }

    public void saveAuctions() {
        if (storage == null) return;
        storage.saveAll(new AuctionSnapshot(
                new ArrayList<>(activeAuctions),
                new ArrayList<>(expiredAuctions),
                new ArrayList<>(soldAuctions)));
    }


    public OreoEssentials   getPlugin()  { return plugin; }
    public AuctionHouseConfig getConfig(){ return cfg; }
    public Economy          getEconomy() { return economy; }

    /** Format using Vault (for balance display etc.) */
    public String formatMoney(double amount) {
        return economy != null ? economy.format(amount) : String.format("$%.2f", amount);
    }

    /** Format using the per-listing currencyId (null = Vault). */
    public String formatMoney(double amount, String currencyId) {
        if (currencyId != null) {
            CurrencyService cs = plugin.getCurrencyService();
            if (cs != null) return cs.formatBalance(currencyId, amount);
        }
        return formatMoney(amount);
    }

    // ─── Pending sell (currency picker → chat input) ─────────────────────────

    public void addPendingSell(Player player, ItemStack item, String currencyId, long durationHours) {
        pendingSells.put(player.getUniqueId(),
                new PendingSell(item.clone(), currencyId, durationHours));
    }

    public boolean isWaitingForPrice(UUID uuid) {
        return pendingSells.containsKey(uuid);
    }

    /**
     * Called by the chat listener when a player types their price.
     * Returns true if this player was waiting (so the chat message is consumed).
     */
    public boolean consumePriceInput(Player player, String rawMessage) {
        PendingSell pending = pendingSells.remove(player.getUniqueId());
        if (pending == null) return false;

        double price;
        try {
            price = Double.parseDouble(rawMessage.trim().replace(",", "."));
        } catch (NumberFormatException e) {
            pendingSells.put(player.getUniqueId(), pending); // put back — let them retry
            player.sendMessage(ChatColor.RED + "Invalid price. Type a valid number (e.g. 100 or 1500.50).");
            return true;
        }
        if (price <= 0) {
            pendingSells.put(player.getUniqueId(), pending);
            player.sendMessage(ChatColor.RED + "Price must be greater than 0.");
            return true;
        }

        final double finalPrice = price;
        final PendingSell ps = pending;
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                fr.elias.oreoEssentials.modules.auctionhouse.gui.SellGUI
                        .getInventory(this, ps.item(), finalPrice, ps.durationHours(), null, ps.currencyId())
                        .open(player);
            } catch (Throwable t) {
                player.sendMessage(ChatColor.RED + "Failed to open sell menu: " + t.getMessage());
            }
        });
        return true;
    }


    // ─── Cross-server sync ────────────────────────────────────────────────────

    /** Sends a sync packet to all other servers (no-op if RabbitMQ is not available). */
    private void broadcastSync(AuctionSyncPacket pkt) {
        try {
            var pm = plugin.getPacketManager();
            if (pm != null && pm.isInitialized()) pm.sendPacket(pkt);
        } catch (Throwable ignored) {}
    }

    private String serverName() {
        try { return plugin.getConfigService().serverName(); }
        catch (Throwable t) { return "unknown"; }
    }

    /**
     * Called by the PacketManager subscriber when another server broadcasts
     * an AuctionSyncPacket. Applies the change to this server's in-memory lists,
     * then schedules a main-thread GUI refresh for all open BrowseGUI viewers.
     */
    public void applyIncomingSync(AuctionSyncPacket pkt) {
        if (pkt == null || !enabled()) return;
        // Ignore packets we sent ourselves
        if (serverName().equals(pkt.getOriginServer())) return;

        switch (pkt.getAction()) {
            case CREATE   -> syncCreate(pkt);
            case PURCHASE -> syncPurchase(pkt);
            case CANCEL   -> syncRemove(pkt.getAuctionId(), AuctionStatus.CANCELLED);
            case EXPIRE   -> syncExpire(pkt.getAuctionId());
        }

        // Push the updated listing to every player with BrowseGUI already open.
        // Must run on the main thread — RabbitMQ callbacks arrive off-thread.
        if (!browseViewers.isEmpty()) {
            Bukkit.getScheduler().runTask(plugin, this::refreshAllBrowseViewers);
        }
    }

    /**
     * Main-thread only. Re-opens BrowseGUI on the same page/filter for every
     * registered viewer, so they see the freshly-mutated activeAuctions list.
     * Stale entries (player offline / viewer closed GUI) are cleaned up here.
     */
    private void refreshAllBrowseViewers() {
        for (Map.Entry<UUID, ViewerContext> entry : new ArrayList<>(browseViewers.entrySet())) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p == null || !p.isOnline()) {
                browseViewers.remove(entry.getKey());
                continue;
            }
            ViewerContext ctx = entry.getValue();
            // getInventory() builds a fresh SmartInventory that calls init() → re-renders all slots.
            BrowseGUI.getInventory(this, ctx.category(), ctx.searchQuery())
                     .open(p, ctx.page());
        }
    }

    private void syncCreate(AuctionSyncPacket pkt) {
        if (activeAuctions.stream().anyMatch(a -> a.getId().equals(pkt.getAuctionId()))) return;
        org.bukkit.inventory.ItemStack item = ItemSerializer.deserialize(pkt.getItemData());
        if (item == null) return;
        AuctionCategory cat;
        try { cat = AuctionCategory.valueOf(pkt.getCategory()); }
        catch (Exception e) { cat = AuctionCategory.MISC; }
        Auction a = new Auction(pkt.getAuctionId(), pkt.getSellerUuid(), pkt.getSellerName(),
                item, pkt.getPrice(), pkt.getListedTime(), pkt.getExpirationTime(),
                cat, AuctionStatus.ACTIVE, null, null, 0L);
        a.setCurrencyId(pkt.getCurrencyId());
        a.setItemsAdderID(pkt.getItemsAdderId());
        a.setNexoID(pkt.getNexoId());
        a.setOraxenID(pkt.getOraxenId());
        activeAuctions.add(a);
    }

    private void syncPurchase(AuctionSyncPacket pkt) {
        Auction a = activeAuctions.stream()
                .filter(x -> x.getId().equals(pkt.getAuctionId()))
                .findFirst().orElse(null);
        if (a == null) return;
        a.markAsSold(pkt.getBuyerUuid(), pkt.getBuyerName());
        activeAuctions.remove(a);
        soldAuctions.add(a);
    }

    private void syncExpire(String auctionId) {
        Auction a = activeAuctions.stream()
                .filter(x -> x.getId().equals(auctionId))
                .findFirst().orElse(null);
        if (a == null) return;
        a.markAsExpired();
        activeAuctions.remove(a);
        expiredAuctions.add(a);
    }

    private void syncRemove(String auctionId, AuctionStatus status) {
        Auction a = activeAuctions.stream()
                .filter(x -> x.getId().equals(auctionId))
                .findFirst().orElse(null);
        if (a == null) return;
        if (status == AuctionStatus.CANCELLED) a.markAsCancelled();
        activeAuctions.remove(a);
    }

    // ─────────────────────────────────────────────────────────────────────────

    private void setupEconomy() {
        try {
            RegisteredServiceProvider<Economy> rsp =
                    Bukkit.getServicesManager().getRegistration(Economy.class);
            economy = rsp != null ? rsp.getProvider() : null;
        } catch (Throwable t) {
            economy = null;
        }
        if (economy == null) plugin.getLogger().warning("[AuctionHouse] Vault economy not found!");
    }

    private void setupStorage() {
        String mode = cfg.storageType();
        String mainMode = plugin.getConfig().getString("essentials.storage", "yaml").toLowerCase();
        boolean wantMongo = mode.equals("mongodb") || (mode.equals("auto") && "mongodb".equals(mainMode));

        if (wantMongo) {
            com.mongodb.client.MongoClient client = getMongoClient();
            if (client != null) {
                String db = plugin.getConfig().getString("storage.mongo.database", "oreo");
                storage = new MongoAuctionStorage(client, db, cfg.mongoCollection(), plugin.getLogger());
                return;
            }
            plugin.getLogger().warning("[AuctionHouse] MongoDB unavailable, falling back to JSON.");
        }
        storage = new JsonAuctionStorage(cfg.folder(), plugin.getLogger());
    }

    private void startTasks() {
        expirationTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin,
                this::checkExpiredAuctions, 20L * 60, 20L * 60).getTaskId();
        autoSaveTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin,
                this::saveAuctions, 20L * 300, 20L * 300).getTaskId();
    }

    private com.mongodb.client.MongoClient getMongoClient() {
        try {
            Field f = OreoEssentials.class.getDeclaredField("homesMongoClient");
            f.setAccessible(true);
            Object obj = f.get(plugin);
            if (obj instanceof com.mongodb.client.MongoClient mc) return mc;
        } catch (Throwable ignored) {}
        return null;
    }

    private void detectCustomItem(Auction auction, ItemStack item) {
        try {
            Class<?> cs = Class.forName("dev.lone.itemsadder.api.CustomStack");
            Object stack = cs.getMethod("byItemStack", ItemStack.class).invoke(null, item);
            if (stack != null) {
                String id = (String) cs.getMethod("getNamespacedID").invoke(stack);
                if (id != null) auction.setItemsAdderID(id);
            }
        } catch (Throwable ignored) {}

        try {
            Class<?> nx = Class.forName("com.nexomc.nexo.api.NexoItems");
            Boolean exists = (Boolean) nx.getMethod("exists", ItemStack.class).invoke(null, item);
            if (Boolean.TRUE.equals(exists)) {
                String id = (String) nx.getMethod("idFromItem", ItemStack.class).invoke(null, item);
                if (id != null) auction.setNexoID(id);
            }
        } catch (Throwable ignored) {}

        try {
            Class<?> ox = Class.forName("io.th0rgal.oraxen.api.OraxenItems");
            String id = (String) ox.getMethod("getIdByItem", ItemStack.class).invoke(null, item);
            if (id != null) auction.setOraxenID(id);
        } catch (Throwable ignored) {}

        if (item.hasItemMeta() && item.getItemMeta().hasCustomModelData()) {
            auction.setCustomModelData(item.getItemMeta().getCustomModelData());
        }
    }

    private static void playSound(Player p, Sound s) {
        try { p.playSound(p.getLocation(), s, 1f, 1f); } catch (Throwable ignored) {}
    }
}