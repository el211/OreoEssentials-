package fr.elias.oreoEssentials.modules.tp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Logger;

public final class RetryTeleporter {
    private final Plugin plugin;
    private final Logger log;
    public RetryTeleporter(Plugin plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
    }


    public void applyWithRetries(UUID playerId, Supplier<Location> targetSupplier, String tag) {
        runOnce(playerId, targetSupplier, tag, 0);
        Bukkit.getScheduler().runTaskLater(plugin, () ->
                runOnce(playerId, targetSupplier, tag, 2), 2L);
        Bukkit.getScheduler().runTaskLater(plugin, () ->
                runOnce(playerId, targetSupplier, tag, 10), 10L);
    }

    private void runOnce(UUID id, Supplier<Location> targetSupplier, String tag, int tick) {
        Player p = Bukkit.getPlayer(id);
        if (p == null || !p.isOnline()) return;

        Location loc = targetSupplier.get();
        if (loc == null) {
            log.warning("[" + tag + "/Retry] tick=" + tick + " target=null (skipping)");
            return;
        }

        boolean teleported = p.teleport(loc);
        log.info("[" + tag + "/Retry] tick=" + tick + " teleported=" + teleported
                + " player=" + p.getName() + " to " + shortLoc(loc));
    }

    private static String shortLoc(Location l) {
        return "loc=" + l.getWorld().getName()
                + "(" + fmt(l.getX()) + ", " + fmt(l.getY()) + ", " + fmt(l.getZ()) + ")"
                + " yaw=" + fmt(l.getYaw()) + " pitch=" + fmt(l.getPitch());
    }
    private static String fmt(double d){ return String.format(Locale.ROOT,"%.2f", d); }
    private static String fmt(float f){ return String.format(Locale.ROOT,"%.2f", f); }
}
