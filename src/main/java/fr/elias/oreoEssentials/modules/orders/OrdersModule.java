package fr.elias.oreoEssentials.modules.orders;

import com.mongodb.client.MongoClient;
import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.orders.gui.OrdersGuiManager;
import fr.elias.oreoEssentials.modules.orders.rabbitmq.OrderSyncPacket;
import fr.elias.oreoEssentials.modules.orders.rabbitmq.OrdersEventBus;
import fr.elias.oreoEssentials.modules.orders.repository.MongoOrderRepository;
import fr.elias.oreoEssentials.modules.orders.repository.OrderRepository;
import fr.elias.oreoEssentials.modules.orders.repository.SQLiteOrderRepository;
import fr.elias.oreoEssentials.modules.orders.service.OrderCurrencyAdapter;
import fr.elias.oreoEssentials.modules.orders.service.OrderService;
import org.bukkit.Bukkit;

import java.lang.reflect.Field;
import java.sql.SQLException;

/**
 * Root class for the Orders (Market) module.
 * Initialised by OreoEssentials#onEnable() and wired into the packet system.
 *
 * Responsibilities:
 *  - Load config from plugins/OreoEssentials/orders/
 *  - Choose storage backend (MongoDB or SQLite)
 *  - Wire service, event bus, GUI manager, command
 *  - Expose static getInstance() for the chat listener
 */
public final class OrdersModule {

    private static OrdersModule instance;
    public static OrdersModule getInstance() { return instance; }

    private final OreoEssentials plugin;

    private OrdersConfig        cfg;
    private OrderRepository     repository;
    private OrderCurrencyAdapter currency;
    private OrdersEventBus       eventBus;
    private OrderService         service;
    private OrdersGuiManager     guiManager;

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
        service    = new OrderService(plugin, cfg, repository, currency, eventBus);
        guiManager = new OrdersGuiManager(plugin, this);

        eventBus.setService(service);
        eventBus.setGuiManager(guiManager);

        // Load active orders async
        service.loadActive().exceptionally(t -> {
            plugin.getLogger().warning("[Orders] Failed to load active orders: " + t.getMessage());
            return null;
        });

        ready = true;
        plugin.getLogger().info("[Orders] Module ready (storage=" + cfg.storageType()
                + ", cross-server=" + cfg.crossServerEnabled() + ").");
    }

    public void stop() {
        instance = null;
        ready    = false;
        if (repository != null) {
            repository.close();
            repository = null;
        }
    }

    /**
     * Called by OreoEssentials after the PacketManager is initialised,
     * to subscribe to incoming OrderSyncPackets.
     */
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


    public boolean             enabled()    { return ready; }
    public OreoEssentials      getPlugin()  { return plugin; }
    public OrdersConfig        getConfig()  { return cfg; }
    public OrderService        getService() { return service; }
    public OrdersGuiManager    getGuiManager() { return guiManager; }
    public OrderCurrencyAdapter getCurrency()  { return currency; }
    public OrdersEventBus      getEventBus()   { return eventBus; }



    private boolean initRepository() {
        String mode = cfg.storageType();

        if ("mongodb".equals(mode)) {
            MongoClient client = getMongoClient();
            if (client != null) {
                String db = plugin.getConfig().getString("storage.mongo.database", "oreo");
                try {
                    repository = new MongoOrderRepository(client, db, cfg.mongoCollection(), plugin.getLogger());
                    return true;
                } catch (Exception e) {
                    plugin.getLogger().warning("[Orders] MongoDB init failed: " + e.getMessage() + ". Falling back to SQLite.");
                }
            } else {
                plugin.getLogger().warning("[Orders] MongoDB unavailable. Falling back to SQLite.");
            }
        }

        // SQLite
        try {
            repository = new SQLiteOrderRepository(cfg.folder(), cfg.sqliteFile(), plugin.getLogger());
            if (cfg.crossServerEnabled() && "mongodb".equals(mode)) {
                plugin.getLogger().warning("[Orders] cross_server.enabled=true requires MongoDB storage. " +
                        "Cross-server features are DISABLED in SQLite mode.");
            }
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("[Orders] SQLite init failed: " + e.getMessage());
            return false;
        }
    }

    private MongoClient getMongoClient() {
        try {
            Field f = OreoEssentials.class.getDeclaredField("homesMongoClient");
            f.setAccessible(true);
            Object obj = f.get(plugin);
            if (obj instanceof MongoClient mc) return mc;
        } catch (Throwable ignored) {}
        return null;
    }

    private String serverName() {
        try { return plugin.getConfigService().serverName(); }
        catch (Throwable t) { return "unknown"; }
    }
}
