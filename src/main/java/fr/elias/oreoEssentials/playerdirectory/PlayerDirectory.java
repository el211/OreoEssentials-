package fr.elias.oreoEssentials.playerdirectory;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.UpdateOptions;
import org.bson.Document;
import java.util.regex.Pattern;
import static com.mongodb.client.model.Filters.*;
import java.time.Instant;
import java.util.*;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.*;

public class PlayerDirectory {
    private final MongoCollection<Document> coll;

    public PlayerDirectory(MongoClient client, String db, String prefix) {
        this.coll = client.getDatabase(db).getCollection(prefix + "player_directory");
        ensureIndexes();
    }

    private void ensureIndexes() {
        try { coll.createIndex(new Document("uuid", 1), new IndexOptions().unique(true)); } catch (Throwable ignored) {}
        try { coll.createIndex(new Document("nameLower", 1)); } catch (Throwable ignored) {}
        try { coll.createIndex(new Document("currentServer", 1)); } catch (Throwable ignored) {}
        try { coll.createIndex(new Document("lastServer", 1)); } catch (Throwable ignored) {}
    }

    public void saveMapping(String name, UUID uuid) { upsertPresence(uuid, name, null); }
    public void saveMapping(UUID uuid, String name) { upsertPresence(uuid, name, null); }

    public UUID lookupUuidByName(String name) {
        if (name == null || name.isBlank()) return null;
        Document d = coll.find(eq("nameLower", name.toLowerCase(Locale.ROOT)))
                .projection(new Document("uuid", 1))
                .first();
        if (d == null) return null;
        try { return UUID.fromString(d.getString("uuid")); } catch (Exception e) { return null; }
    }

    public String lookupNameByUuid(UUID uuid) {
        if (uuid == null) return null;
        Document d = coll.find(eq("uuid", uuid.toString()))
                .projection(new Document("name", 1))
                .first();
        return d == null ? null : d.getString("name");
    }
    public @org.jetbrains.annotations.Nullable String lookupCurrentServer(UUID uuid) {
        if (uuid == null) return null;
        try {
            var doc = coll.find(eq("uuid", uuid.toString())).first();
            if (doc == null) return null;

            String s = doc.getString("currentServer");
            if (s == null && doc.get("presence") instanceof org.bson.Document pres) {
                s = pres.getString("currentServer");
            }
            return (s != null && !s.isBlank()) ? s : null;
        } catch (Throwable t) {
            return null;
        }
    }

    public void upsertPresence(UUID uuid, String name, String server) {
        if (uuid == null) return;
        final String now = Instant.now().toString();
        final String nm  = name == null ? "" : name;
        final String nml = nm.toLowerCase(Locale.ROOT);

        Document doc = new Document("uuid", uuid.toString())
                .append("name", nm)
                .append("nameLower", nml)
                .append("updatedAt", now);

        if (server != null && !server.isBlank()) {
            doc.append("currentServer", server)
                    .append("lastServer", server);
        }

        coll.replaceOne(eq("uuid", uuid.toString()), doc, new ReplaceOptions().upsert(true));
    }

    public void setCurrentServer(UUID uuid, String server) {
        if (uuid == null || server == null || server.isBlank()) return;
        coll.updateOne(
                eq("uuid", uuid.toString()),
                combine(
                        set("currentServer", server),
                        set("lastServer", server),
                        set("updatedAt", Instant.now().toString())
                ),
                new UpdateOptions().upsert(true)
        );
    }

    public void clearCurrentServer(UUID uuid) {
        if (uuid == null) return;
        Document d = coll.find(eq("uuid", uuid.toString()))
                .projection(new Document("currentServer", 1))
                .first();
        if (d == null) return;

        String cur = d.getString("currentServer");
        if (cur == null || cur.isBlank()) {
            coll.updateOne(eq("uuid", uuid.toString()), set("updatedAt", Instant.now().toString()));
        } else {
            coll.updateOne(eq("uuid", uuid.toString()),
                    combine(
                            set("lastServer", cur),
                            unset("currentServer"),
                            set("updatedAt", Instant.now().toString())
                    ));
        }
    }

    public String getCurrentOrLastServer(UUID uuid) {
        if (uuid == null) return null;
        Document d = coll.find(eq("uuid", uuid.toString()))
                .projection(new Document("currentServer", 1).append("lastServer", 1))
                .first();
        if (d == null) return null;
        String cur = d.getString("currentServer");
        if (cur != null && !cur.isBlank()) return cur;
        String last = d.getString("lastServer");
        return (last == null || last.isBlank()) ? null : last;
    }

    public void updateName(UUID uuid, String name) {
        if (uuid == null || name == null) return;
        coll.updateOne(
                eq("uuid", uuid.toString()),
                combine(
                        set("name", name),
                        set("nameLower", name.toLowerCase(Locale.ROOT)),
                        set("updatedAt", Instant.now().toString())
                ),
                new UpdateOptions().upsert(true)
        );
    }
    // inside PlayerDirectory
    public Collection<String> suggestOnlineNames(String prefix, int limit) {
        if (limit <= 0) limit = 50;

        String want = (prefix == null) ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();


        var filter = want.isEmpty()
                ? and(ne("currentServer", null), ne("currentServer", ""))
                : and(
                regex("nameLower", "^" + Pattern.quote(want)),
                ne("currentServer", null),
                ne("currentServer", "")
        );

        for (Document doc : coll.find(filter)
                .projection(new Document("name", 1))
                .limit(limit)) {

            String name = doc.getString("name");
            if (name != null && !name.isBlank()) {
                out.add(name);
            }
        }

        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }
    public Collection<UUID> onlinePlayers() {
        List<UUID> out = new ArrayList<>();
        for (Document doc : coll.find(
                and(ne("currentServer", null), ne("currentServer", ""))
        ).projection(new Document("uuid", 1))) {
            String raw = doc.getString("uuid");
            try { out.add(UUID.fromString(raw)); } catch (Exception ignored) {}
        }
        return out;
    }



}
