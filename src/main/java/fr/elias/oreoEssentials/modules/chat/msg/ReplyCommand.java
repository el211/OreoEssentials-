package fr.elias.oreoEssentials.modules.chat.msg;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel;
import fr.elias.oreoEssentials.rabbitmq.packet.PacketManager;
import fr.elias.oreoEssentials.services.MessageService;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ReplyCommand implements OreoCommand {
    private final MessageService messages;
    private final OreoEssentials plugin;

    public ReplyCommand(MessageService messages, OreoEssentials plugin) {
        this.messages = messages;
        this.plugin = plugin;
    }

    @Override public String name() { return "r"; }
    @Override public List<String> aliases() { return List.of("reply"); }
    @Override public String permission() { return "oreo.msg"; }
    @Override public String usage() { return "<message...>"; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length < 1) {
            Lang.send(sender, "reply.usage",
                    "<red>Usage: /%label% <message...></red>",
                    Map.of("label", label));
            return true;
        }

        Player p = (Player) sender;

        UUID lastUuid = messages.getLast(p.getUniqueId());
        if (lastUuid == null) {
            Lang.send(p, "reply.no-one", "<red>No one to reply to.</red>");
            return true;
        }

        String msg = String.join(" ", args);

        // Try local player first
        Player target = Bukkit.getPlayer(lastUuid);
        if (target != null && target.isOnline()) {
            Lang.send(target, "msg.receive",
                    "<gray>[<light_purple>MSG</light_purple>] <aqua>%sender%</aqua>: <white>%message%</white></gray>",
                    Map.of("sender", p.getName(), "message", msg));

            Lang.send(p, "msg.send",
                    "<gray>[<light_purple>MSG</light_purple>] <white>-></white> <aqua>%target%</aqua>: <white>%message%</white></gray>",
                    Map.of("target", target.getName(), "message", msg));

            messages.record(p, target);
            return true;
        }

        // Try cross-server
        PacketManager pm = plugin.getPacketManager();
        var dir = plugin.getPlayerDirectory();
        if (pm == null || !pm.isInitialized() || dir == null) {
            Lang.send(p, "reply.offline", "<red>That player is offline.</red>");
            return true;
        }

        final UUID targetUuid = lastUuid;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String where = dir.getCurrentOrLastServer(targetUuid);
                if (where == null || where.isBlank()) {
                    Bukkit.getScheduler().runTask(plugin, () ->
                            Lang.send(p, "reply.offline", "<red>That player is offline.</red>"));
                    return;
                }

                String resolvedName = dir.lookupNameByUuid(targetUuid);
                if (resolvedName == null) resolvedName = targetUuid.toString();
                final String finalName = resolvedName;

                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        pm.sendPacket(PacketChannel.individual(where),
                                new CrossServerMsgPacket(p.getUniqueId(), p.getName(), targetUuid, msg));

                        Lang.send(p, "msg.send",
                                "<gray>[<light_purple>MSG</light_purple>] <white>-></white> <aqua>%target%</aqua>: <white>%message%</white></gray>",
                                Map.of("target", finalName, "message", msg));

                        messages.record(p.getUniqueId(), targetUuid);
                    } catch (Exception e) {
                        Lang.send(p, "reply.offline", "<red>Could not reach player on remote server.</red>");
                    }
                });
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        Lang.send(p, "reply.offline", "<red>That player is offline.</red>"));
            }
        });

        return true;
    }
}
