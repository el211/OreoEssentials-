package fr.elias.oreoEssentials.modules.homes.rabbit.packet;

import fr.elias.oreoEssentials.rabbitmq.packet.Packet;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteInputStream;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteOutputStream;

import java.util.UUID;

public class HomeTeleportRequestPacket extends Packet {

    private UUID playerId;
    private String homeName;
    private String targetServer;
    private String requestId;

    public HomeTeleportRequestPacket() { }

    public HomeTeleportRequestPacket(UUID playerId, String homeName, String targetServer) {
        this(playerId, homeName, targetServer, UUID.randomUUID().toString());
    }

    public HomeTeleportRequestPacket(UUID playerId, String homeName, String targetServer, String requestId) {
        this.playerId = playerId;
        this.homeName = homeName;
        this.targetServer = targetServer;
        this.requestId = (requestId == null ? UUID.randomUUID().toString() : requestId);
    }

    public UUID getPlayerId()       { return playerId; }
    public String getHomeName()     { return homeName; }
    public String getTargetServer() { return targetServer; }
    public String getRequestId()    { return requestId; }

    @Override
    protected void write(FriendlyByteOutputStream out) {
        out.writeUUID(playerId);
        out.writeString(homeName != null ? homeName : "");
        out.writeString(targetServer != null ? targetServer : "");
        out.writeString(requestId != null ? requestId : "");
    }

    @Override
    protected void read(FriendlyByteInputStream in) {
        this.playerId     = in.readUUID();
        this.homeName     = in.readString();
        this.targetServer = in.readString();
        this.requestId    = in.readString();
    }
}
