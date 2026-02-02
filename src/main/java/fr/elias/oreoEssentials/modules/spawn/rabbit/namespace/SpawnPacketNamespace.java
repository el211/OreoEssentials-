package fr.elias.oreoEssentials.modules.spawn.rabbit.namespace;

import fr.elias.oreoEssentials.modules.spawn.rabbit.packets.SpawnTeleportRequestPacket;
import fr.elias.oreoEssentials.rabbitmq.namespace.PacketNamespace;

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
