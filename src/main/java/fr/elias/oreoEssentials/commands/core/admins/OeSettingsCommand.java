package fr.elias.oreoEssentials.commands.core.admins;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.config.menu.OeSettingsMenu;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class OeSettingsCommand implements OreoCommand {

    private final OreoEssentials plugin;

    public OeSettingsCommand(OreoEssentials plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "oesettings";
    }

    @Override
    public List<String> aliases() {
        return List.of("oeconfig", "oecfg");
    }

    @Override
    public String permission() {
        return "oreo.admin.settings";
    }

    @Override
    public String usage() {
        return "";
    }

    @Override
    public boolean playerOnly() {
        return true;
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return true;
        }

        if (!player.hasPermission("oreo.admin.settings")) {
            player.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }

        OeSettingsMenu.getInventory(plugin).open(player);
        return true;
    }
}