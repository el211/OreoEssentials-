package fr.elias.oreoEssentials.modules.holograms;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import de.oliver.fancyanalytics.api.FancyAnalyticsAPI;
import de.oliver.fancyanalytics.api.metrics.MetricSupplier;
import de.oliver.fancyanalytics.logger.ExtendedFancyLogger;
import de.oliver.fancyanalytics.logger.LogLevel;
import de.oliver.fancyanalytics.logger.appender.Appender;
import de.oliver.fancyanalytics.logger.appender.ConsoleAppender;
import de.oliver.fancyanalytics.logger.appender.JsonAppender;
import de.oliver.fancylib.FancyLib;
import de.oliver.fancylib.Metrics;
import de.oliver.fancylib.VersionConfig;
import de.oliver.fancylib.serverSoftware.ServerSoftware;
import de.oliver.fancylib.versionFetcher.VersionFetcher;
import de.oliver.fancysitula.api.IFancySitula;
import de.oliver.fancysitula.api.utils.ServerVersion;
import fr.elias.oreoEssentials.modules.holograms.api.HologramConfiguration;
import fr.elias.oreoEssentials.modules.holograms.api.HologramManager;
import fr.elias.oreoEssentials.modules.holograms.api.HologramStorage;
import fr.elias.oreoEssentials.modules.holograms.api.OHologramsPlugin;
import fr.elias.oreoEssentials.modules.holograms.api.data.HologramData;
import fr.elias.oreoEssentials.modules.holograms.api.hologram.Hologram;
import fr.elias.oreoEssentials.modules.holograms.commands.HologramCMD;
import fr.elias.oreoEssentials.modules.holograms.commands.OHologramsCMD;
import fr.elias.oreoEssentials.modules.holograms.commands.OHologramsTestCMD;
import fr.elias.oreoEssentials.modules.holograms.hologram.version.HologramImpl;
import fr.elias.oreoEssentials.modules.holograms.listeners.BedrockPlayerListener;
import fr.elias.oreoEssentials.modules.holograms.listeners.NpcListener;
import fr.elias.oreoEssentials.modules.holograms.listeners.PlayerListener;
import fr.elias.oreoEssentials.modules.holograms.listeners.PlayerLoadedListener;
import fr.elias.oreoEssentials.modules.holograms.listeners.WorldListener;
import fr.elias.oreoEssentials.modules.holograms.storage.FlatFileHologramStorage;
import fr.elias.oreoEssentials.modules.holograms.storage.converter.FHConversionRegistry;
import fr.elias.oreoEssentials.modules.holograms.util.PluginUtils;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static java.util.concurrent.CompletableFuture.supplyAsync;

public final class OHolograms implements OHologramsPlugin {

    private static @Nullable OHolograms INSTANCE;
    private static final String EMBEDDED_DOWNLOAD_URL = "embedded-in-oreoessentials";

    private final JavaPlugin plugin;
    private final ExtendedFancyLogger fancyLogger;
    private final VersionFetcher versionFetcher;
    private final VersionConfig versionConfig;
    private final ScheduledExecutorService hologramThread = Executors.newSingleThreadScheduledExecutor(
            new ThreadFactoryBuilder().setNameFormat("OHolograms-Holograms").build()
    );
    private final ExecutorService fileStorageExecutor = Executors.newSingleThreadExecutor(
            new ThreadFactoryBuilder()
                    .setDaemon(true)
                    .setPriority(Thread.MIN_PRIORITY + 1)
                    .setNameFormat("OHolograms-FileStorageExecutor")
                    .build()
    );

    private FancyAnalyticsAPI fancyAnalytics;
    private HologramConfiguration configuration = new OHologramsConfiguration();
    private HologramStorage hologramStorage = new FlatFileHologramStorage();
    private @Nullable HologramManagerImpl hologramsManager;
    private FileConfiguration config;
    private boolean enabled;
    private boolean versionMetadataAvailable;

    public OHolograms(@NotNull JavaPlugin plugin) {
        INSTANCE = this;
        this.plugin = plugin;
        this.versionFetcher = new EmbeddedVersionFetcher(plugin.getDescription().getVersion());
        this.versionConfig = new VersionConfig(plugin, versionFetcher);
        this.config = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "config.yml"));
        OHologramsPlugin.EnabledChecker.setPlugin(this);

        Appender consoleAppender = new ConsoleAppender("[{loggerName}] ({threadName}) {logLevel}: {message}");
        String date = new SimpleDateFormat("yyyy-MM-dd").format(new Date(System.currentTimeMillis()));
        File logsFile = new File(getDataFolder(), "logs/OH-logs-" + date + ".txt");
        if (!logsFile.exists()) {
            try {
                logsFile.getParentFile().mkdirs();
                logsFile.createNewFile();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        JsonAppender jsonAppender = new JsonAppender(false, false, true, logsFile.getPath());
        this.fancyLogger = new ExtendedFancyLogger(
                "OHolograms",
                LogLevel.INFO,
                List.of(consoleAppender, jsonAppender),
                List.of()
        );
    }

    public static @NotNull OHolograms bootstrap(@NotNull JavaPlugin plugin) {
        OHolograms instance = new OHolograms(plugin);
        instance.onLoad();
        try {
            instance.onEnable();
        } catch (Throwable t) {
            instance.getFancyLogger().warn("Embedded startup encountered a non-fatal error after core initialization: " + t.getMessage());
        }
        return instance;
    }

    public void shutdown() {
        onDisable();
    }

    public static @NotNull OHolograms get() {
        return Objects.requireNonNull(INSTANCE, "plugin is not initialized");
    }

    public static boolean canGet() {
        return INSTANCE != null;
    }

    public void onLoad() {
        final var adapter = resolveHologramAdapter();

        if (adapter == null) {
            fancyLogger.warn("""
                    --------------------------------------------------
                    Unsupported minecraft server version.
                    Please update the server to one of (%s).
                    Embedded OHolograms will stay disabled.
                    --------------------------------------------------
                    """.formatted(String.join(" / ", ServerVersion.getSupportedVersions())));
            return;
        }

        hologramsManager = new HologramManagerImpl(this, adapter);
        enabled = true;
        fancyLogger.info("Successfully loaded OHolograms version %s".formatted(plugin.getDescription().getVersion()));
    }

    public void onEnable() {
        if (!enabled) {
            return;
        }

        fancyLogger.info("Enable stage: reload-config");
        getHologramConfiguration().reload(this);

        fancyLogger.info("Enable stage: fancylib");
        new FancyLib(plugin);

        if (!ServerSoftware.isPaper()) {
            fancyLogger.warn("""
                    --------------------------------------------------
                    It is recommended to use Paper as server software.
                    Because you are not using paper, the plugin
                    might not work correctly.
                    --------------------------------------------------
                    """);
        }

        fancyLogger.info("Enable stage: log-level");
        LogLevel logLevel;
        try {
            logLevel = LogLevel.valueOf(getHologramConfiguration().getLogLevel());
        } catch (IllegalArgumentException e) {
            logLevel = LogLevel.INFO;
        }
        fancyLogger.setCurrentLevel(logLevel);
        IFancySitula.LOGGER.setCurrentLevel(logLevel);

        fancyLogger.info("Enable stage: feature-flags");
        try {
            FHFeatureFlags.load();
        } catch (Throwable t) {
            fancyLogger.warn("Failed to load feature flags, continuing with defaults: " + t.getMessage());
        }

        fancyLogger.info("Enable stage: commands");
        reloadCommands();
        fancyLogger.info("Enable stage: listeners");
        registerListeners();
        fancyLogger.info("Enable stage: init-tasks");
        getHologramsManager().initializeTasks();

        fancyLogger.info("Enable stage: version-config");
        versionMetadataAvailable = false;
        try {
            versionConfig.load();
            versionMetadataAvailable = true;
        } catch (Throwable t) {
            fancyLogger.warn("Failed to load version config, continuing without it: " + t.getMessage());
        }
        if (versionMetadataAvailable && getHologramConfiguration().areVersionNotificationsEnabled()) {
            fancyLogger.info("Enable stage: version-check");
            checkForNewerVersion();
        }

        fancyLogger.info("Enable stage: metrics");
        try {
            registerMetrics();
        } catch (Throwable t) {
            fancyLogger.warn("Failed to initialize metrics, continuing startup: " + t.getMessage());
        }

        if (getHologramConfiguration().isAutosaveEnabled()) {
            fancyLogger.info("Enable stage: autosave");
            getHologramThread().scheduleAtFixedRate(() -> {
                if (hologramsManager != null) {
                    hologramsManager.saveHolograms();
                }
            }, getHologramConfiguration().getAutosaveInterval(), getHologramConfiguration().getAutosaveInterval() * 60L, TimeUnit.SECONDS);
        }

        fancyLogger.info("Enable stage: converters");
        FHConversionRegistry.registerBuiltInConverters();
        fancyLogger.info("Successfully enabled OHolograms version %s".formatted(plugin.getDescription().getVersion()));
    }

    public void onDisable() {
        if (hologramsManager != null) {
            hologramsManager.saveHolograms();
        }
        hologramThread.shutdown();
        fileStorageExecutor.shutdown();
        try {
            if (!hologramThread.awaitTermination(5, TimeUnit.SECONDS)) {
                fancyLogger.warn("Timed out while waiting for hologram tasks to finish during shutdown.");
            }
            if (!fileStorageExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                fancyLogger.warn("Timed out while waiting for hologram storage writes to finish during shutdown.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fancyLogger.warn("Interrupted while waiting for hologram shutdown tasks to finish.");
        }
        INSTANCE = null;
        OHologramsPlugin.EnabledChecker.setPlugin(null);
        enabled = false;
        fancyLogger.info("Successfully disabled OHolograms version %s".formatted(plugin.getDescription().getVersion()));
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public JavaPlugin getPlugin() {
        return plugin;
    }

    @Override
    public ExtendedFancyLogger getFancyLogger() {
        return fancyLogger;
    }

    public @NotNull VersionFetcher getVersionFetcher() {
        return versionFetcher;
    }

    public @NotNull VersionConfig getVersionConfig() {
        return versionConfig;
    }

    @ApiStatus.Internal
    public @NotNull HologramManagerImpl getHologramsManager() {
        return Objects.requireNonNull(this.hologramsManager, "plugin is not initialized");
    }

    @Override
    public HologramManager getHologramManager() {
        return Objects.requireNonNull(this.hologramsManager, "plugin is not initialized");
    }

    @Override
    public HologramConfiguration getHologramConfiguration() {
        return configuration;
    }

    @Override
    public void setHologramConfiguration(HologramConfiguration configuration, boolean reload) {
        this.configuration = configuration;
        if (reload) {
            configuration.reload(this);
            reloadCommands();
        }
    }

    @Override
    public HologramStorage getHologramStorage() {
        return hologramStorage;
    }

    @Override
    public void setHologramStorage(HologramStorage storage, boolean reload) {
        this.hologramStorage = storage;
        if (reload) {
            getHologramsManager().reloadHolograms();
        }
    }

    public ScheduledExecutorService getHologramThread() {
        return hologramThread;
    }

    public ExecutorService getFileStorageExecutor() {
        return this.fileStorageExecutor;
    }

    private @Nullable Function<HologramData, Hologram> resolveHologramAdapter() {
        final var version = Bukkit.getMinecraftVersion();
        if (ServerVersion.isVersionSupported(version)) {
            return HologramImpl::new;
        }
        if (version.startsWith("1.21")) {
            fancyLogger.warn("Server version %s is not in FancySitula's supported list, using the 1.21 display adapter fallback.".formatted(version));
            return HologramImpl::new;
        }
        return null;
    }

    public void reloadCommands() {
        HologramCMD hologramCommand = new HologramCMD(this);
        OHologramsCMD managerCommand = new OHologramsCMD(this);

        if (getHologramConfiguration().isRegisterCommands()) {
            bindPluginCommand("hologram", hologramCommand);
            bindPluginCommand("ohologram", hologramCommand);
            bindPluginCommand("oholograms", managerCommand);
        } else {
            unbindPluginCommand("hologram");
            unbindPluginCommand("ohologram");
            unbindPluginCommand("oholograms");
        }

        if (false) {
            bindPluginCommand("ohologramstest", new OHologramsTestCMD(this));
        }
    }

    private void bindPluginCommand(@NotNull String label, @NotNull Command command) {
        PluginCommand pluginCommand = plugin.getCommand(label);
        if (pluginCommand == null) {
            return;
        }
        CommandExecutor executor = (sender, cmd, usedLabel, args) -> command.execute(sender, usedLabel, args);
        TabCompleter completer = (sender, cmd, usedLabel, args) -> command.tabComplete(sender, usedLabel, args);
        pluginCommand.setExecutor(executor);
        pluginCommand.setTabCompleter(completer);
        pluginCommand.setPermission(command.getPermission());
    }

    private void unbindPluginCommand(@NotNull String label) {
        PluginCommand pluginCommand = plugin.getCommand(label);
        if (pluginCommand == null) {
            return;
        }
        pluginCommand.setExecutor(null);
        pluginCommand.setTabCompleter(null);
    }

    private void registerListeners() {
        plugin.getServer().getPluginManager().registerEvents(new PlayerListener(this), plugin);
        plugin.getServer().getPluginManager().registerEvents(new WorldListener(), plugin);
        if (Set.of("1.21.4", "1.21.5", "1.21.6", "1.21.7", "1.21.8", "1.21.9", "1.21.10", "1.21.11", "26.1").contains(Bukkit.getMinecraftVersion())) {
            plugin.getServer().getPluginManager().registerEvents(new PlayerLoadedListener(), plugin);
        }
        if (PluginUtils.isFancyNpcsEnabled()) {
            plugin.getServer().getPluginManager().registerEvents(new NpcListener(this), plugin);
        }
        if (FHFeatureFlags.DISABLE_HOLOGRAMS_FOR_BEDROCK_PLAYERS.isEnabled() && PluginUtils.isFloodgateEnabled()) {
            plugin.getServer().getPluginManager().registerEvents(new BedrockPlayerListener(), plugin);
        }
    }

    private void checkForNewerVersion() {
        final var current = new ComparableVersion(versionConfig.getVersion());
        supplyAsync(getVersionFetcher()::fetchNewestVersion).thenApply(Objects::requireNonNull).whenComplete((newest, error) -> {
            if (error != null || newest.compareTo(current) <= 0) {
                return;
            }
            fancyLogger.warn("""
                    
                    -------------------------------------------------------
                    You are not using the latest version of the OHolograms plugin.
                    Please update to the newest version (%s).
                    %s
                    -------------------------------------------------------
                    """.formatted(newest, getVersionFetcher().getDownloadUrl()));
        });
    }

    private void registerMetrics() {
        if (!versionMetadataAvailable) {
            return;
        }

        Metrics metrics = new Metrics(plugin, 17990);
        metrics.addCustomChart(new Metrics.SingleLineChart("total_holograms", () -> hologramsManager.getHolograms().size()));
        metrics.addCustomChart(new Metrics.SimplePie("update_notifications", () -> configuration.areVersionNotificationsEnabled() ? "Yes" : "No"));
        metrics.addCustomChart(new Metrics.SimplePie("using_development_build", () -> isDevelopmentBuild() ? "Yes" : "No"));

        fancyAnalytics = new FancyAnalyticsAPI("3b77bd59-2b01-46f2-b3aa-a9584401797f", "E2gW5zc2ZTk1OGFkNGY2ZDQ0ODlM6San");
        fancyAnalytics.getConfig().setDisableLogging(true);

        if (!isDevelopmentBuild()) {
            return;
        }

        fancyAnalytics.registerMinecraftPluginMetrics(plugin);
        fancyAnalytics.getExceptionHandler().registerLogger(plugin.getLogger());
        fancyAnalytics.getExceptionHandler().registerLogger(Bukkit.getLogger());
        fancyAnalytics.getExceptionHandler().registerLogger(fancyLogger);

        fancyAnalytics.registerStringMetric(new MetricSupplier<>("commit_hash", () -> {
            String hash = versionConfig.getHash();
            return hash.length() > 7 ? hash.substring(0, 7) : hash;
        }));
        fancyAnalytics.registerStringMetric(new MetricSupplier<>("server_size", () -> {
            long onlinePlayers = Bukkit.getOnlinePlayers().size();
            if (onlinePlayers == 0) return "empty";
            if (onlinePlayers <= 25) return "small";
            if (onlinePlayers <= 100) return "medium";
            if (onlinePlayers <= 500) return "large";
            return "very_large";
        }));
        fancyAnalytics.registerNumberMetric(new MetricSupplier<>("amount_holograms", () -> (double) hologramsManager.getHolograms().size()));
        fancyAnalytics.registerStringMetric(new MetricSupplier<>("enabled_update_notifications", () -> configuration.areVersionNotificationsEnabled() ? "true" : "false"));
        fancyAnalytics.registerStringMetric(new MetricSupplier<>("fflag_disable_holograms_for_bedrock_players", () -> FHFeatureFlags.DISABLE_HOLOGRAMS_FOR_BEDROCK_PLAYERS.isEnabled() ? "true" : "false"));
        fancyAnalytics.registerStringMetric(new MetricSupplier<>("using_development_build", () -> isDevelopmentBuild() ? "true" : "false"));
        fancyAnalytics.registerStringArrayMetric(new MetricSupplier<>("hologram_type", () -> {
            if (hologramsManager == null) {
                return new String[0];
            }
            return hologramsManager.getHolograms().stream().map(h -> h.getData().getType().name()).toArray(String[]::new);
        }));
        fancyAnalytics.initialize();
    }

    public FancyAnalyticsAPI getFancyAnalytics() {
        return fancyAnalytics;
    }

    private boolean isDevelopmentBuild() {
        if (!versionMetadataAvailable) {
            return false;
        }
        String build = versionConfig.getBuild();
        return build != null && !build.equalsIgnoreCase("release");
    }

    public File getDataFolder() {
        File dir = new File(plugin.getDataFolder(), "OHolograms");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public void reloadConfig() {
        File file = new File(getDataFolder(), "config.yml");
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (Exception ignored) {
            }
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    public void saveConfig() {
        try {
            config.save(new File(getDataFolder(), "config.yml"));
        } catch (Exception e) {
            plugin.getLogger().warning("[OHolograms] Failed to save embedded config: " + e.getMessage());
        }
    }

    private static final class EmbeddedVersionFetcher implements VersionFetcher {
        private final ComparableVersion currentVersion;

        private EmbeddedVersionFetcher(@NotNull String currentVersion) {
            this.currentVersion = new ComparableVersion(currentVersion);
        }

        @Override
        public ComparableVersion fetchNewestVersion() {
            return currentVersion;
        }

        @Override
        public String getDownloadUrl() {
            return EMBEDDED_DOWNLOAD_URL;
        }
    }
}
