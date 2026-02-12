package fr.elias.oreoEssentials.rabbitmq.packet.event;



import fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel;
import fr.elias.oreoEssentials.rabbitmq.packet.Packet;

public interface PacketSubscriber<T extends Packet> {

    void onReceive(PacketChannel channel, T packet);
}

