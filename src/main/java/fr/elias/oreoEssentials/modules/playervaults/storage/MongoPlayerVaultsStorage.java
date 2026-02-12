package fr.elias.oreoEssentials.modules.playervaults.storage;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import fr.elias.oreoEssentials.modules.playervaults.PlayerVaultsStorage;
import org.bson.Document;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.UUID;

public final class MongoPlayerVaultsStorage implements PlayerVaultsStorage {
    private final MongoCollection<Document> col;
    private final String serverName;

    public MongoPlayerVaultsStorage(com.mongodb.client.MongoClient client, String db, String collection, String serverName) {
        this.col = client.getDatabase(db).getCollection(collection);
        this.serverName = serverName;
    }

    @Override
    public VaultSnapshot load(UUID playerId, int vaultId) {
        Document doc = col.find(Filters.and(
                Filters.eq("uuid", playerId.toString()),
                Filters.eq("server", "global")
        )).first();
        if (doc == null) return null;
        Document v = doc.get("vaults", Document.class);
        if (v == null) return null;
        Document one = v.get(String.valueOf(vaultId), Document.class);
        if (one == null) return null;

        int rows = one.getInteger("rows", 3);
        String b64 = one.getString("data");
        ItemStack[] arr = decode(b64);
        return new VaultSnapshot(rows, arr);
    }

    @Override
    public void save(UUID playerId, int vaultId, int rows, ItemStack[] contents) {
        Document doc = col.find(Filters.and(
                Filters.eq("uuid", playerId.toString()),
                Filters.eq("server", "global")
        )).first();
        if (doc == null) {
            doc = new Document("uuid", playerId.toString())
                    .append("server", "global")
                    .append("vaults", new Document());
        }
        Document vaults = doc.get("vaults", Document.class);
        Document one = new Document("rows", rows).append("data", encode(contents));
        vaults.put(String.valueOf(vaultId), one);
        doc.put("vaults", vaults);
        col.replaceOne(Filters.and(
                Filters.eq("uuid", playerId.toString()),
                Filters.eq("server", "global")
        ), doc, new ReplaceOptions().upsert(true));
    }

    private static String encode(ItemStack[] items) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             BukkitObjectOutputStream oos = new BukkitObjectOutputStream(baos)) {
            oos.writeInt(items.length);
            for (ItemStack it : items) oos.writeObject(it);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            return "";
        }
    }

    private static ItemStack[] decode(String b64) {
        if (b64 == null || b64.isEmpty()) return new ItemStack[0];
        try (ByteArrayInputStream bais = new ByteArrayInputStream(Base64.getDecoder().decode(b64));
             BukkitObjectInputStream ois = new BukkitObjectInputStream(bais)) {
            int len = ois.readInt();
            ItemStack[] arr = new ItemStack[len];
            for (int i = 0; i < len; i++) arr[i] = (ItemStack) ois.readObject();
            return arr;
        } catch (Exception e) {
            return new ItemStack[0];
        }
    }
}
