package fr.elias.oreoEssentials.rabbitmq.packet.event;


import fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel;
import fr.elias.oreoEssentials.rabbitmq.packet.Packet;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class PacketSubscriptionQueue<T extends Packet> {

    private final Class<T> packetClass;
    private final Queue<PacketSubscriber<T>> subscribers;

    public PacketSubscriptionQueue(Class<T> packetClass) {
        this.packetClass = packetClass;
        this.subscribers = new ConcurrentLinkedQueue<>();
    }

    public void subscribe(PacketSubscriber<T> subscriber) {
        subscribers.add(subscriber);
    }

    public void unsubscribe(PacketSubscriber<T> subscriber) {
        subscribers.remove(subscriber);
    }

    public void dispatch(PacketChannel channel, T packet) {
        subscribers.forEach(subscriber -> subscriber.onReceive(channel, packet));
    }

    public Class<T> getPacketClass() {
        return packetClass;
    }
}

