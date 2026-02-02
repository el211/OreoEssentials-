package fr.elias.oreoEssentials.modules.economy.rabbit.namespace;


import fr.elias.oreoEssentials.rabbitmq.namespace.PacketNamespace;
import fr.elias.oreoEssentials.modules.oreobotfeatures.rabbit.packets.PlayerJoinPacket;
import fr.elias.oreoEssentials.modules.oreobotfeatures.rabbit.packets.PlayerQuitPacket;
import fr.elias.oreoEssentials.rabbitmq.packet.impl.SendRemoteMessagePacket;

public class EconomyPacketNamespace extends PacketNamespace {

    public EconomyPacketNamespace() {
        super((short) 1);
    }

    @Override
    protected void registerPackets() {
        registerPacket(0, PlayerJoinPacket.class, PlayerJoinPacket::new);
        registerPacket(1, PlayerQuitPacket.class, PlayerQuitPacket::new);
        registerPacket(2, SendRemoteMessagePacket.class, SendRemoteMessagePacket::new);
    }
}
