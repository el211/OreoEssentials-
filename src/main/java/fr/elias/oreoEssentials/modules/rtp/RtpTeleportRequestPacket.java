package fr.elias.oreoEssentials.modules.rtp;

import fr.elias.oreoEssentials.rabbitmq.packet.Packet;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteInputStream;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteOutputStream;

import java.util.UUID;


public final class RtpTeleportRequestPacket extends Packet {

    private UUID playerId;
    private String worldName;
    private String requestId;

    public RtpTeleportRequestPacket() {}

    public RtpTeleportRequestPacket(UUID playerId, String worldName, String requestId) {
        this.playerId = playerId;
        this.worldName = worldName;
        this.requestId = requestId;
    }

    public UUID getPlayerId() { return playerId; }
    public String getWorldName() { return worldName; }
    public String getRequestId() { return requestId; }

    @Override
    protected void write(FriendlyByteOutputStream out) {
        out.writeUUID(playerId);
        out.writeString(worldName != null ? worldName : "");
        out.writeString(requestId != null ? requestId : "");
    }

    @Override
    protected void read(FriendlyByteInputStream in) {
        this.playerId = in.readUUID();
        this.worldName = in.readString();
        this.requestId = in.readString();
    }
}
