package fr.elias.oreoEssentials.rabbitmq.stream;

import java.io.ByteArrayInputStream;
import java.util.Collection;
import java.util.UUID;
import java.util.function.Supplier;

public class FriendlyByteInputStream {

    private final ByteArrayInputStream stream;

    public FriendlyByteInputStream(byte[] bytes) {
        this.stream = new ByteArrayInputStream(bytes);
    }

    public FriendlyByteInputStream() {
        this.stream = new ByteArrayInputStream(new byte[0]);
    }

    private int u() {
        int v = stream.read();
        return (v < 0) ? 0 : v; // avoid -1 poisoning
    }

    public byte readByte() {
        return (byte) u();
    }

    public int readInt() {
        return (u() << 24) | (u() << 16) | (u() << 8) | u();
    }

    public short readShort() {
        return (short) ((u() << 8) | u());
    }

    public long readLong() {
        long v = 0;
        for (int i = 0; i < 8; i++) v = (v << 8) | (u() & 0xFFL);
        return v;
    }

    public float readFloat() {
        return Float.intBitsToFloat(readInt());
    }

    public double readDouble() {
        return Double.longBitsToDouble(readLong());
    }

    public boolean readBoolean() {
        return readByte() == 1;
    }

    public String readString() {
        int length = readShort() & 0xFFFF;
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) bytes[i] = readByte();
        return new String(bytes);
    }

    public byte[] readBytes(int length) {
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) bytes[i] = readByte();
        return bytes;
    }

    public UUID readUUID() {
        return new UUID(readLong(), readLong());
    }

    public <T extends Enum<T>> T readEnum(Class<T> clazz) {
        int idx = readInt(); // match writeEnum(int)
        T[] vals = clazz.getEnumConstants();
        return (idx >= 0 && idx < vals.length) ? vals[idx] : vals[0];
    }

    public <T> Collection<T> readCollection(Collection<T> collection, Supplier<T> reader) {
        int size = readInt();
        for (int i = 0; i < size; i++) collection.add(reader.get());
        return collection;
    }
}
