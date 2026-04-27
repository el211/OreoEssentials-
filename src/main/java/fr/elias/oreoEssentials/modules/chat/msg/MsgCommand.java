package fr.elias.oreoEssentials.modules.chat.msg;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.rabbitmq.PacketChannels;
import fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel;
import fr.elias.oreoEssentials.rabbitmq.packet.PacketManager;
import fr.elias.oreoEssentials.services.MessageService;
import fr.elias.oreoEssentials.util.Lang;
import fr.elias.oreoEssentials.util.OreScheduler;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MsgCommand implements OreoCommand {
    private final MessageService messages;
    private final OreoEssentials plugin;

    public MsgCommand(MessageService messages, OreoEssentials plugin) {
        this.messages = messages;
        this.plugin = plugin;
    }

    @Override public String name() { return "msg"; }
    @Override public List<String> aliases() { return List.of("tell", "w"); }
    @Override public String permission() { return "oreo.msg"; }
    @Override public String usage() { return "<player> <message...>"; }
    @Override public boolean playerOnly() { return false; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            Lang.send(sender, "msg.usage",
                    "<red>Usage: /%label% <player> <message...></red>",
                    Map.of("label", label));
            return true;
        }

        String targetName = args[0];
        String msg = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

        // Try local first
        Player target = Bukkit.getPlayerExact(targetName);
        if (target != null) {
            sendLocal(sender, target, msg);
            return true;
        }

        // Try cross-server via PlayerDirectory
        if (sender instanceof Player senderPlayer) {
            tryCrossServer(senderPlayer, targetName, msg);
        } else {
            Lang.send(sender, "msg.not-found", "<red>Player not found.</red>");
        }
        return true;
    }

    private void sendLocal(CommandSender sender, Player target, String msg) {
        Lang.send(target, "msg.receive",
                "<gray>[<light_purple>MSG</light_purple>] <aqua>%sender%</aqua>: <white>%message%</white></gray>",
                Map.of("sender", sender.getName(), "message", msg));

        Lang.send(sender, "msg.send",
                "<gray>[<light_purple>MSG</light_purple>] <white>-></white> <aqua>%target%</aqua>: <white>%message%</white></gray>",
                Map.of("target", target.getName(), "message", msg));

        if (sender instanceof Player s) {
            messages.record(s, target);
        }
    }

    private void tryCrossServer(Player sender, String targetName, String msg) {
        var dir = plugin.getPlayerDirectory();
        if (dir == null) {
            Lang.send(sender, "msg.not-found", "<red>Player not found.</red>");
            return;
        }

        PacketManager pm = plugin.getPacketManager();
        if (pm == null || !pm.isInitialized()) {
            Lang.send(sender, "msg.not-found", "<red>Player not found.</red>");
            return;
        }

        // Run directory lookup async to avoid blocking main thread
        OreScheduler.runAsync(plugin, () -> {
            try {
                UUID targetUuid = dir.lookupUuidByName(targetName);
                if (targetUuid == null) {
                    OreScheduler.runForEntity(plugin, sender, () ->
                            Lang.send(sender, "msg.not-found", "<red>Player not found.</red>"));
                    return;
                }

                String resolvedName = dir.lookupNameByUuid(targetUuid);
                if (resolvedName == null) resolvedName = targetName;
                final String finalName = resolvedName;

                String where = dir.lookupCurrentServer(targetUuid);
                if (where == null || where.isBlank()) {
                    OreScheduler.runForEntity(plugin, sender, () ->
                            Lang.send(sender, "msg.not-found", "<red>Player not found or offline.</red>"));
                    return;
                }

                final UUID finalUuid = targetUuid;
                OreScheduler.runForEntity(plugin, sender, () -> {
                    try {
                        pm.sendPacket(PacketChannel.individual(where),
                                new CrossServerMsgPacket(sender.getUniqueId(), sender.getName(), finalUuid, msg));

                        Lang.send(sender, "msg.send",
                                "<gray>[<light_purple>MSG</light_purple>] <white>-></white> <aqua>%target%</aqua>: <white>%message%</white></gray>",
                                Map.of("target", finalName, "message", msg));

                        // Record locally so sender can /reply
                        messages.record(sender.getUniqueId(), finalUuid);
                    } catch (Exception e) {
                        Lang.send(sender, "msg.not-found", "<red>Could not reach player on remote server.</red>");
                    }
                });
            } catch (Exception e) {
                OreScheduler.runForEntity(plugin, sender, () ->
                        Lang.send(sender, "msg.not-found", "<red>Player not found.</red>"));
            }
        });
    }
}
