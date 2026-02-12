package fr.elias.oreoEssentials.modules.warps.commands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.modules.warps.rabbit.WarpDirectory;
import fr.elias.oreoEssentials.modules.warps.WarpService;
import fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel;
import fr.elias.oreoEssentials.rabbitmq.packet.PacketManager;
import fr.elias.oreoEssentials.modules.warps.rabbit.packets.WarpTeleportRequestPacket;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class WarpCommand implements OreoCommand {

    private final WarpService warps;

    public WarpCommand(WarpService warps) {
        this.warps = warps;
    }

    @Override public String name() { return "warp"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.warp"; }
    @Override public String usage() { return "<n>|list [player]"; }

    @Override public boolean playerOnly() { return false; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        final OreoEssentials plugin = OreoEssentials.get();
        final var log = plugin.getLogger();

        Player actor = (sender instanceof Player) ? (Player) sender : null;

        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            var list = warps.listWarps();
            if (list.isEmpty()) {
                Lang.send(sender, "warp.list-empty",
                        "<yellow>Warps: <gray>(none)</gray></yellow>");
            } else {
                String joined = list.stream().collect(Collectors.joining(", "));
                Lang.send(sender, "warp.list",
                        "<yellow>Warps:</yellow> <gray>%warps%</gray>",
                        Map.of("warps", joined));
            }
            log.info("[WarpCmd] " + sender.getName() + " requested warp list. Count=" + list.size());
            return true;
        }

        final String rawWarp = args[0];
        final String warpName = rawWarp.trim().toLowerCase(Locale.ROOT);

        Player target;
        if (args.length >= 2) {
            String targetName = args[1];
            target = Bukkit.getPlayerExact(targetName);
            if (target == null) {
                Lang.send(sender, "warp.player-not-found",
                        "<red>Player '<yellow>%player%</yellow>' is not online.</red>",
                        Map.of("player", targetName));
                return true;
            }
        } else {
            if (actor == null) {
                Lang.send(sender, "warp.console-usage",
                        "<red>Usage from console: /warp <warp> <player></red>");
                return true;
            }
            target = actor;
        }

        if (actor != null && !actor.hasPermission("oreo.warp")) {
            Lang.send(actor, "warp.no-permission",
                    "<red>You don't have permission for this warp.</red>");
            log.info("[WarpCmd] Denied (base perm) actor=" + actor.getName() + " warp=" + warpName);
            return true;
        }

        if (actor != null && !warps.canUse(actor, warpName)) {
            String rp = warps.requiredPermission(warpName);
            if (rp == null || rp.isBlank()) {
                Lang.send(actor, "warp.no-permission",
                        "<red>You don't have permission for this warp.</red>");
            } else {
                Lang.send(actor, "warp.no-permission-with-node",
                        "<red>You don't have permission for this warp. <gray>(%permission%)</gray></red>",
                        Map.of("permission", rp));
            }
            log.info("[WarpCmd] Denied (warp perm) actor=" + actor.getName()
                    + " target=" + target.getName()
                    + " warp=" + warpName);
            return true;
        }

        if (actor != null && !actor.equals(target) && !actor.hasPermission("oreo.warp.others")) {
            Lang.send(actor, "warp.no-permission-others",
                    "<red>You don't have permission to warp other players.</red>");
            log.info("[WarpCmd] Denied (others) actor=" + actor.getName()
                    + " tried to warp " + target.getName() + " to " + warpName);
            return true;
        }

        final String localServer = plugin.getConfigService().serverName();
        final WarpDirectory warpDir = plugin.getWarpDirectory();

        String ownerServer = (warpDir != null ? warpDir.getWarpServer(warpName) : null);

        if (ownerServer == null || ownerServer.isBlank()) {
            Location test = warps.getWarp(warpName);
            if (test == null) {
                Lang.send(sender, "warp.not-found",
                        "<red>Warp not found.</red>");
                return true;
            }
            ownerServer = localServer;
        }

        if (ownerServer.equalsIgnoreCase(localServer)) {
            Location test = warps.getWarp(warpName);
            if (test == null) {
                Lang.send(sender, "warp.not-found",
                        "<red>Warp not found.</red>");
                return true;
            }
        }

        FileConfiguration settings = plugin.getSettingsConfig().getRoot();
        ConfigurationSection warpSection = settings.getConfigurationSection("features.warp");
        boolean useCooldown = warpSection != null && warpSection.getBoolean("cooldown", false);
        int seconds = (warpSection != null ? warpSection.getInt("cooldown-amount", 0) : 0);

        boolean actorIsOp = actor != null && actor.isOp();

        if (actor == null || actorIsOp || !useCooldown || seconds <= 0 || target == null) {
            performWarp(plugin, warps, sender, actor, target, warpName);
            return true;
        }

        startCountdown(target, seconds, warpName, () ->
                performWarp(plugin, warps, sender, actor, target, warpName)
        );
        return true;
    }

    private void performWarp(OreoEssentials plugin,
                             WarpService warps,
                             CommandSender sender,
                             Player actor,
                             Player target,
                             String warpName) {

        final var log = plugin.getLogger();

        final String localServer  = plugin.getConfigService().serverName();
        final WarpDirectory warpDir = plugin.getWarpDirectory();
        String targetServer = (warpDir != null ? warpDir.getWarpServer(warpName) : localServer);
        if (targetServer == null || targetServer.isBlank()) targetServer = localServer;

        log.info("[WarpCmd] Sender=" + sender.getName()
                + " actor=" + (actor == null ? "CONSOLE" : actor.getName())
                + " target=" + target.getName()
                + " UUID=" + target.getUniqueId()
                + " warp=" + warpName
                + " localServer=" + localServer
                + " targetServer=" + targetServer
                + " warpDir=" + (warpDir == null ? "null" : "ok"));

        if (targetServer.equalsIgnoreCase(localServer)) {
            Location l = warps.getWarp(warpName);
            if (l == null) {
                Lang.send(sender, "warp.not-found",
                        "<red>Warp not found.</red>");
                log.warning("[WarpCmd] Local warp not found. warp=" + warpName);
                return;
            }

            try {
                target.teleport(l);
                Lang.send(target, "warp.teleported",
                        "<green>Teleported to warp <aqua>%warp%</aqua>.</green>",
                        Map.of("warp", warpName));

                if (!sender.equals(target)) {
                    Lang.send(sender, "warp.teleported-other",
                            "<green>Teleported <yellow>%player%</yellow> to warp <aqua>%warp%</aqua>.</green>",
                            Map.of("player", target.getName(), "warp", warpName));
                }

                log.info("[WarpCmd] Local teleport success. target=" + target.getName()
                        + " warp=" + warpName + " loc=" + l);
            } catch (Exception ex) {
                String err = (ex.getMessage() == null ? "unknown" : ex.getMessage());
                Lang.send(sender, "warp.teleport-failed",
                        "<red>Teleport failed: <gray>%error%</gray></red>",
                        Map.of("error", err));
                log.warning("[WarpCmd] Local teleport exception: " + ex.getMessage());
            }
            return;
        }

        var cs = plugin.getCrossServerSettings();
        if (!cs.warps() && !targetServer.equalsIgnoreCase(localServer)) {
            Lang.send(sender, "warp.cross-disabled",
                    "<red>Cross-server warps are disabled by server config.</red>");
            Lang.send(sender, "warp.cross-disabled-tip",
                    "<gray>Use <aqua>/server %server%</aqua> then run <aqua>/warp %warp%</aqua></gray>",
                    Map.of("server", targetServer, "warp", warpName));
            return;
        }

        final PacketManager pm = plugin.getPacketManager();
        log.info("[WarpCmd] Remote warp. pm=" + (pm == null ? "null" : "ok")
                + " pm.init=" + (pm != null && pm.isInitialized()));

        if (pm != null && pm.isInitialized()) {
            String requestId = UUID.randomUUID().toString();
            plugin.getLogger().info("[WARP/SEND] from=" + localServer
                    + " player=" + target.getUniqueId()
                    + " nameArg='" + warpName + "' -> targetServer=" + targetServer
                    + " requestId=" + requestId);

            WarpTeleportRequestPacket pkt =
                    new WarpTeleportRequestPacket(target.getUniqueId(), warpName, targetServer, requestId);
            PacketChannel ch = PacketChannel.individual(targetServer);
            pm.sendPacket(ch, pkt);
        } else {
            Lang.send(sender, "warp.messaging-disabled",
                    "<red>Cross-server messaging is disabled.</red>");
            Lang.send(sender, "warp.messaging-disabled-tip",
                    "<gray>Use <aqua>/server %server%</aqua> then run <aqua>/warp %warp%</aqua></gray>",
                    Map.of("server", targetServer, "warp", warpName));
            return;
        }

        if (sendPlayerToServer(target, targetServer)) {
            Lang.send(target, "warp.sending",
                    "<yellow>Sending you to <aqua>%server%</aqua>... you'll be teleported to warp <aqua>%warp%</aqua> on arrival.</yellow>",
                    Map.of("server", targetServer, "warp", warpName));

            if (!sender.equals(target)) {
                Lang.send(sender, "warp.sending-other",
                        "<green>Sending <yellow>%player%</yellow> to <aqua>%server%</aqua> (warp <yellow>%warp%</yellow>).</green>",
                        Map.of("player", target.getName(), "server", targetServer, "warp", warpName));
            }

            log.info("[WarpCmd] Proxy switch initiated. player=" + target.getUniqueId()
                    + " to=" + targetServer);
        } else {
            Lang.send(sender, "warp.switch-failed",
                    "<red>Failed to switch you to %server%.</red>",
                    Map.of("server", targetServer));
            log.warning("[WarpCmd] Proxy switch failed to " + targetServer
                    + " (check Velocity/Bungee server name match).");
        }
    }

    private boolean sendPlayerToServer(Player p, String serverName) {
        final var plugin = OreoEssentials.get();
        final var log = plugin.getLogger();
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(b);
            out.writeUTF("Connect");
            out.writeUTF(serverName);
            p.sendPluginMessage(plugin, "BungeeCord", b.toByteArray());
            log.info("[WarpCmd] Sent plugin message 'Connect' to proxy. server=" + serverName
                    + " player=" + p.getName());
            return true;
        } catch (Exception ex) {
            log.warning("[WarpCmd] Failed to send Connect plugin message: " + ex.getMessage());
            return false;
        }
    }

    private void startCountdown(Player target, int seconds, String warpName, Runnable action) {
        final OreoEssentials plugin = OreoEssentials.get();
        final Location origin = target.getLocation().clone();

        new BukkitRunnable() {
            int remaining = seconds;

            @Override
            public void run() {
                if (!target.isOnline()) {
                    cancel();
                    return;
                }

                if (hasBodyMoved(target, origin)) {
                    cancel();
                    Lang.send(target, "warp.cancelled-moved",
                            "<red>Teleport to warp <yellow>%warp%</yellow> cancelled: you moved.</red>",
                            Map.of("warp", warpName));
                    return;
                }

                if (remaining <= 0) {
                    cancel();
                    action.run();
                    return;
                }

                String title = Lang.msgWithDefault(
                        "teleport.countdown.title",
                        "<yellow>Teleporting...</yellow>",
                        target
                );

                String subtitle = Lang.msgWithDefault(
                        "teleport.countdown.subtitle",
                        "<gray>In <white>%seconds%</white>s...</gray>",
                        Map.of("seconds", String.valueOf(remaining)),
                        target
                );

                target.sendTitle(title, subtitle, 0, 20, 0);
                remaining--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private boolean hasBodyMoved(Player p, Location origin) {
        Location now = p.getLocation();
        return !now.getWorld().equals(origin.getWorld())
                || now.getBlockX() != origin.getBlockX()
                || now.getBlockY() != origin.getBlockY()
                || now.getBlockZ() != origin.getBlockZ();
    }
}