package fr.elias.oreoEssentials.modules.afk;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AfkService implements Listener {

    private final JavaPlugin plugin;
    private final Map<UUID, Long> ignoreMoveUntilMs = new ConcurrentHashMap<>();

    private final Set<UUID> afkPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, String> originalTabNames = new ConcurrentHashMap<>();

    // activity tracking
    private final Map<UUID, Long> lastActivityMs = new ConcurrentHashMap<>();
    private final Map<UUID, Long> afkSinceMs = new ConcurrentHashMap<>();

    // Reference to pool service (set after construction)
    private AfkPoolService poolService;

    // auto afk config
    private boolean autoEnabled;
    private int autoSeconds;
    private int checkIntervalSeconds;

    private BukkitTask autoTask;

    public AfkService(JavaPlugin plugin) {
        this.plugin = plugin;
        reloadAutoConfig();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        startAutoAfkTask();
    }

    public void reloadAutoConfig() {
        var cfg = plugin.getConfig();
        this.autoEnabled = cfg.getBoolean("afk.auto.enabled", false);
        this.autoSeconds = Math.max(0, cfg.getInt("afk.auto.seconds", 300));
        this.checkIntervalSeconds = Math.max(1, cfg.getInt("afk.auto.check-interval-seconds", 5));
    }

    public boolean isAfk(Player player) {
        return afkPlayers.contains(player.getUniqueId());
    }

    public long getAfkForSeconds(Player player) {
        Long since = afkSinceMs.get(player.getUniqueId());
        if (since == null) return 0L;
        long diff = System.currentTimeMillis() - since;
        if (diff < 0) diff = 0;
        return diff / 1000L;
    }

    public long getInactiveForSeconds(Player player) {
        Long last = lastActivityMs.get(player.getUniqueId());
        if (last == null) return 0L;
        long diff = System.currentTimeMillis() - last;
        if (diff < 0) diff = 0;
        return diff / 1000L;
    }

    public boolean toggleAfk(Player player) {
        boolean nowAfk = !isAfk(player);
        setAfk(player, nowAfk);
        return nowAfk;
    }

    public void setAfk(Player player, boolean afk) {
        UUID id = player.getUniqueId();

        if (afk) {
            if (afkPlayers.contains(id)) {
                refreshTabName(player);
                return;
            }

            originalTabNames.putIfAbsent(id, safeTabName(player));
            afkPlayers.add(id);
            afkSinceMs.put(id, System.currentTimeMillis());

            if (poolService != null && poolService.isEnabled()) {
                ignoreMoveFor(player, 40); // 2 seconds grace
                poolService.sendPlayerToAfkPool(player);
            }

        } else {
            afkPlayers.remove(id);
            afkSinceMs.remove(id);

            if (poolService != null && poolService.isInAfkPool(player)) {
                ignoreMoveFor(player, 40);
                poolService.returnPlayerFromAfkPool(player);
            }
        }

        refreshTabName(player);
    }

    public void handleQuit(Player player) {
        UUID id = player.getUniqueId();
        afkPlayers.remove(id);
        originalTabNames.remove(id);
        lastActivityMs.remove(id);
        afkSinceMs.remove(id);

        if (poolService != null) {
            poolService.cleanup(player);
        }
    }
    public void ignoreMoveFor(Player p, int ticks) {
        long until = System.currentTimeMillis() + (ticks * 50L);
        ignoreMoveUntilMs.put(p.getUniqueId(), until);
    }

    public void clearAfk(Player player) {
        UUID id = player.getUniqueId();
        afkPlayers.remove(id);
        afkSinceMs.remove(id);
        refreshTabName(player);
        // don't call poolService here (pool calls this)
    }

    public void setPoolService(AfkPoolService poolService) {
        this.poolService = poolService;
    }

    private void refreshTabName(Player player) {
        UUID id = player.getUniqueId();
        String base = originalTabNames.getOrDefault(id, safeTabName(player));

        if (afkPlayers.contains(id)) {
            player.setPlayerListName(ChatColor.GRAY + "[AFK] " + ChatColor.RESET + base);
        } else {
            player.setPlayerListName(base);
        }
    }

    private String safeTabName(Player player) {
        String listName = player.getPlayerListName();
        if (listName == null || listName.isEmpty()) return player.getName();
        return listName;
    }

    private void touch(Player p) {
        Long until = ignoreMoveUntilMs.get(p.getUniqueId());
        if (until != null && System.currentTimeMillis() < until) return;

        if (poolService != null && poolService.isInAfkPool(p)) return;

        UUID id = p.getUniqueId();
        lastActivityMs.put(id, System.currentTimeMillis());

        if (afkPlayers.contains(id)) {
            setAfk(p, false);
        }
    }

    public boolean shouldIgnoreMove(Player p) {
        UUID id = p.getUniqueId();
        Long until = ignoreMoveUntilMs.get(id);
        if (until == null) return false;

        if (System.currentTimeMillis() >= until) {
            ignoreMoveUntilMs.remove(id);
            return false;
        }
        return true;
    }



    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {

        if (poolService != null && poolService.isInAfkPool(e.getPlayer())) {
            return;
        }

        var from = e.getFrom();
        var to = e.getTo();
        if (to == null) return;

        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        touch(e.getPlayer());
    }


    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        touch(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) {
        touch(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        touch(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        lastActivityMs.put(e.getPlayer().getUniqueId(), System.currentTimeMillis());
        afkSinceMs.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        handleQuit(e.getPlayer());
    }

    private void startAutoAfkTask() {
        stopAutoAfkTask();

        if (!autoEnabled || autoSeconds <= 0) return;

        autoTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();

            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p == null || !p.isOnline()) continue;

                UUID id = p.getUniqueId();
                long last = lastActivityMs.getOrDefault(id, now);

                // already AFK -> nothing
                if (afkPlayers.contains(id)) continue;

                long inactiveSec = (now - last) / 1000L;
                if (inactiveSec >= autoSeconds) {
                    setAfk(p, true);
                }
            }

        }, 20L * checkIntervalSeconds, 20L * checkIntervalSeconds);
    }

    private void stopAutoAfkTask() {
        if (autoTask != null) {
            autoTask.cancel();
            autoTask = null;
        }
    }

    public void shutdown() {
        stopAutoAfkTask();
        afkPlayers.clear();
        originalTabNames.clear();
        lastActivityMs.clear();
        afkSinceMs.clear();
    }
}
