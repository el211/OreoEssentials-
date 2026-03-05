package fr.elias.oreoEssentials.modules.auctionhouse.commands;

import fr.elias.oreoEssentials.modules.auctionhouse.AuctionHouseModule;
import fr.elias.oreoEssentials.modules.auctionhouse.gui.CurrencyPickerGUI;
import fr.elias.oreoEssentials.modules.auctionhouse.gui.SellGUI;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public final class SellCommand implements CommandExecutor {

    private final AuctionHouseModule module;

    public SellCommand(AuctionHouseModule module) { this.module = module; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return true;
        }
        if (!module.enabled()) {
            p.sendMessage("§cThe auction house is currently disabled.");
            return true;
        }
        if (!p.hasPermission("oreo.ah.sell")) {
            p.sendMessage(module.getConfig().getMessage("errors.no-permission"));
            return true;
        }
        ItemStack item = p.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR) {
            p.sendMessage(module.getConfig().getMessage("errors.no-item-in-hand"));
            return true;
        }

        long duration = module.getConfig().defaultDurationHours();
        if (args.length >= 2) {
            try { duration = Long.parseLong(args[1]); }
            catch (NumberFormatException e) { p.sendMessage("§cInvalid duration."); return true; }
        }

        // No price argument → open currency picker; player types price in chat after selecting.
        if (args.length == 0) {
            CurrencyPickerGUI.getInventory(module, item, duration).open(p);
            return true;
        }

        // /ahs <price> [duration] → skip picker, use Vault directly (legacy behaviour).
        double price;
        try { price = Double.parseDouble(args[0]); }
        catch (NumberFormatException e) { p.sendMessage("§cInvalid price."); return true; }

        SellGUI.getInventory(module, item, price, duration).open(p);
        return true;
    }
}