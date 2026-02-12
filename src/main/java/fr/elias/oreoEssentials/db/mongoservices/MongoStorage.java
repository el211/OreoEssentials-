package fr.elias.oreoEssentials.db.mongoservices;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.UpdateOptions;
import fr.elias.oreoEssentials.config.ConfigService;
import fr.elias.oreoEssentials.modules.homes.home.HomeService;
import fr.elias.oreoEssentials.services.StorageApi;
import fr.elias.oreoEssentials.util.LocUtil;
import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.combine;
import static com.mongodb.client.model.Updates.set;
import static com.mongodb.client.model.Updates.unset;

public class MongoStorage implements StorageApi {
    private final Plugin plugin;
    private final MongoClient client;
    private final MongoDatabase db;
    private final MongoCollection<Document> colSpawn;
    private final MongoCollection<Document> colWarps;
    private final MongoCollection<Document> colPlayers;

    public MongoStorage(Plugin plugin, ConfigService cfg) {
        this.plugin = plugin;
        this.client = MongoClients.create(cfg.mongoUri());
        this.db = client.getDatabase(cfg.mongoDatabase());
        String p = cfg.mongoPrefix();
        this.colSpawn = db.getCollection(p + "spawn");
        this.colWarps = db.getCollection(p + "warps");
        this.colPlayers = db.getCollection(p + "playerdata");
        // indexes are optional here; data set is small
    }
    @Override
    public void setBackData(UUID uuid, Map<String, Object> data) {
        String id = uuid.toString();

        if (data == null) {
            colPlayers.updateOne(eq("_id", id), unset("back"));
            return;
        }

        Document backDoc = new Document();
        backDoc.put("server", data.get("server"));
        backDoc.put("world", data.get("world"));
        backDoc.put("x", data.get("x"));
        backDoc.put("y", data.get("y"));
        backDoc.put("z", data.get("z"));
        backDoc.put("yaw", data.get("yaw"));
        backDoc.put("pitch", data.get("pitch"));

        colPlayers.updateOne(
                eq("_id", id),
                combine(
                        set("_id", id),
                        set("back", backDoc)
                ),
                new UpdateOptions().upsert(true)
        );
    }

    @Override
    public Map<String, Object> getBackData(UUID uuid) {
        Document d = colPlayers.find(eq("_id", uuid.toString()))
                .projection(new Document("back", 1))
                .first();
        if (d == null) return null;

        Document back = d.get("back", Document.class);
        if (back == null) return null;

        return new LinkedHashMap<>(back);
    }




    @Override
    public void setSpawn(String server, Location loc) {
        String id = "spawn:" + (server == null ? "" : server.trim().toLowerCase(Locale.ROOT));
        Document d = new Document("_id", id).append("loc", LocUtil.toDoc(loc));
        colSpawn.replaceOne(eq("_id", id), d, new com.mongodb.client.model.ReplaceOptions().upsert(true));
    }

    @Override
    public Location getSpawn(String server) {
        String id = "spawn:" + (server == null ? "" : server.trim().toLowerCase(Locale.ROOT));
        Document d = colSpawn.find(eq("_id", id)).first();
        if (d != null) return LocUtil.fromDoc(d.get("loc", Document.class));

        Document legacy = colSpawn.find(eq("_id", "spawn")).first();
        if (legacy == null) return null;
        return LocUtil.fromDoc(legacy.get("loc", Document.class));
    }



    @Override public void setWarp(String name, Location loc) {
        String id = name.toLowerCase(Locale.ROOT);
        Document d = new Document("_id", id).append("loc", LocUtil.toDoc(loc));
        colWarps.replaceOne(eq("_id", id), d, new com.mongodb.client.model.ReplaceOptions().upsert(true));
    }

    @Override public boolean delWarp(String name) {
        return colWarps.deleteOne(eq("_id", name.toLowerCase(Locale.ROOT))).getDeletedCount() > 0;
    }

    @Override public Location getWarp(String name) {
        Document d = colWarps.find(eq("_id", name.toLowerCase(Locale.ROOT))).first();
        if (d == null) return null;
        return LocUtil.fromDoc(d.get("loc", Document.class));
    }

    @Override public Set<String> listWarps() {
        FindIterable<Document> it = colWarps.find().projection(new Document("_id", 1));
        return java.util.stream.StreamSupport.stream(it.spliterator(), false)
                .map(doc -> doc.getString("_id"))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }


    @Override public boolean setHome(UUID uuid, String name, Location loc) {
        String id = uuid.toString();
        String key = "homes." + name.toLowerCase(Locale.ROOT);
        colPlayers.updateOne(eq("_id", id),
                combine(set("_id", id), set(key, LocUtil.toDoc(loc))),
                new com.mongodb.client.model.UpdateOptions().upsert(true));
        return true;
    }

    @Override public boolean delHome(UUID uuid, String name) {
        String id = uuid.toString();
        String key = "homes." + name.toLowerCase(Locale.ROOT);
        var res = colPlayers.updateOne(eq("_id", id), unset(key));
        return res.getModifiedCount() > 0;
    }

    @Override public Location getHome(UUID uuid, String name) {
        Document d = colPlayers.find(eq("_id", uuid.toString()))
                .projection(new Document("homes." + name.toLowerCase(Locale.ROOT), 1)).first();
        if (d == null) return null;
        Document homes = d.get("homes", Document.class);
        if (homes == null) return null;
        Document loc = homes.get(name.toLowerCase(Locale.ROOT), Document.class);
        if (loc == null) return null;
        return LocUtil.fromDoc(loc);
    }

    @Override public Set<String> homes(UUID uuid) {
        Document d = colPlayers.find(eq("_id", uuid.toString()))
                .projection(new Document("homes", 1)).first();
        if (d == null) return java.util.Set.of();
        Document homes = d.get("homes", Document.class);
        if (homes == null) return java.util.Set.of();
        return homes.keySet().stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }


    @Override
    public Map<String, HomeService.StoredHome> listHomes(UUID owner) {
        Map<String, HomeService.StoredHome> out = new LinkedHashMap<>();

        Document d = colPlayers.find(eq("_id", owner.toString()))
                .projection(new Document("homes", 1)).first();
        if (d == null) return out;
        Document homes = d.get("homes", Document.class);
        if (homes == null) return out;

        String defaultServer = Bukkit.getServer().getName();

        for (String name : homes.keySet()) {
            Document loc = homes.get(name, Document.class);
            if (loc == null) continue;

            String world = loc.getString("world");
            if (world == null || world.isBlank()) world = "world";

            double x = num(loc, "x");
            double y = num(loc, "y");
            double z = num(loc, "z");

            out.put(name.toLowerCase(Locale.ROOT),
                    new HomeService.StoredHome(world, x, y, z, defaultServer));
        }
        return out;
    }

    private static double num(Document d, String key) {
        Number n = d.get(key, Number.class);
        return n == null ? 0.0 : n.doubleValue();
    }



    @Override
    public void setLast(UUID uuid, Location loc) {
        String id = uuid.toString();

        if (loc == null) {
            colPlayers.updateOne(eq("_id", id), unset("lastLocation"));
        } else {
            colPlayers.updateOne(
                    eq("_id", id),
                    combine(
                            set("_id", id),
                            set("lastLocation", LocUtil.toDoc(loc))
                    ),
                    new UpdateOptions().upsert(true)
            );
        }

        StorageApi.super.setLast(uuid, loc);
    }

    @Override
    public Location getLast(UUID uuid) {
        Location fromBack = StorageApi.super.getLast(uuid); // uses getBackData(...)
        if (fromBack != null) return fromBack;

        Document d = colPlayers.find(eq("_id", uuid.toString()))
                .projection(new Document("lastLocation", 1))
                .first();
        if (d == null) return null;

        Document loc = d.get("lastLocation", Document.class);
        if (loc == null) return null;

        return LocUtil.fromDoc(loc);
    }



    @Override public void flush() { /* no-op for Mongo */ }

    @Override public void close() {
        try { client.close(); } catch (Exception ignored) {}
    }
}
