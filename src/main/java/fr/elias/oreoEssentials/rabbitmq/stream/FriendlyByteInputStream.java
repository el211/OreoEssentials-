package fr.elias.oreoEssentials.rabbitmq.stream;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

    private int u() throws EOFException {
        int v = stream.read();
        if (v == -1) throw new EOFException("Unexpected end of packet data");
        return v;
    }

    public byte readByte() {
        try {
            return (byte) u();
        } catch (EOFException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    public int readInt() {
        try {
            return (u() << 24) | (u() << 16) | (u() << 8) | u();
        } catch (EOFException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    public short readShort() {
        try {
            return (short) ((u() << 8) | u());
        } catch (EOFException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    public long readLong() {
        try {
            long v = 0;
            for (int i = 0; i < 8; i++) v = (v << 8) | (u() & 0xFFL);
            return v;
        } catch (EOFException e) {
            throw new java.io.UncheckedIOException(e);
        }
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
        int length = readInt();
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) bytes[i] = readByte();
        return new String(bytes, StandardCharsets.UTF_8);
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
