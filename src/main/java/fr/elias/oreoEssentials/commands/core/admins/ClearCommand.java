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

        // InventoryService via Bukkit ServicesManager (comme onDisable())
        InventoryService invService = Bukkit.getServicesManager()
                .load(InventoryService.class);

        if (invService == null) {
            // Info au staff: pas de clear persistant
            Lang.send(sender,
                    "admin.clear.no-service",
                    null,
                    null
            );
            // on peut quand même clear en live si le joueur est ici
        }

        // ---------- /clear (soi-même) ----------
        if (args.length == 0) {
            if (!(sender instanceof Player p)) {
                // Console doit préciser un joueur
                Lang.send(sender,
                        "admin.clear.console-usage",
                        null,
                        Map.of("label", label)
                );
                return true;
            }

            UUID uuid = p.getUniqueId();

            // 1) Clear inventaire live sur CE serveur
            clearLiveInventory(p);

            // 2) Clear persistant (Mongo/YAML) si service dispo
            if (invService != null) {
                clearPersistentInventory(invService, uuid);
                Lang.send(p,
                        "admin.clear.self.all-servers",
                        null,
                        null
                );
            } else {
                Lang.send(p,
                        "admin.clear.self.this-server",
                        null,
                        null
                );
            }

            return true;
        }

        // ---------- /clear <player> ----------
        if (!sender.hasPermission("oreo.clear.others")) {
            Lang.send(sender,
                    "admin.clear.others-no-permission",
                    null,
                    null
            );
            return true;
        }

        String targetName = args[0];

        // 1) Résoudre cross-server via PlayerDirectory
        UUID targetUuid = directory.lookupUuidByName(targetName);
        if (targetUuid == null) {
            Lang.send(sender,
                    "admin.clear.target-not-found",
                    null,
                    Map.of("target", targetName)
            );
            return true;
        }

        // 2) Si le joueur est en ligne sur CE serveur, clear live
        Player online = Bukkit.getPlayer(targetUuid);
        if (online != null) {
            clearLiveInventory(online);

            if (invService != null) {
                // Notifier le joueur: clear all servers
                Lang.send(online,
                        "admin.clear.target-notified-all",
                        null,
                        Map.of("player", sender.getName())
                );
            } else {
                // Notifier le joueur: clear local uniquement
                Lang.send(online,
                        "admin.clear.target-notified-local",
                        null,
                        Map.of("player", sender.getName())
                );
            }
        }

        // 3) Clear persistant (effet cross-server)
        if (invService != null) {
            clearPersistentInventory(invService, targetUuid);
            Lang.send(sender,
                    "admin.clear.sender-confirm-all",
                    null,
                    Map.of("target", targetName)
            );
        } else {
            Lang.send(sender,
                    "admin.clear.sender-confirm-local",
                    null,
                    Map.of("target", targetName)
            );
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
}
