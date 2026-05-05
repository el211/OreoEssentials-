package fr.elias.oreoEssentials.modules.spawn;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.util.Async;
import fr.elias.oreoEssentials.util.Lang;
import fr.elias.oreoEssentials.util.OreScheduler;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * /setfirstspawn — saves a separate spawn location used only for players
 * joining the server for the very first time.
 *
 * Falls back to the regular spawn if no first-join spawn has been set.
 * Permission: oreo.setfirstspawn (default: op)
 */
public class SetFirstSpawnCommand implements OreoCommand {

    private final SpawnService spawn;

    public SetFirstSpawnCommand(SpawnService spawn) {
        this.spawn = spawn;
    }

    @Override public String       name()       { return "setfirstspawn"; }
    @Override public List<String> aliases()    { return List.of(); }
    @Override public String       permission() { return "oreo.setfirstspawn"; }
    @Override public String       usage()      { return ""; }
    @Override public boolean      playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        Player p = (Player) sender;
        OreoEssentials plugin = OreoEssentials.get();
        org.bukkit.Location loc = p.getLocation();

        Async.run(() -> {
            spawn.setFirstJoinSpawn(loc);
            OreScheduler.runForEntity(plugin, p, () ->
                    Lang.send(p, "admin.setfirstspawn.set",
                            "<green>First-join spawn set at your current location.</green>")
            );
        });

        return true;
    }
}
