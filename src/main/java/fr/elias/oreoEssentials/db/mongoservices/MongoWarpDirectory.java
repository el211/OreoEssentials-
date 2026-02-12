package fr.elias.oreoEssentials.db.mongoservices;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import fr.elias.oreoEssentials.modules.warps.rabbit.WarpDirectory;
import org.bson.Document;

import java.util.Locale;

public class MongoWarpDirectory implements WarpDirectory {

    private static final String F_NAME       = "name";
    private static final String F_SERVER     = "server";
    private static final String F_PERMISSION = "permission";

    private final MongoCollection<Document> col;


    public MongoWarpDirectory(MongoClient client, String dbName, String collectionName) {
        this.col = client.getDatabase(dbName).getCollection(collectionName);
        col.createIndex(Indexes.ascending(F_NAME), new IndexOptions().unique(true));
        try {
            col.createIndex(Indexes.ascending(F_SERVER));
        } catch (Throwable ignored) {}
    }

    private static String key(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }


    @Override
    public void setWarpServer(String warpName, String server) {
        final String n = key(warpName);
        col.updateOne(
                Filters.eq(F_NAME, n),
                Updates.combine(
                        Updates.set(F_SERVER, server),
                        Updates.setOnInsert(F_NAME, n)
                ),
                new UpdateOptions().upsert(true)
        );
    }

    @Override
    public String getWarpServer(String warpName) {
        final String n = key(warpName);
        final Document d = col.find(Filters.eq(F_NAME, n))
                .projection(Projections.include(F_SERVER))
                .first();
        return d == null ? null : d.getString(F_SERVER);
    }

    @Override
    public void deleteWarp(String warpName) {
        col.deleteOne(Filters.eq(F_NAME, key(warpName)));
    }


    @Override
    public String getWarpPermission(String warpName) {
        final String n = key(warpName);
        final Document d = col.find(Filters.eq(F_NAME, n))
                .projection(Projections.include(F_PERMISSION))
                .first();
        String perm = (d == null ? null : d.getString(F_PERMISSION));
        return (perm == null || perm.isBlank()) ? null : perm;
    }

    @Override
    public void setWarpPermission(String warpName, String permission) {
        final String n = key(warpName);
        if (permission == null || permission.isBlank()) {
            // Clear permission (public warp)
            col.updateOne(
                    Filters.eq(F_NAME, n),
                    Updates.unset(F_PERMISSION)
            );
            return;
        }
        col.updateOne(
                Filters.eq(F_NAME, n),
                Updates.combine(
                        Updates.set(F_PERMISSION, permission),
                        Updates.setOnInsert(F_NAME, n)
                ),
                new UpdateOptions().upsert(true)
        );
    }
}
