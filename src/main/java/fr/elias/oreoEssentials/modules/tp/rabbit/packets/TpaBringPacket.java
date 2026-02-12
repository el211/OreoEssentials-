package fr.elias.oreoEssentials.modules.tp.rabbit.packets;

import fr.elias.oreoEssentials.rabbitmq.packet.Packet;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteInputStream;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteOutputStream;

import java.util.UUID;

public class TpaBringPacket extends Packet {
    private UUID requesterUuid;
    private String targetServer;

    public TpaBringPacket() {}
    public TpaBringPacket(UUID requesterUuid, String targetServer) {
        this.requesterUuid = requesterUuid;
        this.targetServer = targetServer;
    }

    public UUID getRequesterUuid() { return requesterUuid; }
    public String getTargetServer() { return targetServer; }

    @Override
    protected void write(FriendlyByteOutputStream out) {
        out.writeUUID(requesterUuid);
        out.writeString(targetServer != null ? targetServer : "");
    }

    @Override
    protected void read(FriendlyByteInputStream in) {
        this.requesterUuid = in.readUUID();
        this.targetServer  = in.readString();
    }
}
