package fr.elias.oreoEssentials.modules.tp.rabbit.packets;

import fr.elias.oreoEssentials.rabbitmq.packet.Packet;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteInputStream;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteOutputStream;

import java.util.UUID;

public class TpaRequestPacket extends Packet {

    private UUID requesterUuid;
    private String requesterName;
    private UUID targetUuid;
    private String targetName;
    private String fromServer;
    private long expiresAtEpochMs;

    public TpaRequestPacket() {}

    public TpaRequestPacket(UUID requesterUuid, String requesterName, UUID targetUuid,
                            String targetName, String fromServer, long expiresAtEpochMs) {
        this.requesterUuid = requesterUuid;
        this.requesterName = requesterName;
        this.targetUuid = targetUuid;
        this.targetName = targetName;
        this.fromServer = fromServer;
        this.expiresAtEpochMs = expiresAtEpochMs;
    }

    public UUID getRequesterUuid()   { return requesterUuid; }
    public String getRequesterName() { return requesterName; }
    public UUID getTargetUuid()      { return targetUuid; }
    public String getTargetName()    { return targetName; }
    public String getFromServer()    { return fromServer; }
    public long getExpiresAtEpochMs(){ return expiresAtEpochMs; }

    @Override
    protected void write(FriendlyByteOutputStream out) {
        out.writeUUID(requesterUuid);
        out.writeString(requesterName != null ? requesterName : "");
        out.writeUUID(targetUuid);
        out.writeString(targetName != null ? targetName : "");
        out.writeString(fromServer != null ? fromServer : "");
        out.writeLong(expiresAtEpochMs);
    }

    @Override
    protected void read(FriendlyByteInputStream in) {
        this.requesterUuid     = in.readUUID();
        this.requesterName     = in.readString();
        this.targetUuid        = in.readUUID();
        this.targetName        = in.readString();
        this.fromServer        = in.readString();
        this.expiresAtEpochMs  = in.readLong();
    }
}
