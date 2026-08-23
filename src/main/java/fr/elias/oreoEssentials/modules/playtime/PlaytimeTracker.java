package fr.elias.oreoEssentials.modules.playtime;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.util.OreScheduler;
import fr.elias.oreoEssentials.util.OreTask;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PlaytimeTracker implements Listener {

    private final OreoEssentials plugin;
    private final Map<UUID, Long> totals = new ConcurrentHashMap<>();
    private final Map<UUID, Long> onlineSince = new ConcurrentHashMap<>();
    private final Set<UUID> baselined = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final File file;
    private final AtomicBoolean saveQueued = new AtomicBoolean(false);
    private OreTask autosaveTask;

    public PlaytimeTracker(OreoEssentials plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "playtime_data.yml");
        load();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        autosaveTask = OreScheduler.runTimer(plugin, this::saveQuietAsync, 20L * 60, 20L * 60);
    }

    public void shutdown() {
        if (autosaveTask != null) {
            autosaveTask.cancel();
            autosaveTask = null;
        }
        flushOnlineDeltas();
        // Plugin shutdown should leave data on disk before returning, so this final write is synchronous.
        saveSnapshot(snapshotTotals(), snapshotBaselined());
        HandlerList.unregisterAll(this);
    }

    private void load() {
        try {
            if (!file.exists()) {
                File parent = file.getParentFile();
                if (parent != null) parent.mkdirs();
                new YamlConfiguration().save(file);
            }
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            if (yaml.isConfigurationSection("totals") && yaml.getConfigurationSection("totals") != null) {
                for (String k : Objects.requireNonNull(yaml.getConfigurationSection("totals")).getKeys(false)) {
                    try {
                        UUID u = UUID.fromString(k);
                        totals.put(u, yaml.getLong("totals." + k, 0L));
                    } catch (IllegalArgumentException ignored) {}
                }
            }
            baselined.clear();
            for (String s : yaml.getStringList("baselined")) {
                try { baselined.add(UUID.fromString(s)); } catch (IllegalArgumentException ignored) {}
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[PlaytimeTracker] Failed to load: " + e.getMessage());
        }
    }

    private void saveQuietAsync() {
        flushOnlineDeltas();
        if (!saveQueued.compareAndSet(false, true)) return;

        Map<UUID, Long> totalsSnapshot = snapshotTotals();
        List<UUID> baselinedSnapshot = snapshotBaselined();
        OreScheduler.runAsync(plugin, () -> {
            try {
                saveSnapshot(totalsSnapshot, baselinedSnapshot);
            } finally {
                saveQueued.set(false);
            }
        });
    }

    private Map<UUID, Long> snapshotTotals() {
        return new HashMap<>(totals);
    }

    private List<UUID> snapshotBaselined() {
        return new ArrayList<>(baselined);
    }

    private void saveSnapshot(Map<UUID, Long> totalsSnapshot, Collection<UUID> baselinedSnapshot) {
        try {
            YamlConfiguration out = new YamlConfiguration();
            for (Map.Entry<UUID, Long> e : totalsSnapshot.entrySet()) {
                out.set("totals." + e.getKey(), e.getValue());
            }
            List<String> bl = new ArrayList<>(baselinedSnapshot.size());
            for (UUID u : baselinedSnapshot) bl.add(u.toString());
            out.set("baselined", bl);
            out.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("[PlaytimeTracker] Save failed: " + e.getMessage());
        }
    }

    private void flushOnlineDeltas() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Long> e : onlineSince.entrySet()) {
            UUID u = e.getKey();
            long start = e.getValue();
            long addSec = Math.max(0, (now - start) / 1000L);
            if (addSec > 0) totals.merge(u, addSec, Long::sum);
            onlineSince.replace(u, start, now);
        }
    }

    public long getSeconds(UUID uuid) {
        long base = totals.getOrDefault(uuid, 0L);
        Long start = onlineSince.get(uuid);
        if (start != null) base += Math.max(0, (System.currentTimeMillis() - start) / 1000L);
        return Math.max(0, base);
    }

    public void baselineFromBukkitIfNeeded(Player p) {
        if (!baselined.add(p.getUniqueId())) return;
        int ticks = p.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE);
        totals.put(p.getUniqueId(), Math.max(0, ticks / 20L));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        onlineSince.put(p.getUniqueId(), System.currentTimeMillis());
        if (plugin.getConfig().getBoolean("playtime.internal.baseline-from-bukkit-on-first-seen", false)) {
            baselineFromBukkitIfNeeded(p);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        Long start = onlineSince.remove(p.getUniqueId());
        if (start != null) {
            long addSec = Math.max(0, (System.currentTimeMillis() - start) / 1000L);
            if (addSec > 0) totals.merge(p.getUniqueId(), addSec, Long::sum);
        }
        saveQuietAsync();
    }
}
