package fr.elias.oreoEssentials.util;

import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.Location;

public final class LocUtil {
    private LocUtil(){}

    public static void write(org.bukkit.configuration.ConfigurationSection sec, Location l) {
        if (l == null || sec == null) return;
        sec.set("world", l.getWorld().getName());
        sec.set("x", l.getX());
        sec.set("y", l.getY());
        sec.set("z", l.getZ());
        sec.set("yaw", l.getYaw());
        sec.set("pitch", l.getPitch());
    }
    public static Location read(org.bukkit.configuration.ConfigurationSection sec) {
        if (sec == null) return null;
        String world = sec.getString("world");
        if (world == null || Bukkit.getWorld(world) == null) return null;
        return new Location(
                Bukkit.getWorld(world),
                sec.getDouble("x"),
                sec.getDouble("y"),
                sec.getDouble("z"),
                (float) sec.getDouble("yaw"),
                (float) sec.getDouble("pitch"));
    }

    public static Document toDoc(Location l) {
        if (l == null) return null;
        return new Document("world", l.getWorld().getName())
                .append("x", l.getX())
                .append("y", l.getY())
                .append("z", l.getZ())
                .append("yaw", l.getYaw())
                .append("pitch", l.getPitch());
    }
    public static Location fromDoc(Document d) {
        if (d == null) return null;
        String world = d.getString("world");
        if (world == null || Bukkit.getWorld(world) == null) return null;
        return new Location(
                Bukkit.getWorld(world),
                d.getDouble("x"),
                d.getDouble("y"),
                d.getDouble("z"),
                d.get("yaw") instanceof Number n1 ? n1.floatValue() : ((Double)d.get("yaw")).floatValue(),
                d.get("pitch") instanceof Number n2 ? n2.floatValue() : ((Double)d.get("pitch")).floatValue()
        );
    }
}
