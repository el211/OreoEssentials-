package fr.elias.oreoEssentials.modgui.freeze;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.util.Lang;
import fr.elias.oreoEssentials.util.OreScheduler;
import fr.elias.oreoEssentials.util.OreTask;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FreezeManager {

    public static class FreezeData {
        public final UUID target;
        public final UUID staff;
        public final long until;

        public FreezeData(UUID target, UUID staff, long until) {
            this.target = target;
            this.staff = staff;
            this.until = until;
        }

        public long remainingMillis() { return until - System.currentTimeMillis(); }
    }

    private final OreoEssentials plugin;
    private final Map<UUID, FreezeData> frozen = new ConcurrentHashMap<>();
    private OreTask tickTask;

    public FreezeManager(OreoEssentials plugin) {
        this.plugin = plugin;
        startTickTask();
    }

    public boolean isFrozen(UUID id) {
        FreezeData data = frozen.get(id);
        if (data == null) return false;
        if (data.remainingMillis() <= 0) {
            frozen.remove(id, data);
            return false;
        }
        return true;
    }

    public FreezeData get(UUID id) { return frozen.get(id); }

    public void freeze(Player target, Player staff, long seconds) {
        long until = System.currentTimeMillis() + (seconds * 1000L);
        frozen.put(target.getUniqueId(), new FreezeData(
                target.getUniqueId(), staff == null ? null : staff.getUniqueId(), until));
        Lang.send(target, "freeze.frozen",
                "<red>You are frozen for <yellow>%seconds%</yellow>s.</red>",
                Map.of("seconds", Long.toString(seconds)));
    }

    public void unfreeze(Player target) {
        frozen.remove(target.getUniqueId());
        Lang.send(target, "freeze.unfrozen", "<green>You are no longer frozen.</green>");
    }

    public void shutdown() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        frozen.clear();
    }

    private void startTickTask() {
        tickTask = OreScheduler.runTimer(plugin, () -> {
            final long now = System.currentTimeMillis();
            for (Map.Entry<UUID, FreezeData> entry : frozen.entrySet()) {
                UUID targetId = entry.getKey();
                FreezeData data = entry.getValue();

                if (data.until <= now) {
                    if (!frozen.remove(targetId, data)) continue;
                    Player target = Bukkit.getPlayer(targetId);
                    if (target != null) {
                        OreScheduler.runForEntity(plugin, target, () -> {
                            if (target.isOnline()) {
                                Lang.send(target, "freeze.expired", "<yellow>Your freeze has expired.</yellow>");
                            }
                        });
                    }
                    continue;
                }

                Player target = Bukkit.getPlayer(targetId);
                if (target == null) continue;

                OreScheduler.runForEntity(plugin, target, () -> {
                    if (!target.isOnline() || frozen.get(targetId) != data) return;

                    target.getWorld().spawnParticle(
                            Particle.SNOWFLAKE,
                            target.getLocation().add(0, 0.1, 0),
                            6, 0.3, 0.1, 0.3, 0.01);

                    if (data.staff == null) return;
                    String targetName = target.getName();
                    long remSeconds = Math.max(0L, data.remainingMillis() / 1000L);
                    Player staff = Bukkit.getPlayer(data.staff);
                    if (staff == null) return;

                    OreScheduler.runForEntity(plugin, staff, () -> {
                        if (!staff.isOnline()) return;
                        staff.sendActionBar(Lang.msgComp(
                                "freeze.actionbar-staff",
                                Map.of("target", targetName, "seconds", Long.toString(remSeconds)),
                                staff));
                    });
                });
            }
        }, 10L, 10L);
    }
}
