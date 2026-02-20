package fr.elias.oreoEssentials.modules.mobs;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.ultimateChristmas.UltimateChristmas; // <-- optional soft hook (may be null)
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public final class HealthBarListener implements Listener {

    private static final String HOLO_TAG = "oe_mobhb";
    private static final double DEFAULT_Y_OFFSET = 0.5;

    private final OreoEssentials plugin;
    private final UltimateChristmas xmas; // may be null if UltimateChristmas isn't installed
    private final boolean enabled;

    // config
    private final List<String> formatLines;   // size 1 or 2
    private final boolean showNumbers;
    private final int segments;
    private final String fullCh, emptyCh;
    private final boolean rounded;
    private final String leftEdge, rightEdge;
    private final String colFull, colMid, colLow;
    private final double thMid, thLow;
    private final boolean includePassive, includePlayers, onlyWhenDamaged, mythicEnabled;
    private final double yOffset, lineOffset;
    private final int updateInterval;
    private final double viewDistance;
    private final double viewDistanceSq;
    private final boolean requireLOS;
    private final int spawnPerTickCap;

    private final Map<UUID, ArmorStand> topLine = new HashMap<>();
    private final Map<UUID, ArmorStand> bottomLine = new HashMap<>();
    private BukkitTask sweeper;


    public HealthBarListener(OreoEssentials plugin, UltimateChristmas xmasPlugin) {
        this.plugin = plugin;
        this.xmas = xmasPlugin;

        var root = plugin.getConfig().getConfigurationSection("mobs");
        this.enabled = root != null && root.getBoolean("show-healthmobs", false);

        var hb = (root != null) ? root.getConfigurationSection("healthbar") : null;

        List<String> linesTmp = new ArrayList<>();
        if (hb != null) {
            if (hb.isList("format")) {
                for (Object o : Objects.requireNonNull(hb.getList("format"))) {
                    if (o != null) linesTmp.add(String.valueOf(o));
                }
            } else {
                linesTmp.add(hb.getString("format", "&c❤ <bar> &7(<current>/<max>) &f<name>"));
            }
        }
        if (linesTmp.isEmpty()) linesTmp.add("&c❤ <bar> &7(<current>/<max>) &f<name>");
        if (linesTmp.size() > 2) linesTmp = linesTmp.subList(0, 2);
        this.formatLines = Collections.unmodifiableList(linesTmp);

        this.showNumbers     = hb == null || hb.getBoolean("show-numbers", true);
        this.segments        = (hb != null) ? Math.max(1, hb.getInt("segments", 10)) : 10;
        this.fullCh          = (hb != null) ? hb.getString("full", "█") : "█";
        this.emptyCh         = (hb != null) ? hb.getString("empty", "░") : "░";
        this.rounded         = hb == null || hb.getBoolean("rounded", true);
        this.leftEdge        = (hb != null) ? hb.getString("left-edge", "❮") : "❮";
        this.rightEdge       = (hb != null) ? hb.getString("right-edge", "❯") : "❯";
        this.colFull         = (hb != null) ? hb.getString("color-full", "&a") : "&a";
        this.colMid          = (hb != null) ? hb.getString("color-mid",  "&e") : "&e";
        this.colLow          = (hb != null) ? hb.getString("color-low",  "&c") : "&c";
        this.thMid           = (hb != null) ? clamp01(hb.getDouble("mid-threshold", 0.5)) : 0.5;
        this.thLow           = (hb != null) ? clamp01(hb.getDouble("low-threshold", 0.2)) : 0.2;
        this.includePassive  = hb == null || hb.getBoolean("include-passive", true);
        this.includePlayers  = hb != null && hb.getBoolean("include-players", false);
        this.onlyWhenDamaged = hb != null && hb.getBoolean("only-when-damaged", false);
        this.mythicEnabled   = hb == null || hb.getBoolean("use-mythicmobs", true);
        this.yOffset         = (hb != null) ? hb.getDouble("y-offset", DEFAULT_Y_OFFSET) : DEFAULT_Y_OFFSET;
        this.lineOffset      = (hb != null) ? hb.getDouble("line-offset", 0.35) : 0.35;
        this.updateInterval  = (hb != null) ? Math.max(1, hb.getInt("update-interval-ticks", 5)) : 5;

        this.viewDistance    = (hb != null) ? Math.max(0.0, hb.getDouble("view-distance", 32.0)) : 32.0;
        this.viewDistanceSq  = this.viewDistance * this.viewDistance;
        this.requireLOS      = hb == null || hb.getBoolean("require-line-of-sight", true);
        this.spawnPerTickCap = (hb != null) ? Math.max(1, hb.getInt("spawn-per-tick-cap", 40)) : 40;

        if (enabled) {
            sweeper = Bukkit.getScheduler().runTaskTimer(plugin, this::sweepTick, updateInterval, updateInterval);
        }
    }

    public boolean isEnabled() { return enabled; }

    public void shutdown() {
        if (sweeper != null) { sweeper.cancel(); sweeper = null; }
        removeAll(topLine);
        removeAll(bottomLine);
        topLine.clear();
        bottomLine.clear();
    }


    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onSpawn(CreatureSpawnEvent e) {
        if (!enabled) return;

        if (e.getEntity().getType() == EntityType.ARMOR_STAND) return;
        if (e.getEntity().getScoreboardTags().contains(HOLO_TAG)) return;

        if (!(e.getEntity() instanceof LivingEntity le)) return;
        if (!shouldTrack(le)) return;
        if (onlyWhenDamaged) return;

        update(le);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageEvent e) {
        if (!enabled) return;
        if (!(e.getEntity() instanceof LivingEntity le)) return;
        if (!shouldTrack(le)) return;

        Bukkit.getScheduler().runTask(plugin, () -> update(le));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onRegain(EntityRegainHealthEvent e) {
        if (!enabled) return;
        if (!(e.getEntity() instanceof LivingEntity le)) return;
        if (!shouldTrack(le)) return;

        Bukkit.getScheduler().runTask(plugin, () -> update(le));
    }

    @EventHandler(ignoreCancelled = true)
    public void onDeath(EntityDeathEvent e) {
        if (!enabled) return;
        UUID id = e.getEntity().getUniqueId();
        removeStand(topLine.remove(id));
        removeStand(bottomLine.remove(id));
    }


    private void sweepTick() {
        int created = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.isValid() || p.isDead()) continue;
            var w = p.getWorld();

            for (Entity e : w.getNearbyEntities(p.getLocation(), viewDistance, viewDistance, viewDistance)) {
                if (!(e instanceof LivingEntity le)) continue;
                if (le instanceof Player && !includePlayers) continue;
                if (!shouldTrack(le)) continue;
                if (created >= spawnPerTickCap) break;

                if (p.getLocation().distanceSquared(le.getLocation()) > viewDistanceSq) continue;
                if (requireLOS && !p.hasLineOfSight(le)) continue;

                if (!topLine.containsKey(le.getUniqueId()) && !onlyWhenDamaged) {
                    update(le);
                    created++;
                }
            }
            if (created >= spawnPerTickCap) break;
        }

        proximitySweep(topLine, true);
        proximitySweep(bottomLine, false);
    }


    private boolean shouldTrack(LivingEntity le) {

        if (le.getType() == EntityType.ARMOR_STAND) return false;
        if (le.getScoreboardTags().contains(HOLO_TAG)) return false;

        try {
            if (SantaHook.isSanta(le) || GrinchHook.isGrinch(le)) {
                return false;
            }
        } catch (Throwable ignored) {}

        if (xmas != null) {
            try {
                if (xmas.isSantaEntity(le)) return false;
            } catch (Throwable ignored) {}
        }

        if (le instanceof Player) {
            return includePlayers;
        }

        if (!includePassive && isPassive(le.getType())) {
            return false;
        }

        return true;
    }

    private boolean hasViewer(LivingEntity le) {
        var w = le.getWorld();
        for (Player p : w.getPlayers()) {
            if (!p.isValid() || p.isDead()) continue;
            if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
            if (p.getLocation().distanceSquared(le.getLocation()) <= viewDistanceSq) {
                if (!requireLOS || p.hasLineOfSight(le)) return true;
            }
        }
        return false;
    }

    private void update(LivingEntity le) {

        if (!hasViewer(le)
                || SantaHook.isSanta(le)
                || GrinchHook.isGrinch(le)
                || (xmas != null && safeIsSantaViaApi(le))) {
            UUID id = le.getUniqueId();
            removeStand(topLine.remove(id));
            removeStand(bottomLine.remove(id));
            return;
        }

        double cur = Math.max(0.0, le.getHealth());
        double max = getMaxHealth(le);
        if (max <= 0) max = 20.0;

        String mobName = mythicEnabled ? MythicMobsHook.tryName(le) : null;
        if (mobName == null || mobName.isEmpty()) {
            String base = le.getType().name()
                    .toLowerCase(java.util.Locale.ROOT)
                    .replace('_', ' ');
            mobName = Character.toUpperCase(base.charAt(0)) + base.substring(1);
        }

        String bar = buildBar(cur, max);
        String curStr = showNumbers ? formatHp(cur) : "";
        String maxStr = showNumbers ? formatHp(max) : "";

        String line1 = render(formatLines.get(0), mobName, bar, curStr, maxStr);
        String line2 = (formatLines.size() > 1) ? render(formatLines.get(1), mobName, bar, curStr, maxStr) : null;

        ArmorStand top = getOrCreateStand(le, topLine, yOffset);
        top.setCustomName(color(line1));

        if (line2 != null) {
            ArmorStand bottom = getOrCreateStand(le, bottomLine, yOffset - lineOffset);
            bottom.setCustomName(color(line2));
        } else {
            removeStand(bottomLine.remove(le.getUniqueId()));
        }
    }

    private String render(String fmt, String name, String bar, String cur, String max) {
        String out = fmt.replace("<name>", name)
                .replace("<bar>", bar)
                .replace("<current>", cur)
                .replace("<max>", max);
        if (!showNumbers) out = out.replace("()", "").replace("  ", " ").trim();
        return out;
    }

    private ArmorStand getOrCreateStand(LivingEntity host, Map<UUID, ArmorStand> map, double relY) {
        UUID id = host.getUniqueId();
        ArmorStand as = map.get(id);
        if (as != null && !as.isDead() && as.isValid()) {
            teleport(as, host, relY);
            return as;
        }
        as = spawnStand(host, relY);
        map.put(id, as);
        return as;
    }

    private ArmorStand spawnStand(LivingEntity host, double relY) {
        Location loc = host.getEyeLocation().add(0, relY, 0);
        return host.getWorld().spawn(loc, ArmorStand.class, s -> {
            s.addScoreboardTag(HOLO_TAG);
            s.setMarker(true);
            s.setInvisible(true);
            s.setSmall(true);
            s.setGravity(false);
            s.setCollidable(false);
            s.setSilent(true);
            s.setCustomNameVisible(true);
            s.setRemoveWhenFarAway(false);
            s.setPersistent(false);
        });
    }

    private void teleport(ArmorStand as, LivingEntity host, double relY) {
        as.teleport(host.getEyeLocation().add(0, relY, 0));
    }

    private void proximitySweep(Map<UUID, ArmorStand> map, boolean top) {
        for (Iterator<Map.Entry<UUID, ArmorStand>> it = map.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, ArmorStand> e = it.next();
            UUID id = e.getKey();
            ArmorStand as = e.getValue();

            Entity host = Bukkit.getEntity(id);
            if (!(host instanceof LivingEntity le)
                    || host.isDead()
                    || !host.isValid()
                    || as == null
                    || as.isDead()
                    || !as.isValid()
                    // Hide bars for Santa/Grinch (tool-safe hooks)
                    || SantaHook.isSanta(le)
                    || GrinchHook.isGrinch(le)
                    // Back-compat guard: xmas API if present
                    || (xmas != null && safeIsSantaViaApi(le))) {
                removeStand(as);
                it.remove();
                continue;
            }

            teleport(as, (LivingEntity) host, top ? yOffset : (yOffset - lineOffset));
        }
    }

    private void removeAll(Map<UUID, ArmorStand> map) {
        for (ArmorStand as : map.values()) removeStand(as);
    }

    private void removeStand(ArmorStand as) {
        if (as != null && !as.isDead()) as.remove();
    }

    private String buildBar(double cur, double max) {
        double ratio = (max <= 0 ? 0 : cur / max);
        ratio = Math.max(0, Math.min(1, ratio));
        int fullCount = (int) Math.round(ratio * segments);

        StringBuilder sb = new StringBuilder(segments + 8);
        if (rounded) sb.append(leftEdge);
        String col = colFor(ratio);
        sb.append(color(col));
        for (int i = 0; i < segments; i++) sb.append(i < fullCount ? fullCh : emptyCh);
        if (rounded) sb.append(ChatColor.RESET).append(rightEdge);
        return sb.toString();
    }

    private String colFor(double r) {
        if (r <= thLow) return colLow;
        if (r <= thMid) return colMid;
        return colFull;
    }

    private static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private static String formatHp(double v) {
        long l = (long) v;
        return (Math.abs(v - l) < 0.0001) ? Long.toString(l) : String.format(java.util.Locale.US, "%.1f", v);
    }

    private static double getMaxHealth(LivingEntity le) {
        Attribute type = resolveMaxHealthAttribute();
        if (type != null && le.getAttribute(type) != null) {
            return le.getAttribute(type).getValue();
        }
        return Math.max(20.0, le.getHealth());
    }

    private static Attribute resolveMaxHealthAttribute() {
        try { return Attribute.valueOf("GENERIC_MAX_HEALTH"); }
        catch (IllegalArgumentException ignored) {
            try { return Attribute.valueOf("MAX_HEALTH"); }
            catch (IllegalArgumentException ignored2) { return null; }
        }
    }

    private static double clamp01(double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) return 0;
        return Math.max(0, Math.min(1, d));
    }

    private static boolean isPassive(EntityType t) {
        switch (t) {
            case SHEEP: case COW: case PIG: case CHICKEN: case RABBIT:
            case HORSE: case DONKEY: case MULE: case VILLAGER:
            case SQUID: case GLOW_SQUID: case FOX: case CAT:
            case TURTLE: case STRIDER: case SNIFFER: case CAMEL:
            case BEE: case PARROT:
                return true;
            default: return false;
        }
    }

    /* ---------- Back-compat helper (won't crash if method missing) ---------- */
    private boolean safeIsSantaViaApi(LivingEntity le) {
        try {
            return xmas != null && xmas.isSantaEntity(le);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
