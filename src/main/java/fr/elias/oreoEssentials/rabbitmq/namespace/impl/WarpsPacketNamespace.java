package fr.elias.oreoEssentials.rabbitmq.namespace.impl;

import fr.elias.oreoEssentials.rabbitmq.namespace.PacketNamespace;
import fr.elias.oreoEssentials.rabbitmq.packet.impl.WarpTeleportRequestPacket;

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
