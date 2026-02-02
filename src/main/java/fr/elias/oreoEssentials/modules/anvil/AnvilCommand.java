package fr.elias.oreoEssentials.modules.anvil;

import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;

import java.util.List;

public class AnvilCommand implements OreoCommand {
    @Override public String name() { return "anvil"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.anvil"; }
    @Override public String usage() { return ""; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;

        String title = Lang.msgLegacy("anvil.title", "<dark_gray>Anvil</dark_gray>", p);

        Inventory anvil = Bukkit.createInventory(p, InventoryType.ANVIL, title);
        p.openInventory(anvil);
        return true;
    }
}