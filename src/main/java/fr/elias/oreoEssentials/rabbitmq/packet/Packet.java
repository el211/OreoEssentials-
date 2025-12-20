package fr.elias.oreoEssentials.rabbitmq.packet;


import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteInputStream;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteOutputStream;

import java.util.UUID;

public abstract class Packet {

    //  Ensures the packet always has a valid ID
    private UUID packetId = UUID.randomUUID();

    /**
     * Called after UUID is read from the stream.
     * Subclasses must implement this to deserialize custom fields.
     */
    protected abstract void read(FriendlyByteInputStream stream);

    /**
     * Called before UUID is written to the stream.
     * Subclasses must implement this to serialize custom fields.
     */
    protected abstract void write(FriendlyByteOutputStream stream);

    /**
     * Reads full packet data including internal packet UUID.
     */
    public void readData(FriendlyByteInputStream stream) {
        this.packetId = stream.readUUID();
        read(stream);
    }

    /**
     * Writes full packet data including internal packet UUID.
     */
    public void writeData(FriendlyByteOutputStream stream) {
        stream.writeUUID(packetId);
        write(stream);
    }

    /**
     * Optional getter if you need to access packetId externally.
     */
    public UUID getPacketId() {
        return packetId;
    }

    /**
     * Optional setter (in case a manual UUID is needed).
     */
    public void setPacketId(UUID packetId) {
        this.packetId = packetId != null ? packetId : UUID.randomUUID();
    }
}
