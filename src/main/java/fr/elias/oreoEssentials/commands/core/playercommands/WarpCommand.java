// File: src/main/java/fr/elias/oreoEssentials/commands/core/playercommands/WarpCommand.java
package fr.elias.oreoEssentials.commands.core.playercommands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel;
import fr.elias.oreoEssentials.rabbitmq.packet.PacketManager;
import fr.elias.oreoEssentials.rabbitmq.packet.impl.WarpTeleportRequestPacket;
import fr.elias.oreoEssentials.services.WarpDirectory;
import fr.elias.oreoEssentials.services.WarpService;
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
    @Override public String usage() { return "<name>|list [player]"; }

    // ❗ IMPORTANT: allow console
    @Override public boolean playerOnly() { return false; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        final OreoEssentials plugin = OreoEssentials.get();
        final var log = plugin.getLogger();

        // Actor = the one running the command (may be null if console)
        Player actor = (sender instanceof Player) ? (Player) sender : null;

        // /warp or /warp list → list warps (NO COOLDOWN)
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            var list = warps.listWarps();
            if (list.isEmpty()) {
                if (actor != null) {
                    Lang.send(actor, "warp.list-empty", Map.of(), actor);
                } else {
                    sender.sendMessage("[Warp] No warps defined.");
                }
            } else {
                String joined = list.stream().collect(Collectors.joining(", "));
                if (actor != null) {
                    Lang.send(actor, "warp.list",
                            Map.of("warps", joined),
                            actor
                    );
                } else {
                    sender.sendMessage("[Warp] Warps: " + joined);
                }
            }
            log.info("[WarpCmd] " + sender.getName() + " requested warp list. Count=" + list.size());
            return true;
        }

        // ---- Parse warp + optional player ----
        final String rawWarp = args[0];
        final String warpName = rawWarp.trim().toLowerCase(Locale.ROOT);

        // Target player
        Player target;
        if (args.length >= 2) {
            // /warp <warp> <player>
            String targetName = args[1];
            target = Bukkit.getPlayerExact(targetName);
            if (target == null) {
                sender.sendMessage("§cPlayer '" + targetName + "' is not online.");
                return true;
            }
        } else {
            // /warp <warp> → self only
            if (actor == null) {
                sender.sendMessage("§cUsage from console: /warp <warp> <player>");
                return true;
            }
            target = actor;
        }

        // ---- Permission checks ----
        // Base permission for actor (if actor exists)
        if (actor != null && !actor.hasPermission("oreo.warp")) {
            Lang.send(actor, "warp.no-permission", Map.of(), actor);
            log.info("[WarpCmd] Denied (base perm) actor=" + actor.getName() + " warp=" + warpName);
            return true;
        }

        // Check warp-specific permission for actor, if actor exists
        if (actor != null && !warps.canUse(actor, warpName)) {
            String rp = warps.requiredPermission(warpName);
            if (rp == null || rp.isBlank()) {
                Lang.send(actor, "warp.no-permission", Map.of(), actor);
            } else {
                Lang.send(actor, "warp.no-permission-with-node",
                        Map.of("permission", rp),
                        actor
                );
            }
            log.info("[WarpCmd] Denied (warp perm) actor=" + actor.getName()
                    + " target=" + target.getName()
                    + " warp=" + warpName);
            return true;
        }

        // Extra perm to warp other players
        if (actor != null && !actor.equals(target) && !actor.hasPermission("oreo.warp.others")) {
            actor.sendMessage("§cYou don't have permission to warp other players.");
            log.info("[WarpCmd] Denied (others) actor=" + actor.getName()
                    + " tried to warp " + target.getName() + " to " + warpName);
            return true;
        }
        // ---- Existence pre-check (avoid warmup if warp doesn't exist) ----
        final String localServer = plugin.getConfigService().serverName();
        final WarpDirectory warpDir = plugin.getWarpDirectory();

        String ownerServer = (warpDir != null ? warpDir.getWarpServer(warpName) : null);

        // If directory doesn't know this warp at all => not found (no warmup)
        if (ownerServer == null || ownerServer.isBlank()) {
            // maybe it's a local-only warp
            Location test = warps.getWarp(warpName);
            if (test == null) {
                if (actor != null) Lang.send(actor, "warp.not-found", Map.of(), actor);
                else sender.sendMessage("§cWarp '" + warpName + "' not found.");
                return true;
            }
            ownerServer = localServer; // treat as local
        }

        // If warp is local, validate existence in local storage
        if (ownerServer.equalsIgnoreCase(localServer)) {
            Location test = warps.getWarp(warpName);
            if (test == null) {
                if (actor != null) Lang.send(actor, "warp.not-found", Map.of(), actor);
                else sender.sendMessage("§cWarp '" + warpName + "' not found.");
                return true;
            }
        }


        // ---- Cooldown / countdown (features.warp.cooldown) ----
        FileConfiguration settings = plugin.getSettingsConfig().getRoot();
        ConfigurationSection warpSection = settings.getConfigurationSection("features.warp");
        boolean useCooldown = warpSection != null && warpSection.getBoolean("cooldown", false);
        int seconds = (warpSection != null ? warpSection.getInt("cooldown-amount", 0) : 0);

        /*
         * If:
         *  - command is run from CONSOLE (actor == null), OR
         *  - actor is OP, OR
         *  - cooldown disabled, OR
         *  - cooldown invalid,
         * then teleport IMMEDIATELY with no countdown.
         */
        boolean actorIsOp = actor != null && actor.isOp();

        if (actor == null || actorIsOp || !useCooldown || seconds <= 0 || target == null) {
            performWarp(plugin, warps, sender, actor, target, warpName);
            return true;
        }

        // Countdown shown to the TARGET player (only for non-op in-game actors)
        startCountdown(target, seconds, warpName, () ->
                performWarp(plugin, warps, sender, actor, target, warpName)
        );
        return true;

    }


    /**
     * Runs the original warp logic (local or cross-server) AFTER the countdown.
     */
    private void performWarp(OreoEssentials plugin,
                             WarpService warps,
                             CommandSender sender,
                             Player actor,
                             Player target,
                             String warpName) {

        final var log = plugin.getLogger();

        // Resolve server owner for this warp
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

        // Local warp -> direct teleport
        if (targetServer.equalsIgnoreCase(localServer)) {
            Location l = warps.getWarp(warpName);
            if (l == null) {
                if (actor != null) {
                    Lang.send(actor, "warp.not-found", Map.of(), actor);
                } else {
                    sender.sendMessage("§cWarp '" + warpName + "' not found on this server.");
                }
                log.warning("[WarpCmd] Local warp not found. warp=" + warpName);
                return;
            }
            try {
                target.teleport(l);
                // Message to target
                Lang.send(target, "warp.teleported",
                        Map.of("warp", warpName),
                        target
                );
                // Message to sender if different
                if (!sender.equals(target)) {
                    sender.sendMessage("§aTeleported §e" + target.getName()
                            + "§a to warp §b" + warpName + "§a.");
                }
                log.info("[WarpCmd] Local teleport success. target=" + target.getName()
                        + " warp=" + warpName + " loc=" + l);
            } catch (Exception ex) {
                String err = (ex.getMessage() == null ? "unknown" : ex.getMessage());
                if (actor != null) {
                    Lang.send(actor, "warp.teleport-failed",
                            Map.of("error", err),
                            actor
                    );
                } else {
                    sender.sendMessage("§cTeleport failed: " + err);
                }
                log.warning("[WarpCmd] Local teleport exception: " + ex.getMessage());
            }
            return;
        }

        // Respect cross-server toggle for warps
        var cs = plugin.getCrossServerSettings();
        if (!cs.warps() && !targetServer.equalsIgnoreCase(localServer)) {
            if (actor != null) {
                Lang.send(actor, "warp.cross-disabled", Map.of(), actor);
                Lang.send(actor, "warp.cross-disabled-tip",
                        Map.of(
                                "server", targetServer,
                                "warp", warpName
                        ),
                        actor
                );
            } else {
                sender.sendMessage("§cCross-server warps are disabled in config.");
            }
            return;
        }

        // Remote warp: publish to the target server’s queue, then proxy-switch the TARGET player
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
            if (actor != null) {
                Lang.send(actor, "warp.messaging-disabled", Map.of(), actor);
                Lang.send(actor, "warp.messaging-disabled-tip",
                        Map.of(
                                "server", targetServer,
                                "warp", warpName
                        ),
                        actor
                );
            } else {
                sender.sendMessage("§cCross-server messaging is disabled; cannot warp to " + targetServer + ".");
            }
            return;
        }

        // Proxy switch for the TARGET player
        if (sendPlayerToServer(target, targetServer)) {
            // Message to target
            Lang.send(target, "warp.sending",
                    Map.of(
                            "server", targetServer,
                            "warp", warpName
                    ),
                    target
            );
            // Message to sender if different
            if (!sender.equals(target)) {
                sender.sendMessage("§aSending §e" + target.getName()
                        + "§a to §b" + targetServer + "§a (warp §e" + warpName + "§a).");
            }
            log.info("[WarpCmd] Proxy switch initiated. player=" + target.getUniqueId()
                    + " to=" + targetServer);
        } else {
            if (actor != null) {
                Lang.send(actor, "warp.switch-failed",
                        Map.of("server", targetServer),
                        actor
                );
            } else {
                sender.sendMessage("§cProxy switch failed to " + targetServer + ".");
            }
            log.warning("[WarpCmd] Proxy switch failed to " + targetServer
                    + " (check Velocity/Bungee server name match).");
        }
    }

    /** Bungee/Velocity plugin message switch (for the TARGET player) */
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

    /**
     * Shows a big title countdown on the player, cancels if he moves,
     * then runs the action at the end.
     *
     * Uses:
     *  - warp.cancelled-moved in lang.yml when the player moves.
     */
    private void startCountdown(Player target, int seconds, String warpName, Runnable action) {
        final OreoEssentials plugin = OreoEssentials.get();
        final Location origin = target.getLocation().clone(); // for movement check

        new BukkitRunnable() {
            int remaining = seconds;

            @Override
            public void run() {
                if (!target.isOnline()) {
                    cancel();
                    return;
                }

                // Cancel if player moved to another block/world (head rotation allowed)
                if (hasBodyMoved(target, origin)) {
                    cancel();
                    // Lang: warp cancelled because of movement
                    Lang.send(target, "warp.cancelled-moved",
                            Map.of("warp", warpName),
                            target
                    );
                    return;
                }

                if (remaining <= 0) {
                    cancel();
                    action.run();
                    return;
                }

                // Title + subtitle from lang.yml
                String title = Lang.msg("teleport.countdown.title", null, target);
                String subtitle = Lang.msg(
                        "teleport.countdown.subtitle",
                        Map.of("seconds", String.valueOf(remaining)),
                        target
                );

                target.sendTitle(title, subtitle, 0, 20, 0);
                remaining--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }



    /**
     * Check if player moved to another block/world (head rotation allowed).
     */
    private boolean hasBodyMoved(Player p, Location origin) {
        Location now = p.getLocation();
        return !now.getWorld().equals(origin.getWorld())
                || now.getBlockX() != origin.getBlockX()
                || now.getBlockY() != origin.getBlockY()
                || now.getBlockZ() != origin.getBlockZ();
    }

}
