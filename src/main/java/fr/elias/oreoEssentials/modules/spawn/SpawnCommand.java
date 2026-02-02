package fr.elias.oreoEssentials.modules.spawn;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel;
import fr.elias.oreoEssentials.rabbitmq.packet.PacketManager;
import fr.elias.oreoEssentials.modules.spawn.rabbit.packets.SpawnTeleportRequestPacket;
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

    public SpawnCommand(SpawnService spawn) {
        this.spawn = spawn;
    }

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

        if (!plugin.getSettingsConfig().isEnabled("spawn")) {
            Lang.send(p, "spawn.disabled",
                    "<red>Spawn command is currently disabled.</red>");
            return true;
        }

        final String localServer = plugin.getConfigService().serverName();
        var cs = plugin.getCrossServerSettings();

        String targetServer = localServer; // Default to local server

        if (cs.spawn()) {
            final SpawnDirectory spawnDir = plugin.getSpawnDirectory();
            if (spawnDir != null) {
                String remoteServer = spawnDir.getSpawnServer();
                if (remoteServer != null && !remoteServer.isBlank()) {
                    targetServer = remoteServer;
                }
            }
        }

        log.info("[SpawnCmd] Player=" + p.getName() + " UUID=" + p.getUniqueId()
                + " localServer=" + localServer
                + " targetServer=" + targetServer
                + " crossServerEnabled=" + cs.spawn());

        if (targetServer.equalsIgnoreCase(localServer)) {
            return handleLocalSpawn(plugin, p, log);
        }


        final PacketManager pm = plugin.getPacketManager();
        log.info("[SpawnCmd] Remote spawn. pm=" + (pm == null ? "null" : "ok")
                + " pm.init=" + (pm != null && pm.isInitialized()));

        if (pm == null || !pm.isInitialized()) {
            Lang.send(p, "spawn.messaging-disabled",
                    "<red>Cross-server messaging is disabled.</red>");
            Lang.send(p, "spawn.messaging-disabled-tip",
                    "<gray>Ask an admin to enable messaging or use <yellow>/server %server%</yellow> then <yellow>/spawn</yellow>.</gray>",
                    Map.of("server", targetServer));
            return true;
        }

        return handleRemoteSpawn(plugin, p, localServer, targetServer, log);
    }
    private boolean handleLocalSpawn(OreoEssentials plugin, Player p, java.util.logging.Logger log) {
        String localServer = plugin.getConfigService().serverName();
        Location spawnLoc = spawn.getSpawn(localServer);
        if (spawnLoc == null) {
            Lang.send(p, "spawn.not-set",
                    "<red>Spawn location is not set.</red>");
            log.warning("[SpawnCmd] Local spawn not set.");
            return true;
        }
        log.info("[SpawnCmd] Spawn location: world=" + spawnLoc.getWorld().getName()
                + " x=" + spawnLoc.getX()
                + " y=" + spawnLoc.getY()
                + " z=" + spawnLoc.getZ());
        ConfigurationSection sec =
                plugin.getSettingsConfig().getRoot().getConfigurationSection("features.spawn");
        boolean enabled = sec != null && sec.getBoolean("cooldown", false);
        int seconds     = (sec != null ? sec.getInt("cooldown-amount", 0) : 0);

        boolean bypassCooldown = p.isOp();

        if (bypassCooldown || !enabled || seconds <= 0) {
            try {
                p.teleport(spawnLoc);
                Lang.send(p, "spawn.teleported",
                        "<green>Teleported to spawn.</green>");
                log.info("[SpawnCmd] Local teleport success. loc=" + spawnLoc
                        + (bypassCooldown ? " (bypass cooldown: OP)" : ""));
            } catch (Exception ex) {
                log.warning("[SpawnCmd] Local teleport exception: " + ex.getMessage());
                Lang.send(p, "spawn.teleport-failed",
                        "<red>Teleport failed: %error%</red>",
                        Map.of("error", ex.getMessage() == null ? "unknown" : ex.getMessage()));
            }
            return true;
        }

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

                if (hasBodyMoved(p, origin)) {
                    cancel();
                    Lang.send(p, "spawn.cancelled-moved",
                            "<red>Teleport cancelled: you moved.</red>");
                    log.info("[SpawnCmd] Player moved during spawn countdown; cancelled.");
                    return;
                }

                if (remain <= 0) {
                    cancel();
                    try {
                        p.teleport(spawnLoc);
                        Lang.send(p, "spawn.teleported",
                                "<green>Teleported to spawn.</green>");
                        log.info("[SpawnCmd] Local teleport success after countdown. loc=" + spawnLoc);
                    } catch (Exception ex) {
                        log.warning("[SpawnCmd] Local teleport exception after countdown: " + ex.getMessage());
                        Lang.send(p, "spawn.teleport-failed",
                                "<red>Teleport failed: %error%</red>",
                                Map.of("error", ex.getMessage() == null ? "unknown" : ex.getMessage()));
                    }
                    return;
                }

                String title = Lang.msgWithDefault(
                        "teleport.countdown.title",
                        "<yellow>Teleporting...</yellow>",
                        p
                );

                String subtitle = Lang.msgWithDefault(
                        "teleport.countdown.subtitle",
                        "<gray>In <white>%seconds%</white>s...</gray>",
                        Map.of("seconds", String.valueOf(remain)),
                        p
                );

                p.sendTitle(title, subtitle, 0, 20, 0);
                remain--;
            }
        }.runTaskTimer(plugin, 0L, 20L);

        return true;
    }


    private boolean handleRemoteSpawn(OreoEssentials plugin,
                                      Player p,
                                      String localServer,
                                      String targetServer,
                                      java.util.logging.Logger log) {

        final PacketManager pm = plugin.getPacketManager();
        log.info("[SpawnCmd] Remote spawn. pm=" + (pm == null ? "null" : "ok")
                + " pm.init=" + (pm != null && pm.isInitialized()));

        if (pm == null || !pm.isInitialized()) {
            Lang.send(p, "spawn.messaging-disabled",
                    "<red>Cross-server messaging is disabled.</red>");
            Lang.send(p, "spawn.messaging-disabled-tip",
                    "<gray>Ask an admin to enable messaging or use <yellow>/server %server%</yellow> then <yellow>/spawn</yellow>.</gray>",
                    Map.of("server", targetServer));
            return true;
        }

        ConfigurationSection sec =
                plugin.getSettingsConfig().getRoot().getConfigurationSection("features.spawn");
        boolean enabled = sec != null && sec.getBoolean("cooldown", false);
        int seconds     = (sec != null ? sec.getInt("cooldown-amount", 0) : 0);

        boolean bypassCooldown = p.isOp();

        final String requestId = UUID.randomUUID().toString();

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
                        "<gray>Sending you to <yellow>%server%</yellow> for spawn...</gray>",
                        Map.of("server", targetServer));
                log.info("[SpawnCmd] Proxy switch initiated (no cooldown"
                        + (bypassCooldown ? " / OP bypass" : "")
                        + "). player=" + p.getUniqueId() + " to=" + targetServer);
            } else {
                Lang.send(p, "spawn.switch-failed",
                        "<red>Failed to switch to server <yellow>%server%</yellow>.</red>",
                        Map.of("server", targetServer));
                log.warning("[SpawnCmd] Proxy switch failed to " + targetServer + " (check Velocity/Bungee server name match).");
            }
            return true;
        }

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

                if (hasBodyMoved(p, origin)) {
                    cancel();
                    Lang.send(p, "spawn.cancelled-moved",
                            "<red>Teleport cancelled: you moved.</red>");
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
                                "<gray>Sending you to <yellow>%server%</yellow> for spawn...</gray>",
                                Map.of("server", targetServer));
                        log.info("[SpawnCmd] Proxy switch initiated after countdown. player="
                                + p.getUniqueId() + " to=" + targetServer);
                    } else {
                        Lang.send(p, "spawn.switch-failed",
                                "<red>Failed to switch to server <yellow>%server%</yellow>.</red>",
                                Map.of("server", targetServer));
                        log.warning("[SpawnCmd] Proxy switch failed to " + targetServer
                                + " (check Velocity/Bungee server name match).");
                    }
                    return;
                }

                String title = Lang.msgWithDefault(
                        "teleport.countdown.title",
                        "<yellow>Teleporting...</yellow>",
                        p
                );

                String subtitle = Lang.msgWithDefault(
                        "teleport.countdown.subtitle",
                        "<gray>In <white>%seconds%</white>s...</gray>",
                        Map.of("seconds", String.valueOf(remain)),
                        p
                );

                p.sendTitle(title, subtitle, 0, 20, 0);
                remain--;
            }
        }.runTaskTimer(plugin, 0L, 20L);

        return true;
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
            log.info("[SpawnCmd] Sent plugin message 'Connect' to proxy. server=" + serverName);
            return true;
        } catch (Exception ex) {
            log.warning("[SpawnCmd] Failed to send Connect plugin message: " + ex.getMessage());
            return false;
        }
    }

    private boolean hasBodyMoved(Player p, Location origin) {
        Location now = p.getLocation();
        return !now.getWorld().equals(origin.getWorld())
                || now.getBlockX() != origin.getBlockX()
                || now.getBlockY() != origin.getBlockY()
                || now.getBlockZ() != origin.getBlockZ();
    }
}