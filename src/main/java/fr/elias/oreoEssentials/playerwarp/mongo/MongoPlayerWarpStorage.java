// src/main/java/fr/elias/oreoEssentials/services/mongoservices/MongoPlayerWarpStorage.java
package fr.elias.oreoEssentials.playerwarp.mongo;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import fr.elias.oreoEssentials.playerwarp.PlayerWarp;
import fr.elias.oreoEssentials.playerwarp.PlayerWarpStorage;
import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.stream.Collectors;

public class MongoPlayerWarpStorage implements PlayerWarpStorage {

    // Core fields
    private static final String F_ID       = "id";
    private static final String F_OWNER    = "owner";
    private static final String F_NAME     = "name";
    private static final String F_WORLD    = "world";
    private static final String F_X        = "x";
    private static final String F_Y        = "y";
    private static final String F_Z        = "z";
    private static final String F_YAW      = "yaw";
    private static final String F_PITCH    = "pitch";

    // Extra metadata
    private static final String F_DESC     = "description";
    private static final String F_CATEGORY = "category";
    private static final String F_LOCKED   = "locked";
    private static final String F_COST     = "cost";
    private static final String F_ICON     = "icon";

    // Whitelist
    private static final String F_WL_ENABLED = "whitelist_enabled";
    private static final String F_WL_PLAYERS = "whitelist_players";

    //  managers & password
    private static final String F_MANAGERS = "managers";
    private static final String F_PASSWORD = "password";

    private final MongoCollection<Document> col;

    /**
     * @param collectionName e.g. prefix + "playerwarps"
     */
    public MongoPlayerWarpStorage(MongoClient client, String dbName, String collectionName) {
        this.col = client.getDatabase(dbName).getCollection(collectionName);

        // Unique id per warp
        col.createIndex(Indexes.ascending(F_ID), new IndexOptions().unique(true));

        // Fast lookup by owner + name (what /pw <name> uses)
        col.createIndex(Indexes.compoundIndex(
                Indexes.ascending(F_OWNER),
                Indexes.ascending(F_NAME)
        ));
    }

    // ------------------------------------------------------
    // Storage API
    // ------------------------------------------------------

    @Override
    public void save(PlayerWarp warp) {
        Document d = toDoc(warp);
        col.replaceOne(
                Filters.eq(F_ID, warp.getId()),
                d,
                new com.mongodb.client.model.ReplaceOptions().upsert(true)
        );
    }

    @Override
    public PlayerWarp getById(String id) {
        Document d = col.find(Filters.eq(F_ID, id)).first();
        return fromDoc(d);
    }

    @Override
    public PlayerWarp getByOwnerAndName(UUID owner, String nameLower) {
        if (owner == null || nameLower == null) return null;

        Document d = col.find(Filters.and(
                Filters.eq(F_OWNER, owner.toString()),
                Filters.eq(F_NAME, nameLower.trim().toLowerCase(Locale.ROOT))
        )).first();

        return fromDoc(d);
    }

    @Override
    public boolean delete(String id) {
        if (id == null || id.isEmpty()) return false;
        return col.deleteOne(Filters.eq(F_ID, id)).getDeletedCount() > 0;
    }

    @Override
    public List<PlayerWarp> listByOwner(UUID owner) {
        if (owner == null) return Collections.emptyList();

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

    // ------------------------------------------------------
    // Document <-> PlayerWarp
    // ------------------------------------------------------

    private Document toDoc(PlayerWarp warp) {
        Location loc = warp.getLocation();
        Document d = new Document();

        d.put(F_ID, warp.getId());
        d.put(F_OWNER, warp.getOwner().toString());
        // store lowercase name for consistent lookup
        d.put(F_NAME, warp.getName() == null
                ? ""
                : warp.getName().trim().toLowerCase(Locale.ROOT));

        if (loc != null) {
            if (loc.getWorld() != null) {
                d.put(F_WORLD, loc.getWorld().getName());
            }
            d.put(F_X, loc.getX());
            d.put(F_Y, loc.getY());
            d.put(F_Z, loc.getZ());
            d.put(F_YAW, loc.getYaw());
            d.put(F_PITCH, loc.getPitch());
        }

        d.put(F_DESC, warp.getDescription());
        d.put(F_CATEGORY, warp.getCategory());
        d.put(F_LOCKED, warp.isLocked());
        d.put(F_COST, warp.getCost());

        // Icon (serialized map)
        if (warp.getIcon() != null) {
            d.put(F_ICON, warp.getIcon().serialize());
        } else {
            d.remove(F_ICON);
        }

        // Whitelist
        d.put(F_WL_ENABLED, warp.isWhitelistEnabled());
        List<String> wl = warp.getWhitelist().stream()
                .map(UUID::toString)
                .collect(Collectors.toList());
        d.put(F_WL_PLAYERS, wl);

        //  managers
        if (warp.getManagers() != null && !warp.getManagers().isEmpty()) {
            List<String> mgr = warp.getManagers().stream()
                    .map(UUID::toString)
                    .collect(Collectors.toList());
            d.put(F_MANAGERS, mgr);
        } else {
            d.remove(F_MANAGERS);
        }

        //  password
        if (warp.getPassword() != null && !warp.getPassword().isEmpty()) {
            d.put(F_PASSWORD, warp.getPassword());
        } else {
            d.remove(F_PASSWORD);
        }

        return d;
    }

    @SuppressWarnings("unchecked")
    private PlayerWarp fromDoc(Document d) {
        if (d == null) return null;

        String worldName = d.getString(F_WORLD);
        World world = (worldName == null ? null : Bukkit.getWorld(worldName));
        if (world == null) {
            // World not loaded or missing → skip this warp
            return null;
        }

        double x = num(d, F_X, 0.0);
        double y = num(d, F_Y, 0.0);
        double z = num(d, F_Z, 0.0);
        float yaw = (float) num(d, F_YAW, 0.0);
        float pitch = (float) num(d, F_PITCH, 0.0);

        Location loc = new Location(world, x, y, z, yaw, pitch);

        String id = d.getString(F_ID);
        String ownerStr = d.getString(F_OWNER);
        if (id == null || ownerStr == null) return null;

        UUID owner;
        try {
            owner = UUID.fromString(ownerStr);
        } catch (IllegalArgumentException ex) {
            return null;
        }

        String name = d.getString(F_NAME);
        if (name == null) name = "";

        // Whitelist fields (safe defaults for legacy docs)
        boolean whitelistEnabled = d.getBoolean(F_WL_ENABLED, false);
        Set<UUID> whitelist = new HashSet<>();
        Object rawList = d.get(F_WL_PLAYERS);
        if (rawList instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof String s) {
                    try {
                        whitelist.add(UUID.fromString(s));
                    } catch (IllegalArgumentException ignored) { }
                }
            }
        }

        // Use constructor that supports whitelist
        PlayerWarp warp = new PlayerWarp(id, owner, name, loc, whitelistEnabled, whitelist);

        // Extra metadata
        warp.setDescription(d.getString(F_DESC));
        warp.setCategory(d.getString(F_CATEGORY));
        warp.setLocked(d.getBoolean(F_LOCKED, false));
        warp.setCost(num(d, F_COST, 0.0));

        // Icon
        Object rawIcon = d.get(F_ICON);
        if (rawIcon instanceof Map<?, ?> rawMap) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> iconMap = (Map<String, Object>) rawMap;
                ItemStack icon = ItemStack.deserialize(iconMap);
                warp.setIcon(icon);
            } catch (Exception ignored) {
                // if something is wrong with stored data, just ignore and keep default icon
            }
        }

        //  managers
        Object rawMgrList = d.get(F_MANAGERS);
        Set<UUID> managers = new HashSet<>();
        if (rawMgrList instanceof List<?> mgrList) {
            for (Object o : mgrList) {
                if (o instanceof String s) {
                    try {
                        managers.add(UUID.fromString(s));
                    } catch (IllegalArgumentException ignored) { }
                }
            }
        }
        warp.setManagers(managers);

        //  password
        String pwd = d.getString(F_PASSWORD);
        warp.setPassword(pwd); // PlayerWarp already normalizes null / empty

        return warp;
    }

    private static double num(Document d, String key, double def) {
        Object o = d.get(key);
        if (o instanceof Number n) return n.doubleValue();
        return def;
    }
}
