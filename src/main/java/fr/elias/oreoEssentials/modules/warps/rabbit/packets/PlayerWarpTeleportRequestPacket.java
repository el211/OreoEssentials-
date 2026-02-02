package fr.elias.oreoEssentials.modules.warps.rabbit.packets;

import fr.elias.oreoEssentials.rabbitmq.packet.Packet;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteInputStream;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteOutputStream;

import java.util.UUID;


public final class PlayerWarpTeleportRequestPacket extends Packet {

    private UUID playerId;
    private UUID ownerId;
    private String warpName;
    private String targetServer;
    private String requestId;

    public PlayerWarpTeleportRequestPacket() {
    }

    public PlayerWarpTeleportRequestPacket(UUID playerId,
                                           UUID ownerId,
                                           String warpName,
                                           String targetServer,
                                           String requestId) {
        this.playerId = playerId;
        this.ownerId = ownerId;
        this.warpName = warpName;
        this.targetServer = targetServer;
        this.requestId = requestId;
    }

    public UUID getPlayerId()      { return playerId; }
    public UUID getOwnerId()       { return ownerId; }
    public String getWarpName()    { return warpName; }
    public String getTargetServer(){ return targetServer; }
    public String getRequestId()   { return requestId; }

    @Override
    protected void write(FriendlyByteOutputStream out) {
        out.writeUUID(playerId);
        out.writeUUID(ownerId);
        out.writeString(warpName != null ? warpName : "");
        out.writeString(targetServer != null ? targetServer : "");
        out.writeString(requestId != null ? requestId : "");
    }

    @Override
    protected void read(FriendlyByteInputStream in) {
        this.playerId     = in.readUUID();
        this.ownerId      = in.readUUID();
        this.warpName     = in.readString();
        this.targetServer = in.readString();
        this.requestId    = in.readString();
    }
}
