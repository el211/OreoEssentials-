package fr.elias.oreoEssentials.rabbitmq.stream;


import java.io.ByteArrayOutputStream;
import java.util.Collection;
import java.util.function.Consumer;

public class FriendlyByteOutputStream {

    private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    public void writeByte(int value) {
        outputStream.write(value);
    }

    public void writeShort(short value) {
        outputStream.write(value >> 8);
        outputStream.write(value);
    }

    public void writeInt(int value) {
        outputStream.write(value >> 24);
        outputStream.write(value >> 16);
        outputStream.write(value >> 8);
        outputStream.write(value);
    }

    public void writeLong(long value) {
        outputStream.write((int) (value >> 56));
        outputStream.write((int) (value >> 48));
        outputStream.write((int) (value >> 40));
        outputStream.write((int) (value >> 32));
        outputStream.write((int) (value >> 24));
        outputStream.write((int) (value >> 16));
        outputStream.write((int) (value >> 8));
        outputStream.write((int) value);
    }

    public void writeFloat(float value) {
        writeInt(Float.floatToIntBits(value));
    }

    public void writeDouble(double value) {
        writeLong(Double.doubleToLongBits(value));
    }

    public void writeBoolean(boolean value) {
        outputStream.write(value ? 1 : 0);
    }

    public void writeString(String value) {
        byte[] bytes = value.getBytes();

        writeShort((short) bytes.length);
        writeBytes(bytes);
    }

    public void writeBytes(byte[] bytes) {
        outputStream.write(bytes, 0, bytes.length);
    }

    public <T> void writeCollection(Collection<T> collection, Consumer<T> writer) {
        writeInt(collection.size());

        for (T entry : collection) {
            writer.accept(entry);
        }
    }

    public void writeUUID(java.util.UUID value) {
        writeLong(value.getMostSignificantBits());
        writeLong(value.getLeastSignificantBits());
    }

    public void writeEnum(Enum<?> value) {
        writeInt(value.ordinal());
    }

    public void writeNullable(Object value, Consumer<Object> writer) {
        writeBoolean(value != null);

        if (value != null) {
            writer.accept(value);
        }
    }

    public byte[] toByteArray() {
        return outputStream.toByteArray();
    }
}
