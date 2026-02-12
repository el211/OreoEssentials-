package fr.elias.oreoEssentials.modules.bossbar;

import fr.elias.oreoEssentials.OreoEssentials;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class BossBarService implements Listener {
    private final OreoEssentials plugin;
    private final Map<UUID, BossBar> bars = new HashMap<>();

    private boolean enabled;
    private String text;
    private BarColor color;
    private BarStyle style;
    private double progress;
    private long period;

    private int taskId = -1;

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    public BossBarService(OreoEssentials plugin) {
        this.plugin = plugin;
        readConfig();
    }

    private void readConfig() {
        var cfg = plugin.getConfig().getConfigurationSection("bossbar");

        this.enabled = plugin.getSettingsConfig().bossbarEnabled();

        this.text     = cfg != null ? cfg.getString("text", "<gradient:#FF1493:#00FF7F>Welcome</gradient> {player}")
                : "<gradient:#FF1493:#00FF7FF>Welcome</gradient> {player}";
        this.color    = safeColor(cfg != null ? cfg.getString("color", "PURPLE") : "PURPLE");
        this.style    = safeStyle(cfg != null ? cfg.getString("style", "SEGMENTED_10") : "SEGMENTED_10");
        double p      = cfg != null ? cfg.getDouble("progress", 1.0) : 1.0;
        this.progress = Math.max(0.0, Math.min(1.0, p));
        long per      = cfg != null ? cfg.getLong("update-ticks", 40) : 40L;
        this.period   = Math.max(1L, per);
    }



    public void start() {
        if (!enabled) {
            plugin.getLogger().info("[BossBar] Disabled in config.");
            return;
        }

        for (Player p : Bukkit.getOnlinePlayers()) show(p);

        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            if (!enabled) return;
            for (Player p : Bukkit.getOnlinePlayers()) {
                BossBar bar = bars.get(p.getUniqueId());
                if (bar == null) { show(p); continue; }
                bar.setTitle(render(p, text));
                bar.setColor(color);
                bar.setStyle(style);
                bar.setProgress(progress);
            }
        }, period, period);

        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("[BossBar] Enabled (color=" + color + ", style=" + style + ").");
    }

    public void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        for (BossBar bar : bars.values()) {
            try { bar.removeAll(); } catch (Throwable ignored) {}
        }
        bars.clear();
    }

    public void reload() {
        // re-read settings
        readConfig();

        if (!enabled) {
            stop();
            plugin.getLogger().info("[BossBar] Now disabled by config; removed all bars.");
            return;
        }

        // ensure task is running with new period
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                BossBar bar = bars.get(p.getUniqueId());
                if (bar == null) { show(p); continue; }
                bar.setTitle(render(p, text));
                bar.setColor(color);
                bar.setStyle(style);
                bar.setProgress(progress);
            }
        }, period, period);


        for (Player p : Bukkit.getOnlinePlayers()) {
            show(p);
        }

        plugin.getLogger().info("[BossBar] Reloaded from config.");
    }


    public boolean isShown(Player p) { return bars.containsKey(p.getUniqueId()); }

    public void show(Player p) {
        if (!enabled) return;

        BossBar bar = bars.get(p.getUniqueId());
        if (bar == null) {
            bar = Bukkit.createBossBar(render(p, text), color, style);
            bars.put(p.getUniqueId(), bar);
        }
        bar.setTitle(render(p, text));
        bar.setColor(color);
        bar.setStyle(style);
        bar.setProgress(progress);
        if (!bar.getPlayers().contains(p)) bar.addPlayer(p);
    }

    public void hide(Player p) {
        BossBar bar = bars.remove(p.getUniqueId());
        if (bar != null) {
            try { bar.removeAll(); } catch (Throwable ignored) {}
        }
    }


    @EventHandler public void onJoin(PlayerJoinEvent e) { if (enabled) show(e.getPlayer()); }
    @EventHandler public void onQuit(PlayerQuitEvent e) { hide(e.getPlayer()); }


    private static BarColor safeColor(String name) {
        try { return BarColor.valueOf(name.toUpperCase(Locale.ROOT)); }
        catch (Exception ignored) { return BarColor.PURPLE; }
    }

    private static BarStyle safeStyle(String name) {
        try { return BarStyle.valueOf(name.toUpperCase(Locale.ROOT)); }
        catch (Exception ignored) { return BarStyle.SEGMENTED_10; }
    }

    private String render(Player p, String raw) {
        String s = raw == null ? "" : raw.replace("{player}", p.getName());
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                s = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(p, s);
            }
        } catch (Throwable ignored) {}
        try {
            Component c = MM.deserialize(s);
            s = LEGACY.serialize(c);
        } catch (Throwable ignored) {}
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
