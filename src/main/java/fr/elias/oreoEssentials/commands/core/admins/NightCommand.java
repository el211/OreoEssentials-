package fr.elias.oreoEssentials.commands.core.admins;

import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class NightCommand implements OreoCommand {

    @Override
    public String name() {
        return "night";
    }

    @Override
    public List<String> aliases() {
        return List.of();
    }

    @Override
    public String permission() {
        return "oreo.night";
    }

    @Override
    public String usage() {
        return "";
    }

    @Override
    public boolean playerOnly() {
        return false;
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (sender instanceof Player p) {
            p.getWorld().setTime(13000L);
        } else {
            org.bukkit.Bukkit.getWorlds().forEach(w -> w.setTime(13000L));
        }
        Lang.send(sender, "time.night", "<dark_blue>Time set to <blue>night</blue>.</dark_blue>");
        return true;
    }
}
