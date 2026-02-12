package fr.elias.oreoEssentials.modules.cross.rabbit.namespace;

import fr.elias.oreoEssentials.rabbitmq.namespace.PacketNamespace;
import fr.elias.oreoEssentials.modules.cross.rabbit.packets.CrossInvPacket;

public final class CrossInvPacketNamespace extends PacketNamespace {


    public static final short NS_ID = 91;
    public static final int   CROSS_INV_PACKET_ID = 9101;

    public CrossInvPacketNamespace() {
        super(NS_ID);
    }

    @Override
    protected void registerPackets() {
        registerPacket(CROSS_INV_PACKET_ID, CrossInvPacket.class, CrossInvPacket::new);
    }
}
