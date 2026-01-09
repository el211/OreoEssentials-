package fr.elias.oreoEssentials.config;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.configuration.file.FileConfiguration;

public final class CrossServerSettings {

    private final boolean homes;
    private final boolean warps;
    private final boolean spawn;
    private final boolean economy;
    private final boolean enderchest;

    private CrossServerSettings(boolean homes,
                                boolean warps,
                                boolean spawn,
                                boolean economy,
                                boolean enderchest) {
        this.homes = homes;
        this.warps = warps;
        this.spawn = spawn;
        this.economy = economy;
        this.enderchest = enderchest;
    }

    public static CrossServerSettings load(OreoEssentials plugin) {
        FileConfiguration settings = plugin.getSettingsConfig().getRoot();

        boolean homes      = settings.getBoolean("features.cross-server.homes", true);
        boolean warps      = settings.getBoolean("features.cross-server.warps", true);
        boolean spawn      = settings.getBoolean("features.cross-server.spawn", true);
        boolean economy    = settings.getBoolean("features.cross-server.economy", true);
        boolean enderchest = settings.getBoolean("features.cross-server.enderchest", true);

        plugin.getLogger().info("[CROSS] homes=" + homes
                + " warps=" + warps
                + " spawn=" + spawn
                + " economy=" + economy
                + " enderchest=" + enderchest);

        return new CrossServerSettings(homes, warps, spawn, economy, enderchest);
    }

    public boolean homes()      { return homes; }
    public boolean warps()      { return warps; }
    public boolean spawn()      { return spawn; }
    public boolean economy()    { return economy; }
    public boolean enderchest() { return enderchest; }
}
