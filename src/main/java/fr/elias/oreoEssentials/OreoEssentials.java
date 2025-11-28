// File: src/main/java/fr/elias/oreoEssentials/OreoEssentials.java
package fr.elias.oreoEssentials;

import fr.elias.oreoEssentials.bossbar.BossBarService;
import fr.elias.oreoEssentials.bossbar.BossBarToggleCommand;
import fr.elias.oreoEssentials.clearlag.ClearLagManager;
import fr.elias.oreoEssentials.commands.CommandManager;
import fr.elias.oreoEssentials.playerwarp.*;
import fr.elias.oreoEssentials.playerwarp.command.PlayerWarpCommand;
import fr.elias.oreoEssentials.playerwarp.command.PlayerWarpTabCompleter;
import fr.elias.oreoEssentials.rtp.listeners.RtpJoinListener;
// Core commands (essentials-like)
import fr.elias.oreoEssentials.commands.completion.*;
import fr.elias.oreoEssentials.commands.core.playercommands.*;
import fr.elias.oreoEssentials.commands.core.admins.*;
import fr.elias.oreoEssentials.commands.core.moderation.*;
import fr.elias.oreoEssentials.commands.core.admins.FlyCommand;
import fr.elias.oreoEssentials.commands.core.moderation.HealCommand;
import fr.elias.oreoEssentials.commands.core.playercommands.ReplyCommand;
import fr.elias.oreoEssentials.commands.core.playercommands.SetHomeCommand;
import fr.elias.oreoEssentials.commands.core.playercommands.SpawnCommand;
import fr.elias.oreoEssentials.commands.core.playercommands.TpAcceptCommand;
import fr.elias.oreoEssentials.commands.core.playercommands.TpDenyCommand;
import fr.elias.oreoEssentials.commands.core.playercommands.TpaCommand;
import fr.elias.oreoEssentials.commands.core.playercommands.WarpCommand;
import fr.elias.oreoEssentials.config.SettingsConfig;
import fr.elias.oreoEssentials.customcraft.CustomCraftingService;
import fr.elias.oreoEssentials.homes.TeleportBroker;
import fr.elias.oreoEssentials.modgui.freeze.FreezeManager;
import fr.elias.oreoEssentials.modgui.ip.IpTracker;
import fr.elias.oreoEssentials.modgui.notes.NotesChatListener;
import fr.elias.oreoEssentials.modgui.notes.PlayerNotesManager;
import fr.elias.oreoEssentials.modgui.world.WorldTweaksListener;
import fr.elias.oreoEssentials.rtp.RtpPendingService;
import fr.elias.oreoEssentials.services.*;
import fr.elias.oreoEssentials.services.chatservices.MuteService;
import fr.elias.oreoEssentials.services.mongoservices.*;

import fr.elias.oreoEssentials.playerwarp.mongo.MongoPlayerWarpStorage;
import fr.elias.oreoEssentials.playerwarp.mongo.MongoPlayerWarpDirectory;

import fr.elias.oreoEssentials.util.KillallLogger;
import fr.elias.oreoEssentials.util.Lang;
import com.mongodb.client.MongoClient;
import fr.elias.oreoEssentials.scoreboard.ScoreboardConfig;
import fr.elias.oreoEssentials.scoreboard.ScoreboardService;
import fr.elias.oreoEssentials.scoreboard.ScoreboardToggleCommand;
import fr.elias.oreoEssentials.customcraft.OeCraftCommand;
import fr.elias.oreoEssentials.rabbitmq.packet.PacketManager;
import fr.elias.oreoEssentials.commands.core.playercommands.SitCommand;
import fr.elias.oreoEssentials.listeners.SitListener;
import fr.elias.oreoEssentials.commands.core.admins.MoveCommand;

import fr.elias.oreoEssentials.services.yaml.YamlPlayerWarpStorage;
// Tab completion

// Economy commands
import fr.elias.oreoEssentials.commands.ecocommands.MoneyCommand;
import fr.elias.oreoEssentials.commands.ecocommands.completion.MoneyTabCompleter;
import fr.elias.oreoEssentials.rtp.RtpConfig;

// Databases / Cache
import fr.elias.oreoEssentials.database.JsonEconomyDatabase;
import fr.elias.oreoEssentials.database.MongoDBManager;
import fr.elias.oreoEssentials.database.PlayerEconomyDatabase;
import fr.elias.oreoEssentials.database.PostgreSQLManager;
import fr.elias.oreoEssentials.database.RedisManager;

// Economy bootstrap (internal bridge)
import fr.elias.oreoEssentials.economy.EconomyBootstrap;

// Listeners
import fr.elias.oreoEssentials.listeners.*;

// Offline cache
import fr.elias.oreoEssentials.offline.OfflinePlayerCache;
import  fr.elias.oreoEssentials.cross.ModBridge ;
// RabbitMQ
import fr.elias.oreoEssentials.rabbitmq.PacketChannels;
import fr.elias.oreoEssentials.rabbitmq.handler.PlayerJoinPacketHandler;
import fr.elias.oreoEssentials.rabbitmq.handler.PlayerQuitPacketHandler;
import fr.elias.oreoEssentials.rabbitmq.handler.RemoteMessagePacketHandler;
import fr.elias.oreoEssentials.rabbitmq.sender.RabbitMQSender;

// Services
import fr.minuskube.inv.InventoryManager;

// Chat
import fr.elias.oreoEssentials.chat.AsyncChatListener;
import fr.elias.oreoEssentials.chat.CustomConfig;
import fr.elias.oreoEssentials.chat.FormatManager;
import fr.elias.oreoEssentials.util.ChatSyncManager;

// Vault
import fr.elias.oreoEssentials.util.ProxyMessenger;
import fr.elias.oreoEssentials.vault.VaultEconomyProvider;
import net.milkbowl.vault.economy.Economy;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class OreoEssentials extends JavaPlugin {

    // Singleton
    private static OreoEssentials instance;
    public static OreoEssentials get() { return instance; }
    private MuteService muteService;
    public MuteService getMuteService() { return muteService; }
    // Economy bridge (internal) — distinct from Vault Economy
    private MongoClient homesMongoClient;
    private fr.elias.oreoEssentials.config.SettingsConfig settingsConfig;
    public fr.elias.oreoEssentials.config.SettingsConfig getSettingsConfig() { return settingsConfig; }
    private PlayerNotesManager notesManager;
    private NotesChatListener notesChat;
    private fr.elias.oreoEssentials.daily.DailyMongoStore dailyStore;
    private FreezeManager freezeManager;
    private Economy economy;
    private EconomyBootstrap ecoBootstrap;
    // add near other services
    private fr.elias.oreoEssentials.integration.DiscordModerationNotifier discordMod;
    public fr.elias.oreoEssentials.integration.DiscordModerationNotifier getDiscordMod() { return discordMod; }
    private fr.elias.oreoEssentials.services.HomeDirectory homeDirectory; // cross-server directory
    // Essentials services
    // with other fields
    private fr.elias.oreoEssentials.teleport.TpCrossServerBroker tpBroker;
    public fr.elias.oreoEssentials.teleport.TpCrossServerBroker getTpBroker() { return tpBroker; }
    // Portals + JumpPads + PlayerVaults
    private fr.elias.oreoEssentials.portals.PortalsManager portals;
    private fr.elias.oreoEssentials.jumpads.JumpPadsManager jumpPads;
    private fr.elias.oreoEssentials.playervaults.PlayerVaultsConfig playerVaultsConfig;
    // Field at top of class:
    private fr.elias.oreoEssentials.modgui.ModGuiService modGuiService;
    public fr.elias.oreoEssentials.modgui.ModGuiService getModGuiService() { return modGuiService; }

    private ConfigService configService;
    private StorageApi storage;
    private SpawnService spawnService;
    private InventoryManager invManager;
    public InventoryManager getInvManager() { return invManager; }
    public InventoryManager getInventoryManager() {
        return invManager;
    }

    private ProxyMessenger proxyMessenger;
    public ProxyMessenger getProxyMessenger() { return proxyMessenger; }
    private WarpService warpService;
    private HomeService homeService;
    private TeleportService teleportService;
    // Player warps (cross-server via Mongo)
    private PlayerWarpService playerWarpService;
    private PlayerWarpDirectory playerWarpDirectory;

    private BackService backService;
    private MessageService messageService;
    private DeathBackService deathBackService;
    private GodService godService;
    private CommandManager commands;
    // near your other fields
    private fr.elias.oreoEssentials.teleport.TpaCrossServerBroker tpaBroker;
    public fr.elias.oreoEssentials.teleport.TpaCrossServerBroker getTpaBroker() { return tpaBroker; }

    private fr.elias.oreoEssentials.kits.KitsManager kitsManager;
    private fr.elias.oreoEssentials.tab.TabListManager tabListManager;
    private WarpDirectory warpDirectory;
    private SpawnDirectory spawnDirectory;
    private TeleportBroker teleportBroker;
    public fr.elias.oreoEssentials.kits.KitsManager getKitsManager() { return kitsManager; }
    public fr.elias.oreoEssentials.tab.TabListManager getTabListManager() { return tabListManager; }
    private fr.elias.oreoEssentials.bossbar.BossBarService bossBarService;
    public fr.elias.oreoEssentials.bossbar.BossBarService getBossBarService() { return bossBarService; }
    // top-level fields
    private ModBridge modBridge;


    // Economy / messaging stack
    private PlayerEconomyDatabase database;
    private RedisManager redis;
    private OfflinePlayerCache offlinePlayerCache;
    private RtpPendingService rtpPendingService;

    // Vault provider reference (optional)
    private Economy vaultEconomy;
    // PlayerVaults
    private fr.elias.oreoEssentials.playervaults.PlayerVaultsService playervaultsService;
    public fr.elias.oreoEssentials.playervaults.PlayerVaultsService getPlayervaultsService() { return playervaultsService; }
    // Cross-server Invsee (live inventory viewer)
    private fr.elias.oreoEssentials.cross.InvseeService invseeService;
    private fr.elias.oreoEssentials.cross.InvseeCrossServerBroker invseeBroker;

    public fr.elias.oreoEssentials.cross.InvseeService getInvseeService() {
        return invseeService;
    }

    private KillallLogger killallLogger;
    // Interactive Commands
    private fr.elias.oreoEssentials.ic.ICManager icManager;
    public fr.elias.oreoEssentials.ic.ICManager getIcManager() { return icManager; }
    // in OreoEssentials.java (fields with other services)
    private fr.elias.oreoEssentials.events.EventConfig eventConfig;
    private fr.elias.oreoEssentials.events.DeathMessageService deathMessages;
    // Playtime Rewards
    private fr.elias.oreoEssentials.playtime.PlaytimeRewardsService playtimeRewards;
    public fr.elias.oreoEssentials.playtime.PlaytimeRewardsService getPlaytimeRewards() { return playtimeRewards; }
    private IpTracker ipTracker;
    private SettingsConfig settings;

    // Toggles
    private boolean economyEnabled;
    private boolean redisEnabled;
    private boolean rabbitEnabled;
    // with other fields
    private fr.elias.oreoEssentials.trade.TradeCrossServerBroker tradeBroker;
    public fr.elias.oreoEssentials.trade.TradeCrossServerBroker getTradeBroker() { return tradeBroker; }

    // RabbitMQ packet manager (optional)
    private PacketManager packetManager;

    // Chat system (Afelius -> merged)
    private CustomConfig chatConfig;
    private FormatManager chatFormatManager;
    private ChatSyncManager chatSyncManager;
    // trade
    private fr.elias.oreoEssentials.trade.TradeConfig tradeConfig;
    private fr.elias.oreoEssentials.trade.TradeService tradeService;
    public fr.elias.oreoEssentials.trade.TradeService getTradeService() { return tradeService; }

    private fr.elias.oreoEssentials.homes.HomeTeleportBroker homeTpBroker;
    // in OreoEssentials.java fields
    private fr.elias.oreoEssentials.config.CrossServerSettings crossServerSettings;
    public fr.elias.oreoEssentials.config.CrossServerSettings getCrossServerSettings() { return crossServerSettings; }

    // RTP + EnderChest


    private fr.elias.oreoEssentials.enderchest.EnderChestConfig ecConfig;
    private fr.elias.oreoEssentials.enderchest.EnderChestService ecService;
    public fr.elias.oreoEssentials.enderchest.EnderChestService getEnderChestService() { return ecService; }
    // RTP + EnderChest
    private RtpConfig rtpConfig;
    public RtpConfig getRtpConfig() { return rtpConfig; }
    // RTP cooldowns (per-player, in millis)
    private final java.util.Map<java.util.UUID, Long> rtpCooldownCache = new java.util.concurrent.ConcurrentHashMap<>();

    // NEW: Cross-server RTP bridge (used by RtpCommand)
    private fr.elias.oreoEssentials.rtp.RtpCrossServerBridge rtpBridge;


    private ScoreboardService scoreboardService;
    private fr.elias.oreoEssentials.mobs.HealthBarListener healthBarListener;
    private ClearLagManager clearLag;

    private fr.elias.oreoEssentials.aliases.AliasService aliasService;
    public fr.elias.oreoEssentials.aliases.AliasService getAliasService(){ return aliasService; }
    private fr.elias.oreoEssentials.jail.JailService jailService;
    public fr.elias.oreoEssentials.jail.JailService getJailService() { return jailService; }
    private CustomCraftingService customCraftingService;
    public CustomCraftingService getCustomCraftingService() { return customCraftingService; }
    private fr.elias.oreoEssentials.holograms.OreoHolograms oreoHolograms;

    // Playtime
    private fr.elias.oreoEssentials.playtime.PlaytimeTracker playtimeTracker;

    @Override
    public void onEnable() {

        // -------------------------------------------------
        // 0. BASE SINGLETON + CONFIG
        // -------------------------------------------------
        instance = this;
        saveDefaultConfig();
        fr.elias.oreoEssentials.config.LegacySettingsMigrator.migrate(this);

        this.settingsConfig = new fr.elias.oreoEssentials.config.SettingsConfig(this);
        this.settings = this.settingsConfig; // keep both names valid

        getLogger().info("[BOOT] OreoEssentials starting up…");

        // We'll need these values everywhere, so grab them ASAP.
        this.configService = new ConfigService(this); // we pull this early now
        final String essentialsStorage = getConfig().getString("essentials.storage", "yaml").toLowerCase();

        final String economyType       = getConfig().getString("economy.type", "none").toLowerCase();
        this.economyEnabled = settingsConfig.economyEnabled();
        getLogger().info("[Economy] " + (economyEnabled ? "Enabled" : "Disabled") + " via settings.yml");

        this.redisEnabled              = getConfig().getBoolean("redis.enabled", false);
        this.rabbitEnabled             = getConfig().getBoolean("rabbitmq.enabled", false);

        this.crossServerSettings = fr.elias.oreoEssentials.config.CrossServerSettings.load(this);
        final String localServerName = configService.serverName(); // unified server id

        this.killallLogger = new KillallLogger(this);

        // -------- Commands (manager then registrations) --------
        this.commands = new CommandManager(this);

        getLogger().info(
                "[BOOT] storage=" + essentialsStorage
                        + " economyType=" + economyType
                        + " redis=" + redisEnabled
                        + " rabbit=" + rabbitEnabled
                        + " server.name=" + localServerName
        );

        // -------------------------------------------------
        // 1. REDIS (needed before economy DB in your design)
        // -------------------------------------------------
        if (redisEnabled) {
            this.redis = new RedisManager(
                    getConfig().getString("redis.host", "localhost"),
                    getConfig().getInt("redis.port", 6379),
                    getConfig().getString("redis.password", "")
            );
            if (!redis.connect()) {
                getLogger().warning("[REDIS] Enabled but failed to connect. Continuing without cache.");
            } else {
                getLogger().info("[REDIS] Connected.");
            }
        } else {
            // Dummy instance prevents null checks in economy / db classes
            this.redis = new RedisManager("", 6379, "");
            getLogger().info("[REDIS] Disabled.");
        }

        // -------------------------------------------------
        // 2. ECONOMY BOOTSTRAP + VAULT REGISTRATION
        //    (THIS MUST BE BEFORE ANY OTHER PLUGIN TOUCHES ECONOMY)
        // -------------------------------------------------
        // Your internal economy layer:
        this.ecoBootstrap = new EconomyBootstrap(this);
        this.ecoBootstrap.enable();

        // We'll set up DB + Vault provider right now so it's available
        // to ChestShop or anything else that depends on Vault.
        if (economyEnabled) {
            this.database = null;

            switch (economyType) {
                case "mongodb" -> {
                    MongoDBManager mgr = new MongoDBManager(redis);
                    boolean ok = mgr.connect(
                            getConfig().getString("economy.mongodb.uri"),
                            getConfig().getString("economy.mongodb.database"),
                            getConfig().getString("economy.mongodb.collection")
                    );
                    if (!ok) {
                        getLogger().severe("[ECON] MongoDB connect failed. Disabling plugin.");
                        getServer().getPluginManager().disablePlugin(this);
                        return;
                    }
                    this.database = mgr;
                }
                case "postgresql" -> {
                    PostgreSQLManager mgr = new PostgreSQLManager(this, redis);
                    boolean ok = mgr.connect(
                            getConfig().getString("economy.postgresql.url"),
                            getConfig().getString("economy.postgresql.user"),
                            getConfig().getString("economy.postgresql.password")
                    );
                    if (!ok) {
                        getLogger().severe("[ECON] PostgreSQL connect failed. Disabling plugin.");
                        getServer().getPluginManager().disablePlugin(this);
                        return;
                    }
                    this.database = mgr;
                }
                case "json" -> {
                    JsonEconomyDatabase mgr = new JsonEconomyDatabase(this, redis);
                    boolean ok = mgr.connect("", "", ""); // JSON ignores args
                    if (!ok) {
                        getLogger().severe("[ECON] JSON init failed. Disabling plugin.");
                        getServer().getPluginManager().disablePlugin(this);
                        return;
                    }
                    this.database = mgr;
                }
                case "none" -> this.database = null;
                default -> { /* leave null for unknown */ }
            }

            if (this.database != null) {
                // We EXPECT Vault to already be on the server, since other plugins depend on it.
                if (getServer().getPluginManager().getPlugin("Vault") == null) {
                    getLogger().severe("[ECON] Vault not found but economy.enabled=true. Disabling plugin.");
                    getServer().getPluginManager().disablePlugin(this);
                    return;
                }

                // Unregister any stale provider just in case
                try {
                    getServer().getServicesManager().unregister(
                            net.milkbowl.vault.economy.Economy.class,
                            this
                    );
                } catch (Throwable ignored) {}

                // Register OUR provider into Vault at the highest priority.
                VaultEconomyProvider vaultProvider = new VaultEconomyProvider(this);
                getServer().getServicesManager().register(
                        net.milkbowl.vault.economy.Economy.class,
                        vaultProvider,
                        this,
                        org.bukkit.plugin.ServicePriority.Highest
                );

                // Cache the provider for fast access later
                var rsp = getServer().getServicesManager()
                        .getRegistration(net.milkbowl.vault.economy.Economy.class);
                if (rsp == null) {
                    getLogger().severe("[ECON] Failed to hook Vault Economy.");
                } else {
                    this.vaultEconomy = rsp.getProvider();
                    getLogger().info("[ECON] Vault economy integration enabled at HIGHEST priority.");
                }

                // Listeners for economy player data + join/quit sync
                Bukkit.getPluginManager().registerEvents(new PlayerDataListener(this), this);
                Bukkit.getPluginManager().registerEvents(new PlayerListener(this), this);

                // Offline player cache
                this.offlinePlayerCache = new OfflinePlayerCache();

                // Warm cache now + schedule refresh
                this.database.populateCache(offlinePlayerCache);
                Bukkit.getScheduler().runTaskTimerAsynchronously(
                        this,
                        () -> this.database.populateCache(offlinePlayerCache),
                        20L * 60,   // after 1 min
                        20L * 300   // every 5 min
                );

                // Register money/pay/cheque commands NOW via CommandManager
                if (this.database != null) {
                    var moneyCmd  = new MoneyCommand(this);
                    var payCmd    = new fr.elias.oreoEssentials.commands.ecocommands.PayCommand();
                    var chequeCmd = new fr.elias.oreoEssentials.commands.ecocommands.ChequeCommand(this);

                    this.commands
                            .register(moneyCmd)
                            .register(payCmd)
                            .register(chequeCmd);

                    // Optional: keep extra tab-completers if they are NOT inside the command classes
                    if (getCommand("money") != null) {
                        getCommand("money").setTabCompleter(new MoneyTabCompleter(this));
                    }
                    if (getCommand("pay") != null) {
                        getCommand("pay").setTabCompleter(
                                new fr.elias.oreoEssentials.commands.ecocommands.completion.PayTabCompleter(this)
                        );
                    }
                    if (getCommand("cheque") != null) {
                        getCommand("cheque").setTabCompleter(
                                new fr.elias.oreoEssentials.commands.ecocommands.completion.ChequeTabCompleter()
                        );
                    }
                }


            } else {
                getLogger().warning("[ECON] Enabled but no database selected/connected; economy commands unavailable.");
            }
        } else {
            getLogger().info("[ECON] Disabled. Skipping Vault, DB, and economy commands.");
            this.database = null;
            this.vaultEconomy = null;
        }

        // -------------------------------------------------
        // 3. EARLY MISC BOOT (stuff you had before this point)
        // -------------------------------------------------

        // Make sure ClearLag config exists very early (so schedulers can start later)
        try {
            java.io.File f = new java.io.File(getDataFolder(), "clearlag.yml");
            if (!f.exists()) {
                saveResource("clearlag.yml", false);
            }
        } catch (Throwable ignored) {}

        fr.elias.oreoEssentials.util.SkinRefresherBootstrap.init(this);
        fr.elias.oreoEssentials.util.SkinDebug.init(this);

        // Locales
        Lang.init(this);
        // --- Kits (fully optional) ---
        boolean kitsFeature   = settingsConfig.kitsEnabled();
        boolean kitsRegister  = settingsConfig.kitsCommandsEnabled();


        if (kitsFeature) {
            this.kitsManager = new fr.elias.oreoEssentials.kits.KitsManager(this);

            if (kitsRegister) {
                new fr.elias.oreoEssentials.kits.KitCommands(this, this.kitsManager); // registers /kits and /kit
                getLogger().info("[Kits] Loaded " + this.kitsManager.getKits().size() + " kits from kits.yml");
            } else {
                // make absolutely sure the labels are free
                unregisterCommandHard("kits");
                unregisterCommandHard("kit");
                getLogger().info("[Kits] Module loaded, but commands are NOT registered.");
            }
        } else {
            // module fully off: free command labels so other plugins can use them
            unregisterCommandHard("kits");
            unregisterCommandHard("kit");
            this.kitsManager = null;
            getLogger().info("[Kits] Module disabled by config; commands unregistered.");
        }


        // --- Alias editor boot ---
        this.aliasService = new fr.elias.oreoEssentials.aliases.AliasService(this);
        this.aliasService.load();
        this.aliasService.applyRuntimeRegistration();
        // -------- Proxy plugin messaging (server switching) --------
        // -------- Proxy plugin messaging (server switching) --------
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        getServer().getMessenger().registerOutgoingPluginChannel(this, "bungeecord:main");
        this.proxyMessenger = new ProxyMessenger(this);
        ProxyMessenger proxyMessenger = this.proxyMessenger; // reuse local var below
        getLogger().info("[BOOT] Registered proxy plugin messaging channels.");


        // -------- UI/Managers created early --------
        this.invManager = new InventoryManager(this);
        this.invManager.init();
        // --- Server Management GUI (ModGUI) ---
        try {
            this.modGuiService = new fr.elias.oreoEssentials.modgui.ModGuiService(this);
            getLogger().info("[ModGUI] Server management GUI ready (/modgui).");

            // World tweaks (Nether/End, Elytra, Trident, PvP per world, etc.)
            new WorldTweaksListener(this);  // self-registers in constructor
        } catch (Throwable t) {
            getLogger().warning("[ModGUI] Failed to init: " + t.getMessage());
            this.modGuiService = null;
        }

        notesManager = new PlayerNotesManager(this);
        notesChat = new NotesChatListener(this, notesManager);
        ipTracker = new IpTracker(this);

        // --- Trade (/trade) ---
        this.tradeConfig = new fr.elias.oreoEssentials.trade.TradeConfig(this);

        if (this.tradeConfig.enabled && settingsConfig.tradeEnabled()) {
            // service
            this.tradeService = new fr.elias.oreoEssentials.trade.TradeService(this, this.tradeConfig);

            // command
            if (getCommand("trade") != null) {
                getCommand("trade").setExecutor(
                        new fr.elias.oreoEssentials.trade.TradeCommand(this, this.tradeService)
                );
                getLogger().info("[Trade] Enabled.");
            } else {
                getLogger().warning("[Trade] Command 'trade' not found in plugin.yml; skipping.");
            }

            org.bukkit.Bukkit.getPluginManager().registerEvents(
                    new fr.elias.oreoEssentials.trade.ui.TradeGuiGuardListener(this),
                    this
            );
        } else {
            this.tradeService = null;
            unregisterCommandHard("trade");
            getLogger().info("[Trade] Disabled (trade.yml or settings.yml).");
        }



        // === Custom Crafting (SmartInvs GUI) ===
        this.customCraftingService = new CustomCraftingService(this);
        this.customCraftingService.loadAllAndRegister();

        getServer().getPluginManager().registerEvents(
                new fr.elias.oreoEssentials.customcraft.CustomCraftingListener(this.customCraftingService),
                this
        );

        if (getCommand("oecraft") != null) {
            getCommand("oecraft").setExecutor(new OeCraftCommand(this, invManager, customCraftingService));
        } else {
            getLogger().warning("[CustomCraft] Command 'oecraft' not found in plugin.yml; skipping registration.");
        }
        getServer().getPluginManager().registerEvents(
                new fr.elias.oreoEssentials.trade.ui.TradeInventoryCloseListener(this), this);


        // (you had this listener registration twice; keeping it once is enough, but we won't delete it)
        getServer().getPluginManager().registerEvents(
                new fr.elias.oreoEssentials.customcraft.CustomCraftingListener(customCraftingService),
                this
        );
        this.freezeManager = new FreezeManager(this);

        // -------- Daily (Mongo) + rewards.yml loader (NEW) --------
        var dailyCfg = new fr.elias.oreoEssentials.daily.DailyConfig(this);
        dailyCfg.load();

        this.dailyStore = new fr.elias.oreoEssentials.daily.DailyMongoStore(this, dailyCfg);
        if (dailyCfg.mongo.enabled) this.dailyStore.connect();

        var dailyRewardsCfg = new fr.elias.oreoEssentials.daily.RewardsConfig(this);
        dailyRewardsCfg.load();

        var dailySvc = new fr.elias.oreoEssentials.daily.DailyService(this, dailyCfg, this.dailyStore, dailyRewardsCfg);


        // Register /daily (claim GUI, claim, top)
        var dailyCmd = new fr.elias.oreoEssentials.daily.DailyCommand(this, dailyCfg, dailySvc, dailyRewardsCfg);
        if (getCommand("daily") != null) {
            getCommand("daily").setExecutor(dailyCmd);
            getCommand("daily").setTabCompleter(dailyCmd);
        }

        // -------- Moderation core needed by chat --------
        muteService = new MuteService(this);
        getServer().getPluginManager().registerEvents(
                new fr.elias.oreoEssentials.listeners.MuteListener(muteService),
                this
        );

        // -------- Discord moderation notifier (separate config)
        if (settingsConfig.discordModerationEnabled()) {
            this.discordMod = new fr.elias.oreoEssentials.integration.DiscordModerationNotifier(this);
            getLogger().info("[DiscordMod] Discord moderation integration enabled (settings.yml).");
        } else {
            this.discordMod = null;
            getLogger().info("[DiscordMod] Disabled by settings.yml (features.discord-moderation.enabled=false).");
        }

        // -------- MOBS Health bar --------
        try {
            // Soft-hook UltimateChristmas (may be null)
            fr.elias.ultimateChristmas.UltimateChristmas xmasHook = null;
            try {
                var maybe = getServer().getPluginManager().getPlugin("UltimateChristmas");
                if (maybe instanceof fr.elias.ultimateChristmas.UltimateChristmas uc && maybe.isEnabled()) {
                    xmasHook = uc;
                    getLogger().info("[MOBS] UltimateChristmas hooked.");
                }
            } catch (Throwable ignored) {}

            if (settingsConfig.mobsHealthbarEnabled()) {
                try {
                    var hbl = new fr.elias.oreoEssentials.mobs.HealthBarListener(this, xmasHook);
                    this.healthBarListener = hbl;
                    getServer().getPluginManager().registerEvents(hbl, this);
                    getLogger().info("[MOBS] Health bars enabled (settings.yml).");
                } catch (Throwable t) {
                    getLogger().warning("[MOBS] Failed to init health bars: " + t.getMessage());
                }
            } else {
                getLogger().info("[MOBS] Disabled by settings.yml (features.mobs.enabled=false or healthbar=false).");
            }
        } catch (Throwable t) {
            getLogger().warning("[MOBS] Unexpected failure booting health bars: " + t.getMessage());
        }



        // -------- KILLALL Recorder commands --------
        var killExec = new KillallRecorderCommand(this, killallLogger);
        getCommand("killallr").setExecutor(killExec);
        getCommand("killallr").setTabCompleter(killExec);
        getCommand("killallrlog").setExecutor(new KillallLogViewCommand(killallLogger));

        // -------- ClearLag Module (manager + command) --------
        if (settingsConfig.clearLagEnabled()) {
            try {
                this.clearLag = new ClearLagManager(this);

                var olaggCmd = getCommand("olagg");
                if (olaggCmd != null) {
                    var olagg = new fr.elias.oreoEssentials.clearlag.ClearLagCommands(clearLag);
                    olaggCmd.setExecutor(olagg);
                    olaggCmd.setTabCompleter(olagg);
                    getLogger().info("[OreoLag] Enabled — /olagg active.");
                } else {
                    getLogger().warning("[OreoLag] Command 'olagg' not found in plugin.yml; skipping.");
                }

            } catch (Throwable t) {
                getLogger().warning("[OreoLag] FAILED to initialize: " + t.getMessage());
                this.clearLag = null;
            }
        } else {
            unregisterCommandHard("olagg");
            this.clearLag = null;
            getLogger().info("[OreoLag] Disabled by settings.yml.");
        }


        // -------- Chat system (Afelius merge) --------
        this.chatConfig = new fr.elias.oreoEssentials.chat.CustomConfig(this, "chat-format.yml");
        this.chatFormatManager = new fr.elias.oreoEssentials.chat.FormatManager(chatConfig);

        // Custom join messages
        getServer().getPluginManager().registerEvents(new JoinMessagesListener(this), this);

        // Chat sync via RabbitMQ (optional) — init before listener, pass mute service
        boolean chatSyncEnabled = chatConfig.getCustomConfig().getBoolean("MongoDB_rabbitmq.enabled", false);
        String chatRabbitUri    = chatConfig.getCustomConfig().getString("MongoDB_rabbitmq.rabbitmq.uri", "");
        try {
            this.chatSyncManager = new ChatSyncManager(chatSyncEnabled, chatRabbitUri, muteService);
            if (chatSyncEnabled) this.chatSyncManager.subscribeMessages();
            getLogger().info("[CHAT] ChatSync enabled=" + chatSyncEnabled);
        } catch (Exception e) {
            getLogger().severe("[CHAT] ChatSync init failed: " + e.getMessage());
            this.chatSyncManager = new ChatSyncManager(false, "", muteService);
        }

        // Discord relay (controlled by settings.yml master toggle + chat-format.yml)
        boolean discordEnabled = false;
        String discordWebhookUrl = "";

        if (getSettingsConfig().chatDiscordBridgeEnabled()) { // ← NEW master toggle check
            var chatRoot = chatConfig.getCustomConfig().getConfigurationSection("chat.discord");
            if (chatRoot != null) {
                discordEnabled = chatRoot.getBoolean("enabled", false);
                discordWebhookUrl = chatRoot.getString("webhook_url", "");
            }
        }

        // Register async chat listener (mute-aware)
        getServer().getPluginManager().registerEvents(
                new AsyncChatListener(
                        chatFormatManager,
                        chatConfig,
                        chatSyncManager,
                        discordEnabled,
                        discordWebhookUrl,
                        muteService
                ),
                this
        );


        // Conversations / auto messages
        getServer().getPluginManager().registerEvents(new ConversationListener(this), this);
        new fr.elias.oreoEssentials.tasks.AutoMessageScheduler(this).start();
        // PlayerWarps flags (used in all storage modes)
        var pwRoot = settingsConfig.getRoot().getConfigurationSection("playerwarps");
        boolean pwEnabled = (pwRoot == null) || pwRoot.getBoolean("enabled", true);
        boolean pwCross   = (pwRoot == null) || pwRoot.getBoolean("cross-server", true);

        // -------- Essentials storage selection (Homes/Warps/Spawn/Back) --------
        // Also sets up cross-server directories when using MongoDB
        switch (essentialsStorage) {
            case "mongodb" -> {
                String uri = getConfig().getString("storage.mongo.uri", "mongodb://localhost:27017");
                String dbName = getConfig().getString("storage.mongo.database", "oreo");
                String prefix = getConfig().getString("storage.mongo.collectionPrefix", "oreo_");

                this.homesMongoClient = com.mongodb.client.MongoClients.create(uri);

                this.playerDirectory = new fr.elias.oreoEssentials.playerdirectory.PlayerDirectory(
                        this.homesMongoClient, dbName, prefix
                );
                var dirListener = new fr.elias.oreoEssentials.playerdirectory.DirectoryPresenceListener(
                        this.playerDirectory,
                        configService.serverName()
                );
                getServer().getPluginManager().registerEvents(dirListener, this);
                dirListener.backfillOnline(); // optional
                // after dirListener.backfillOnline();
                try {
                    new fr.elias.oreoEssentials.playerdirectory.DirectoryHeartbeat(
                            this.playerDirectory,
                            configService.serverName()
                    ).start();
                    getLogger().info("[PlayerDirectory] Heartbeat started (every 30s).");
                } catch (Throwable t) {
                    getLogger().warning("[PlayerDirectory] Heartbeat failed to start: " + t.getMessage());
                }

                // (Optional) migration helper
                try {
                    MongoHomesMigrator.run(
                            this.homesMongoClient,
                            dbName,
                            prefix,
                            org.bukkit.Bukkit.getServer().getName(), // legacy
                            localServerName,
                            getLogger()
                    );
                } catch (Throwable ignored) {
                    getLogger().info("[STORAGE] MongoHomesMigrator skipped.");
                }

                // Mongo-backed StorageApi
                this.storage = new MongoHomesStorage(
                        this.homesMongoClient, dbName, prefix, localServerName
                );

                // Cross-server directories
                this.homeDirectory = new MongoHomeDirectory(
                        this.homesMongoClient, dbName, prefix + "home_directory"
                );
                try {
                    this.warpDirectory = new MongoWarpDirectory(
                            this.homesMongoClient, dbName, prefix + "warp_directory"
                    );
                } catch (Throwable ignored) {
                    this.warpDirectory = null;
                }
                try {
                    this.spawnDirectory = new MongoSpawnDirectory(
                            this.homesMongoClient, dbName, prefix + "spawn_directory"
                    );
                } catch (Throwable ignored) {
                    this.spawnDirectory = null;
                }
                {
                    {
                        // --- PlayerWarps (Mongo only, optional cross-server) ---
                        var settingsRoot = this.settingsConfig.getRoot();
                        var pwSection = settingsRoot.getConfigurationSection("playerwarps");

                        getLogger().info("[PlayerWarps/DEBUG] essentialsStorage=" + essentialsStorage
                                + " pwEnabled=" + pwEnabled
                                + " pwCross=" + pwCross
                                + " pwSectionExists=" + (pwSection != null));

                        if (pwEnabled) {
                            PlayerWarpStorage pwStorage = new MongoPlayerWarpStorage(
                                    this.homesMongoClient,
                                    dbName,
                                    prefix + "playerwarps"
                            );

                            PlayerWarpDirectory pwDir = null;
                            if (pwCross) {
                                try {
                                    pwDir = new MongoPlayerWarpDirectory(
                                            this.homesMongoClient,
                                            dbName,
                                            prefix + "playerwarp_directory"
                                    );
                                    getLogger().info("[PlayerWarps/DEBUG] MongoPlayerWarpDirectory initialized: "
                                            + pwDir.getClass().getSimpleName());
                                } catch (Throwable t) {
                                    getLogger().warning("[PlayerWarps] Failed to init MongoPlayerWarpDirectory: " + t.getMessage());
                                }
                            } else {
                                getLogger().info("[PlayerWarps/DEBUG] pwCross=false, directory will be null (local-only warps).");
                            }

                            this.playerWarpDirectory = pwDir;
                            this.playerWarpService = new PlayerWarpService(pwStorage, pwDir);

                            getLogger().info("[PlayerWarps] Enabled with MongoDB storage. cross-server=" + pwCross
                                    + " directory=" + (pwDir == null ? "null" : pwDir.getClass().getSimpleName()));
                        } else {
                            this.playerWarpDirectory = null;
                            this.playerWarpService = null;
                            getLogger().info("[PlayerWarps] Disabled by settings.yml (playerwarps.enabled=false).");
                        }

                        getLogger().info("[STORAGE] Using MongoDB (MongoHomesStorage + directories).");
                    }

                }
            }
            case "json" -> {
                this.storage        = new JsonStorage(this);
                this.homeDirectory  = null;
                this.warpDirectory  = null;
                this.spawnDirectory = null;

                if (pwEnabled) {
                    PlayerWarpStorage pwStorage = new YamlPlayerWarpStorage(this); // local file
                    this.playerWarpService      = new PlayerWarpService(pwStorage, null); // directory = null
                    this.playerWarpDirectory    = null;
                    getLogger().info("[PlayerWarps] Enabled with local YAML storage (essentials.storage=json, no cross-server).");
                } else {
                    this.playerWarpService   = null;
                    this.playerWarpDirectory = null;
                    getLogger().info("[PlayerWarps] Disabled by settings.yml (playerwarps.enabled=false).");
                }

                getLogger().info("[STORAGE] Using JSON.");
            }

            default -> {
                this.storage        = new fr.elias.oreoEssentials.services.YamlStorage(this);
                this.homeDirectory  = null;
                this.warpDirectory  = null;
                this.spawnDirectory = null;

                if (pwEnabled) {
                    PlayerWarpStorage pwStorage = new YamlPlayerWarpStorage(this); // local YAML
                    this.playerWarpService      = new PlayerWarpService(pwStorage, null);
                    this.playerWarpDirectory    = null;
                    getLogger().info("[PlayerWarps] Enabled with local YAML storage (no cross-server).");
                } else {
                    this.playerWarpService   = null;
                    this.playerWarpDirectory = null;
                    getLogger().info("[PlayerWarps] Disabled by settings.yml (playerwarps.enabled=false).");
                }

                getLogger().info("[STORAGE] Using YAML.");
            }


        }

        // ---- EnderChest config + storage (respect cross-server.enderchest + storage mode)
        this.ecConfig = new fr.elias.oreoEssentials.enderchest.EnderChestConfig(this);
        // Constructor already calls reload(), so no extra call needed.

        final boolean crossServerEc =
                settingsConfig.featureOption("cross-server", "enderchest", true);
        final boolean mongoStorage =
                "mongodb".equalsIgnoreCase(getConfig().getString("essentials.storage", "yaml"));

        fr.elias.oreoEssentials.enderchest.EnderChestStorage ecStorage;

        if (mongoStorage && crossServerEc && this.homesMongoClient != null) {
            String dbName = getConfig().getString("storage.mongo.database", "oreo");
            String prefix = getConfig().getString("storage.mongo.collectionPrefix", "oreo_");

            ecStorage = new fr.elias.oreoEssentials.enderchest.MongoEnderChestStorage(
                    this.homesMongoClient,
                    dbName,
                    prefix,
                    getLogger()
            );
            getLogger().info("[EC] Using MongoDB cross-server ender chest storage.");
        } else {
            ecStorage = new fr.elias.oreoEssentials.enderchest.YamlEnderChestStorage(this);
            getLogger().info("[EC] Using local YAML ender chest storage.");
        }

        this.ecService = new fr.elias.oreoEssentials.enderchest.EnderChestService(
                this,
                this.ecConfig,
                ecStorage
        );

        Bukkit.getServicesManager().register(
                fr.elias.oreoEssentials.enderchest.EnderChestService.class,
                this.ecService,
                this,
                org.bukkit.plugin.ServicePriority.Normal
        );



        // --- Player Sync bootstrap ---
        final boolean invSyncEnabled = settingsConfig.featureOption("cross-server", "inventory", true);

        fr.elias.oreoEssentials.playersync.PlayerSyncStorage invStorage;

        if (invSyncEnabled
                && "mongodb".equalsIgnoreCase(getConfig().getString("essentials.storage", "yaml"))
                && this.homesMongoClient != null) {
            String dbName = getConfig().getString("storage.mongo.database", "oreo");
            String prefix = getConfig().getString("storage.mongo.collectionPrefix", "oreo_");
            invStorage = new fr.elias.oreoEssentials.playersync.MongoPlayerSyncStorage(
                    this.homesMongoClient, dbName, prefix
            );
            getLogger().info("[SYNC] Using MongoDB storage.");
        } else {
            invStorage = new fr.elias.oreoEssentials.playersync.YamlPlayerSyncStorage(this);
            getLogger().info("[SYNC] Using local YAML storage.");
        }

        final var syncPrefsStore    = new fr.elias.oreoEssentials.playersync.PlayerSyncPrefsStore(this);
        final var playerSyncService = new fr.elias.oreoEssentials.playersync.PlayerSyncService(
                this,
                invStorage,
                syncPrefsStore
        );

        getServer().getPluginManager().registerEvents(
                new fr.elias.oreoEssentials.playersync.PlayerSyncListener(playerSyncService, invSyncEnabled),
                this
        );

        // --- expose InventoryService so /invsee works for offline & cross-server ---
        fr.elias.oreoEssentials.services.InventoryService invSvc =
                new fr.elias.oreoEssentials.services.InventoryService() {
                    @Override
                    public Snapshot load(java.util.UUID uuid) {
                        try {
                            var s = invStorage.load(uuid); // Mongo or YAML depending on config
                            if (s == null) return null;
                            Snapshot snap = new Snapshot();
                            snap.contents = s.inventory;
                            snap.armor    = s.armor;
                            snap.offhand  = s.offhand;
                            return snap;
                        } catch (Exception e) {
                            getLogger().warning("[INVSEE] load failed: " + e.getMessage());
                            return null;
                        }
                    }

                    @Override
                    public void save(java.util.UUID uuid, Snapshot snapshot) {
                        try {
                            var s = new fr.elias.oreoEssentials.playersync.PlayerSyncSnapshot();
                            s.inventory = snapshot.contents;
                            s.armor     = snapshot.armor;
                            s.offhand   = snapshot.offhand;
                            invStorage.save(uuid, s);
                        } catch (Exception e) {
                            getLogger().warning("[INVSEE] save failed: " + e.getMessage());
                        }
                    }
                };

        Bukkit.getServicesManager().register(
                fr.elias.oreoEssentials.services.InventoryService.class,
                invSvc,
                this,
                org.bukkit.plugin.ServicePriority.Normal
        );

        getServer().getPluginManager().registerEvents(
                new fr.elias.oreoEssentials.enderchest.EnderChestListener(this, ecService, crossServerEc),
                this
        );

        // -------- Essentials Services --------
        this.spawnService = new SpawnService(storage);
        this.warpService  = new WarpService(storage, this.warpDirectory);
        this.homeService  = new HomeService(this.storage, this.configService, this.homeDirectory);

        // -------- RabbitMQ (optional cross-server signaling) --------
        if (rabbitEnabled) {
            RabbitMQSender rabbit = new RabbitMQSender(getConfig().getString("rabbitmq.uri"));

            // Make sure OfflinePlayerCache exists before any packet handlers / consumers start
            if (this.offlinePlayerCache == null) {
                this.offlinePlayerCache = new OfflinePlayerCache();
            }

            this.packetManager = new PacketManager(this, rabbit);
            if (rabbit.connect()) {
                packetManager.init();
                registerAllPacketsDeterministically(packetManager);
                try {
                    // If PacketManager exposes registryChecksum(), log it; otherwise no-op
                    getLogger().info("[RABBIT] Packet registry checksum=" + packetManager.registryChecksum());
                } catch (Throwable ignored) {}

            // ---- Cross-server Invsee broker + service ----
                if (invSyncEnabled) {
                    try {
                        this.invseeBroker = new fr.elias.oreoEssentials.cross.InvseeCrossServerBroker(
                                this,
                                packetManager,
                                localServerName,
                                null // temp, we set the real service just after
                        );
                        this.invseeService = new fr.elias.oreoEssentials.cross.InvseeService(
                                this,
                                invseeBroker
                        );
                        this.invseeBroker.setService(invseeService); // ★ IMPORTANT ★

                        getLogger().info("[INVSEE] Cross-server Invsee broker + service ready.");
                    } catch (Throwable t) {
                        this.invseeBroker = null;
                        this.invseeService = null;
                        getLogger().warning("[INVSEE] Failed to initialize cross-server Invsee: " + t.getMessage());
                    }
                } else {
                    this.invseeBroker = null;
                    this.invseeService = null;
                    getLogger().info("[INVSEE] Cross-server Invsee disabled (invSyncEnabled=false).");
                }


                // now you can subscribe channels & handlers safely
                packetManager.subscribeChannel(PacketChannels.GLOBAL);
                packetManager.subscribeChannel(
                        fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel.individual(localServerName)
                );
                getLogger().info("[RABBIT] Subscribed to individual channel for this server: " + localServerName);

                // --- Invsee packets (only if broker is active) ---
                if (this.invseeBroker != null) {
                    packetManager.subscribe(
                            fr.elias.oreoEssentials.cross.InvseeOpenRequestPacket.class,
                            (channel, pkt) -> invseeBroker.handleOpenRequest(pkt)
                    );
                    packetManager.subscribe(
                            fr.elias.oreoEssentials.cross.InvseeStatePacket.class,
                            (channel, pkt) -> invseeBroker.handleState(pkt)
                    );
                    packetManager.subscribe(
                            fr.elias.oreoEssentials.cross.InvseeEditPacket.class,
                            (channel, pkt) -> invseeBroker.handleEdit(pkt)
                    );
                    getLogger().info("[INVSEE] Subscribed Invsee packets on RabbitMQ.");
                }

                // your existing generic packet subscriptions...
                packetManager.subscribe(
                        fr.elias.oreoEssentials.rabbitmq.packet.impl.SendRemoteMessagePacket.class,
                        new RemoteMessagePacketHandler()
                );

                // --- Trade packets ---
                packetManager.subscribe(
                        fr.elias.oreoEssentials.rabbitmq.packet.impl.trade.TradeStartPacket.class,
                        (fr.elias.oreoEssentials.rabbitmq.packet.event.PacketSubscriber<
                                fr.elias.oreoEssentials.rabbitmq.packet.impl.trade.TradeStartPacket>
                                ) new fr.elias.oreoEssentials.rabbitmq.handler.trade.TradeStartPacketHandler(this)
                );

                packetManager.subscribe(
                        fr.elias.oreoEssentials.rabbitmq.packet.impl.PlayerJoinPacket.class,
                        new PlayerJoinPacketHandler(this)
                );
                packetManager.subscribe(
                        fr.elias.oreoEssentials.rabbitmq.packet.impl.PlayerQuitPacket.class,
                        new PlayerQuitPacketHandler(this)
                );
                packetManager.subscribe(
                        fr.elias.oreoEssentials.rabbitmq.packet.impl.trade.TradeInvitePacket.class,
                        new fr.elias.oreoEssentials.rabbitmq.handler.trade.TradeInvitePacketHandler(this)
                );
                packetManager.subscribe(
                        fr.elias.oreoEssentials.rabbitmq.packet.impl.trade.TradeStatePacket.class,
                        new fr.elias.oreoEssentials.rabbitmq.handler.trade.TradeStatePacketHandler(this)
                );
                packetManager.subscribe(
                        fr.elias.oreoEssentials.rabbitmq.packet.impl.trade.TradeConfirmPacket.class,
                        new fr.elias.oreoEssentials.rabbitmq.handler.trade.TradeConfirmPacketHandler(this)
                );
                packetManager.subscribe(
                        fr.elias.oreoEssentials.rabbitmq.packet.impl.trade.TradeCancelPacket.class,
                        new fr.elias.oreoEssentials.rabbitmq.handler.trade.TradeCancelPacketHandler(this)
                );
                packetManager.subscribe(
                        fr.elias.oreoEssentials.rabbitmq.packet.impl.trade.TradeGrantPacket.class,
                        new fr.elias.oreoEssentials.rabbitmq.handler.trade.TradeGrantPacketHandler(this)
                );
                packetManager.subscribe(
                        fr.elias.oreoEssentials.rabbitmq.packet.impl.trade.TradeClosePacket.class,
                        new fr.elias.oreoEssentials.rabbitmq.handler.trade.TradeClosePacketHandler(this)
                );

                getLogger().info("[RABBIT] Connected and subscriptions active.");
            } else {
                getLogger().severe("[RABBIT] Connect failed; continuing without messaging.");
                this.packetManager = null;
                this.invseeBroker = null;
                this.invseeService = null;
            }
        } else {
            getLogger().info("[RABBIT] Disabled.");
            this.packetManager = null;
            this.invseeBroker = null;
            this.invseeService = null;
        }

// ---- Cross-server Moderation Bridge (kill/kick/ban via Rabbit) ----
        if (packetManager != null && packetManager.isInitialized()) {
            this.modBridge = new ModBridge(
                    this,
                    packetManager,
                    configService.serverName()
            );
            getLogger().info("[MOD-BRIDGE] Cross-server moderation bridge ready.");
        } else {
            this.modBridge = null;
            getLogger().info("[MOD-BRIDGE] Cross-server moderation bridge disabled (PacketManager unavailable).");
        }

// ---- Cross-server Trade broker (only if Rabbit AND tradeService AND enabled in settings) ----
        if (packetManager != null
                && packetManager.isInitialized()
                && this.tradeService != null
                && settingsConfig.tradeCrossServerEnabled()) {

            this.tradeBroker = new fr.elias.oreoEssentials.trade.TradeCrossServerBroker(
                    this,
                    packetManager,
                    configService.serverName(),
                    this.tradeService
            );
            getLogger().info("[TRADE] Cross-server trade broker ready.");
        } else {
            this.tradeBroker = null;
            getLogger().info("[TRADE] Cross-server trade broker disabled (PacketManager unavailable, trade disabled, or settings.yml).");
        }


        // -------- Cross-server teleport brokers --------
        if (packetManager != null && packetManager.isInitialized()) {

            final var cs = this.getCrossServerSettings();
            final boolean anyCross =
                    cs.homes() || cs.warps() || cs.spawn() || cs.economy();

            // Only bind RabbitMQ channels if at least one cross-server feature is enabled
            if (anyCross) {
                packetManager.subscribeChannel(
                        fr.elias.oreoEssentials.rabbitmq.PacketChannels.GLOBAL
                );
                packetManager.subscribeChannel(
                        fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel
                                .individual(configService.serverName())
                );
                getLogger().info("[RABBIT] Subscribed channels for cross-server features. server=" + configService.serverName());
            } else {
                getLogger().info("[RABBIT] All cross-server features disabled by config; skipping channel subscriptions.");
            }

            // Spawn/Warp broker
            if (cs.spawn() || cs.warps()) {
                new fr.elias.oreoEssentials.teleport.CrossServerTeleportBroker(
                        this,
                        spawnService,
                        warpService,
                        packetManager,
                        configService.serverName()
                );
                getLogger().info("[BROKER] CrossServerTeleportBroker ready (spawn=" + cs.spawn() + ", warps=" + cs.warps() + ").");
            } else {
                getLogger().info("[BROKER] CrossServerTeleportBroker disabled by config (spawn & warps off).");
            }

            // Home broker (only if enabled)
            if (cs.homes()) {
                this.homeTpBroker = new fr.elias.oreoEssentials.homes.HomeTeleportBroker(
                        this,
                        homeService,
                        packetManager
                );
                getLogger().info("[BROKER] HomeTeleportBroker ready (server=" + configService.serverName() + ").");
            } else {
                getLogger().info("[BROKER] HomeTeleportBroker disabled by config (homes off).");
            }

        } else {
            getLogger().warning("[BROKER] Brokers not started: PacketManager unavailable.");
        }

        // -------- Core services --------
        this.backService      = new BackService(storage);
        this.messageService   = new MessageService();
        this.teleportService  = new TeleportService(this, backService, configService);
        this.deathBackService = new DeathBackService();
        this.godService       = new GodService();


// --- TPA cross-server broker (single initialization; no duplicates) ---
        if (packetManager != null && packetManager.isInitialized()) {
            this.tpaBroker = new fr.elias.oreoEssentials.teleport.TpaCrossServerBroker(
                    this,
                    this.teleportService,
                    this.packetManager,
                    proxyMessenger,
                    configService.serverName()
            );
            getLogger().info("[BROKER] TPA cross-server broker ready (server=" + configService.serverName() + ").");
        } else {
            this.tpaBroker = null;
            getLogger().info("[BROKER] TPA cross-server broker disabled (PacketManager unavailable or not initialized).");
        }
        // --- NEW: TP cross-server broker (admin /tp) ---
        if (packetManager != null && packetManager.isInitialized()) {
            this.tpBroker = new fr.elias.oreoEssentials.teleport.TpCrossServerBroker(
                    this, this.teleportService, this.packetManager, proxyMessenger, configService.serverName()
            );
            getLogger().info("[BROKER] TP cross-server broker ready (server=" + configService.serverName() + ").");
        } else {
            this.tpBroker = null;
            getLogger().info("[BROKER] TP cross-server broker disabled (PacketManager unavailable or not initialized).");
        }
        // --- NEW: TP cross-server broker (admin /tp) ---
        if (packetManager != null && packetManager.isInitialized()) {
            this.tpBroker = new fr.elias.oreoEssentials.teleport.TpCrossServerBroker(
                    this,
                    this.teleportService,
                    this.packetManager,
                    proxyMessenger,
                    configService.serverName()
            );
            getLogger().info("[BROKER] TP cross-server broker ready (server=" + configService.serverName() + ").");
        } else {
            this.tpBroker = null;
            getLogger().info("[BROKER] TP cross-server broker disabled (PacketManager unavailable or not initialized).");
        }

// --- NEW: PlayerWarp cross-server broker (/pw) ---
        if (packetManager != null
                && packetManager.isInitialized()
                && playerWarpService != null
                && proxyMessenger != null) {

            try {
                new fr.elias.oreoEssentials.playerwarp.PlayerWarpCrossServerBroker(
                        this,
                        playerWarpService,
                        packetManager,
                        proxyMessenger,
                        configService.serverName()
                );
                getLogger().info("[BROKER] PlayerWarpCrossServerBroker enabled.");
            } catch (Throwable t) {
                getLogger().warning("[BROKER] Failed to init PlayerWarpCrossServerBroker: " + t.getMessage());
            }
        } else {
            getLogger().info("[BROKER] PlayerWarpCrossServerBroker disabled "
                    + "(packetManager or playerWarpService or proxyMessenger missing).");
        }

        // -------- Moderation listeners --------
        FreezeService freezeService = new FreezeService();
        getServer().getPluginManager().registerEvents(
                new fr.elias.oreoEssentials.listeners.FreezeListener(freezeService),
                this
        );
        VanishService vanishService = new VanishService(this);
        getServer().getPluginManager().registerEvents(
                new fr.elias.oreoEssentials.listeners.VanishListener(vanishService, this),
                this
        );

        // Track locations for /back + /deathback + god protection
        getServer().getPluginManager().registerEvents(
                new PlayerTrackingListener(backService),
                this
        );
        getServer().getPluginManager().registerEvents(
                new DeathBackListener(deathBackService),
                this
        );
        getServer().getPluginManager().registerEvents(
                new GodListener(godService),
                this
        );

            // -------- Portals --------
        this.portals = new fr.elias.oreoEssentials.portals.PortalsManager(this);
        getServer().getPluginManager().registerEvents(
                new fr.elias.oreoEssentials.portals.PortalsListener(this.portals),
                this
        );
        if (getCommand("portal") != null) {
            var portalCmd = new fr.elias.oreoEssentials.portals.PortalsCommand(this.portals);
            getCommand("portal").setExecutor(portalCmd);
            getCommand("portal").setTabCompleter(portalCmd);
        }

        // -------- JumpPads --------
        this.jumpPads = new fr.elias.oreoEssentials.jumpads.JumpPadsManager(this);
        getServer().getPluginManager().registerEvents(
                new fr.elias.oreoEssentials.jumpads.JumpPadsListener(this.jumpPads),
                this
        );
        if (getCommand("jumpad") != null) {
            var jumpCmd = new fr.elias.oreoEssentials.jumpads.JumpPadsCommand(this.jumpPads);
            getCommand("jumpad").setExecutor(jumpCmd);
            getCommand("jumpad").setTabCompleter(jumpCmd);
        }

        // --- PlayerVaults ---
        this.playerVaultsConfig = new fr.elias.oreoEssentials.playervaults.PlayerVaultsConfig(this);
        this.playervaultsService = new fr.elias.oreoEssentials.playervaults.PlayerVaultsService(this);
        if (this.playervaultsService.enabled()) {
            getLogger().info("[Vaults] PlayerVaults enabled.");
        } else {
            getLogger().info("[Vaults] PlayerVaults disabled by config or storage unavailable.");
        }
        // Initialize RTP config (constructor calls reload())
        // ---- RTP config (local + cross-server aware)
        this.rtpPendingService = new RtpPendingService();     // 1) data holder
        this.rtpConfig         = new RtpConfig(this);         // 2) load rtp.yml

        getServer().getPluginManager().registerEvents(        // 3) listener
                new RtpJoinListener(this),
                this
        );

        if (!settingsConfig.rtpEnabled()) {
            unregisterCommandHard("rtp");
            unregisterCommandHard("wild");
            getLogger().info("[RTP] Disabled by settings.yml.");
        } else if (!this.rtpConfig.isEnabled()) {
            unregisterCommandHard("rtp");
            unregisterCommandHard("wild");
            getLogger().info("[RTP] Disabled by rtp.yml (enabled=false).");
        } else {
            // Use a single instance for executor + tab-completer
            var rtpCmd = new fr.elias.oreoEssentials.commands.core.playercommands.RtpCommand();
            this.commands.register(rtpCmd);

            getLogger().info("[RTP] Enabled — /rtp registered with tab-completion.");
        }

        // RTP cross-server bridge (/rtp) ---
        // Only useful if RabbitMQ is up AND cross-server RTP is enabled in rtp.yml

        if (packetManager != null && packetManager.isInitialized()
                && this.rtpConfig != null && this.rtpConfig.isCrossServerEnabled()) {
            try {
                this.rtpBridge = new fr.elias.oreoEssentials.rtp.RtpCrossServerBridge(
                        this,
                        this.packetManager,
                        this.configService.serverName()
                );
                getLogger().info("[RTP] Cross-server RTP bridge ready (server=" + configService.serverName() + ").");
            } catch (Throwable t) {
                this.rtpBridge = null;
                getLogger().warning("[RTP] Failed to init cross-server RTP bridge: " + t.getMessage());
            }
        } else {
            this.rtpBridge = null;
            getLogger().info("[RTP] Cross-server RTP bridge disabled "
                    + "(PacketManager missing or cross-server RTP off).");
        }

        // --- BossBar (controlled by config: bossbar.enabled)
        if (settingsConfig.bossbarEnabled()) {
            this.bossBarService = new BossBarService(this);
            this.bossBarService.start();
            this.commands.register(new BossBarToggleCommand(this.bossBarService));
            getLogger().info("[BossBar] Enabled from settings.yml.");
        } else {
            unregisterCommandHard("bossbar");
            getLogger().info("[BossBar] Disabled by settings.yml.");
        }


        // --- Scoreboard (controlled by settings.yml: features.scoreboard.enabled)
        if (settingsConfig.scoreboardEnabled()) {
            ScoreboardConfig sbCfg = ScoreboardConfig.load(this);
            this.scoreboardService = new ScoreboardService(this, sbCfg);
            this.scoreboardService.start();
            this.commands.register(new ScoreboardToggleCommand(this.scoreboardService));
            getLogger().info("[Scoreboard] Enabled from settings.yml");
        } else {
            getLogger().info("[Scoreboard] Disabled via settings.yml");
            unregisterCommandHard("scoreboard");
            unregisterCommandHard("sb");
        }

        // --- TAB list (controlled by settings.yml: features.tab.enabled)
        if (settingsConfig.tabEnabled()) {
            this.tabListManager = new fr.elias.oreoEssentials.tab.TabListManager(this);
            this.tabListManager.start();
            getLogger().info("[TAB] Custom tab-list enabled via settings.yml.");
        } else {
            this.tabListManager = null;
            getLogger().info("[TAB] Disabled by settings.yml (features.tab.enabled=false).");
        }

        var tphere = new fr.elias.oreoEssentials.commands.core.admins.TphereCommand(this);
        this.commands.register(tphere);
        if (getCommand("tphere") != null) {
            getCommand("tphere").setTabCompleter(tphere);
        }

        var muteCmd   = new MuteCommand(muteService, chatSyncManager);
        var unmuteCmd = new UnmuteCommand(muteService, chatSyncManager);

        // Nick (has completer)
        var nickCmd = new fr.elias.oreoEssentials.commands.core.playercommands.NickCommand();
        this.commands.register(nickCmd);

        // --- AFK service
        var afkService = new fr.elias.oreoEssentials.services.AfkService();
        getServer().getPluginManager().registerEvents(
                new fr.elias.oreoEssentials.listeners.AfkListener(afkService),
                this
        );

        // --- Jails ---
        this.jailService = new fr.elias.oreoEssentials.jail.JailService(this);
        this.jailService.enable();
        getServer().getPluginManager().registerEvents(
                new fr.elias.oreoEssentials.jail.JailGuardListener(jailService),
                this
        );
        // --- Player Warps (/pw) ---
        PlayerWarpCommand pwCmd = null;
        if (this.playerWarpService != null) {
            pwCmd = new PlayerWarpCommand(this.playerWarpService);
        }

        // /pwwhitelist command for managing warp whitelists
        if (getCommand("pwwhitelist") != null && this.playerWarpService != null) {
            PlayerWarpWhitelistCommand pwwCmd = new PlayerWarpWhitelistCommand(this.playerWarpService);
            getCommand("pwwhitelist").setExecutor(pwwCmd);
            getCommand("pwwhitelist").setTabCompleter(pwwCmd);
            getLogger().info("[PlayerWarps] /pwwhitelist registered.");
        } else {
            getLogger().info("[PlayerWarps] /pwwhitelist not registered (command missing in plugin.yml or playerWarpService=null).");
        }

        // Registeration of all remaining commands
        this.commands
                .register(new SpawnCommand(spawnService)) //go to spawn command
                .register(new SetSpawnCommand(spawnService)) //set spawn command
                .register(new BackCommand(backService)) // go back command
                .register(new WarpCommand(warpService)) //go to warps
                .register(new SetWarpCommand(warpService)) //setwarps
                .register(new WarpsCommand(warpService))      // <- Player GUI /warps
                .register(new WarpsAdminCommand(warpService)) // <- Admin GUI /warpsadmin (was /warpsgui)                .
                .register(new HomeCommand(homeService)) //home command
                .register(new DelWarpCommand(warpService)) //delete warps
                .register(new SetHomeCommand(homeService, configService)) //sethome commamnd
                .register(new DelHomeCommand(homeService)) //delete home command
                .register(new TpaCommand(teleportService)) //tpa command
                .register(new TpAcceptCommand(teleportService)) //tpaacept command
                .register(new TpDenyCommand(teleportService)) //tpdeny command
                .register(new FlyCommand()) //fly command here
                .register(new HealCommand()) //heal command
                .register(new FeedCommand()) //feed command
                .register(new MsgCommand(messageService)) //msg command
                .register(new ReplyCommand(messageService)) //reply command
                .register(new BroadcastCommand()) //broadcast command
                .register(new HomesCommand(homeService)) //homes command
                .register(new HomesGuiCommand(homeService)) //homes gui command
                .register(new DeathBackCommand(deathBackService)) //deathback command
                .register(new GodCommand(godService)) //god command
                .register(new AfeliusReloadCommand(this, chatConfig)) //afelius reload command
                .register(new VanishCommand(vanishService)) // vanish command
                .register(new BanCommand()) //ban command
                .register(new KickCommand()) //kick command
                .register(new FreezeCommand(freezeService)) // freeze command
                .register(new EnchantCommand()) //enchant command
                .register(new DisenchantCommand()) //disenchant command
                .register(muteCmd) //mute command
                .register(new UnbanCommand()) //unban command
                .register(unmuteCmd) //unmute command
                .register(new OeCommand()) //oecommand
                .register(new ServerProxyCommand(proxyMessenger)) //server command
                .register(new SkinCommand()) //skin command ( not being used yet)
                .register(new CloneCommand()) //clone command ( not implemented fully yet )
                .register(new fr.elias.oreoEssentials.playersync.PlayerSyncCommand(this, playerSyncService, invSyncEnabled)) //player synch class
                .register(new fr.elias.oreoEssentials.commands.core.playercommands.EcCommand(this.ecService, crossServerEc)) //cross server enderchests
                .register(new HeadCommand()) //head command
                .register(new fr.elias.oreoEssentials.commands.core.playercommands.AfkCommand(afkService)) //afk command
                .register(new fr.elias.oreoEssentials.commands.core.playercommands.TrashCommand()) //trash command
                .register(new fr.elias.oreoEssentials.commands.core.playercommands.WorkbenchCommand()) //workbench command
                .register(new fr.elias.oreoEssentials.commands.core.playercommands.AnvilCommand()) //anvill command
                .register(new ClearCommand()) //clear commmand
                .register(new fr.elias.oreoEssentials.commands.core.playercommands.SeenCommand()) //seen command
                .register(new fr.elias.oreoEssentials.commands.core.playercommands.PingCommand())  //ping command
                .register(new fr.elias.oreoEssentials.commands.core.playercommands.HatCommand()) //hat command
                .register(new fr.elias.oreoEssentials.commands.core.playercommands.RealNameCommand()) //realname command
                .register(new fr.elias.oreoEssentials.commands.core.playercommands.FurnaceCommand()) //furnace command
                .register(new fr.elias.oreoEssentials.commands.core.playercommands.NearCommand()) //near command
                .register(new fr.elias.oreoEssentials.commands.core.playercommands.KillCommand()) //kill command
                .register(new fr.elias.oreoEssentials.commands.core.playercommands.InvseeCommand()) //invseee command
                .register(new fr.elias.oreoEssentials.commands.core.playercommands.CookCommand()) //cook command
                .register(new fr.elias.oreoEssentials.commands.ecocommands.BalanceCommand(this)) //balance command
                .register(new fr.elias.oreoEssentials.commands.ecocommands.BalTopCommand(this)) //baltop command
                .register(new fr.elias.oreoEssentials.commands.core.playercommands.EcSeeCommand()) //ecsee commmand
                .register(new fr.elias.oreoEssentials.commands.core.admins.ReloadAllCommand()) //reload all command
                .register(new fr.elias.oreoEssentials.commands.core.playercommands.VaultsCommand()) //vaults command
                .register(new fr.elias.oreoEssentials.commands.core.playercommands.UuidCommand())//uuid command
                .register(new TpCommand(teleportService))
                .register(new MoveCommand(teleportService)); // /move <player> [target]
        // Register /pw if service is available
        if (pwCmd != null) {
            this.commands.register(pwCmd);
        }
        // -------- Tab completion wiring --------
        if (getCommand("oeserver") != null) {
            getCommand("oeserver").setTabCompleter(new ServerProxyCommand(proxyMessenger));
        }
        getCommand("kick").setTabCompleter(new KickTabCompleter(this));
        // /clear & /ci cross-server tab completion
        if (getCommand("clear") != null) {
            getCommand("clear").setTabCompleter(new ClearTabCompleter(this));
        }


        // /tpa → only ONLINE (network-wide)
        TpaTabCompleter tpaCompleter = new TpaTabCompleter(this);
        if (getCommand("tpa") != null) {
            getCommand("tpa").setTabCompleter(tpaCompleter);
        }

        // /tp → ONLINE + OFFLINE (local + network)
        TpTabCompleter tpCompleter = new TpTabCompleter(this);
        if (getCommand("tp") != null) {
            getCommand("tp").setTabCompleter(tpCompleter);
        }

        // /move still uses online-only suggestion (same as /tpa)
        if (getCommand("move") != null) {
            getCommand("move").setTabCompleter(tpaCompleter);
        }


        if (getCommand("balance") != null) {
            getCommand("balance").setTabCompleter((sender, cmd, alias, args) -> {
                if (args.length == 1 && sender.hasPermission("oreo.balance.others")) {
                    String partial = args[0].toLowerCase(java.util.Locale.ROOT);
                    return org.bukkit.Bukkit.getOnlinePlayers().stream()
                            .map(org.bukkit.entity.Player::getName)
                            .filter(n -> n.toLowerCase(java.util.Locale.ROOT).startsWith(partial))
                            .sorted(String.CASE_INSENSITIVE_ORDER)
                            .toList();
                }
                return java.util.List.of();
            });
        }
        if (getCommand("otherhomes") != null) {
            var c = new fr.elias.oreoEssentials.commands.core.admins.OtherHomesListCommand(this, homeService);
            getCommand("otherhomes").setExecutor(c);
            getCommand("otherhomes").setTabCompleter(c);
        }
        if (getCommand("jail") != null)
            getCommand("jail").setExecutor(new fr.elias.oreoEssentials.jail.commands.JailCommand(jailService));
        if (getCommand("jailedit") != null)
            getCommand("jailedit").setExecutor(new fr.elias.oreoEssentials.jail.commands.JailEditCommand(jailService));
        if (getCommand("jaillist") != null)
            getCommand("jaillist").setExecutor(new fr.elias.oreoEssentials.jail.commands.JailListCommand(jailService));

        // /aliaseditor registration
        if (getCommand("aliaseditor") != null) {
            var aliasCmd = new fr.elias.oreoEssentials.aliases.AliasEditorCommand(aliasService, invManager);
            getCommand("aliaseditor").setExecutor(aliasCmd);
            getCommand("aliaseditor").setTabCompleter(aliasCmd);
        }

        if (getCommand("otherhome") != null) {
            var otherHome = new fr.elias.oreoEssentials.commands.core.admins.OtherHomeCommand(this, homeService);
            this.commands.register(otherHome); // uses CommandManager (OreoCommand)
            getCommand("otherhome").setTabCompleter(otherHome);
        }

        var visitorService = new fr.elias.oreoEssentials.services.VisitorService();
        getServer().getPluginManager().registerEvents(
                new fr.elias.oreoEssentials.listeners.VisitorGuardListener(visitorService),
                this
        );
        // Sit command + listener (fully controlled by settings.yml)
        if (settingsConfig.sitEnabled()) {
            this.commands.register(new SitCommand());
            getServer().getPluginManager().registerEvents(new SitListener(), this);
            getLogger().info("[Sit] Enabled — /sit dynamically registered.");
        } else {
            getLogger().info("[Sit] Disabled by settings.yml — /sit not registered at all.");
        }



        var gmCmd = new fr.elias.oreoEssentials.commands.core.admins.GamemodeCommand(visitorService);
        this.getCommands().register(gmCmd);
        if (getCommand("gamemode") != null) {
            getCommand("gamemode").setTabCompleter(gmCmd);
        }

        if (getCommand("skin") != null)
            getCommand("skin").setTabCompleter(new SkinCommand());
        if (getCommand("clone") != null)
            getCommand("clone").setTabCompleter(new CloneCommand());
        if (getCommand("head") != null)
            getCommand("head").setTabCompleter(new HeadCommand());
        if (getCommand("home") != null)
            getCommand("home").setTabCompleter(new HomeTabCompleter(homeService));
        if (getCommand("warp") != null)
            getCommand("warp").setTabCompleter(new WarpTabCompleter(warpService));
        if (getCommand("enchant") != null)
            getCommand("enchant").setTabCompleter(new fr.elias.oreoEssentials.commands.completion.EnchantTabCompleter());
        if (getCommand("warp") != null)
            getCommand("warp").setTabCompleter(new WarpTabCompleter(warpService));
        if (getCommand("pw") != null && this.playerWarpService != null) {
            getCommand("pw").setTabCompleter(new PlayerWarpTabCompleter(this.playerWarpService));
        }

        if (getCommand("enchant") != null)
            getCommand("enchant").setTabCompleter(new fr.elias.oreoEssentials.commands.completion.EnchantTabCompleter());

        if (getCommand("disenchant") != null)
            getCommand("disenchant").setTabCompleter(
                    new fr.elias.oreoEssentials.commands.completion.EnchantTabCompleter()
            );
        if (getCommand("mute") != null)
            getCommand("mute").setTabCompleter(muteCmd);
        if (getCommand("unban") != null)
            getCommand("unban").setTabCompleter(new UnbanCommand());
        if (getCommand("nick") != null)
            getCommand("nick").setTabCompleter(nickCmd);
        if (getCommand("unmute") != null)
            getCommand("unmute").setTabCompleter(unmuteCmd);
        if (getCommand("invsee") != null)
            getCommand("invsee").setTabCompleter(
                    new fr.elias.oreoEssentials.commands.core.playercommands.InvseeCommand()
            );
        if (getCommand("ecsee") != null)
            getCommand("ecsee").setTabCompleter(
                    new fr.elias.oreoEssentials.commands.core.playercommands.EcSeeCommand()
            );

        getCommand("effectme").setExecutor(new fr.elias.oreoEssentials.effects.EffectCommands());
        getCommand("effectme").setTabCompleter(new fr.elias.oreoEssentials.effects.EffectCommands());
        getCommand("effectto").setExecutor(new fr.elias.oreoEssentials.effects.EffectCommands());
        getCommand("effectto").setTabCompleter(new fr.elias.oreoEssentials.effects.EffectCommands());

        final fr.elias.oreoEssentials.mobs.SpawnMobCommand spawnCmd = new fr.elias.oreoEssentials.mobs.SpawnMobCommand();
        getCommand("spawnmob").setExecutor(spawnCmd);
        getCommand("spawnmob").setTabCompleter(spawnCmd);

        final fr.elias.oreoEssentials.commands.core.admins.FlySpeedCommand fs =
                new fr.elias.oreoEssentials.commands.core.admins.FlySpeedCommand();
        getCommand("flyspeed").setExecutor(fs);
        getCommand("flyspeed").setTabCompleter(fs);

        final var worldCmd = new fr.elias.oreoEssentials.commands.core.admins.WorldTeleportCommand();
        getCommand("world").setExecutor(worldCmd);
        getCommand("world").setTabCompleter(worldCmd);

        this.icManager = new fr.elias.oreoEssentials.ic.ICManager(getDataFolder());
        fr.elias.oreoEssentials.ic.ICCommand icCmd = new fr.elias.oreoEssentials.ic.ICCommand(icManager);
        getCommand("ic").setExecutor(icCmd);
        getCommand("ic").setTabCompleter(icCmd);
        getServer().getPluginManager().registerEvents(
                new fr.elias.oreoEssentials.ic.ICListener(icManager),
                this
        );

        {
            final var timeCmd = new fr.elias.oreoEssentials.commands.core.admins.OeTimeCommand();
            getCommand("oetime").setExecutor(timeCmd);
            getCommand("oetime").setTabCompleter(timeCmd);

            final var weatherCmd = new fr.elias.oreoEssentials.commands.core.admins.WeatherCommand();
            getCommand("weather").setExecutor(weatherCmd);
            getCommand("weather").setTabCompleter(weatherCmd);
            getCommand("sun").setExecutor(weatherCmd);
            getCommand("sun").setTabCompleter(weatherCmd);
            getCommand("rain").setExecutor(weatherCmd);
            getCommand("rain").setTabCompleter(weatherCmd);
            getCommand("storm").setExecutor(weatherCmd);
            getCommand("storm").setTabCompleter(weatherCmd);
        }

        // --- Event system ---
        this.eventConfig   = new fr.elias.oreoEssentials.events.EventConfig(getDataFolder());
        this.deathMessages = new fr.elias.oreoEssentials.events.DeathMessageService(getDataFolder());

        // --- Playtime (per-server) + Rewards
// --- Playtime (per-server) + Rewards
        this.playtimeTracker = new fr.elias.oreoEssentials.playtime.PlaytimeTracker(this);

        this.playtimeRewards = new fr.elias.oreoEssentials.playtime.PlaytimeRewardsService(
                this,
                playtimeTracker
        );

        // Let the service load its own file first
        this.playtimeRewards.init();

        // Now override with settings.yml toggle
        if (!settingsConfig.playtimeRewardsEnabled()) {
            // turns it off (cancels tasks + listeners and writes settings.enable=false)
            this.playtimeRewards.setEnabled(false);
            getLogger().info("[Prewards] Disabled by settings.yml (playtime-rewards.enabled=false).");
        } else {
            getLogger().info("[Prewards] Enabled by settings.yml.");
        }

        var prewardsCmd = new fr.elias.oreoEssentials.playtime.PrewardsCommand(
                this,
                this.playtimeRewards
        );
        if (getCommand("prewards") != null) {
            getCommand("prewards").setExecutor(prewardsCmd);
            getCommand("prewards").setTabCompleter(prewardsCmd);
        } else {
            getLogger().warning("[Prewards] Command 'prewards' not found in plugin.yml; skipping registration.");
        }

        // Playtime cmd
        var playtimeCmd = new fr.elias.oreoEssentials.commands.core.playercommands.PlaytimeCommand();
        if (getCommand("playtime") != null) {
            getCommand("playtime").setExecutor(playtimeCmd);
            getCommand("playtime").setTabCompleter(playtimeCmd);
        } else {
            getLogger().warning("[Playtime] Command 'playtime' not found in plugin.yml; skipping registration.");
        }

        var eventEngine = new fr.elias.oreoEssentials.events.EventEngine(eventConfig, deathMessages);
        getServer().getPluginManager().registerEvents(eventEngine, this);

        var eventCmd = new fr.elias.oreoEssentials.events.EventCommands(eventConfig, deathMessages);
        if (getCommand("oevents") != null) {
            getCommand("oevents").setExecutor(eventCmd);
            getCommand("oevents").setTabCompleter(eventCmd);
        }

// --- OreoHolograms (NEW MODULE, settings toggle) ---
        if (settingsConfig.oreoHologramsEnabled()) {
            try {
                // sanity check: requires Paper/Folia Display entities
                try {
                    Class.forName("org.bukkit.entity.Display");
                } catch (ClassNotFoundException x) {
                    getLogger().warning("[OreoHolograms] Display entities not available on this server. Requires Paper/Folia.");
                    throw x;
                }

                this.oreoHolograms = new fr.elias.oreoEssentials.holograms.OreoHolograms(this);
                this.oreoHolograms.load(); // loads & respawns holograms

                // Command registration
                fr.elias.oreoEssentials.holograms.OreoHologramCommand holoCmd =
                        new fr.elias.oreoEssentials.holograms.OreoHologramCommand(this.oreoHolograms);

                boolean registered = false;
                if (getCommand("ohologram") != null) {
                    getCommand("ohologram").setExecutor(holoCmd);
                    getCommand("ohologram").setTabCompleter(holoCmd);
                    registered = true;
                }
                if (getCommand("hologram") != null) {
                    getCommand("hologram").setExecutor(holoCmd);
                    getCommand("hologram").setTabCompleter(holoCmd);
                    registered = true;
                }
                if (!registered) {
                    getLogger().warning("[OreoHolograms] No command entry found. Add ohologram or hologram in plugin.yml.");
                }

                // Tick update
                Bukkit.getScheduler().runTaskTimer(
                        this,
                        () -> {
                            try { this.oreoHolograms.tickAll(); } catch (Throwable ignored) {}
                        },
                        20L, 20L
                ); // every 1 second

                getLogger().info("[OreoHolograms] Enabled from settings.yml.");
            } catch (Throwable t) {
                this.oreoHolograms = null;
                getLogger().warning("[OreoHolograms] Failed to initialize: " + t.getMessage());
            }
        } else {
            // Fully disabled
            unregisterCommandHard("ohologram");
            unregisterCommandHard("hologram");
            this.oreoHolograms = null;
            getLogger().info("[OreoHolograms] Disabled by settings.yml.");
        }


        // -------- PlaceholderAPI hook (optional; reflection) --------
        tryRegisterPlaceholderAPI();

        getLogger().info("OreoEssentials enabled.");
    }


    public fr.elias.oreoEssentials.playervaults.PlayerVaultsService getPlayerVaultsService() {
        return playervaultsService;
    }

    private void registerAllPacketsDeterministically(PacketManager pm) {
        // --- Core/Generic packets ---

        pm.registerPacket(
                fr.elias.oreoEssentials.rabbitmq.packet.impl.SendRemoteMessagePacket.class,
                fr.elias.oreoEssentials.rabbitmq.packet.impl.SendRemoteMessagePacket::new
        );
        pm.registerPacket(
                fr.elias.oreoEssentials.rabbitmq.packet.impl.PlayerJoinPacket.class,
                fr.elias.oreoEssentials.rabbitmq.packet.impl.PlayerJoinPacket::new
        );
        pm.registerPacket(
                fr.elias.oreoEssentials.rabbitmq.packet.impl.PlayerQuitPacket.class,
                fr.elias.oreoEssentials.rabbitmq.packet.impl.PlayerQuitPacket::new
        );
        pm.registerPacket(
                fr.elias.oreoEssentials.rabbitmq.packet.impl.PlayerWarpTeleportRequestPacket.class,
                fr.elias.oreoEssentials.rabbitmq.packet.impl.PlayerWarpTeleportRequestPacket::new
        );
        pm.registerPacket(
                fr.elias.oreoEssentials.rtp.RtpTeleportRequestPacket.class,
                fr.elias.oreoEssentials.rtp.RtpTeleportRequestPacket::new
        );
        pm.registerPacket(
                fr.elias.oreoEssentials.cross.InvseeOpenRequestPacket.class,
                fr.elias.oreoEssentials.cross.InvseeOpenRequestPacket::new
        );
        pm.registerPacket(
                fr.elias.oreoEssentials.cross.InvseeStatePacket.class,
                fr.elias.oreoEssentials.cross.InvseeStatePacket::new
        );
        pm.registerPacket(
                fr.elias.oreoEssentials.cross.InvseeEditPacket.class,
                fr.elias.oreoEssentials.cross.InvseeEditPacket::new
        );


        // --- Trade packets ---
        pm.registerPacket(
                fr.elias.oreoEssentials.rabbitmq.packet.impl.trade.TradeStartPacket.class,
                fr.elias.oreoEssentials.rabbitmq.packet.impl.trade.TradeStartPacket::new
        );
        pm.registerPacket(
                fr.elias.oreoEssentials.rabbitmq.packet.impl.trade.TradeInvitePacket.class,
                fr.elias.oreoEssentials.rabbitmq.packet.impl.trade.TradeInvitePacket::new
        );
        pm.registerPacket(
                fr.elias.oreoEssentials.rabbitmq.packet.impl.trade.TradeStatePacket.class,
                fr.elias.oreoEssentials.rabbitmq.packet.impl.trade.TradeStatePacket::new
        );
        pm.registerPacket(
                fr.elias.oreoEssentials.rabbitmq.packet.impl.trade.TradeConfirmPacket.class,
                fr.elias.oreoEssentials.rabbitmq.packet.impl.trade.TradeConfirmPacket::new
        );
        pm.registerPacket(
                fr.elias.oreoEssentials.rabbitmq.packet.impl.trade.TradeCancelPacket.class,
                fr.elias.oreoEssentials.rabbitmq.packet.impl.trade.TradeCancelPacket::new
        );
        pm.registerPacket(
                fr.elias.oreoEssentials.rabbitmq.packet.impl.trade.TradeGrantPacket.class,
                fr.elias.oreoEssentials.rabbitmq.packet.impl.trade.TradeGrantPacket::new
        );
        pm.registerPacket(
                fr.elias.oreoEssentials.rabbitmq.packet.impl.trade.TradeClosePacket.class,
                fr.elias.oreoEssentials.rabbitmq.packet.impl.trade.TradeClosePacket::new
        );

        // --- TP / TPA packets ---
        pm.registerPacket(
                fr.elias.oreoEssentials.rabbitmq.packet.impl.tp.TpJumpPacket.class,
                fr.elias.oreoEssentials.rabbitmq.packet.impl.tp.TpJumpPacket::new
        );
        pm.registerPacket(
                fr.elias.oreoEssentials.rabbitmq.packet.impl.TpaBringPacket.class,
                fr.elias.oreoEssentials.rabbitmq.packet.impl.TpaBringPacket::new
        );
        pm.registerPacket(
                fr.elias.oreoEssentials.rabbitmq.packet.impl.TpaRequestPacket.class,
                fr.elias.oreoEssentials.rabbitmq.packet.impl.TpaRequestPacket::new
        );
        pm.registerPacket(
                fr.elias.oreoEssentials.rabbitmq.packet.impl.TpaSummonPacket.class,
                fr.elias.oreoEssentials.rabbitmq.packet.impl.TpaSummonPacket::new
        );
        pm.registerPacket(
                fr.elias.oreoEssentials.rabbitmq.packet.impl.TpaAcceptPacket.class,
                fr.elias.oreoEssentials.rabbitmq.packet.impl.TpaAcceptPacket::new
        );
    }




    public boolean isMessagingAvailable() {
        return packetManager != null && packetManager.isInitialized();
    }

    @Override
    public void onDisable() {
        getLogger().info("[SHUTDOWN] OreoEssentials disabling…");

        // -------------------------------------------------
        // 1) Sauvegarder tous les inventaires en ligne
        //    (très important avec Velocity / restart brutal)
        // -------------------------------------------------
        try {
            fr.elias.oreoEssentials.services.InventoryService invSvc =
                    org.bukkit.Bukkit.getServicesManager().load(
                            fr.elias.oreoEssentials.services.InventoryService.class
                    );

            if (invSvc != null) {
                int online = org.bukkit.Bukkit.getOnlinePlayers().size();
                getLogger().info("[SHUTDOWN] Saving inventories of " + online + " online players...");

                for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
                    try {
                        fr.elias.oreoEssentials.services.InventoryService.Snapshot snap =
                                new fr.elias.oreoEssentials.services.InventoryService.Snapshot();

                        snap.contents = p.getInventory().getContents();
                        snap.armor    = p.getInventory().getArmorContents();
                        snap.offhand  = p.getInventory().getItemInOffHand();

                        invSvc.save(p.getUniqueId(), snap);
                        getLogger().info("[SHUTDOWN] Saved inventory for " + p.getName());
                    } catch (Exception ex) {
                        getLogger().warning("[SHUTDOWN] Failed to save inventory for "
                                + p.getName() + ": " + ex.getMessage());
                    }
                }
            } else {
                getLogger().info("[SHUTDOWN] No InventoryService registered; skipping inventory save.");
            }
        } catch (Throwable t) {
            getLogger().warning("[SHUTDOWN] Error while saving inventories on shutdown: " + t.getMessage());
        }

        // -------------------------------------------------
        // 2) Cancel des tâches et shutdown des services
        // -------------------------------------------------
        org.bukkit.Bukkit.getScheduler().cancelTasks(this);

        try { if (teleportService != null) teleportService.shutdown(); } catch (Exception ignored) {}
        try { if (storage != null) { storage.flush(); storage.close(); } } catch (Exception ignored) {}
        try { if (database != null) database.close(); } catch (Exception ignored) {}
        try { if (packetManager != null) packetManager.close(); } catch (Exception ignored) {}
        try { if (ecoBootstrap != null) ecoBootstrap.disable(); } catch (Exception ignored) {}
        try { if (chatSyncManager != null) chatSyncManager.close(); } catch (Exception ignored) {}
        try { if (tabListManager != null) tabListManager.stop(); } catch (Exception ignored) {}
        try { if (kitsManager != null) kitsManager.saveData(); } catch (Exception ignored) {}
        try { if (scoreboardService != null) scoreboardService.stop(); } catch (Exception ignored) {}
        try { if (this.homesMongoClient != null) this.homesMongoClient.close(); } catch (Exception ignored) {}
        this.homesMongoClient = null;
        try { if (bossBarService != null) bossBarService.stop(); } catch (Exception ignored) {}
        try { if (playervaultsService != null) playervaultsService.stop(); } catch (Exception ignored) {}
        try { if (aliasService != null) aliasService.shutdown(); } catch (Exception ignored) {}
        try { if (jailService != null) jailService.disable(); } catch (Exception ignored) {}
        try { if (oreoHolograms != null) oreoHolograms.unload(); } catch (Exception ignored) {}
        try { if (dailyStore != null) dailyStore.close(); } catch (Exception ignored) {}
        try { if (tradeService != null) tradeService.cancelAll(); } catch (Throwable ignored) {}

        dailyStore = null;
        this.healthBarListener = null;

        getLogger().info("OreoEssentials disabled.");
    }



    /* ----------------------------- Helpers ----------------------------- */

    /** Optional PlaceholderAPI hook using reflection. */
    private void tryRegisterPlaceholderAPI() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            getLogger().info("PlaceholderAPI not found; skipping placeholders.");
            return;
        }
        try {
            Class<?> hookCls = Class.forName("fr.elias.oreoEssentials.PlaceholderAPIHook");
            Object hook = hookCls.getConstructor(OreoEssentials.class).newInstance(this);
            hookCls.getMethod("register").invoke(hook);
            getLogger().info("PlaceholderAPI placeholders registered.");
        } catch (ClassNotFoundException e) {
            getLogger().warning("PlaceholderAPIHook class not found; skipping placeholders.");
        } catch (Throwable t) {
            getLogger().warning("Failed to register PlaceholderAPI placeholders: " + t.getMessage());
        }
    }

    /* ----------------------------- Getters ----------------------------- */
    public IpTracker getIpTracker() { return ipTracker; }
    public FreezeManager getFreezeManager() { return freezeManager; }
    public PlayerNotesManager getNotesManager() { return notesManager; }
    public NotesChatListener getNotesChat() { return notesChat; }
    public ConfigService getConfigService() { return configService; }
    public StorageApi getStorage() { return storage; }
    public SpawnService getSpawnService() { return spawnService; }
    public WarpService getWarpService() { return warpService; }
    public HomeService getHomeService() { return homeService; }
    public PlayerWarpService getPlayerWarpService() { return playerWarpService; }
    public PlayerWarpDirectory getPlayerWarpDirectory() { return playerWarpDirectory; }

    public TeleportService getTeleportService() { return teleportService; }
    public BackService getBackService() { return backService; }
    public MessageService getMessageService() { return messageService; }
    public DeathBackService getDeathBackService() { return deathBackService; }
    public GodService getGodService() { return godService; }
    public CommandManager getCommands() { return commands; }
    public ChatSyncManager getChatSyncManager() { return chatSyncManager; }
    public fr.elias.oreoEssentials.mobs.HealthBarListener getHealthBarListener() { return healthBarListener; }


    public fr.elias.oreoEssentials.chat.CustomConfig getChatConfig() { return chatConfig; }

    public WarpDirectory getWarpDirectory() { return warpDirectory; }
    public SpawnDirectory getSpawnDirectory() { return spawnDirectory; }
    private fr.elias.oreoEssentials.playerdirectory.PlayerDirectory playerDirectory;
    public fr.elias.oreoEssentials.playerdirectory.PlayerDirectory getPlayerDirectory() { return playerDirectory; }
    public TeleportBroker getTeleportBroker() { return teleportBroker; }
    public RedisManager getRedis() { return redis; }
    public OfflinePlayerCache getOfflinePlayerCache() {
        if (offlinePlayerCache == null) offlinePlayerCache = new OfflinePlayerCache();
        return offlinePlayerCache;
    }
    public PlayerEconomyDatabase getDatabase() { return database; }
    public PacketManager getPacketManager() { return packetManager; }
    public ScoreboardService getScoreboardService() { return scoreboardService; }
    public fr.elias.oreoEssentials.homes.HomeTeleportBroker getHomeTeleportBroker() {
        return homeTpBroker;
    }
    @SuppressWarnings("unchecked")
    private void unregisterCommandHard(String label) {
        try {
            // Get the command map
            org.bukkit.plugin.SimplePluginManager spm =
                    (org.bukkit.plugin.SimplePluginManager) getServer().getPluginManager();
            java.lang.reflect.Field f = org.bukkit.plugin.SimplePluginManager.class.getDeclaredField("commandMap");
            f.setAccessible(true);
            org.bukkit.command.SimpleCommandMap map = (org.bukkit.command.SimpleCommandMap) f.get(spm);

            // Known commands map
            java.lang.reflect.Field f2 = org.bukkit.command.SimpleCommandMap.class.getDeclaredField("knownCommands");
            f2.setAccessible(true);
            java.util.Map<String, org.bukkit.command.Command> known =
                    (java.util.Map<String, org.bukkit.command.Command>) f2.get(map);

            // remove plain and namespaced aliases that point to us
            String lower = label.toLowerCase(java.util.Locale.ROOT);
            known.entrySet().removeIf(e -> {
                String k = e.getKey().toLowerCase(java.util.Locale.ROOT);
                if (!k.equals(lower) && !k.endsWith(":" + lower)) return false;
                org.bukkit.command.Command cmd = e.getValue();
                org.bukkit.plugin.Plugin owner = null;
                try {
                    java.lang.reflect.Field fc = org.bukkit.command.PluginCommand.class.getDeclaredField("owningPlugin");
                    fc.setAccessible(true);
                    if (cmd instanceof org.bukkit.command.PluginCommand pc) {
                        owner = (org.bukkit.plugin.Plugin) fc.get(pc);
                    }
                } catch (Throwable ignored) {}
                return owner == this;
            });
        } catch (Throwable ignored) {
        }
    }
    // Playtime Rewards service getter (name expected by PlaceholderAPIHook)
    public fr.elias.oreoEssentials.playtime.PlaytimeRewardsService getPlaytimeRewardsService() {
        return this.playtimeRewards;
    }
    /**
     * Safe helper for logging / cross-server tags.
     * Tries configService.serverName(), then Bukkit server name, then "UNKNOWN".
     */
    public String getServerNameSafe() {
        try {
            if (configService != null) {
                String name = configService.serverName();
                if (name != null && !name.isBlank()) {
                    return name;
                }
            }
        } catch (Throwable ignored) {
        }

        try {
            String bukkitName = getServer().getName();
            if (bukkitName != null && !bukkitName.isBlank()) {
                return bukkitName;
            }
        } catch (Throwable ignored) {
        }

        return "UNKNOWN";
    }

    public ModBridge getModBridge() {
        return modBridge;
    }
    public java.util.Map<java.util.UUID, Long> getRtpCooldownCache() {
        return rtpCooldownCache;
    }
    public fr.elias.oreoEssentials.playtime.PlaytimeTracker getPlaytimeTracker() {
        return this.playtimeTracker;
    }
    public SettingsConfig getSettings() {
        return settings;
    }
    public RtpPendingService getRtpPendingService() {
        return rtpPendingService;
    }
    public fr.elias.oreoEssentials.rtp.RtpCrossServerBridge getRtpBridge() {
        return rtpBridge;
    }

    public EconomyBootstrap getEcoBootstrap() { return ecoBootstrap; }
    public Economy getVaultEconomy() { return vaultEconomy; }
}

