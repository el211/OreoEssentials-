package fr.elias.oreoEssentials.modules.afk;

import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class AfkCommand implements OreoCommand {
    private final AfkService afk;

    public AfkCommand(AfkService afk) { this.afk = afk; }

    @Override public String name() { return "afk"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.afk"; }
    @Override public String usage() { return ""; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;

        boolean nowAfk = afk.toggleAfk(p);
        if (nowAfk) {
            Lang.send(p, "afk.now-afk", "<yellow>You are now AFK.</yellow>");
        } else {
            Lang.send(p, "afk.no-longer-afk", "<green>You are no longer AFK.</green>");
        }
        return true;
    }
}