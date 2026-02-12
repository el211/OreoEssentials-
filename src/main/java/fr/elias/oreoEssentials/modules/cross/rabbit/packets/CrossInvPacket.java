package fr.elias.oreoEssentials.modules.cross.rabbit.packets;

import fr.elias.oreoEssentials.rabbitmq.packet.Packet;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteInputStream;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteOutputStream;

public final class CrossInvPacket extends Packet {
    private String json;

    public CrossInvPacket() {
    }

    public CrossInvPacket(String json) {
        this.json = json;
    }

    public String getJson() {
        return json;
    }

    @Override
    protected void read(FriendlyByteInputStream in) {
        this.json = in.readString();
    }

    @Override
    protected void write(FriendlyByteOutputStream out) {
        out.writeString(json != null ? json : "");
    }
}
