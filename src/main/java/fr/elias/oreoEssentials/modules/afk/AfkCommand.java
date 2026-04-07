package fr.elias.oreoEssentials.modules.afk;

import fr.elias.oreoEssentials.commands.OreoCommand;
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
        // Messages are sent inside AfkService.setAfk() (supports custom per-permission messages)
        afk.toggleAfk(p);
        return true;
    }
}