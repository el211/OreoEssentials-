package fr.elias.oreoEssentials.modules.auctionhouse.rabbitmq;

import fr.elias.oreoEssentials.modules.auctionhouse.models.Auction;
import fr.elias.oreoEssentials.modules.auctionhouse.utils.ItemSerializer;
import fr.elias.oreoEssentials.rabbitmq.packet.Packet;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteInputStream;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteOutputStream;

import java.util.UUID;

/**
 * Broadcast when an auction is created, purchased, cancelled, or expired.
 * Receiving servers apply the change to their in-memory auction lists so
 * every node in the network stays in sync without reloading from storage.
 */
public class AuctionSyncPacket extends Packet {

    public enum Action { CREATE, PURCHASE, CANCEL, EXPIRE }

    // ── Always present ────────────────────────────────────────────────────────
    private String originServer;
    private Action action;
    private String auctionId;

    // ── CREATE only ───────────────────────────────────────────────────────────
    private UUID   sellerUuid;
    private String sellerName;
    private String itemData;        // Base64-encoded serialized ItemStack
    private double price;
    private long   listedTime;
    private long   expirationTime;
    private String category;
    private String currencyId;      // "" = Vault
    private String itemsAdderId;
    private String nexoId;
    private String oraxenId;

    // ── PURCHASE only ─────────────────────────────────────────────────────────
    private UUID   buyerUuid;
    private String buyerName;

    /** Required no-arg constructor for the PacketManager. */
    public AuctionSyncPacket() {}

    // ── Factories ─────────────────────────────────────────────────────────────

    public static AuctionSyncPacket create(String origin, Auction a) {
        AuctionSyncPacket p = new AuctionSyncPacket();
        p.originServer   = origin;
        p.action         = Action.CREATE;
        p.auctionId      = a.getId();
        p.sellerUuid     = a.getSeller();
        p.sellerName     = nvl(a.getSellerName());
        p.itemData       = nvl(ItemSerializer.serialize(a.getItem()));
        p.price          = a.getPrice();
        p.listedTime     = a.getListedTime();
        p.expirationTime = a.getExpirationTime();
        p.category       = nvl(a.getCategory().name());
        p.currencyId     = nvl(a.getCurrencyId());
        p.itemsAdderId   = nvl(a.getItemsAdderID());
        p.nexoId         = nvl(a.getNexoID());
        p.oraxenId       = nvl(a.getOraxenID());
        return p;
    }

    public static AuctionSyncPacket purchase(String origin, String auctionId,
                                             UUID buyerUuid, String buyerName) {
        AuctionSyncPacket p = new AuctionSyncPacket();
        p.originServer = origin;
        p.action       = Action.PURCHASE;
        p.auctionId    = auctionId;
        p.buyerUuid    = buyerUuid;
        p.buyerName    = nvl(buyerName);
        return p;
    }

    public static AuctionSyncPacket remove(String origin, Action action, String auctionId) {
        AuctionSyncPacket p = new AuctionSyncPacket();
        p.originServer = origin;
        p.action       = action;
        p.auctionId    = auctionId;
        return p;
    }

    // ── Serialization ─────────────────────────────────────────────────────────

    @Override
    protected void write(FriendlyByteOutputStream out) {
        out.writeString(nvl(originServer));
        out.writeString(action.name());
        out.writeString(nvl(auctionId));

        if (action == Action.CREATE) {
            out.writeUUID(sellerUuid);
            out.writeString(nvl(sellerName));
            out.writeString(nvl(itemData));
            out.writeDouble(price);
            out.writeLong(listedTime);
            out.writeLong(expirationTime);
            out.writeString(nvl(category));
            out.writeString(nvl(currencyId));
            out.writeString(nvl(itemsAdderId));
            out.writeString(nvl(nexoId));
            out.writeString(nvl(oraxenId));
        } else if (action == Action.PURCHASE) {
            out.writeUUID(buyerUuid);
            out.writeString(nvl(buyerName));
        }
    }

    @Override
    protected void read(FriendlyByteInputStream in) {
        originServer = in.readString();
        action       = Action.valueOf(in.readString());
        auctionId    = in.readString();

        if (action == Action.CREATE) {
            sellerUuid     = in.readUUID();
            sellerName     = in.readString();
            itemData       = in.readString();
            price          = in.readDouble();
            listedTime     = in.readLong();
            expirationTime = in.readLong();
            category       = in.readString();
            String cid  = in.readString(); currencyId   = cid.isEmpty()  ? null : cid;
            String ia   = in.readString(); itemsAdderId = ia.isEmpty()   ? null : ia;
            String nx   = in.readString(); nexoId       = nx.isEmpty()   ? null : nx;
            String ox   = in.readString(); oraxenId     = ox.isEmpty()   ? null : ox;
        } else if (action == Action.PURCHASE) {
            buyerUuid = in.readUUID();
            buyerName = in.readString();
        }
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String getOriginServer()   { return originServer; }
    public Action getAction()         { return action; }
    public String getAuctionId()      { return auctionId; }
    public UUID   getSellerUuid()     { return sellerUuid; }
    public String getSellerName()     { return sellerName; }
    public String getItemData()       { return itemData; }
    public double getPrice()          { return price; }
    public long   getListedTime()     { return listedTime; }
    public long   getExpirationTime() { return expirationTime; }
    public String getCategory()       { return category; }
    public String getCurrencyId()     { return currencyId; }
    public String getItemsAdderId()   { return itemsAdderId; }
    public String getNexoId()         { return nexoId; }
    public String getOraxenId()       { return oraxenId; }
    public UUID   getBuyerUuid()      { return buyerUuid; }
    public String getBuyerName()      { return buyerName; }

    // ── Utility ───────────────────────────────────────────────────────────────

    private static String nvl(String s) { return s != null ? s : ""; }
}
