package fr.elias.oreoEssentials.rabbitmq.packet.impl.trade;

import fr.elias.oreoEssentials.rabbitmq.packet.Packet;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteInputStream;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteOutputStream;

import java.util.UUID;


public final class TradeStartPacket extends Packet {

    // Canonical session id for this A<->B pair (order-independent)
    private UUID sessionId;

    // Participant A (original requester)
    private UUID aId;
    private String aName;
    public UUID getRequesterId()   { return aId; }
    public String getRequesterName(){ return aName; }
    public UUID getAcceptorId()    { return bId; }
    public String getAcceptorName(){ return bName; }

    // Participant B (acceptor on the other server)
    private UUID bId;
    private String bName;

    /** No-args constructor required by the registry. */
    public TradeStartPacket() {}

    public TradeStartPacket(UUID sessionId, UUID aId, String aName, UUID bId, String bName) {
        this.sessionId = sessionId;
        this.aId = aId;
        this.aName = (aName == null ? "Player" : aName);
        this.bId = bId;
        this.bName = (bName == null ? "Player" : bName);
    }

    // --- Getters (both styles) ---
    public UUID getSessionId() { return sessionId; }
    /** Alias used by handlers (name matters): */
    public UUID getSid()       { return sessionId; }

    public UUID getAId()       { return aId; }
    public String getAName()   { return aName; }
    public UUID getBId()       { return bId; }
    public String getBName()   { return bName; }

    // --- Optional setter so the publisher can inject the computed SID before send ---
    public void setSid(UUID sid) { this.sessionId = sid; }

    @Override
    protected void read(FriendlyByteInputStream in) {
        // packetId already consumed by Packet.readData(...)
        this.sessionId = in.readUUID();
        this.aId       = in.readUUID();
        this.aName     = in.readString();
        this.bId       = in.readUUID();
        this.bName     = in.readString();

        if (this.aName == null) this.aName = "Player";
        if (this.bName == null) this.bName = "Player";
    }

    @Override
    protected void write(FriendlyByteOutputStream out) {
        // Packet.writeData(...) will handle packetId
        out.writeUUID(sessionId);
        out.writeUUID(aId);
        out.writeString(aName != null ? aName : "Player");
        out.writeUUID(bId);
        out.writeString(bName != null ? bName : "Player");
    }

    @Override
    public String toString() {
        return "TradeStartPacket{" +
                "sessionId=" + sessionId +
                ", aId=" + aId +
                ", aName='" + aName + '\'' +
                ", bId=" + bId +
                ", bName='" + bName + '\'' +
                '}';
    }
}
