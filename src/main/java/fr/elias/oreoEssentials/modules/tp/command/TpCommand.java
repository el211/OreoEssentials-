// File: src/main/java/fr/elias/oreoEssentials/commands/core/admins/TpCommand.java
package fr.elias.oreoEssentials.modules.tp.command;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.playerdirectory.PlayerDirectory;
import fr.elias.oreoEssentials.modules.tp.service.TeleportService;
import fr.elias.oreoEssentials.modules.tp.rabbit.brokers.TpCrossServerBroker;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class TpCommand implements OreoCommand {

    private final TeleportService teleportService;

    public TpCommand(TeleportService teleportService) {
        this.teleportService = teleportService;
    }

    @Override public String name() { return "tp"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.tp"; }
    @Override public String usage() { return "<player>"; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player admin)) return true;

        if (args.length < 1) {
            Lang.send(admin, "admin.tp.usage",
                    "<red>Usage: /tp <player></red>");
            return true;
        }

        String arg = args[0].trim();
        if (arg.isEmpty()) {
            Lang.send(admin, "admin.tp.usage",
                    "<red>Usage: /tp <player></red>");
            return true;
        }

        OreoEssentials plugin = OreoEssentials.get();
        String localServer = plugin.getConfigService().serverName();

        // 1) Try local online
        Player localTarget = resolveOnline(arg);
        if (localTarget != null) {
            if (localTarget.equals(admin)) {
                Lang.send(admin, "admin.tp.self",
                        "<red>You are already yourself.</red>");
                return true;
            }
            teleportService.teleportSilently(admin, localTarget);
            Lang.send(admin, "admin.tp.teleported",
                    "<green>Teleported to <aqua>%target%</aqua>.</green>",
                    Map.of("target", localTarget.getName()));
            return true;
        }

        // 2) Cross-server via PlayerDirectory
        PlayerDirectory dir = plugin.getPlayerDirectory();
        if (dir == null) {
            Lang.send(admin, "admin.tp.not-found-no-directory",
                    "<red>Player not found online. <gray>(Cross-server directory is not available.)</gray></red>");
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
            Lang.send(admin, "admin.tp.not-found",
                    "<red>Player not found online.</red>");
            return true;
        }

        String presence = null;
        try {
            presence = dir.getCurrentOrLastServer(targetUuid);
        } catch (Throwable ignored) {}

        String targetName = safeNameLookup(dir, targetUuid, arg);

        // If directory says: same server -> re-check just in case
        if (presence != null && presence.equalsIgnoreCase(localServer)) {
            Player again = Bukkit.getPlayer(targetUuid);
            if (again != null && again.isOnline()) {
                if (again.equals(admin)) {
                    Lang.send(admin, "admin.tp.self",
                            "<red>You are already yourself.</red>");
                    return true;
                }
                teleportService.teleportSilently(admin, again);
                Lang.send(admin, "admin.tp.teleported",
                        "<green>Teleported to <aqua>%target%</aqua>.</green>",
                        Map.of("target", again.getName()));
                return true;
            }
        }

        // 3) Remote server: use TpCrossServerBroker
        if (presence != null && !presence.isBlank() && !presence.equalsIgnoreCase(localServer)) {
            TpCrossServerBroker tpBroker = plugin.getTpBroker();
            if (tpBroker == null) {
                Lang.send(admin, "admin.tp.no-broker",
                        "<red>Cross-server teleport broker not available; cannot /tp to other servers.</red>");
                return true;
            }

            tpBroker.requestCrossServerTp(admin, targetUuid, targetName, presence);
            return true;
        }

        // 4) Unknown presence
        Lang.send(admin, "admin.tp.not-found",
                "<red>Player not found online.</red>");
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

    private String safeNameLookup(PlayerDirectory dir, UUID uuid, String fallback) {
        try {
            String n = dir.lookupNameByUuid(uuid);
            return (n == null || n.isBlank()) ? fallback : n;
        } catch (Throwable ignored) {
            return fallback;
        }
    }
}