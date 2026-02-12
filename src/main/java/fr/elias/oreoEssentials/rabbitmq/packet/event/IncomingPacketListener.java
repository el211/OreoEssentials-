package fr.elias.oreoEssentials.rabbitmq.packet.event;


import fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel;

public interface IncomingPacketListener {

    void onReceive(PacketChannel channel, byte[] content);

}

