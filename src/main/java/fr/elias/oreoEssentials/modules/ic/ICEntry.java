package fr.elias.oreoEssentials.modules.ic;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;

import java.util.*;

public final class ICEntry {
    public final String name;
    public boolean isPublic = false;

    public final Set<ICPos> blocks = new HashSet<>();
    public final Set<UUID> entities = new HashSet<>();
    public final List<String> commands = new ArrayList<>();

    public ICEntry(String name) { this.name = name; }

    public List<String> signArgsAt(ICPos pos) {
        World w = Bukkit.getWorld(pos.world);
        if (w == null) return Collections.emptyList();
        var state = w.getBlockAt(pos.x, pos.y, pos.z).getState();
        if (!(state instanceof Sign s)) return Collections.emptyList();
        String[] lines;
        try {
            lines = s.getSide(Side.FRONT).getLines();
        } catch (Throwable t) {
            lines = s.getLines();
        }
        List<String> out = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line != null && !line.isEmpty()) out.add(line);
        }
        return out;
    }
}