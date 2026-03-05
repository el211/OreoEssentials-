package fr.elias.oreoEssentials.modules.auctionhouse.models;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class Auction {

    private String id;
    private UUID seller;
    private String sellerName;
    private ItemStack item;
    private double price;
    private long listedTime;
    private long expirationTime;
    private AuctionCategory category;
    private AuctionStatus status;
    private UUID buyer;
    private String buyerName;
    private long soldTime;

    private String itemsAdderID;
    private String nexoID;
    private String oraxenID;
    private Integer customModelData;
    /** null = Vault economy; non-null = CurrencyService currency ID */
    private String currencyId;

    public Auction(UUID seller, String sellerName, ItemStack item, double price,
                   long durationMs, AuctionCategory category) {
        this.id = UUID.randomUUID().toString();
        this.seller = seller;
        this.sellerName = sellerName;
        this.item = item.clone();
        this.price = price;
        this.listedTime = System.currentTimeMillis();
        this.expirationTime = listedTime + durationMs;
        this.category = category;
        this.status = AuctionStatus.ACTIVE;
    }

    public Auction(String id, UUID seller, String sellerName, ItemStack item, double price,
                   long listedTime, long expirationTime, AuctionCategory category,
                   AuctionStatus status, UUID buyer, String buyerName, long soldTime) {
        this.id = id;
        this.seller = seller;
        this.sellerName = sellerName;
        this.item = item;
        this.price = price;
        this.listedTime = listedTime;
        this.expirationTime = expirationTime;
        this.category = category;
        this.status = status;
        this.buyer = buyer;
        this.buyerName = buyerName;
        this.soldTime = soldTime;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expirationTime && status == AuctionStatus.ACTIVE;
    }

    public long getTimeRemaining() {
        return Math.max(0, expirationTime - System.currentTimeMillis());
    }

    public void markAsSold(UUID buyer, String buyerName) {
        this.status = AuctionStatus.SOLD;
        this.buyer = buyer;
        this.buyerName = buyerName;
        this.soldTime = System.currentTimeMillis();
    }

    public void markAsExpired()  { this.status = AuctionStatus.EXPIRED;   }
    public void markAsCancelled(){ this.status = AuctionStatus.CANCELLED; }


    public String getId()                     { return id; }
    public void   setId(String id)            { this.id = id; }

    public UUID   getSeller()                 { return seller; }
    public void   setSeller(UUID seller)      { this.seller = seller; }

    public String getSellerName()             { return sellerName; }
    public void   setSellerName(String n)     { this.sellerName = n; }

    public ItemStack getItem()                { return item.clone(); }
    public void      setItem(ItemStack item)  { this.item = item; }

    public double getPrice()                  { return price; }
    public void   setPrice(double price)      { this.price = price; }

    public long getListedTime()               { return listedTime; }
    public void setListedTime(long t)         { this.listedTime = t; }

    public long getExpirationTime()           { return expirationTime; }
    public void setExpirationTime(long t)     { this.expirationTime = t; }

    public AuctionCategory getCategory()                { return category; }
    public void            setCategory(AuctionCategory c){ this.category = c; }

    public AuctionStatus getStatus()                    { return status; }
    public void          setStatus(AuctionStatus s)     { this.status = s; }

    public UUID   getBuyer()                  { return buyer; }
    public void   setBuyer(UUID buyer)        { this.buyer = buyer; }

    public String getBuyerName()              { return buyerName; }
    public void   setBuyerName(String n)      { this.buyerName = n; }

    public long getSoldTime()                 { return soldTime; }
    public void setSoldTime(long t)           { this.soldTime = t; }

    public String  getItemsAdderID()                   { return itemsAdderID; }
    public void    setItemsAdderID(String id)          { this.itemsAdderID = id; }

    public String  getNexoID()                         { return nexoID; }
    public void    setNexoID(String id)                { this.nexoID = id; }

    public String  getOraxenID()                       { return oraxenID; }
    public void    setOraxenID(String id)              { this.oraxenID = id; }

    public Integer getCustomModelData()                { return customModelData; }
    public void    setCustomModelData(Integer cmd)     { this.customModelData = cmd; }

    /** null = Vault; non-null = CurrencyService currency ID */
    public String  getCurrencyId()                     { return currencyId; }
    public void    setCurrencyId(String currencyId)    { this.currencyId = (currencyId != null && currencyId.isBlank()) ? null : currencyId; }
}