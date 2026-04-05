package fr.elias.oreoEssentials.modules.portals.rabbit;

import fr.elias.oreoEssentials.rabbitmq.namespace.PacketNamespace;

public final class PortalsPacketNamespace extends PacketNamespace {

    public static final int PORTAL_TELEPORT_ID = 3001;

    public PortalsPacketNamespace() {
        super((short) 30);
    }

    @Override
    protected void registerPackets() {
        registerPacket(PORTAL_TELEPORT_ID, PortalTeleportPacket.class, PortalTeleportPacket::new);
    }
}
