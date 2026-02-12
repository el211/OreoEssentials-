package fr.elias.oreoEssentials.modules.currency.rabbitmq;

import fr.elias.oreoEssentials.rabbitmq.packet.Packet;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteInputStream;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteOutputStream;

import java.util.UUID;

/**
 * Packet for synchronizing currency balance updates across servers
 */
public final class CurrencyUpdatePacket extends Packet {

    private UUID playerId;
    private String currencyId;
    private double newBalance;

    public CurrencyUpdatePacket() {}

    public CurrencyUpdatePacket(UUID playerId, String currencyId, double newBalance) {
        this.playerId = playerId;
        this.currencyId = currencyId;
        this.newBalance = newBalance;
    }

    @Override
    protected void read(FriendlyByteInputStream in) {
        this.playerId = in.readUUID();
        this.currencyId = in.readString();
        this.newBalance = in.readDouble();

        if (this.currencyId == null) this.currencyId = "unknown";
    }

    @Override
    protected void write(FriendlyByteOutputStream out) {
        out.writeUUID(playerId);
        out.writeString(currencyId != null ? currencyId : "unknown");
        out.writeDouble(newBalance);
    }

    public UUID getPlayerId() { return playerId; }
    public String getCurrencyId() { return currencyId; }
    public double getNewBalance() { return newBalance; }

    @Override
    public String toString() {
        return "CurrencyUpdatePacket{" +
                "playerId=" + playerId +
                ", currencyId='" + currencyId + '\'' +
                ", newBalance=" + newBalance +
                '}';
    }
}