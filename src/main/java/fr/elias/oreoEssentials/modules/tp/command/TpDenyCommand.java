package fr.elias.oreoEssentials.modules.tp.command;



import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.modules.tp.service.TeleportService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class TpDenyCommand implements OreoCommand {
    private final TeleportService tpa;

    public TpDenyCommand(TeleportService tpa) { this.tpa = tpa; }

    @Override public String name() { return "tpdeny"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.tpa"; }
    @Override public String usage() { return ""; }
    @Override public boolean playerOnly() { return true; }

    @Override public boolean execute(CommandSender sender, String label, String[] args) {
        return tpa.deny((Player) sender);
    }
}
