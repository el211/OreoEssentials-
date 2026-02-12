package fr.elias.oreoEssentials.modules.afk;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.back.BackLocation;
import fr.elias.oreoEssentials.rabbitmq.PacketChannels;
import fr.elias.oreoEssentials.rabbitmq.packet.PacketManager;
import fr.elias.oreoEssentials.modules.afk.rabbit.packets.AfkPoolEnterPacket;
import fr.elias.oreoEssentials.modules.afk.rabbit.packets.AfkPoolExitPacket;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AfkPoolService implements Listener {
    private final Map<UUID, BackLocation> pendingReturnTeleports = new ConcurrentHashMap<>();

    private final OreoEssentials plugin;
    private final AfkService afkService;
    private final String localServer;

    private final Map<UUID, BackLocation> afkReturnLocations = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> pendingPoolTeleports = new ConcurrentHashMap<>();
    private final Map<UUID, Long> moveGraceUntilMs = new ConcurrentHashMap<>();

    private String afkPoolRegionName;
    private String afkPoolWorldName;
    private String afkPoolServer;
    private boolean enabled;
    private boolean crossServerEnabled;

    private volatile boolean crossHandlersRegistered = false;

    public AfkPoolService(OreoEssentials plugin, AfkService afkService) {
        this.plugin = plugin;
        this.afkService = afkService;
        this.localServer = plugin.getConfigService().serverName();

        loadConfig();

        if (enabled) {
            Bukkit.getPluginManager().registerEvents(this, plugin);

            plugin.getLogger().info("[AfkPool] Enabled - Region: " + afkPoolRegionName
                    + " | World: " + afkPoolWorldName
                    + " | Server: " + afkPoolServer
                    + " | Cross-server: " + crossServerEnabled
                    + " | LocalServer: " + localServer);

            if (crossServerEnabled) {
                plugin.getLogger().info("[AfkPool] Waiting for PacketManager init to hook cross-server handlers...");
            }
        }
    }

    private void loadConfig() {
        var config = plugin.getConfig();
        this.enabled = config.getBoolean("afk-pool.enabled", false);
        this.afkPoolRegionName = config.getString("afk-pool.region-name", "afk_pool");
        this.afkPoolWorldName = config.getString("afk-pool.world-name", "world");
        this.afkPoolServer = config.getString("afk-pool.server", localServer);
        this.crossServerEnabled = config.getBoolean("afk-pool.cross-server", false);
    }

    public void tryHookCrossServerNow() {
        if (crossHandlersRegistered) return;
        if (!enabled) return;
        if (!crossServerEnabled) return;

        PacketManager pm = plugin.getPacketManager();
        if (pm == null) return;
        if (!pm.isInitialized()) return;

        try {
            pm.subscribeChannel(PacketChannels.GLOBAL);
        } catch (Throwable ignored) {
        }

        setupCrossServerHandlers(pm);
        crossHandlersRegistered = true;

        plugin.getLogger().info("[AfkPool] Cross-server handlers hooked on server=" + localServer);
    }

    private void setupCrossServerHandlers(PacketManager pm) {
        pm.subscribe(AfkPoolEnterPacket.class, (channel, pkt) -> {
            if (pkt == null) return;

            String target = pkt.getTargetServer();
            if (target == null || target.isEmpty()) return;
            if (!localServer.equalsIgnoreCase(target)) return;

            UUID playerId = pkt.getPlayerId();
            BackLocation returnLoc = pkt.getReturnLocation();
            if (playerId == null) return;

            plugin.getLogger().info("[AfkPool] Received AfkPoolEnterPacket target=" + target
                    + " local=" + localServer + " playerId=" + playerId);

            Bukkit.getScheduler().runTask(plugin, () -> {
                Player player = Bukkit.getPlayer(playerId);

                if (returnLoc != null) afkReturnLocations.put(playerId, returnLoc);

                if (player != null && player.isOnline()) {
                    markInPool(playerId);
                    setGrace(playerId, 1500);
                    teleportToAfkPool(player);
                } else {
                    pendingPoolTeleports.put(playerId, true);
                }
            });
        });

        pm.subscribe(AfkPoolExitPacket.class, (channel, pkt) -> {
            if (pkt == null) return;

            String target = pkt.getTargetServer();
            if (target == null || target.isEmpty()) return;
            if (!localServer.equalsIgnoreCase(target)) return;

            UUID playerId = pkt.getPlayerId();
            BackLocation returnLoc = pkt.getReturnLocation();
            if (playerId == null) return;

            plugin.getLogger().info("[AfkPool] Received AfkPoolExitPacket target=" + target
                    + " local=" + localServer + " playerId=" + playerId);

            Bukkit.getScheduler().runTask(plugin, () -> {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline() && returnLoc != null) {
                    setGrace(playerId, 1500);
                    returnFromAfkPool(player, returnLoc);
                    cleanup(playerId);
                } else if (returnLoc != null) {
                    pendingReturnTeleports.put(playerId, returnLoc);
                }

            });
        });
    }

    public boolean sendPlayerToAfkPool(Player player) {
        if (!enabled) return false;
        if (player == null || !player.isOnline()) return false;

        UUID playerId = player.getUniqueId();

        BackLocation returnLoc = BackLocation.from(localServer, player.getLocation());
        if (returnLoc == null) {
            plugin.getLogger().warning("[AfkPool] Could not create BackLocation for player " + player.getName());
            return false;
        }

        afkReturnLocations.put(playerId, returnLoc);
        markInPool(playerId);
        setGrace(playerId, 1500);

        if (crossServerEnabled && !localServer.equalsIgnoreCase(afkPoolServer)) {

            PacketManager pm = plugin.getPacketManager();

            if (pm == null || !pm.isInitialized()) {
                plugin.getLogger().warning("[AfkPool] PacketManager not ready, retrying soon. local=" + localServer);

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!player.isOnline()) return;

                    PacketManager pm2 = plugin.getPacketManager();
                    if (pm2 == null || !pm2.isInitialized()) {
                        plugin.getLogger().warning("[AfkPool] PacketManager still not ready, cannot cross-server send. local=" + localServer);
                        Lang.send(player, "afk.pool.not-ready",
                                "<red>AFK Pool is not ready yet. Please try again in a few seconds.</red>",
                                Map.of());
                        return;
                    }

                    AfkPoolEnterPacket packet = new AfkPoolEnterPacket(playerId, returnLoc, afkPoolServer);

                    plugin.getLogger().info("[AfkPool] Sending AfkPoolEnterPacket from=" + localServer
                            + " to=" + afkPoolServer + " player=" + player.getName());

                    pm2.sendPacket(packet);

                    plugin.getLogger().info("[AfkPool] Proxy sending player " + player.getName() + " to server " + afkPoolServer);
                    plugin.getProxyMessenger().sendToServer(player, afkPoolServer);

                    Lang.send(player, "afk.pool.cross-server",
                            "<gray>You've been moved to the AFK pool on <aqua>%server%</aqua>.</gray>",
                            Map.of("server", afkPoolServer));
                }, 40L);

                return true;
            }

            AfkPoolEnterPacket packet = new AfkPoolEnterPacket(playerId, returnLoc, afkPoolServer);

            plugin.getLogger().info("[AfkPool] Sending AfkPoolEnterPacket from=" + localServer
                    + " to=" + afkPoolServer + " player=" + player.getName());

            pm.sendPacket(packet);

            plugin.getLogger().info("[AfkPool] Proxy sending player " + player.getName() + " to server " + afkPoolServer);
            plugin.getProxyMessenger().sendToServer(player, afkPoolServer);

            Lang.send(player, "afk.pool.cross-server",
                    "<gray>You've been moved to the AFK pool on <aqua>%server%</aqua>.</gray>",
                    Map.of("server", afkPoolServer));

            return true;
        }

        teleportToAfkPool(player);
        return true;
    }

    public void returnPlayerFromAfkPool(Player player) {
        if (!enabled) return;
        if (player == null || !player.isOnline()) return;

        UUID playerId = player.getUniqueId();
        BackLocation returnLoc = afkReturnLocations.get(playerId);

        if (returnLoc == null) {
            plugin.getLogger().warning("[AfkPool] No return location found for " + player.getName());
            cleanup(playerId);
            return;
        }

        unmarkInPool(playerId);
        setGrace(playerId, 1500);

        String returnServer = returnLoc.getServer();

        if (crossServerEnabled && returnServer != null && !returnServer.isEmpty()
                && !localServer.equalsIgnoreCase(returnServer)) {

            PacketManager pm = plugin.getPacketManager();
            if (pm == null || !pm.isInitialized()) {
                plugin.getLogger().warning("[AfkPool] PacketManager not ready, cannot cross-server return. local=" + localServer);
                return;
            }

            AfkPoolExitPacket packet = new AfkPoolExitPacket(playerId, returnLoc, returnServer);

            plugin.getLogger().info("[AfkPool] Sending AfkPoolExitPacket from=" + localServer
                    + " to=" + returnServer + " player=" + player.getName());

            pm.sendPacket(packet);

            plugin.getLogger().info("[AfkPool] Proxy returning player " + player.getName() + " to server " + returnServer);
            plugin.getProxyMessenger().sendToServer(player, returnServer);

            Lang.send(player, "afk.pool.cross-server-return",
                    "<gray>Returning you to <aqua>%server%</aqua>...</gray>",
                    Map.of("server", returnServer));

            cleanup(playerId);
            return;
        }

        returnFromAfkPool(player, returnLoc);
        cleanup(playerId);
    }

    private void teleportToAfkPool(Player player) {
        Location poolLocation = getAfkPoolLocation();

        if (poolLocation == null) {
            plugin.getLogger().warning("[AfkPool] Could not find AFK pool location for region="
                    + afkPoolRegionName + " world=" + afkPoolWorldName + " server=" + localServer);
            return;
        }

        setGrace(player.getUniqueId(), 1500);
        player.teleport(poolLocation);

        Lang.send(player, "afk.pool.entered",
                "<gray>You've been moved to the AFK pool. Move to return to your original location.</gray>");
    }

    private void returnFromAfkPool(Player player, BackLocation returnLoc) {
        Location loc = returnLoc.toLocalLocation();

        if (loc == null) {
            plugin.getLogger().warning("[AfkPool] Could not convert return location for " + player.getName());
            return;
        }

        setGrace(player.getUniqueId(), 1500);
        player.teleport(loc);

        Lang.send(player, "afk.pool.exited",
                "<green>You've been returned to your original location.</green>");
    }

    private Location getAfkPoolLocation() {
        World world = Bukkit.getWorld(afkPoolWorldName);
        if (world == null) return null;

        try {
            RegionManager regionManager = WorldGuard.getInstance()
                    .getPlatform()
                    .getRegionContainer()
                    .get(BukkitAdapter.adapt(world));

            if (regionManager == null) return null;

            ProtectedRegion region = regionManager.getRegion(afkPoolRegionName);
            if (region == null) return null;

            var min = region.getMinimumPoint();
            var max = region.getMaximumPoint();

            double x = (min.x() + max.x()) / 2.0 + 0.5;
            double y = min.y() + 1;
            double z = (min.z() + max.z()) / 2.0 + 0.5;

            return new Location(world, x, y, z);
        } catch (Throwable t) {
            plugin.getLogger().severe("[AfkPool] Error getting region location: " + t.getMessage());
            return null;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!enabled) return;

        Player player = event.getPlayer();
        if (player == null) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        UUID playerId = player.getUniqueId();

        if (!isInAfkPool(player)) return;

        long graceUntil = moveGraceUntilMs.getOrDefault(playerId, 0L);
        if (System.currentTimeMillis() < graceUntil) return;

        if (afkService != null) {
            afkService.clearAfk(player);
        }

        returnPlayerFromAfkPool(player);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!enabled) return;

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        BackLocation pendingReturn = pendingReturnTeleports.remove(playerId);
        if (pendingReturn != null) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;

                setGrace(playerId, 1500);
                returnFromAfkPool(player, pendingReturn);
                cleanup(playerId);
            }, 20L);
            return;
        }

        if (Boolean.TRUE.equals(pendingPoolTeleports.remove(playerId))) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;

                markInPool(playerId);
                setGrace(playerId, 1500);
                teleportToAfkPool(player);
            }, 20L);
        }
    }


    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();

        if (!crossServerEnabled) {
            cleanup(playerId);
        }

        pendingPoolTeleports.remove(playerId);
        moveGraceUntilMs.remove(playerId);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isInAfkPool(Player player) {
        return player != null && afkReturnLocations.containsKey(player.getUniqueId());
    }

    public void cleanup(Player player) {
        if (player == null) return;
        cleanup(player.getUniqueId());
    }

    public void cleanupAll() {
        afkReturnLocations.clear();
        pendingPoolTeleports.clear();
        moveGraceUntilMs.clear();
    }

    private void cleanup(UUID playerId) {
        if (playerId == null) return;
        afkReturnLocations.remove(playerId);
        pendingPoolTeleports.remove(playerId);
        moveGraceUntilMs.remove(playerId);
    }

    private void markInPool(UUID playerId) {
        if (playerId == null) return;
        moveGraceUntilMs.putIfAbsent(playerId, 0L);
    }

    private void unmarkInPool(UUID playerId) {
        if (playerId == null) return;
        moveGraceUntilMs.remove(playerId);
    }

    private void setGrace(UUID playerId, long millis) {
        if (playerId == null) return;
        moveGraceUntilMs.put(playerId, System.currentTimeMillis() + Math.max(0L, millis));
    }
}
