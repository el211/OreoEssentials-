package fr.elias.oreoEssentials.modules.homes;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.config.ConfigService;
import fr.elias.oreoEssentials.modules.homes.home.HomeService;
import fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel;
import fr.elias.oreoEssentials.rabbitmq.packet.PacketManager;
import fr.elias.oreoEssentials.modules.homes.rabbit.packet.HomeTeleportRequestPacket;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.*;
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
        if (!(sender instanceof Player p)) return true;

        if (args.length >= 1) {
            String raw = args[0];
            String key = normalize(raw);

            String targetServer = homes.homeServer(p.getUniqueId(), key);
            String localServer  = homes.localServer();
            if (targetServer == null) targetServer = localServer;

            if (targetServer.equalsIgnoreCase(localServer)) {
                Location loc = getHomeReflect(key, p);
                if (loc == null) {
                    Lang.send(p, "homes.not-found",
                            "<red>Home not found: <yellow>%name%</yellow></red>",
                            Map.of("name", raw));

                    var list = listNamesCrossServer(p);
                    if (!list.isEmpty()) {
                        String homes = String.join(", ", list);
                        Lang.send(p, "homes.available",
                                "<gray>Available:</gray> <aqua>%homes%</aqua>",
                                Map.of("homes", homes));
                    }
                    return true;
                }
                p.teleport(loc);
                Lang.send(p, "homes.teleported",
                        "<green>Teleported to home <aqua>%name%</aqua>.</green>",
                        Map.of("name", raw));
                return true;
            }

            var cs = OreoEssentials.get().getCrossServerSettings();
            if (!cs.homes()) {
                Lang.send(p, "homes.cross-disabled",
                        "<red>Cross-server homes are disabled by server config.</red>");
                Lang.send(p, "homes.cross-disabled-manual",
                        "<gray>Use <aqua>/server %server%</aqua> then run <aqua>/homes %name%</aqua>.</gray>",
                        Map.of("server", targetServer, "name", key));
                return true;
            }

            final OreoEssentials plugin = OreoEssentials.get();
            final PacketManager pm = plugin.getPacketManager();
            if (pm != null && pm.isInitialized()) {
                final String requestId = UUID.randomUUID().toString();
                HomeTeleportRequestPacket pkt = new HomeTeleportRequestPacket(p.getUniqueId(), key, targetServer, requestId);
                pm.sendPacket(PacketChannel.individual(targetServer), pkt);
            } else {
                Lang.send(p, "homes.messaging-disabled",
                        "<red>Cross-server messaging is disabled. Ask an admin to enable RabbitMQ.</red>");
                Lang.send(p, "homes.messaging-disabled-manual",
                        "<gray>You can still switch with <aqua>/server %server%</aqua> and run <aqua>/homes %name%</aqua>.</gray>",
                        Map.of("server", targetServer, "name", key));
                return true;
            }

            boolean switched = sendPlayerToServer(p, targetServer);
            if (switched) {
                Lang.send(p, "homes.sending",
                        "<yellow>Sending you to <aqua>%server%</aqua>... you'll be teleported to home <aqua>%name%</aqua> on arrival.</yellow>",
                        Map.of("server", targetServer, "name", key));
            } else {
                Lang.send(p, "homes.switch-failed",
                        "<red>Failed to switch you to %server%.</red>",
                        Map.of("server", targetServer));
            }
            return true;
        }

        List<String> list = listNamesCrossServer(p);
        int used = list.size();
        int max = maxHomes(p);

        if (used == 0) {
            Lang.send(p, "homes.no-homes",
                    "<yellow>You have no homes. Use <aqua>/sethome <n></aqua>.</yellow>");
            return true;
        }

        String cap = (max > 0) ? (used + "/" + max) : (used + "/?");
        String homesList = String.join(", ", list);

        Lang.send(p, "homes.list",
                "<gold>Homes (%count%):</gold> <aqua>%homes%</aqua>",
                Map.of("count", cap, "homes", homesList));
        Lang.send(p, "homes.tip",
                "<gray>Tip: <aqua>/homes <n></aqua> to teleport.</gray>");

        return true;
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    private Location getHomeReflect(String key, Player p) {
        UUID id = p.getUniqueId();

        Location loc = invokeLoc(homes, "getHome", new Class[]{UUID.class, String.class}, new Object[]{id, key});
        if (loc != null) return loc;

        loc = invokeLoc(homes, "home", new Class[]{UUID.class, String.class}, new Object[]{id, key});
        if (loc != null) return loc;

        Map<String, Location> map = getMapOfHomesReflect(p);
        if (map != null) {
            Location l = map.get(key);
            if (l == null) {
                for (Map.Entry<String, Location> e : map.entrySet()) {
                    if (normalize(e.getKey()).equals(key)) return e.getValue();
                }
            }
            return l;
        }

        return null;
    }

    private List<String> listNamesCrossServer(Player p) {
        try {
            Map<String, String> byServer = homes.homeServers(p.getUniqueId());
            if (byServer != null && !byServer.isEmpty()) {
                return byServer.keySet().stream()
                        .filter(n -> n != null && !n.isEmpty())
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .collect(Collectors.toList());
            }
        } catch (Throwable ignored) {
        }
        return listNamesReflect(p);
    }

    private List<String> listNamesReflect(Player p) {
        Map<String, Location> map = getMapOfHomesReflect(p);
        if (map != null) {
            return map.keySet().stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());
        }

        Collection<?> coll = getCollectionOfHomesReflect(p);
        if (coll != null) {
            return coll.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }

    private Map<String, Location> getMapOfHomesReflect(Player p) {
        UUID id = p.getUniqueId();

        Object o = invoke(homes, "getHomes", new Class[]{UUID.class}, new Object[]{id});
        if (o instanceof Map<?, ?> m) return castMapToLoc(m);

        o = invoke(homes, "homes", new Class[]{UUID.class}, new Object[]{id});
        if (o instanceof Map<?, ?> m2) return castMapToLoc(m2);

        o = invoke(homes, "listHomes", new Class[]{UUID.class}, new Object[]{id});
        if (o instanceof Map<?, ?> m3) return castMapToLoc(m3);

        return null;
    }

    private Collection<?> getCollectionOfHomesReflect(Player p) {
        UUID id = p.getUniqueId();

        Object o = invoke(homes, "getHomeNames", new Class[]{UUID.class}, new Object[]{id});
        if (o instanceof Collection<?> c) return c;

        o = invoke(homes, "homes", new Class[]{UUID.class}, new Object[]{id});
        if (o instanceof Collection<?> c2) return c2;

        o = invoke(homes, "listHomes", new Class[]{UUID.class}, new Object[]{id});
        if (o instanceof Collection<?> c3) return c3;

        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Location> castMapToLoc(Map<?, ?> m) {
        Map<String, Location> out = new HashMap<>();
        for (Map.Entry<?, ?> e : m.entrySet()) {
            Object k = e.getKey();
            Object v = e.getValue();
            if (k == null || v == null) continue;
            if (v instanceof Location loc) {
                out.put(k.toString(), loc);
            }
        }
        return out;
    }

    private int maxHomes(Player p) {
        try {
            if (cfg != null) {
                try {
                    Method m = cfg.getClass().getMethod("getMaxHomesFor", Player.class);
                    Object r = m.invoke(cfg, p);
                    if (r instanceof Number n) return n.intValue();
                } catch (NoSuchMethodException ignored) {
                }
                try {
                    Method m2 = cfg.getClass().getMethod("defaultMaxHomes");
                    Object r2 = m2.invoke(cfg);
                    if (r2 instanceof Number n2) return n2.intValue();
                } catch (NoSuchMethodException ignored) {
                }
            }
        } catch (Throwable ignored) {}
        return -1;
    }

    private static Object invoke(Object target, String method, Class<?>[] sig, Object[] args) {
        try {
            Method m = target.getClass().getMethod(method, sig);
            m.setAccessible(true);
            return m.invoke(target, args);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Location invokeLoc(Object target, String method, Class<?>[] sig, Object[] args) {
        Object o = invoke(target, method, sig, args);
        return (o instanceof Location) ? (Location) o : null;
    }

    private boolean sendPlayerToServer(Player p, String serverName) {
        try {
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            java.io.DataOutputStream out = new java.io.DataOutputStream(b);
            out.writeUTF("Connect");
            out.writeUTF(serverName);
            p.sendPluginMessage(OreoEssentials.get(), "BungeeCord", b.toByteArray());
            return true;
        } catch (Exception ex) {
            Bukkit.getLogger().warning("[OreoEssentials] Failed to send Connect plugin message: " + ex.getMessage());
            return false;
        }
    }
}