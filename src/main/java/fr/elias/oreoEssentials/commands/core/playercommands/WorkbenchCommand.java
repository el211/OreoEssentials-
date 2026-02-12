package fr.elias.oreoEssentials.commands.core.playercommands;

import fr.elias.oreoEssentials.commands.OreoCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class WorkbenchCommand implements OreoCommand {
    @Override public String name() { return "workbench"; }
    @Override public List<String> aliases() { return List.of("wb"); }
    @Override public String permission() { return "oreo.workbench"; }
    @Override public String usage() { return ""; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        p.openWorkbench(p.getLocation(), true);
        return true;
    }
}
