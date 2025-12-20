package fr.elias.oreoEssentials.commands.core.playercommands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel;
import fr.elias.oreoEssentials.rabbitmq.packet.PacketManager;
import fr.elias.oreoEssentials.rabbitmq.packet.impl.WarpTeleportRequestPacket;
import fr.elias.oreoEssentials.services.WarpDirectory;
import fr.elias.oreoEssentials.services.WarpService;
import fr.minuskube.inv.SmartInventory;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class WarpsAdminCommand implements OreoCommand {

    private final WarpService warps;

    public WarpsAdminCommand(WarpService warps) {
        this.warps = warps;
    }

    @Override public String name() { return "warpsadmin"; }
    @Override public List<String> aliases() { return List.of("warpsgui", "warpguiadmin"); } // renamed from /warpsgui
    @Override public String permission() { return "oreo.warps.admin"; }
    @Override public String usage() { return ""; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;

        SmartInventory.builder()
                .id("oreo:warps_admin")
                .provider(new WarpsAdminProvider(warps))
                .size(6, 9)
                .title(ChatColor.DARK_AQUA + "Warps (Admin)")
                .manager(OreoEssentials.get().getInvManager())
                .build()
                .open(p);

        return true;
    }

    /* ---------------- helpers for providers ---------------- */

    static boolean crossServerTeleport(WarpService warps, Player p, String warpName) {
        final OreoEssentials plugin = OreoEssentials.get();
        final String key = warpName.trim().toLowerCase(Locale.ROOT);

        final String localServer = plugin.getConfigService().serverName();
        final WarpDirectory dir = plugin.getWarpDirectory();
        String targetServer = (dir != null ? dir.getWarpServer(key) : localServer);
        if (targetServer == null || targetServer.isBlank()) targetServer = localServer;

        if (targetServer.equalsIgnoreCase(localServer)) {
            Location l = warps.getWarp(key);
            if (l == null) {
                p.sendMessage(ChatColor.RED + "Warp not found: " + ChatColor.YELLOW + warpName);
                return false;
            }
            p.teleport(l);
            p.sendMessage(ChatColor.GREEN + "Teleported to warp " + ChatColor.AQUA + warpName + ChatColor.GREEN + ".");
            return true;
        }

        var cs = plugin.getCrossServerSettings();
        if (!cs.warps()) {
            p.sendMessage(ChatColor.RED + "Cross-server warps are disabled by server config.");
            p.sendMessage(ChatColor.GRAY + "Use " + ChatColor.AQUA + "/server " + targetServer + ChatColor.GRAY
                    + " then " + ChatColor.AQUA + "/warp " + key);
            return false;
        }

        final PacketManager pm = plugin.getPacketManager();
        if (pm != null && pm.isInitialized()) {
            final String requestId = UUID.randomUUID().toString();
            WarpTeleportRequestPacket pkt = new WarpTeleportRequestPacket(p.getUniqueId(), key, targetServer, requestId);
            pm.sendPacket(PacketChannel.individual(targetServer), pkt);
        } else {
            p.sendMessage(ChatColor.RED + "Cross-server messaging is disabled. Ask an admin to enable RabbitMQ.");
            p.sendMessage(ChatColor.GRAY + "You can still /server " + targetServer + ChatColor.GRAY + " then /warp " + key);
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
            p.sendMessage(ChatColor.YELLOW + "Sending you to " + ChatColor.AQUA + serverName
                    + ChatColor.YELLOW + "… (teleport on arrival).");
            return true;
        } catch (Exception ex) {
            Bukkit.getLogger().warning("[OreoEssentials] Connect plugin message failed: " + ex.getMessage());
            p.sendMessage(ChatColor.RED + "Failed to switch you to " + serverName + ".");
            return false;
        }
    }
    /** Helper so other classes can reopen the admin GUI at a specific page. */
    public static void openAdmin(Player p, WarpService warps, int page) {
        SmartInventory inv = SmartInventory.builder()
                .id("oreo:warps_admin")
                .provider(new WarpsAdminProvider(warps))
                .size(6, 9)
                .title(ChatColor.DARK_AQUA + "Warps (Admin)")
                .manager(OreoEssentials.get().getInvManager())
                .build();
        inv.open(p, Math.max(0, page));
    }
}
