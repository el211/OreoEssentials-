package fr.elias.oreoEssentials.modules.trade.rabbit.packet;

import fr.elias.oreoEssentials.rabbitmq.packet.Packet;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteInputStream;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteOutputStream;

import java.util.UUID;

public final class TradeGrantPacket extends Packet {
    private UUID sessionId;
    private UUID grantTo;
    private byte[] itemsBytes;

    public TradeGrantPacket() { }

    public TradeGrantPacket(UUID sessionId, UUID grantTo, byte[] itemsBytes) {
        this.sessionId = sessionId;
        this.grantTo = grantTo;
        this.itemsBytes = (itemsBytes == null ? new byte[0] : itemsBytes);
    }

    public UUID getSessionId()   { return sessionId; }
    public UUID getGrantTo()     { return grantTo; }
    public byte[] getItemsBytes(){ return itemsBytes; }

    @Override
    protected void write(FriendlyByteOutputStream out) {
        out.writeUUID(sessionId);
        out.writeUUID(grantTo);
        writeBlob(out, itemsBytes);
    }

    @Override
    protected void read(FriendlyByteInputStream in) {
        sessionId  = in.readUUID();
        grantTo    = in.readUUID();
        itemsBytes = readBlob(in);
    }

    /* -------- length-prefixed blob helpers -------- */
    private static void writeBlob(FriendlyByteOutputStream out, byte[] data) {
        int n = (data == null ? 0 : data.length);
        out.writeInt(n);
        for (int i = 0; i < n; i++) out.writeByte(data[i]);
    }

    private static byte[] readBlob(FriendlyByteInputStream in) {
        int n = Math.max(0, in.readInt());
        byte[] b = new byte[n];
        for (int i = 0; i < n; i++) b[i] = in.readByte();
        return b;
    }
}
