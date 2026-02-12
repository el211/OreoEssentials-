package fr.elias.oreoEssentials.services;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fr.elias.oreoEssentials.modules.homes.home.HomeService;
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


    @Override
    public void setBackData(UUID uuid, Map<String, Object> backData) {
        synchronized (lock) {
            PlayerData p = data.players.computeIfAbsent(uuid.toString(), k -> new PlayerData());
            if (backData == null || backData.isEmpty()) {
                p.back = null;
            } else {
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
            return new LinkedHashMap<>(p.back);
        }
    }

    /* ---------------- last location (compat + uses /back) ---------------- */

    @Override
    public void setLast(UUID uuid, Location loc) {

        StorageApi.super.setLast(uuid, loc);

        synchronized (lock) {
            PlayerData p = data.players.computeIfAbsent(uuid.toString(), k -> new PlayerData());
            p.lastLocation = toDoc(loc);
            save();
        }
    }

    @Override
    public Location getLast(UUID uuid) {
        Location fromBack = StorageApi.super.getLast(uuid);
        if (fromBack != null) {
            return fromBack;
        }

        synchronized (lock) {
            PlayerData p = data.players.get(uuid.toString());
            if (p == null) return null;
            return fromDoc(p.lastLocation);
        }
    }


    @Override
    public void flush() {
    }

    @Override
    public void close() {
    }


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

                if (data.spawns == null) data.spawns = new LinkedHashMap<>();

                String thisServer = org.bukkit.Bukkit.getServer().getName().toLowerCase(Locale.ROOT);
                if (data.spawn != null && !data.spawns.containsKey(thisServer)) {
                    data.spawns.put(thisServer, data.spawn);
                    // optionnel: data.spawn = null;
                    save();
                }
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


    private static final class Data {
        Map<String, Document> spawns = new LinkedHashMap<>();

        Document spawn = null;
        Map<String, Document> warps = new LinkedHashMap<>();
        Map<String, PlayerData> players = new LinkedHashMap<>();
    }
    @Override
    public void setSpawn(String server, Location loc) {
        if (server == null) server = "";
        final String key = server.toLowerCase(Locale.ROOT);

        synchronized (lock) {
            if (loc == null) {
                if (data.spawns != null) data.spawns.remove(key);
            } else {
                if (data.spawns == null) data.spawns = new LinkedHashMap<>();
                data.spawns.put(key, toDoc(loc));
            }
            save();
        }
    }

    @Override
    public Location getSpawn(String server) {
        if (server == null) server = "";
        final String key = server.toLowerCase(Locale.ROOT);

        synchronized (lock) {
            if (data.spawns != null) {
                Document d = data.spawns.get(key);
                if (d != null) return fromDoc(d);
            }
            return fromDoc(data.spawn);
        }
    }

    private static final class PlayerData {
        Document lastLocation = null;
        Map<String, Document> homes = new LinkedHashMap<>();
        Map<String, Object> back = null;
    }
}
