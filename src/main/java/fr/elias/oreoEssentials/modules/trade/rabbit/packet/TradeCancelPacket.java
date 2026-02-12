package fr.elias.oreoEssentials.modules.trade.rabbit.packet;

import fr.elias.oreoEssentials.rabbitmq.packet.Packet;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteInputStream;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteOutputStream;

import java.util.UUID;

public final class TradeCancelPacket extends Packet {
    private UUID sessionId;
    private String reason;

    public TradeCancelPacket() { }

    public TradeCancelPacket(UUID sessionId, String reason) {
        this.sessionId = sessionId;
        this.reason = (reason == null ? "" : reason);
    }

    public UUID getSessionId() { return sessionId; }
    public String getReason()  { return reason; }

    @Override
    protected void write(FriendlyByteOutputStream out) {
        out.writeUUID(sessionId);
        out.writeString(reason != null ? reason : "");
    }

    @Override
    protected void read(FriendlyByteInputStream in) {
        sessionId = in.readUUID();
        reason    = in.readString();
    }
}
