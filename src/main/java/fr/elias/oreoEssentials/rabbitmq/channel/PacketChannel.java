package fr.elias.oreoEssentials.rabbitmq.channel;

import fr.elias.oreoEssentials.rabbitmq.IndividualPacketChannel;
import fr.elias.oreoEssentials.rabbitmq.MultiPacketChannel;

import java.util.Iterator;


public interface PacketChannel extends Iterable<String> {

    static PacketChannel individual(String name) {
        return IndividualPacketChannel.create(name);
    }


    static PacketChannel multiple(String... names) {
        return MultiPacketChannel.create(names);
    }


    static PacketChannel multiple(PacketChannel... channels) {
        return MultiPacketChannel.create(channels);
    }


    Iterable<String> getChannels();

    @Override
    default Iterator<String> iterator() {
        return getChannels().iterator();
    }
}
