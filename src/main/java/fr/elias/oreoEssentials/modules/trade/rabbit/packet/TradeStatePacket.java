package fr.elias.oreoEssentials.modules.trade.rabbit.packet;

import fr.elias.oreoEssentials.rabbitmq.packet.Packet;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteInputStream;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteOutputStream;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;

public final class TradeStatePacket extends Packet {
    // TR8: Version byte written as the very first field so cross-version mismatches are caught early.
    static final byte PACKET_VERSION = 1;

    private UUID sessionId;
    private UUID fromPlayerId;
    private boolean ready;
    private byte[] offerBytes; // encoded offer

    public TradeStatePacket() { }

    public TradeStatePacket(UUID sessionId, UUID fromPlayerId, boolean ready, byte[] offerBytes) {
        this.sessionId = sessionId;
        this.fromPlayerId = fromPlayerId;
        this.ready = ready;
        this.offerBytes = (offerBytes == null ? new byte[0] : offerBytes);
    }

    public UUID getSessionId()   { return sessionId; }
    public UUID getFromPlayerId(){ return fromPlayerId; }
    public boolean isReady()     { return ready; }
    public byte[] getOfferBytes(){ return offerBytes; }

    @Override
    protected void write(FriendlyByteOutputStream out) {
        // TR8: Write protocol version first so the reader can detect incompatible packets.
        out.writeByte(PACKET_VERSION);
        out.writeUUID(sessionId);
        out.writeUUID(fromPlayerId);
        out.writeBoolean(ready);
        writeBlob(out, offerBytes);
    }

    @Override
    protected void read(FriendlyByteInputStream in) {
        // TR8: Read and validate version byte before parsing any fields.
        byte readVersion = in.readByte();
        if (readVersion != PACKET_VERSION) {
            throw new UncheckedIOException(new IOException(
                    "Unsupported TradeStatePacket version: " + readVersion));
        }
        sessionId   = in.readUUID();
        fromPlayerId= in.readUUID();
        ready       = in.readBoolean();
        offerBytes  = readBlob(in);
    }

    /* -------- length-prefixed blob helpers (no writeByteArray API) -------- */
    private static void writeBlob(FriendlyByteOutputStream out, byte[] data) {
        int n = (data == null ? 0 : data.length);
        out.writeInt(n);
        for (int i = 0; i < n; i++) out.writeByte(data[i]);
    }

    private static byte[] readBlob(FriendlyByteInputStream in) {
        int n = in.readInt();
        if (n < 0 || n > 10_000_000) {
            throw new UncheckedIOException(new IOException(
                    "[Trade] Received malformed packet: invalid blob size " + n));
        }
        byte[] b = new byte[n];
        for (int i = 0; i < n; i++) b[i] = in.readByte();
        return b;
    }
}
