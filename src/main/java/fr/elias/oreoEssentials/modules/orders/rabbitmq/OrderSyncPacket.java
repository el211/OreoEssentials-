package fr.elias.oreoEssentials.modules.orders.rabbitmq;

import fr.elias.oreoEssentials.modules.orders.model.Order;
import fr.elias.oreoEssentials.modules.orders.model.OrderStatus;
import fr.elias.oreoEssentials.rabbitmq.packet.Packet;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteInputStream;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteOutputStream;

import java.util.UUID;

/**
 * Broadcasted when an order is created, partially filled, completed, or cancelled.
 *
 * Payload schema:
 *   type         : "ORDER_CREATED|ORDER_UPDATED|ORDER_REMOVED"
 *   orderId      : UUID string
 *   revision     : int (stale-event guard; ignore if <= local revision)
 *   status       : "ACTIVE|COMPLETED|CANCELLED"
 *   remainingQty : int
 *   escrowRemaining: double
 *   updatedAt    : long (epoch ms)
 *   serverId     : origin server name
 *   -- ORDER_CREATED only --
 *   requesterUuid  : UUID
 *   requesterName  : String
 *   itemData       : Base64 ItemStack
 *   displayItemName: String
 *   totalQty       : int
 *   unitPrice      : double
 *   currencyId     : String (empty = Vault)
 */
public class OrderSyncPacket extends Packet {

    public enum Type { ORDER_CREATED, ORDER_UPDATED, ORDER_REMOVED }

    private Type   type;
    private String orderId;
    private int    revision;
    private OrderStatus status;
    private int    remainingQty;
    private double escrowRemaining;
    private long   updatedAt;
    private String serverId;
    private UUID   requesterUuid;
    private String requesterName;
    private String itemData;
    private String displayItemName;
    private int    totalQty;
    private double unitPrice;
    private String currencyId;

    /** No-arg constructor required by PacketManager. */
    public OrderSyncPacket() {}


    public static OrderSyncPacket created(String serverId, Order order) {
        OrderSyncPacket p = new OrderSyncPacket();
        p.type            = Type.ORDER_CREATED;
        p.orderId         = order.getId();
        p.revision        = order.getRevision();
        p.status          = OrderStatus.ACTIVE;
        p.remainingQty    = order.getRemainingQty();
        p.escrowRemaining = order.getEscrowRemaining();
        p.updatedAt       = order.getUpdatedAt();
        p.serverId        = serverId;
        p.requesterUuid   = order.getRequesterUuid();
        p.requesterName   = nvl(order.getRequesterName());
        p.itemData        = nvl(order.getItemData());
        p.displayItemName = nvl(order.getDisplayItemName());
        p.totalQty        = order.getTotalQty();
        p.unitPrice       = order.getUnitPrice();
        p.currencyId      = nvl(order.getCurrencyId());
        return p;
    }

    public static OrderSyncPacket updated(String serverId, Order order) {
        OrderSyncPacket p = new OrderSyncPacket();
        p.type            = Type.ORDER_UPDATED;
        p.orderId         = order.getId();
        p.revision        = order.getRevision();
        p.status          = order.getStatus();
        p.remainingQty    = order.getRemainingQty();
        p.escrowRemaining = order.getEscrowRemaining();
        p.updatedAt       = order.getUpdatedAt();
        p.serverId        = serverId;
        return p;
    }

    public static OrderSyncPacket removed(String serverId, Order order) {
        OrderSyncPacket p = new OrderSyncPacket();
        p.type            = Type.ORDER_REMOVED;
        p.orderId         = order.getId();
        p.revision        = order.getRevision();
        p.status          = order.getStatus();
        p.remainingQty    = 0;
        p.escrowRemaining = 0;
        p.updatedAt       = System.currentTimeMillis();
        p.serverId        = serverId;
        return p;
    }


    @Override
    protected void write(FriendlyByteOutputStream out) {
        out.writeString(type.name());
        out.writeString(nvl(orderId));
        out.writeInt(revision);
        out.writeString(status.name());
        out.writeInt(remainingQty);
        out.writeDouble(escrowRemaining);
        out.writeLong(updatedAt);
        out.writeString(nvl(serverId));

        if (type == Type.ORDER_CREATED) {
            out.writeUUID(requesterUuid);
            out.writeString(nvl(requesterName));
            out.writeString(nvl(itemData));
            out.writeString(nvl(displayItemName));
            out.writeInt(totalQty);
            out.writeDouble(unitPrice);
            out.writeString(nvl(currencyId));
        }
    }

    @Override
    protected void read(FriendlyByteInputStream in) {
        type            = Type.valueOf(in.readString());
        orderId         = in.readString();
        revision        = in.readInt();
        status          = OrderStatus.valueOf(in.readString());
        remainingQty    = in.readInt();
        escrowRemaining = in.readDouble();
        updatedAt       = in.readLong();
        serverId        = in.readString();

        if (type == Type.ORDER_CREATED) {
            requesterUuid   = in.readUUID();
            requesterName   = in.readString();
            itemData        = in.readString();
            displayItemName = in.readString();
            totalQty        = in.readInt();
            unitPrice       = in.readDouble();
            currencyId      = in.readString();
        }
    }


    public Type        getType()             { return type; }
    public String      getOrderId()          { return orderId; }
    public int         getRevision()         { return revision; }
    public OrderStatus getStatus()           { return status; }
    public int         getRemainingQty()     { return remainingQty; }
    public double      getEscrowRemaining()  { return escrowRemaining; }
    public long        getUpdatedAt()        { return updatedAt; }
    public String      getServerId()         { return serverId; }
    public UUID        getRequesterUuid()    { return requesterUuid; }
    public String      getRequesterName()    { return requesterName; }
    public String      getItemData()         { return itemData; }
    public String      getDisplayItemName()  { return displayItemName; }
    public int         getTotalQty()         { return totalQty; }
    public double      getUnitPrice()        { return unitPrice; }
    public String      getCurrencyId()       { return currencyId; }

    private static String nvl(String s) { return s != null ? s : ""; }
}
