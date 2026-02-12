package fr.elias.oreoEssentials.modules.back;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.HashMap;
import java.util.Map;

public class BackLocation {

    private final String server;
    private final String world;
    private final double x, y, z;
    private final float yaw, pitch;

    public BackLocation(String server, String world,
                        double x, double y, double z,
                        float yaw, float pitch) {
        this.server = server;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public String getServer() { return server; }
    public String getWorldName() { return world; }

    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }

    public Location toLocalLocation() {
        World w = Bukkit.getWorld(world);
        if (w == null) return null;
        return new Location(w, x, y, z, yaw, pitch);
    }

    public static BackLocation from(String serverName, Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        return new BackLocation(
                serverName,
                loc.getWorld().getName(),
                loc.getX(), loc.getY(), loc.getZ(),
                loc.getYaw(), loc.getPitch()
        );
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("server", server);
        m.put("world", world);
        m.put("x", x);
        m.put("y", y);
        m.put("z", z);
        m.put("yaw", yaw);
        m.put("pitch", pitch);
        return m;
    }

    public static BackLocation fromMap(Map<String, Object> m) {
        if (m == null) return null;

        String server = (String) m.get("server");
        String world = (String) m.get("world");
        if (world == null) return null;

        Object ox = m.get("x");
        Object oy = m.get("y");
        Object oz = m.get("z");
        Object oyaw = m.get("yaw");
        Object opitch = m.get("pitch");

        if (!(ox instanceof Number) || !(oy instanceof Number) || !(oz instanceof Number)
                || !(oyaw instanceof Number) || !(opitch instanceof Number)) {
            return null;
        }

        double x = ((Number) ox).doubleValue();
        double y = ((Number) oy).doubleValue();
        double z = ((Number) oz).doubleValue();
        float yaw = ((Number) oyaw).floatValue();
        float pitch = ((Number) opitch).floatValue();

        return new BackLocation(server, world, x, y, z, yaw, pitch);
    }
}
