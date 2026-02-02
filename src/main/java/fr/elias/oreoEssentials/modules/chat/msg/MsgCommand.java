package fr.elias.oreoEssentials.modules.chat.msg;

import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.services.MessageService;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

public class MsgCommand implements OreoCommand {
    private final MessageService messages;

    public MsgCommand(MessageService messages) {
        this.messages = messages;
    }

    @Override public String name() { return "msg"; }
    @Override public List<String> aliases() { return List.of("tell", "w"); }
    @Override public String permission() { return "oreo.msg"; }
    @Override public String usage() { return "<player> <message...>"; }
    @Override public boolean playerOnly() { return false; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        // Require at least player name and message
        if (args.length < 2) {
            Lang.send(sender, "msg.usage",
                    "<red>Usage: /%label% <player> <message...></red>",
                    Map.of("label", label));
            return true;
        }

        // Find target player
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            Lang.send(sender, "msg.not-found",
                    "<red>Player not found.</red>");
            return true;
        }

        // Build message from remaining args
        String msg = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));

        // Send to target: [MSG] SenderName: message
        Lang.send(target, "msg.receive",
                "<gray>[<light_purple>MSG</light_purple>] <aqua>%sender%</aqua>: <white>%message%</white></gray>",
                Map.of("sender", sender.getName(), "message", msg));

        // Send confirmation to sender: [MSG] -> TargetName: message
        Lang.send(sender, "msg.send",
                "<gray>[<light_purple>MSG</light_purple>] <white>-></white> <aqua>%target%</aqua>: <white>%message%</white></gray>",
                Map.of("target", target.getName(), "message", msg));

        // Record conversation for /reply
        if (sender instanceof Player s) {
            messages.record(s, target);
        }

        return true;
    }
}