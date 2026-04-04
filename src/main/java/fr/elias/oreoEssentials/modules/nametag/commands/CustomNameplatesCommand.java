package fr.elias.oreoEssentials.modules.nametag.commands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * /customnameplates reload|help
 * Aliases: /cnp
 */
public final class CustomNameplatesCommand implements OreoCommand {

    private final OreoEssentials plugin;

    public CustomNameplatesCommand(OreoEssentials plugin) {
        this.plugin = plugin;
    }

    @Override public String name()          { return "customnameplates"; }
    @Override public List<String> aliases() { return List.of("cnp"); }
    @Override public String permission()    { return "oe.customnameplates.admin"; }
    @Override public String usage()         { return "reload|help"; }
    @Override public boolean playerOnly()   { return false; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            sender.sendMessage("§7[CustomNameplates] Reloading...");
            try {
                plugin.reloadCustomNameplates();
                sender.sendMessage("§a[CustomNameplates] Reloaded successfully.");
            } catch (Exception e) {
                sender.sendMessage("§c[CustomNameplates] Error during reload: " + e.getMessage());
                plugin.getLogger().severe("[CustomNameplates] Reload error: " + e.getMessage());
            }
            return true;
        }

        sendHelp(sender);
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        if (args.length == 1) return List.of("reload", "help");
        return List.of();
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6--- Custom Nameplates ---");
        sender.sendMessage("§6/cnp reload §7— Reload config without restart");
        sender.sendMessage("§7Config: §eplugins/OreoEssentials/custom-nameplates/config.yml");
    }
}
