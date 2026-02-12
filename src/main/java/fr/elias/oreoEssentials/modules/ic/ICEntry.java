// File: src/main/java/fr/elias/oreoEssentials/ic/ICEntry.java
package fr.elias.oreoEssentials.modules.ic;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Sign;

import java.util.*;

public final class ICEntry {
    public final String name;
    public boolean isPublic = false;

    /** bound blocks (world,x,y,z) */
    public final Set<ICPos> blocks = new HashSet<>();
    /** bound entity UUIDs */
    public final Set<UUID> entities = new HashSet<>();
    /** command templates (without leading /). supports asConsole!, asPlayer!, delay! <sec> */
    public final List<String> commands = new ArrayList<>();

    public ICEntry(String name) { this.name = name; }

    public List<String> signArgsAt(ICPos pos) {
        World w = Bukkit.getWorld(pos.world);
        if (w == null) return Collections.emptyList();
        var state = w.getBlockAt(pos.x, pos.y, pos.z).getState();
        if (!(state instanceof Sign)) return Collections.emptyList();
        Sign s = (Sign) state;
        // ignore line 1 (index 0) -> [ic:name]; rest are $1..$n
        List<String> out = new ArrayList<>();
        for (int i = 1; i < s.getLines().length; i++) {
            String line = s.getLine(i);
            if (line != null && !line.isEmpty()) out.add(line);
        }
        return out;
    }
}
