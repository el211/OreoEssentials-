package fr.elias.oreoEssentials.modules.tp.rabbit.packets;

import fr.elias.oreoEssentials.rabbitmq.packet.Packet;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteInputStream;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteOutputStream;

import java.util.UUID;


public class TpJumpPacket extends Packet {

    private UUID adminUuid;
    private UUID targetUuid;
    private String targetName;
    private String fromServer;

    public TpJumpPacket() {
    }

    public TpJumpPacket(UUID adminUuid, UUID targetUuid, String targetName, String fromServer) {
        this.adminUuid = adminUuid;
        this.targetUuid = targetUuid;
        this.targetName = targetName;
        this.fromServer = fromServer;
    }

    public UUID getAdminUuid()  { return adminUuid; }
    public UUID getTargetUuid() { return targetUuid; }
    public String getTargetName(){ return targetName; }
    public String getFromServer(){ return fromServer; }

    @Override
    protected void write(FriendlyByteOutputStream out) {
        out.writeUUID(adminUuid);
        out.writeUUID(targetUuid);
        out.writeString(targetName != null ? targetName : "");
        out.writeString(fromServer != null ? fromServer : "");
    }

    @Override
    protected void read(FriendlyByteInputStream in) {
        this.adminUuid  = in.readUUID();
        this.targetUuid = in.readUUID();
        this.targetName = in.readString();
        this.fromServer = in.readString();
    }
}
