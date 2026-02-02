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
import fr.minuskube.inv.SmartInventory;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
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

            p.teleport(l);
            Lang.send(p, "warp.admin.teleported",
                    "<green>Teleported to warp <aqua>%warp%</aqua>.</green>",
                    Map.of("warp", warpName));
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