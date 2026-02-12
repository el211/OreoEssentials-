package fr.elias.oreoEssentials.rabbitmq;


import fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel;

import java.util.ArrayList;
import java.util.List;

public class MultiPacketChannel implements PacketChannel {

    private final Iterable<String> channels;

    private MultiPacketChannel(Iterable<String> channels) {
        this.channels = channels;
    }

    public static PacketChannel create(String... channels) {
        return new MultiPacketChannel(List.of(channels));
    }

    public static PacketChannel create(PacketChannel... channels) {
        List<String> channelList = new ArrayList<>();

        for (PacketChannel channel : channels) {
            for (String subchannel : channel.getChannels()) {
                channelList.add(subchannel);
            }
        }

        return new MultiPacketChannel(channelList);
    }

    @Override
    public Iterable<String> getChannels() {
        return channels;
    }
}
