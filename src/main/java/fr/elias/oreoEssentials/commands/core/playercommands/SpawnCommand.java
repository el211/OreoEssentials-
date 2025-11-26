// File: src/main/java/fr/elias/oreoEssentials/commands/core/playercommands/SpawnCommand.java
package fr.elias.oreoEssentials.commands.core.playercommands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel;
import fr.elias.oreoEssentials.rabbitmq.packet.PacketManager;
import fr.elias.oreoEssentials.rabbitmq.packet.impl.SpawnTeleportRequestPacket;
import fr.elias.oreoEssentials.services.SpawnDirectory;
import fr.elias.oreoEssentials.services.SpawnService;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SpawnCommand implements OreoCommand {
    private final SpawnService spawn;

    public SpawnCommand(SpawnService spawn) { this.spawn = spawn; }

    @Override public String name() { return "spawn"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.spawn"; }
    @Override public String usage() { return ""; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        final OreoEssentials plugin = OreoEssentials.get();
        final var log = plugin.getLogger();
        if (!(sender instanceof Player p)) return true;

        final String localServer = plugin.getConfigService().serverName();
        final SpawnDirectory spawnDir = plugin.getSpawnDirectory();
        String targetServer = (spawnDir != null ? spawnDir.getSpawnServer() : localServer);
        if (targetServer == null || targetServer.isBlank()) targetServer = localServer;

        log.info("[SpawnCmd] Player=" + p.getName() + " UUID=" + p.getUniqueId()
                + " localServer=" + localServer
                + " targetServer=" + targetServer
                + " spawnDir=" + (spawnDir == null ? "null" : "ok"));

        // Local spawn -> cooldown + teleport on this server
        if (targetServer.equalsIgnoreCase(localServer)) {
            return handleLocalSpawn(plugin, p, log);
        }

        // Respect cross-server toggle for spawn
        var cs = plugin.getCrossServerSettings();
        if (!cs.spawn()) {
            Lang.send(p, "spawn.cross-disabled", Map.of(), p);
            Lang.send(p, "spawn.cross-disabled-tip",
                    Map.of("server", targetServer),
                    p
            );
            return true;
        }

        // Remote spawn -> cooldown on current server, then packet + proxy switch
        return handleRemoteSpawn(plugin, p, localServer, targetServer, log);
    }

    /**
     * Local spawn (targetServer == localServer).
     * Applies cooldown (if enabled) and then teleports the player to spawn.
     * OPs bypass the cooldown.
     */
    private boolean handleLocalSpawn(OreoEssentials plugin, Player p, java.util.logging.Logger log) {
        Location spawnLoc = spawn.getSpawn();
        if (spawnLoc == null) {
            Lang.send(p, "spawn.not-set", Map.of(), p);
            log.warning("[SpawnCmd] Local spawn not set.");
            return true;
        }

        // Read cooldown from settings.yml: features.spawn.cooldown / cooldown-amount
        ConfigurationSection sec =
                plugin.getSettingsConfig().getRoot().getConfigurationSection("features.spawn");
        boolean enabled = sec != null && sec.getBoolean("cooldown", false);
        int seconds     = (sec != null ? sec.getInt("cooldown-amount", 0) : 0);

        // OP bypass
        boolean bypassCooldown = p.isOp();

        // No cooldown configured OR OP -> immediate teleport (legacy behavior)
        if (bypassCooldown || !enabled || seconds <= 0) {
            try {
                p.teleport(spawnLoc);
                Lang.send(p, "spawn.teleported", Map.of(), p);
                log.info("[SpawnCmd] Local teleport success. loc=" + spawnLoc
                        + (bypassCooldown ? " (bypass cooldown: OP)" : ""));
            } catch (Exception ex) {
                log.warning("[SpawnCmd] Local teleport exception: " + ex.getMessage());
                Lang.send(p, "spawn.teleport-failed",
                        Map.of("error", ex.getMessage() == null ? "unknown" : ex.getMessage()),
                        p
                );
            }
            return true;
        }

        // Cooldown enabled: countdown with "no movement" rule (no block movement)
        final Location origin = p.getLocation().clone();

        new BukkitRunnable() {
            int remain = seconds;

            @Override
            public void run() {
                if (!p.isOnline()) {
                    log.info("[SpawnCmd] Player went offline during spawn countdown; cancel.");
                    cancel();
                    return;
                }

                // Cancel if player moved to another block/world (head rotation allowed)
                if (hasBodyMoved(p, origin)) {
                    cancel();
                    Lang.send(p, "spawn.cancelled-moved", Map.of(), p);
                    log.info("[SpawnCmd] Player moved during spawn countdown; cancelled.");
                    return;
                }

                if (remain <= 0) {
                    cancel();
                    try {
                        p.teleport(spawnLoc);
                        Lang.send(p, "spawn.teleported", Map.of(), p);
                        log.info("[SpawnCmd] Local teleport success after countdown. loc=" + spawnLoc);
                    } catch (Exception ex) {
                        log.warning("[SpawnCmd] Local teleport exception after countdown: " + ex.getMessage());
                        Lang.send(p, "spawn.teleport-failed",
                                Map.of("error", ex.getMessage() == null ? "unknown" : ex.getMessage()),
                                p
                        );
                    }
                    return;
                }

                // Show countdown to the player (lang-based)
                String title = Lang.msg("teleport.countdown.title", null, p);
                String subtitle = Lang.msg(
                        "teleport.countdown.subtitle",
                        Map.of("seconds", String.valueOf(remain)),
                        p
                );
                p.sendTitle(title, subtitle, 0, 20, 0);
                remain--;
            }
        }.runTaskTimer(plugin, 0L, 20L);

        return true;
    }

    /**
     * Remote spawn: countdown on current server, then send SpawnTeleportRequestPacket
     * + proxy switch via plugin messaging.
     * OPs bypass the cooldown.
     */
    private boolean handleRemoteSpawn(OreoEssentials plugin,
                                      Player p,
                                      String localServer,
                                      String targetServer,
                                      java.util.logging.Logger log) {

        final PacketManager pm = plugin.getPacketManager();
        log.info("[SpawnCmd] Remote spawn. pm=" + (pm == null ? "null" : "ok")
                + " pm.init=" + (pm != null && pm.isInitialized()));

        if (pm == null || !pm.isInitialized()) {
            Lang.send(p, "spawn.messaging-disabled", Map.of(), p);
            Lang.send(p, "spawn.messaging-disabled-tip",
                    Map.of("server", targetServer),
                    p
            );
            return true;
        }

        // Read cooldown from settings.yml: features.spawn.cooldown / cooldown-amount
        ConfigurationSection sec =
                plugin.getSettingsConfig().getRoot().getConfigurationSection("features.spawn");
        boolean enabled = sec != null && sec.getBoolean("cooldown", false);
        int seconds     = (sec != null ? sec.getInt("cooldown-amount", 0) : 0);

        // OP bypass
        boolean bypassCooldown = p.isOp();

        final String requestId = UUID.randomUUID().toString();

        // If no cooldown configured OR OP -> behave like before (instant request + server switch)
        if (bypassCooldown || !enabled || seconds <= 0) {
            plugin.getLogger().info("[SPAWN/SEND] (no cooldown"
                    + (bypassCooldown ? " / OP bypass" : "")
                    + ") from=" + localServer
                    + " player=" + p.getUniqueId()
                    + " -> targetServer=" + targetServer
                    + " requestId=" + requestId);

            SpawnTeleportRequestPacket pkt =
                    new SpawnTeleportRequestPacket(p.getUniqueId(), targetServer, requestId);
            PacketChannel ch = PacketChannel.individual(targetServer);
            pm.sendPacket(ch, pkt);

            if (sendPlayerToServer(p, targetServer)) {
                Lang.send(p, "spawn.sending",
                        Map.of("server", targetServer),
                        p
                );
                log.info("[SpawnCmd] Proxy switch initiated (no cooldown"
                        + (bypassCooldown ? " / OP bypass" : "")
                        + "). player=" + p.getUniqueId() + " to=" + targetServer);
            } else {
                Lang.send(p, "spawn.switch-failed",
                        Map.of("server", targetServer),
                        p
                );
                log.warning("[SpawnCmd] Proxy switch failed to " + targetServer + " (check Velocity/Bungee server name match).");
            }
            return true;
        }

        // Cooldown enabled: countdown on current server, then send packet + switch.
        final Location origin = p.getLocation().clone();

        plugin.getLogger().info("[SPAWN/SEND] (cooldown) from=" + localServer
                + " player=" + p.getUniqueId()
                + " -> targetServer=" + targetServer
                + " requestId=" + requestId
                + " cooldown=" + seconds + "s");

        new BukkitRunnable() {
            int remain = seconds;

            @Override
            public void run() {
                if (!p.isOnline()) {
                    log.info("[SpawnCmd] Player went offline during remote spawn countdown; cancel.");
                    cancel();
                    return;
                }

                // Cancel if player moved to another block/world (head rotation allowed)
                if (hasBodyMoved(p, origin)) {
                    cancel();
                    Lang.send(p, "spawn.cancelled-moved", Map.of(), p);
                    log.info("[SpawnCmd] Player moved during remote spawn countdown; cancelled.");
                    return;
                }

                if (remain <= 0) {
                    cancel();

                    // Send packet to target server
                    SpawnTeleportRequestPacket pkt =
                            new SpawnTeleportRequestPacket(p.getUniqueId(), targetServer, requestId);
                    PacketChannel ch = PacketChannel.individual(targetServer);
                    pm.sendPacket(ch, pkt);

                    // Now proxy-switch the player
                    if (sendPlayerToServer(p, targetServer)) {
                        Lang.send(p, "spawn.sending",
                                Map.of("server", targetServer),
                                p
                        );
                        log.info("[SpawnCmd] Proxy switch initiated after countdown. player="
                                + p.getUniqueId() + " to=" + targetServer);
                    } else {
                        Lang.send(p, "spawn.switch-failed",
                                Map.of("server", targetServer),
                                p
                        );
                        log.warning("[SpawnCmd] Proxy switch failed to " + targetServer
                                + " (check Velocity/Bungee server name match).");
                    }
                    return;
                }

                // Show countdown to the player (lang-based)
                String title = Lang.msg("teleport.countdown.title", null, p);
                String subtitle = Lang.msg(
                        "teleport.countdown.subtitle",
                        Map.of("seconds", String.valueOf(remain)),
                        p
                );
                p.sendTitle(title, subtitle, 0, 20, 0);
                remain--;
            }
        }.runTaskTimer(plugin, 0L, 20L);

        return true;
    }

    /** Bungee/Velocity plugin message switch */
    private boolean sendPlayerToServer(Player p, String serverName) {
        final var plugin = OreoEssentials.get();
        final var log = plugin.getLogger();
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(b);
            out.writeUTF("Connect");
            out.writeUTF(serverName);
            p.sendPluginMessage(plugin, "BungeeCord", b.toByteArray());
            log.info("[SpawnCmd] Sent plugin message 'Connect' to proxy. server=" + serverName);
            return true;
        } catch (Exception ex) {
            log.warning("[SpawnCmd] Failed to send Connect plugin message: " + ex.getMessage());
            return false;
        }
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
