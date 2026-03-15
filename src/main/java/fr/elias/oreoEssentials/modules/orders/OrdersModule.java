package fr.elias.oreoEssentials.modules.orders;

import com.mongodb.client.MongoClient;
import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.orders.gui.OrdersGuiManager;
import fr.elias.oreoEssentials.modules.orders.listener.OrdersJoinListener;
import fr.elias.oreoEssentials.modules.orders.rabbitmq.OrderSyncPacket;
import fr.elias.oreoEssentials.modules.orders.rabbitmq.OrdersEventBus;
import fr.elias.oreoEssentials.modules.orders.repository.MongoOrderRepository;
import fr.elias.oreoEssentials.modules.orders.repository.OrderRepository;
import fr.elias.oreoEssentials.modules.orders.repository.PendingDeliveryRepository;
import fr.elias.oreoEssentials.modules.orders.repository.SQLiteOrderRepository;
import fr.elias.oreoEssentials.modules.orders.repository.SQLitePendingDeliveryRepository;
import fr.elias.oreoEssentials.modules.orders.service.OrderCurrencyAdapter;
import fr.elias.oreoEssentials.modules.orders.service.OrderService;
import org.bukkit.Bukkit;

import java.sql.SQLException;

public final class OrdersModule {

    private static OrdersModule instance;
    public static OrdersModule getInstance() { return instance; }

    private final OreoEssentials plugin;

    private OrdersConfig              cfg;
    private OrderRepository           repository;
    private PendingDeliveryRepository deliveryRepo;
    private OrderCurrencyAdapter      currency;
    private OrdersEventBus            eventBus;
    private OrderService              service;
    private OrdersGuiManager          guiManager;

    private boolean ready = false;

    public OrdersModule(OreoEssentials plugin) {
        this.plugin = plugin;
        reload();
    }


    public synchronized void reload() {
        ready = false;

        cfg = new OrdersConfig(plugin);

        if (!cfg.enabled()) {
            plugin.getLogger().info("[Orders] Disabled by config.");
            instance = null;
            return;
        }

        instance = this;

        if (!initRepository()) {
            plugin.getLogger().severe("[Orders] Failed to initialise storage. Module disabled.");
            instance = null;
            return;
        }

        String serverId = serverName();
        currency   = new OrderCurrencyAdapter(plugin);
        eventBus   = new OrdersEventBus(plugin, serverId);

        service    = new OrderService(plugin, cfg, repository, currency, eventBus, deliveryRepo);
        guiManager = new OrdersGuiManager(plugin, this);

        eventBus.setService(service);
        eventBus.setGuiManager(guiManager);
        service.setGuiManager(guiManager);

        Bukkit.getPluginManager().registerEvents(
                new OrdersJoinListener(plugin, deliveryRepo), plugin);

        service.loadActive()
                .thenRun(() -> {
                    ready = true;
                    String actualStorage = (repository instanceof MongoOrderRepository) ? "mongodb" : "sqlite";
                    plugin.getLogger().info("[Orders] Module ready (storage=" + actualStorage
                            + ", cross-server=" + cfg.crossServerEnabled() + ").");
                    if (!"mongodb".equals(actualStorage) && "mongodb".equals(cfg.storageType())) {
                        plugin.getLogger().severe("[Orders] *** MISCONFIGURATION: storage=mongodb in config but running on SQLite! "
                                + "Cross-server order fills WILL fail. Check MongoDB connection. ***");
                    }
                })
                .exceptionally(t -> {
                    plugin.getLogger().warning("[Orders] Failed to load active orders: " + t.getMessage());
                    // Still mark ready so the module works for new orders, just with empty cache
                    ready = true;
                    return null;
                });
    }

    // The MongoClient created by getMongoClient() is owned by this module.
    private com.mongodb.client.MongoClient ownedMongoClient = null;

    public void stop() {
        instance = null;
        ready    = false;
        if (repository != null) {
            repository.close();
            repository = null;
        }
        if (deliveryRepo != null) {
            deliveryRepo.close();
            deliveryRepo = null;
        }
        if (ownedMongoClient != null) {
            try { ownedMongoClient.close(); } catch (Throwable ignored) {}
            ownedMongoClient = null;
        }
    }

    public void subscribeCrossServerEvents() {
        if (!ready || !cfg.crossServerEnabled()) return;
        var pm = plugin.getPacketManager();
        if (pm == null || !pm.isInitialized()) return;

        pm.subscribe(OrderSyncPacket.class, (channel, pkt) -> {
            try { eventBus.onReceive(pkt); }
            catch (Throwable t) { plugin.getLogger().warning("[Orders] Failed to handle packet: " + t.getMessage()); }
        });
        plugin.getLogger().info("[Orders] Cross-server sync subscribed.");
    }


    public boolean             enabled()       { return ready; }
    public OreoEssentials      getPlugin()     { return plugin; }
    public OrdersConfig        getConfig()     { return cfg; }
    public OrderService        getService()    { return service; }
    public OrdersGuiManager    getGuiManager() { return guiManager; }
    public OrderCurrencyAdapter getCurrency()  { return currency; }
    public OrdersEventBus      getEventBus()   { return eventBus; }


    private boolean initRepository() {
        String mode = cfg.storageType();

        if ("mongodb".equals(mode)) {
            MongoClient client = getMongoClient();
            if (client != null) {

                String db         = plugin.getConfig().getString("storage.mongo.database", "oreo");
                String prefix     = plugin.getConfig().getString("storage.mongo.collectionPrefix", "oreo_");
                String collection = resolveMongoCollection(prefix);

                // ── Diagnostic log — printed on EVERY server so mismatches are obvious
                plugin.getLogger().info("[Orders] MongoDB target: database=\"" + db
                        + "\" collection=\"" + collection + "\"");

                try {
                    ownedMongoClient = client;   // take ownership for cleanup in stop()
                    repository   = new MongoOrderRepository(client, db, collection, plugin.getLogger());
                    deliveryRepo = createSQLiteDeliveryRepo();
                    return deliveryRepo != null;
                } catch (Exception e) {
                    plugin.getLogger().warning("[Orders] MongoDB init failed: " + e.getMessage() + ". Falling back to SQLite.");
                }
            } else {
                plugin.getLogger().warning("[Orders] MongoDB unavailable. Falling back to SQLite.");
            }
        }

        // SQLite fallback
        try {
            repository = new SQLiteOrderRepository(cfg.folder(), cfg.sqliteFile(), plugin.getLogger());
            if (cfg.crossServerEnabled() && "mongodb".equals(mode)) {
                plugin.getLogger().warning("[Orders] cross_server.enabled=true requires MongoDB storage. " +
                        "Cross-server features are DISABLED in SQLite mode.");
            }
            deliveryRepo = createSQLiteDeliveryRepo();
            return deliveryRepo != null;
        } catch (SQLException e) {
            plugin.getLogger().severe("[Orders] SQLite init failed: " + e.getMessage());
            return false;
        }
    }


    private String resolveMongoCollection(String prefix) {
        String fromSettings = cfg.mongoCollection(); // from orders/settings.yml
        String defaultValue = "oreo_orders";         // the built-in default in OrdersConfig

        if (fromSettings != null && !fromSettings.equals(defaultValue)) {
            // Operator has set a custom collection name — respect it.
            plugin.getLogger().info("[Orders] Using custom MongoDB collection from settings.yml: \"" + fromSettings + "\"");
            return fromSettings;
        }

        // Use the global prefix so all modules are consistent.
        return prefix + "orders";
    }

    private PendingDeliveryRepository createSQLiteDeliveryRepo() {
        try {
            return new SQLitePendingDeliveryRepository(cfg.folder(), cfg.sqliteFile(), plugin.getLogger());
        } catch (SQLException e) {
            plugin.getLogger().severe("[Orders] PendingDelivery SQLite init failed: " + e.getMessage());
            return null;
        }
    }


    private MongoClient getMongoClient() {
        try {
            String uri = plugin.getConfig().getString("storage.mongo.uri",
                    plugin.getConfig().getString("storage.mongodb.uri", "mongodb://localhost:27017"));
            if (uri == null || uri.isBlank()) {
                plugin.getLogger().warning("[Orders] storage.mongo.uri is not set in config.yml.");
                return null;
            }
            plugin.getLogger().info("[Orders] Creating dedicated MongoClient for Orders module (uri host redacted).");
            return com.mongodb.client.MongoClients.create(uri);
        } catch (Throwable t) {
            plugin.getLogger().warning("[Orders] Failed to create MongoClient: " + t.getMessage());
            return null;
        }
    }

    private String serverName() {
        try { return plugin.getConfigService().serverName(); }
        catch (Throwable t) { return "unknown"; }
    }
}