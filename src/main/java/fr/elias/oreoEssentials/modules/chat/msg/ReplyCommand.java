package fr.elias.oreoEssentials.modules.chat.msg;

import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.services.MessageService;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

public class ReplyCommand implements OreoCommand {
    private final MessageService messages;

    public ReplyCommand(MessageService messages) {
        this.messages = messages;
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

        var last = messages.getLast(p.getUniqueId());
        if (last == null) {
            Lang.send(p, "reply.no-one",
                    "<red>No one to reply to.</red>");
            return true;
        }

        Player target = Bukkit.getPlayer(last);
        if (target == null) {
            Lang.send(p, "reply.offline",
                    "<red>That player is offline.</red>");
            return true;
        }

        String msg = String.join(" ", args);

        Lang.send(target, "msg.receive",
                "<gray>[<light_purple>MSG</light_purple>] <aqua>%sender%</aqua>: <white>%message%</white></gray>",
                Map.of("sender", p.getName(), "message", msg));

        Lang.send(p, "msg.send",
                "<gray>[<light_purple>MSG</light_purple>] <white>-></white> <aqua>%target%</aqua>: <white>%message%</white></gray>",
                Map.of("target", target.getName(), "message", msg));

        messages.record(p, target);

        return true;
    }
}