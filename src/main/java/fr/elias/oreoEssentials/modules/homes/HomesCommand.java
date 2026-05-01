package fr.elias.oreoEssentials.modules.homes;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.config.ConfigService;
import fr.elias.oreoEssentials.modules.homes.home.HomeService;
import fr.elias.oreoEssentials.modules.homes.rabbit.packet.HomeTeleportRequestPacket;
import fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel;
import fr.elias.oreoEssentials.rabbitmq.packet.PacketManager;
import fr.elias.oreoEssentials.util.Async;
import fr.elias.oreoEssentials.util.Lang;
import fr.elias.oreoEssentials.util.OreScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

public class HomesCommand implements OreoCommand {

    private final HomeService homes;
    private final ConfigService cfg;

    public HomesCommand(HomeService homes) {
        this.homes = homes;
        this.cfg = OreoEssentials.get().getConfigService();
    }

    @Override public String name() { return "homes"; }
    @Override public List<String> aliases() { return List.of("listhomes"); }
    @Override public String permission() { return "oreo.homes"; }
    @Override public String usage() { return "[name]"; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;

        if (args.length >= 1) {
            handleTeleport(player, args[0]);
            return true;
        }

        List<String> list = listNamesCrossServer(player.getUniqueId());
        int used = list.size();
        int max = maxHomes(player);

        if (used == 0) {
            Lang.send(player, "homes.no-homes",
                    "<yellow>You have no homes. Use <aqua>/sethome <n></aqua>.</yellow>");
            return true;
        }

        String cap = (max > 0) ? (used + "/" + max) : (used + "/?");
        String homesList = String.join(", ", list);

        Lang.send(player, "homes.list",
                "<gold>Homes (%count%):</gold> <aqua>%homes%</aqua>",
                Map.of("count", cap, "homes", homesList));
        Lang.send(player, "homes.tip",
                "<gray>Tip: <aqua>/homes <n></aqua> to teleport.</gray>");

        return true;
    }

    private void handleTeleport(Player player, String raw) {
        final OreoEssentials plugin = OreoEssentials.get();
        final UUID playerId = player.getUniqueId();
        final String key = normalize(raw);
        final String localServer = homes.localServer();

        Async.run(() -> {
            String resolvedTargetServer = homes.homeServer(playerId, key);
            if (resolvedTargetServer == null) resolvedTargetServer = localServer;

            final String targetServer = resolvedTargetServer;
            final Location localHome = targetServer.equalsIgnoreCase(localServer)
                    ? homes.getHome(playerId, key)
                    : null;
            final List<String> availableHomes = (targetServer.equalsIgnoreCase(localServer) && localHome == null)
                    ? listNamesCrossServer(playerId)
                    : List.of();

            OreScheduler.runForEntity(plugin, player, () -> {
                if (!player.isOnline()) return;

                if (targetServer.equalsIgnoreCase(localServer)) {
                    if (localHome == null) {
                        Lang.send(player, "homes.not-found",
                                "<red>Home not found: <yellow>%name%</yellow></red>",
                                Map.of("name", raw));

                        if (!availableHomes.isEmpty()) {
                            Lang.send(player, "homes.available",
                                    "<gray>Available:</gray> <aqua>%homes%</aqua>",
                                    Map.of("homes", String.join(", ", availableHomes)));
                        }
                        return;
                    }

                    if (localHome.getWorld() == null) {
                        Lang.send(player, "homes.world-not-loaded",
                                "<red>Home <yellow>%name%</yellow> points to a world that is not loaded.</red>",
                                Map.of("name", raw));
                        return;
                    }

                    if (OreScheduler.isFolia()) {
                        player.teleportAsync(localHome).thenRun(() ->
                                Lang.send(player, "homes.teleported",
                                        "<green>Teleported to home <aqua>%name%</aqua>.</green>",
                                        Map.of("name", raw)));
                    } else {
                        player.teleport(localHome);
                        Lang.send(player, "homes.teleported",
                                "<green>Teleported to home <aqua>%name%</aqua>.</green>",
                                Map.of("name", raw));
                    }
                    return;
                }

                var cs = plugin.getCrossServerSettings();
                if (!cs.homes()) {
                    Lang.send(player, "homes.cross-disabled",
                            "<red>Cross-server homes are disabled by server config.</red>");
                    Lang.send(player, "homes.cross-disabled-manual",
                            "<gray>Use <aqua>/server %server%</aqua> then run <aqua>/homes %name%</aqua>.</gray>",
                            Map.of("server", targetServer, "name", key));
                    return;
                }

                PacketManager pm = plugin.getPacketManager();
                if (pm != null && pm.isInitialized()) {
                    String requestId = UUID.randomUUID().toString();
                    HomeTeleportRequestPacket packet = new HomeTeleportRequestPacket(playerId, key, targetServer, requestId);
                    pm.sendPacket(PacketChannel.individual(targetServer), packet);
                } else {
                    Lang.send(player, "homes.messaging-disabled",
                            "<red>Cross-server messaging is disabled. Ask an admin to enable RabbitMQ.</red>");
                    Lang.send(player, "homes.messaging-disabled-manual",
                            "<gray>You can still switch with <aqua>/server %server%</aqua> and run <aqua>/homes %name%</aqua>.</gray>",
                            Map.of("server", targetServer, "name", key));
                    return;
                }

                if (sendPlayerToServer(player, targetServer)) {
                    Lang.send(player, "homes.sending",
                            "<yellow>Sending you to <aqua>%server%</aqua>... you'll be teleported to home <aqua>%name%</aqua> on arrival.</yellow>",
                            Map.of("server", targetServer, "name", key));
                } else {
                    Lang.send(player, "homes.switch-failed",
                            "<red>Failed to switch you to %server%.</red>",
                            Map.of("server", targetServer));
                }
            });
        });
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    private List<String> listNamesCrossServer(UUID playerId) {
        try {
            Map<String, String> byServer = homes.homeServers(playerId);
            if (byServer != null && !byServer.isEmpty()) {
                return byServer.keySet().stream()
                        .filter(name -> name != null && !name.isEmpty())
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .collect(Collectors.toList());
            }
        } catch (Throwable ignored) {
        }
        return listNamesReflect(playerId);
    }

    private List<String> listNamesReflect(UUID playerId) {
        Map<String, Location> map = getMapOfHomesReflect(playerId);
        if (map != null) {
            return map.keySet().stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());
        }

        Collection<?> collection = getCollectionOfHomesReflect(playerId);
        if (collection != null) {
            return collection.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }

    private Map<String, Location> getMapOfHomesReflect(UUID playerId) {
        Object value = invoke(homes, "getHomes", new Class[]{UUID.class}, new Object[]{playerId});
        if (value instanceof Map<?, ?> map) return castMapToLoc(map);

        value = invoke(homes, "homes", new Class[]{UUID.class}, new Object[]{playerId});
        if (value instanceof Map<?, ?> map) return castMapToLoc(map);

        value = invoke(homes, "listHomes", new Class[]{UUID.class}, new Object[]{playerId});
        if (value instanceof Map<?, ?> map) return castMapToLoc(map);

        return null;
    }

    private Collection<?> getCollectionOfHomesReflect(UUID playerId) {
        Object value = invoke(homes, "getHomeNames", new Class[]{UUID.class}, new Object[]{playerId});
        if (value instanceof Collection<?> collection) return collection;

        value = invoke(homes, "homes", new Class[]{UUID.class}, new Object[]{playerId});
        if (value instanceof Collection<?> collection) return collection;

        value = invoke(homes, "listHomes", new Class[]{UUID.class}, new Object[]{playerId});
        if (value instanceof Collection<?> collection) return collection;

        return null;
    }

    private Map<String, Location> castMapToLoc(Map<?, ?> map) {
        Map<String, Location> out = new HashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object key = entry.getKey();
            Object value = entry.getValue();
            if (key == null || !(value instanceof Location location)) continue;
            out.put(key.toString(), location);
        }
        return out;
    }

    private int maxHomes(Player player) {
        try {
            if (cfg != null) {
                try {
                    Method method = cfg.getClass().getMethod("getMaxHomesFor", Player.class);
                    Object result = method.invoke(cfg, player);
                    if (result instanceof Number number) return number.intValue();
                } catch (NoSuchMethodException ignored) {
                }
                try {
                    Method method = cfg.getClass().getMethod("defaultMaxHomes");
                    Object result = method.invoke(cfg);
                    if (result instanceof Number number) return number.intValue();
                } catch (NoSuchMethodException ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return -1;
    }

    private static Object invoke(Object target, String method, Class<?>[] signature, Object[] args) {
        try {
            Method reflected = target.getClass().getMethod(method, signature);
            reflected.setAccessible(true);
            return reflected.invoke(target, args);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private boolean sendPlayerToServer(Player player, String serverName) {
        try {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            java.io.DataOutputStream out = new java.io.DataOutputStream(bytes);
            out.writeUTF("Connect");
            out.writeUTF(serverName);
            player.sendPluginMessage(OreoEssentials.get(), "BungeeCord", bytes.toByteArray());
            return true;
        } catch (Exception ex) {
            Bukkit.getLogger().warning("[OreoEssentials] Failed to send Connect plugin message: " + ex.getMessage());
            return false;
        }
    }
}
