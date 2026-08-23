package fr.elias.oreoEssentials.modules.tp.command;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.playerdirectory.PlayerDirectory;
import fr.elias.oreoEssentials.modules.tp.service.TeleportService;
import fr.elias.oreoEssentials.modules.tp.rabbit.brokers.TpCrossServerBroker;
import fr.elias.oreoEssentials.util.Lang;
import fr.elias.oreoEssentials.util.OreScheduler;
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
            Lang.send(admin, "admin.tp.usage", "<red>Usage: /tp <player></red>");
            return true;
        }

        String arg = args[0].trim();
        if (arg.isEmpty()) {
            Lang.send(admin, "admin.tp.usage", "<red>Usage: /tp <player></red>");
            return true;
        }

        OreoEssentials plugin = OreoEssentials.get();
        String localServer = plugin.getConfigService().serverName();

        Player localTarget = resolveOnline(arg);
        if (localTarget != null) {
            if (localTarget.equals(admin)) {
                Lang.send(admin, "admin.tp.self", "<red>You are already yourself.</red>");
                return true;
            }
            teleportService.teleportSilently(admin, localTarget);
            Lang.send(admin, "admin.tp.teleported",
                    "<green>Teleported to <aqua>%target%</aqua>.</green>",
                    Map.of("target", localTarget.getName()));
            return true;
        }

        PlayerDirectory dir = plugin.getPlayerDirectory();
        if (dir == null) {
            Lang.send(admin, "admin.tp.not-found-no-directory",
                    "<red>Player not found online. <gray>(Cross-server directory is not available.)</gray></red>");
            return true;
        }

        // PlayerDirectory uses synchronous MongoDB. Resolve presence asynchronously and
        // return to the admin's entity scheduler before touching Bukkit/player state again.
        OreScheduler.runAsync(plugin, () -> {
            UUID targetUuid = null;
            String presence = null;
            String targetName = arg;
            try {
                targetUuid = dir.lookupUuidByName(arg);
                if (targetUuid == null) {
                    try { targetUuid = UUID.fromString(arg); } catch (Exception ignored) {}
                }
                if (targetUuid != null) {
                    presence = dir.lookupCurrentServer(targetUuid);
                    String resolved = dir.lookupNameByUuid(targetUuid);
                    if (resolved != null && !resolved.isBlank()) targetName = resolved;
                }
            } catch (Throwable ignored) {}

            final UUID resolvedUuid = targetUuid;
            final String resolvedPresence = presence;
            final String resolvedName = targetName;

            OreScheduler.runForEntity(plugin, admin, () -> {
                if (!admin.isOnline()) return;

                if (resolvedUuid == null) {
                    Lang.send(admin, "admin.tp.not-found", "<red>Player not found online.</red>");
                    return;
                }

                if (resolvedPresence != null && resolvedPresence.equalsIgnoreCase(localServer)) {
                    Player again = Bukkit.getPlayer(resolvedUuid);
                    if (again != null && again.isOnline()) {
                        if (again.equals(admin)) {
                            Lang.send(admin, "admin.tp.self", "<red>You are already yourself.</red>");
                            return;
                        }
                        teleportService.teleportSilently(admin, again);
                        Lang.send(admin, "admin.tp.teleported",
                                "<green>Teleported to <aqua>%target%</aqua>.</green>",
                                Map.of("target", again.getName()));
                        return;
                    }
                }

                if (resolvedPresence != null && !resolvedPresence.isBlank()
                        && !resolvedPresence.equalsIgnoreCase(localServer)) {
                    TpCrossServerBroker tpBroker = plugin.getTpBroker();
                    if (tpBroker == null) {
                        Lang.send(admin, "admin.tp.no-broker",
                                "<red>Cross-server teleport broker not available; cannot /tp to other servers.</red>");
                        return;
                    }
                    tpBroker.requestCrossServerTp(admin, resolvedUuid, resolvedName, resolvedPresence);
                    return;
                }

                Lang.send(admin, "admin.tp.not-found", "<red>Player not found online.</red>");
            });
        });

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
}
