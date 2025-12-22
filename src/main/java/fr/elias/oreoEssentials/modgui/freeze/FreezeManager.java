// File: src/main/java/fr/elias/oreoEssentials/modgui/freeze/FreezeManager.java
package fr.elias.oreoEssentials.modgui.freeze;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FreezeManager {

    public static class FreezeData {
        public final UUID target;
        public final UUID staff;
        public final long until; // millis

        public FreezeData(UUID target, UUID staff, long until) {
            this.target = target;
            this.staff  = staff;
            this.until  = until;
        }

        public long remainingMillis() {
            return until - System.currentTimeMillis();
        }
    }

    private final OreoEssentials plugin;
    private final Map<UUID, FreezeData> frozen = new ConcurrentHashMap<>();

    public FreezeManager(OreoEssentials plugin) {
        this.plugin = plugin;
        startTickTask();
    }

    public boolean isFrozen(UUID id) {
        FreezeData data = frozen.get(id);
        if (data == null) return false;
        if (data.remainingMillis() <= 0) {
            frozen.remove(id);
            return false;
        }
        return true;
    }

    public FreezeData get(UUID id) { return frozen.get(id); }

    public void freeze(Player target, Player staff, long seconds) {
        long until = System.currentTimeMillis() + (seconds * 1000L);
        frozen.put(
                target.getUniqueId(),
                new FreezeData(
                        target.getUniqueId(),
                        staff == null ? null : staff.getUniqueId(),
                        until
                )
        );

        // why: correct Lang.send signature and MiniMessage default
        Lang.send(
                target,
                "freeze.frozen",
                "<red>You are frozen for <yellow>%seconds%</yellow>s.</red>",
                Map.of("seconds", Long.toString(seconds))
        );
    }

    public void unfreeze(Player target) {
        frozen.remove(target.getUniqueId());

        // why: provide default; vars not needed
        Lang.send(
                target,
                "freeze.unfrozen",
                "<green>You are no longer frozen.</green>"
        );
    }

    private void startTickTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();

            for (Iterator<Map.Entry<UUID, FreezeData>> it = frozen.entrySet().iterator(); it.hasNext();) {
                Map.Entry<UUID, FreezeData> e = it.next();
                FreezeData data = e.getValue();

                if (data.until <= now) {
                    Player t = Bukkit.getPlayer(e.getKey());
                    if (t != null) {
                        Lang.send(
                                t,
                                "freeze.expired",
                                "<yellow>Your freeze has expired.</yellow>"
                        );
                    }
                    it.remove();
                    continue;
                }

                Player t = Bukkit.getPlayer(e.getKey());
                if (t == null) continue;

                // particle (visual feedback)
                t.getWorld().spawnParticle(
                        org.bukkit.Particle.SNOWFLAKE,
                        t.getLocation().add(0, 0.1, 0),
                        6, 0.3, 0.1, 0.3, 0.01
                );

                // staff actionbar with remaining seconds (Adventure)
                long remSeconds = data.remainingMillis() / 1000L;
                if (data.staff != null) {
                    Player s = Bukkit.getPlayer(data.staff);
                    if (s != null && s.isOnline()) {
                        s.sendActionBar(
                                Lang.msgComp(
                                        "freeze.actionbar-staff",
                                        Map.of("target", t.getName(), "seconds", Long.toString(remSeconds)),
                                        s
                                )
                        );
                    }
                }
            }
        }, 10L, 10L);
    }
}
