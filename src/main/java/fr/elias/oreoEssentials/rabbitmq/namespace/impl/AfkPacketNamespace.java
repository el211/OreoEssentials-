package fr.elias.oreoEssentials.rabbitmq.namespace.impl;

import fr.elias.oreoEssentials.rabbitmq.namespace.PacketNamespace;
import fr.elias.oreoEssentials.rabbitmq.packet.impl.tp.AfkPoolEnterPacket;
import fr.elias.oreoEssentials.rabbitmq.packet.impl.tp.AfkPoolExitPacket;

public final class AfkPacketNamespace extends PacketNamespace {

    public static final int AFK_POOL_ENTER_ID = 2001;
    public static final int AFK_POOL_EXIT_ID  = 2002;

    public AfkPacketNamespace() {
        super((short) 20);
    }

    @Override
    protected void registerPackets() {
        registerPacket(AFK_POOL_ENTER_ID, AfkPoolEnterPacket.class, AfkPoolEnterPacket::new);
        registerPacket(AFK_POOL_EXIT_ID,  AfkPoolExitPacket.class,  AfkPoolExitPacket::new);
    }
}
