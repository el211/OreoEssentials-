package fr.elias.oreoEssentials;

import com.google.gson.Gson;
import fr.elias.oreoEssentials.bossbar.BossBarService;
import fr.elias.oreoEssentials.bossbar.BossBarToggleCommand;
import fr.elias.oreoEssentials.clearlag.ClearLagManager;
import fr.elias.oreoEssentials.commands.CommandManager;
import fr.elias.oreoEssentials.commands.core.moderation.freeze.FreezeCommand;
import fr.elias.oreoEssentials.commands.core.playercommands.back.BackBroker;
import fr.elias.oreoEssentials.commands.core.playercommands.back.BackCommand;
import fr.elias.oreoEssentials.commands.core.playercommands.back.BackLocation;
import fr.elias.oreoEssentials.commands.core.playercommands.back.BackService;
import fr.elias.oreoEssentials.commands.core.playercommands.back.BackJoinListener;
import fr.elias.oreoEssentials.commandtoggle.CommandToggleConfig;
import fr.elias.oreoEssentials.commandtoggle.CommandToggleListener;
import fr.elias.oreoEssentials.commandtoggle.CommandToggleService;
import fr.elias.oreoEssentials.cross.InvlookListener;
import fr.elias.oreoEssentials.cross.InvlookManager;
import fr.elias.oreoEssentials.currency.CurrencyConfig;
import fr.elias.oreoEssentials.currency.CurrencyService;
import fr.elias.oreoEssentials.currency.commands.CurrencyBalanceCommand;
import fr.elias.oreoEssentials.currency.commands.CurrencyCommand;
import fr.elias.oreoEssentials.currency.commands.CurrencySendCommand;
import fr.elias.oreoEssentials.currency.commands.CurrencyTopCommand;
import fr.elias.oreoEssentials.currency.placeholders.CurrencyPlaceholderExpansion;
import fr.elias.oreoEssentials.currency.rabbitmq.CurrencyPacketNamespace;
import fr.elias.oreoEssentials.commandtoggle.CommandToggleCommand;
import fr.elias.oreoEssentials.currency.storage.CurrencyStorage;
import fr.elias.oreoEssentials.currency.storage.JsonCurrencyStorage;
import fr.elias.oreoEssentials.currency.storage.MongoCurrencyStorage;
import fr.elias.oreoEssentials.migration.commands.ZEssentialsHomesImportCommand;
import fr.elias.oreoEssentials.nametag.PlayerNametagManager;
import fr.elias.oreoEssentials.playerwarp.*;
import fr.elias.oreoEssentials.playerwarp.command.PlayerWarpCommand;
import fr.elias.oreoEssentials.playerwarp.command.PlayerWarpTabCompleter;
import fr.elias.oreoEssentials.rtp.listeners.RtpJoinListener;
import org.bukkit.event.Listener;
import fr.elias.oreoEssentials.currency.commands.CurrencyBalanceTabCompleter;
import fr.elias.oreoEssentials.currency.commands.CurrencyTopTabCompleter;
import fr.elias.oreoEssentials.currency.commands.CurrencySendTabCompleter;
import fr.elias.oreoEssentials.currency.commands.CurrencyCommandTabCompleter;

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
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;
import org.bstats.charts.AdvancedPie;
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

import fr.elias.oreoEssentials.commands.ecocommands.MoneyCommand;
import fr.elias.oreoEssentials.commands.ecocommands.completion.MoneyTabCompleter;
import fr.elias.oreoEssentials.rtp.RtpConfig;

import fr.elias.oreoEssentials.database.JsonEconomyDatabase;
import fr.elias.oreoEssentials.database.MongoDBManager;
import fr.elias.oreoEssentials.database.PlayerEconomyDatabase;
import fr.elias.oreoEssentials.database.PostgreSQLManager;
import fr.elias.oreoEssentials.database.RedisManager;

import fr.elias.oreoEssentials.economy.EconomyBootstrap;

import fr.elias.oreoEssentials.listeners.*;

import fr.elias.oreoEssentials.offline.OfflinePlayerCache;
import  fr.elias.oreoEssentials.cross.ModBridge ;

import fr.elias.oreoEssentials.rabbitmq.PacketChannels;
import fr.elias.oreoEssentials.rabbitmq.handler.PlayerJoinPacketHandler;
import fr.elias.oreoEssentials.rabbitmq.handler.PlayerQuitPacketHandler;
import fr.elias.oreoEssentials.rabbitmq.handler.RemoteMessagePacketHandler;
import fr.elias.oreoEssentials.rabbitmq.sender.RabbitMQSender;

import fr.minuskube.inv.InventoryManager;

import fr.elias.oreoEssentials.chat.AsyncChatListener;
import fr.elias.oreoEssentials.chat.CustomConfig;
import fr.elias.oreoEssentials.chat.FormatManager;
import fr.elias.oreoEssentials.chat.ChatSyncManager;

import fr.elias.oreoEssentials.util.ProxyMessenger;
import fr.elias.oreoEssentials.vault.VaultEconomyProvider;
import net.milkbowl.vault.economy.Economy;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class OreoEssentials extends JavaPlugin {
    private Metrics metrics;
    private static OreoEssentials instance;
    public static OreoEssentials get() { return instance; }
    private MuteService muteService;
    private fr.elias.oreoEssentials.shards.OreoShardsModule shardsModule;
    public MuteService getMuteService() { return muteService; }
    private MongoClient homesMongoClient;
    private fr.elias.oreoEssentials.config.SettingsConfig settingsConfig;
    public fr.elias.oreoEssentials.config.SettingsConfig getSettingsConfig() { return settingsConfig; }
    private PlayerNotesManager notesManager;
    private NotesChatListener notesChat;
    private fr.elias.oreoEssentials.daily.DailyMongoStore dailyStore;
    private FreezeManager freezeManager;
    private Economy economy;
    private EconomyBootstrap ecoBootstrap;
    private fr.elias.oreoEssentials.integration.DiscordModerationNotifier discordMod;
    public fr.elias.oreoEssentials.integration.DiscordModerationNotifier getDiscordMod() { return discordMod; }
    private fr.elias.oreoEssentials.services.HomeDirectory homeDirectory;
    private fr.elias.oreoEssentials.teleport.TpCrossServerBroker tpBroker;
    public fr.elias.oreoEssentials.teleport.TpCrossServerBroker getTpBroker() { return tpBroker; }
    private fr.elias.oreoEssentials.portals.PortalsManager portals;
    private fr.elias.oreoEssentials.jumpads.JumpPadsManager jumpPads;
    private fr.elias.oreoEssentials.playervaults.PlayerVaultsConfig playerVaultsConfig;
    private fr.elias.oreoEssentials.modgui.ModGuiService modGuiService;
    public fr.elias.oreoEssentials.modgui.ModGuiService getModGuiService() { return modGuiService; }
    private fr.elias.oreoEssentials.tempfly.TempFlyService tempFlyService;
    private fr.elias.oreoEssentials.tempfly.TempFlyConfig tempFlyConfig;
    private fr.elias.oreoEssentials.chat.channels.ChatChannelManager channelManager;
    private BackBroker backBroker;
    private PlaceholderAPIHook placeholderHook;
    private CurrencyService currencyService;
    private CurrencyConfig currencyConfig;
    private CommandToggleConfig commandToggleConfig;
    private CommandToggleService commandToggleService;

    public CurrencyService getCurrencyService() { return currencyService; }
    public CurrencyConfig getCurrencyConfig() { return currencyConfig; }
    private Map<UUID, BackLocation> pendingBackTeleports = new ConcurrentHashMap<>();
    public fr.elias.oreoEssentials.chat.channels.ChatChannelManager getChannelManager() {
        return channelManager;
    }
    public fr.elias.oreoEssentials.tempfly.TempFlyService getTempFlyService() {
        return tempFlyService;
    }
    private fr.elias.oreoEssentials.holograms.perplayer_nms.PerPlayerTextDisplayService perPlayerTextDisplayService;
    public fr.elias.oreoEssentials.holograms.perplayer_nms.PerPlayerTextDisplayService getPerPlayerTextDisplayService() {
        return perPlayerTextDisplayService;
    }

    private ConfigService configService;
    private StorageApi storage;
    private SpawnService spawnService;
    private InventoryManager invManager;
    public InventoryManager getInvManager() { return invManager; }
    public InventoryManager getInventoryManager() {
        return invManager;
    }
    private AutoRebootService autoRebootService;
    public AutoRebootService getAutoRebootService() { return autoRebootService; }

    private fr.elias.oreoEssentials.sellgui.SellGuiManager sellGuiManager;
    public fr.elias.oreoEssentials.sellgui.SellGuiManager getSellGuiManager() { return sellGuiManager; }

    private ProxyMessenger proxyMessenger;
    public ProxyMessenger getProxyMessenger() { return proxyMessenger; }
    private WarpService warpService;
    private HomeService homeService;
    private TeleportService teleportService;
    private PlayerWarpService playerWarpService;
    private PlayerWarpDirectory playerWarpDirectory;
    private BackService backService;
    private MessageService messageService;
    private DeathBackService deathBackService;
    private GodService godService;
    private CommandManager commands;
    private fr.elias.oreoEssentials.teleport.TpaCrossServerBroker tpaBroker;
    public fr.elias.oreoEssentials.teleport.TpaCrossServerBroker getTpaBroker() { return tpaBroker; }
    private FreezeService freezeService;

    private fr.elias.oreoEssentials.kits.KitsManager kitsManager;
    private fr.elias.oreoEssentials.tab.TabListManager tabListManager;
    private WarpDirectory warpDirectory;
    private SpawnDirectory spawnDirectory;
    private TeleportBroker teleportBroker;
    public fr.elias.oreoEssentials.kits.KitsManager getKitsManager() { return kitsManager; }
    public fr.elias.oreoEssentials.tab.TabListManager getTabListManager() { return tabListManager; }
    private fr.elias.oreoEssentials.bossbar.BossBarService bossBarService;
    public fr.elias.oreoEssentials.bossbar.BossBarService getBossBarService() { return bossBarService; }
    private ModBridge modBridge;

    private PlayerEconomyDatabase database;
    private RedisManager redis;
    private OfflinePlayerCache offlinePlayerCache;
    private RtpPendingService rtpPendingService;

    private Economy vaultEconomy;
    private fr.elias.oreoEssentials.playervaults.PlayerVaultsService playervaultsService;
    public fr.elias.oreoEssentials.playervaults.PlayerVaultsService getPlayervaultsService() { return playervaultsService; }
    private fr.elias.oreoEssentials.cross.InvseeService invseeService;
    private fr.elias.oreoEssentials.cross.InvseeCrossServerBroker invseeBroker;

    public fr.elias.oreoEssentials.cross.InvseeService getInvseeService() {
        return invseeService;
    }

    private KillallLogger killallLogger;
    private fr.elias.oreoEssentials.ic.ICManager icManager;
    public fr.elias.oreoEssentials.ic.ICManager getIcManager() { return icManager; }
    private fr.elias.oreoEssentials.events.EventConfig eventConfig;
    private fr.elias.oreoEssentials.events.DeathMessageService deathMessages;
    private fr.elias.oreoEssentials.playtime.PlaytimeRewardsService playtimeRewards;
    public fr.elias.oreoEssentials.playtime.PlaytimeRewardsService getPlaytimeRewards() { return playtimeRewards; }
    private IpTracker ipTracker;
    private SettingsConfig settings;

    private boolean economyEnabled;
    private boolean redisEnabled;
    private boolean rabbitEnabled;
    private fr.elias.oreoEssentials.trade.TradeCrossServerBroker tradeBroker;
    public fr.elias.oreoEssentials.trade.TradeCrossServerBroker getTradeBroker() { return tradeBroker; }

    private PacketManager packetManager;
    private InvlookManager invlookManager;

    private CustomConfig chatConfig;
    private FormatManager chatFormatManager;
    private ChatSyncManager chatSyncManager;
    private fr.elias.oreoEssentials.trade.TradeConfig tradeConfig;
    private fr.elias.oreoEssentials.trade.TradeService tradeService;
    public fr.elias.oreoEssentials.trade.TradeService getTradeService() { return tradeService; }
    private AfkService afkService;
    private AfkPoolService afkPoolService;

    public AfkService getAfkService() { return afkService; }
    public AfkPoolService getAfkPoolService() { return afkPoolService; }
    private fr.elias.oreoEssentials.homes.HomeTeleportBroker homeTpBroker;
    private fr.elias.oreoEssentials.config.CrossServerSettings crossServerSettings;
    public fr.elias.oreoEssentials.config.CrossServerSettings getCrossServerSettings() { return crossServerSettings; }

    private fr.elias.oreoEssentials.enderchest.EnderChestConfig ecConfig;
    private fr.elias.oreoEssentials.enderchest.EnderChestService ecService;
    public fr.elias.oreoEssentials.enderchest.EnderChestService getEnderChestService() { return ecService; }
    private RtpConfig rtpConfig;
    public RtpConfig getRtpConfig() { return rtpConfig; }
    private final java.util.Map<java.util.UUID, Long> rtpCooldownCache = new java.util.concurrent.ConcurrentHashMap<>();

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
    private CurrencyPlaceholderExpansion currencyPlaceholders;
    private fr.elias.oreoEssentials.playtime.PlaytimeTracker playtimeTracker;
    private PlayerNametagManager nametagManager;
    private Gson gson = new Gson();
    public BackBroker getBackBroker() {
        return backBroker;
    }
    public Gson getGson() { return gson; }
    public Map<UUID, BackLocation> getPendingBackTeleports() {
        return pendingBackTeleports;
    }
    @Override
    public void onLoad() {
        loadExternalLibraries();
    }

    private void loadExternalLibraries() {
        try {
            net.byteflux.libby.BukkitLibraryManager libraryManager =
                    new net.byteflux.libby.BukkitLibraryManager(this);

            libraryManager.addMavenCentral();

            libraryManager.loadLibrary(net.byteflux.libby.Library.builder()
                    .groupId("org.mongodb")
                    .artifactId("bson")
                    .version("5.1.0")
                    .build());

            libraryManager.loadLibrary(net.byteflux.libby.Library.builder()
                    .groupId("org.mongodb")
                    .artifactId("mongodb-driver-core")
                    .version("5.1.0")
                    .build());

            libraryManager.loadLibrary(net.byteflux.libby.Library.builder()
                    .groupId("org.mongodb")
                    .artifactId("mongodb-driver-sync")
                    .version("5.1.0")
                    .build());

            libraryManager.loadLibrary(net.byteflux.libby.Library.builder()
                    .groupId("org.apache.commons")
                    .artifactId("commons-pool2")
                    .version("2.12.0")
                    .build());

            libraryManager.loadLibrary(net.byteflux.libby.Library.builder()
                    .groupId("redis.clients")
                    .artifactId("jedis")
                    .version("5.1.0")
                    .build());

            libraryManager.loadLibrary(net.byteflux.libby.Library.builder()
                    .groupId("org.postgresql")
                    .artifactId("postgresql")
                    .version("42.7.4")
                    .build());

            libraryManager.loadLibrary(net.byteflux.libby.Library.builder()
                    .groupId("com.rabbitmq")
                    .artifactId("amqp-client")
                    .version("5.15.0")
                    .build());

            getLogger().info("[LibraryLoader] All dependencies loaded successfully!");

        } catch (Exception e) {
            getLogger().severe("[LibraryLoader] FAILED to load dependencies: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onEnable() {

        instance = this;
        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            getDataFolder().mkdirs();
            saveDefaultConfig();
            getLogger().info("[Config] Created default config.yml");
        } else {
            getLogger().info("[Config] Loading existing config.yml");
        }        fr.elias.oreoEssentials.config.LegacySettingsMigrator.migrate(this);
        reloadConfig();

        this.settingsConfig = new fr.elias.oreoEssentials.config.SettingsConfig(this);
        this.settings = this.settingsConfig;

        showStartupBanner();
        this.configService = new ConfigService(this);

        this.autoRebootService = new AutoRebootService(this);
        this.autoRebootService.start();


        final String essentialsStorage = getConfig().getString("essentials.storage", "yaml").toLowerCase();

        final String economyType       = getConfig().getString("economy.type", "none").toLowerCase();
        this.economyEnabled = settingsConfig.economyEnabled();
        getLogger().info("[Economy] " + (economyEnabled ? "Enabled" : "Disabled") + " via settings.yml");

        this.redisEnabled              = getConfig().getBoolean("redis.enabled", false);
        this.rabbitEnabled             = getConfig().getBoolean("rabbitmq.enabled", false);
        this.invlookManager = new InvlookManager();

        this.crossServerSettings = fr.elias.oreoEssentials.config.CrossServerSettings.load(this);
        final String localServerName = configService.serverName();

        this.killallLogger = new KillallLogger(this);
        this.freezeService = new FreezeService();
        getServer().getPluginManager().registerEvents(
                new FreezeListener(freezeService), this
        );
        this.commands = new CommandManager(this);
        if (settingsConfig.isEnabled("tempfly")) {
            this.tempFlyConfig = new fr.elias.oreoEssentials.tempfly.TempFlyConfig(getDataFolder());
            this.tempFlyService = new fr.elias.oreoEssentials.tempfly.TempFlyService(this, tempFlyConfig);

            var tempFlyCmd = new fr.elias.oreoEssentials.commands.core.playercommands.TempFlyCommand(tempFlyService);
            this.commands.register(tempFlyCmd);

            if (getCommand("tempfly") != null) {
                getCommand("tempfly").setTabCompleter(tempFlyCmd);
            }
            if (getCommand("tfly") != null) {
                getCommand("tfly").setTabCompleter(tempFlyCmd);
            }

            getLogger().info("[TempFly] Enabled with tab completion.");
        } else {
            unregisterCommandHard("tempfly");
            unregisterCommandHard("tfly");
            getLogger().info("[TempFly] Disabled by settings.yml.");
        }

        var settingsCmd = new fr.elias.oreoEssentials.commands.core.admins.OeSettingsCommand(this);
        this.commands.register(settingsCmd);
        getLogger().info("[Settings] GUI command registered (/oesettings).");

        this.invlookManager = new InvlookManager();
        getServer().getPluginManager().registerEvents(new InvlookListener(this), this);

        getLogger().info(
                "[BOOT] storage=" + essentialsStorage
                        + " economyType=" + economyType
                        + " redis=" + redisEnabled
                        + " rabbit=" + rabbitEnabled
                        + " server.name=" + localServerName
        );

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
            this.redis = new RedisManager("", 6379, "");
            getLogger().info("[REDIS] Disabled.");
        }

        this.ecoBootstrap = new EconomyBootstrap(this);
        this.ecoBootstrap.enable();
        if (economyEnabled) {
            this.database = null;

            switch (economyType) {
                case "mongodb" -> {
                    MongoDBManager mgr = new MongoDBManager(this, redis);
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
                    boolean ok = mgr.connect("", "", "");
                    if (!ok) {
                        getLogger().severe("[ECON] JSON init failed. Disabling plugin.");
                        getServer().getPluginManager().disablePlugin(this);
                        return;
                    }
                    this.database = mgr;
                }
                case "none" -> this.database = null;
                default -> { }
            }

            if (this.database != null) {
                if (getServer().getPluginManager().getPlugin("Vault") == null) {
                    getLogger().severe("[ECON] Vault not found but economy.enabled=true. Disabling plugin.");
                    getServer().getPluginManager().disablePlugin(this);
                    return;
                }

                try {
                    getServer().getServicesManager().unregister(
                            net.milkbowl.vault.economy.Economy.class,
                            this
                    );
                } catch (Throwable ignored) {}

                VaultEconomyProvider vaultProvider = new VaultEconomyProvider(this);
                getServer().getServicesManager().register(
                        net.milkbowl.vault.economy.Economy.class,
                        vaultProvider,
                        this,
                        org.bukkit.plugin.ServicePriority.Highest
                );

                var rsp = getServer().getServicesManager()
                        .getRegistration(net.milkbowl.vault.economy.Economy.class);
                if (rsp == null) {
                    getLogger().severe("[ECON] Failed to hook Vault Economy.");
                } else {
                    this.vaultEconomy = rsp.getProvider();
                    getLogger().info("[ECON] Vault economy integration enabled at HIGHEST priority.");
                }

                Bukkit.getPluginManager().registerEvents(new PlayerDataListener(this), this);
                Bukkit.getPluginManager().registerEvents(new PlayerListener(this), this);

                this.offlinePlayerCache = new OfflinePlayerCache();

                this.database.populateCache(offlinePlayerCache);
                Bukkit.getScheduler().runTaskTimerAsynchronously(
                        this,
                        () -> this.database.populateCache(offlinePlayerCache),
                        20L * 60,
                        20L * 300
                );

                if (this.database != null) {
                    var moneyCmd  = new MoneyCommand(this);
                    var payCmd    = new fr.elias.oreoEssentials.commands.ecocommands.PayCommand();
                    var chequeCmd = new fr.elias.oreoEssentials.commands.ecocommands.ChequeCommand(this);

                    this.commands
                            .register(moneyCmd)
                            .register(payCmd)
                            .register(chequeCmd);

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
        try {
            java.io.File f = new java.io.File(getDataFolder(), "command-control.yml");
            if (!f.exists()) saveResource("command-control.yml", false);

            var yml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f);

            var commandControl = new fr.elias.oreoEssentials.commandcontrol.CommandControlService();
            commandControl.load(yml);

            if (commandControl.isEnabled()) {
                Bukkit.getPluginManager().registerEvents(
                        new fr.elias.oreoEssentials.commandcontrol.CommandControlListener(this, commandControl),
                        this
                );

                if (commandControl.isHideFromTab()) {
                    Bukkit.getPluginManager().registerEvents(
                            new fr.elias.oreoEssentials.commandcontrol.CommandControlTabHideListener(commandControl),
                            this
                    );
                }

                getLogger().info("[CommandControl] Enabled (hideFromTab=" + commandControl.isHideFromTab() + ")");
            } else {
                getLogger().info("[CommandControl] Disabled.");
            }

        } catch (Throwable t) {
            getLogger().warning("[CommandControl] Failed to init: " + t.getMessage());
        }

        try {
            java.io.File f = new java.io.File(getDataFolder(), "clearlag.yml");
            if (!f.exists()) {
                saveResource("clearlag.yml", false);
            }
        } catch (Throwable ignored) {}

        fr.elias.oreoEssentials.util.SkinRefresherBootstrap.init(this);
        fr.elias.oreoEssentials.util.SkinDebug.init(this);
        try {
            this.commandToggleConfig = new CommandToggleConfig(this);
            this.commandToggleService = new CommandToggleService(this, commandToggleConfig);

            // Register the listener to intercept disabled commands
            getServer().getPluginManager().registerEvents(
                    new CommandToggleListener(this, commandToggleConfig),
                    this
            );

            getLogger().info("[CommandToggle] Command toggle system initialized");
        } catch (Exception e) {
            getLogger().severe("[CommandToggle] Failed to initialize: " + e.getMessage());
            e.printStackTrace();
        }
        Lang.init(this);
        boolean kitsFeature   = settingsConfig.kitsEnabled();
        boolean kitsRegister  = settingsConfig.kitsCommandsEnabled();

        if (kitsFeature) {
            this.kitsManager = new fr.elias.oreoEssentials.kits.KitsManager(this);

            if (kitsRegister) {
                new fr.elias.oreoEssentials.kits.KitCommands(this, this.kitsManager);
                getLogger().info("[Kits] Loaded " + this.kitsManager.getKits().size() + " kits from kits.yml");
            } else {
                unregisterCommandHard("kits");
                unregisterCommandHard("kit");
                getLogger().info("[Kits] Module loaded, but commands are NOT registered.");
            }
        } else {
            unregisterCommandHard("kits");
            unregisterCommandHard("kit");
            this.kitsManager = null;
            getLogger().info("[Kits] Module disabled by config; commands unregistered.");
        }

        this.aliasService = new fr.elias.oreoEssentials.aliases.AliasService(this);
        this.aliasService.load();
        this.aliasService.applyRuntimeRegistration();
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        getServer().getMessenger().registerOutgoingPluginChannel(this, "bungeecord:main");
        this.proxyMessenger = new ProxyMessenger(this);
        ProxyMessenger proxyMessenger = this.proxyMessenger;
        getLogger().info("[BOOT] Registered proxy plugin messaging channels.");
        this.afkService = new AfkService(this);
        getServer().getPluginManager().registerEvents(
                new fr.elias.oreoEssentials.listeners.AfkListener(afkService),
                this
        );
        if (settingsConfig.afkPoolEnabled()) {
            try {
                this.afkPoolService = new AfkPoolService(this, afkService);
                afkService.setPoolService(afkPoolService);
                getLogger().info("[AfkPool] Enabled with WorldGuard region support");
            } catch (Throwable t) {
                getLogger().warning("[AfkPool] Failed to initialize: " + t.getMessage());
                getLogger().warning("[AfkPool] Make sure WorldGuard is installed for region support");
                this.afkPoolService = null;
            }
        } else {
            getLogger().info("[AfkPool] Disabled by settings.yml");
            this.afkPoolService = null;
        }
        this.invManager = new InventoryManager(this);
        this.invManager.init();
        this.sellGuiManager = new fr.elias.oreoEssentials.sellgui.SellGuiManager(this, this.invManager);

        try {
            this.modGuiService = new fr.elias.oreoEssentials.modgui.ModGuiService(this);
            getLogger().info("[ModGUI] Server management GUI ready (/modgui).");

            new WorldTweaksListener(this);
        } catch (Throwable t) {
            getLogger().warning("[ModGUI] Failed to init: " + t.getMessage());
            this.modGuiService = null;
        }

        notesManager = new PlayerNotesManager(this);
        notesChat = new NotesChatListener(this, notesManager);
        ipTracker = new IpTracker(this);

        this.tradeConfig = new fr.elias.oreoEssentials.trade.TradeConfig(this);

        if (this.tradeConfig.enabled && settingsConfig.tradeEnabled()) {
            this.tradeService = new fr.elias.oreoEssentials.trade.TradeService(this, this.tradeConfig);

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

        getServer().getPluginManager().registerEvents(
                new fr.elias.oreoEssentials.customcraft.CustomCraftingListener(customCraftingService),
                this
        );
        this.freezeManager = new FreezeManager(this);

        var dailyCfg = new fr.elias.oreoEssentials.daily.DailyConfig(this);
        dailyCfg.load();

        fr.elias.oreoEssentials.daily.DailyStorage dailyStorage =
                fr.elias.oreoEssentials.daily.DailyStorage.create(this, dailyCfg);

        var dailyRewardsCfg = new fr.elias.oreoEssentials.daily.RewardsConfig(this);
        dailyRewardsCfg.load();

        var dailySvc = new fr.elias.oreoEssentials.daily.DailyService(
                this,
                dailyCfg,
                dailyStorage,
                dailyRewardsCfg
        );

        var dailyCmd = new fr.elias.oreoEssentials.daily.DailyCommand(
                this,
                dailyCfg,
                dailySvc,
                dailyRewardsCfg
        );
        if (getCommand("daily") != null) {
            getCommand("daily").setExecutor(dailyCmd);
            getCommand("daily").setTabCompleter(dailyCmd);
        }

        getLogger().info("[Daily] Rewards system initialized with "
                + (dailyCfg.mongo.enabled ? "MongoDB" : "file-based")
                + " storage.");

        muteService = new MuteService(this);
        getServer().getPluginManager().registerEvents(
                new fr.elias.oreoEssentials.listeners.MuteListener(muteService),
                this
        );

        if (settingsConfig.discordModerationEnabled()) {
            this.discordMod = new fr.elias.oreoEssentials.integration.DiscordModerationNotifier(this);
            getLogger().info("[DiscordMod] Discord moderation integration enabled (settings.yml).");
        } else {
            this.discordMod = null;
            getLogger().info("[DiscordMod] Disabled by settings.yml (features.discord-moderation.enabled=false).");
        }

        try {
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

        var killExec = new KillallRecorderCommand(this, killallLogger);
        getCommand("killallr").setExecutor(killExec);
        getCommand("killallr").setTabCompleter(killExec);
        getCommand("killallrlog").setExecutor(new KillallLogViewCommand(killallLogger));

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

        this.chatConfig = new fr.elias.oreoEssentials.chat.CustomConfig(this, "chat-format.yml");
        this.chatFormatManager = new fr.elias.oreoEssentials.chat.FormatManager(chatConfig);

        this.channelManager = new fr.elias.oreoEssentials.chat.channels.ChatChannelManager(
                this,
                this.chatConfig,
                this.homesMongoClient
        );

        this.channelManager.reload();


        getServer().getPluginManager().registerEvents(new JoinMessagesListener(this), this);
        getServer().getPluginManager().registerEvents(new QuitMessagesListener(this), this);

        boolean chatSyncEnabled = chatConfig.getCustomConfig().getBoolean("MongoDB_rabbitmq.enabled", false);
        String chatRabbitUri    = chatConfig.getCustomConfig().getString("MongoDB_rabbitmq.rabbitmq.uri", "");
        try {
            this.chatSyncManager = new ChatSyncManager(chatSyncEnabled, chatRabbitUri, muteService, channelManager);
            if (chatSyncEnabled) this.chatSyncManager.subscribeMessages();
            getLogger().info("[CHAT] ChatSync enabled=" + chatSyncEnabled);
        } catch (Exception e) {
            getLogger().severe("[CHAT] ChatSync init failed: " + e.getMessage());
            this.chatSyncManager = new ChatSyncManager(false, "", muteService, null);
        }

        boolean discordEnabled = false;
        String discordWebhookUrl = "";
        Listener chatListener;

        if (channelManager != null && channelManager.isEnabled()) {
            chatListener = new fr.elias.oreoEssentials.chat.AsyncChatListenerWithChannels(
                    this,
                    chatFormatManager,
                    chatConfig,
                    chatSyncManager,
                    discordEnabled,
                    discordWebhookUrl,
                    muteService,
                    channelManager
            );
            getLogger().info("[Chat] Initialized with channel support");
        } else {
            chatListener = new fr.elias.oreoEssentials.chat.AsyncChatListener(
                    chatFormatManager,
                    chatConfig,
                    chatSyncManager,
                    discordEnabled,
                    discordWebhookUrl,
                    muteService
            );
            getLogger().info("[Chat] Initialized without channels (legacy formatting mode)");
        }

        getServer().getPluginManager().registerEvents(chatListener, this);

        if (getSettingsConfig().chatDiscordBridgeEnabled()) {
            var chatRoot = chatConfig.getCustomConfig().getConfigurationSection("chat.discord");
            if (chatRoot != null) {
                discordEnabled = chatRoot.getBoolean("enabled", false);
                discordWebhookUrl = chatRoot.getString("webhook_url", "");
            }
        }



        if (channelManager.isEnabled()) {
            var channelsCmd = new fr.elias.oreoEssentials.chat.channels.commands.OeChannelsCommand(this, channelManager);
            var channelCmd = new fr.elias.oreoEssentials.chat.channels.commands.OeChannelCommand(this, channelManager);

            this.commands.register(channelsCmd);
            this.commands.register(channelCmd);

            if (getCommand("oechannel") != null) {
                getCommand("oechannel").setTabCompleter(channelCmd);
            }
            if (getCommand("channel") != null) {
                getCommand("channel").setTabCompleter(channelCmd);
            }

            getLogger().info("[Channels] Enabled with " + channelManager.getAllChannels().size() + " channels");

            var announceCmd = new fr.elias.oreoEssentials.chat.channels.commands.ChannelAnnounceCommand(
                    this,
                    channelManager,
                    chatSyncManager
            );

            if (getCommand("channelannounce") != null) {
                getCommand("channelannounce").setExecutor(announceCmd);
                getCommand("channelannounce").setTabCompleter(announceCmd);
            }
            if (getCommand("channelannounce") != null) {
                getCommand("channelannounce").setTabCompleter(announceCmd);
            }

            getLogger().info("[Channels] Announcement command registered (/channelannounce)");

        } else {
            getLogger().info("[Channels] Disabled by config (chat.channels.enabled=false)");
        }


        getServer().getPluginManager().registerEvents(new ConversationListener(this), this);
        new fr.elias.oreoEssentials.tasks.AutoMessageScheduler(this).start();
        var pwRoot = settingsConfig.getRoot().getConfigurationSection("playerwarps");
        boolean pwEnabled = (pwRoot == null) || pwRoot.getBoolean("enabled", true);
        boolean pwCross   = (pwRoot == null) || pwRoot.getBoolean("cross-server", true);

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
                dirListener.backfillOnline();
                try {
                    new fr.elias.oreoEssentials.playerdirectory.DirectoryHeartbeat(
                            this.playerDirectory,
                            configService.serverName()
                    ).start();
                    getLogger().info("[PlayerDirectory] Heartbeat started (every 30s).");
                } catch (Throwable t) {
                    getLogger().warning("[PlayerDirectory] Heartbeat failed to start: " + t.getMessage());
                }

                try {
                    MongoHomesMigrator.run(
                            this.homesMongoClient,
                            dbName,
                            prefix,
                            org.bukkit.Bukkit.getServer().getName(),
                            localServerName,
                            getLogger()
                    );
                } catch (Throwable ignored) {
                    getLogger().info("[STORAGE] MongoHomesMigrator skipped.");
                }

                this.storage = new MongoHomesStorage(
                        this.homesMongoClient, dbName, prefix, localServerName
                );

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
                    PlayerWarpStorage pwStorage = new YamlPlayerWarpStorage(this);
                    this.playerWarpService      = new PlayerWarpService(pwStorage, null);
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
                    PlayerWarpStorage pwStorage = new YamlPlayerWarpStorage(this);
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

        this.ecConfig = new fr.elias.oreoEssentials.enderchest.EnderChestConfig(this);

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

        fr.elias.oreoEssentials.services.InventoryService invSvc =
                new fr.elias.oreoEssentials.services.InventoryService() {
                    @Override
                    public Snapshot load(java.util.UUID uuid) {
                        try {
                            var s = invStorage.load(uuid);
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

        this.spawnService = new SpawnService(storage);
        this.warpService  = new WarpService(storage, this.warpDirectory);
        this.homeService  = new HomeService(this.storage, this.configService, this.homeDirectory);

        if (rabbitEnabled) {

            RabbitMQSender rabbit = new RabbitMQSender(getConfig().getString("rabbitmq.uri"), localServerName);

            if (this.offlinePlayerCache == null) {
                this.offlinePlayerCache = new OfflinePlayerCache();
            }

            this.packetManager = new PacketManager(this, rabbit);

            if (rabbit.connect()) {

                registerAllPacketsDeterministically(this.packetManager);


                if (invSyncEnabled) {
                    try {
                        this.invseeBroker = new fr.elias.oreoEssentials.cross.InvseeCrossServerBroker(
                                this,
                                this.packetManager,
                                localServerName,
                                null
                        );
                        this.invseeService = new fr.elias.oreoEssentials.cross.InvseeService(
                                this,
                                this.invseeBroker
                        );
                        this.invseeBroker.setService(this.invseeService);

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

                this.packetManager.subscribeChannel(PacketChannels.GLOBAL);
                this.packetManager.subscribeChannel(
                        fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel.individual(localServerName)
                );
                getLogger().info("[RABBIT] Subscribed channels: global + individual(" + localServerName + ")");

                if (this.invseeBroker != null) {
                    this.packetManager.subscribe(
                            fr.elias.oreoEssentials.cross.InvseeOpenRequestPacket.class,
                            (channel, pkt) -> this.invseeBroker.handleOpenRequest(pkt)
                    );
                    this.packetManager.subscribe(
                            fr.elias.oreoEssentials.cross.InvseeStatePacket.class,
                            (channel, pkt) -> this.invseeBroker.handleState(pkt)
                    );
                    this.packetManager.subscribe(
                            fr.elias.oreoEssentials.cross.InvseeEditPacket.class,
                            (channel, pkt) -> this.invseeBroker.handleEdit(pkt)
                    );
                    getLogger().info("[INVSEE] Subscribed Invsee packets on RabbitMQ.");
                }

                this.packetManager.subscribe(
                        fr.elias.oreoEssentials.rabbitmq.packet.impl.SendRemoteMessagePacket.class,
                        new RemoteMessagePacketHandler()
                );

                this.packetManager.subscribe(
                        fr.elias.oreoEssentials.rabbitmq.packet.impl.trade.TradeStartPacket.class,
                        (fr.elias.oreoEssentials.rabbitmq.packet.event.PacketSubscriber<
                                fr.elias.oreoEssentials.rabbitmq.packet.impl.trade.TradeStartPacket>
                                ) new fr.elias.oreoEssentials.rabbitmq.handler.trade.TradeStartPacketHandler(this)
                );
                this.packetManager.subscribe(
                        fr.elias.oreoEssentials.rabbitmq.packet.impl.PlayerJoinPacket.class,
                        new PlayerJoinPacketHandler(this)
                );
                this.packetManager.subscribe(
                        fr.elias.oreoEssentials.rabbitmq.packet.impl.PlayerQuitPacket.class,
                        new PlayerQuitPacketHandler(this)
                );
                this.packetManager.subscribe(
                        fr.elias.oreoEssentials.rabbitmq.packet.impl.DeathMessagePacket.class,
                        new fr.elias.oreoEssentials.rabbitmq.handler.DeathMessagePacketHandler(this)
                );

                this.packetManager.subscribe(
                        fr.elias.oreoEssentials.rabbitmq.packet.impl.trade.TradeInvitePacket.class,
                        new fr.elias.oreoEssentials.rabbitmq.handler.trade.TradeInvitePacketHandler(this)
                );

                this.packetManager.subscribe(
                        fr.elias.oreoEssentials.rabbitmq.packet.impl.trade.TradeStatePacket.class,
                        new fr.elias.oreoEssentials.rabbitmq.handler.trade.TradeStatePacketHandler(this)
                );
                this.packetManager.subscribe(
                        fr.elias.oreoEssentials.rabbitmq.packet.impl.trade.TradeConfirmPacket.class,
                        new fr.elias.oreoEssentials.rabbitmq.handler.trade.TradeConfirmPacketHandler(this)
                );
                this.packetManager.subscribe(
                        fr.elias.oreoEssentials.rabbitmq.packet.impl.trade.TradeCancelPacket.class,
                        new fr.elias.oreoEssentials.rabbitmq.handler.trade.TradeCancelPacketHandler(this)
                );
                this.packetManager.subscribe(
                        fr.elias.oreoEssentials.rabbitmq.packet.impl.trade.TradeGrantPacket.class,
                        new fr.elias.oreoEssentials.rabbitmq.handler.trade.TradeGrantPacketHandler(this)
                );
                this.packetManager.subscribe(
                        fr.elias.oreoEssentials.rabbitmq.packet.impl.trade.TradeClosePacket.class,
                        new fr.elias.oreoEssentials.rabbitmq.handler.trade.TradeClosePacketHandler(this)
                );

                this.packetManager.init();
                try {
                    if (this.afkPoolService != null) {
                        this.afkPoolService.tryHookCrossServerNow();
                    }
                } catch (Throwable t) {
                    getLogger().warning("[AfkPool] Failed to hook cross-server handlers after PacketManager init: " + t.getMessage());
                }
                try {
                    getLogger().info("[RABBIT] Packet registry checksum=" + this.packetManager.registryChecksum());
                } catch (Throwable ignored) {}

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

        initCurrencySystem();

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI") && currencyService != null) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                try {
                    this.currencyPlaceholders = new CurrencyPlaceholderExpansion(this);

                    if (this.currencyPlaceholders.register()) {
                        getLogger().info("✓ PlaceholderAPI expansion registered successfully!");
                        getLogger().info("  Available: %oreo_currency_balance_<id>%, etc.");
                    } else {
                        getLogger().warning("✗ Failed to register PlaceholderAPI expansion!");
                    }
                } catch (Throwable t) {
                    getLogger().warning("[Currency] PlaceholderAPI expansion failed: " + t.getMessage());
                }
            }, 60L);
        } else {
            getLogger().info("[Currency] PlaceholderAPI not found - placeholders disabled");
        }


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


        if (packetManager != null && packetManager.isInitialized()) {

            final var cs = this.getCrossServerSettings();
            final boolean anyCross =
                    cs.homes() || cs.warps() || cs.spawn() || cs.economy();

            if (anyCross) {
                getLogger().info("[RABBIT] Cross-server features enabled; channels already subscribed. server=" + configService.serverName());
            } else {
                getLogger().info("[RABBIT] All cross-server features disabled by config; skipping brokers.");
            }

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


        this.backService      = new BackService(storage);
        this.messageService   = new MessageService();
        this.teleportService  = new TeleportService(this, backService, configService);
        this.deathBackService = new DeathBackService();
        this.godService       = new GodService();

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
        if (rabbitEnabled && packetManager != null && packetManager.isInitialized()) {
            try {
                com.rabbitmq.client.ConnectionFactory factory = new com.rabbitmq.client.ConnectionFactory();
                factory.setUri(getConfig().getString("rabbitmq.uri"));
                com.rabbitmq.client.Connection rabbitConn = factory.newConnection();

                this.backBroker = new BackBroker(this, backService, rabbitConn);
                this.backBroker.start();

                getServer().getPluginManager().registerEvents(
                        new BackJoinListener(this, backService),
                        this
                );


                getLogger().info("[BackBroker] Cross-server /back broker ready (server=" + configService.serverName() + ").");
            } catch (Exception e) {
                this.backBroker = null;
                getLogger().severe("[BackBroker] Failed to initialize: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            this.backBroker = null;
            getLogger().info("[BackBroker] Disabled (RabbitMQ not available).");
        }

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

        VanishService vanishService = new VanishService(this);
        getServer().getPluginManager().registerEvents(
                new fr.elias.oreoEssentials.listeners.VanishListener(vanishService, this),
                this
        );

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
        getServer().getPluginManager().registerEvents(
                new fr.elias.oreoEssentials.rtp.listeners.DeathRespawnListener(this), this
        );
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

        this.playerVaultsConfig = new fr.elias.oreoEssentials.playervaults.PlayerVaultsConfig(this);
        this.playervaultsService = new fr.elias.oreoEssentials.playervaults.PlayerVaultsService(this);
        if (this.playervaultsService.enabled()) {
            getLogger().info("[Vaults] PlayerVaults enabled.");
        } else {
            getLogger().info("[Vaults] PlayerVaults disabled by config or storage unavailable.");
        }
        this.rtpPendingService = new RtpPendingService();
        this.rtpConfig         = new RtpConfig(this);

        getServer().getPluginManager().registerEvents(
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
            var rtpCmd = new fr.elias.oreoEssentials.commands.core.playercommands.RtpCommand();
            this.commands.register(rtpCmd);

            getLogger().info("[RTP] Enabled — /rtp registered with tab-completion.");
        }

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

        if (settingsConfig.bossbarEnabled()) {
            this.bossBarService = new BossBarService(this);
            this.bossBarService.start();
            this.commands.register(new BossBarToggleCommand(this.bossBarService));
            getLogger().info("[BossBar] Enabled from settings.yml.");
        } else {
            unregisterCommandHard("bossbar");
            getLogger().info("[BossBar] Disabled by settings.yml.");
        }

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

        if (settingsConfig.tabEnabled()) {
            this.tabListManager = new fr.elias.oreoEssentials.tab.TabListManager(this);
            this.tabListManager.start();
            getLogger().info("[TAB] Custom tab-list enabled via settings.yml.");
        } else {
            this.tabListManager = null;
            getLogger().info("[TAB] Disabled by settings.yml (features.tab.enabled=false).");
        }
        checkProtocolLib();
        var tphere = new fr.elias.oreoEssentials.commands.core.admins.TphereCommand(this);
        this.commands.register(tphere);
        if (getCommand("tphere") != null) {
            getCommand("tphere").setTabCompleter(tphere);
        }

        var muteCmd   = new MuteCommand(muteService, chatSyncManager);
        var unmuteCmd = new UnmuteCommand(muteService, chatSyncManager);

        var nickCmd = new fr.elias.oreoEssentials.commands.core.playercommands.NickCommand();
        this.commands.register(nickCmd);


        fr.elias.oreoEssentials.jail.JailStorage jailStorage;

        boolean useMongoForJails = getConfig().getBoolean("Jail.Storage.Mongo.Enabled", false);

        if (useMongoForJails && "mongodb".equalsIgnoreCase(essentialsStorage) && this.homesMongoClient != null) {
            String mongoDb = getConfig().getString("storage.mongo.database", "oreo");

            jailStorage = new fr.elias.oreoEssentials.jail.MongoJailStorage(
                    getConfig().getString("storage.mongo.uri", "mongodb://localhost:27017"),
                    mongoDb
            );
            getLogger().info("[Jails] Using MongoDB storage (cross-server enabled).");
        } else {
            jailStorage = new fr.elias.oreoEssentials.jail.YamlJailStorage(this);
            getLogger().info("[Jails] Using YAML storage (single-server only).");
        }

        this.jailService = new fr.elias.oreoEssentials.jail.JailService(this, jailStorage);
        this.jailService.enable();

        getServer().getPluginManager().registerEvents(
                new fr.elias.oreoEssentials.jail.JailGuardListener(jailService),
                this
        );
        getServer().getPluginManager().registerEvents(
                new fr.elias.oreoEssentials.jail.JailJoinListener(jailService),
                this
        );

        getLogger().info("[Jails] System initialized with " + jailStorage.getClass().getSimpleName());
        PlayerWarpCommand pwCmd = null;
        if (this.playerWarpService != null) {
            pwCmd = new PlayerWarpCommand(this.playerWarpService);
        }

        if (getCommand("pwwhitelist") != null && this.playerWarpService != null) {
            PlayerWarpWhitelistCommand pwwCmd = new PlayerWarpWhitelistCommand(this.playerWarpService);
            getCommand("pwwhitelist").setExecutor(pwwCmd);
            getCommand("pwwhitelist").setTabCompleter(pwwCmd);
            getLogger().info("[PlayerWarps] /pwwhitelist registered.");
        } else {
            getLogger().info("[PlayerWarps] /pwwhitelist not registered (command missing in plugin.yml or playerWarpService=null).");
        }

        this.commands
                .register(new SpawnCommand(spawnService))
                .register(new SetSpawnCommand(spawnService))
                .register(new BackCommand(backService, teleportService, this))
                .register(new WarpCommand(warpService))
                .register(new SetWarpCommand(warpService))
                .register(new WarpsCommand(warpService))
                .register(new WarpsAdminCommand(warpService))
                .register(new HomeCommand(homeService))
                .register(new DelWarpCommand(warpService))
                .register(new SetHomeCommand(homeService, configService))
                .register(new DelHomeCommand(homeService))
                .register(new TpaCommand(teleportService))
                .register(new TpAcceptCommand(teleportService))
                .register(new TpDenyCommand(teleportService))
                .register(new FlyCommand())
                .register(new HealCommand())
                .register(new FeedCommand())
                .register(new MsgCommand(messageService))
                .register(new ReplyCommand(messageService))
                .register(new BroadcastCommand())
                .register(new HomesCommand(homeService))
                .register(new HomesGuiCommand(homeService))
                .register(new DeathBackCommand(deathBackService))
                .register(new GodCommand(godService))
                .register(new AfeliusReloadCommand(this, chatConfig))
                .register(new VanishCommand(vanishService))
                .register(new BanCommand())
                .register(new KickCommand())
                .register(new FreezeCommand(freezeService))
                .register(new EnchantCommand())
                .register(new DisenchantCommand())
                .register(muteCmd)
                .register(new UnbanCommand())
                .register(unmuteCmd)
                .register(new OeCommand())
                .register(new ServerProxyCommand(proxyMessenger))
                .register(new SkinCommand())
                .register(new CloneCommand())
                .register(new fr.elias.oreoEssentials.playersync.PlayerSyncCommand(this, playerSyncService, invSyncEnabled))
                .register(new fr.elias.oreoEssentials.commands.core.playercommands.EcCommand(this.ecService, crossServerEc))
                .register(new HeadCommand())
                .register(new fr.elias.oreoEssentials.commands.core.playercommands.SellGuiCommand(this))
                .register(new fr.elias.oreoEssentials.commands.core.playercommands.AfkCommand(afkService))
                .register(new fr.elias.oreoEssentials.commands.core.playercommands.TrashCommand())
                .register(new fr.elias.oreoEssentials.commands.core.playercommands.WorkbenchCommand())
                .register(new fr.elias.oreoEssentials.commands.core.playercommands.AnvilCommand())
                .register(new ClearCommand())
                .register(new fr.elias.oreoEssentials.commands.core.playercommands.SeenCommand())
                .register(new fr.elias.oreoEssentials.commands.core.playercommands.PingCommand())
                .register(new fr.elias.oreoEssentials.commands.core.playercommands.HatCommand())
                .register(new fr.elias.oreoEssentials.commands.core.playercommands.RealNameCommand())
                .register(new fr.elias.oreoEssentials.commands.core.playercommands.FurnaceCommand())
                .register(new fr.elias.oreoEssentials.commands.core.playercommands.NearCommand())
                .register(new fr.elias.oreoEssentials.commands.core.playercommands.KillCommand())
                .register(new fr.elias.oreoEssentials.commands.core.playercommands.InvseeCommand())
                .register(new fr.elias.oreoEssentials.commands.core.playercommands.invlook.InvlookCommand())
                .register(new fr.elias.oreoEssentials.commands.core.playercommands.CookCommand())
                .register(new fr.elias.oreoEssentials.commands.ecocommands.BalanceCommand(this))
                .register(new fr.elias.oreoEssentials.commands.ecocommands.BalTopCommand(this))
                .register(new fr.elias.oreoEssentials.commands.core.playercommands.EcSeeCommand())
                .register(new fr.elias.oreoEssentials.commands.core.admins.ReloadAllCommand())
                .register(new fr.elias.oreoEssentials.commands.core.playercommands.VaultsCommand())
                .register(new fr.elias.oreoEssentials.commands.core.playercommands.UuidCommand())
                .register(new TpCommand(teleportService))
                .register(new ZEssentialsHomesImportCommand(this, storage, homeDirectory))
                .register(new MoveCommand(teleportService))
                .register(new fr.elias.oreoEssentials.currency.commands.CurrencyAdminCommand(this));
        if (pwCmd != null) {
            this.commands.register(pwCmd);
        }
        if (getCommand("oeserver") != null) {
            getCommand("oeserver").setTabCompleter(new ServerProxyCommand(proxyMessenger));
        }
        getCommand("kick").setTabCompleter(new KickTabCompleter(this));
        if (getCommand("clear") != null) {
            getCommand("clear").setTabCompleter(new ClearTabCompleter(this));
        }

        TpaTabCompleter tpaCompleter = new TpaTabCompleter(this);
        if (getCommand("tpa") != null) {
            getCommand("tpa").setTabCompleter(tpaCompleter);
        }
        if (getCommand("invlook") != null) {
            getCommand("invlook").setTabCompleter(
                    new fr.elias.oreoEssentials.commands.core.playercommands.invlook.InvlookCommand()
            );
        }

        TpTabCompleter tpCompleter = new TpTabCompleter(this);
        if (getCommand("tp") != null) {
            getCommand("tp").setTabCompleter(tpCompleter);
        }

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
        if (getCommand("jail") != null) {
            var jailCmd = new fr.elias.oreoEssentials.jail.commands.JailCommand(jailService);
            getCommand("jail").setExecutor(jailCmd);
            getCommand("jail").setTabCompleter(new fr.elias.oreoEssentials.jail.commands.JailCommandTabCompleter(jailService));
        }

        if (getCommand("jailedit") != null) {
            var jailEditCmd = new fr.elias.oreoEssentials.jail.commands.JailEditCommand(jailService);
            getCommand("jailedit").setExecutor(jailEditCmd);
            getCommand("jailedit").setTabCompleter(new fr.elias.oreoEssentials.jail.commands.JailEditCommandTabCompleter(jailService));
        }

        if (getCommand("jaillist") != null) {
            var jailListCmd = new fr.elias.oreoEssentials.jail.commands.JailListCommand(jailService);
            getCommand("jaillist").setExecutor(jailListCmd);
            getCommand("jaillist").setTabCompleter(new fr.elias.oreoEssentials.jail.commands.JailListCommandTabCompleter(jailService));
        }

        if (getCommand("aliaseditor") != null) {
            var aliasCmd = new fr.elias.oreoEssentials.aliases.AliasEditorCommand(aliasService, invManager);
            getCommand("aliaseditor").setExecutor(aliasCmd);
            getCommand("aliaseditor").setTabCompleter(aliasCmd);
        }
        if (getCommand("commandtoggle") != null && commandToggleConfig != null && commandToggleService != null) {
            var cmdToggleCmd = new CommandToggleCommand(this, commandToggleConfig, commandToggleService);
            getCommand("commandtoggle").setExecutor(cmdToggleCmd);
            getCommand("commandtoggle").setTabCompleter(cmdToggleCmd);
            getLogger().info("[CommandToggle] /commandtoggle command registered");
        }
        if (getCommand("otherhome") != null) {
            var otherHome = new fr.elias.oreoEssentials.commands.core.admins.OtherHomeCommand(this, homeService);
            this.commands.register(otherHome);
            getCommand("otherhome").setTabCompleter(otherHome);
        }

        var visitorService = new fr.elias.oreoEssentials.services.VisitorService();
        getServer().getPluginManager().registerEvents(
                new fr.elias.oreoEssentials.listeners.VisitorGuardListener(visitorService),
                this
        );
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

        this.eventConfig   = new fr.elias.oreoEssentials.events.EventConfig(getDataFolder());
        this.deathMessages = new fr.elias.oreoEssentials.events.DeathMessageService(getDataFolder());

        this.playtimeTracker = new fr.elias.oreoEssentials.playtime.PlaytimeTracker(this);

        this.playtimeRewards = new fr.elias.oreoEssentials.playtime.PlaytimeRewardsService(
                this,
                playtimeTracker
        );

        this.playtimeRewards.init();

        if (!settingsConfig.playtimeRewardsEnabled()) {
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
        if (settingsConfig.getRoot().getBoolean("holograms.text.per-player", false)) {
            try {
                Class.forName("org.bukkit.entity.Display");

                fr.elias.oreoEssentials.holograms.nms.NmsHologramBridge nms =
                        fr.elias.oreoEssentials.holograms.nms.NmsBridgeLoader.loadOrThrow();

                this.perPlayerTextDisplayService =
                        new fr.elias.oreoEssentials.holograms.perplayer_nms.PerPlayerTextDisplayService(this, nms);

                getServer().getPluginManager().registerEvents(
                        new fr.elias.oreoEssentials.holograms.perplayer_nms.PerPlayerTextDisplayListener(
                                this.perPlayerTextDisplayService
                        ),
                        this
                );
                getServer().getScheduler().runTaskTimer(this, () -> {
                    try { perPlayerTextDisplayService.tick(); }
                    catch (Throwable t) { getLogger().warning("[PerPlayerTextDisplay] tick failed: " + t.getMessage()); }
                }, 20L, 10L);

                getLogger().info("[PerPlayerTextDisplay] Enabled.");
            } catch (ClassNotFoundException x) {
                getLogger().warning("[PerPlayerTextDisplay] Display entities not available on this server. Requires Paper/Folia.");
            } catch (Throwable t) {
                getLogger().warning("[PerPlayerTextDisplay] Failed to initialize: " + t.getMessage());
            }
        }

        if (settingsConfig.oreoHologramsEnabled()) {
            try {
                try {
                    Class.forName("org.bukkit.entity.Display");
                } catch (ClassNotFoundException x) {
                    getLogger().warning("[OreoHolograms] Display entities not available on this server. Requires Paper/Folia.");
                    throw x;
                }

                this.oreoHolograms = new fr.elias.oreoEssentials.holograms.OreoHolograms(this);
                this.oreoHolograms.load();

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

                Bukkit.getScheduler().runTaskTimer(
                        this,
                        () -> {
                            try { this.oreoHolograms.tickAll(); } catch (Throwable ignored) {}
                        },
                        20L, 20L
                );

                getLogger().info("[OreoHolograms] Enabled from settings.yml.");
            } catch (Throwable t) {
                this.oreoHolograms = null;
                getLogger().warning("[OreoHolograms] Failed to initialize: " + t.getMessage());
            }
        } else {
            unregisterCommandHard("ohologram");
            unregisterCommandHard("hologram");
            this.oreoHolograms = null;
            getLogger().info("[OreoHolograms] Disabled by settings.yml.");
        }
        if (settingsConfig.worldShardingEnabled()) {
            try {
                this.shardsModule = new fr.elias.oreoEssentials.shards.OreoShardsModule(this);
                this.shardsModule.enable();
                getLogger().info("[Sharding] World sharding enabled (seamless shard transfers).");
            } catch (Throwable t) {
                this.shardsModule = null;
                getLogger().warning("[Sharding] Failed to initialize: " + t.getMessage());
            }
        } else {
            this.shardsModule = null;
            getLogger().info("[Sharding] Disabled by settings.yml.");
        }
        initializeBStats();
        showCompletionBanner();
        if (settingsConfig.getRoot().getBoolean("nametag.enabled", true)) {
            try {
                this.nametagManager = new PlayerNametagManager(
                        this,
                        getSettingsConfig().raw()
                );
                getLogger().info("[Nametag] Custom nametags initialized");
            } catch (Exception e) {
                getLogger().severe("[Nametag] Failed to initialize: " + e.getMessage());
                e.printStackTrace();
                this.nametagManager = null;
            }
        } else {
            this.nametagManager = null;
            getLogger().info("[Nametag] Disabled by settings.yml");
        }
        tryRegisterPlaceholderAPI();
        try {
            if (commandToggleService != null) {
                commandToggleService.applyToggles();
                getLogger().info("[CommandToggle] Command toggles applied successfully");
            }
        } catch (Exception e) {
            getLogger().severe("[CommandToggle] Failed to apply command toggles: " + e.getMessage());
            e.printStackTrace();
        }
        getLogger().info("OreoEssentials enabled.");
    }

    public fr.elias.oreoEssentials.playervaults.PlayerVaultsService getPlayerVaultsService() {
        return playervaultsService;

    }

    private void registerAllPacketsDeterministically(PacketManager pm) {

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

        pm.registerPacket(
                fr.elias.oreoEssentials.rabbitmq.packet.impl.DeathMessagePacket.class,
                fr.elias.oreoEssentials.rabbitmq.packet.impl.DeathMessagePacket::new
        );

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

        pm.registerPacket(
                fr.elias.oreoEssentials.rabbitmq.packet.impl.tp.TpJumpPacket.class,
                fr.elias.oreoEssentials.rabbitmq.packet.impl.tp.TpJumpPacket::new
        );

        pm.registerPacket(
                fr.elias.oreoEssentials.rabbitmq.packet.impl.tp.BackTeleportPacket.class,
                fr.elias.oreoEssentials.rabbitmq.packet.impl.tp.BackTeleportPacket::new
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


        pm.registerPacket(
                fr.elias.oreoEssentials.currency.rabbitmq.CurrencyTransferPacket.class,
                fr.elias.oreoEssentials.currency.rabbitmq.CurrencyTransferPacket::new
        );

        pm.registerPacket(
                fr.elias.oreoEssentials.currency.rabbitmq.CurrencyUpdatePacket.class,
                fr.elias.oreoEssentials.currency.rabbitmq.CurrencyUpdatePacket::new
        );

        pm.registerPacket(
                fr.elias.oreoEssentials.currency.rabbitmq.CurrencySyncPacket.class,
                fr.elias.oreoEssentials.currency.rabbitmq.CurrencySyncPacket::new
        );
        pm.registerPacket(
                fr.elias.oreoEssentials.rabbitmq.packet.impl.tp.AfkPoolEnterPacket.class,
                fr.elias.oreoEssentials.rabbitmq.packet.impl.tp.AfkPoolEnterPacket::new
        );

        pm.registerPacket(
                fr.elias.oreoEssentials.rabbitmq.packet.impl.tp.AfkPoolExitPacket.class,
                fr.elias.oreoEssentials.rabbitmq.packet.impl.tp.AfkPoolExitPacket::new
        );

        getLogger().info("[RABBIT] Registered " + 25 + " packet types deterministically");
    }

    public boolean isMessagingAvailable() {
        return packetManager != null && packetManager.isInitialized();
    }
    private void initCurrencySystem() {
        if (!settingsConfig.currencyEnabled()) {
            getLogger().info("[Currency] Disabled by settings.yml");
            return;
        }

        try {
            this.currencyConfig = new CurrencyConfig(this);
            getLogger().info("[Currency/DEBUG] file=" + new java.io.File(getDataFolder(), "currency-config.yml").getAbsolutePath());
            getLogger().info("[Currency/DEBUG] enabled=" + currencyConfig.isEnabled()
                    + " storage=" + currencyConfig.getStorageType()
                    + " cross=" + currencyConfig.isCrossServerEnabled()
                    + " homesMongoClient=" + (homesMongoClient != null));

            final CurrencyStorage currencyStorage;
            if (currencyConfig.useMongoStorage() && this.homesMongoClient != null) {
                String dbName = getConfig().getString("storage.mongo.database", "oreo");
                String prefix = getConfig().getString("storage.mongo.collectionPrefix", "oreo_");
                getLogger().info("[Currency/DEBUG] mongo db=" + getConfig().getString("storage.mongo.database")
                        + " prefix=" + getConfig().getString("storage.mongo.collectionPrefix"));

                currencyStorage = new MongoCurrencyStorage(this.homesMongoClient, dbName, prefix);
                getLogger().info("[Currency] Using MongoDB storage");
            } else {
                currencyStorage = new JsonCurrencyStorage(this);
                getLogger().info("[Currency] Using JSON storage");
            }

            this.currencyService = new CurrencyService(this, currencyStorage, currencyConfig);

            this.commands.register(new CurrencyCommand(this));
            this.commands.register(new CurrencyBalanceCommand(this));
            this.commands.register(new CurrencySendCommand(this));
            this.commands.register(new CurrencyTopCommand(this));

            if (getCommand("currency") != null) {
                getCommand("currency").setTabCompleter(new CurrencyCommandTabCompleter(this));
            }
            if (getCommand("currencybalance") != null) {
                getCommand("currencybalance").setTabCompleter(new CurrencyBalanceTabCompleter(this));
            }
            if (getCommand("currencysend") != null) {
                getCommand("currencysend").setTabCompleter(new CurrencySendTabCompleter(this));
            }
            if (getCommand("currencytop") != null) {
                getCommand("currencytop").setTabCompleter(new CurrencyTopTabCompleter(this));
            }

            if (currencyConfig.isCrossServerEnabled()
                    && packetManager != null
                    && packetManager.isInitialized()) {



                packetManager.subscribe(
                        fr.elias.oreoEssentials.currency.rabbitmq.CurrencySyncPacket.class,
                        (channel, pkt) -> {
                            try {
                                currencyService.handleCurrencySync(pkt);
                            } catch (Throwable t) {
                                getLogger().warning("[Currency] Failed to handle CurrencySyncPacket: " + t.getMessage());
                            }
                        }
                );

                getLogger().info("[Currency] Registered cross-server packets + sync listener");
            } else {
                getLogger().info("[Currency] Cross-server sync disabled or PacketManager not ready");
            }

            getLogger().info("[Currency] Enabled (currencies load async)");

        } catch (Throwable t) {
            getLogger().severe("[Currency] Failed to initialize: " + t.getMessage());
            t.printStackTrace();
        }
    }


    @Override
    public void onDisable() {
        getLogger().info("[SHUTDOWN] OreoEssentials disabling…");

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

        org.bukkit.Bukkit.getScheduler().cancelTasks(this);
        try {
            if (backBroker != null) {
                backBroker.shutdown();
                getLogger().info("[BackBroker] Shutdown complete.");
            }
        } catch (Exception ignored) {}

        try {
            pendingBackTeleports.clear();
            getLogger().info("[BackBroker] Cleared pending teleports.");
        } catch (Exception ignored) {}
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
        try { if (afkPoolService != null) afkPoolService.cleanupAll(); } catch (Exception ignored) {}
        try { if (shardsModule != null) shardsModule.disable(); } catch (Exception ignored) {}
        try { if (channelManager != null) channelManager.savePlayerData(); } catch (Exception ignored) {}
        try { if (autoRebootService != null) autoRebootService.stop(); } catch (Exception ignored) {}
        autoRebootService = null;

        try { if (tempFlyService != null) tempFlyService.shutdown(); } catch (Exception ignored) {}
        try {
            if (invlookManager != null) {
                invlookManager.clear();
            }
        } catch (Exception ignored) {}
        dailyStore = null;
        try { if (dailyStore != null) dailyStore.close(); } catch (Exception ignored) {}
        try { if (tradeService != null) tradeService.cancelAll(); } catch (Throwable ignored) {}
        try {
            if (placeholderHook != null) {
                placeholderHook.unregister();
                getLogger().info("PlaceholderAPI expansion unregistered");
            }
        } catch (Exception e) {
            getLogger().warning("Error unregistering PlaceholderAPI: " + e.getMessage());
        }
        try {
            if (nametagManager != null) {
                nametagManager.shutdown();
            }
        } catch (Exception ignored) {}
        this.healthBarListener = null;

        getLogger().info("OreoEssentials disabled.");
    }

    private void tryRegisterPlaceholderAPI() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            getLogger().info("PlaceholderAPI not found; skipping placeholders.");
            return;
        }

        try {
            placeholderHook = new PlaceholderAPIHook(this);

            Bukkit.getScheduler().runTaskLater(this, () -> {
                try {
                    if (placeholderHook.register()) {
                        getLogger().info("✓ PlaceholderAPI expansion 'oreo' registered successfully!");
                        getLogger().info("  Available: %oreo_balance%, %oreo_balance_formatted%, %oreo_network_online%, etc.");

                        if (getConfig().getBoolean("placeholder-debug", false)) {
                            getLogger().info("[PAPI TEST] Testing placeholder registration...");
                            Player testPlayer = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
                            if (testPlayer != null) {
                                String test = placeholderHook.onRequest(testPlayer, "network_online");
                                getLogger().info("[PAPI TEST] %oreo_network_online% = " + test);
                            }
                        }
                    } else {
                        getLogger().severe("✗ Failed to register PlaceholderAPI expansion!");
                        getLogger().severe("  Check if another plugin is using the 'oreo' identifier.");
                    }
                } catch (Exception e) {
                    getLogger().severe("✗ Error during PlaceholderAPI registration: " + e.getMessage());
                    e.printStackTrace();
                }
            }, 60L);

        } catch (Throwable t) {
            getLogger().severe("Failed to register PlaceholderAPI: " + t.getMessage());
            t.printStackTrace();
        }
    }
    private void showStartupBanner() {
        String version = getDescription().getVersion();

        getLogger().info("╔════════════════════════════════════════════════════════════╗");
        getLogger().info("║                                                            ║");
        getLogger().info("║               STARTING OREOESSENTIALS PREMIUM                  ║");
        getLogger().info("║                                                            ║");
        getLogger().info("║        Version: " + String.format("%-30s", version) +    " ║");
        getLogger().info("║                                                            ║");
        getLogger().info("║              Loading all features and modules...           ║");
        getLogger().info("║                                                            ║");
        getLogger().info("╚════════════════════════════════════════════════════════════╝");
    }
    private void checkProtocolLib() {
        boolean hasProtocolLib = Bukkit.getPluginManager().getPlugin("ProtocolLib") != null;
        boolean tabEnabled = settingsConfig.tabEnabled();

        if (tabEnabled && !hasProtocolLib) {
            getLogger().warning("╔════════════════════════════════════════════════════════════╗");
            getLogger().warning("║                    ⚠ WARNING ⚠                             ║");
            getLogger().warning("║                                                            ║");
            getLogger().warning("║  ProtocolLib is NOT INSTALLED!                             ║");
            getLogger().warning("║                                                            ║");
            getLogger().warning("║  Your custom tab-list will NOT work without ProtocolLib.   ║");
            getLogger().warning("║                                                            ║");
            getLogger().warning("║  Download: https://www.spigotmc.org/resources/1997/        ║");
            getLogger().warning("║                                                            ║");
            getLogger().warning("║  Install ProtocolLib and restart your server.              ║");
            getLogger().warning("╚════════════════════════════════════════════════════════════╝");
        } else if (tabEnabled && hasProtocolLib) {
            // Show success box when both are enabled
            getLogger().info("╔════════════════════════════════════════════════════════════╗");
            getLogger().info("║                    ✓ SUCCESS ✓                             ║");
            getLogger().info("║                                                            ║");
            getLogger().info("║  ProtocolLib detected!                                     ║");
            getLogger().info("║                                                            ║");
            getLogger().info("║  Custom tab-list features are available.                  ║");
            getLogger().info("╚════════════════════════════════════════════════════════════╝");
        } else if (!tabEnabled && !hasProtocolLib) {
            // Both disabled - informational message
            getLogger().info("╔════════════════════════════════════════════════════════════╗");
            getLogger().info("║                  ℹ INFORMATION ℹ                           ║");
            getLogger().info("║                                                            ║");
            getLogger().info("║  Custom tab-list is disabled (settings.yml).              ║");
            getLogger().info("║                                                            ║");
            getLogger().info("║  Install ProtocolLib to enable custom tab features.       ║");
            getLogger().info("║  Download: https://www.spigotmc.org/resources/1997/        ║");
            getLogger().info("╚════════════════════════════════════════════════════════════╝");
        }
    }
    private void showCompletionBanner() {
        long totalOnline = Bukkit.getOnlinePlayers().size();

        getLogger().info("╔════════════════════════════════════════════════════════════╗");
        getLogger().info("║                                                            ║");
        getLogger().info("║              ✓ OREOESSENTIALS READY ✓                     ║");
        getLogger().info("║                                                            ║");
        getLogger().info("║  All features loaded successfully!                        ║");
        getLogger().info("║                                                            ║");
        getLogger().info("║  Players online: " + String.format("%-42d", totalOnline) + " ║");
        getLogger().info("║                                                            ║");
        getLogger().info("╚════════════════════════════════════════════════════════════╝");
    }
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
    public InvlookManager getInvlookManager() { return invlookManager; }
    public TeleportService getTeleportService() { return teleportService; }
    public BackService getBackService() { return backService; }
    public MessageService getMessageService() { return messageService; }
    public DeathBackService getDeathBackService() { return deathBackService; }
    public GodService getGodService() { return godService; }
    public CommandManager getCommands() { return commands; }
    public ChatSyncManager getChatSyncManager() { return chatSyncManager; }
    public fr.elias.oreoEssentials.mobs.HealthBarListener getHealthBarListener() { return healthBarListener; }
    public FreezeService getFreezeService() { return freezeService; }
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
    public fr.elias.oreoEssentials.homes.HomeTeleportBroker getHomeTeleportBroker() { return homeTpBroker; }

    @SuppressWarnings("unchecked")
    private void unregisterCommandHard(String label) {
        try {
            Object craftServer = getServer();
            org.bukkit.command.CommandMap commandMap = null;

            try {
                var m = craftServer.getClass().getMethod("getCommandMap");
                Object res = m.invoke(craftServer);
                if (res instanceof org.bukkit.command.CommandMap cm) {
                    commandMap = cm;
                }
            } catch (Throwable ignored) {}

            if (commandMap == null) {
                var f = craftServer.getClass().getDeclaredField("commandMap");
                f.setAccessible(true);
                Object res = f.get(craftServer);
                if (res instanceof org.bukkit.command.CommandMap cm) {
                    commandMap = cm;
                }
            }

            if (!(commandMap instanceof org.bukkit.command.SimpleCommandMap map)) return;

            var f2 = org.bukkit.command.SimpleCommandMap.class.getDeclaredField("knownCommands");
            f2.setAccessible(true);
            Map<String, org.bukkit.command.Command> known = (Map<String, Command>) f2.get(map);

            String lower = label.toLowerCase(java.util.Locale.ROOT);

            known.entrySet().removeIf(e -> {
                String k = e.getKey().toLowerCase(java.util.Locale.ROOT);
                if (!k.equals(lower) && !k.endsWith(":" + lower)) return false;

                org.bukkit.command.Command cmd = e.getValue();
                if (!(cmd instanceof org.bukkit.command.PluginCommand pc)) return false;

                try {
                    return pc.getPlugin() == this;
                } catch (Throwable ignored) {
                    return false;
                }
            });

        } catch (Throwable ignored) {}
    }

    public fr.elias.oreoEssentials.playtime.PlaytimeRewardsService getPlaytimeRewardsService() {
        return this.playtimeRewards;
    }

    public final class PapiUtil {
        private PapiUtil() {}

        public static String apply(Player p, String text) {
            if (text == null) return "";
            if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) return text;
            try {
                return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(p, text);
            } catch (Throwable t) {
                return text;
            }
        }
    }

    public String getServerNameSafe() {
        try {
            if (configService != null) {
                String name = configService.serverName();
                if (name != null && !name.isBlank()) {
                    return name;
                }
            }
        } catch (Throwable ignored) {}

        try {
            String bukkitName = getServer().getName();
            if (bukkitName != null && !bukkitName.isBlank()) {
                return bukkitName;
            }
        } catch (Throwable ignored) {}

        return "UNKNOWN";
    }
    private void initializeBStats() {
        try {
            int pluginId = 28852;
            this.metrics = new Metrics(this, pluginId);
            metrics.addCustomChart(new SimplePie("storage_type", () -> {
                String storageType = getConfig().getString("essentials.storage", "yaml");
                return storageType.toUpperCase();
            }));
            metrics.addCustomChart(new SimplePie("economy_type", () -> {
                if (!economyEnabled) return "Disabled";
                String ecoType = getConfig().getString("economy.type", "none");
                return ecoType.toUpperCase();
            }));
            metrics.addCustomChart(new AdvancedPie("enabled_features", () -> {
                Map<String, Integer> features = new HashMap<>();

                if (settingsConfig.kitsEnabled()) features.put("Kits", 1);
                if (settingsConfig.tradeEnabled()) features.put("Trade", 1);
                if (settingsConfig.rtpEnabled()) features.put("RTP", 1);
                if (settingsConfig.bossbarEnabled()) features.put("BossBar", 1);
                if (settingsConfig.scoreboardEnabled()) features.put("Scoreboard", 1);
                if (settingsConfig.tabEnabled()) features.put("Tab", 1);
                if (settingsConfig.clearLagEnabled()) features.put("ClearLag", 1);
                if (settingsConfig.oreoHologramsEnabled()) features.put("Holograms", 1);
                if (settingsConfig.playtimeRewardsEnabled()) features.put("PlaytimeRewards", 1);
                if (settingsConfig.worldShardingEnabled()) features.put("Sharding", 1);

                return features;
            }));

            metrics.addCustomChart(new SimplePie("cross_server_mode", () -> {
                if (!rabbitEnabled) return "Disabled";

                boolean anyCross = crossServerSettings.homes()
                        || crossServerSettings.warps()
                        || crossServerSettings.spawn()
                        || crossServerSettings.economy();

                return anyCross ? "Enabled" : "Disabled";
            }));

            metrics.addCustomChart(new SimplePie("redis_enabled", () ->
                    redisEnabled ? "Enabled" : "Disabled"
            ));

            metrics.addCustomChart(new SingleLineChart("total_kits", () -> {
                if (kitsManager == null) return 0;
                return kitsManager.getKits().size();
            }));

            metrics.addCustomChart(new SingleLineChart("custom_recipes", () -> {
                if (customCraftingService == null) return 0;
                return customCraftingService.getRecipeCount();
            }));

            getLogger().info("[bStats] Metrics initialized successfully!");
            getLogger().info("[bStats] View your stats at: https://bstats.org/plugin/bukkit/" + pluginId);

        } catch (Exception e) {
            getLogger().warning("[bStats] Failed to initialize metrics: " + e.getMessage());
        }
    }
    public ModBridge getModBridge() { return modBridge; }
    public java.util.Map<java.util.UUID, Long> getRtpCooldownCache() { return rtpCooldownCache; }
    public fr.elias.oreoEssentials.playtime.PlaytimeTracker getPlaytimeTracker() { return this.playtimeTracker; }
    public PlayerNametagManager getNametagManager() { return nametagManager; }
    public SettingsConfig getSettings() { return settings; }
    public RtpPendingService getRtpPendingService() { return rtpPendingService; }
    public fr.elias.oreoEssentials.rtp.RtpCrossServerBridge getRtpBridge() { return rtpBridge; }
    public fr.elias.oreoEssentials.shards.OreoShardsModule getShardsModule() { return shardsModule; }
    public EconomyBootstrap getEcoBootstrap() { return ecoBootstrap; }
    public Economy getVaultEconomy() { return vaultEconomy; }
    public CurrencyPlaceholderExpansion getCurrencyPlaceholders() {
        return currencyPlaceholders;
    }
}


