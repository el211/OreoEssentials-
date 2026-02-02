package fr.elias.oreoEssentials.modules.playerwarp.mongo;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import fr.elias.oreoEssentials.modules.playerwarp.PlayerWarpDirectory;
import org.bson.Document;

public class MongoPlayerWarpDirectory implements PlayerWarpDirectory {

    private static final String F_ID        = "id";
    private static final String F_SERVER    = "server";
    private static final String F_PERMISSION = "permission";

    private final MongoCollection<Document> col;


    public MongoPlayerWarpDirectory(MongoClient client, String dbName, String collectionName) {
        this.col = client.getDatabase(dbName).getCollection(collectionName);
        col.createIndex(Indexes.ascending(F_ID), new IndexOptions().unique(true));
        try {
            col.createIndex(Indexes.ascending(F_SERVER));
        } catch (Throwable ignored) {}
    }


    @Override
    public void setWarpServer(String warpId, String server) {
        col.updateOne(
                Filters.eq(F_ID, warpId),
                Updates.combine(
                        Updates.set(F_SERVER, server),
                        Updates.setOnInsert(F_ID, warpId)
                ),
                new UpdateOptions().upsert(true)
        );
    }

    @Override
    public String getWarpServer(String warpId) {
        Document d = col.find(Filters.eq(F_ID, warpId))
                .projection(Projections.include(F_SERVER))
                .first();
        return d == null ? null : d.getString(F_SERVER);
    }

    @Override
    public void deleteWarp(String warpId) {
        col.deleteOne(Filters.eq(F_ID, warpId));
    }


    @Override
    public String getWarpPermission(String warpId) {
        Document d = col.find(Filters.eq(F_ID, warpId))
                .projection(Projections.include(F_PERMISSION))
                .first();
        String perm = (d == null ? null : d.getString(F_PERMISSION));
        return (perm == null || perm.isBlank()) ? null : perm;
    }

    @Override
    public void setWarpPermission(String warpId, String permission) {
        if (permission == null || permission.isBlank()) {
            col.updateOne(
                    Filters.eq(F_ID, warpId),
                    Updates.unset(F_PERMISSION)
            );
            return;
        }

        col.updateOne(
                Filters.eq(F_ID, warpId),
                Updates.combine(
                        Updates.set(F_PERMISSION, permission),
                        Updates.setOnInsert(F_ID, warpId)
                ),
                new UpdateOptions().upsert(true)
        );
    }
}
