// File: src/main/java/fr/elias/oreoEssentials/commands/core/admins/SetSpawnCommand.java
package fr.elias.oreoEssentials.modules.spawn;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

public class SetSpawnCommand implements OreoCommand {
    private final SpawnService spawn;

    public SetSpawnCommand(SpawnService spawn) {
        this.spawn = spawn;
    }

    @Override public String name() { return "setspawn"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.setspawn"; }
    @Override public String usage() { return ""; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        Player p = (Player) sender;

        String local = OreoEssentials.get().getConfigService().serverName();

        spawn.setSpawn(local, p.getLocation());

        Lang.send(p, "admin.setspawn.set",
                "<green>Spawn set.</green>");

        SpawnDirectory spawnDir = OreoEssentials.get().getSpawnDirectory();
        if (spawnDir != null) {
            spawnDir.setSpawnServer(local);

            Lang.send(p, "admin.setspawn.cross-server-info",
                    "<gray>(Cross-server) Spawn owner set to <aqua>%server%</aqua>.</gray>",
                    Map.of("server", local));
        }

        return true;
    }

}