package fr.elias.oreoEssentials.modules.homes.rabbit.packet;

import fr.elias.oreoEssentials.rabbitmq.packet.Packet;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteInputStream;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteOutputStream;

import java.util.UUID;

public final class OtherHomeTeleportRequestPacket extends Packet {
    private UUID subjectId;
    private UUID ownerId;
    private String homeName;
    private String targetServer;
    private String requestId;

    public OtherHomeTeleportRequestPacket() {}

    public OtherHomeTeleportRequestPacket(UUID subjectId, UUID ownerId, String homeName, String targetServer, String requestId) {
        this.subjectId = subjectId;
        this.ownerId = ownerId;
        this.homeName = homeName;
        this.targetServer = targetServer;
        this.requestId = (requestId == null ? UUID.randomUUID().toString() : requestId);
    }

    public UUID getSubjectId()   { return subjectId; }
    public UUID getOwnerId()     { return ownerId; }
    public String getHomeName()  { return homeName; }
    public String getTargetServer() { return targetServer; }
    public String getRequestId() { return requestId; }

    @Override
    protected void write(FriendlyByteOutputStream out) {
        out.writeUUID(subjectId);
        out.writeUUID(ownerId);
        out.writeString(homeName != null ? homeName : "");
        out.writeString(targetServer != null ? targetServer : "");
        out.writeString(requestId != null ? requestId : "");
    }

    @Override
    protected void read(FriendlyByteInputStream in) {
        this.subjectId    = in.readUUID();
        this.ownerId      = in.readUUID();
        this.homeName     = in.readString();
        this.targetServer = in.readString();
        this.requestId    = in.readString();
    }
}
