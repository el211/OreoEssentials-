// File: src/main/java/fr/elias/oreoEssentials/services/JsonStorage.java
package fr.elias.oreoEssentials.services;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bson.Document;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.*;
import java.util.stream.Collectors;

public class JsonStorage implements StorageApi {

    private final File file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private final Object lock = new Object();
    private Data data;

    public JsonStorage(Plugin plugin) {
        this.file = new File(plugin.getDataFolder(), "essentials.json");
        load();
    }

    /* ---------------- spawn ---------------- */

    @Override
    public void setSpawn(Location loc) {
        synchronized (lock) {
            data.spawn = toDoc(loc);
            save();
        }
    }

    @Override
    public Location getSpawn() {
        synchronized (lock) {
            return fromDoc(data.spawn);
        }
    }

    /* ---------------- warps ---------------- */

    @Override
    public void setWarp(String name, Location loc) {
        synchronized (lock) {
            data.warps.put(name.toLowerCase(Locale.ROOT), toDoc(loc));
            save();
        }
    }

    @Override
    public boolean delWarp(String name) {
        synchronized (lock) {
            boolean removed = (data.warps.remove(name.toLowerCase(Locale.ROOT)) != null);
            if (removed) save();
            return removed;
        }
    }

    @Override
    public Location getWarp(String name) {
        synchronized (lock) {
            return fromDoc(data.warps.get(name.toLowerCase(Locale.ROOT)));
        }
    }

    @Override
    public Set<String> listWarps() {
        synchronized (lock) {
            return data.warps.keySet().stream()
                    .sorted()
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
    }

    /* ---------------- homes ---------------- */

    @Override
    public boolean setHome(UUID uuid, String name, Location loc) {
        synchronized (lock) {
            PlayerData p = data.players.computeIfAbsent(uuid.toString(), k -> new PlayerData());
            p.homes.put(name.toLowerCase(Locale.ROOT), toDoc(loc));
            save();
            return true;
        }
    }

    @Override
    public boolean delHome(UUID uuid, String name) {
        synchronized (lock) {
            PlayerData p = data.players.get(uuid.toString());
            if (p == null) return false;
            boolean removed = (p.homes.remove(name.toLowerCase(Locale.ROOT)) != null);
            if (removed) save();
            return removed;
        }
    }

    @Override
    public Location getHome(UUID uuid, String name) {
        synchronized (lock) {
            PlayerData p = data.players.get(uuid.toString());
            if (p == null) return null;
            return fromDoc(p.homes.get(name.toLowerCase(Locale.ROOT)));
        }
    }

    @Override
    public Set<String> homes(UUID uuid) {
        synchronized (lock) {
            PlayerData p = data.players.get(uuid.toString());
            if (p == null) return Collections.emptySet();
            return p.homes.keySet().stream()
                    .sorted()
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
    }

    @Override
    public Map<String, HomeService.StoredHome> listHomes(UUID owner) {
        synchronized (lock) {
            Map<String, HomeService.StoredHome> out = new LinkedHashMap<>();

            PlayerData p = data.players.get(owner.toString());
            if (p == null || p.homes == null) return out;

            String server = org.bukkit.Bukkit.getServer().getName();

            for (Map.Entry<String, Document> e : p.homes.entrySet()) {
                String name = e.getKey().toLowerCase(Locale.ROOT);
                Document d = e.getValue();
                if (d == null) continue;

                String world = d.getString("world");
                double x = asDouble(d.get("x"));
                double y = asDouble(d.get("y"));
                double z = asDouble(d.get("z"));

                out.put(name, new HomeService.StoredHome(world, x, y, z, server));
            }
            return out;
        }
    }

    private static double asDouble(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(v)); } catch (Exception e) { return 0.0; }
    }

    /* ---------------- BACK: new global storage ---------------- */

    @Override
    public void setBackData(UUID uuid, Map<String, Object> backData) {
        synchronized (lock) {
            PlayerData p = data.players.computeIfAbsent(uuid.toString(), k -> new PlayerData());
            if (backData == null || backData.isEmpty()) {
                p.back = null;
            } else {
                // copy to avoid accidental external mutation
                p.back = new LinkedHashMap<>(backData);
            }
            save();
        }
    }

    @Override
    public Map<String, Object> getBackData(UUID uuid) {
        synchronized (lock) {
            PlayerData p = data.players.get(uuid.toString());
            if (p == null || p.back == null) return null;
            // return a copy to avoid leaking internal map
            return new LinkedHashMap<>(p.back);
        }
    }

    /* ---------------- last location (compat + uses /back) ---------------- */

    @Override
    public void setLast(UUID uuid, Location loc) {
        // NEW: update /back data using the default StorageApi logic
        // This calls setBackData(UUID, Map...) above (which is synchronized).
        StorageApi.super.setLast(uuid, loc);

        // LEGACY: keep the old 'lastLocation' field for anything that still uses it
        synchronized (lock) {
            PlayerData p = data.players.computeIfAbsent(uuid.toString(), k -> new PlayerData());
            p.lastLocation = toDoc(loc);
            save();
        }
    }

    @Override
    public Location getLast(UUID uuid) {
        // 1) Prefer /back global data (server-aware) via the default implementation
        Location fromBack = StorageApi.super.getLast(uuid);
        if (fromBack != null) {
            return fromBack;
        }

        // 2) Fallback to the old local-only 'lastLocation'
        synchronized (lock) {
            PlayerData p = data.players.get(uuid.toString());
            if (p == null) return null;
            return fromDoc(p.lastLocation);
        }
    }

    /* ---------------- lifecycle ---------------- */

    @Override
    public void flush() {
        // no-op for JSON; every change calls save()
    }

    @Override
    public void close() {
        // no-op
    }

    /* ---------------- file I/O ---------------- */

    private void load() {
        try {
            if (!file.getParentFile().exists()) file.getParentFile().mkdirs();
            if (!file.exists()) {
                data = new Data();
                save();
                return;
            }
            try (FileReader r = new FileReader(file)) {
                data = gson.fromJson(r, Data.class);
                if (data == null) data = new Data();
                if (data.warps == null) data.warps = new LinkedHashMap<>();
                if (data.players == null) data.players = new LinkedHashMap<>();
            }
        } catch (Exception e) {
            e.printStackTrace();
            data = new Data();
        }
    }

    private void save() {
        try (FileWriter w = new FileWriter(file)) {
            gson.toJson(data, w);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /* ---------------- location helpers ---------------- */

    private static Document toDoc(Location loc) {
        if (loc == null) return null;
        return new Document()
                .append("world", loc.getWorld() != null ? loc.getWorld().getName() : null)
                .append("x", loc.getX())
                .append("y", loc.getY())
                .append("z", loc.getZ())
                .append("yaw", loc.getYaw())
                .append("pitch", loc.getPitch());
    }

    private static Location fromDoc(Document d) {
        if (d == null) return null;
        String world = d.getString("world");
        World w = (world != null) ? org.bukkit.Bukkit.getWorld(world) : null;
        Number x = (Number) d.getOrDefault("x", 0.0);
        Number y = (Number) d.getOrDefault("y", 0.0);
        Number z = (Number) d.getOrDefault("z", 0.0);
        Number yaw = (Number) d.getOrDefault("yaw", 0.0f);
        Number pitch = (Number) d.getOrDefault("pitch", 0.0f);
        Location loc = new Location(w, x.doubleValue(), y.doubleValue(), z.doubleValue());
        loc.setYaw(yaw.floatValue());
        loc.setPitch(pitch.floatValue());
        return loc;
    }

    /* ---------------- DTOs ---------------- */

    private static final class Data {
        Document spawn = null;
        Map<String, Document> warps = new LinkedHashMap<>();
        Map<String, PlayerData> players = new LinkedHashMap<>();
    }

    private static final class PlayerData {
        Document lastLocation = null;                 // legacy /back storage
        Map<String, Document> homes = new LinkedHashMap<>();
        Map<String, Object> back = null;             // NEW: global /back data (BackLocation.toMap)
    }
}
