package fr.elias.oreoEssentials.commands.core.playercommands;

import fr.elias.oreoEssentials.commands.OreoCommand;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.List;

public class TrashCommand implements OreoCommand {
    @Override public String name() { return "trash"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.trash"; }
    @Override public String usage() { return ""; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        Inventory inv = Bukkit.createInventory(p, 27, "§8Trash");
        p.openInventory(inv);
        p.sendMessage(ChatColor.GRAY + "This inventory will not be saved.");
        return true;
    }
}
