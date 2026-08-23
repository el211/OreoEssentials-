package fr.elias.oreoEssentials.modules.tp.rabbit.brokers;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.tp.rabbit.packets.TpaRequestPacket;
import fr.elias.oreoEssentials.modules.tp.rabbit.packets.TpaSummonPacket;
import fr.elias.oreoEssentials.modules.tp.service.TeleportService;
import fr.elias.oreoEssentials.rabbitmq.PacketChannels;
import fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel;
import fr.elias.oreoEssentials.rabbitmq.packet.PacketManager;
import fr.elias.oreoEssentials.util.Lang;
import fr.elias.oreoEssentials.util.OreScheduler;
import fr.elias.oreoEssentials.util.OreTask;
import fr.elias.oreoEssentials.util.ProxyMessenger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TpaCrossServerBroker implements Listener {

    private final OreoEssentials plugin;
    @SuppressWarnings("unused")
    private final TeleportService teleportService;
    private final PacketManager pm;
    private final ProxyMessenger proxy;
    private final String localServer;

    private final Map<UUID, Pending> pendingForTarget = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> pendingArrival = new ConcurrentHashMap<>();
    private final long expireMs;
    private final boolean offlineUuidCompat;

    private static final class Pending {
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

        long cfgSec = plugin.getConfig().getLong("features.tpa.expire-seconds",
                plugin.getConfig().getLong("tpa.expire-seconds", 60L));
        if (cfgSec <= 0) cfgSec = 60L;
        this.expireMs = cfgSec * 1000L;
        this.offlineUuidCompat = plugin.getConfig().getBoolean("features.tpa.offline-uuid-compat", true);

        if (pm != null && pm.isInitialized()) {
            pm.subscribe(TpaRequestPacket.class, this::onTpaRequest);
            pm.subscribe(TpaSummonPacket.class, this::onTpaSummon);
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        OreScheduler.runTimer(plugin, this::purgeExpired, 20L * 30, 20L * 30);
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
        pm.sendPacket(PacketChannel.individual(destServer), new TpaRequestPacket(
                requester.getUniqueId(), requester.getName(), targetUuid,
                targetName == null ? "" : targetName, localServer, now + expireMs));
    }

    public void sendRequestGlobal(Player requester, UUID targetUuid, String targetName) {
        if (!isMessagingReady() || requester == null) return;
        long now = System.currentTimeMillis();
        pm.sendPacket(PacketChannels.GLOBAL, new TpaRequestPacket(
                requester.getUniqueId(), requester.getName(), targetUuid,
                targetName == null ? "" : targetName, localServer, now + expireMs));
    }

    public boolean acceptCrossServer(Player target) {
        if (target == null) return false;
        Pending p = pendingForTarget.remove(target.getUniqueId());
        if (p == null) return false;

        long now = System.currentTimeMillis();
        if (p.expiresAt > 0 && p.expiresAt < now) {
            Lang.send(target, "tpa.accept.expired", "<red>That teleport request expired.</red>");
            return true;
        }
        if (pendingArrival.putIfAbsent(p.requesterUuid, target.getUniqueId()) != null) {
            Lang.send(target, "tpa.accept.busy", "<red>That requester is already being summoned to another player.</red>");
            return true;
        }

        if (isMessagingReady()) {
            pm.sendPacket(PacketChannel.individual(p.fromServer), new TpaSummonPacket(p.requesterUuid, localServer));
        }
        Lang.send(target, "tpa.accept.summon",
                "<green>Teleport request accepted.</green> <gray>Summoning</gray> <yellow>%player%</yellow><gray>…</gray>",
                Map.of("player", p.requesterName));
        OreScheduler.runLater(plugin, () -> pendingArrival.remove(p.requesterUuid), 20L * 60);
        return true;
    }

    public boolean denyCrossServer(Player target) {
        Pending p = pendingForTarget.remove(target.getUniqueId());
        if (p == null) return false;
        Lang.send(target, "tpa.deny.target",
                "<yellow>Denied the teleport request from</yellow> <white>%player%</white>.",
                Map.of("player", p.requesterName));
        return true;
    }

    public boolean hasPendingFor(Player target) {
        return target != null && pendingForTarget.containsKey(target.getUniqueId());
    }

    private void onTpaRequest(PacketChannel channel, TpaRequestPacket pkt) {
        if (pkt == null) return;
        long now = System.currentTimeMillis();
        if (pkt.getExpiresAtEpochMs() > 0 && pkt.getExpiresAtEpochMs() < now) return;

        Player target = resolveOnlineTarget(pkt.getTargetUuid(), pkt.getTargetName());
        if (target == null) return;

        Pending p = new Pending();
        p.requesterUuid = pkt.getRequesterUuid();
        p.requesterName = pkt.getRequesterName();
        p.fromServer = pkt.getFromServer();
        p.expiresAt = pkt.getExpiresAtEpochMs() > 0 ? pkt.getExpiresAtEpochMs() : now + expireMs;

        OreScheduler.runForEntity(plugin, target, () -> {
            if (!target.isOnline()) return;
            pendingForTarget.put(target.getUniqueId(), p);
            Lang.send(target, "tpa.request-target",
                    "<yellow><bold>%player%</bold></yellow> <gray>wants to teleport to you.</gray> "
                            + "<dark_gray>(expires in</dark_gray> <white>%timeout%</white><dark_gray>s)</dark_gray> "
                            + "<gray>Use</gray> <green>/tpaccept</green> <gray>or</gray> <red>/tpdeny</red>.",
                    Map.of("player", p.requesterName,
                            "timeout", String.valueOf(Math.max(0L, (p.expiresAt - System.currentTimeMillis()) / 1000L))));
        });
    }

    private void onTpaSummon(PacketChannel channel, TpaSummonPacket pkt) {
        if (pkt == null) return;
        Player requester = Bukkit.getPlayer(pkt.getRequesterUuid());
        if (requester == null) return;

        OreScheduler.runForEntity(plugin, requester, () -> {
            if (!requester.isOnline()) return;

            var sec = plugin.getSettingsConfig().getRoot().getConfigurationSection("features.tpa");
            boolean countdownEnabled = sec != null && sec.getBoolean("cooldown", false);
            int seconds = sec != null ? sec.getInt("cooldown-amount", 0) : 0;
            String destServer = pkt.getDestServer();

            if (!countdownEnabled || seconds <= 0) {
                connectAndNotify(requester, destServer);
                return;
            }

            Location origin = requester.getLocation().clone();
            int[] remain = {seconds};
            OreTask[] holder = new OreTask[1];
            holder[0] = OreScheduler.runTimerForEntity(plugin, requester, () -> {
                if (!requester.isOnline()) {
                    holder[0].cancel();
                    return;
                }
                if (hasBodyMoved(requester, origin)) {
                    Lang.send(requester, "teleport.countdown.cancelled-moved", "<red>Teleport cancelled: you moved.</red>");
                    holder[0].cancel();
                    return;
                }
                if (remain[0] <= 0) {
                    holder[0].cancel();
                    connectAndNotify(requester, destServer);
                    return;
                }

                String title = Lang.msg("teleport.countdown.title", "<yellow>Teleporting…</yellow>", requester);
                String subtitle = Lang.msgWithDefault("teleport.countdown.subtitle",
                        "<gray>Teleporting in <white>%seconds%</white>s…</gray>",
                        Map.of("seconds", String.valueOf(remain[0])), requester);
                requester.sendTitle(title, subtitle, 0, 20, 0);
                remain[0]--;
            }, 0L, 20L);
        });
    }

    private void connectAndNotify(Player requester, String destServer) {
        boolean ok = connectToServer(requester, destServer);
        if (ok) {
            Lang.send(requester, "tpa.cross.connecting",
                    "<gray>Connecting you to</gray> <yellow>%server%</yellow><gray>…</gray>",
                    Map.of("server", destServer));
        } else {
            plugin.getLogger().warning("[TPA-X] Failed to connect " + requester.getName() + " to " + destServer);
        }
    }

    private boolean hasBodyMoved(Player p, Location origin) {
        Location now = p.getLocation();
        return now.getBlockX() != origin.getBlockX()
                || now.getBlockY() != origin.getBlockY()
                || now.getBlockZ() != origin.getBlockZ();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player requester = e.getPlayer();
        UUID targetUuid = pendingArrival.remove(requester.getUniqueId());
        if (targetUuid == null) return;

        Player target = Bukkit.getPlayer(targetUuid);
        if (target == null) {
            Lang.send(requester, "tpa.arrival.target-offline", "<red>Teleport target went offline.</red>");
            return;
        }

        // Capture the destination on the target's region, then teleport the requester on
        // the requester's region. Never read target.getLocation() from another region.
        OreScheduler.runForEntity(plugin, target, () -> {
            if (!target.isOnline()) {
                OreScheduler.runForEntity(plugin, requester, () ->
                        Lang.send(requester, "tpa.arrival.target-offline", "<red>Teleport target went offline.</red>"));
                return;
            }
            Location destination = target.getLocation().clone();
            String targetName = target.getName();
            OreScheduler.runLaterForEntity(plugin, requester, () -> {
                if (!requester.isOnline()) return;
                if (OreScheduler.isFolia()) requester.teleportAsync(destination);
                else requester.teleport(destination);
                dbg("Arrival: snapped " + requester.getName() + " -> " + targetName);
            }, 3L);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        pendingArrival.remove(e.getPlayer().getUniqueId());
    }

    private boolean isMessagingReady() {
        return pm != null && pm.isInitialized();
    }

    private Player resolveOnlineTarget(UUID targetUuid, String targetName) {
        Player p = Bukkit.getPlayer(targetUuid);
        if (p != null) return p;
        if (!offlineUuidCompat || targetName == null || targetName.isBlank()) return null;
        Player exact = Bukkit.getPlayerExact(targetName);
        if (exact != null) return exact;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getName().equalsIgnoreCase(targetName)) return online;
        }
        // Intentionally no PlayerDirectory/Mongo lookup here: packet callbacks must never
        // block a server/region thread. The packet already carries UUID + last-known name.
        return null;
    }

    private boolean connectToServer(Player player, String server) {
        if (proxy == null || player == null || server == null || server.isBlank()) return false;
        try {
            for (String m : new String[]{"connect", "send", "sendToServer"}) {
                Method method = find(proxy.getClass(), m, Player.class, String.class);
                if (method != null) {
                    method.invoke(proxy, player, server);
                    return true;
                }
            }
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
        pendingForTarget.entrySet().removeIf(e -> e.getValue() == null
                || (e.getValue().expiresAt > 0 && e.getValue().expiresAt < now));
    }

    private void dbg(String msg) {
        try {
            if (plugin.getConfig().getBoolean("features.tpa.debug", plugin.getConfig().getBoolean("debug", false))) {
                plugin.getLogger().info("[TPA-X@" + localServer + "] " + msg);
            }
        } catch (Throwable ignored) {}
    }
}
