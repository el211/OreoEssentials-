package fr.elias.oreoEssentials.modules.orders.command;

import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.modules.orders.OrdersModule;
import fr.elias.oreoEssentials.modules.orders.gui.OrderBrowserMenu;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * /order [subcommand]
 * Aliases: /orders, /market
 *
 * Usage:
 *   /order                → opens the order browser GUI
 *   /order help           → shows usage message
 *   /order reload         → (admin) reloads module config
 */
public final class OrderCommand implements OreoCommand {

    private final OrdersModule module;

    public OrderCommand(OrdersModule module) {
        this.module = module;
    }

    @Override public String name()       { return "order"; }
    @Override public List<String> aliases() { return List.of("orders", "market"); }
    @Override public String permission() { return "oreo.orders.use"; }
    @Override public String usage()      { return "[help|reload]"; }
    @Override public boolean playerOnly(){ return false; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!module.enabled()) {
            sender.sendMessage("§cThe Orders/Market module is currently disabled.");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("oreo.orders.admin")) {
                sender.sendMessage("§cNo permission.");
                return true;
            }
            module.reload();
            sender.sendMessage("§a[Orders] Module reloaded.");
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command is player-only (no subcommand specified).");
            return true;
        }

        // Default: open browser
        OrderBrowserMenu.getInventory(module).open(player);
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("oreo.orders.admin"))
            return List.of("reload");
        return List.of();
    }
}
