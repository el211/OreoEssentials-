package fr.elias.oreoEssentials.modules.invsee.rabbit.packets;

import fr.elias.oreoEssentials.rabbitmq.packet.Packet;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteInputStream;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteOutputStream;

import java.util.UUID;


public final class InvseeEditPacket extends Packet {

    private UUID targetId;
    private UUID viewerId;
    private int slot;
    private byte[] itemBytes;

    public InvseeEditPacket() {
    }

    public InvseeEditPacket(UUID targetId, UUID viewerId, int slot, byte[] itemBytes) {
        this.targetId = targetId;
        this.viewerId = viewerId;
        this.slot = slot;
        this.itemBytes = (itemBytes == null ? new byte[0] : itemBytes);
    }

    public UUID getTargetId() {
        return targetId;
    }

    public UUID getViewerId() {
        return viewerId;
    }

    public int getSlot() {
        return slot;
    }

    public byte[] getItemBytes() {
        return itemBytes;
    }

    @Override
    protected void write(FriendlyByteOutputStream out) {
        out.writeUUID(targetId);
        out.writeUUID(viewerId);
        out.writeInt(slot);
        writeBlob(out, itemBytes);
    }

    @Override
    protected void read(FriendlyByteInputStream in) {
        targetId = in.readUUID();
        viewerId = in.readUUID();
        slot = in.readInt();
        itemBytes = readBlob(in);
    }


    private static void writeBlob(FriendlyByteOutputStream out, byte[] data) {
        int n = (data == null ? 0 : data.length);
        out.writeInt(n);
        for (int i = 0; i < n; i++) {
            out.writeByte(data[i]);
        }
    }

    private static byte[] readBlob(FriendlyByteInputStream in) {
        int n = Math.max(0, in.readInt());
        byte[] b = new byte[n];
        for (int i = 0; i < n; i++) {
            b[i] = in.readByte();
        }
        return b;
    }
}
