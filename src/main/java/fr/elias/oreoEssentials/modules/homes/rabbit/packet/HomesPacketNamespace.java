package fr.elias.oreoEssentials.modules.homes.rabbit.packet;

import fr.elias.oreoEssentials.rabbitmq.namespace.PacketNamespace;
// ADD:


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
