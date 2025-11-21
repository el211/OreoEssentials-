package fr.elias.oreoEssentials.commands.core.admins;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.playerdirectory.PlayerDirectory;
import fr.elias.oreoEssentials.services.InventoryService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

public class ClearCommand implements OreoCommand {

    @Override public String name() { return "clear"; }
    @Override public List<String> aliases() { return List.of("ci"); }
    @Override public String permission() { return "oreo.clear"; }
    @Override public String usage() { return "[player]"; }
    @Override public boolean playerOnly() { return false; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {

        // PlayerDirectory pour la résolution cross-server
        OreoEssentials plugin = OreoEssentials.get();
        PlayerDirectory directory = plugin.getPlayerDirectory();

        // InventoryService via Bukkit ServicesManager (comme onDisable())
        InventoryService invService = Bukkit.getServicesManager()
                .load(InventoryService.class);

        if (invService == null) {
            sender.sendMessage(ChatColor.RED + "InventoryService is not available; cannot clear persistent inventory.");
            // on peut quand même clear en live si le joueur est ici
        }

        // ---------- /clear (soi-même) ----------
        if (args.length == 0) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(ChatColor.RED + "Console must specify a player: /clear <player>");
                return true;
            }

            UUID uuid = p.getUniqueId();

            // 1) Clear inventaire live sur CE serveur
            clearLiveInventory(p);

            // 2) Clear persistant (Mongo/YAML) si service dispo
            if (invService != null) {
                clearPersistentInventory(invService, uuid);
                p.sendMessage(ChatColor.GREEN + "Your inventory has been cleared on all servers.");
            } else {
                p.sendMessage(ChatColor.GREEN + "Your in-game inventory has been cleared on this server.");
            }

            return true;
        }

        // ---------- /clear <player> ----------
        if (!sender.hasPermission("oreo.clear.others")) {
            sender.sendMessage(ChatColor.RED + "You lack permission: oreo.clear.others");
            return true;
        }

        String targetName = args[0];

        // 1) Résoudre cross-server via PlayerDirectory
        UUID targetUuid = directory.lookupUuidByName(targetName);
        if (targetUuid == null) {
            sender.sendMessage(ChatColor.RED + "Player '" + targetName + "' not found in directory.");
            return true;
        }

        // 2) Si le joueur est en ligne sur CE serveur, clear live
        Player online = Bukkit.getPlayer(targetUuid);
        if (online != null) {
            clearLiveInventory(online);

            if (invService != null) {
                online.sendMessage(ChatColor.YELLOW + "Your inventory was cleared by "
                        + sender.getName() + " on all servers.");
            } else {
                online.sendMessage(ChatColor.YELLOW + "Your inventory was cleared by "
                        + sender.getName() + " on this server.");
            }
        }

        // 3) Clear persistant (effet cross-server)
        if (invService != null) {
            clearPersistentInventory(invService, targetUuid);
            sender.sendMessage(ChatColor.GREEN + "Cleared " + targetName
                    + "'s inventory (persistent, all servers).");
        } else {
            sender.sendMessage(ChatColor.GREEN + "Cleared " + targetName
                    + "'s live inventory on this server (no persistent InventoryService).");
        }

        return true;
    }

    private void clearLiveInventory(Player p) {
        p.getInventory().clear();
        p.getInventory().setArmorContents(null);
        p.getInventory().setItemInOffHand(null);
        p.updateInventory();
    }

    private void clearPersistentInventory(InventoryService invService, UUID uuid) {
        InventoryService.Snapshot snap = new InventoryService.Snapshot();
        snap.contents = new ItemStack[41]; // 41 slots: tous null => inventaire vide
        snap.armor    = new ItemStack[4];  // 4 slots d'armure vides
        snap.offhand  = null;              // main secondaire vide

        invService.save(uuid, snap);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        // /clear <player>
        if (args.length != 1) {
            return List.of();
        }

        if (!sender.hasPermission("oreo.clear.others")) {
            return List.of();
        }

        String prefix = args[0];

        var plugin = OreoEssentials.get();
        var directory = plugin.getPlayerDirectory();

        var names = directory.suggestOnlineNames(prefix, 50);

        return names.stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }
}
