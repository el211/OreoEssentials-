package fr.elias.oreoEssentials.modules.grouprtp.rabbit;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.grouprtp.service.SafeLocationFinder;
import fr.elias.oreoEssentials.rabbitmq.packet.PacketManager;
import fr.elias.oreoEssentials.util.OreScheduler;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Receives {@link GroupRtpSyncPacket} on the target server, finds a safe
 * wilderness location async, and teleports each player once they join.
 *
 * <h3>Flow (target server)</h3>
 * <ol>
 *   <li>Originating server sends packet + BungeeCord Connect.</li>
 *   <li>This broker receives the packet, finds a base location async, then
 *       computes a scatter destination for every player UUID in the group.</li>
 *   <li>Players that are already online are teleported immediately; others are
 *       stored in a pending map and teleported on {@code PlayerJoinEvent}.</li>
 * </ol>
 */
public final class GroupRtpCrossServerBroker implements Listener {

    /** playerId → pre-computed scatter destination waiting for the player to join */
    private final ConcurrentHashMap<UUID, Location> pending = new ConcurrentHashMap<>();
    /** playerId → error message to deliver once the player joins (when location search failed) */
    private final ConcurrentHashMap<UUID, String> pendingFailed = new ConcurrentHashMap<>();
    /** requestId dedup — prevents double-processing on RabbitMQ redelivery.
     *  Value is the timestamp (ms epoch) when the entry was recorded; old entries
     *  are evicted on each check to prevent unbounded growth. */
    private final ConcurrentHashMap<String, Long> seenRequests = new ConcurrentHashMap<>();
    private static final long SEEN_REQUESTS_TTL_MS = 30_000L; // 30 seconds

    private final OreoEssentials plugin;

    public GroupRtpCrossServerBroker(OreoEssentials plugin, PacketManager pm) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);

        pm.subscribe(GroupRtpSyncPacket.class, (channel, pkt) -> {
            if (pkt.getRequestId() == null || pkt.getPlayerUuids() == null
                    || pkt.getPlayerUuids().isEmpty()) return;

            // Dedup — ignore redeliveries of the same request.
            // Sweep expired entries first to keep the map bounded.
            long now = System.currentTimeMillis();
            seenRequests.entrySet().removeIf(e -> now - e.getValue() > SEEN_REQUESTS_TTL_MS);
            if (seenRequests.putIfAbsent(pkt.getRequestId(), now) != null) return;

            List<UUID> players = new ArrayList<>(pkt.getPlayerUuids());
            Set<String> unsafeBlocks     = new HashSet<>(pkt.getUnsafeBlocks());
            Set<String> blacklistedBiomes = new HashSet<>(pkt.getBlacklistedBiomes());

            OreScheduler.runAsync(plugin, () -> {
                World world = Bukkit.getWorld(pkt.getWorldName());
                if (world == null) {
                    plugin.getLogger().warning("[GroupRTP] Cross-server broker: world '"
                            + pkt.getWorldName() + "' not found on this server (portal: "
                            + pkt.getPortalId() + ").");
                    for (UUID uuid : players) pendingFailed.put(uuid,
                            ChatColor.RED + "Could not find a safe location. Please try again later.");
                    return;
                }

                Location base = SafeLocationFinder.find(world,
                        pkt.getRtpRadius(), pkt.getRtpMinRadius(),
                        pkt.getRtpCenterX(), pkt.getRtpCenterZ(),
                        pkt.getRtpMinY(), pkt.getRtpMaxY(), pkt.getRtpAttempts(),
                        unsafeBlocks, blacklistedBiomes);

                if (base == null) {
                    plugin.getLogger().warning("[GroupRTP] Cross-server broker: could not find safe"
                            + " location for portal " + pkt.getPortalId());
                    for (UUID uuid : players) pendingFailed.put(uuid,
                            ChatColor.RED + "Could not find a safe location. Please try again later.");
                    return;
                }

                for (UUID uuid : players) {
                    Location dest = SafeLocationFinder.scatter(base, pkt.getClusterRadius(),
                            world, unsafeBlocks);

                    Player online = Bukkit.getPlayer(uuid);
                    if (online != null && online.isOnline()) {
                        applyTeleport(online, dest);
                    } else {
                        pending.put(uuid, dest);
                    }
                }
            });
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();

        // Handle failed location search — notify player after a short settle delay
        String failMsg = pendingFailed.remove(p.getUniqueId());
        if (failMsg != null) {
            OreScheduler.runLater(plugin, () -> {
                if (p.isOnline()) p.sendMessage(failMsg);
            }, 20L);
            return;
        }

        Location dest = pending.remove(p.getUniqueId());
        if (dest == null) return;

        // Retry at 1, 5, 20, 60 ticks — the world may still be loading when the join fires
        AtomicBoolean done = new AtomicBoolean(false);
        for (long delay : new long[]{1L, 5L, 20L, 60L}) {
            OreScheduler.runLater(plugin, () -> {
                if (!p.isOnline() || done.get()) return;
                if (dest.getWorld() == null || Bukkit.getWorld(dest.getWorld().getName()) == null) return;
                if (done.compareAndSet(false, true)) {
                    applyTeleport(p, dest);
                }
            }, delay);
        }
    }

    private void applyTeleport(Player p, Location dest) {
        OreScheduler.runForEntity(plugin, p, () -> {
            if (!p.isOnline()) return;
            p.teleportAsync(dest).thenRun(() ->
                    OreScheduler.runForEntity(plugin, p, () -> {
                        if (!p.isOnline()) return;
                        p.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD
                                + "You have been teleported to the wilderness!");
                        p.sendTitle(
                                ChatColor.translateAlternateColorCodes('&', "&a&lTeleported!"),
                                ChatColor.translateAlternateColorCodes('&', "&7Explore the wilderness"),
                                5, 40, 10);
                    }));
        });
    }
}
