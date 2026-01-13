package fr.elias.oreoEssentials.commands.core.playercommands;

import fr.elias.oreoEssentials.commands.OreoCommand;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class HatCommand implements OreoCommand {
    @Override public String name() { return "hat"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.hat"; }
    @Override public String usage() { return ""; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;

        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() == Material.AIR) {
            p.sendMessage(ChatColor.RED + "Hold an item to wear as a hat.");
            return true;
        }
        ItemStack old = p.getInventory().getHelmet();
        p.getInventory().setHelmet(hand.clone());
        if (old != null && old.getType() != Material.AIR) {
            p.getInventory().addItem(old);
        }
        if (hand.getAmount() > 1) {
            hand.setAmount(hand.getAmount() - 1);
            p.getInventory().setItemInMainHand(hand);
        } else {
            p.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        }

        p.sendMessage(ChatColor.GREEN + "Nice hat!");
        return true;
    }
}
