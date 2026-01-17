package fr.elias.oreoEssentials.cross;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.services.InventoryService;
import fr.elias.oreoEssentials.trade.ItemStacksCodec;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InvseeService {

    private final OreoEssentials plugin;
    private final InvseeCrossServerBroker broker;

    /**
     * Persistance / snapshots offline (Mongo ou YAML) exposés
     * via InventoryService, enregistré dans OreoEssentials.onEnable().
     */
    private final InventoryService invStorage;

    private final Map<UUID, InvseeSession> sessions = new ConcurrentHashMap<>();

    public InvseeService(OreoEssentials plugin, InvseeCrossServerBroker broker) {
        this.plugin = plugin;
        this.broker = broker;
        this.invStorage = Bukkit.getServicesManager().load(InventoryService.class);

        if (this.invStorage == null) {
            plugin.getLogger().warning("[INVSEE] InventoryService not found; offline snapshot support disabled.");
        } else {
            plugin.getLogger().info("[INVSEE] InventoryService hook OK (offline / persisted snapshots available).");
        }
    }

    public InvseeCrossServerBroker getBroker() {
        return broker;
    }



    /**
     * Appelé côté OWNER quand ce nœud reçoit un InvseeOpenRequestPacket
     * (le PlayerDirectory dit que ce serveur "possède" le joueur).
     */
    public void handleRemoteOpen(InvseeOpenRequestPacket p) {
        if (p == null) return;

        Player target = Bukkit.getPlayer(p.getTargetId());
        if (target == null || !target.isOnline()) {
            plugin.getLogger().info("[INVSEE] handleRemoteOpen: target not online here: " + p.getTargetId());
            return;
        }




        ItemStack[] snap = target.getInventory().getContents();
        broker.sendStateToViewer(p.getViewerId(), target.getUniqueId(), snap);

        plugin.getLogger().info("[INVSEE] handleRemoteOpen: sent initial state to viewer="
                + p.getViewerName() + " for target=" + target.getName());
    }


    /**
     * OWNER node; applique une édition d'un viewer distant sur l'inventaire réel.
     */
    public void applyRemoteEdit(InvseeEditPacket p) {
        if (p == null) return;

        Player target = Bukkit.getPlayer(p.getTargetId());
        if (target == null || !target.isOnline()) {
            plugin.getLogger().info("[INVSEE] applyRemoteEdit: target not online here: " + p.getTargetId());
            return;
        }

        ItemStack[] decoded = ItemStacksCodec.decodeFromBytes(p.getItemBytes());
        ItemStack newItem = (decoded.length > 0 ? decoded[0] : null);

        target.getInventory().setItem(p.getSlot(), newItem);
        target.updateInventory();

        // Sauvegarde dans le storage persistant si dispo
        if (invStorage != null) {
            try {
                InventoryService.Snapshot snap = snapshotFromOnline(target);
                invStorage.save(target.getUniqueId(), snap);
            } catch (Exception e) {
                plugin.getLogger().warning("[INVSEE] applyRemoteEdit: failed to save snapshot: " + e.getMessage());
            }
        }

        // Optionnel : renvoyer l'état complet pour garder le viewer bien synchro
        broker.sendStateToViewer(p.getViewerId(), p.getTargetId(), target.getInventory().getContents());

        plugin.getLogger().info("[INVSEE] applyRemoteEdit: slot=" + p.getSlot()
                + " updated for targetId=" + p.getTargetId());
    }

    /**
     * VIEWER node; reçoit un snapshot depuis l'owner et met à jour le GUI / session.
     */
    public void applyRemoteState(InvseeStatePacket p) {
        if (p == null) return;

        ItemStack[] contents = ItemStacksCodec.decodeFromBytes(p.getContentsBytes());

        InvseeSession sess = sessions.get(p.getViewerId());
        if (sess == null) {
            plugin.getLogger().fine("[INVSEE] applyRemoteState: no session for viewerId="
                    + p.getViewerId() + " (GUI probably closed)");
            return;
        }

        sess.setLastSnapshot(contents);

        Player viewer = Bukkit.getPlayer(p.getViewerId());
        if (viewer != null && viewer.isOnline()) {
            InvseeMenu menu = sess.getMenu();
            if (menu != null) {
                menu.refreshFromSession(sess); // you can leave it empty, SmartInvs will call update()
            }
        }

        plugin.getLogger().info("[INVSEE] applyRemoteState: snapshot received for viewerId="
                + p.getViewerId() + " targetId=" + p.getTargetId());
    }




    /**
     * Appelé quand un staff exécute /invsee <player> sur ce serveur.
     * On :
     *  - demande à l'owner node d'ouvrir l'inv (Rabbit)
     *  - crée une session locale + GUI vide
     *  - peuple le GUI quand le premier InvseeStatePacket arrive.
     */
    public void openLocalViewer(Player viewer, UUID targetId, String targetName) {
        if (viewer == null || targetId == null) return;

        InvseeSession sess = new InvseeSession(targetId, viewer.getUniqueId());
        sess.setTargetName(targetName);
        sessions.put(viewer.getUniqueId(), sess);

        InventoryService.Snapshot initial = requestSnapshot(targetId); // uses invStorage + online player
        if (initial != null && initial.contents != null) {
            sess.setLastSnapshot(initial.contents);
        } else {
            sess.setLastSnapshot(new ItemStack[0]);
        }

        InvseeMenu menu = new InvseeMenu(plugin, this, sess, viewer); // 🔹 add viewer here
        sess.setMenu(menu);
        menu.open(viewer);

        if (broker != null && plugin.isMessagingAvailable() && isPlayerKnownOnNetwork(targetId)) {
            broker.requestOpen(viewer, targetId, targetName);
        } else {
            plugin.getLogger().info("[INVSEE] openLocalViewer: no live owner for "
                    + targetId + ", using local/offline snapshot only.");
        }
    }


    private boolean isPlayerKnownOnNetwork(UUID targetId) {
        try {
            var dir = plugin.getPlayerDirectory();
            if (dir == null) return false;
            String server = dir.lookupCurrentServer(targetId);
            return server != null && !server.isBlank();
        } catch (Throwable t) {
            plugin.getLogger().warning("[INVSEE] isPlayerKnownOnNetwork failed: " + t.getMessage());
            return false;
        }
    }


    /**
     * Utilisé par cross.InvseeMenu quand le staff édite un slot.
     * On envoie un InvseeEditPacket vers l'owner node.
     */
    public void sendEdit(UUID viewerId, UUID targetId, int slot, ItemStack item) {
        if (broker == null) return;
        broker.sendEdit(viewerId, targetId, slot, item);
    }

    /* ------------------------------------------------------------------
     * 3) API POUR LE ModGUI (ModGUI InvSeeMenu utilise ces méthodes)
     * ------------------------------------------------------------------ */

    /**
     * Utilisé par le ModGUI InvSeeMenu pour récupérer le meilleur snapshot possible :
     *  - Si le joueur est en ligne sur CE serveur => inventaire live.
     *  - Sinon, on tente le storage persistant (Mongo/YAML via InventoryService).
     */
    public InventoryService.Snapshot requestSnapshot(UUID targetId) {
        if (targetId == null) return null;

        Player online = Bukkit.getPlayer(targetId);
        if (online != null && online.isOnline()) {
            return snapshotFromOnline(online);
        }

        if (invStorage != null) {
            try {
                return invStorage.load(targetId);
            } catch (Exception e) {
                plugin.getLogger().warning("[INVSEE] requestSnapshot: failed to load snapshot: " + e.getMessage());
            }
        }

        return null;
    }

    private InventoryService.Snapshot snapshotFromOnline(Player p) {
        InventoryService.Snapshot snap = new InventoryService.Snapshot();
        snap.contents = p.getInventory().getContents();
        snap.armor    = p.getInventory().getArmorContents();
        snap.offhand  = p.getInventory().getItemInOffHand();

        //  copy XP
        snap.level    = p.getLevel();
        snap.exp      = p.getExp();
        snap.totalExp = p.getTotalExperience();

        return snap;
    }



    /**
     * Appelé par le ModGUI InvSeeMenu quand le staff ferme le GUI
     * et qu'on a reconstruit un Snapshot à partir du contenu visuel.
     *
     *  - Si le joueur est en ligne ici => on applique directement sur son inventaire.
     *  - Dans tous les cas, si invStorage est dispo => on sauvegarde pour la synchro.
     */
    public boolean applySnapshotFromGui(UUID viewerId,
                                        UUID targetId,
                                        InventoryService.Snapshot snap) {
        if (targetId == null || snap == null) return false;

        Player online = Bukkit.getPlayer(targetId);
        if (online != null && online.isOnline()) {
            try {
                if (snap.contents != null) {
                    online.getInventory().setContents(snap.contents);
                }
                if (snap.armor != null) {
                    online.getInventory().setArmorContents(snap.armor);
                }
                if (snap.offhand != null) {
                    online.getInventory().setItemInOffHand(snap.offhand);
                }

                //  restore XP (if snapshot has it)
                online.setLevel(snap.level);
                online.setExp(snap.exp);
                online.setTotalExperience(snap.totalExp);

                online.updateInventory();
            } catch (Exception e) {
                plugin.getLogger().warning("[INVSEE] applySnapshotFromGui: failed to apply to online player: " + e.getMessage());
            }
        }

        if (invStorage != null) {
            try {
                invStorage.save(targetId, snap);
            } catch (Exception e) {
                plugin.getLogger().warning("[INVSEE] applySnapshotFromGui: failed to save snapshot: " + e.getMessage());
                return false;
            }
        }

        plugin.getLogger().info("[INVSEE] applySnapshotFromGui: changes applied for targetId=" + targetId
                + " (viewerId=" + viewerId + ")");
        return true;
    }
}
