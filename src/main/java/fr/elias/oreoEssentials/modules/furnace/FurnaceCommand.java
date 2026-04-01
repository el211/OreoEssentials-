package fr.elias.oreoEssentials.modules.furnace;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.modules.furnace.VirtualFurnaceListener;
import fr.elias.oreoEssentials.util.Lang;
import fr.elias.oreoEssentials.util.OreScheduler;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;

import java.util.List;

public class FurnaceCommand implements OreoCommand {
    private final OreoEssentials plugin;

    public FurnaceCommand(OreoEssentials plugin) {
        this.plugin = plugin;
    }

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
        OreScheduler.runForEntity(plugin, p, () -> p.openInventory(inv));

        VirtualFurnaceListener.startTask(plugin, p, inv);

        return true;
    }
}
