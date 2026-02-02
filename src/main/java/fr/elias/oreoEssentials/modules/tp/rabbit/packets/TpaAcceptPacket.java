package fr.elias.oreoEssentials.modules.tp.rabbit.packets;

import fr.elias.oreoEssentials.rabbitmq.packet.Packet;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteInputStream;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteOutputStream;

import java.util.UUID;


public class TpaAcceptPacket extends Packet {

    private String requestId;
    private UUID requesterUuid;
    private String requesterName;
    private UUID targetUuid;
    private String targetName;
    private String fromServer;

    public TpaAcceptPacket() {}

    public TpaAcceptPacket(
            String requestId,
            UUID requesterUuid,
            String requesterName,
            UUID targetUuid,
            String targetName,
            String fromServer
    ) {
        this.requestId = requestId;
        this.requesterUuid = requesterUuid;
        this.requesterName = requesterName;
        this.targetUuid = targetUuid;
        this.targetName = targetName;
        this.fromServer = fromServer;
    }

    public String getRequestId()     { return requestId; }
    public UUID getRequesterUuid()   { return requesterUuid; }
    public String getRequesterName() { return requesterName; }
    public UUID getTargetUuid()      { return targetUuid; }
    public String getTargetName()    { return targetName; }
    public String getFromServer()    { return fromServer; }

    @Override
    protected void write(FriendlyByteOutputStream out) {
        out.writeString(requestId != null ? requestId : "");
        out.writeUUID(requesterUuid);
        out.writeString(requesterName != null ? requesterName : "");
        out.writeUUID(targetUuid);
        out.writeString(targetName != null ? targetName : "");
        out.writeString(fromServer != null ? fromServer : "");
    }

    @Override
    protected void read(FriendlyByteInputStream in) {
        this.requestId     = in.readString();
        this.requesterUuid = in.readUUID();
        this.requesterName = in.readString();
        this.targetUuid    = in.readUUID();
        this.targetName    = in.readString();
        this.fromServer    = in.readString();
    }

    @Override
    public String toString() {
        return "TpaAcceptPacket{" +
                "requestId='" + requestId + '\'' +
                ", requesterUuid=" + requesterUuid +
                ", requesterName='" + requesterName + '\'' +
                ", targetUuid=" + targetUuid +
                ", targetName='" + targetName + '\'' +
                ", fromServer='" + fromServer + '\'' +
                '}';
    }
}
