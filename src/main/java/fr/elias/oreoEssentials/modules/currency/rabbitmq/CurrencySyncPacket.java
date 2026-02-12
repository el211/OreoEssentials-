package fr.elias.oreoEssentials.modules.currency.rabbitmq;

import fr.elias.oreoEssentials.rabbitmq.packet.Packet;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteInputStream;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteOutputStream;

/**
 * Packet for syncing currency creation/deletion across servers
 */
public final class CurrencySyncPacket extends Packet {

    public enum Action {
        CREATE,
        DELETE
    }

    private Action action;
    private String currencyId;
    private String currencyName;
    private String currencySymbol;
    private String displayName;
    private double defaultBalance;
    private boolean tradeable;
    private boolean crossServer;
    private boolean allowNegative; // ADD THIS FIELD

    public CurrencySyncPacket() {}

    public CurrencySyncPacket(Action action, String currencyId, String currencyName,
                              String currencySymbol, String displayName, double defaultBalance,
                              boolean tradeable, boolean crossServer, boolean allowNegative) {
        this.action = action;
        this.currencyId = currencyId;
        this.currencyName = currencyName;
        this.currencySymbol = currencySymbol;
        this.displayName = displayName;
        this.defaultBalance = defaultBalance;
        this.tradeable = tradeable;
        this.crossServer = crossServer;
        this.allowNegative = allowNegative;
    }

    public CurrencySyncPacket(Action action, String currencyId) {
        this.action = action;
        this.currencyId = currencyId;
    }

    @Override
    protected void read(FriendlyByteInputStream in) {
        this.action = Action.valueOf(in.readString());
        this.currencyId = in.readString();

        if (action == Action.CREATE) {
            this.currencyName = in.readString();
            this.currencySymbol = in.readString();
            this.displayName = in.readString();
            this.defaultBalance = in.readDouble();
            this.tradeable = in.readBoolean();
            this.crossServer = in.readBoolean();
            this.allowNegative = in.readBoolean();
        }
    }

    @Override
    protected void write(FriendlyByteOutputStream out) {
        out.writeString(action.name());
        out.writeString(currencyId != null ? currencyId : "");

        if (action == Action.CREATE) {
            out.writeString(currencyName != null ? currencyName : "");
            out.writeString(currencySymbol != null ? currencySymbol : "");
            out.writeString(displayName != null ? displayName : "");
            out.writeDouble(defaultBalance);
            out.writeBoolean(tradeable);
            out.writeBoolean(crossServer);
            out.writeBoolean(allowNegative);
        }
    }

    public Action getAction() { return action; }
    public String getCurrencyId() { return currencyId; }
    public String getCurrencyName() { return currencyName; }
    public String getCurrencySymbol() { return currencySymbol; }
    public String getDisplayName() { return displayName; }
    public double getDefaultBalance() { return defaultBalance; }
    public boolean isTradeable() { return tradeable; }
    public boolean isCrossServer() { return crossServer; }
    public boolean isAllowNegative() { return allowNegative; }

    @Override
    public String toString() {
        return "CurrencySyncPacket{" +
                "action=" + action +
                ", currencyId='" + currencyId + '\'' +
                ", currencyName='" + currencyName + '\'' +
                '}';
    }
}