package fr.elias.oreoEssentials.modules.jail;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.*;

public final class JailModels {
    public static final class Jail {
        public String name;
        public String world;
        public Cuboid region;
        public Map<String, Location> cells = new HashMap<>(); // cellId -> spawn location

        public boolean isValid() {
            return name != null && !name.isBlank()
                    && world != null && !world.isBlank()
                    && region != null && region.valid();
        }
    }

    public static final class Cuboid {
        public double x1, y1, z1;
        public double x2, y2, z2;

        public static Cuboid of(Location a, Location b) {
            Cuboid c = new Cuboid();
            c.x1 = Math.min(a.getX(), b.getX());
            c.y1 = Math.min(a.getY(), b.getY());
            c.z1 = Math.min(a.getZ(), b.getZ());
            c.x2 = Math.max(a.getX(), b.getX());
            c.y2 = Math.max(a.getY(), b.getY());
            c.z2 = Math.max(a.getZ(), b.getZ());
            return c;
        }

        public boolean contains(Location l) {
            return l.getX() >= x1 && l.getX() <= x2
                    && l.getY() >= y1 && l.getY() <= y2
                    && l.getZ() >= z1 && l.getZ() <= z2;
        }

        public boolean valid() { return x2 >= x1 && y2 >= y1 && z2 >= z1; }
    }

    /** Active sentence for a player. */
    public static final class Sentence {
        public UUID player;
        public String jailName;
        public String cellId;
        public long endEpochMs; // <=0 = permanent
        public String reason;
        public String by; // staff name

        public boolean expired() { return endEpochMs > 0 && System.currentTimeMillis() > endEpochMs; }
        public long remainingMs() { return endEpochMs <= 0 ? -1 : Math.max(0, endEpochMs - System.currentTimeMillis()); }
    }

    /* ------- helpers ------- */

    public static Location loc(String world, double x, double y, double z, float yaw, float pitch) {
        World w = Bukkit.getWorld(world);
        return w == null ? null : new Location(w, x, y, z, yaw, pitch);
    }
}
