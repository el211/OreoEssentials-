package fr.elias.oreoEssentials.rabbitmq.packet;


import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteInputStream;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteOutputStream;

import java.util.UUID;

public abstract class Packet {

    private UUID packetId = UUID.randomUUID();


    protected abstract void read(FriendlyByteInputStream stream);


    protected abstract void write(FriendlyByteOutputStream stream);


    public void readData(FriendlyByteInputStream stream) {
        this.packetId = stream.readUUID();
        read(stream);
    }


    public void writeData(FriendlyByteOutputStream stream) {
        stream.writeUUID(packetId);
        write(stream);
    }


    public UUID getPacketId() {
        return packetId;
    }

    public void setPacketId(UUID packetId) {
        this.packetId = packetId != null ? packetId : UUID.randomUUID();
    }
}
