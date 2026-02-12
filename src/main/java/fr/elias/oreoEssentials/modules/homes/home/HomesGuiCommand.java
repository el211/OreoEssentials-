package fr.elias.oreoEssentials.modules.homes.home;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel;
import fr.elias.oreoEssentials.rabbitmq.packet.PacketManager;
import fr.elias.oreoEssentials.modules.homes.rabbit.packet.HomeTeleportRequestPacket;
import fr.elias.oreoEssentials.util.Lang;
import fr.minuskube.inv.SmartInventory;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class HomesGuiCommand implements OreoCommand {

    private final HomeService homes;

    public HomesGuiCommand(HomeService homes) {
        this.homes = homes;
    }

    @Override public String name() { return "homesgui"; }
    @Override public List<String> aliases() { return List.of("homesmenu", "homegui"); }
    @Override public String permission() { return "oreo.homes.gui"; }
    @Override public String usage() { return ""; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;

        String title = Lang.msgLegacy("homes.gui.title",
                "<dark_green>Your Homes</dark_green>",
                p);

        SmartInventory inv = SmartInventory.builder()
                .id("oreo:homes")
                .provider(new HomesGuiProvider(homes))
                .size(6, 9)
                .title(title)
                .manager(OreoEssentials.get().getInvManager())
                .build();

        inv.open(p);
        return true;
    }


    static boolean crossServerTeleport(HomeService homes, Player p, String homeName) {
        final String key = homeName.toLowerCase();
        String targetServer = homes.homeServer(p.getUniqueId(), key);
        String localServer  = homes.localServer();
        if (targetServer == null) targetServer = localServer;

        if (targetServer.equalsIgnoreCase(localServer)) {
            var loc = homes.getHome(p.getUniqueId(), key);
            if (loc == null) {
                Lang.send(p, "homes.gui.not-found",
                        "<red>Home not found: <yellow>%name%</yellow></red>",
                        Map.of("name", homeName));
                return false;
            }
            p.teleport(loc);
            Lang.send(p, "homes.gui.teleported",
                    "<green>Teleported to <aqua>%name%</aqua>.</green>",
                    Map.of("name", homeName));
            return true;
        }

        var cs = OreoEssentials.get().getCrossServerSettings();
        if (!cs.homes()) {
            Lang.send(p, "homes.gui.cross-disabled",
                    "<red>Cross-server homes are disabled by server config.</red>");
            Lang.send(p, "homes.gui.cross-disabled-manual",
                    "<gray>Use <aqua>/server %server%</aqua> then <aqua>/home %name%</aqua>.</gray>",
                    Map.of("server", targetServer, "name", key));
            return false;
        }

        final var plugin = OreoEssentials.get();
        final PacketManager pm = plugin.getPacketManager();
        if (pm != null && pm.isInitialized()) {
            final String requestId = UUID.randomUUID().toString();
            HomeTeleportRequestPacket pkt = new HomeTeleportRequestPacket(p.getUniqueId(), key, targetServer, requestId);
            pm.sendPacket(PacketChannel.individual(targetServer), pkt);
        } else {
            Lang.send(p, "homes.gui.messaging-disabled",
                    "<red>Cross-server messaging is disabled. Ask an admin to enable RabbitMQ.</red>");
            Lang.send(p, "homes.gui.messaging-disabled-manual",
                    "<gray>You can still <aqua>/server %server%</aqua> then <aqua>/home %name%</aqua>.</gray>",
                    Map.of("server", targetServer, "name", key));
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

            Lang.send(p, "homes.gui.sending",
                    "<yellow>Sending you to <aqua>%server%</aqua>... (teleport on arrival).</yellow>",
                    Map.of("server", serverName));
            return true;
        } catch (Exception ex) {
            Bukkit.getLogger().warning("[OreoEssentials] Connect plugin message failed: " + ex.getMessage());
            Lang.send(p, "homes.gui.switch-failed",
                    "<red>Failed to switch you to %server%.</red>",
                    Map.of("server", serverName));
            return false;
        }
    }
}