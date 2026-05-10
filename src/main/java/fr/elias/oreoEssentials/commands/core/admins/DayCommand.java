package fr.elias.oreoEssentials.commands.core.admins;

import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class DayCommand implements OreoCommand {

    @Override
    public String name() {
        return "day";
    }

    @Override
    public List<String> aliases() {
        return List.of();
    }

    @Override
    public String permission() {
        return "oreo.day";
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
            p.getWorld().setTime(1000L);
        } else {
            org.bukkit.Bukkit.getWorlds().forEach(w -> w.setTime(1000L));
        }
        Lang.send(sender, "time.day", "<yellow>Time set to <gold>day</gold>.</yellow>");
        return true;
    }
}
