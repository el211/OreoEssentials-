package fr.elias.oreoEssentials.modules.warps.commands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.modules.warps.WarpService;
import fr.elias.oreoEssentials.modules.warps.provider.WarpsPlayerProvider;
import fr.minuskube.inv.SmartInventory;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class WarpsCommand implements OreoCommand {

    private final WarpService warps;

    public WarpsCommand(WarpService warps) { this.warps = warps; }

    @Override public String name() { return "warps"; }
    @Override public List<String> aliases() { return List.of("warplist"); }
    @Override public String permission() { return "oreo.warps"; } // default true in plugin.yml
    @Override public String usage() { return ""; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;

        SmartInventory.builder()
                .id("oreo:warps_player")
                .provider(new WarpsPlayerProvider(warps))
                .size(6, 9)
                .title(ChatColor.DARK_AQUA + "Warps")
                .manager(OreoEssentials.get().getInvManager())
                .build()
                .open(p);

        return true;
    }
}
