package fr.elias.oreoEssentials.modules.invsee;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.invsee.menu.InvseeMenu;
import fr.elias.oreoEssentials.modules.invsee.rabbit.InvseeCrossServerBroker;
import fr.elias.oreoEssentials.modules.invsee.rabbit.packets.InvseeEditPacket;
import fr.elias.oreoEssentials.modules.invsee.rabbit.packets.InvseeOpenRequestPacket;
import fr.elias.oreoEssentials.modules.invsee.rabbit.packets.InvseeStatePacket;
import fr.elias.oreoEssentials.services.InventoryService;
import fr.elias.oreoEssentials.modules.trade.ItemStacksCodec;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InvseeService {

    private final OreoEssentials plugin;
    private final InvseeCrossServerBroker broker;
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

    public void handleRemoteOpen(InvseeOpenRequestPacket packet) {
        if (packet == null) return;

        Player target = Bukkit.getPlayer(packet.getTargetId());
        if (target == null || !target.isOnline()) {
            return;
        }

        ItemStack[] snapshot = target.getInventory().getContents();
        broker.sendStateToViewer(packet.getViewerId(), target.getUniqueId(), snapshot);
    }

    public void applyRemoteEdit(InvseeEditPacket packet) {
        if (packet == null) return;

        Player target = Bukkit.getPlayer(packet.getTargetId());
        if (target == null || !target.isOnline()) {
            return;
        }

        ItemStack[] decoded = ItemStacksCodec.decodeFromBytes(packet.getItemBytes());
        ItemStack newItem = (decoded.length > 0 ? decoded[0] : null);

        target.getInventory().setItem(packet.getSlot(), newItem);
        target.updateInventory();

        saveInventorySnapshot(target);
        broker.sendStateToViewer(packet.getViewerId(), packet.getTargetId(), target.getInventory().getContents());
    }

    public void applyRemoteState(InvseeStatePacket packet) {
        if (packet == null) return;

        ItemStack[] contents = ItemStacksCodec.decodeFromBytes(packet.getContentsBytes());

        InvseeSession session = sessions.get(packet.getViewerId());
        if (session == null) {
            return;
        }

        session.setLastSnapshot(contents);

        Player viewer = Bukkit.getPlayer(packet.getViewerId());
        if (viewer != null && viewer.isOnline()) {
            InvseeMenu menu = session.getMenu();
            if (menu != null) {
                menu.refreshFromSession(session);
            }
        }
    }

    public void openLocalViewer(Player viewer, UUID targetId, String targetName) {
        if (viewer == null || targetId == null) return;

        InvseeSession session = createSession(viewer, targetId, targetName);
        InvseeMenu menu = new InvseeMenu(plugin, this, session, viewer);
        session.setMenu(menu);
        menu.open(viewer);

        if (shouldRequestNetworkData(targetId)) {
            broker.requestOpen(viewer, targetId, targetName);
        }
    }

    private InvseeSession createSession(Player viewer, UUID targetId, String targetName) {
        InvseeSession session = new InvseeSession(targetId, viewer.getUniqueId());
        session.setTargetName(targetName);
        sessions.put(viewer.getUniqueId(), session);

        InventoryService.Snapshot initial = requestSnapshot(targetId);
        if (initial != null && initial.contents != null) {
            session.setLastSnapshot(initial.contents);
        } else {
            session.setLastSnapshot(new ItemStack[0]);
        }

        return session;
    }

    private boolean shouldRequestNetworkData(UUID targetId) {
        return broker != null
                && plugin.isMessagingAvailable()
                && isPlayerKnownOnNetwork(targetId);
    }

    private boolean isPlayerKnownOnNetwork(UUID targetId) {
        try {
            var dir = plugin.getPlayerDirectory();
            if (dir == null) return false;
            String server = dir.lookupCurrentServer(targetId);
            return server != null && !server.isBlank();
        } catch (Throwable t) {
            return false;
        }
    }

    public void sendEdit(UUID viewerId, UUID targetId, int slot, ItemStack item) {
        if (broker == null) return;
        broker.sendEdit(viewerId, targetId, slot, item);
    }

    public InventoryService.Snapshot requestSnapshot(UUID targetId) {
        if (targetId == null) return null;

        Player online = Bukkit.getPlayer(targetId);
        if (online != null && online.isOnline()) {
            return snapshotFromOnline(online);
        }

        if (invStorage != null) {
            try {
                return invStorage.load(targetId);
            } catch (Exception ignored) {
            }
        }

        return null;
    }

    private InventoryService.Snapshot snapshotFromOnline(Player player) {
        InventoryService.Snapshot snapshot = new InventoryService.Snapshot();
        snapshot.contents = player.getInventory().getContents();
        snapshot.armor = player.getInventory().getArmorContents();
        snapshot.offhand = player.getInventory().getItemInOffHand();
        snapshot.level = player.getLevel();
        snapshot.exp = player.getExp();
        snapshot.totalExp = player.getTotalExperience();
        return snapshot;
    }

    private void saveInventorySnapshot(Player player) {
        if (invStorage == null) return;

        try {
            InventoryService.Snapshot snapshot = snapshotFromOnline(player);
            invStorage.save(player.getUniqueId(), snapshot);
        } catch (Exception ignored) {
        }
    }

    public boolean applySnapshotFromGui(UUID viewerId, UUID targetId, InventoryService.Snapshot snapshot) {
        if (targetId == null || snapshot == null) return false;

        applyToOnlinePlayer(targetId, snapshot);
        return saveSnapshot(targetId, snapshot);
    }

    private void applyToOnlinePlayer(UUID targetId, InventoryService.Snapshot snapshot) {
        Player online = Bukkit.getPlayer(targetId);
        if (online == null || !online.isOnline()) return;

        try {
            if (snapshot.contents != null) {
                online.getInventory().setContents(snapshot.contents);
            }
            if (snapshot.armor != null) {
                online.getInventory().setArmorContents(snapshot.armor);
            }
            if (snapshot.offhand != null) {
                online.getInventory().setItemInOffHand(snapshot.offhand);
            }

            online.setLevel(snapshot.level);
            online.setExp(snapshot.exp);
            online.setTotalExperience(snapshot.totalExp);
            online.updateInventory();
        } catch (Exception ignored) {
        }
    }

    private boolean saveSnapshot(UUID targetId, InventoryService.Snapshot snapshot) {
        if (invStorage == null) return true;

        try {
            invStorage.save(targetId, snapshot);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}