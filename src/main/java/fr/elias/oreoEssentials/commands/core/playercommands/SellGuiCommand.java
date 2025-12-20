package fr.elias.oreoEssentials.commands.core.playercommands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class SellGuiCommand implements OreoCommand {

    private final OreoEssentials plugin;

    public SellGuiCommand(OreoEssentials plugin) {
        this.plugin = plugin;
    }

    @Override public String name() { return "sellgui"; }
    @Override public List<String> aliases() { return List.of("sell"); }

    @Override public String permission() { return "oreo.sellgui"; }
    @Override public String usage() { return "/sellgui"; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;

        if (plugin.getSellGuiManager() == null) {
            sender.sendMessage("SellGUI is not initialized.");
            return true;
        }

        plugin.getSellGuiManager().openSell(p);
        return true;
    }
}
