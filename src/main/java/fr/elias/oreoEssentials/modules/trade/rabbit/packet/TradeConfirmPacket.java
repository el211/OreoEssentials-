package fr.elias.oreoEssentials.modules.trade.rabbit.packet;

import fr.elias.oreoEssentials.rabbitmq.packet.Packet;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteInputStream;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteOutputStream;

import java.util.UUID;

public final class TradeConfirmPacket extends Packet {
    private UUID sessionId;
    private UUID confirmerId;

    public TradeConfirmPacket() { }

    public TradeConfirmPacket(UUID sessionId, UUID confirmerId) {
        this.sessionId = sessionId;
        this.confirmerId = confirmerId;
    }
  //
    public UUID getSessionId()  { return sessionId; }
    public UUID getConfirmerId(){ return confirmerId; }

    @Override
    protected void write(FriendlyByteOutputStream out) {
        out.writeUUID(sessionId);
        out.writeUUID(confirmerId);
    }

    @Override
    protected void read(FriendlyByteInputStream in) {
        sessionId   = in.readUUID();
        confirmerId = in.readUUID();
    }
}
