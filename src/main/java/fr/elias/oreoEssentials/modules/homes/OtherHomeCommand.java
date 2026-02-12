// File: src/main/java/fr/elias/oreoEssentials/commands/core/admins/OtherHomeCommand.java
package fr.elias.oreoEssentials.modules.homes;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel;
import fr.elias.oreoEssentials.rabbitmq.packet.PacketManager;
import fr.elias.oreoEssentials.modules.homes.rabbit.packet.OtherHomeTeleportRequestPacket;
import fr.elias.oreoEssentials.modules.homes.home.HomeService;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

public class OtherHomeCommand implements OreoCommand, org.bukkit.command.TabCompleter {

    private final OreoEssentials plugin;
    private final HomeService homes;

    public OtherHomeCommand(OreoEssentials plugin, HomeService homes) {
        this.plugin = plugin;
        this.homes = homes;
    }

    @Override public String name() { return "otherhome"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.homes.other"; }
    @Override public String usage() { return "<player> <home>"; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player admin)) return true;

        if (!sender.hasPermission("oreo.homes.other")) {
            Lang.send(sender, "admin.otherhome.no-permission",
                    "<red>You don't have permission.</red>");
            return true;
        }

        if (args.length < 2) {
            Lang.send(sender, "admin.otherhome.usage",
                    "<yellow>Usage: <gold>/%label% <player> <home></gold></yellow>",
                    Map.of("label", label));
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (target == null || (target.getName() == null && !target.hasPlayedBefore())) {
            Lang.send(sender, "admin.otherhome.player-not-found",
                    "<red>Unknown player: <yellow>%player%</yellow></red>",
                    Map.of("player", args[0]));
            return true;
        }

        final UUID ownerId = target.getUniqueId();
        final String homeName = normalize(args[1]);

        // 1) Find which server owns <owner>/<home>
        String targetServer = resolveHomeServer(ownerId, homeName);
        if (targetServer == null) targetServer = homes.localServer();
        final String localServer = homes.localServer();

        // 2) If it's local, teleport directly
        if (targetServer.equalsIgnoreCase(localServer)) {
            Location loc = homes.getHome(ownerId, homeName);
            if (loc == null) {
                Lang.send(sender, "admin.otherhome.not-found",
                        "<red>Home not found: <yellow>%home%</yellow></red>",
                        Map.of("home", args[1]));
                return true;
            }

            World w = loc.getWorld();
            if (w == null) {
                Lang.send(sender, "admin.otherhome.world-not-loaded",
                        "<red>World not loaded: <yellow>%world%</yellow></red>",
                        Map.of("world", loc.getWorld() != null ? loc.getWorld().getName() : "null"));
                return true;
            }

            admin.teleport(loc);

            String ownerName = target.getName() != null ? target.getName() : ownerId.toString();
            Lang.send(sender, "admin.otherhome.teleported",
                    "<green>Teleported to <aqua>%owner%</aqua>'s home <aqua>%home%</aqua>.</green>",
                    Map.of("owner", ownerName, "home", homeName));
            return true;
        }

        // 3) Cross-server disabled?
        var cs = plugin.getCrossServerSettings();
        if (!cs.homes()) {
            String ownerName = target.getName() != null ? target.getName() : ownerId.toString();
            Lang.send(sender, "admin.otherhome.cross-disabled",
                    "<red>Cross-server homes are disabled by server config.</red>");
            Lang.send(sender, "admin.otherhome.cross-disabled-manual",
                    "<gray>Use <aqua>/server %server%</aqua> then run <aqua>/otherhome %owner% %home%</aqua></gray>",
                    Map.of("server", targetServer, "owner", ownerName, "home", homeName));
            return true;
        }

        // 4) Publish to the target server's INDIVIDUAL channel
        PacketManager pm = plugin.getPacketManager();
        if (pm != null && pm.isInitialized()) {
            final String requestId = java.util.UUID.randomUUID().toString();

            OtherHomeTeleportRequestPacket pkt = new OtherHomeTeleportRequestPacket(
                    admin.getUniqueId(), ownerId, homeName, targetServer, requestId
            );

            PacketChannel channel = PacketChannel.individual(targetServer);
            pm.sendPacket(channel, pkt);

            plugin.getLogger().info("[HOME/SEND-OTHER] from=" + localServer
                    + " subject=" + admin.getUniqueId()
                    + " owner=" + ownerId
                    + " home=" + homeName
                    + " -> target=" + targetServer
                    + " requestId=" + requestId);
        } else {
            String ownerName = target.getName() != null ? target.getName() : ownerId.toString();
            Lang.send(sender, "admin.otherhome.messaging-disabled",
                    "<red>Cross-server messaging is disabled. Ask an admin to enable RabbitMQ.</red>");
            Lang.send(sender, "admin.otherhome.messaging-disabled-manual",
                    "<gray>You can still switch with <aqua>/server %server%</aqua> and run <aqua>/otherhome %owner% %home%</aqua></gray>",
                    Map.of("server", targetServer, "owner", ownerName, "home", homeName));
            return true;
        }

        // 5) Switch the admin to the target server
        String ownerName = target.getName() != null ? target.getName() : ownerId.toString();

        if (sendPlayerToServer(admin, targetServer)) {
            Lang.send(sender, "admin.otherhome.sending",
                    "<yellow>Sending you to <aqua>%server%</aqua>… you'll be teleported to <aqua>%owner%</aqua>'s home <aqua>%home%</aqua> on arrival.</yellow>",
                    Map.of("server", targetServer, "owner", ownerName, "home", homeName));
        } else {
            Lang.send(sender, "admin.otherhome.switch-failed",
                    "<red>Failed to switch you to %server%.</red>",
                    Map.of("server", targetServer));
            Lang.send(sender, "admin.otherhome.switch-failed-tip",
                    "<gray>Make sure that server name matches your proxy config.</gray>");
        }

        return true;
    }

    /* ---------- Tab Completion ---------- */

    @Override
    public List<String> onTabComplete(CommandSender sender, org.bukkit.command.Command cmd, String alias, String[] args) {
        if (!(sender instanceof Player)) return List.of();
        if (!sender.hasPermission("oreo.homes.other")) return List.of();

        if (args.length == 1) {
            String p = args[0].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(p))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            if (target == null) return List.of();
            return safeHomesOf(target.getUniqueId()).stream()
                    .filter(h -> h.startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());
        }

        return List.of();
    }

    /* ---------- Helpers ---------- */

    private Set<String> safeHomesOf(UUID owner) {
        try {
            Set<String> s = homes.homes(owner);
            return (s == null) ? Collections.emptySet() : s;
        } catch (Throwable t) {
            return Collections.emptySet();
        }
    }

    private String resolveHomeServer(UUID ownerId, String homeName) {
        // Prefer dedicated method if your HomeService has it
        try {
            Method m = homes.getClass().getMethod("homeServer", UUID.class, String.class);
            Object v = m.invoke(homes, ownerId, homeName);
            if (v != null) return v.toString();
        } catch (NoSuchMethodException ignored) {
            // Fall back to listHomes/getHomes + getServer()
            try {
                Method m = homes.getClass().getMethod("listHomes", UUID.class);
                Object res = m.invoke(homes, ownerId);
                String srv = extractServerFromMap(res, homeName);
                if (srv != null) return srv;
            } catch (NoSuchMethodException ignored2) {
                try {
                    Method m = homes.getClass().getMethod("getHomes", UUID.class);
                    Object res = m.invoke(homes, ownerId);
                    String srv = extractServerFromMap(res, homeName);
                    if (srv != null) return srv;
                } catch (Throwable ignored3) {}
            } catch (Throwable ignored4) {}
        } catch (Throwable ignored) {}

        return null; // unknown -> let caller treat as local
    }

    @SuppressWarnings("unchecked")
    private String extractServerFromMap(Object mapObj, String homeName) {
        if (!(mapObj instanceof Map<?, ?> map)) return null;
        Object dto = map.get(homeName.toLowerCase(Locale.ROOT));
        if (dto == null) return null;
        try {
            Method gs = dto.getClass().getMethod("getServer");
            Object v = gs.invoke(dto);
            return v == null ? null : v.toString();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    private boolean sendPlayerToServer(Player p, String serverName) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(b);
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