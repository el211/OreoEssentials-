package fr.elias.oreoEssentials.modules.orders.model;

import java.util.UUID;

/**
 * Represents a buy-request order on the market.
 * A requester escrows (unitPrice * totalQty) upfront.
 * Fillers deliver items and receive escrow proportionally.
 */
public class Order {

    private String id;
    private UUID requesterUuid;
    private String requesterName;
    private String itemData;          // Base64 BukkitObjectOutputStream ItemStack
    private String displayItemName;   // cached readable name for GUIs
    private int totalQty;
    private int remainingQty;
    private String currencyId;        // null = Vault
    private double unitPrice;
    private double escrowTotal;
    private double escrowRemaining;
    private OrderStatus status;
    private long createdAt;
    private long updatedAt;
    private int revision;             // incremented on every write; used to reject stale events

    // Custom-item identifiers (optional)
    private String itemsAdderId;
    private String nexoId;
    private String oraxenId;

    public Order() {}

    public Order(String id, UUID requesterUuid, String requesterName,
                 String itemData, String displayItemName,
                 int totalQty, String currencyId,
                 double unitPrice) {
        this.id              = id;
        this.requesterUuid   = requesterUuid;
        this.requesterName   = requesterName;
        this.itemData        = itemData;
        this.displayItemName = displayItemName;
        this.totalQty        = totalQty;
        this.remainingQty    = totalQty;
        this.currencyId      = currencyId;
        this.unitPrice       = unitPrice;
        this.escrowTotal     = unitPrice * totalQty;
        this.escrowRemaining = this.escrowTotal;
        this.status          = OrderStatus.ACTIVE;
        this.createdAt       = System.currentTimeMillis();
        this.updatedAt       = this.createdAt;
        this.revision        = 0;
    }


    public String      getId()                { return id; }
    public void        setId(String id)       { this.id = id; }

    public UUID        getRequesterUuid()                         { return requesterUuid; }
    public void        setRequesterUuid(UUID requesterUuid)       { this.requesterUuid = requesterUuid; }

    public String      getRequesterName()                         { return requesterName; }
    public void        setRequesterName(String requesterName)     { this.requesterName = requesterName; }

    public String      getItemData()                              { return itemData; }
    public void        setItemData(String itemData)               { this.itemData = itemData; }

    public String      getDisplayItemName()                       { return displayItemName; }
    public void        setDisplayItemName(String displayItemName) { this.displayItemName = displayItemName; }

    public int         getTotalQty()                              { return totalQty; }
    public void        setTotalQty(int totalQty)                  { this.totalQty = totalQty; }

    public int         getRemainingQty()                          { return remainingQty; }
    public void        setRemainingQty(int remainingQty)          { this.remainingQty = remainingQty; }

    public String      getCurrencyId()                            { return currencyId; }
    public void        setCurrencyId(String currencyId)           { this.currencyId = currencyId; }

    public double      getUnitPrice()                             { return unitPrice; }
    public void        setUnitPrice(double unitPrice)             { this.unitPrice = unitPrice; }

    public double      getEscrowTotal()                           { return escrowTotal; }
    public void        setEscrowTotal(double escrowTotal)         { this.escrowTotal = escrowTotal; }

    public double      getEscrowRemaining()                       { return escrowRemaining; }
    public void        setEscrowRemaining(double escrowRemaining) { this.escrowRemaining = escrowRemaining; }

    public OrderStatus getStatus()                                { return status; }
    public void        setStatus(OrderStatus status)              { this.status = status; }

    public long        getCreatedAt()                             { return createdAt; }
    public void        setCreatedAt(long createdAt)               { this.createdAt = createdAt; }

    public long        getUpdatedAt()                             { return updatedAt; }
    public void        setUpdatedAt(long updatedAt)               { this.updatedAt = updatedAt; }

    public int         getRevision()                              { return revision; }
    public void        setRevision(int revision)                  { this.revision = revision; }

    public String      getItemsAdderId()                          { return itemsAdderId; }
    public void        setItemsAdderId(String itemsAdderId)       { this.itemsAdderId = itemsAdderId; }

    public String      getNexoId()                                { return nexoId; }
    public void        setNexoId(String nexoId)                   { this.nexoId = nexoId; }

    public String      getOraxenId()                              { return oraxenId; }
    public void        setOraxenId(String oraxenId)               { this.oraxenId = oraxenId; }
}
