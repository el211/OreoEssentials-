package fr.elias.oreoEssentials.rabbitmq;


import fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel;

import java.util.Collections;

public final class IndividualPacketChannel implements PacketChannel {
    private final Iterable<String> channel;

    private IndividualPacketChannel(String channel) {
        this.channel = Collections.singletonList(channel);
    }

    public static PacketChannel create(String channel) { return new IndividualPacketChannel(channel); }

    @Override public Iterable<String> getChannels() { return channel; }
}