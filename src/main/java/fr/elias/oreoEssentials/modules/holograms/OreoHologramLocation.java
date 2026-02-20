    package fr.elias.oreoEssentials.modules.holograms;

    import org.bukkit.Bukkit;
    import org.bukkit.Location;
    import org.bukkit.World;

    public final class OreoHologramLocation {
        public String world;
        public double x, y, z;
        public float yaw, pitch;

        public static OreoHologramLocation of(Location l) {
            OreoHologramLocation o = new OreoHologramLocation();
            o.world = l.getWorld().getName();
            o.x = l.getX(); o.y = l.getY(); o.z = l.getZ();
            o.yaw = l.getYaw(); o.pitch = l.getPitch();
            return o;
        }

        public Location toLocation() {
            World w = Bukkit.getWorld(world);
            if (w == null) return null;
            return new Location(w, x, y, z, yaw, pitch);
        }
    }
