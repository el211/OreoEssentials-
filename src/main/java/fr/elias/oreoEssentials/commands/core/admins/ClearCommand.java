// File: src/main/java/fr/elias/oreoEssentials/commands/core/admins/ClearCommand.java
package fr.elias.oreoEssentials.commands.core.admins;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.playerdirectory.PlayerDirectory;
import fr.elias.oreoEssentials.services.InventoryService;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ClearCommand implements OreoCommand {

    @Override public String name() { return "clear"; }
    @Override public List<String> aliases() { return List.of("ci"); }
    @Override public String permission() { return "oreo.clear"; }
    @Override public String usage() { return "[player]"; }
    @Override public boolean playerOnly() { return false; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {

        OreoEssentials plugin = OreoEssentials.get();
        PlayerDirectory directory = plugin.getPlayerDirectory();

        // InventoryService via Bukkit ServicesManager
        InventoryService invService = Bukkit.getServicesManager()
                .load(InventoryService.class);

        if (invService == null) {
            // Info to staff: no persistent clear available
            Lang.send(sender,
                    "admin.clear.no-service",
                    "<yellow>Note: Inventory service unavailable - clearing local only.</yellow>");
        }

        // ---------- /clear (self) ----------
        if (args.length == 0) {
            if (!(sender instanceof Player p)) {
                // Console must specify a player
                Lang.send(sender,
                        "admin.clear.console-usage",
                        "<red>Usage: /%label% <player></red>",
                        Map.of("label", label));
                return true;
            }

            UUID uuid = p.getUniqueId();

            // 1) Clear live inventory on THIS server
            clearLiveInventory(p);

            // 2) Clear persistent (Mongo/YAML) if service available
            if (invService != null) {
                clearPersistentInventory(invService, uuid);
                Lang.send(p,
                        "admin.clear.self.all-servers",
                        "<green>Inventory cleared on all servers.</green>");
            } else {
                Lang.send(p,
                        "admin.clear.self.this-server",
                        "<green>Inventory cleared on this server.</green>");
            }

            return true;
        }

        // ---------- /clear <player> ----------
        if (!sender.hasPermission("oreo.clear.others")) {
            Lang.send(sender,
                    "admin.clear.others-no-permission",
                    "<red>You don't have permission to clear other players' inventories.</red>");
            return true;
        }

        String targetName = args[0];

        // 1) Resolve cross-server via PlayerDirectory
        UUID targetUuid = directory.lookupUuidByName(targetName);
        if (targetUuid == null) {
            Lang.send(sender,
                    "admin.clear.target-not-found",
                    "<red>Player not found: <yellow>%target%</yellow></red>",
                    Map.of("target", targetName));
            return true;
        }

        // 2) If player is online on THIS server, clear live
        Player online = Bukkit.getPlayer(targetUuid);
        if (online != null) {
            clearLiveInventory(online);

            if (invService != null) {
                // Notify player: clear all servers
                Lang.send(online,
                        "admin.clear.target-notified-all",
                        "<yellow>Your inventory was cleared on all servers by <aqua>%player%</aqua>.</yellow>",
                        Map.of("player", sender.getName()));
            } else {
                // Notify player: clear local only
                Lang.send(online,
                        "admin.clear.target-notified-local",
                        "<yellow>Your inventory was cleared on this server by <aqua>%player%</aqua>.</yellow>",
                        Map.of("player", sender.getName()));
            }
        }

        // 3) Clear persistent (cross-server effect)
        if (invService != null) {
            clearPersistentInventory(invService, targetUuid);
            Lang.send(sender,
                    "admin.clear.sender-confirm-all",
                    "<green>Cleared <aqua>%target%</aqua>'s inventory on all servers.</green>",
                    Map.of("target", targetName));
        } else {
            Lang.send(sender,
                    "admin.clear.sender-confirm-local",
                    "<green>Cleared <aqua>%target%</aqua>'s inventory on this server.</green>",
                    Map.of("target", targetName));
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
        snap.contents = new ItemStack[41]; // 41 slots: all null => empty inventory
        snap.armor = new ItemStack[4];     // 4 armor slots empty
        snap.offhand = null;               // offhand empty

        invService.save(uuid, snap);
    }
}