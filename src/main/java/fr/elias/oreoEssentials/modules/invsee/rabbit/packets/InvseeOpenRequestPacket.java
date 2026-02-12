package fr.elias.oreoEssentials.modules.invsee.rabbit.packets;

import fr.elias.oreoEssentials.rabbitmq.packet.Packet;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteInputStream;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteOutputStream;

import java.util.UUID;


public final class InvseeOpenRequestPacket extends Packet {

    private UUID viewerId;
    private String viewerName;
    private UUID targetId;

    public InvseeOpenRequestPacket() {
    }

    public InvseeOpenRequestPacket(UUID viewerId, String viewerName, UUID targetId) {
        this.viewerId = viewerId;
        this.viewerName = (viewerName == null ? "Viewer" : viewerName);
        this.targetId = targetId;
    }

    public UUID getViewerId() {
        return viewerId;
    }

    public String getViewerName() {
        return viewerName;
    }

    public UUID getTargetId() {
        return targetId;
    }

    @Override
    protected void write(FriendlyByteOutputStream out) {
        out.writeUUID(viewerId);
        out.writeString(viewerName != null ? viewerName : "");
        out.writeUUID(targetId);
    }

    @Override
    protected void read(FriendlyByteInputStream in) {
        viewerId = in.readUUID();
        viewerName = in.readString();
        targetId = in.readUUID();

        if (viewerName == null) viewerName = "Viewer";
    }
}
