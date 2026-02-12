package fr.elias.oreoEssentials.modules.invsee.rabbit;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.invsee.InvseeService;
import fr.elias.oreoEssentials.modules.invsee.rabbit.packets.InvseeEditPacket;
import fr.elias.oreoEssentials.modules.invsee.rabbit.packets.InvseeOpenRequestPacket;
import fr.elias.oreoEssentials.modules.invsee.rabbit.packets.InvseeStatePacket;
import fr.elias.oreoEssentials.rabbitmq.PacketChannels;
import fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel;
import fr.elias.oreoEssentials.rabbitmq.packet.PacketManager;
import fr.elias.oreoEssentials.modules.trade.ItemStacksCodec;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public final class InvseeCrossServerBroker {

    private final OreoEssentials plugin;
    private final PacketManager pm;
    private final String serverName;
    private InvseeService service;

    public InvseeCrossServerBroker(OreoEssentials plugin, PacketManager pm,
                                   String serverName, InvseeService service) {
        this.plugin = plugin;
        this.pm = pm;
        this.serverName = serverName;
        this.service = service;
    }

    public void setService(InvseeService service) {
        this.service = service;
    }

    public void requestOpen(Player viewer, UUID targetId, String targetName) {
        if (viewer == null || targetId == null) return;
        if (!pmReady()) {
            viewer.sendMessage("§cCross-server messaging unavailable (invsee).");
            return;
        }

        String ownerServer = findNodeFor(targetId);

        InvseeOpenRequestPacket pkt = new InvseeOpenRequestPacket(
                viewer.getUniqueId(),
                viewer.getName(),
                targetId
        );

        if (ownerServer != null && !ownerServer.isBlank()) {
            pm.sendPacket(PacketChannels.individual(ownerServer), pkt);
        } else {
            pm.sendPacket(PacketChannels.GLOBAL, pkt);
        }
    }

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

    public void handleOpenRequest(InvseeOpenRequestPacket p) {
        if (p == null || service == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> service.handleRemoteOpen(p));
    }

    public void handleEdit(InvseeEditPacket p) {
        if (p == null || service == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> service.applyRemoteEdit(p));
    }

    public void handleState(InvseeStatePacket p) {
        if (p == null || service == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> service.applyRemoteState(p));
    }

    private boolean pmReady() {
        return pm != null && pm.isInitialized();
    }

    private String findNodeFor(UUID playerId) {
        try {
            var dir = plugin.getPlayerDirectory();
            return (dir != null) ? dir.lookupCurrentServer(playerId) : null;
        } catch (Throwable t) {
            return null;
        }
    }

    @SuppressWarnings("unused")
    private PacketChannel currentNodeChannel() {
        return PacketChannels.individual(serverName);
    }
}