package fr.elias.oreoEssentials.modules.warps.rabbit.namespace;

import fr.elias.oreoEssentials.rabbitmq.namespace.PacketNamespace;
import fr.elias.oreoEssentials.modules.warps.rabbit.packets.WarpTeleportRequestPacket;

public final class WarpsPacketNamespace extends PacketNamespace {

    public static final int WARP_TP_REQ_ID = 1002;

    public WarpsPacketNamespace() {
        super((short) 11);
    }

    @Override
    protected void registerPackets() {
        registerPacket(WARP_TP_REQ_ID, WarpTeleportRequestPacket.class, WarpTeleportRequestPacket::new);
    }
}
