package fr.elias.oreoEssentials.modules.clearlag;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.clearlag.config.ClearLagConfig;
import fr.elias.oreoEssentials.modules.clearlag.logic.EntityMatcher;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import fr.elias.oreoEssentials.util.OreScheduler;
import fr.elias.oreoEssentials.util.OreTask;

import java.io.File;
import java.util.List;

public class ClearLagManager {

    private final OreoEssentials plugin;
    private ClearLagConfig cfg;
    private OreTask autoTask;
    private OreTask autoKillMobsTask;
    private OreTask tpsTask;
    private OreTask tpsSampleTask;
    private volatile boolean tpsSamplerStarted = false;
    private volatile long lastTickNanos = System.nanoTime();
    private volatile double rollingTps = 20.0;

    public ClearLagManager(OreoEssentials plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "clearlag.yml");
        if (!file.exists()) plugin.saveResource("clearlag.yml", false);
        FileConfiguration root = YamlConfiguration.loadConfiguration(file);

        this.cfg = new ClearLagConfig(root);

        if (!cfg.masterEnabled) {
            cancelSchedulers();
            plugin.getLogger().info("[OreoLag] Disabled by config (enable=false).");
            return;
        }

        startTpsSampler();
        restartSchedulers();
    }

    private void cancelSchedulers() {
        if (autoTask != null) {
            autoTask.cancel();
            autoTask = null;
        }
        if (autoKillMobsTask != null) {
            autoKillMobsTask.cancel();
            autoKillMobsTask = null;
        }
        if (tpsTask != null) {
            tpsTask.cancel();
            tpsTask = null;
        }
    }

    private void restartSchedulers() {
        if (autoTask != null) autoTask.cancel();
        if (autoKillMobsTask != null) autoKillMobsTask.cancel();
        if (tpsTask != null) tpsTask.cancel();

        if (cfg.auto.enabled) {
            int[] autoTick = {0};
            autoTask = OreScheduler.runTimer(plugin, () -> {
                autoTick[0] += 20;
                int remaining = (int) (cfg.auto.intervalSec - (autoTick[0] / 20));
                cfg.auto.warnings.forEach(w -> {
                    if (remaining == w.time()) {
                        String msg = w.msg().replace("+remaining", String.valueOf(remaining));
                        broadcast(msg);
                    }
                });
                if (remaining <= 0) {
                    int removed = performRemoval(cfg.auto, true, null);
                    if (cfg.auto.broadcastRemoval) {
                        broadcast(cfg.auto.broadcastMsg.replace("+RemoveAmount", String.valueOf(removed)));
                    }
                    autoTick[0] = 0;
                }
            }, 20L, 20L);
        }

        if (cfg.autoKillMobs.enabled()) {
            int[] killTick = {0};
            autoKillMobsTask = OreScheduler.runTimer(plugin, () -> {
                killTick[0] += 20;
                int remaining = (int) (cfg.autoKillMobs.intervalSec() - (killTick[0] / 20));
                cfg.autoKillMobs.warnings().forEach(w -> {
                    if (remaining == w.time()) {
                        String msg = w.msg().replace("+remaining", String.valueOf(remaining));
                        broadcast(msg);
                    }
                });
                if (remaining <= 0) {
                    int removed = performAutoKillMobs();
                    if (cfg.autoKillMobs.broadcastRemoval()) {
                        broadcast(cfg.autoKillMobs.broadcastMsg().replace("+RemoveAmount", String.valueOf(removed)));
                    }
                    killTick[0] = 0;
                }
            }, 20L, 20L);
        }

        if (cfg.tps.enabled) {
            boolean[] tpsTriggered = {false};
            tpsTask = OreScheduler.runTimer(plugin, () -> {
                double tps = getServerTPS();
                if (!tpsTriggered[0] && tps <= cfg.tps.trigger) {
                    tpsTriggered[0] = true;
                    if (cfg.tps.broadcastEnabled) broadcast(cfg.tps.triggerMsg);
                    runCommands(cfg.tps.commands);
                } else if (tpsTriggered[0] && tps >= cfg.tps.recover) {
                    tpsTriggered[0] = false;
                    if (cfg.tps.broadcastEnabled) broadcast(cfg.tps.recoverMsg);
                    runCommands(cfg.tps.recoverCommands);
                }
            }, 20L * cfg.tps.intervalSec, 20L * cfg.tps.intervalSec);
        }
    }

    private void startTpsSampler() {
        if (tpsSamplerStarted) return;
        tpsSamplerStarted = true;

        tpsSampleTask = OreScheduler.runTimer(plugin, () -> {
            long now = System.nanoTime();
            long dt = now - lastTickNanos;
            lastTickNanos = now;

            if (dt <= 0) return;
            double instTps = 1_000_000_000.0 / dt;
            if (instTps > 25.0) instTps = 25.0;
            rollingTps = (rollingTps * 0.9) + (Math.min(20.0, instTps) * 0.1);
        }, 1L, 1L);
    }

    private void runCommands(List<String> commands) {
        CommandSender console = Bukkit.getConsoleSender();
        for (String c : commands) Bukkit.dispatchCommand(console, c);
    }

    public int commandClear(CommandSender sender) {
        if (!cfg.masterEnabled) {
            sender.sendMessage("§c[OreoLag] Disabled by config.");
            return 0;
        }
        int removed = performRemoval(cfg.cmd, false, sender);
        if (!cfg.cmd.broadcastRemoval) {
            sender.sendMessage("§a[OreoLag] Removed §e" + removed + " §aentities.");
        }
        return removed;
    }

    public int commandKillMobs(CommandSender sender) {
        if (!cfg.masterEnabled) {
            sender.sendMessage("§c[OreoLag] Disabled by config.");
            return 0;
        }
        int removed = killMobs(cfg.killMobs);
        sender.sendMessage("§a[OreoLag] Removed §e" + removed + " §amobs.");
        return removed;
    }

    public void reloadAndAck(CommandSender sender) {
        reload();
        sender.sendMessage("§a[OreoLag] Reloaded clearlag.yml and restarted tasks.");
    }

    private int performAutoKillMobs() {
        return killMobs(cfg.autoKillMobs);
    }

    private int killMobs(ClearLagConfig.KillMobs config) {
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            if (world == null) continue;
            if (config.worldFilter().contains(world.getName())) continue;

            for (LivingEntity le : world.getEntitiesByClass(LivingEntity.class)) {
                if (le instanceof Player) continue;

                if (!config.removeNamed() && hasCustomName(le)) continue;
                if (EntityMatcher.isFilteredMob(le, config.mobFilter())) continue;

                removeEntitySafe(le);
                removed++;
            }
        }
        return removed;
    }

    private int performRemoval(ClearLagConfig.Removal r, boolean scheduled, CommandSender issuer) {
        if (!cfg.masterEnabled) return 0;
        int removed = 0;
        for (World w : Bukkit.getWorlds()) {
            if (w == null) continue;
            if (r.worldFilter.contains(w.getName())) continue;

            for (Entity e : w.getEntities()) {
                if (e instanceof Player) continue;

                if (!allowedByFlags(e, r)) continue;
                if (EntityMatcher.inAreaFilter(e, cfg.areaFilter)) continue;
                if (EntityMatcher.matchesTokens(e, r.removeEntities)) {
                    removeEntitySafe(e);
                    removed++;
                    continue;
                }

                if (e instanceof Item it) {
                    if (!r.flagItem) continue;
                    if (OreScheduler.isFolia()) {
                        // On Folia, getItemStack() must run on the entity's region thread.
                        // Dispatch the whitelist check + removal there; count is approximate.
                        it.getScheduler().run(plugin, ctx -> {
                            if (!r.itemWhitelist.contains(it.getItemStack().getType())) it.remove();
                        }, null);
                        removed++;
                    } else {
                        if (r.itemWhitelist.contains(it.getItemStack().getType())) continue;
                        removeEntitySafe(it);
                        removed++;
                    }
                    continue;
                }

                if (isDirectlyRemovableByFlags(e, r)) {
                    removeEntitySafe(e);
                    removed++;
                }
            }
        }
        if (issuer != null && scheduled && r.broadcastRemoval) {
            broadcast(r.broadcastMsg.replace("+RemoveAmount", String.valueOf(removed)));
        }
        return removed;
    }

    /**
     * Removes an entity safely on both Paper and Folia.
     * On Folia, entity.remove() must run on the entity's own region thread;
     * calling it from the global scheduler thread crashes with a thread-check error.
     */
    private void removeEntitySafe(Entity entity) {
        if (OreScheduler.isFolia()) {
            entity.getScheduler().run(plugin, ctx -> entity.remove(), null);
        } else {
            entity.remove();
        }
    }

    private boolean allowedByFlags(Entity e, ClearLagConfig.Removal r) {
        if (e instanceof Item) return r.flagItem;
        if (e instanceof Painting || e instanceof ItemFrame) return r.flagItemFrame;
        if (e instanceof Vehicle v) {
            if (v instanceof Minecart) return r.flagMinecart;
            if (v instanceof Boat) return r.flagBoat;
        }
        if (e instanceof ExperienceOrb) return r.flagExp;
        if (e instanceof Projectile) return r.flagProjectile;
        if (e instanceof TNTPrimed) return r.flagPrimedTnt;
        if (e instanceof FallingBlock) return r.flagFallingBlock;
        return true;
    }

    private boolean isDirectlyRemovableByFlags(Entity e, ClearLagConfig.Removal r) {
        if (e instanceof Item) return r.flagItem;
        if (e instanceof ExperienceOrb) return r.flagExp;
        if (e instanceof Projectile) return r.flagProjectile;
        if (e instanceof Painting || e instanceof ItemFrame) return r.flagItemFrame;
        if (e instanceof Vehicle v) {
            if (v instanceof Minecart) return r.flagMinecart;
            if (v instanceof Boat) return r.flagBoat;
        }
        if (e instanceof TNTPrimed) return r.flagPrimedTnt;
        if (e instanceof FallingBlock) return r.flagFallingBlock;
        return false;
    }

    private static boolean hasCustomName(Entity e) {
        String n = e.getCustomName();
        return n != null && !n.isBlank();
    }

    private void broadcast(String message) {
        if (!cfg.masterEnabled) return;
        if (!cfg.broadcast.enabled()) return;
        if (cfg.broadcast.usePerm()) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.hasPermission(cfg.broadcast.perm())) {
                    p.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
                }
            }
        } else {
            Bukkit.getServer().broadcastMessage(ChatColor.translateAlternateColorCodes('&', message));
        }
    }

    private double getServerTPS() {
        try {
            java.lang.reflect.Method m = Bukkit.getServer().getClass().getMethod("getTPS");
            Object res = m.invoke(Bukkit.getServer());
            if (res instanceof double[] arr && arr.length > 0) {
                return Math.min(20.0, arr[0]);
            }
        } catch (Throwable ignored) {
        }
        return rollingTps > 0 ? rollingTps : 20.0;
    }

    public ClearLagConfig getConfigModel() {
        return cfg;
    }
}