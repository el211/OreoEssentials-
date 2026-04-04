package fr.elias.oreoEssentials.modules.nametag;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.util.OreScheduler;
import fr.elias.oreoEssentials.util.OreTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
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
 * Priority-based custom action bar with carousel (rotating text) support.
 *
 * Features:
 *  - Multiple entries sorted by priority; first entry whose conditions pass is shown
 *  - Each entry can have a list of texts that rotate (carousel)
 *  - Per-player carousel index tracked independently
 *  - PlaceholderAPI + MiniMessage support
 *  - Conditions per entry (same NametageCondition system)
 *  - Configurable global refresh interval + per-entry carousel interval
 *  - Folia-compatible
 */
public final class ActionBarService implements Listener {

    // ── Inner model ───────────────────────────────────────────────────────────

    private static final class ActionBarEntry {
        final int priority;
        final List<String> texts;
        final int carouselIntervalTicks;
        final List<NametageCondition> conditions;

        ActionBarEntry(int priority, List<String> texts, int carouselIntervalTicks,
                       List<NametageCondition> conditions) {
            this.priority = priority;
            this.texts = texts;
            this.carouselIntervalTicks = Math.max(1, carouselIntervalTicks);
            this.conditions = conditions;
        }
    }

    // ── Config ────────────────────────────────────────────────────────────────
    private boolean enabled;
    private int refreshIntervalTicks;
    private List<ActionBarEntry> entries = new ArrayList<>();

    // ── State ─────────────────────────────────────────────────────────────────
    /** player UUID → map of entry index → carousel tick counter */
    private final ConcurrentHashMap<UUID, int[]> carouselCounters = new ConcurrentHashMap<>();

    private OreTask updateTask;

    private final OreoEssentials plugin;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    // ── Constructor ───────────────────────────────────────────────────────────

    public ActionBarService(OreoEssentials plugin, FileConfiguration config) {
        this.plugin = plugin;
        loadConfig(config);

        if (enabled) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
            startTask();
            plugin.getLogger().info("[ActionBar] Custom action bar enabled (" + entries.size() + " entr(ies)).");
        } else {
            plugin.getLogger().info("[ActionBar] Disabled in config.");
        }
    }

    // ── Config ────────────────────────────────────────────────────────────────

    private void loadConfig(FileConfiguration config) {
        ConfigurationSection s = config.getConfigurationSection("actionbar");
        this.enabled = s != null && s.getBoolean("enabled", false);
        if (s == null) return;

        this.refreshIntervalTicks = Math.max(1, s.getInt("refresh-interval-ticks", 20));

        entries.clear();
        ConfigurationSection entriesSec = s.getConfigurationSection("entries");
        if (entriesSec == null) return;

        for (String key : entriesSec.getKeys(false)) {
            ConfigurationSection e = entriesSec.getConfigurationSection(key);
            if (e == null) continue;
            try {
                int priority = e.getInt("priority", 0);

                // texts can be a single string or a list
                List<String> texts;
                if (e.isList("texts")) {
                    texts = e.getStringList("texts");
                } else {
                    String single = e.getString("text", "");
                    texts = single.isEmpty() ? Collections.emptyList() : List.of(single);
                }
                if (texts.isEmpty()) continue;

                int carouselTicks = e.getInt("carousel-interval-ticks", 60);
                List<NametageCondition> conds = NametageCondition.parseList(e, "conditions");

                entries.add(new ActionBarEntry(priority, texts, carouselTicks, conds));
            } catch (Exception ex) {
                plugin.getLogger().warning("[ActionBar] Failed to parse entry '" + key + "': " + ex.getMessage());
            }
        }

        // Sort by priority descending (highest priority shown first)
        entries.sort(Comparator.comparingInt((ActionBarEntry e) -> e.priority).reversed());
    }

    // ── Task ──────────────────────────────────────────────────────────────────

    private void startTask() {
        if (updateTask != null) updateTask.cancel();
        updateTask = OreScheduler.runTimer(plugin, this::updateAll,
                refreshIntervalTicks, refreshIntervalTicks);
    }

    private void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            updatePlayer(player);
        }
    }

    private void updatePlayer(Player player) {
        UUID uuid = player.getUniqueId();
        int[] counters = carouselCounters.computeIfAbsent(uuid, k -> new int[entries.size()]);

        // Tick all carousel counters
        for (int i = 0; i < counters.length && i < entries.size(); i++) {
            counters[i] += refreshIntervalTicks;
        }

        // Find first entry whose conditions pass
        for (int i = 0; i < entries.size(); i++) {
            ActionBarEntry entry = entries.get(i);
            if (!NametageCondition.evaluateAll(entry.conditions, player)) continue;

            // Pick the current carousel text
            int carouselIndex = (counters[i] / entry.carouselIntervalTicks) % entry.texts.size();
            String raw = entry.texts.get(carouselIndex);

            // Resolve PlaceholderAPI + MiniMessage
            String resolved = resolvePapi(raw, player);
            Component component;
            try { component = MM.deserialize(resolved); }
            catch (Exception e) { component = Component.text(resolved); }

            final Component toSend = component;
            OreScheduler.runForEntity(plugin, player, () -> player.sendActionBar(toSend));
            return; // Only show the highest-priority matching entry
        }
    }

    // ── Events ────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        carouselCounters.put(event.getPlayer().getUniqueId(), new int[entries.size()]);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        carouselCounters.remove(event.getPlayer().getUniqueId());
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

    // ── Public API ────────────────────────────────────────────────────────────

    public boolean isEnabled() { return enabled; }

    public void reload(FileConfiguration config) {
        if (updateTask != null) { updateTask.cancel(); updateTask = null; }
        carouselCounters.clear();
        loadConfig(config);
        if (enabled) {
            startTask();
            // Re-seed counters for online players
            for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
                carouselCounters.put(p.getUniqueId(), new int[entries.size()]);
            }
        }
        plugin.getLogger().info("[ActionBar] Reloaded (" + entries.size() + " entr(ies)).");
    }

    public void shutdown() {
        if (updateTask != null) { updateTask.cancel(); updateTask = null; }
        carouselCounters.clear();
        plugin.getLogger().info("[ActionBar] Shutdown complete.");
    }
}
