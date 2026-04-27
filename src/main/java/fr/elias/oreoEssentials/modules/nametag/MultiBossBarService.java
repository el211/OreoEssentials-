package fr.elias.oreoEssentials.modules.nametag;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.util.OreScheduler;
import fr.elias.oreoEssentials.util.OreTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Multiple boss bars with per-player conditions and carousel text rotation.
 * Configured under the {@code bossbars} section in custom-nameplates/config.yml.
 *
 * Each bar entry supports:
 *  - Multiple rotating texts (carousel)
 *  - Conditions to show/hide per player
 *  - Color, style, progress
 *  - Independent refresh and carousel intervals
 */
public final class MultiBossBarService implements Listener {

    // ── Inner model ───────────────────────────────────────────────────────────

    private static final class BarConfig {
        final String id;
        final List<String> texts;
        final int carouselIntervalTicks;
        final BarColor color;
        final BarStyle style;
        final double progress;
        final List<NametageCondition> conditions;

        BarConfig(String id, List<String> texts, int carouselIntervalTicks,
                  BarColor color, BarStyle style, double progress,
                  List<NametageCondition> conditions) {
            this.id = id;
            this.texts = texts;
            this.carouselIntervalTicks = Math.max(1, carouselIntervalTicks);
            this.color = color;
            this.style = style;
            this.progress = Math.max(0, Math.min(1, progress));
            this.conditions = conditions;
        }
    }

    // ── Config ────────────────────────────────────────────────────────────────
    private boolean enabled;
    private int refreshIntervalTicks;
    private List<BarConfig> barConfigs = new ArrayList<>();

    // ── State ─────────────────────────────────────────────────────────────────
    /** barId → (playerUUID → BossBar) */
    private final ConcurrentHashMap<String, ConcurrentHashMap<UUID, BossBar>> activeBars = new ConcurrentHashMap<>();
    /** barId → carousel tick counter per player */
    private final ConcurrentHashMap<String, ConcurrentHashMap<UUID, int[]>> carouselCounters = new ConcurrentHashMap<>();
    /** barId → last rendered title per player */
    private final ConcurrentHashMap<String, ConcurrentHashMap<UUID, String>> lastTitles = new ConcurrentHashMap<>();

    private OreTask updateTask;

    private final OreoEssentials plugin;
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    // ── Constructor ───────────────────────────────────────────────────────────

    public MultiBossBarService(OreoEssentials plugin, FileConfiguration config) {
        this.plugin = plugin;
        loadConfig(config);

        if (enabled) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
            startTask();
            OreScheduler.runLater(plugin, () -> {
                for (Player p : Bukkit.getOnlinePlayers()) onJoinInit(p);
            }, 20L);
            plugin.getLogger().info("[MultiBossBar] Enabled (" + barConfigs.size() + " bar(s)).");
        } else {
            plugin.getLogger().info("[MultiBossBar] Disabled in config.");
        }
    }

    // ── Config ────────────────────────────────────────────────────────────────

    private void loadConfig(FileConfiguration config) {
        ConfigurationSection root = config.getConfigurationSection("bossbars");
        this.enabled = root != null && root.getBoolean("enabled", false);
        if (root == null) return;

        this.refreshIntervalTicks = Math.max(1, root.getInt("refresh-interval-ticks", 40));

        barConfigs.clear();
        ConfigurationSection bars = root.getConfigurationSection("bars");
        if (bars == null) return;

        for (String key : bars.getKeys(false)) {
            ConfigurationSection s = bars.getConfigurationSection(key);
            if (s == null) continue;
            try {
                List<String> texts;
                if (s.isList("texts")) {
                    texts = s.getStringList("texts");
                } else {
                    String t = s.getString("text", "");
                    texts = t.isEmpty() ? List.of("") : List.of(t);
                }

                int carouselTicks = s.getInt("carousel-interval-ticks", 60);

                BarColor color;
                try { color = BarColor.valueOf(s.getString("color", "PURPLE").toUpperCase()); }
                catch (Exception e) { color = BarColor.PURPLE; }

                BarStyle style;
                try { style = BarStyle.valueOf(s.getString("style", "SOLID").toUpperCase()); }
                catch (Exception e) { style = BarStyle.SOLID; }

                double progress = s.getDouble("progress", 1.0);
                List<NametageCondition> conditions = NametageCondition.parseList(s, "conditions");

                barConfigs.add(new BarConfig(key, texts, carouselTicks, color, style, progress, conditions));
            } catch (Exception e) {
                plugin.getLogger().warning("[MultiBossBar] Failed to parse bar '" + key + "': " + e.getMessage());
            }
        }
    }

    // ── Task ──────────────────────────────────────────────────────────────────

    private void startTask() {
        if (updateTask != null) updateTask.cancel();
        updateTask = OreScheduler.runTimer(plugin, this::updateAll, refreshIntervalTicks, refreshIntervalTicks);
    }

    private void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            updatePlayer(player);
        }
    }

    private void updatePlayer(Player player) {
        for (BarConfig cfg : barConfigs) {
            boolean shouldShow = NametageCondition.evaluateAll(cfg.conditions, player);

            ConcurrentHashMap<UUID, BossBar> playerBars = activeBars.computeIfAbsent(cfg.id, k -> new ConcurrentHashMap<>());
            ConcurrentHashMap<UUID, int[]> counters = carouselCounters.computeIfAbsent(cfg.id, k -> new ConcurrentHashMap<>());
            ConcurrentHashMap<UUID, String> titles = lastTitles.computeIfAbsent(cfg.id, k -> new ConcurrentHashMap<>());

            if (shouldShow) {
                // Get or create the bar
                BossBar bar = playerBars.computeIfAbsent(player.getUniqueId(), k -> {
                    BossBar b = Bukkit.createBossBar("", cfg.color, cfg.style);
                    b.addPlayer(player);
                    return b;
                });

                // Tick carousel
                int[] counter = counters.computeIfAbsent(player.getUniqueId(), k -> new int[]{0});
                counter[0] += refreshIntervalTicks;
                int carouselIndex = (counter[0] / cfg.carouselIntervalTicks) % cfg.texts.size();

                String raw = cfg.texts.get(carouselIndex);
                String resolved = resolvePapi(raw, player);
                String legacy = renderToLegacy(resolved);

                String previous = titles.put(player.getUniqueId(), legacy);
                if (!legacy.equals(previous)) {
                    bar.setTitle(legacy);
                }
                if (bar.getColor() != cfg.color) bar.setColor(cfg.color);
                if (bar.getStyle() != cfg.style) bar.setStyle(cfg.style);
                if (Math.abs(bar.getProgress() - cfg.progress) > 0.0001d) bar.setProgress(cfg.progress);
                if (!bar.getPlayers().contains(player)) bar.addPlayer(player);

            } else {
                // Hide the bar from this player
                BossBar bar = playerBars.remove(player.getUniqueId());
                counters.remove(player.getUniqueId());
                titles.remove(player.getUniqueId());
                if (bar != null) {
                    try { bar.removeAll(); } catch (Throwable ignored) {}
                }
            }
        }
    }

    private void onJoinInit(Player player) {
        // Initialize carousel counters and show bars immediately
        updatePlayer(player);
    }

    private void removePlayer(Player player) {
        for (Map.Entry<String, ConcurrentHashMap<UUID, BossBar>> entry : activeBars.entrySet()) {
            BossBar bar = entry.getValue().remove(player.getUniqueId());
            if (bar != null) {
                try { bar.removeAll(); } catch (Throwable ignored) {}
            }
            ConcurrentHashMap<UUID, int[]> counters = carouselCounters.get(entry.getKey());
            if (counters != null) counters.remove(player.getUniqueId());
            ConcurrentHashMap<UUID, String> titles = lastTitles.get(entry.getKey());
            if (titles != null) titles.remove(player.getUniqueId());
        }
    }

    // ── Events ────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!enabled) return;
        OreScheduler.runLater(plugin, () -> {
            if (event.getPlayer().isOnline()) onJoinInit(event.getPlayer());
        }, 20L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (!enabled) return;
        removePlayer(event.getPlayer());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String resolvePapi(String text, Player player) {
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
            }
        } catch (Throwable ignored) {}
        return text;
    }

    private String renderToLegacy(String text) {
        try {
            Component c = MM.deserialize(text);
            return LEGACY.serialize(c);
        } catch (Throwable e) {
            return text;
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public boolean isEnabled() { return enabled; }

    public void reload(FileConfiguration config) {
        // Stop existing task & clear all bars
        shutdown();
        activeBars.clear();
        carouselCounters.clear();
        lastTitles.clear();

        loadConfig(config);

        if (enabled) {
            startTask();
            OreScheduler.runLater(plugin, () -> {
                for (Player p : Bukkit.getOnlinePlayers()) onJoinInit(p);
            }, 10L);
            plugin.getLogger().info("[MultiBossBar] Reloaded (" + barConfigs.size() + " bar(s)).");
        }
    }

    public void shutdown() {
        if (updateTask != null) { updateTask.cancel(); updateTask = null; }
        for (ConcurrentHashMap<UUID, BossBar> map : activeBars.values()) {
            for (BossBar bar : map.values()) {
                try { bar.removeAll(); } catch (Throwable ignored) {}
            }
        }
        activeBars.clear();
        carouselCounters.clear();
        lastTitles.clear();
        plugin.getLogger().info("[MultiBossBar] Shutdown complete.");
    }
}
