package fr.elias.oreoEssentials.modules.autoreboot;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.rabbitmq.PacketChannels;
import fr.elias.oreoEssentials.rabbitmq.packet.PacketManager;
import fr.elias.oreoEssentials.rabbitmq.packet.impl.tp.SafeZoneEnterPacket;
import fr.elias.oreoEssentials.util.Lang;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class AutoRebootService implements Listener {

    private final OreoEssentials plugin;
    private String localServer = "";
    private long lastSecondsLeft = Long.MAX_VALUE;

    private BukkitTask tickTask;

    private boolean enabled;
    private Mode mode;
    private ZoneId zoneId;

    private LocalTime rebootTime;
    private int intervalMinutes;

    private List<Integer> warnings;
    private boolean broadcast;
    private List<String> preCommands;

    private Action action;

    private String kickMessage;

    private boolean safeEnabled;
    private String safeServer;
    private String safeWorld;
    private String safeRegion;
    private String safeMessage;
    private int safeDelaySeconds;

    private Instant nextRebootAt;
    private final Set<Integer> warnedAlready = new HashSet<>();

    private volatile boolean crossHandlersRegistered = false;

    // If packet arrives while player offline / switching server: apply on join (like AfkPool)
    private final Map<UUID, PendingSafeTeleport> pendingSafeTeleports = new ConcurrentHashMap<>();

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private enum Mode { TIME, INTERVAL }
    private enum Action { KICK, SAFE_ZONE }

    private record PendingSafeTeleport(String worldName, String regionName, String message) {}

    public AutoRebootService(OreoEssentials plugin) {
        this.plugin = plugin;
    }

    public void start() {
        // register join listener only once
        try {
            Bukkit.getPluginManager().registerEvents(this, plugin);
        } catch (Throwable ignored) {}

        reload();
    }

    public void stop() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        warnedAlready.clear();
        nextRebootAt = null;
        pendingSafeTeleports.clear();
    }

    public void reload() {
        stop();

        var cs = plugin.getConfigService();
        this.localServer = (cs != null && cs.serverName() != null) ? cs.serverName() : "";

        this.enabled = plugin.getSettingsConfig().autoRebootEnabled();

        String modeStr = plugin.getSettingsConfig().autoRebootMode();
        this.mode = "INTERVAL".equalsIgnoreCase(modeStr) ? Mode.INTERVAL : Mode.TIME;

        this.zoneId = parseZoneId(plugin.getSettingsConfig().autoRebootTimezone());
        this.rebootTime = parseLocalTime(plugin.getSettingsConfig().autoRebootTime());
        this.intervalMinutes = Math.max(1, plugin.getSettingsConfig().autoRebootIntervalMinutes());

        List<Integer> rawWarnings = plugin.getSettingsConfig().autoRebootWarningsSeconds();
        if (rawWarnings == null) rawWarnings = List.of();

        this.warnings = rawWarnings.stream()
                .filter(Objects::nonNull)
                .map(i -> Math.max(0, i))
                .distinct()
                .sorted(Comparator.reverseOrder())
                .toList();

        this.broadcast = plugin.getSettingsConfig().autoRebootBroadcast();
        this.preCommands = plugin.getSettingsConfig().autoRebootPreCommands();
        if (this.preCommands == null) this.preCommands = List.of();

        String actionStr = plugin.getSettingsConfig().autoRebootAction();
        this.action = "SAFE_ZONE".equalsIgnoreCase(actionStr) ? Action.SAFE_ZONE : Action.KICK;

        this.kickMessage = plugin.getSettingsConfig().autoRebootKickMessage();

        this.safeEnabled = plugin.getSettingsConfig().autoRebootSafeZoneEnabled();
        this.safeServer = nullToEmpty(plugin.getSettingsConfig().autoRebootSafeZoneServer()).trim();
        this.safeWorld = nullToEmpty(plugin.getSettingsConfig().autoRebootSafeZoneWorld()).trim();
        this.safeRegion = nullToEmpty(plugin.getSettingsConfig().autoRebootSafeZoneRegion()).trim();
        this.safeMessage = plugin.getSettingsConfig().autoRebootSafeZoneMessage();
        this.safeDelaySeconds = Math.max(0, plugin.getSettingsConfig().autoRebootSafeZoneDelaySeconds());

        // hook cross-server receiver (packet) as soon as possible
        tryHookCrossServerNow();
        if (!crossHandlersRegistered) {
            Bukkit.getScheduler().runTaskLater(plugin, this::tryHookCrossServerNow, 40L);
            Bukkit.getScheduler().runTaskLater(plugin, this::tryHookCrossServerNow, 100L);
            Bukkit.getScheduler().runTaskLater(plugin, this::tryHookCrossServerNow, 200L);
        }
        plugin.getLogger().info("[AutoReboot/DEBUG] enabled=" + enabled
                + " mode=" + mode
                + " zone=" + zoneId
                + " time=" + rebootTime
                + " intervalMin=" + intervalMinutes
                + " warnings=" + warnings
                + " broadcast=" + broadcast
                + " action=" + action
                + " safeEnabled=" + safeEnabled
                + " safeServer=" + safeServer
                + " safeWorld=" + safeWorld
                + " safeRegion=" + safeRegion
                + " localServer=" + localServer
        );

        if (!enabled) return;

        computeNextReboot();
        scheduleTick();

        plugin.getLogger().info("[AutoReboot] Enabled mode=" + mode
                + " next=" + nextRebootAt
                + " zone=" + zoneId
                + " action=" + action
                + " local=" + localServer);
    }

    public void tryHookCrossServerNow() {
        if (crossHandlersRegistered) return;
        if (localServer == null || localServer.isBlank()) return;

        PacketManager pm = plugin.getPacketManager();
        if (pm == null) return;
        if (!pm.isInitialized()) return;

        try {
            pm.subscribeChannel(PacketChannels.GLOBAL);
        } catch (Throwable ignored) {}

        pm.subscribe(SafeZoneEnterPacket.class, (channel, pkt) -> {
            if (pkt == null) return;

            String target = pkt.getTargetServer();
            if (target == null || target.isEmpty()) return;
            if (!localServer.equalsIgnoreCase(target)) return;

            UUID playerId = pkt.getPlayerId();
            if (playerId == null) return;

            String worldName = pkt.getWorldName();
            String regionName = pkt.getRegionName();

            Bukkit.getScheduler().runTask(plugin, () -> {
                Player p = Bukkit.getPlayer(playerId);

                // if player not online yet (server switch), store & apply on join
                if (p == null || !p.isOnline()) {
                    pendingSafeTeleports.put(playerId, new PendingSafeTeleport(worldName, regionName, safeMessage));
                    return;
                }

                teleportPlayerToSafeRegion(p, worldName, regionName, safeMessage);
            });
        });

        crossHandlersRegistered = true;
        plugin.getLogger().info("[AutoReboot] Cross-server SAFE_ZONE handler hooked on server=" + localServer);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!enabled) return;

        Player p = event.getPlayer();
        if (p == null) return;

        PendingSafeTeleport pending = pendingSafeTeleports.remove(p.getUniqueId());
        if (pending == null) return;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            teleportPlayerToSafeRegion(p, pending.worldName(), pending.regionName(), pending.message());
        }, 20L);
    }

    private void scheduleTick() {
        lastSecondsLeft = Long.MAX_VALUE;

        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!enabled) return;
            if (nextRebootAt == null) return;

            long secondsLeft = Duration.between(Instant.now(), nextRebootAt).getSeconds();
            if (secondsLeft < 0) secondsLeft = 0;

            for (int w : warnings) {
                if (!warnedAlready.contains(w) && lastSecondsLeft > w && secondsLeft <= w) {
                    warnedAlready.add(w);
                    announce(w);
                }
            }

            if (lastSecondsLeft > 0 && secondsLeft <= 0) {
                performRestart();
            }

            lastSecondsLeft = secondsLeft;
        }, 20L, 20L);
    }


    private void announce(long secondsLeft) {
        if (!broadcast) return;

        String msg;
        if (secondsLeft >= 60) {
            long m = secondsLeft / 60;
            msg = "<gold>Server restarting in <yellow>" + m + " minute(s)</yellow>...</gold>";
        } else {
            msg = "<gold>Server restarting in <yellow>" + secondsLeft + "s</yellow>...</gold>";
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            Lang.send(p, "auto-reboot.warning", msg, Map.of());
        }
        plugin.getLogger().info("[AutoReboot] Warning: " + secondsLeft + "s");
    }

    private void performRestart() {
        stop();

        plugin.getLogger().info("[AutoReboot] Restarting now... action=" + action);

        for (String cmd : preCommands) {
            if (cmd == null || cmd.isBlank()) continue;
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }

        if (action == Action.SAFE_ZONE && safeEnabled) {
            moveEveryoneToSafeZone();
        } else {
            kickEveryone();
        }

        int delayTicks = Math.max(1, (action == Action.SAFE_ZONE ? safeDelaySeconds : 1) * 20);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (tryPaperRestart()) return;
            Bukkit.shutdown();
        }, delayTicks);
    }

    private void moveEveryoneToSafeZone() {
        final String msg = safeMessage == null ? "" : safeMessage;

        final boolean hasRegion = !safeWorld.isEmpty() && !safeRegion.isEmpty();

        if (!safeServer.isEmpty() && !localServer.equalsIgnoreCase(safeServer)) {
            PacketManager pm = plugin.getPacketManager();
            boolean canPacket = pm != null && pm.isInitialized();

            plugin.getLogger().info("[AutoReboot] SAFE_ZONE cross-server local=" + localServer
                    + " target=" + safeServer
                    + " hasRegion=" + hasRegion
                    + " pm=" + (pm != null)
                    + " init=" + canPacket);

            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.isOnline()) continue;

                if (hasRegion && canPacket) {
                    // PacketManager.sendPacket(Packet) -> GLOBAL (matches subscribeChannel GLOBAL)
                    pm.sendPacket(new SafeZoneEnterPacket(p.getUniqueId(), safeServer, safeWorld, safeRegion));
                }

                plugin.getProxyMessenger().sendToServer(p, safeServer);

                if (!msg.isEmpty()) {
                    Lang.send(p, "auto-reboot.safe-zone", msg, Map.of());
                }
            }

            plugin.getLogger().info("[AutoReboot] SAFE_ZONE: sent players to server=" + safeServer
                    + (hasRegion ? (" + region " + safeWorld + "/" + safeRegion) : " (no region)"));
            return;
        }

        if (hasRegion) {
            Location loc = getRegionCenter(safeWorld, safeRegion);
            if (loc == null) {
                plugin.getLogger().warning("[AutoReboot] SAFE_ZONE: region not found world=" + safeWorld + " region=" + safeRegion + " -> kicking instead");
                kickEveryone();
                return;
            }

            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.isOnline()) continue;
                p.teleport(loc);
                if (!msg.isEmpty()) {
                    Lang.send(p, "auto-reboot.safe-zone", msg, Map.of());
                }
            }

            plugin.getLogger().info("[AutoReboot] SAFE_ZONE: teleported players to region " + safeWorld + "/" + safeRegion);
            return;
        }

        plugin.getLogger().warning("[AutoReboot] SAFE_ZONE enabled but no destination configured -> kicking instead");
        kickEveryone();
    }

    private void teleportPlayerToSafeRegion(Player p, String worldName, String regionName, String msg) {
        if (p == null || !p.isOnline()) return;

        Location loc = getRegionCenter(worldName, regionName);
        if (loc == null) {
            plugin.getLogger().warning("[AutoReboot] SafeZone: region not found world=" + worldName + " region=" + regionName + " player=" + p.getName());
            return;
        }

        p.teleport(loc);

        if (msg != null && !msg.isBlank()) {
            Lang.send(p, "auto-reboot.safe-zone", msg, Map.of());
        }
    }

    private void kickEveryone() {
        final String kick = kickMessage == null ? "" : kickMessage;

        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                p.kick(MM.deserialize(kick));
            } catch (Throwable t) {
                try {
                    p.kickPlayer(stripMiniLike(kick));
                } catch (Throwable ignored) {}
            }
        }
    }

    private boolean tryPaperRestart() {
        try {
            java.lang.reflect.Method m = Bukkit.getServer().getClass().getMethod("restart");
            m.invoke(Bukkit.getServer());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private Location getRegionCenter(String worldName, String regionName) {
        if (worldName == null || worldName.isBlank()) return null;
        if (regionName == null || regionName.isBlank()) return null;

        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;

        try {
            RegionManager regionManager = WorldGuard.getInstance()
                    .getPlatform()
                    .getRegionContainer()
                    .get(BukkitAdapter.adapt(world));

            if (regionManager == null) return null;

            ProtectedRegion region = regionManager.getRegion(regionName);
            if (region == null) return null;

            var min = region.getMinimumPoint();
            var max = region.getMaximumPoint();

            double x = (min.x() + max.x()) / 2.0 + 0.5;
            double y = min.y() + 1;
            double z = (min.z() + max.z()) / 2.0 + 0.5;

            return new Location(world, x, y, z);
        } catch (Throwable t) {
            plugin.getLogger().severe("[AutoReboot] SafeZone region error: " + t.getMessage());
            return null;
        }
    }

    private void computeNextReboot() {
        warnedAlready.clear();

        Instant now = Instant.now();

        if (mode == Mode.INTERVAL) {
            nextRebootAt = now.plus(Duration.ofMinutes(intervalMinutes));
            return;
        }

        ZonedDateTime zonedNow = ZonedDateTime.now(zoneId);
        ZonedDateTime target = zonedNow
                .withHour(rebootTime.getHour())
                .withMinute(rebootTime.getMinute())
                .withSecond(0)
                .withNano(0);

        if (!target.isAfter(zonedNow)) {
            target = target.plusDays(1);
        }
        nextRebootAt = target.toInstant();
    }

    private ZoneId parseZoneId(String tz) {
        if (tz == null || tz.isBlank()) return ZoneId.of("Europe/Paris");

        String s = tz.trim();
        String upper = s.toUpperCase(Locale.ROOT);

        if (upper.startsWith("GMT") || upper.startsWith("UTC")) {
            String off = upper.replace("GMT", "").replace("UTC", "").trim();
            if (off.isEmpty()) off = "+0";
            try {
                ZoneOffset zo = ZoneOffset.of(off);
                return ZoneId.ofOffset("UTC", zo);
            } catch (Throwable ignored) {}
        }

        try {
            return ZoneId.of(s);
        } catch (Throwable ignored) {
            return ZoneId.of("Europe/Paris");
        }
    }

    private LocalTime parseLocalTime(String timeStr) {
        if (timeStr == null || timeStr.isBlank()) return LocalTime.MIDNIGHT;
        try {
            return LocalTime.parse(timeStr.trim(), TIME_FMT);
        } catch (DateTimeParseException ignored) {
            return LocalTime.MIDNIGHT;
        }
    }

    private String stripMiniLike(String s) {
        if (s == null) return "";
        return s.replaceAll("(?i)<[^>]+>", "");
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
