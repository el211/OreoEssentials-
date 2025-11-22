// src/main/java/fr/elias/oreoEssentials/services/mongoservices/MongoPlayerWarpStorage.java
package fr.elias.oreoEssentials.services.mongoservices;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.ReplaceOptions;
import fr.elias.oreoEssentials.playerwarp.PlayerWarp;
import fr.elias.oreoEssentials.playerwarp.PlayerWarpStorage;
import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class MongoPlayerWarpStorage implements PlayerWarpStorage {

    private static final String F_ID       = "id";
    private static final String F_OWNER    = "owner";
    private static final String F_NAME     = "name";
    private static final String F_WORLD    = "world";
    private static final String F_X        = "x";
    private static final String F_Y        = "y";
    private static final String F_Z        = "z";
    private static final String F_YAW      = "yaw";
    private static final String F_PITCH    = "pitch";
    private static final String F_DESC     = "description";
    private static final String F_CATEGORY = "category";
    private static final String F_LOCKED   = "locked";
    private static final String F_COST     = "cost";

    private final MongoCollection<Document> col;

    /**
     * @param collectionName e.g. prefix + "playerwarps"
     */
    public MongoPlayerWarpStorage(MongoClient client, String dbName, String collectionName) {
        this.col = client.getDatabase(dbName).getCollection(collectionName);
        // Unique warp id
        col.createIndex(Indexes.ascending(F_ID), new IndexOptions().unique(true));
        try {
            col.createIndex(Indexes.ascending(F_OWNER));
            col.createIndex(Indexes.ascending(F_NAME));
        } catch (Throwable ignored) {}
    }

    @Override
    public void save(PlayerWarp warp) {
        Document d = toDoc(warp);
        col.replaceOne(
                Filters.eq(F_ID, warp.getId()),
                d,
                new ReplaceOptions().upsert(true)
        );
    }

    @Override
    public PlayerWarp getById(String id) {
        Document d = col.find(Filters.eq(F_ID, id)).first();
        return fromDoc(d);
    }

    @Override
    public PlayerWarp getByOwnerAndName(UUID owner, String nameLower) {
        Document d = col.find(Filters.and(
                Filters.eq(F_OWNER, owner.toString()),
                Filters.eq(F_NAME, nameLower.trim().toLowerCase(Locale.ROOT))
        )).first();
        return fromDoc(d);
    }

    @Override
    public boolean delete(String id) {
        return col.deleteOne(Filters.eq(F_ID, id)).getDeletedCount() > 0;
    }

    @Override
    public List<PlayerWarp> listByOwner(UUID owner) {
        List<PlayerWarp> out = new ArrayList<>();
        for (Document d : col.find(Filters.eq(F_OWNER, owner.toString()))) {
            PlayerWarp w = fromDoc(d);
            if (w != null) out.add(w);
        }
        return out;
    }

    @Override
    public List<PlayerWarp> listAll() {
        List<PlayerWarp> out = new ArrayList<>();
        for (Document d : col.find()) {
            PlayerWarp w = fromDoc(d);
            if (w != null) out.add(w);
        }
        return out;
    }

    /* ------------- helpers ------------- */

    private Document toDoc(PlayerWarp warp) {
        Location loc = warp.getLocation();
        Document d = new Document();
        d.put(F_ID, warp.getId());
        d.put(F_OWNER, warp.getOwner().toString());
        d.put(F_NAME, warp.getName().trim().toLowerCase(Locale.ROOT));

        if (loc.getWorld() != null) {
            d.put(F_WORLD, loc.getWorld().getName());
        }
        d.put(F_X, loc.getX());
        d.put(F_Y, loc.getY());
        d.put(F_Z, loc.getZ());
        d.put(F_YAW, loc.getYaw());
        d.put(F_PITCH, loc.getPitch());

        d.put(F_DESC, warp.getDescription());
        d.put(F_CATEGORY, warp.getCategory());
        d.put(F_LOCKED, warp.isLocked());
        d.put(F_COST, warp.getCost());

        return d;
    }

    private PlayerWarp fromDoc(Document d) {
        if (d == null) return null;

        String worldName = d.getString(F_WORLD);
        World world = (worldName == null ? null : Bukkit.getWorld(worldName));
        if (world == null) {
            // World not loaded → you can decide to skip or handle differently
            return null;
        }

        double x = d.getDouble(F_X);
        double y = d.getDouble(F_Y);
        double z = d.getDouble(F_Z);
        float yaw = d.get(F_YAW) == null ? 0f : ((Number) d.get(F_YAW)).floatValue();
        float pitch = d.get(F_PITCH) == null ? 0f : ((Number) d.get(F_PITCH)).floatValue();

        Location loc = new Location(world, x, y, z, yaw, pitch);

        String id = d.getString(F_ID);
        UUID owner = UUID.fromString(d.getString(F_OWNER));
        String name = d.getString(F_NAME);

        PlayerWarp warp = new PlayerWarp(id, owner, name, loc);
        warp.setDescription(d.getString(F_DESC));
        warp.setCategory(d.getString(F_CATEGORY));
        warp.setLocked(d.getBoolean(F_LOCKED, false));
        warp.setCost(d.get(F_COST) == null ? 0.0 : ((Number) d.get(F_COST)).doubleValue());

        return warp;
    }
}
