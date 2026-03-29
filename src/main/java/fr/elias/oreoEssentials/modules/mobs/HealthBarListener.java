package fr.elias.oreoEssentials.modules.mobs;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.ultimateChristmas.UltimateChristmas;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import fr.elias.oreoEssentials.util.OreScheduler;
import fr.elias.oreoEssentials.util.OreTask;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.event.world.ChunkUnloadEvent;

import java.lang.reflect.Field;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HealthBarListener implements Listener {

    private static final String HOLO_TAG = "oe_mobhb";
    private static final double DEFAULT_Y_OFFSET = 0.5;
    private static final Attribute MAX_HEALTH_ATTR = resolveMaxHealthAttribute();

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.builder()
                    .character('\u00a7')
                    .hexColors()
                    .useUnusualXRepeatedCharacterHexFormat()
                    .build();
    private static final Pattern AMP_HEX    = Pattern.compile("&#([0-9a-fA-F]{6})");
    private static final Pattern LEGACY_HEX = Pattern.compile("\u00a7x(\u00a7[0-9a-fA-F]){6}");
    private static final Pattern PAPI_TAG   = Pattern.compile("<papi:([^>]+)>");

    private final OreoEssentials plugin;
    private final UltimateChristmas xmas;
    private final boolean enabled;

    private final List<String> formatLines;
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

    private final Map<UUID, ArmorStand> topLine    = new HashMap<>();
    private final Map<UUID, ArmorStand> bottomLine = new HashMap<>();
    private OreTask sweeper;
    private int orphanCleanupCounter = 0;

    public HealthBarListener(OreoEssentials plugin, UltimateChristmas xmasPlugin) {
        this.plugin = plugin;
        this.xmas   = xmasPlugin;

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
        this.fullCh          = (hb != null) ? hb.getString("full", "\u2588") : "\u2588";
        this.emptyCh         = (hb != null) ? hb.getString("empty", "\u2591") : "\u2591";
        this.rounded         = hb == null || hb.getBoolean("rounded", true);
        this.leftEdge        = (hb != null) ? hb.getString("left-edge", "\u276e") : "\u276e";
        this.rightEdge       = (hb != null) ? hb.getString("right-edge", "\u276f") : "\u276f";
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
        this.viewDistance     = (hb != null) ? Math.max(0.0, hb.getDouble("view-distance", 32.0)) : 32.0;
        this.viewDistanceSq  = this.viewDistance * this.viewDistance;
        this.requireLOS      = hb == null || hb.getBoolean("require-line-of-sight", true);
        this.spawnPerTickCap = (hb != null) ? Math.max(1, hb.getInt("spawn-per-tick-cap", 40)) : 40;

        if (enabled) {
            sweeper = OreScheduler.runTimer(plugin, this::sweepTick, updateInterval, updateInterval);
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
        OreScheduler.runForEntity(plugin, le, () -> {
            if (le.isDead() || !le.isValid()) {
                cleanupMob(le.getUniqueId());
            } else {
                update(le);
            }
        });
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onRegain(EntityRegainHealthEvent e) {
        if (!enabled) return;
        if (!(e.getEntity() instanceof LivingEntity le)) return;
        if (!shouldTrack(le)) return;
        OreScheduler.runForEntity(plugin, le, () -> update(le));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onDeath(EntityDeathEvent e) {
        if (!enabled) return;
        UUID id = e.getEntity().getUniqueId();
        cleanupMob(id);
        OreScheduler.runLater(plugin, () -> cleanupMob(id), 1L);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEntityRemoveFromWorld(EntityRemoveFromWorldEvent e) {
        if (!enabled) return;
        if (!(e.getEntity() instanceof LivingEntity)) return;
        UUID id = e.getEntity().getUniqueId();
        if (topLine.containsKey(id) || bottomLine.containsKey(id)) {
            OreScheduler.run(plugin, () -> cleanupMob(id));
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent e) {
        if (!enabled) return;
        for (Entity ent : e.getChunk().getEntities()) {
            if (ent instanceof LivingEntity && !(ent instanceof ArmorStand)) {
                UUID id = ent.getUniqueId();
                if (topLine.containsKey(id) || bottomLine.containsKey(id)) {
                    cleanupMob(id);
                }
            }
        }
    }

    private void cleanupMob(UUID mobId) {
        removeStand(topLine.remove(mobId));
        removeStand(bottomLine.remove(mobId));
    }

    private void sweepTick() {
        // On Folia the GlobalRegionScheduler has no region ownership:
        // getNearbyEntities(), getEntities(), getEntity(uuid), and entity
        // teleport all require the owning region thread.  Health-bar
        // creation/updates still happen through onSpawn/onDamage/onRegain
        // events, so no bars are silently lost – only the proactive sweep
        // (which shows bars for mobs already in range before a damage event)
        // is skipped.
        if (OreScheduler.isFolia()) return;

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

        orphanCleanupCounter += updateInterval;
        if (orphanCleanupCounter >= 100) {
            orphanCleanupCounter = 0;
            sweepOrphanedStands();
        }
    }

    private void sweepOrphanedStands() {
        Set<UUID> tracked = new HashSet<>();
        for (ArmorStand as : topLine.values()) {
            if (as != null) tracked.add(as.getUniqueId());
        }
        for (ArmorStand as : bottomLine.values()) {
            if (as != null) tracked.add(as.getUniqueId());
        }

        for (var world : Bukkit.getWorlds()) {
            for (Entity e : world.getEntities()) {
                if (!(e instanceof ArmorStand as)) continue;
                if (!as.getScoreboardTags().contains(HOLO_TAG)) continue;
                if (!tracked.contains(as.getUniqueId())) {
                    try { as.remove(); } catch (Throwable ignored) {}
                }
            }
        }
    }

    private boolean shouldTrack(LivingEntity le) {
        if (le.getType() == EntityType.ARMOR_STAND) return false;
        if (le.getScoreboardTags().contains(HOLO_TAG)) return false;

        try {
            if (SantaHook.isSanta(le) || GrinchHook.isGrinch(le)) return false;
        } catch (Throwable ignored) {}

        if (xmas != null) {
            try { if (xmas.isSantaEntity(le)) return false; } catch (Throwable ignored) {}
        }

        if (le instanceof Player) return includePlayers;
        if (!includePassive && isPassive(le.getType())) return false;
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
        if (le.isDead() || !le.isValid()) {
            cleanupMob(le.getUniqueId());
            return;
        }

        if (!hasViewer(le)
                || SantaHook.isSanta(le)
                || GrinchHook.isGrinch(le)
                || (xmas != null && safeIsSantaViaApi(le))) {
            cleanupMob(le.getUniqueId());
            return;
        }

        double cur = Math.max(0.0, le.getHealth());
        double max = getMaxHealth(le);
        if (max <= 0) max = 20.0;

        if (cur <= 0) {
            cleanupMob(le.getUniqueId());
            return;
        }

        String mobName = mythicEnabled ? MythicMobsHook.tryName(le) : null;
        if (mobName == null || mobName.isEmpty()) {
            String base = le.getType().name()
                    .toLowerCase(java.util.Locale.ROOT)
                    .replace('_', ' ');
            mobName = Character.toUpperCase(base.charAt(0)) + base.substring(1);
        }

        String bar    = buildBar(cur, max);
        String curStr = showNumbers ? formatHp(cur) : "";
        String maxStr = showNumbers ? formatHp(max) : "";

        Player nearest = nearestPlayer(le);

        String line1 = renderFormat(formatLines.get(0), mobName, bar, curStr, maxStr, nearest);
        String line2 = (formatLines.size() > 1)
                ? renderFormat(formatLines.get(1), mobName, bar, curStr, maxStr, nearest)
                : null;

        ArmorStand top = getOrCreateStand(le, topLine, yOffset);
        top.setCustomName(line1);

        if (line2 != null) {
            ArmorStand bottom = getOrCreateStand(le, bottomLine, yOffset - lineOffset);
            bottom.setCustomName(line2);
        } else {
            removeStand(bottomLine.remove(le.getUniqueId()));
        }
    }

    private String renderFormat(String fmt, String name, String bar, String cur, String max, Player viewer) {
        if (fmt == null) return "";

        String s = fmt.replace("<name>", name)
                .replace("<bar>", bar)
                .replace("<current>", cur)
                .replace("<max>", max);
        if (!showNumbers) s = s.replace("()", "").replace("  ", " ").trim();

        if (viewer != null) {
            s = s.replace("{player}", viewer.getName());
        }

        s = convertPapiTags(s);

        try {
            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                s = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(viewer, s);
                if (s.contains("%")) {
                    s = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(viewer, s);
                }
            }
        } catch (Throwable ignored) {}

        s = AMP_HEX.matcher(s).replaceAll("<#$1>");

        if (s.indexOf('<') != -1 && s.indexOf('>') != -1) {
            try {
                boolean nexoEnabled = Bukkit.getPluginManager().getPlugin("Nexo") != null
                        && Bukkit.getPluginManager().getPlugin("Nexo").isEnabled();

                if (nexoEnabled) {
                    s = parseWithNexoAdventureUtils(s);
                } else {
                    Component comp = MM.deserialize(s);
                    s = LEGACY.serialize(comp);
                }
            } catch (Throwable ignored) {}
        }

        s = ChatColor.translateAlternateColorCodes('&', s);
        s = downsampleLegacyHexToLegacy16Safe(s);

        return s;
    }

    private String parseWithNexoAdventureUtils(String input) {
        try {
            Class<?> adv = Class.forName("com.nexomc.nexo.utils.AdventureUtils");
            try {
                return (String) adv.getMethod("parseLegacyThroughMiniMessage", String.class).invoke(null, input);
            } catch (NoSuchMethodException ignored) {}
            try {
                return (String) adv.getMethod("parseLegacy", String.class).invoke(null, input);
            } catch (NoSuchMethodException ignored) {}
        } catch (Throwable ignored) {}

        try {
            Component comp = MM.deserialize(input);
            return LEGACY.serialize(comp);
        } catch (Throwable ignored) {
            return input;
        }
    }

    private static String convertPapiTags(String input) {
        if (input == null || !input.contains("<papi:")) return input;
        Matcher m = PAPI_TAG.matcher(input);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(out, Matcher.quoteReplacement("%" + m.group(1) + "%"));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static String downsampleLegacyHexToLegacy16Safe(String s) {
        if (s == null || s.isEmpty()) return s;
        Matcher m = LEGACY_HEX.matcher(s);
        StringBuffer out = new StringBuffer();

        while (m.find()) {
            int start = m.start();
            int end = m.end();

            boolean nearUnicode = false;
            for (int i = Math.max(0, start - 3); i < Math.min(s.length(), end + 3); i++) {
                if (s.charAt(i) > 255) { nearUnicode = true; break; }
            }

            if (nearUnicode) {
                m.appendReplacement(out, Matcher.quoteReplacement(m.group()));
            } else {
                String hex = m.group().replace("\u00a7x", "").replace("\u00a7", "");
                int rgb = Integer.parseInt(hex, 16);
                char legacy = nearestLegacyColor(rgb);
                m.appendReplacement(out, "\u00a7" + legacy);
            }
        }
        m.appendTail(out);
        return out.toString();
    }

    private static char nearestLegacyColor(int rgb) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        int[][] palette = {
                {0x00,0x00,0x00,'0'}, {0x00,0x00,0xAA,'1'}, {0x00,0xAA,0x00,'2'},
                {0x00,0xAA,0xAA,'3'}, {0xAA,0x00,0x00,'4'}, {0xAA,0x00,0xAA,'5'},
                {0xFF,0xAA,0x00,'6'}, {0xAA,0xAA,0xAA,'7'}, {0x55,0x55,0x55,'8'},
                {0x55,0x55,0xFF,'9'}, {0x55,0xFF,0x55,'a'}, {0x55,0xFF,0xFF,'b'},
                {0xFF,0x55,0x55,'c'}, {0xFF,0x55,0xFF,'d'}, {0xFF,0xFF,0x55,'e'},
                {0xFF,0xFF,0xFF,'f'}
        };
        int best = Integer.MAX_VALUE;
        char bestCode = 'f';
        for (int[] p : palette) {
            int dr = r - p[0], dg = g - p[1], db = b - p[2];
            int dist = dr*dr + dg*dg + db*db;
            if (dist < best) { best = dist; bestCode = (char) p[3]; }
        }
        return bestCode;
    }

    private ArmorStand getOrCreateStand(LivingEntity host, Map<UUID, ArmorStand> map, double relY) {
        UUID id = host.getUniqueId();
        ArmorStand as = map.get(id);
        if (as != null && !as.isDead() && as.isValid()) {
            teleport(as, host, relY);
            return as;
        }
        if (as != null) removeStand(as);
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

            if (host == null
                    || !(host instanceof LivingEntity le)
                    || host.isDead()
                    || !host.isValid()
                    || as == null
                    || as.isDead()
                    || !as.isValid()
                    || le.getHealth() <= 0
                    || SantaHook.isSanta(le)
                    || GrinchHook.isGrinch(le)
                    || (xmas != null && safeIsSantaViaApi(le))) {
                removeStand(as);
                it.remove();
                continue;
            }

            teleport(as, le, top ? yOffset : (yOffset - lineOffset));
        }
    }

    private void removeAll(Map<UUID, ArmorStand> map) {
        for (ArmorStand as : map.values()) removeStand(as);
    }

    private void removeStand(ArmorStand as) {
        if (as == null || as.isDead()) return;
        if (OreScheduler.isFolia()) {
            // ArmorStand removal must run on its owning region thread on Folia.
            as.getScheduler().run(plugin, ctx -> { try { as.remove(); } catch (Throwable ignored) {} }, null);
        } else {
            try { as.remove(); } catch (Throwable ignored) {}
        }
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
        if (MAX_HEALTH_ATTR != null && le.getAttribute(MAX_HEALTH_ATTR) != null) {
            return le.getAttribute(MAX_HEALTH_ATTR).getValue();
        }
        return Math.max(20.0, le.getHealth());
    }

    private static Attribute resolveMaxHealthAttribute() {
        for (String fieldName : new String[]{"MAX_HEALTH", "GENERIC_MAX_HEALTH"}) {
            try {
                Field f = Attribute.class.getDeclaredField(fieldName);
                if (f.getType() == Attribute.class || Attribute.class.isAssignableFrom(f.getType())) {
                    return (Attribute) f.get(null);
                }
            } catch (Throwable ignored) {}
        }
        return null;
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

    private static Player nearestPlayer(LivingEntity le) {
        if (le == null || le.getWorld() == null) return null;
        Player best = null;
        double bestDist = Double.MAX_VALUE;
        for (Player p : le.getWorld().getPlayers()) {
            if (!p.isValid() || p.isDead()) continue;
            double d = p.getLocation().distanceSquared(le.getLocation());
            if (d < bestDist) { bestDist = d; best = p; }
        }
        return best;
    }

    private boolean safeIsSantaViaApi(LivingEntity le) {
        try { return xmas != null && xmas.isSantaEntity(le); }
        catch (Throwable ignored) { return false; }
    }
}