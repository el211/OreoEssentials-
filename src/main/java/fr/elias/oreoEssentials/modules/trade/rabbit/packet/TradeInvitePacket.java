package fr.elias.oreoEssentials.modules.trade.rabbit.packet;

import fr.elias.oreoEssentials.rabbitmq.packet.Packet;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteInputStream;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteOutputStream;

import java.util.UUID;

public final class TradeInvitePacket extends Packet {
    private UUID requesterId;
    private String requesterName;
    private String requesterServer;

    private UUID targetId;
    private String targetName;

    public TradeInvitePacket() { }

    public TradeInvitePacket(UUID requesterId, String requesterName, String requesterServer,
                             UUID targetId, String targetName) {
        this.requesterId = requesterId;
        this.requesterName = requesterName;
        this.requesterServer = requesterServer;
        this.targetId = targetId;
        this.targetName = targetName;
    }

    public UUID getRequesterId()     { return requesterId; }
    public String getRequesterName() { return requesterName; }
    public String getRequesterServer(){ return requesterServer; }
    public UUID getTargetId()        { return targetId; }
    public String getTargetName()    { return targetName; }

    @Override
    protected void write(FriendlyByteOutputStream out) {
        out.writeUUID(requesterId);
        out.writeString(requesterName != null ? requesterName : "");
        out.writeString(requesterServer != null ? requesterServer : "");
        out.writeUUID(targetId);
        out.writeString(targetName != null ? targetName : "");
    }

    @Override
    protected void read(FriendlyByteInputStream in) {
        requesterId     = in.readUUID();
        requesterName   = in.readString();
        requesterServer = in.readString();
        targetId        = in.readUUID();
        targetName      = in.readString();
    }
}
