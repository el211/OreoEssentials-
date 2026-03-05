package fr.elias.oreoEssentials.modules.auctionhouse.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import fr.elias.oreoEssentials.modules.auctionhouse.models.Auction;
import fr.elias.oreoEssentials.modules.auctionhouse.models.AuctionCategory;
import fr.elias.oreoEssentials.modules.auctionhouse.models.AuctionStatus;
import fr.elias.oreoEssentials.modules.auctionhouse.utils.ItemSerializer;
import org.bukkit.inventory.ItemStack;

import java.io.*;
import java.lang.reflect.Type;
import java.util.*;
import java.util.logging.Logger;

public final class JsonAuctionStorage implements AuctionStorage {

    private final File dataFile;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Logger logger;

    public JsonAuctionStorage(File moduleFolder, Logger logger) {
        this.dataFile = new File(moduleFolder, "auctions.json");
        this.logger = logger;
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
                saveAll(new AuctionSnapshot(List.of(), List.of(), List.of()));
            } catch (IOException e) {
                logger.severe("[AuctionHouse] Could not create auctions.json: " + e.getMessage());
            }
        }
    }

    @Override
    public AuctionSnapshot loadAll() {
        try (Reader reader = new FileReader(dataFile)) {
            Type type = new TypeToken<Map<String, Object>>(){}.getType();
            Map<String, Object> raw = gson.fromJson(reader, type);
            if (raw == null) return new AuctionSnapshot(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());

            return new AuctionSnapshot(
                    deserializeList((List<?>) raw.get("auctions")),
                    deserializeList((List<?>) raw.get("expired")),
                    deserializeList((List<?>) raw.get("sold"))
            );
        } catch (IOException e) {
            logger.severe("[AuctionHouse] Could not load auctions.json: " + e.getMessage());
            return new AuctionSnapshot(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        }
    }

    @Override
    public synchronized void saveAll(AuctionSnapshot snap) {
        try (Writer writer = new FileWriter(dataFile)) {
            Map<String, Object> out = new HashMap<>();
            out.put("auctions", serializeList(snap.active()));
            out.put("expired",  serializeList(snap.expired()));
            out.put("sold",     serializeList(snap.sold()));
            gson.toJson(out, writer);
        } catch (IOException e) {
            logger.severe("[AuctionHouse] Could not save auctions.json: " + e.getMessage());
        }
    }


    private List<Map<String, Object>> serializeList(List<Auction> auctions) {
        if (auctions == null) return List.of();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Auction a : auctions) {
            Map<String, Object> m = new HashMap<>();
            m.put("id",             a.getId());
            m.put("seller",         a.getSeller().toString());
            m.put("sellerName",     a.getSellerName());
            m.put("item",           ItemSerializer.serialize(a.getItem()));
            m.put("price",          a.getPrice());
            m.put("listedTime",     a.getListedTime());
            m.put("expirationTime", a.getExpirationTime());
            m.put("category",       a.getCategory().name());
            m.put("status",         a.getStatus().name());

            if (a.getBuyer() != null) {
                m.put("buyer",     a.getBuyer().toString());
                m.put("buyerName", a.getBuyerName());
                m.put("soldTime",  a.getSoldTime());
            }
            if (a.getItemsAdderID()  != null) m.put("itemsAdderID",  a.getItemsAdderID());
            if (a.getNexoID()        != null) m.put("nexoID",        a.getNexoID());
            if (a.getOraxenID()      != null) m.put("oraxenID",      a.getOraxenID());
            if (a.getCustomModelData() != null) m.put("customModelData", a.getCustomModelData());
            if (a.getCurrencyId()    != null) m.put("currencyId",    a.getCurrencyId());

            list.add(m);
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    private List<Auction> deserializeList(List<?> raw) {
        if (raw == null) return new ArrayList<>();
        List<Auction> out = new ArrayList<>();
        for (Object obj : raw) {
            if (!(obj instanceof Map)) continue;
            Map<String, Object> m = (Map<String, Object>) obj;
            try {
                String id              = (String) m.get("id");
                UUID seller            = UUID.fromString((String) m.get("seller"));
                String sellerName      = (String) m.get("sellerName");
                ItemStack item         = ItemSerializer.deserialize((String) m.get("item"));
                double price           = ((Number) m.get("price")).doubleValue();
                long listedTime        = ((Number) m.get("listedTime")).longValue();
                long expirationTime    = ((Number) m.get("expirationTime")).longValue();
                AuctionCategory cat    = AuctionCategory.valueOf((String) m.get("category"));
                AuctionStatus status   = AuctionStatus.valueOf((String) m.get("status"));
                UUID buyer             = m.containsKey("buyer") ? UUID.fromString((String) m.get("buyer")) : null;
                String buyerName       = (String) m.get("buyerName");
                long soldTime          = m.containsKey("soldTime") ? ((Number) m.get("soldTime")).longValue() : 0;

                Auction a = new Auction(id, seller, sellerName, item, price,
                        listedTime, expirationTime, cat, status, buyer, buyerName, soldTime);

                if (m.containsKey("itemsAdderID"))   a.setItemsAdderID((String) m.get("itemsAdderID"));
                if (m.containsKey("nexoID"))          a.setNexoID((String) m.get("nexoID"));
                if (m.containsKey("oraxenID"))        a.setOraxenID((String) m.get("oraxenID"));
                if (m.containsKey("customModelData")) a.setCustomModelData(((Number) m.get("customModelData")).intValue());
                if (m.containsKey("currencyId"))      a.setCurrencyId((String) m.get("currencyId"));

                out.add(a);
            } catch (Exception e) {
                logger.warning("[AuctionHouse] Skipping corrupt auction entry: " + e.getMessage());
            }
        }
        return out;
    }
}