package fr.elias.oreoEssentials.cross;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.rabbitmq.PacketChannels;
import fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel;
import fr.elias.oreoEssentials.rabbitmq.packet.PacketManager;
import fr.elias.oreoEssentials.trade.ItemStacksCodec;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Cross-server broker for /invsee.
 *
 * - Viewer node sends InvseeOpenRequestPacket to the owner node (where target is online).
 * - Owner node responds with InvseeStatePacket (full snapshot, then optional updates).
 * - Viewer node sends InvseeEditPacket when staff modifies a slot.
 *
 * Logic is very similar to TradeCrossServerBroker but simplified for 1-way view/edit.
 */
public final class InvseeCrossServerBroker {

    private final OreoEssentials plugin;
    private final PacketManager pm;
    private final String serverName;

    // ❗ no longer final so we can inject it after construction
    private InvseeService service; // local invsee orchestrator

    public InvseeCrossServerBroker(OreoEssentials plugin,
                                   PacketManager pm,
                                   String serverName,
                                   InvseeService service) {
        this.plugin = plugin;
        this.pm = pm;
        this.serverName = serverName;
        this.service = service;

        if (pm != null) {
            plugin.getLogger().info("[INVSEE] Broker init server=" + serverName
                    + " pm=" + pm.getClass().getSimpleName()
                    + " init=" + pm.isInitialized());
        } else {
            plugin.getLogger().warning("[INVSEE] Broker init with null PacketManager!");
        }
    }

    // ★ called from OreoEssentials after creating InvseeService
    public void setService(InvseeService service) {
        this.service = service;
    }

    /* ---------------------------------------------------------------------
     * Public API (called from commands / InvseeService)
     * --------------------------------------------------------------------- */

    /**
     * Viewer on THIS server wants to /invsee a target (possibly remote).
     * Sends an InvseeOpenRequestPacket to the target's "owner" node.
     */
    public void requestOpen(Player viewer, UUID targetId, String targetName) {
        if (viewer == null || targetId == null) return;
        if (!pmReady()) {
            viewer.sendMessage("§cCross-server messaging unavailable (invsee).");
            return;
        }

        String ownerServer = findNodeFor(targetId); // same pattern as TradeCrossServerBroker

        InvseeOpenRequestPacket pkt = new InvseeOpenRequestPacket(
                viewer.getUniqueId(),
                viewer.getName(),
                targetId
        );

        if (ownerServer != null && !ownerServer.isBlank()) {
            pm.sendPacket(PacketChannels.individual(ownerServer), pkt);
        } else {
            // Fallback if directory can’t find them
            pm.sendPacket(PacketChannels.GLOBAL, pkt);
        }
    }

    /**
     * Owner -> Viewer: send full snapshot (and later refreshes).
     */
    public void sendStateToViewer(UUID viewerId, UUID targetId, ItemStack[] contents) {
        if (viewerId == null || targetId == null) return;
        if (!pmReady()) return;

        byte[] bytes = ItemStacksCodec.encodeToBytes(contents);
        InvseeStatePacket pkt = new InvseeStatePacket(targetId, viewerId, bytes);

        String viewerServer = findNodeFor(viewerId);

        if (viewerServer != null && !viewerServer.isBlank()) {
            pm.sendPacket(PacketChannels.individual(viewerServer), pkt);
        } else {
            pm.sendPacket(PacketChannels.GLOBAL, pkt);
        }
    }

    /**
     * Viewer -> Owner: “set slot X in target’s inventory to <newItem>”.
     */
    public void sendEdit(UUID viewerId, UUID targetId, int slot, ItemStack newItem) {
        if (viewerId == null || targetId == null) return;
        if (!pmReady()) return;

        byte[] single = ItemStacksCodec.encodeToBytes(
                newItem != null ? new ItemStack[]{newItem} : new ItemStack[0]
        );

        InvseeEditPacket pkt = new InvseeEditPacket(targetId, viewerId, slot, single);
        String ownerServer = findNodeFor(targetId);

        if (ownerServer != null && !ownerServer.isBlank()) {
            pm.sendPacket(PacketChannels.individual(ownerServer), pkt);
        } else {
            pm.sendPacket(PacketChannels.GLOBAL, pkt);
        }
    }

    /* ---------------------------------------------------------------------
     * Inbound handlers (called from your packet listener)
     * --------------------------------------------------------------------- */

    /**
     * Called when THIS node receives an InvseeOpenRequestPacket and is the owner of the target.
     */
    public void handleOpenRequest(InvseeOpenRequestPacket p) {
        if (p == null || service == null) return;

        Bukkit.getScheduler().runTask(plugin, () -> service.handleRemoteOpen(p));
    }

    /**
     * Called when THIS node owns the target and receives an InvseeEditPacket from a viewer node.
     */
    public void handleEdit(InvseeEditPacket p) {
        if (p == null || service == null) return;

        Bukkit.getScheduler().runTask(plugin, () -> service.applyRemoteEdit(p));
    }

    /**
     * Called when THIS node is the viewer node and receives a snapshot from the owner.
     */
    public void handleState(InvseeStatePacket p) {
        if (p == null || service == null) return;

        Bukkit.getScheduler().runTask(plugin, () -> service.applyRemoteState(p));
    }

    /* ---------------------------------------------------------------------
     * Helpers
     * --------------------------------------------------------------------- */

    private boolean pmReady() {
        return pm != null && pm.isInitialized();
    }

    /**
     * Ask the shared PlayerDirectory which node a player is currently on.
     * Same style as TradeCrossServerBroker#findNodeFor.
     */
    private String findNodeFor(UUID playerId) {
        try {
            var dir = plugin.getPlayerDirectory();
            return (dir != null) ? dir.lookupCurrentServer(playerId) : null;
        } catch (Throwable t) {
            plugin.getLogger().warning("[INVSEE] findNodeFor failed: " + t.getMessage());
            return null;
        }
    }

    /** Convenience if you ever want this server's own individual channel. */
    @SuppressWarnings("unused")
    private PacketChannel currentNodeChannel() {
        return PacketChannels.individual(serverName);
    }
}
