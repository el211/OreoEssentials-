package fr.elias.oreoEssentials.rabbitmq.packet.event;


import fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel;

public interface PacketSender {

    void sendPacket(PacketChannel channel, byte[] content);
    void registerChannel(PacketChannel channel);
    void registerListener(IncomingPacketListener listener);

    void close();
}
