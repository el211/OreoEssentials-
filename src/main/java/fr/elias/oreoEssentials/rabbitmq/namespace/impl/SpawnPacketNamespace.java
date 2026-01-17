package fr.elias.oreoEssentials.rabbitmq.namespace.impl;

import fr.elias.oreoEssentials.rabbitmq.namespace.PacketNamespace;
import fr.elias.oreoEssentials.rabbitmq.packet.impl.SpawnTeleportRequestPacket;

public final class SpawnPacketNamespace extends PacketNamespace {

    public static final int SPAWN_TP_REQ_ID = 1003;

    public SpawnPacketNamespace() {
        super((short) 12);
    }

    @Override
    protected void registerPackets() {
        registerPacket(SPAWN_TP_REQ_ID, SpawnTeleportRequestPacket.class, SpawnTeleportRequestPacket::new);
    }
}
