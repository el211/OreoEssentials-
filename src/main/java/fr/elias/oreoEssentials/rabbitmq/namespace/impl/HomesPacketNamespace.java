package fr.elias.oreoEssentials.rabbitmq.namespace.impl;

import fr.elias.oreoEssentials.rabbitmq.namespace.PacketNamespace;
import fr.elias.oreoEssentials.rabbitmq.packet.impl.HomeTeleportRequestPacket;
// ADD:
import fr.elias.oreoEssentials.rabbitmq.packet.impl.OtherHomeTeleportRequestPacket;

public final class HomesPacketNamespace extends PacketNamespace {

    public static final int HOME_TP_REQ_ID = 1001;

    public static final int OTHER_HOME_TP_REQ_ID = 1002;

    public HomesPacketNamespace() {
        super((short) 10); // keep your existing namespace id
    }

    @Override
    protected void registerPackets() {
        registerPacket(HOME_TP_REQ_ID, HomeTeleportRequestPacket.class, HomeTeleportRequestPacket::new);

        // ADD: register the new “/otherhome” packet
        registerPacket(OTHER_HOME_TP_REQ_ID, OtherHomeTeleportRequestPacket.class, OtherHomeTeleportRequestPacket::new);
    }
}
