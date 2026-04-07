package fr.elias.oreoEssentials.modules.afk;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.afk.rabbit.packets.AfkStatusPacket;
import fr.elias.oreoEssentials.rabbitmq.PacketChannels;
import fr.elias.oreoEssentials.rabbitmq.packet.PacketManager;
import fr.elias.oreoEssentials.util.Lang;
import fr.elias.oreoEssentials.util.OreScheduler;
import fr.elias.oreoEssentials.util.OreTask;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class AfkService implements Listener {

    private final JavaPlugin plugin;
    // OreoEssentials cast — may be null if not that type (tests / other callers)
    private final OreoEssentials orePlugin;
    private final AfkConfig afkConfig;
    private final Logger logger;

    private final Map<UUID, Long> ignoreMoveUntilMs  = new ConcurrentHashMap<>();
    private final Set<UUID>       afkPlayers         = ConcurrentHashMap.newKeySet();
    private final Map<UUID, String> originalTabNames = new ConcurrentHashMap<>();
    private final Map<UUID, Long>   lastActivityMs   = new ConcurrentHashMap<>();
    private final Map<UUID, Long>   afkSinceMs       = new ConcurrentHashMap<>();

    // Cross-server / Web UI: key = "<server>:<uuid>"
    private final Map<String, AfkPlayerData> globalAfkData = new ConcurrentHashMap<>();

    private AfkPoolService poolService;
    private AfkWebServer   webServer;
    private fr.elias.oreoEssentials.modules.webpanel.WebPanelSyncService webPanelSync;
    private volatile boolean crossHandlersRegistered = false;

    // ---- config ----
    private boolean autoEnabled;
    private int     autoSeconds;
    private int     checkIntervalSeconds;
    private List<PermissionTier>    permissionTiers  = new ArrayList<>();
    private List<PermissionMessage> customMessages   = new ArrayList<>();
    private boolean actionBarEnabled;
    private boolean backMessageEnabled;
    private boolean webEnabled;
    private int     webPort;
    private String  localServer = "";

    private OreTask autoTask;
    private OreTask actionBarTask;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public AfkService(JavaPlugin plugin) {
        this.plugin     = plugin;
        this.orePlugin  = (plugin instanceof OreoEssentials ore) ? ore : null;
        this.afkConfig  = new AfkConfig(plugin);
        this.logger     = plugin.getLogger();
        this.localServer = (orePlugin != null) ? orePlugin.getConfigService().serverName() : Bukkit.getServer().getName();

        reloadAutoConfig();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        startAutoAfkTask();
        startActionBarTask();
        startWebServerIfEnabled();
    }

    // -------------------------------------------------------------------------
    // Config
    // -------------------------------------------------------------------------

    public void reloadAutoConfig() {
        afkConfig.reload();

        this.autoEnabled          = afkConfig.autoEnabled();
        this.autoSeconds          = afkConfig.autoSeconds();
        this.checkIntervalSeconds = afkConfig.checkIntervalSeconds();

        // Permission tiers
        this.permissionTiers = new ArrayList<>();
        for (Map<?, ?> entry : afkConfig.permissionTiers()) {
            String perm = String.valueOf(entry.get("permission"));
            int secs;
            try { secs = Integer.parseInt(String.valueOf(entry.get("seconds"))); }
            catch (NumberFormatException e) { continue; }
            permissionTiers.add(new PermissionTier(perm, secs));
        }

        // Custom messages per permission
        this.customMessages = new ArrayList<>();
        for (Map<?, ?> entry : afkConfig.customMessages()) {
            String perm     = entry.containsKey("permission")    ? String.valueOf(entry.get("permission"))    : "";
            String nowAfk   = entry.containsKey("now-afk")       ? String.valueOf(entry.get("now-afk"))       : null;
            String noLonger = entry.containsKey("no-longer-afk") ? String.valueOf(entry.get("no-longer-afk")) : null;
            String back     = entry.containsKey("back")          ? String.valueOf(entry.get("back"))          : null;
            customMessages.add(new PermissionMessage(perm, nowAfk, noLonger, back));
        }

        this.actionBarEnabled   = afkConfig.actionBarEnabled();
        this.backMessageEnabled = afkConfig.backMessageEnabled();
        this.webEnabled         = afkConfig.webEnabled();
        this.webPort            = afkConfig.webPort();
    }

    public AfkConfig getAfkConfig() { return afkConfig; }

    // -------------------------------------------------------------------------
    // Web server
    // -------------------------------------------------------------------------

    private void startWebServerIfEnabled() {
        if (!webEnabled) return;
        try {
            webServer = new AfkWebServer(webPort, globalAfkData, logger);
            webServer.start();
        } catch (IOException e) {
            logger.warning("[AfkWeb] Failed to start web server on port " + webPort + ": " + e.getMessage());
            webServer = null;
        }
    }

    // -------------------------------------------------------------------------
    // Cross-server
    // -------------------------------------------------------------------------

    public void tryHookCrossServerNow() {
        if (crossHandlersRegistered) return;
        if (orePlugin == null) return;

        PacketManager pm = orePlugin.getPacketManager();
        if (pm == null || !pm.isInitialized()) return;

        try { pm.subscribeChannel(PacketChannels.GLOBAL); } catch (Throwable ignored) {}

        pm.subscribe(AfkStatusPacket.class, (channel, pkt) -> {
            if (pkt == null || pkt.getPlayerId() == null) return;

            // Ignore our own broadcasts (we already update globalAfkData locally)
            if (localServer.equalsIgnoreCase(pkt.getServer())) return;

            String key = pkt.getServer() + ":" + pkt.getPlayerId();
            if (pkt.isEntering()) {
                globalAfkData.put(key, new AfkPlayerData(
                        pkt.getPlayerId(), pkt.getPlayerName(),
                        pkt.getServer(),   pkt.getWorldName(),
                        pkt.getX(), pkt.getY(), pkt.getZ(),
                        pkt.getAfkSinceMs()
                ));
            } else {
                globalAfkData.remove(key);
            }
        });

        crossHandlersRegistered = true;
        logger.info("[AfkService] Cross-server status tracking hooked.");
    }

    private void broadcastStatus(Player player, boolean entering) {
        var loc   = player.getLocation();
        String world = (loc.getWorld() != null) ? loc.getWorld().getName() : "";
        long since = afkSinceMs.getOrDefault(player.getUniqueId(), System.currentTimeMillis());

        // Cross-server plugin broadcast via RabbitMQ (AfkStatusPacket)
        if (orePlugin != null) {
            PacketManager pm = orePlugin.getPacketManager();
            if (pm != null && pm.isInitialized()) {
                AfkStatusPacket pkt = new AfkStatusPacket(
                        player.getUniqueId(), player.getName(),
                        localServer, world,
                        loc.getX(), loc.getY(), loc.getZ(),
                        since, entering
                );
                try { pm.sendPacket(PacketChannels.GLOBAL, pkt); } catch (Throwable t) {
                    logger.warning("[AfkService] Failed to broadcast status: " + t.getMessage());
                }
            }
        }

        // Web panel push (backend aggregate + dashboard)
        if (webPanelSync != null) {
            webPanelSync.publishAfkStatus(
                    player.getUniqueId(), player.getName(),
                    localServer, world,
                    loc.getX(), loc.getY(), loc.getZ(),
                    since, entering
            );
        }
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public boolean isAfk(Player player) {
        return afkPlayers.contains(player.getUniqueId());
    }

    public long getAfkForSeconds(Player player) {
        Long since = afkSinceMs.get(player.getUniqueId());
        if (since == null) return 0L;
        return Math.max(0L, (System.currentTimeMillis() - since) / 1000L);
    }

    public long getInactiveForSeconds(Player player) {
        Long last = lastActivityMs.get(player.getUniqueId());
        if (last == null) return 0L;
        return Math.max(0L, (System.currentTimeMillis() - last) / 1000L);
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
            long since = System.currentTimeMillis();
            afkSinceMs.put(id, since);

            // Send custom "now AFK" message
            sendNowAfkMessage(player);

            // Update global AFK data (for web UI)
            var loc   = player.getLocation();
            String world = (loc.getWorld() != null) ? loc.getWorld().getName() : "";
            globalAfkData.put(localServer + ":" + id, new AfkPlayerData(
                    id, player.getName(), localServer, world,
                    loc.getX(), loc.getY(), loc.getZ(), since
            ));

            // Broadcast cross-server
            broadcastStatus(player, true);

            if (poolService != null && poolService.isEnabled()) {
                ignoreMoveFor(player, 40);
                poolService.sendPlayerToAfkPool(player);
            }

        } else {
            long wasAfkSec = getAfkForSeconds(player);
            afkPlayers.remove(id);
            afkSinceMs.remove(id);

            // Send custom "no longer AFK" message
            sendNoLongerAfkMessage(player);

            // Send duration message
            if (backMessageEnabled && wasAfkSec > 0) {
                sendBackMessage(player, wasAfkSec);
            }

            // Remove from global AFK data
            globalAfkData.remove(localServer + ":" + id);

            // Broadcast cross-server
            broadcastStatus(player, false);

            if (poolService != null && poolService.isInAfkPool(player)) {
                ignoreMoveFor(player, 40);
                poolService.returnPlayerFromAfkPool(player);
            }
        }

        refreshTabName(player);
    }

    public void handleQuit(Player player) {
        UUID id = player.getUniqueId();

        if (afkPlayers.contains(id)) {
            broadcastStatus(player, false);
        }

        afkPlayers.remove(id);
        originalTabNames.remove(id);
        lastActivityMs.remove(id);
        afkSinceMs.remove(id);
        globalAfkData.remove(localServer + ":" + id);

        if (poolService != null) poolService.cleanup(player);
    }

    public void ignoreMoveFor(Player p, int ticks) {
        ignoreMoveUntilMs.put(p.getUniqueId(), System.currentTimeMillis() + (ticks * 50L));
    }

    public void clearAfk(Player player) {
        UUID id = player.getUniqueId();
        afkPlayers.remove(id);
        afkSinceMs.remove(id);
        globalAfkData.remove(localServer + ":" + id);
        broadcastStatus(player, false);
        refreshTabName(player);
    }

    public void setPoolService(AfkPoolService poolService) { this.poolService = poolService; }
    public void setWebPanelSync(fr.elias.oreoEssentials.modules.webpanel.WebPanelSyncService sync) { this.webPanelSync = sync; }

    /** Returns a snapshot of all AFK players tracked globally (local + remote). */
    public Map<String, AfkPlayerData> getGlobalAfkData() {
        return Collections.unmodifiableMap(globalAfkData);
    }

    // -------------------------------------------------------------------------
    // Messaging helpers
    // -------------------------------------------------------------------------

    private void sendNowAfkMessage(Player player) {
        PermissionMessage custom = findCustomMessage(player);
        if (custom != null && custom.nowAfk() != null) {
            Lang.sendRaw(player, custom.nowAfk());
        } else {
            Lang.send(player, "afk.now-afk", "<yellow>You are now AFK.</yellow>");
        }
    }

    private void sendNoLongerAfkMessage(Player player) {
        PermissionMessage custom = findCustomMessage(player);
        if (custom != null && custom.noLongerAfk() != null) {
            Lang.sendRaw(player, custom.noLongerAfk());
        } else {
            Lang.send(player, "afk.no-longer-afk", "<green>You are no longer AFK.</green>");
        }
    }

    private void sendBackMessage(Player player, long afkSeconds) {
        String time = Lang.timeHuman(afkSeconds);
        PermissionMessage custom = findCustomMessage(player);
        String template;
        if (custom != null && custom.back() != null) {
            template = custom.back();
        } else {
            template = Lang.get("afk.back-message", "<green>You were AFK for <white>%time%</white>.</green>");
        }
        Lang.sendRaw(player, template.replace("%time%", time));
    }

    private PermissionMessage findCustomMessage(Player player) {
        for (PermissionMessage msg : customMessages) {
            if (msg.permission() != null && !msg.permission().isEmpty()
                    && player.hasPermission(msg.permission())) {
                return msg;
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Tab list
    // -------------------------------------------------------------------------

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
        String n = player.getPlayerListName();
        return (n == null || n.isEmpty()) ? player.getName() : n;
    }

    // -------------------------------------------------------------------------
    // Activity tracking
    // -------------------------------------------------------------------------

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
        Long until = ignoreMoveUntilMs.get(p.getUniqueId());
        if (until == null) return false;
        if (System.currentTimeMillis() >= until) {
            ignoreMoveUntilMs.remove(p.getUniqueId());
            return false;
        }
        return true;
    }

    // -------------------------------------------------------------------------
    // Tasks
    // -------------------------------------------------------------------------

    private int effectiveTimeoutFor(Player p) {
        for (PermissionTier tier : permissionTiers) {
            if (p.hasPermission(tier.permission())) return tier.seconds();
        }
        return autoSeconds;
    }

    private void startAutoAfkTask() {
        stopAutoAfkTask();
        if (!autoEnabled || autoSeconds <= 0) return;

        autoTask = OreScheduler.runTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p == null || !p.isOnline()) continue;
                UUID id = p.getUniqueId();
                if (afkPlayers.contains(id)) continue;

                long last    = lastActivityMs.getOrDefault(id, now);
                int  timeout = effectiveTimeoutFor(p);
                if (timeout <= 0) continue;

                if ((now - last) / 1000L >= timeout) {
                    setAfk(p, true);
                }
            }
        }, 20L * checkIntervalSeconds, 20L * checkIntervalSeconds);
    }

    private void stopAutoAfkTask() {
        if (autoTask != null) { autoTask.cancel(); autoTask = null; }
    }

    private void startActionBarTask() {
        stopActionBarTask();
        if (!actionBarEnabled) return;

        actionBarTask = OreScheduler.runTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p == null || !p.isOnline()) continue;
                if (!afkPlayers.contains(p.getUniqueId())) continue;

                long secs = getAfkForSeconds(p);
                String time = Lang.timeHuman(secs);
                String raw  = Lang.get("afk.actionbar", "<gray>AFK for <white>%time%</white></gray>")
                        .replace("%time%", time);

                // Use entity scheduler for Folia safety
                OreScheduler.runForEntity(plugin, p, () -> {
                    if (p.isOnline()) {
                        p.sendActionBar(Lang.toComponent(raw));
                    }
                });
            }
        }, 20L, 20L); // every second
    }

    private void stopActionBarTask() {
        if (actionBarTask != null) { actionBarTask.cancel(); actionBarTask = null; }
    }

    // -------------------------------------------------------------------------
    // Events
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (poolService != null && poolService.isInAfkPool(e.getPlayer())) return;

        var from = e.getFrom();
        var to   = e.getTo();
        if (to == null) return;
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) return;

        touch(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) { touch(e.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) {
        OreScheduler.run(plugin, () -> touch(e.getPlayer()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) { touch(e.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        lastActivityMs.put(e.getPlayer().getUniqueId(), System.currentTimeMillis());
        afkSinceMs.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) { handleQuit(e.getPlayer()); }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    public void shutdown() {
        stopAutoAfkTask();
        stopActionBarTask();
        if (webServer != null) { webServer.stop(); webServer = null; }
        afkPlayers.clear();
        originalTabNames.clear();
        lastActivityMs.clear();
        afkSinceMs.clear();
        globalAfkData.clear();
    }

    // -------------------------------------------------------------------------
    // Inner records
    // -------------------------------------------------------------------------

    private record PermissionTier(String permission, int seconds) {}
    private record PermissionMessage(String permission, String nowAfk, String noLongerAfk, String back) {}
}
