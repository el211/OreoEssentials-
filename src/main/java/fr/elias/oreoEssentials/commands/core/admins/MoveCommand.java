package fr.elias.oreoEssentials.commands.core.admins;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.playerdirectory.PlayerDirectory;
import fr.elias.oreoEssentials.modules.tp.service.TeleportService;
import fr.elias.oreoEssentials.modules.tp.rabbit.brokers.TpCrossServerBroker;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.UUID;

public class MoveCommand implements OreoCommand, org.bukkit.command.TabCompleter {

    private final TeleportService teleportService;

    public MoveCommand(TeleportService teleportService) {
        this.teleportService = teleportService;
    }

    @Override public String name() { return "move"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.move"; }
    @Override public String usage() { return "<player> [target]"; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player admin)) return true;

        if (args.length < 1) {
            admin.sendMessage("§cUsage: /" + label + " <player> [target]");
            return true;
        }

        // /move <player>  -> behave like /tp <player> (admin -> target)
        if (args.length == 1) {
            return moveSelfToTarget(admin, args[0].trim());
        }

        // /move <from> <to> -> move 'from' to 'to'
        String fromArg = args[0].trim();
        String toArg   = args[1].trim();

        if (fromArg.isEmpty() || toArg.isEmpty()) {
            admin.sendMessage("§cUsage: /" + label + " <player> [target]");
            return true;
        }

        OreoEssentials plugin = OreoEssentials.get();
        String localServer = plugin.getConfigService().serverName();
        PlayerDirectory dir = plugin.getPlayerDirectory();

        // 1) try both local
        Player fromLocal = resolveOnlineLocalOnly(fromArg);
        Player toLocal   = resolveOnlineLocalOnly(toArg);

        if (fromLocal != null && toLocal != null) {
            if (fromLocal.equals(toLocal)) {
                admin.sendMessage("§cYou cannot move a player to themselves.");
                return true;
            }

            teleportService.teleportSilently(fromLocal, toLocal);
            admin.sendMessage("§aMoved §b" + fromLocal.getName() + " §ato §b" + toLocal.getName() + "§a.");
            if (!admin.equals(fromLocal)) {
                fromLocal.sendMessage("§aYou have been moved to §b" + toLocal.getName() + "§a by §e" + admin.getName() + "§a.");
            }
            return true;
        }

        // At this point, we only support cross-server if the *source* is on this server.
        if (fromLocal == null) {
            admin.sendMessage("§cSource player must be online on this server to move them (for now).");
            return true;
        }

        // 2) destination local?
        if (toLocal != null) {
            teleportService.teleportSilently(fromLocal, toLocal);
            admin.sendMessage("§aMoved §b" + fromLocal.getName() + " §ato §b" + toLocal.getName() + "§a.");
            if (!admin.equals(fromLocal)) {
                fromLocal.sendMessage("§aYou have been moved to §b" + toLocal.getName() + "§a by §e" + admin.getName() + "§a.");
            }
            return true;
        }

        // 3) cross-server: fromLocal on this server, to is somewhere else on the network
        if (dir == null) {
            admin.sendMessage("§cPlayer not found online. §7(Cross-server directory is not available.)");
            return true;
        }

        UUID toUuid = null;
        try {
            toUuid = dir.lookupUuidByName(toArg);
        } catch (Throwable ignored) {}

        if (toUuid == null) {
            try {
                toUuid = UUID.fromString(toArg);
            } catch (Exception ignored) {}
        }

        if (toUuid == null) {
            admin.sendMessage("§cDestination player not found online.");
            return true;
        }

        String presence = null;
        try {
            presence = dir.getCurrentOrLastServer(toUuid);
        } catch (Throwable ignored) {}

        String targetName = safeNameLookup(dir, toUuid, toArg);

        // presence says same server → maybe race condition; try UUID-online
        if (presence != null && presence.equalsIgnoreCase(localServer)) {
            Player again = Bukkit.getPlayer(toUuid);
            if (again != null && again.isOnline()) {
                if (fromLocal.equals(again)) {
                    admin.sendMessage("§cYou cannot move a player to themselves.");
                    return true;
                }
                teleportService.teleportSilently(fromLocal, again);
                admin.sendMessage("§aMoved §b" + fromLocal.getName() + " §ato §b" + again.getName() + "§a.");
                if (!admin.equals(fromLocal)) {
                    fromLocal.sendMessage("§aYou have been moved to §b" + again.getName() + "§a by §e" + admin.getName() + "§a.");
                }
                return true;
            }
        }

        // presence says another server → use the same broker as /tp
        if (presence != null && !presence.isBlank() && !presence.equalsIgnoreCase(localServer)) {
            TpCrossServerBroker broker = plugin.getTpBroker();
            if (broker == null) {
                admin.sendMessage("§cCross-server teleport broker not available; cannot move players to other servers.");
                return true;
            }

            broker.requestCrossServerTp(fromLocal, toUuid, targetName, presence);
            admin.sendMessage("§aAsked proxy to move §b" + fromLocal.getName() + " §ato §b" + targetName + "§a on §b" + presence + "§a.");
            return true;
        }

        // presence unknown
        admin.sendMessage("§cDestination player not found online.");
        return true;
    }

    // ---------- helpers ----------

    private boolean moveSelfToTarget(Player admin, String arg) {
        if (arg == null || arg.isEmpty()) {
            admin.sendMessage("§cUsage: /move <player> [target]");
            return true;
        }

        OreoEssentials plugin = OreoEssentials.get();
        String localServer = plugin.getConfigService().serverName();

        // 1) local online
        Player localTarget = resolveOnline(arg);
        if (localTarget != null) {
            if (localTarget.equals(admin)) {
                admin.sendMessage("§cYou are already yourself.");
                return true;
            }
            teleportService.teleportSilently(admin, localTarget);
            admin.sendMessage("§aTeleported to §b" + localTarget.getName() + "§a.");
            return true;
        }

        // 2) cross-server via PlayerDirectory
        PlayerDirectory dir = plugin.getPlayerDirectory();
        if (dir == null) {
            admin.sendMessage("§cPlayer not found online. §7(Cross-server directory is not available.)");
            return true;
        }

        UUID targetUuid = null;
        try {
            targetUuid = dir.lookupUuidByName(arg);
        } catch (Throwable ignored) {}

        if (targetUuid == null) {
            try {
                targetUuid = UUID.fromString(arg);
            } catch (Exception ignored) {}
        }

        if (targetUuid == null) {
            admin.sendMessage("§cPlayer not found online.");
            return true;
        }

        String presence = null;
        try {
            presence = dir.getCurrentOrLastServer(targetUuid);
        } catch (Throwable ignored) {}

        String targetName = safeNameLookup(dir, targetUuid, arg);

        if (presence != null && presence.equalsIgnoreCase(localServer)) {
            Player again = Bukkit.getPlayer(targetUuid);
            if (again != null && again.isOnline()) {
                if (again.equals(admin)) {
                    admin.sendMessage("§cYou are already yourself.");
                    return true;
                }
                teleportService.teleportSilently(admin, again);
                admin.sendMessage("§aTeleported to §b" + again.getName() + "§a.");
                return true;
            }
        }

        if (presence != null && !presence.isBlank() && !presence.equalsIgnoreCase(localServer)) {
            TpCrossServerBroker tpBroker = plugin.getTpBroker();
            if (tpBroker == null) {
                admin.sendMessage("§cCross-server teleport broker not available; cannot /move to other servers.");
                return true;
            }

            tpBroker.requestCrossServerTp(admin, targetUuid, targetName, presence);
            return true;
        }

        admin.sendMessage("§cPlayer not found online.");
        return true;
    }

    private Player resolveOnline(String nameOrUuid) {
        Player p = Bukkit.getPlayerExact(nameOrUuid);
        if (p != null) return p;

        String want = nameOrUuid.toLowerCase(Locale.ROOT);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getName().toLowerCase(Locale.ROOT).equals(want)) return online;
        }
        try {
            UUID id = UUID.fromString(nameOrUuid);
            Player byId = Bukkit.getPlayer(id);
            if (byId != null) return byId;
        } catch (Exception ignored) {}
        return null;
    }

    private Player resolveOnlineLocalOnly(String nameOrUuid) {
        Player p = Bukkit.getPlayerExact(nameOrUuid);
        if (p != null) return p;

        String want = nameOrUuid.toLowerCase(Locale.ROOT);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getName().toLowerCase(Locale.ROOT).equals(want)) return online;
        }
        try {
            UUID id = UUID.fromString(nameOrUuid);
            return Bukkit.getPlayer(id);
        } catch (Exception ignored) {}
        return null;
    }

    private String safeNameLookup(PlayerDirectory dir, UUID uuid, String fallback) {
        try {
            String n = dir.lookupNameByUuid(uuid);
            return (n == null || n.isBlank()) ? fallback : n;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    // ---------- TAB COMPLETER ----------

    @Override
    public List<String> onTabComplete(CommandSender sender,
                                      Command cmd,
                                      String alias,
                                      String[] args) {
        if (!sender.hasPermission("oreo.move")) return List.of();

        // We complete both arg1 (<player>) and arg2 ([target]) with player names
        if (args.length == 1 || args.length == 2) {
            String partial = args[args.length - 1];
            String want = partial.toLowerCase(Locale.ROOT);

            Set<String> out = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

            // 1) Local online players
            for (Player p : Bukkit.getOnlinePlayers()) {
                String n = p.getName();
                if (n != null && n.toLowerCase(Locale.ROOT).startsWith(want)) {
                    out.add(n);
                }
            }

            // 2) Network-wide via PlayerDirectory.suggestOnlineNames()
            OreoEssentials plugin = OreoEssentials.get();
            PlayerDirectory dir = plugin.getPlayerDirectory();
            if (dir != null) {
                try {
                    var names = dir.suggestOnlineNames(want, 50);
                    if (names != null) {
                        for (String n : names) {
                            if (n != null && n.toLowerCase(Locale.ROOT).startsWith(want)) {
                                out.add(n);
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }

            return out.stream().limit(50).toList();
        }

        return List.of();
    }
}
