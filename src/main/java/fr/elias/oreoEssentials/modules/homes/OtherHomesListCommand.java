// File: src/main/java/fr/elias/oreoEssentials/commands/core/admins/OtherHomesListCommand.java
package fr.elias.oreoEssentials.modules.homes;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.homes.home.HomeService;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

public class OtherHomesListCommand implements CommandExecutor, TabCompleter {

    private final OreoEssentials plugin;
    private final HomeService homeService;

    public OtherHomesListCommand(OreoEssentials plugin, HomeService homeService) {
        this.plugin = plugin;
        this.homeService = homeService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("oreo.homes.other")) {
            Lang.send(sender, "admin.otherhomes.no-permission",
                    "<red>You don't have permission.</red>");
            return true;
        }

        if (args.length < 1) {
            Lang.send(sender, "admin.otherhomes.usage",
                    "<yellow>Usage: <gold>/%label% <player></gold></yellow>",
                    Map.of("label", label));
            return true;
        }

        // Resolve the RIGHT UUID for the target name
        final String inputName = args[0];
        final UUID owner = resolveTargetUUID(inputName);
        if (owner == null) {
            Lang.send(sender, "admin.otherhomes.player-not-found",
                    "<red>Unknown player: <yellow>%player%</yellow></red>",
                    Map.of("player", inputName));
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(owner);
        String ownerName = target.getName() != null ? target.getName() : owner.toString();

        Map<String, HomeInfo> homes = fetchHomesReflectively(owner);
        if (homes.isEmpty()) {
            Lang.send(sender, "admin.otherhomes.no-homes",
                    "<gray>%player% has no homes.</gray>",
                    Map.of("player", ownerName));
            return true;
        }

        Lang.send(sender, "admin.otherhomes.header",
                "<gold>Homes of <yellow>%player%</yellow>:</gold>",
                Map.of("player", ownerName));

        for (var e : homes.entrySet()) {
            HomeInfo h = e.getValue();
            Lang.send(sender, "admin.otherhomes.entry",
                    " <dark_gray>-</dark_gray> <aqua>%name%</aqua> <gray>@ <white>%world%</white> (%.1f, %.1f, %.1f) <dark_gray>[server: %server%]</dark_gray></gray>",
                    Map.of(
                            "name", e.getKey(),
                            "world", h.world,
                            "x", String.format("%.1f", h.x),
                            "y", String.format("%.1f", h.y),
                            "z", String.format("%.1f", h.z),
                            "server", h.server
                    ));
        }

        Lang.send(sender, "admin.otherhomes.tip",
                "<gray>Tip: <yellow>/otherhome %player% <home></yellow></gray>",
                Map.of("player", ownerName));

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!sender.hasPermission("oreo.homes.other")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            String p = args[0].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(p))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }


    private UUID resolveTargetUUID(String inputName) {
        if (inputName == null || inputName.isEmpty()) return null;

        // 1) exact online
        Player online = Bukkit.getPlayerExact(inputName);
        if (online != null) return online.getUniqueId();

        // 2) try PlayerDirectory if Oreo has it
        try {
            Object dir = plugin.getPlayerDirectory();
            if (dir != null) {
                try {
                    Method m = dir.getClass().getMethod("lookupUuidByName", String.class);
                    Object res = m.invoke(dir, inputName);
                    if (res instanceof UUID) return (UUID) res;
                } catch (NoSuchMethodException ignored) {
                    // directory present but doesn't expose helper; fall through
                } catch (Throwable t) {
                    plugin.getLogger().warning("[OtherHomesList] PlayerDirectory lookup failed: " + t.getMessage());
                }
            }
        } catch (Throwable ignored) {
        }

        try {
            return Bukkit.getOfflinePlayer(inputName).getUniqueId();
        } catch (Throwable t) {
            return null;
        }
    }


    @SuppressWarnings("unchecked")
    private Map<String, HomeInfo> fetchHomesReflectively(UUID owner) {
        if (owner == null) return Collections.emptyMap();

        // Try listHomes(UUID)
        try {
            Method m = HomeService.class.getMethod("listHomes", UUID.class);
            Object result = m.invoke(homeService, owner);
            return convertResultToMap(result);
        } catch (NoSuchMethodException ignored) {
            // Try getHomes(UUID)
            try {
                Method m = HomeService.class.getMethod("getHomes", UUID.class);
                Object result = m.invoke(homeService, owner);
                return convertResultToMap(result);
            } catch (NoSuchMethodException e2) {
                plugin.getLogger().warning("[OtherHomesList] HomeService has neither listHomes(UUID) nor getHomes(UUID).");
            } catch (Throwable t) {
                plugin.getLogger().warning("[OtherHomesList] getHomes(UUID) failed: " + t.getMessage());
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[OtherHomesList] listHomes(UUID) failed: " + t.getMessage());
        }
        return Collections.emptyMap();
    }

    private Map<String, HomeInfo> convertResultToMap(Object result) {
        if (!(result instanceof Map<?, ?> map)) return Collections.emptyMap();

        Map<String, HomeInfo> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String name = String.valueOf(entry.getKey()).toLowerCase(Locale.ROOT);
            HomeInfo hi = readDto(entry.getValue(), name);
            if (hi != null) out.put(name, hi);
        }
        return out;
    }

    private HomeInfo readDto(Object dto, String name) {
        if (dto == null) return null;
        try {
            // Expected getters: getWorld(), getX(), getY(), getZ(), optional getServer()
            String world = invokeString(dto, "getWorld", "world");
            double x = invokeDouble(dto, "getX", 0.0);
            double y = invokeDouble(dto, "getY", 0.0);
            double z = invokeDouble(dto, "getZ", 0.0);
            String server = tryInvokeString(dto, "getServer");
            if (server == null || server.isBlank()) {
                server = Bukkit.getServer().getName();
            }
            return new HomeInfo(name, world, x, y, z, server);
        } catch (Throwable t) {
            plugin.getLogger().warning("[OtherHomesList] DTO read failed for '" + name + "': " + t.getMessage());
            return null;
        }
    }

    private String tryInvokeString(Object o, String method) {
        try {
            return invokeString(o, method, null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String invokeString(Object o, String method, String def) throws Exception {
        Object v = o.getClass().getMethod(method).invoke(o);
        return v != null ? v.toString() : def;
    }

    private double invokeDouble(Object o, String method, double def) throws Exception {
        Object v = o.getClass().getMethod(method).invoke(o);
        return (v instanceof Number n) ? n.doubleValue() : def;
    }


    private static final class HomeInfo {
        final String name;
        final String world;
        final double x, y, z;
        final String server;

        HomeInfo(String name, String world, double x, double y, double z, String server) {
            this.name = name;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.server = server;
        }
    }
}