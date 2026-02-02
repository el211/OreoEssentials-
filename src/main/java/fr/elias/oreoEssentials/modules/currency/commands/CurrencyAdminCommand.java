package fr.elias.oreoEssentials.modules.currency.commands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.modules.currency.gui.CurrencyAdminGUI;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

/**
 * Command to open the currency admin GUI
 * Usage: /currencyadmin or /cadmin
 */
public class CurrencyAdminCommand implements OreoCommand {

    private final OreoEssentials plugin;

    public CurrencyAdminCommand(OreoEssentials plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "currencyadmin";
    }

    @Override
    public List<String> aliases() {
        return Arrays.asList("cadmin", "currencygui");
    }

    @Override
    public String permission() {
        return "oreo.currency.admin";
    }

    @Override
    public String usage() {
        return "";
    }

    @Override
    public boolean playerOnly() {
        return true; // GUI requires a player
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        Player player = (Player) sender;

        CurrencyAdminGUI.getInventory(plugin).open(player);

        return true;
    }
}