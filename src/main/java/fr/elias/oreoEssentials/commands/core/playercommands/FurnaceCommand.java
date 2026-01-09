package fr.elias.oreoEssentials.commands.core.playercommands;

import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;

import java.util.List;

public class FurnaceCommand implements OreoCommand {
    @Override public String name() { return "furnace"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.furnace"; }
    @Override public String usage() { return ""; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;

        String title = Lang.msgLegacy("furnace.title", "<dark_gray>Furnace</dark_gray>", p);

        Inventory inv = Bukkit.createInventory(p, InventoryType.FURNACE, title);
        p.openInventory(inv);

        return true;
    }
}