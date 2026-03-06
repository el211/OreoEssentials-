package fr.elias.oreoEssentials.modules.orders.model;

import java.util.UUID;

/**
 * Represents items owed to a player that could not be delivered immediately
 * (because they were offline or on a different server at the time of the fill).
 *
 * Persisted in the database and delivered the next time the player joins this server.
 */
public class PendingDelivery {

    private String id;           // UUID string
    private UUID   playerUuid;   // recipient
    private String itemData;     // Base64-serialized ItemStack (quantity baked in)
    private int    quantity;     // how many of that item
    private String orderId;      // the order that triggered this delivery
    private long   createdAt;

    public PendingDelivery() {}

    public PendingDelivery(String id, UUID playerUuid, String itemData, int quantity, String orderId) {
        this.id         = id;
        this.playerUuid = playerUuid;
        this.itemData   = itemData;
        this.quantity   = quantity;
        this.orderId    = orderId;
        this.createdAt  = System.currentTimeMillis();
    }

    public String getId()                         { return id; }
    public void   setId(String id)                { this.id = id; }

    public UUID   getPlayerUuid()                 { return playerUuid; }
    public void   setPlayerUuid(UUID playerUuid)  { this.playerUuid = playerUuid; }

    public String getItemData()                   { return itemData; }
    public void   setItemData(String itemData)    { this.itemData = itemData; }

    public int    getQuantity()                   { return quantity; }
    public void   setQuantity(int quantity)       { this.quantity = quantity; }

    public String getOrderId()                    { return orderId; }
    public void   setOrderId(String orderId)      { this.orderId = orderId; }

    public long   getCreatedAt()                  { return createdAt; }
    public void   setCreatedAt(long createdAt)    { this.createdAt = createdAt; }
}