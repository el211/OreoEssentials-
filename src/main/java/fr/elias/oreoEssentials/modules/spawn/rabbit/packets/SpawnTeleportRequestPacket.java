package fr.elias.oreoEssentials.modules.spawn.rabbit.packets;

import fr.elias.oreoEssentials.rabbitmq.packet.Packet;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteInputStream;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteOutputStream;

import java.util.UUID;

public final class SpawnTeleportRequestPacket extends Packet {
    private UUID playerId;
    private String targetServer;
    private String requestId;

    public SpawnTeleportRequestPacket() {}
    public SpawnTeleportRequestPacket(UUID playerId, String targetServer, String requestId) {
        this.playerId = playerId;
        this.targetServer = targetServer;
        this.requestId = requestId;
    }

    public UUID getPlayerId() { return playerId; }
    public String getTargetServer() { return targetServer; }
    public String getRequestId() { return requestId; }

    @Override protected void write(FriendlyByteOutputStream out) {
        out.writeUUID(playerId);
        out.writeString(targetServer != null ? targetServer : "");
        out.writeString(requestId != null ? requestId : "");
    }

    @Override protected void read(FriendlyByteInputStream in) {
        playerId = in.readUUID();
        targetServer = in.readString();
        requestId = in.readString();
    }
}

