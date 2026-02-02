package fr.elias.oreoEssentials.modules.warps.rabbit.packets;

import fr.elias.oreoEssentials.rabbitmq.packet.Packet;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteInputStream;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteOutputStream;

import java.util.UUID;

public final class WarpTeleportRequestPacket extends Packet {
    private UUID playerId;
    private String warpName;
    private String targetServer;
    private String requestId;

    public WarpTeleportRequestPacket() {}
    public WarpTeleportRequestPacket(UUID playerId, String warpName, String targetServer, String requestId) {
        this.playerId = playerId;
        this.warpName = warpName;
        this.targetServer = targetServer;
        this.requestId = requestId;
    }

    public UUID getPlayerId() { return playerId; }
    public String getWarpName() { return warpName; }
    public String getTargetServer() { return targetServer; }
    public String getRequestId() { return requestId; }

    @Override protected void write(FriendlyByteOutputStream out) {
        out.writeUUID(playerId);
        out.writeString(warpName != null ? warpName : "");
        out.writeString(targetServer != null ? targetServer : "");
        out.writeString(requestId != null ? requestId : "");
    }

    @Override protected void read(FriendlyByteInputStream in) {
        playerId = in.readUUID();
        warpName = in.readString();
        targetServer = in.readString();
        requestId = in.readString();
    }
}

