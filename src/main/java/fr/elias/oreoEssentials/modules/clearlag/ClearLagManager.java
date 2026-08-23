package fr.elias.oreoEssentials.modules.clearlag;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.clearlag.config.ClearLagConfig;
import fr.elias.oreoEssentials.modules.clearlag.logic.EntityMatcher;
import fr.elias.oreoEssentials.util.OreScheduler;
import fr.elias.oreoEssentials.util.OreTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;

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
        File file = new File(plugin.getDataFolder(), "server/clearlag.yml");
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            plugin.saveResource("server/clearlag.yml", false);
        }
        FileConfiguration root = YamlConfiguration.loadConfiguration(file);
        this.cfg = new ClearLagConfig(root);

        cancelSchedulers();
        if (!cfg.masterEnabled) {
            plugin.getLogger().info("[OreoLag] Disabled by config (enable=false).");
            return;
        }

        startTpsSampler();
        restartSchedulers();
    }

    public void shutdown() {
        cancelSchedulers();
    }

    private void cancelSchedulers() {
        if (autoTask != null) { autoTask.cancel(); autoTask = null; }
        if (autoKillMobsTask != null) { autoKillMobsTask.cancel(); autoKillMobsTask = null; }
        if (tpsTask != null) { tpsTask.cancel(); tpsTask = null; }
        if (tpsSampleTask != null) { tpsSampleTask.cancel(); tpsSampleTask = null; }
        tpsSamplerStarted = false;
    }

    private void restartSchedulers() {
        if (cfg.auto.enabled) {
            int[] autoTick = {0};
            autoTask = OreScheduler.runTimer(plugin, () -> {
                autoTick[0] += 20;
                int remaining = (int) (cfg.auto.intervalSec - (autoTick[0] / 20));
                cfg.auto.warnings.forEach(w -> {
                    if (remaining == w.time()) broadcast(w.msg().replace("+remaining", String.valueOf(remaining)));
                });
                if (remaining <= 0) {
                    if (OreScheduler.isFolia()) {
                        performRemovalFolia(cfg.auto, removed -> {
                            if (cfg.auto.broadcastRemoval) {
                                broadcast(cfg.auto.broadcastMsg.replace("+RemoveAmount", String.valueOf(removed)));
                            }
                        });
                    } else {
                        int removed = performRemovalPaper(cfg.auto);
                        if (cfg.auto.broadcastRemoval) {
                            broadcast(cfg.auto.broadcastMsg.replace("+RemoveAmount", String.valueOf(removed)));
                        }
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
                    if (remaining == w.time()) broadcast(w.msg().replace("+remaining", String.valueOf(remaining)));
                });
                if (remaining <= 0) {
                    if (OreScheduler.isFolia()) {
                        killMobsFolia(cfg.autoKillMobs, removed -> {
                            if (cfg.autoKillMobs.broadcastRemoval()) {
                                broadcast(cfg.autoKillMobs.broadcastMsg().replace("+RemoveAmount", String.valueOf(removed)));
                            }
                        });
                    } else {
                        int removed = killMobsPaper(cfg.autoKillMobs);
                        if (cfg.autoKillMobs.broadcastRemoval()) {
                            broadcast(cfg.autoKillMobs.broadcastMsg().replace("+RemoveAmount", String.valueOf(removed)));
                        }
                    }
                    killTick[0] = 0;
                }
            }, 20L, 20L);
        }

        if (cfg.tps.enabled) {
            boolean[] triggered = {false};
            tpsTask = OreScheduler.runTimer(plugin, () -> {
                double tps = getServerTPS();
                if (!triggered[0] && tps <= cfg.tps.trigger) {
                    triggered[0] = true;
                    if (cfg.tps.broadcastEnabled) broadcast(cfg.tps.triggerMsg);
                    runCommands(cfg.tps.commands);
                } else if (triggered[0] && tps >= cfg.tps.recover) {
                    triggered[0] = false;
                    if (cfg.tps.broadcastEnabled) broadcast(cfg.tps.recoverMsg);
                    runCommands(cfg.tps.recoverCommands);
                }
            }, 20L * cfg.tps.intervalSec, 20L * cfg.tps.intervalSec);
        }
    }

    private void startTpsSampler() {
        if (tpsSamplerStarted) return;
        tpsSamplerStarted = true;
        lastTickNanos = System.nanoTime();
        tpsSampleTask = OreScheduler.runTimer(plugin, () -> {
            long now = System.nanoTime();
            long dt = now - lastTickNanos;
            lastTickNanos = now;
            if (dt <= 0) return;
            double instTps = Math.min(25.0, 1_000_000_000.0 / dt);
            rollingTps = (rollingTps * 0.9) + (Math.min(20.0, instTps) * 0.1);
        }, 1L, 1L);
    }

    private void runCommands(List<String> commands) {
        CommandSender console = Bukkit.getConsoleSender();
        for (String c : commands) Bukkit.dispatchCommand(console, c);
    }

    public int commandClear(CommandSender sender) {
        if (!cfg.masterEnabled) {
            send(sender, "§c[OreoLag] Disabled by config.");
            return 0;
        }
        if (OreScheduler.isFolia()) {
            send(sender, "§e[OreoLag] Cleanup scheduled across loaded player regions...");
            performRemovalFolia(cfg.cmd, removed -> send(sender,
                    "§a[OreoLag] Removed §e" + removed + " §aentities."));
            return 0;
        }
        int removed = performRemovalPaper(cfg.cmd);
        if (!cfg.cmd.broadcastRemoval) send(sender, "§a[OreoLag] Removed §e" + removed + " §aentities.");
        return removed;
    }

    public int commandKillMobs(CommandSender sender) {
        if (!cfg.masterEnabled) {
            send(sender, "§c[OreoLag] Disabled by config.");
            return 0;
        }
        if (OreScheduler.isFolia()) {
            send(sender, "§e[OreoLag] Mob cleanup scheduled across loaded player regions...");
            killMobsFolia(cfg.killMobs, removed -> send(sender,
                    "§a[OreoLag] Removed §e" + removed + " §amobs."));
            return 0;
        }
        int removed = killMobsPaper(cfg.killMobs);
        send(sender, "§a[OreoLag] Removed §e" + removed + " §amobs.");
        return removed;
    }

    public void reloadAndAck(CommandSender sender) {
        reload();
        send(sender, "§a[OreoLag] Reloaded clearlag.yml and restarted tasks.");
    }

    private int killMobsPaper(ClearLagConfig.KillMobs config) {
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            if (world == null || config.worldFilter().contains(world.getName())) continue;
            for (LivingEntity le : world.getEntitiesByClass(LivingEntity.class)) {
                if (le instanceof Player) continue;
                if (!config.removeNamed() && hasCustomName(le)) continue;
                if (EntityMatcher.isFilteredMob(le, config.mobFilter())) continue;
                le.remove();
                removed++;
            }
        }
        return removed;
    }

    private int performRemovalPaper(ClearLagConfig.Removal r) {
        int removed = 0;
        for (World w : Bukkit.getWorlds()) {
            if (w == null || r.worldFilter.contains(w.getName())) continue;
            for (Entity e : w.getEntities()) {
                if (e instanceof Player) continue;
                if (shouldRemove(e, r)) {
                    e.remove();
                    removed++;
                }
            }
        }
        return removed;
    }

    private boolean shouldRemove(Entity e, ClearLagConfig.Removal r) {
        if (!allowedByFlags(e, r)) return false;
        if (EntityMatcher.inAreaFilter(e, cfg.areaFilter)) return false;
        if (EntityMatcher.matchesTokens(e, r.removeEntities)) return true;
        if (e instanceof Item it) {
            return r.flagItem && !r.itemWhitelist.contains(it.getItemStack().getType());
        }
        return isDirectlyRemovableByFlags(e, r);
    }

    private void killMobsFolia(ClearLagConfig.KillMobs config, Consumer<Integer> callback) {
        scanLoadedPlayerRegions(entity -> {
            if (!(entity instanceof LivingEntity le) || le instanceof Player) return false;
            if (config.worldFilter().contains(le.getWorld().getName())) return false;
            if (!config.removeNamed() && hasCustomName(le)) return false;
            return !EntityMatcher.isFilteredMob(le, config.mobFilter());
        }, callback);
    }

    private void performRemovalFolia(ClearLagConfig.Removal r, Consumer<Integer> callback) {
        scanLoadedPlayerRegions(entity -> {
            if (entity instanceof Player) return false;
            if (r.worldFilter.contains(entity.getWorld().getName())) return false;
            return shouldRemove(entity, r);
        }, callback);
    }

    /**
     * Folia-safe cleanup: discover loaded chunks from each player's region and then inspect
     * each chunk on that chunk's owning region scheduler. This avoids World#getEntities()
     * and World#getEntitiesByClass() from the global region thread.
     */
    private void scanLoadedPlayerRegions(Predicate<Entity> removePredicate, Consumer<Integer> callback) {
        Set<String> scheduledChunks = ConcurrentHashMap.newKeySet();
        AtomicInteger pending = new AtomicInteger(1);
        AtomicInteger removed = new AtomicInteger();
        int radius = Math.max(2, Bukkit.getViewDistance() + 1);

        Runnable completeOne = () -> {
            if (pending.decrementAndGet() == 0) {
                OreScheduler.run(plugin, () -> callback.accept(removed.get()));
            }
        };

        for (Player player : Bukkit.getOnlinePlayers()) {
            pending.incrementAndGet();
            OreScheduler.runForEntity(plugin, player, () -> {
                try {
                    if (!player.isOnline()) return;
                    Location loc = player.getLocation();
                    World world = loc.getWorld();
                    if (world == null) return;
                    int centerX = loc.getBlockX() >> 4;
                    int centerZ = loc.getBlockZ() >> 4;

                    for (int dx = -radius; dx <= radius; dx++) {
                        for (int dz = -radius; dz <= radius; dz++) {
                            int cx = centerX + dx;
                            int cz = centerZ + dz;
                            String key = world.getUID() + ":" + cx + ":" + cz;
                            if (!scheduledChunks.add(key)) continue;

                            pending.incrementAndGet();
                            Location anchor = new Location(world, (cx << 4) + 8, world.getMinHeight(), (cz << 4) + 8);
                            OreScheduler.runAtLocation(plugin, anchor, () -> {
                                try {
                                    if (!world.isChunkLoaded(cx, cz)) return;
                                    for (Entity entity : world.getChunkAt(cx, cz).getEntities()) {
                                        try {
                                            if (removePredicate.test(entity)) {
                                                entity.remove();
                                                removed.incrementAndGet();
                                            }
                                        } catch (Throwable ignored) {}
                                    }
                                } finally {
                                    completeOne.run();
                                }
                            });
                        }
                    }
                } finally {
                    completeOne.run();
                }
            });
        }
        completeOne.run();
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

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private void broadcast(String message) {
        if (!cfg.masterEnabled || !cfg.broadcast.enabled()) return;
        Component component = parseMessage(message);
        for (Player p : Bukkit.getOnlinePlayers()) {
            OreScheduler.runForEntity(plugin, p, () -> {
                if (!p.isOnline()) return;
                if (!cfg.broadcast.usePerm() || p.hasPermission(cfg.broadcast.perm())) {
                    p.sendMessage(component);
                }
            });
        }
    }

    private void send(CommandSender sender, String message) {
        if (sender instanceof Player p) {
            OreScheduler.runForEntity(plugin, p, () -> {
                if (p.isOnline()) p.sendMessage(message);
            });
        } else {
            sender.sendMessage(message);
        }
    }

    private static Component parseMessage(String message) {
        if (message == null || message.isEmpty()) return Component.empty();
        message = message.replaceAll("&#([A-Fa-f0-9]{6})", "<#$1>").replace('§', '&');
        if (message.contains("<") && message.contains(">")) {
            try { return MM.deserialize(message); } catch (Throwable ignored) {}
        }
        return LEGACY.deserialize(ChatColor.translateAlternateColorCodes('&', message));
    }

    private double getServerTPS() {
        try {
            java.lang.reflect.Method m = Bukkit.getServer().getClass().getMethod("getTPS");
            Object res = m.invoke(Bukkit.getServer());
            if (res instanceof double[] arr && arr.length > 0) return Math.min(20.0, arr[0]);
        } catch (Throwable ignored) {}
        return rollingTps > 0 ? rollingTps : 20.0;
    }

    public ClearLagConfig getConfigModel() { return cfg; }
}
