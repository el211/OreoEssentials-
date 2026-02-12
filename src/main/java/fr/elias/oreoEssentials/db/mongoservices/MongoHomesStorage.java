package fr.elias.oreoEssentials.db.mongoservices;

import com.mongodb.client.*;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.ReplaceOptions;
import fr.elias.oreoEssentials.services.StorageApi;
import fr.elias.oreoEssentials.modules.homes.home.HomeService;   // <-- add
import java.util.LinkedHashMap;                        // optional, if not already present
import java.util.Map;                                  // <-- add

import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.*;
import java.util.stream.Collectors;


public class MongoHomesStorage implements StorageApi {

    private final MongoCollection<Document> homesCol;
    private final MongoCollection<Document> warpsCol;
    private final MongoCollection<Document> metaCol;
    private final String localServer; // used when setting homes

    public MongoHomesStorage(MongoClient client, String dbName, String prefix, String localServer) {
        MongoDatabase db = client.getDatabase(dbName);
        this.homesCol = db.getCollection(prefix + "homes");
        this.warpsCol = db.getCollection(prefix + "warps");
        this.metaCol  = db.getCollection(prefix + "meta");
        this.localServer = localServer; // <-- must be configService.serverName()

        // indexes
        homesCol.createIndex(Indexes.ascending("uuid", "name"), new IndexOptions().unique(true));
        warpsCol.createIndex(Indexes.ascending("name"), new IndexOptions().unique(true));
        metaCol.createIndex(Indexes.ascending("type")); // non-unique, we filter by type + uuid when needed
    }

    /* ---------------- spawn ---------------- */

    @Override
    public void setSpawn(String server, Location loc) {
        String s = (server == null ? "" : server.trim().toLowerCase(Locale.ROOT));
        Document key = new Document("type", "spawn").append("server", s);

        metaCol.replaceOne(
                key,
                new Document(key).append("data", toDoc(loc)),
                new ReplaceOptions().upsert(true)
        );
    }

    @Override
    public Location getSpawn(String server) {
        String s = (server == null ? "" : server.trim().toLowerCase(Locale.ROOT));

        Document d = metaCol.find(Filters.and(
                Filters.eq("type", "spawn"),
                Filters.eq("server", s)
        )).first();

        if (d != null) {
            return fromDoc(d.get("data", Document.class));
        }

        // LEGACY fallback (old global spawn)
        Document legacy = metaCol.find(Filters.eq("type", "spawn")).first();
        if (legacy == null) return null;
        return fromDoc(legacy.get("data", Document.class));
    }

    @Override
    public void setSpawn(Location loc) {
        setSpawn(Bukkit.getServer().getName(), loc);
    }

    @Override
    public Location getSpawn() {
        return getSpawn(Bukkit.getServer().getName());
    }

    @Override
    public void setBackData(UUID uuid, Map<String, Object> data) {
        if (data == null) {
            metaCol.deleteOne(Filters.and(
                    Filters.eq("type", "back"),
                    Filters.eq("uuid", uuid.toString())
            ));
            return;
        }

        Document doc = new Document("type", "back")
                .append("uuid", uuid.toString())
                .append("data", new Document(data));

        metaCol.replaceOne(
                Filters.and(
                        Filters.eq("type", "back"),
                        Filters.eq("uuid", uuid.toString())
                ),
                doc,
                new ReplaceOptions().upsert(true)
        );
    }

    @Override
    public Map<String, Object> getBackData(UUID uuid) {
        Document d = metaCol.find(Filters.and(
                Filters.eq("type", "back"),
                Filters.eq("uuid", uuid.toString())
        )).first();
        if (d == null) return null;

        Document data = d.get("data", Document.class);
        if (data == null) return null;

        return new LinkedHashMap<>(data);
    }



    @Override
    public void setWarp(String name, Location loc) {
        Document key = new Document("name", name.toLowerCase(Locale.ROOT));
        warpsCol.replaceOne(key, key.append("data", toDoc(loc)), new ReplaceOptions().upsert(true));
    }

    @Override
    public boolean delWarp(String name) {
        return warpsCol.deleteOne(Filters.eq("name", name.toLowerCase(Locale.ROOT))).getDeletedCount() > 0;
    }

    @Override
    public Location getWarp(String name) {
        Document d = warpsCol.find(Filters.eq("name", name.toLowerCase(Locale.ROOT))).first();
        if (d == null) return null;
        return fromDoc(d.get("data", Document.class));
    }

    @Override
    public Set<String> listWarps() {
        Set<String> out = new HashSet<>();
        try (MongoCursor<Document> cur = warpsCol.find().projection(new Document("name", 1)).iterator()) {
            while (cur.hasNext()) {
                String n = cur.next().getString("name");
                if (n != null) out.add(n);
            }
        }
        return out.stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }



    @Override
    public boolean setHome(UUID uuid, String name, Location loc) {
        return setHome(uuid, name, loc, localServer);
    }

    /** Extra helper (not in StorageApi): set a home and record the owning server. */
    public boolean setHome(UUID uuid, String name, Location loc, String server) {
        String n = safeName(name);
        World w = loc.getWorld();
        if (w == null) return false;

        Document key = new Document("uuid", uuid.toString()).append("name", n);
        Document doc = new Document(key)
                .append("server", server)
                .append("data", toDoc(loc));

        homesCol.replaceOne(key, doc, new ReplaceOptions().upsert(true));
        return true;
    }
    @Override
    public Map<String, HomeService.StoredHome> listHomes(UUID owner) {
        Map<String, HomeService.StoredHome> out = new LinkedHashMap<>();
        try (MongoCursor<Document> cur = homesCol.find(Filters.eq("uuid", owner.toString())).iterator()) {
            while (cur.hasNext()) {
                Document d = cur.next();
                String name = d.getString("name");
                if (name == null) continue;

                // you store the location in "data": { world, worldName, x, y, z, yaw, pitch }
                Document data = d.get("data", Document.class);
                if (data == null) continue;

                String worldName = data.getString("worldName");
                if (worldName == null || worldName.isBlank()) worldName = "world";

                double x = data.get("x", Number.class).doubleValue();
                double y = data.get("y", Number.class).doubleValue();
                double z = data.get("z", Number.class).doubleValue();

                String server = d.getString("server");
                if (server == null || server.isBlank()) server = Bukkit.getServer().getName();

                out.put(name.toLowerCase(Locale.ROOT),
                        new HomeService.StoredHome(worldName, x, y, z, server));
            }
        }
        return out;
    }

    @Override
    public boolean delHome(UUID uuid, String name) {
        String n = safeName(name);
        return homesCol.deleteOne(Filters.and(
                Filters.eq("uuid", uuid.toString()),
                Filters.eq("name", n)
        )).getDeletedCount() > 0;
    }

    @Override
    public Location getHome(UUID uuid, String name) {
        String n = safeName(name);
        Document d = homesCol.find(Filters.and(
                Filters.eq("uuid", uuid.toString()),
                Filters.eq("name", n)
        )).first();
        if (d == null) return null;
        Document data = d.get("data", Document.class);
        return fromDoc(data);
    }

    @Override
    public Set<String> homes(UUID uuid) {
        Set<String> out = new HashSet<>();
        try (MongoCursor<Document> cur = homesCol.find(Filters.eq("uuid", uuid.toString()))
                .projection(new Document("name", 1)).iterator()) {
            while (cur.hasNext()) {
                String n = cur.next().getString("name");
                if (n != null) out.add(n);
            }
        }
        return out.stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** Extra helper (not in StorageApi): which server owns this home? */
    public String homeServer(UUID uuid, String name) {
        String n = safeName(name);
        Document d = homesCol.find(Filters.and(
                Filters.eq("uuid", uuid.toString()),
                Filters.eq("name", n)
        )).projection(new Document("server", 1)).first();
        return d == null ? null : d.getString("server");
    }


    @Override
    public void setLast(UUID uuid, Location loc) {
        Document key = new Document("type", "last")
                .append("uuid", uuid.toString());

        if (loc == null) {
            metaCol.deleteOne(key);
        } else {
            metaCol.replaceOne(
                    key,
                    key.append("data", toDoc(loc)),
                    new ReplaceOptions().upsert(true)
            );
        }

        StorageApi.super.setLast(uuid, loc);
    }

    @Override
    public Location getLast(UUID uuid) {

        Map<String, Object> backData = getBackData(uuid);
        if (backData != null) {

            return StorageApi.super.getLast(uuid);
        }


        Document d = metaCol.find(Filters.and(
                Filters.eq("type", "last"),
                Filters.eq("uuid", uuid.toString())
        )).first();

        if (d == null) return null;

        Document data = d.get("data", Document.class);
        if (data == null) return null;

        return fromDoc(data);
    }


    @Override
    public void flush() {

    }

    @Override
    public void close() {

    }



    private static String safeName(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    private static Document toDoc(Location loc) {
        World w = loc.getWorld();
        String worldId = (w != null ? w.getUID().toString() : null);
        String worldName = (w != null ? w.getName() : null);
        return new Document()
                .append("world", worldId)
                .append("worldName", worldName)
                .append("x", loc.getX())
                .append("y", loc.getY())
                .append("z", loc.getZ())
                .append("yaw", loc.getYaw())
                .append("pitch", loc.getPitch());
    }

    private static Location fromDoc(Document d) {
        if (d == null) return null;
        String worldId = d.getString("world");
        String worldName = d.getString("worldName");
        World w = null;
        if (worldId != null) {
            try { w = Bukkit.getWorld(UUID.fromString(worldId)); } catch (Exception ignored) {}
        }
        if (w == null && worldName != null) w = Bukkit.getWorld(worldName);
        if (w == null) return null;

        double x = d.get("x", Number.class).doubleValue();
        double y = d.get("y", Number.class).doubleValue();
        double z = d.get("z", Number.class).doubleValue();
        float yaw = d.get("yaw", Number.class).floatValue();
        float pitch = d.get("pitch", Number.class).floatValue();
        return new Location(w, x, y, z, yaw, pitch);
    }
}
