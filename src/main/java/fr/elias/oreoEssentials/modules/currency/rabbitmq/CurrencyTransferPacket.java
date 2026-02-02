package fr.elias.oreoEssentials.modules.currency.rabbitmq;

import fr.elias.oreoEssentials.rabbitmq.packet.Packet;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteInputStream;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteOutputStream;

import java.util.UUID;

/**
 * Packet for cross-server currency transfers
 */
public final class CurrencyTransferPacket extends Packet {

    private UUID fromPlayer;
    private UUID toPlayer;
    private String currencyId;
    private double amount;
    private String fromServer;
    private String toServer;

    public CurrencyTransferPacket() {}

    public CurrencyTransferPacket(UUID fromPlayer, UUID toPlayer, String currencyId,
                                  double amount, String fromServer, String toServer) {
        this.fromPlayer = fromPlayer;
        this.toPlayer = toPlayer;
        this.currencyId = currencyId;
        this.amount = amount;
        this.fromServer = fromServer;
        this.toServer = toServer;
    }

    @Override
    protected void read(FriendlyByteInputStream in) {
        this.fromPlayer = in.readUUID();
        this.toPlayer = in.readUUID();
        this.currencyId = in.readString();
        this.amount = in.readDouble();
        this.fromServer = in.readString();
        this.toServer = in.readString();

        // Null safety
        if (this.currencyId == null) this.currencyId = "unknown";
        if (this.fromServer == null) this.fromServer = "unknown";
        if (this.toServer == null) this.toServer = "unknown";
    }

    @Override
    protected void write(FriendlyByteOutputStream out) {
        out.writeUUID(fromPlayer);
        out.writeUUID(toPlayer);
        out.writeString(currencyId != null ? currencyId : "unknown");
        out.writeDouble(amount);
        out.writeString(fromServer != null ? fromServer : "unknown");
        out.writeString(toServer != null ? toServer : "unknown");
    }

    public UUID getFromPlayer() { return fromPlayer; }
    public UUID getToPlayer() { return toPlayer; }
    public String getCurrencyId() { return currencyId; }
    public double getAmount() { return amount; }
    public String getFromServer() { return fromServer; }
    public String getToServer() { return toServer; }

    @Override
    public String toString() {
        return "CurrencyTransferPacket{" +
                "fromPlayer=" + fromPlayer +
                ", toPlayer=" + toPlayer +
                ", currencyId='" + currencyId + '\'' +
                ", amount=" + amount +
                ", fromServer='" + fromServer + '\'' +
                ", toServer='" + toServer + '\'' +
                '}';
    }
}