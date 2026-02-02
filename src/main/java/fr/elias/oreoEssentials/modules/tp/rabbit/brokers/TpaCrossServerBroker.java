package fr.elias.oreoEssentials.modules.tp.rabbit.brokers;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.rabbitmq.PacketChannels;
import fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel;
import fr.elias.oreoEssentials.rabbitmq.packet.PacketManager;
import fr.elias.oreoEssentials.modules.tp.rabbit.packets.TpaRequestPacket;
import fr.elias.oreoEssentials.modules.tp.rabbit.packets.TpaSummonPacket;
import fr.elias.oreoEssentials.modules.tp.service.TeleportService;
import fr.elias.oreoEssentials.util.Lang;
import fr.elias.oreoEssentials.util.ProxyMessenger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TpaCrossServerBroker implements Listener {

    private final OreoEssentials plugin;
    private final TeleportService teleportService;
    private final PacketManager pm;
    private final ProxyMessenger proxy;
    private final String localServer;

    private final Map<UUID, Pending> pendingForTarget = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> pendingArrival = new ConcurrentHashMap<>();

    private final long expireMs;
    private final boolean offlineUuidCompat;

    private static class Pending {
        UUID requesterUuid;
        String requesterName;
        String fromServer;
        long expiresAt;
    }

    public TpaCrossServerBroker(OreoEssentials plugin, TeleportService teleportService,
                                PacketManager pm, ProxyMessenger proxy, String localServer) {
        this.plugin = plugin;
        this.teleportService = teleportService;
        this.pm = pm;
        this.proxy = proxy;
        this.localServer = localServer;

        long cfgSec = 60L;
        try {
            cfgSec = plugin.getConfig().getLong("features.tpa.expire-seconds",
                    plugin.getConfig().getLong("tpa.expire-seconds", 60L));
            if (cfgSec <= 0) cfgSec = 60L;
        } catch (Throwable ignored) {}
        this.expireMs = cfgSec * 1000L;

        boolean compat = true;
        try {
            compat = plugin.getConfig().getBoolean("features.tpa.offline-uuid-compat", true);
        } catch (Throwable ignored) {}
        this.offlineUuidCompat = compat;

        if (pm != null && pm.isInitialized()) {
            pm.subscribe(TpaRequestPacket.class, this::onTpaRequest);
            pm.subscribe(TpaSummonPacket.class, this::onTpaSummon);
            dbg("Subscribed TpaRequestPacket + TpaSummonPacket (expire=" + cfgSec + "s, offlineCompat=" + offlineUuidCompat + ")");
        } else {
            dbg("PacketManager not initialized; cross-server TPA disabled on this node.");
        }

        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::purgeExpired, 20L * 30, 20L * 30);
    }

    public void sendRequestToServer(Player requester, UUID targetUuid, String destServer) {
        sendRequestToServer(requester, targetUuid, "", destServer);
    }

    public void sendRequestGlobal(Player requester, UUID targetUuid) {
        sendRequestGlobal(requester, targetUuid, "");
    }

    public void sendRequestToServer(Player requester, UUID targetUuid, String targetName, String destServer) {
        if (!isMessagingReady() || requester == null) return;
        long now = System.currentTimeMillis();
        TpaRequestPacket pkt = new TpaRequestPacket(requester.getUniqueId(), requester.getName(),
                targetUuid, targetName != null ? targetName : "", localServer, now + expireMs);
        pm.sendPacket(PacketChannel.individual(destServer), pkt);
        dbg("Send TpaRequest -> server=" + destServer + " requester=" + requester.getName()
                + " target=" + targetUuid + (targetName != null && !targetName.isBlank() ? " (" + targetName + ")" : ""));
    }

    public void sendRequestGlobal(Player requester, UUID targetUuid, String targetName) {
        if (!isMessagingReady() || requester == null) return;
        long now = System.currentTimeMillis();
        TpaRequestPacket pkt = new TpaRequestPacket(requester.getUniqueId(), requester.getName(),
                targetUuid, targetName != null ? targetName : "", localServer, now + expireMs);
        pm.sendPacket(PacketChannels.GLOBAL, pkt);
        dbg("Send TpaRequest (GLOBAL) requester=" + requester.getName()
                + " target=" + targetUuid + (targetName != null && !targetName.isBlank() ? " (" + targetName + ")" : ""));
    }

    public boolean acceptCrossServer(Player target) {
        if (target == null) return false;

        Pending p = pendingForTarget.remove(target.getUniqueId());
        if (p == null) {
            dbg("No cross-server pending for " + target.getName());
            return false;
        }

        long now = System.currentTimeMillis();
        if (p.expiresAt > 0 && p.expiresAt < now) {
            Lang.send(target, "tpa.accept.expired", "<red>That teleport request expired.</red>");
            dbg("Pending expired (target=" + target.getUniqueId() + ")");
            return true;
        }

        pendingArrival.put(p.requesterUuid, target.getUniqueId());

        if (isMessagingReady()) {
            pm.sendPacket(PacketChannel.individual(p.fromServer),
                    new TpaSummonPacket(p.requesterUuid, localServer));
        }

        Lang.send(target, "tpa.accept.summon",
                "<green>Teleport request accepted.</green> <gray>Summoning</gray> <yellow>%player%</yellow><gray>…</gray>",
                Map.of("player", p.requesterName));
        dbg("Accept -> summon requester=" + p.requesterUuid
                + " from=" + p.fromServer + " to=" + localServer
                + " (target=" + target.getName() + ")");

        Bukkit.getScheduler().runTaskLater(plugin,
                () -> pendingArrival.remove(p.requesterUuid), 20L * 60);

        return true;
    }

    public boolean denyCrossServer(Player target) {
        Pending p = pendingForTarget.remove(target.getUniqueId());
        if (p == null) return false;

        Lang.send(target, "tpa.deny.target",
                "<yellow>Denied the teleport request from</yellow> <white>%player%</white>.",
                Map.of("player", p.requesterName));
        dbg("Denied pending for target=" + target.getName() + " requester=" + p.requesterUuid);
        return true;
    }

    public boolean hasPendingFor(Player target) {
        return pendingForTarget.containsKey(target.getUniqueId());
    }

    private void onTpaRequest(PacketChannel channel, TpaRequestPacket pkt) {
        if (pkt == null) return;

        long now = System.currentTimeMillis();
        if (pkt.getExpiresAtEpochMs() > 0 && pkt.getExpiresAtEpochMs() < now) {
            dbg("TpaRequestPacket expired; ignoring. from=" + pkt.getFromServer());
            return;
        }

        Player target = resolveTarget(pkt.getTargetUuid(), pkt.getTargetName());
        if (target == null) {
            return;
        }

        Pending p = new Pending();
        p.requesterUuid = pkt.getRequesterUuid();
        p.requesterName = pkt.getRequesterName();
        p.fromServer = pkt.getFromServer();
        p.expiresAt = (pkt.getExpiresAtEpochMs() > 0 ? pkt.getExpiresAtEpochMs() : now + expireMs);

        pendingForTarget.put(target.getUniqueId(), p);

        Lang.send(target, "tpa.request-target",
                "<yellow><bold>%player%</bold></yellow> <gray>wants to teleport to you.</gray> "
                        + "<dark_gray>(expires in</dark_gray> <white>%timeout%</white><dark_gray>s)</dark_gray> "
                        + "<gray>Use</gray> <green>/tpaccept</green> <gray>or</gray> <red>/tpdeny</red>.",
                Map.of("player", p.requesterName, "timeout", String.valueOf((p.expiresAt - now) / 1000L)));
        dbg("Saved pending for target=" + target.getUniqueId() + " name=" + target.getName()
                + " fromServer=" + p.fromServer + " expiresAt=" + p.expiresAt);
    }

    private void onTpaSummon(PacketChannel channel, TpaSummonPacket pkt) {
        if (pkt == null) return;

        Player requester = Bukkit.getPlayer(pkt.getRequesterUuid());
        if (requester == null) {
            dbg("Summon received, but requester not online here: " + pkt.getRequesterUuid());
            return;
        }

        var root = plugin.getSettingsConfig().getRoot();
        var sec = root.getConfigurationSection("features.tpa");

        boolean enabled = sec != null && sec.getBoolean("cooldown", false);
        int seconds = (sec != null ? sec.getInt("cooldown-amount", 0) : 0);

        if (!enabled || seconds <= 0) {
            boolean ok = connectToServer(requester, pkt.getDestServer());
            if (ok) {
                Lang.send(requester, "tpa.cross.connecting",
                        "<gray>Connecting you to</gray> <yellow>%server%</yellow><gray>…</gray>",
                        Map.of("server", pkt.getDestServer()));
            } else {
                plugin.getLogger().warning("[TPA-X] Could not send " + requester.getName()
                        + " to server " + pkt.getDestServer() + " (no ProxyMessenger method matched)");
            }
            return;
        }

        final String destServer = pkt.getDestServer();
        final Location origin = requester.getLocation().clone();

        dbg("Starting cross-server TPA countdown for requester=" + requester.getName()
                + " destServer=" + destServer + " seconds=" + seconds);

        new org.bukkit.scheduler.BukkitRunnable() {
            int remain = seconds;

            @Override
            public void run() {
                if (!requester.isOnline()) {
                    dbg("Requester went offline during cross-server countdown; cancel.");
                    cancel();
                    return;
                }

                if (hasBodyMoved(requester, origin)) {
                    Lang.send(requester, "teleport.countdown.cancelled-moved",
                            "<red>Teleport cancelled: you moved.</red>");
                    dbg("Requester moved; cancelling cross-server countdown.");
                    cancel();
                    return;
                }

                if (remain <= 0) {
                    cancel();
                    dbg("Cross-server countdown finished; connecting " + requester.getName() + " -> " + destServer);
                    boolean ok = connectToServer(requester, destServer);
                    if (ok) {
                        Lang.send(requester, "tpa.cross.connecting",
                                "<gray>Connecting you to</gray> <yellow>%server%</yellow><gray>…</gray>",
                                Map.of("server", destServer));
                    } else {
                        plugin.getLogger().warning("[TPA-X] Failed to connect " + requester.getName() + " to " + destServer);
                    }
                    return;
                }

                String title = Lang.msg("teleport.countdown.title", "<yellow>Teleporting…</yellow>", requester);
                String subtitle = Lang.msgWithDefault("teleport.countdown.subtitle",
                        "<gray>Teleporting in <white>%seconds%</white>s…</gray>",
                        Map.of("seconds", String.valueOf(remain)), requester);

                requester.sendTitle(title, subtitle, 0, 20, 0);
                remain--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private boolean hasBodyMoved(Player p, Location origin) {
        Location now = p.getLocation();
        return now.getBlockX() != origin.getBlockX() || now.getBlockZ() != origin.getBlockZ();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        UUID targetUuid = pendingArrival.remove(e.getPlayer().getUniqueId());
        if (targetUuid == null) return;

        Player target = Bukkit.getPlayer(targetUuid);
        if (target == null) {
            Lang.send(e.getPlayer(), "tpa.arrival.target-offline", "<red>Teleport target went offline.</red>");
            dbg("Arrival: target offline for requester=" + e.getPlayer().getName());
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                Location to = target.getLocation();
                e.getPlayer().teleport(to);
                dbg("Arrival: snapped " + e.getPlayer().getName() + " -> " + target.getName());
            } catch (Throwable t) {
                plugin.getLogger().warning("[TPA-X] Arrival teleport failed: " + t.getMessage());
            }
        }, 3L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        pendingArrival.remove(e.getPlayer().getUniqueId());
    }

    private boolean isMessagingReady() {
        return pm != null && pm.isInitialized();
    }

    private Player resolveTarget(UUID targetUuid, String targetName) {
        Player p = Bukkit.getPlayer(targetUuid);
        if (p != null) return p;

        if (!offlineUuidCompat) return null;

        if (targetName != null && !targetName.isBlank()) {
            Player byExact = Bukkit.getPlayerExact(targetName);
            if (byExact != null) return byExact;
            String want = targetName.toLowerCase(java.util.Locale.ROOT);
            for (Player op : Bukkit.getOnlinePlayers()) {
                if (op.getName().equalsIgnoreCase(want)) return op;
            }
        }

        try {
            var dir = plugin.getPlayerDirectory();
            if (dir != null) {
                String lastKnownName = dir.lookupNameByUuid(targetUuid);
                if (lastKnownName != null && !lastKnownName.isBlank()) {
                    Player byExact = Bukkit.getPlayerExact(lastKnownName);
                    if (byExact != null) return byExact;
                    String want = lastKnownName.toLowerCase(java.util.Locale.ROOT);
                    for (Player op : Bukkit.getOnlinePlayers()) {
                        if (op.getName().equalsIgnoreCase(want)) return op;
                    }
                }
            }
        } catch (Throwable ignored) {}

        return null;
    }

    private boolean connectToServer(Player player, String server) {
        try {
            for (String m : new String[]{"connect", "send", "sendToServer"}) {
                Method method = find(proxy.getClass(), m, Player.class, String.class);
                if (method != null) {
                    method.invoke(proxy, player, server);
                    return true;
                }
            }
        } catch (Throwable ignored) {}

        try {
            for (String m : new String[]{"connect", "send", "sendToServer"}) {
                Method method = find(proxy.getClass(), m, String.class);
                if (method != null) {
                    method.invoke(proxy, server);
                    return true;
                }
            }
        } catch (Throwable ignored) {}

        return false;
    }

    private static Method find(Class<?> cls, String name, Class<?>... params) {
        try {
            Method m = cls.getMethod(name, params);
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        int removed = 0;
        Iterator<Map.Entry<UUID, Pending>> it = pendingForTarget.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Pending> e = it.next();
            Pending p = e.getValue();
            if (p == null) {
                it.remove();
                removed++;
                continue;
            }
            if (p.expiresAt > 0 && p.expiresAt < now) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) dbg("Purged " + removed + " expired pending requests.");
    }

    private void dbg(String msg) {
        try {
            if (plugin.getConfig().getBoolean("features.tpa.debug",
                    plugin.getConfig().getBoolean("debug", false))) {
                plugin.getLogger().info("[TPA-X@" + localServer + "] " + msg);
            }
        } catch (Throwable ignored) {}
    }
}