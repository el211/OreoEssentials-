package fr.elias.oreoEssentials.modules.deathback;

import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class DeathBackCommand implements OreoCommand {
    private final DeathBackService deathBack;

    public DeathBackCommand(DeathBackService deathBack) {
        this.deathBack = deathBack;
    }

    @Override public String name() { return "deathback"; }
    @Override public List<String> aliases() { return List.of("backdeath", "db"); }
    @Override public String permission() { return "oreo.deathback"; }
    @Override public String usage() { return ""; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        Player p = (Player) sender;

        Location loc = deathBack.getLastDeath(p.getUniqueId());
        if (loc == null) {
            Lang.send(p, "deathback.no-location",
                    "<red>No death location stored.</red>");
            return true;
        }

        boolean ok = p.teleport(loc);
        if (ok) {
            Lang.send(p, "deathback.success",
                    "<green>Teleported to your last death.</green>");

        } else {
            Lang.send(p, "deathback.failed",
                    "<red>Teleport failed.</red>");
        }

        return true;
    }
}