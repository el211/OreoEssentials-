package fr.elias.oreoEssentials.modules.homes.home;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.config.ConfigService;
import fr.elias.oreoEssentials.util.Async;
import fr.elias.oreoEssentials.util.Lang;
import fr.elias.oreoEssentials.util.OreScheduler;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SetHomeCommand implements OreoCommand {
    private final HomeService homes;
    private final ConfigService config;

    public SetHomeCommand(HomeService homes, ConfigService config) {
        this.homes = homes;
        this.config = config;
    }

    @Override public String name() { return "sethome"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.sethome"; }
    @Override public String usage() { return "<name>"; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length < 1) return false;

        Player p = (Player) sender;
        String rawName = args[0];
        String key = rawName.toLowerCase(Locale.ROOT);
        Location loc = p.getLocation();

        // Capture max homes on entity thread before going async.
        int max = config.getMaxHomesFor(p);

        Async.run(() -> {
            boolean ok = homes.setHome(p, rawName, loc);
            OreoEssentials plugin = OreoEssentials.get();
            OreScheduler.runForEntity(plugin, p, () -> {
                if (!ok) {
                    Lang.send(p, "sethome.limit",
                            "<red>You've reached your home limit of <yellow>%max%</yellow>.</red>",
                            Map.of("max", String.valueOf(max)));
                } else {
                    Lang.send(p, "sethome.set",
                            "<green>Home <yellow>%name%</yellow> has been set.</green>",
                            Map.of("name", key));
                }
            });
        });

        return true;
    }
}