// File: src/main/java/fr/elias/oreoEssentials/commands/core/playercommands/WarpsAdminCommand.java
package fr.elias.oreoEssentials.modules.warps.commands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.modules.warps.rabbit.WarpDirectory;
import fr.elias.oreoEssentials.modules.warps.WarpService;
import fr.elias.oreoEssentials.modules.warps.provider.WarpsAdminProvider;
import fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel;
import fr.elias.oreoEssentials.rabbitmq.packet.PacketManager;
import fr.elias.oreoEssentials.modules.warps.rabbit.packets.WarpTeleportRequestPacket;
import fr.elias.oreoEssentials.util.Lang;
import fr.elias.oreoEssentials.util.OreScheduler;
import fr.elias.oreoEssentials.util.OreTask;
import fr.minuskube.inv.SmartInventory;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class WarpsAdminCommand implements OreoCommand {

    private final WarpService warps;

    public WarpsAdminCommand(WarpService warps) {
        this.warps = warps;
    }

    @Override public String name() { return "warpsadmin"; }
    @Override public List<String> aliases() { return List.of("warpsgui", "warpguiadmin"); }
    @Override public String permission() { return "oreo.warps.admin"; }
    @Override public String usage() { return ""; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;

        String title = Lang.msgWithDefault(
                "warp.admin.list.title",
                "<dark_aqua>Warps (Admin)</dark_aqua>",
                p
        );

        SmartInventory.builder()
                .id("oreo:warps_admin")
                .provider(new WarpsAdminProvider(warps))
                .size(6, 9)
                .title(title)
                .manager(OreoEssentials.get().getInvManager())
                .build()
                .open(p);

        return true;
    }


    public static boolean crossServerTeleport(WarpService warps, Player p, String warpName) {
        final OreoEssentials plugin = OreoEssentials.get();
        final String key = warpName.trim().toLowerCase(Locale.ROOT);

        final String localServer = plugin.getConfigService().serverName();
        final WarpDirectory dir = plugin.getWarpDirectory();
        String targetServer = (dir != null ? dir.getWarpServer(key) : localServer);
        if (targetServer == null || targetServer.isBlank()) targetServer = localServer;

        if (targetServer.equalsIgnoreCase(localServer)) {
            Location l = warps.getWarp(key);
            if (l == null) {
                Lang.send(p, "warp.admin.not-found",
                        "<red>Warp not found: <yellow>%warp%</yellow></red>",
                        Map.of("warp", warpName));
                return false;
            }
            if (l.getWorld() == null) {
                Lang.send(p, "warp.admin.world-not-loaded",
                        "<red>Warp <yellow>%warp%</yellow> points to a world that is not loaded.</red>",
                        Map.of("warp", warpName));
                return false;
            }

            OreScheduler.runForEntity(plugin, p, () -> {
                Runnable doTeleport = () -> {
                    if (OreScheduler.isFolia()) {
                        p.teleportAsync(l).thenRun(() ->
                                Lang.send(p, "warp.admin.teleported",
                                        "<green>Teleported to warp <aqua>%warp%</aqua>.</green>",
                                        Map.of("warp", warpName)));
                    } else {
                        p.teleport(l);
                        Lang.send(p, "warp.admin.teleported",
                                "<green>Teleported to warp <aqua>%warp%</aqua>.</green>",
                                Map.of("warp", warpName));
                    }
                };

                // Apply cooldown when admin teleports themselves, unless they have bypass permission.
                if (p.hasPermission("oreo.warp.bypasscooldown")) {
                    doTeleport.run();
                } else {
                    FileConfiguration settings = plugin.getSettingsConfig().getRoot();
                    ConfigurationSection warpSection = settings.getConfigurationSection("features.warp");
                    boolean useCooldown = warpSection != null && warpSection.getBoolean("cooldown", false);
                    int seconds = (warpSection != null ? warpSection.getInt("cooldown-amount", 0) : 0);
                    if (useCooldown && seconds > 0) {
                        startCooldownThenTeleport(plugin, p, warpName, seconds, doTeleport);
                    } else {
                        doTeleport.run();
                    }
                }
            });
            return true;
        }

        var cs = plugin.getCrossServerSettings();
        if (!cs.warps()) {
            Lang.send(p, "warp.cross-disabled",
                    "<red>Cross-server warps are disabled by server config.</red>");
            Lang.send(p, "warp.cross-disabled-tip",
                    "<gray>Use <aqua>/server %server%</aqua> then run <aqua>/warp %warp%</aqua></gray>",
                    Map.of("server", targetServer, "warp", key));
            return false;
        }

        final PacketManager pm = plugin.getPacketManager();
        if (pm != null && pm.isInitialized()) {
            final String requestId = UUID.randomUUID().toString();
            WarpTeleportRequestPacket pkt = new WarpTeleportRequestPacket(p.getUniqueId(), key, targetServer, requestId);
            pm.sendPacket(PacketChannel.individual(targetServer), pkt);
        } else {
            Lang.send(p, "warp.messaging-disabled",
                    "<red>Cross-server messaging is disabled.</red>");
            Lang.send(p, "warp.messaging-disabled-tip",
                    "<gray>Use <aqua>/server %server%</aqua> then run <aqua>/warp %warp%</aqua></gray>",
                    Map.of("server", targetServer, "warp", key));
            return false;
        }

        return sendPlayerToServer(p, targetServer);
    }

    static boolean sendPlayerToServer(Player p, String serverName) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(b);
            out.writeUTF("Connect");
            out.writeUTF(serverName);
            p.sendPluginMessage(OreoEssentials.get(), "BungeeCord", b.toByteArray());

            Lang.send(p, "warp.admin.sending",
                    "<yellow>Sending you to <aqua>%server%</aqua>... (teleport on arrival).</yellow>",
                    Map.of("server", serverName));
            return true;
        } catch (Exception ex) {
            Bukkit.getLogger().warning("[OreoEssentials] Connect plugin message failed: " + ex.getMessage());
            Lang.send(p, "warp.admin.switch-failed",
                    "<red>Failed to switch you to %server%.</red>",
                    Map.of("server", serverName));
            return false;
        }
    }

    private static void startCooldownThenTeleport(OreoEssentials plugin, Player p, String warpName, int seconds, Runnable action) {
        final Location origin = p.getLocation().clone();
        final int[] remaining = {seconds};
        final OreTask[] taskHolder = {OreTask.EMPTY};

        taskHolder[0] = OreScheduler.runTimerForEntity(plugin, p, () -> {
            if (!p.isOnline()) {
                taskHolder[0].cancel();
                return;
            }
            Location now = p.getLocation();
            boolean moved = now.getWorld() == null || origin.getWorld() == null
                    || !now.getWorld().equals(origin.getWorld())
                    || now.getBlockX() != origin.getBlockX()
                    || now.getBlockY() != origin.getBlockY()
                    || now.getBlockZ() != origin.getBlockZ();
            if (moved) {
                taskHolder[0].cancel();
                Lang.send(p, "warp.cancelled-moved",
                        "<red>Teleport to warp <yellow>%warp%</yellow> cancelled: you moved.</red>",
                        Map.of("warp", warpName));
                return;
            }
            if (remaining[0] <= 0) {
                taskHolder[0].cancel();
                action.run();
                return;
            }
            String title = Lang.msgWithDefault("teleport.countdown.title", "<yellow>Teleporting...</yellow>", p);
            String subtitle = Lang.msgWithDefault(
                    "teleport.countdown.subtitle",
                    "<gray>In <white>%seconds%</white>s...</gray>",
                    Map.of("seconds", String.valueOf(remaining[0])),
                    p
            );
            p.sendTitle(title, subtitle, 0, 20, 0);
            remaining[0]--;
        }, 0L, 20L);
    }

    public static void openAdmin(Player p, WarpService warps, int page) {
        String title = Lang.msgWithDefault(
                "warp.admin.list.title",
                "<dark_aqua>Warps (Admin)</dark_aqua>",
                p
        );

        SmartInventory inv = SmartInventory.builder()
                .id("oreo:warps_admin")
                .provider(new WarpsAdminProvider(warps))
                .size(6, 9)
                .title(title)
                .manager(OreoEssentials.get().getInvManager())
                .build();
        inv.open(p, Math.max(0, page));
    }
}
