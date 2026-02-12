// File: src/main/java/fr/elias/oreoEssentials/ic/ICPos.java
package fr.elias.oreoEssentials.modules.ic;

import java.util.Objects;

public final class ICPos {
    public final String world;
    public final int x, y, z;

    public ICPos(String world, int x, int y, int z) {
        this.world = world; this.x = x; this.y = y; this.z = z;
    }

    public static ICPos of(org.bukkit.block.Block b) {
        var l = b.getLocation();
        return new ICPos(l.getWorld().getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ());
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ICPos)) return false;
        ICPos p = (ICPos) o;
        return x == p.x && y == p.y && z == p.z && Objects.equals(world, p.world);
    }
    @Override public int hashCode() { return Objects.hash(world, x, y, z); }
}
