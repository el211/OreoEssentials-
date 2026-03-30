package fr.elias.oreoEssentials.modules.holograms;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class TextOreoHologram extends OreoHologram {

    private static final LegacyComponentSerializer LEGACY_SECTION = LegacyComponentSerializer.legacySection();

    private static final NamespacedKey K_IS_OREO = NamespacedKey.fromString("oreoessentials:oreo_hologram");
    private static final NamespacedKey K_NAME    = NamespacedKey.fromString("oreoessentials:name");
    private static final NamespacedKey K_TYPE    = NamespacedKey.fromString("oreoessentials:type");

    /** Manages ICON:Material inline item displays. */
    private final InlineIconManager iconManager;

    public TextOreoHologram(String name, OreoHologramData data) {
        super(name, data);
        this.iconManager = new InlineIconManager(name);
    }

    @Override
    public void spawnIfMissing() {
        final Location loc = data.location.toLocation();
        if (loc == null || loc.getWorld() == null) return;

        var existingOpt = findEntity();
        if (existingOpt.isPresent()) {
            // Entity already in world — register with per-player service and interceptor if not yet tracked
            if (isPerPlayerEnabled() || hasAnyPapiPlaceholder()) {
                TextDisplay td = (TextDisplay) existingOpt.get();
                var interceptor = getInterceptor();
                if (interceptor != null) {
                    interceptor.track(td.getEntityId(), () -> data.lines);
                }
                var svc = getPerPlayerSvc();
                if (svc != null) {
                    double view = (data.visibilityDistance > 0) ? data.visibilityDistance : 64.0;
                    long refresh = (data.updateIntervalTicks > 0) ? data.updateIntervalTicks : 20L;
                    svc.track(td, () -> data.lines, view, refresh);
                }
            }
            return;
        }

        if (data.lines == null) data.lines = new ArrayList<>();
        if (data.lines.isEmpty()) {
            data.lines.add("<white>Edit this line with <gray>/ohologram edit " + name + "</gray>");
        }

        TextDisplay td = findTaggedExisting().orElse(
                (TextDisplay) loc.getWorld().spawnEntity(loc, org.bukkit.entity.EntityType.TEXT_DISPLAY)
        );
        entityId = td.getUniqueId();
        tag(td);

        applyTransform();
        applyCommon();
        applyVisibility();

        // --- ICON: processing ---
        List<String> renderLines = processIcons(data.lines, loc);
        textTweaks(td);

        // If any line contains a PAPI placeholder (%...%), register with the per-player
        // service so each player sees their own resolved values. This is automatic —
        // no need to set holograms.text.per-player in config. Pure static lines
        // (no % signs) skip the per-player service to avoid unnecessary NMS overhead.
        if (isPerPlayerEnabled() || hasAnyPapiPlaceholder()) {
            // Render raw text as a visible server-side fallback (placeholders unresolved).
            // The ProtocolLib interceptor replaces this per-player before the client ever sees it.
            setText(td, HoloText.render(renderLines));

            // ProtocolLib interceptor — intercepts ENTITY_METADATA packets and resolves PAPI per-player.
            var interceptor = getInterceptor();
            if (interceptor != null) {
                interceptor.track(td.getEntityId(), () -> data.lines);
                org.bukkit.Bukkit.getLogger().info("[OreoHolograms][DBG] registered interceptor for holo=" + name + " entityIntId=" + td.getEntityId());
            } else {
                org.bukkit.Bukkit.getLogger().warning("[OreoHolograms][DBG] interceptor is NULL for holo=" + name + " — ProtocolLib missing?");
            }

            // Legacy per-player NMS service (fallback when ProtocolLib unavailable).
            var svc = getPerPlayerSvc();
            if (svc != null) {
                double view = (data.visibilityDistance > 0) ? data.visibilityDistance : 64.0;
                long refresh = (data.updateIntervalTicks > 0) ? data.updateIntervalTicks : 20L;
                svc.track(td, () -> data.lines, view, refresh);
            }
        } else {
            // No placeholders — render once with null player (server-side static text).
            setText(td, HoloText.render(renderLines));
        }
    }

    @Override
    public void despawn() {
        // Despawn inline icons first
        iconManager.despawnAll(data.location.toLocation());

        var svc = getPerPlayerSvc();
        if (svc != null && entityId != null) {
            svc.untrack(entityId);
        }

        // Unregister from ProtocolLib interceptor using the entity's int ID
        if (entityId != null) {
            findEntity().ifPresent(e -> {
                var interceptor = getInterceptor();
                if (interceptor != null) interceptor.untrack(e.getEntityId());
            });
        }

        removeAllTaggedCopies();
        entityId = null;
    }

    private boolean isPerPlayerEnabled() {
        try {
            var root = org.bukkit.Bukkit.getPluginManager().getPlugin("OreoEssentials");
            if (root instanceof fr.elias.oreoEssentials.OreoEssentials plug) {
                return plug.getSettingsConfig().getRoot()
                        .getBoolean("holograms.text.per-player", false);
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private fr.elias.oreoEssentials.modules.holograms.perplayer_nms.PerPlayerTextDisplayService getPerPlayerSvc() {
        try {
            var root = org.bukkit.Bukkit.getPluginManager().getPlugin("OreoEssentials");
            if (root instanceof fr.elias.oreoEssentials.OreoEssentials plug) {
                return plug.getPerPlayerTextDisplayService();
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private fr.elias.oreoEssentials.modules.holograms.ProtocolLibHoloInterceptor getInterceptor() {
        try {
            var root = org.bukkit.Bukkit.getPluginManager().getPlugin("OreoEssentials");
            if (root instanceof fr.elias.oreoEssentials.OreoEssentials plug) {
                return plug.getProtocolLibHoloInterceptor();
            }
        } catch (Throwable ignored) {}
        return null;
    }

    @Override
    public Location currentLocation() {
        return findEntity().map(e -> e.getLocation()).orElseGet(() -> data.location.toLocation());
    }

    @Override
    protected void applyTransform() {
        findEntity().ifPresent(e -> {
            Location l = data.location.toLocation();
            if (l != null) e.teleportAsync(l); // teleport() is forbidden on Folia
        });
    }

    @Override
    protected void applyCommon() {
        findEntity().ifPresent(e -> commonDisplayTweaks((org.bukkit.entity.Display) e));
        findEntity().ifPresent(e -> textTweaks((TextDisplay) e));
    }

    @Override
    protected void applyVisibility() {
    }

    @Override
    protected void onTimedUpdate() {
        // Per-player service handles its own refresh loop — don't double-render.
        if (isPerPlayerEnabled() || hasAnyPapiPlaceholder()) return;
        findEntity().ifPresent(e -> {
            TextDisplay td = (TextDisplay) e;
            List<String> renderLines = processIcons(data.lines, td.getLocation());
            setText(td, HoloText.render(renderLines));
        });
    }

    /**
     * Re-renders the hologram text immediately.
     * For per-player / placeholder holograms the service handles rendering per-player;
     * for static holograms this re-applies any server-side values.
     */
    public void forceRefresh() {
        if (isPerPlayerEnabled() || hasAnyPapiPlaceholder()) return;
        findEntity().ifPresent(e -> {
            TextDisplay td = (TextDisplay) e;
            List<String> renderLines = processIcons(data.lines, td.getLocation());
            setText(td, HoloText.render(renderLines));
        });
    }

    /** Returns true if any hologram line contains a PAPI placeholder (has % signs). */
    private boolean hasAnyPapiPlaceholder() {
        if (data.lines == null) return false;
        for (String line : data.lines) {
            if (line != null && line.contains("%")) return true;
        }
        return false;
    }

    /* ================================================================== */
    /*  Line editing methods                                               */
    /* ================================================================== */

    public void setLine(int line, String text) {
        ensureLines();
        if (line <= 0) throw new IllegalArgumentException("Invalid line: " + line);

        int idx = line - 1;
        while (data.lines.size() <= idx) data.lines.add("");
        data.lines.set(idx, text);

        refreshTextAndIcons();
    }

    public void addLine(String text) {
        ensureLines();
        data.lines.add(text);
        refreshTextAndIcons();
    }

    public void insertBefore(int line, String text) {
        ensureLines();
        int idx = Math.max(0, Math.min(line - 1, data.lines.size()));
        data.lines.add(idx, text);
        refreshTextAndIcons();
    }

    public void insertAfter(int line, String text) {
        ensureLines();
        int idx = Math.max(0, Math.min(line, data.lines.size()));
        data.lines.add(idx, text);
        refreshTextAndIcons();
    }

    public void removeLine(int line) {
        ensureLines();
        int idx = line - 1;
        if (idx < 0 || idx >= data.lines.size()) throw new IllegalArgumentException("Invalid line: " + line);
        data.lines.remove(idx);
        refreshTextAndIcons();
    }

    public void setBackground(String color) {
        data.backgroundColor = color;
        findEntity().ifPresent(e -> textTweaks((TextDisplay) e));
    }

    public void setTextShadow(boolean shadow) {
        data.textShadow = shadow;
        findEntity().ifPresent(e -> textTweaks((TextDisplay) e));
    }

    public void setAlignment(String align) {
        data.textAlign = align;
        findEntity().ifPresent(e -> textTweaks((TextDisplay) e));
    }

    public void setUpdateIntervalTicks(long ticks) {
        data.updateIntervalTicks = Math.max(0, ticks);
    }

    /* ================================================================== */
    /*  ICON: processing                                                   */
    /* ================================================================== */

    /**
     * Processes the raw lines through the {@link InlineIconManager}.
     * Lines matching {@code ICON:Material} are replaced by a spacer and a
     * small {@link org.bukkit.entity.ItemDisplay} is spawned at the
     * corresponding vertical position.
     *
     * @param rawLines the original hologram lines (may contain ICON: entries)
     * @param baseLoc  location of the parent TextDisplay entity
     * @return cleaned lines ready for rendering on the TextDisplay
     */
    private List<String> processIcons(List<String> rawLines, Location baseLoc) {
        if (!InlineIconManager.hasAnyIcon(rawLines)) {
            // No icons → despawn any leftover icon entities and return as-is
            iconManager.despawnAll(baseLoc);
            return rawLines;
        }

        int[] brightness = (data.brightnessBlock >= 0 || data.brightnessSky >= 0)
                ? new int[]{ data.brightnessBlock, data.brightnessSky }
                : null;

        return iconManager.processAndSpawn(
                rawLines,
                baseLoc,
                data.scale,
                data.billboard,
                brightness
        );
    }

    /**
     * Convenience: re-renders text AND re-spawns icons after any line edit.
     * Ensures icons are always in sync with the current line list.
     */
    private void refreshTextAndIcons() {
        findEntity().ifPresent(e -> {
            TextDisplay td = (TextDisplay) e;
            List<String> renderLines = processIcons(data.lines, td.getLocation());
            setText(td, HoloText.render(renderLines));
        });
    }

    /* ================================================================== */
    /*  Internal helpers (unchanged logic)                                  */
    /* ================================================================== */

    private static void setText(TextDisplay td, Component c) {
        try {
            td.text(c);
        } catch (NoSuchMethodError err) {
            td.setText(LEGACY_SECTION.serialize(c));
        }
    }

    private void ensureLines() {
        if (data.lines == null) data.lines = new ArrayList<>();
    }

    private Optional<TextDisplay> findTaggedExisting() {
        Location loc = data.location.toLocation();
        if (loc == null || loc.getWorld() == null) return Optional.empty();

        final var world = loc.getWorld();
        final String nameKey = getName().toLowerCase(Locale.ROOT);

        for (var e : world.getNearbyEntities(loc, 3, 3, 3, ent -> ent instanceof TextDisplay)) {
            var td = (TextDisplay) e;
            var pdc = td.getPersistentDataContainer();
            if (pdc.has(K_IS_OREO, PersistentDataType.BYTE)) {
                String n = pdc.get(K_NAME, PersistentDataType.STRING);
                if (nameKey.equals(n)) return Optional.of(td);
            }
        }
        for (var e : world.getChunkAt(loc).getEntities()) {
            if (e instanceof TextDisplay td) {
                var pdc = td.getPersistentDataContainer();
                if (pdc.has(K_IS_OREO, PersistentDataType.BYTE)) {
                    String n = pdc.get(K_NAME, PersistentDataType.STRING);
                    if (nameKey.equals(n)) return Optional.of(td);
                }
            }
        }
        return Optional.empty();
    }

    private void tag(TextDisplay td) {
        PersistentDataContainer pdc = td.getPersistentDataContainer();
        pdc.set(K_IS_OREO, PersistentDataType.BYTE, (byte) 1);
        pdc.set(K_NAME, PersistentDataType.STRING, getName().toLowerCase(Locale.ROOT));
        pdc.set(K_TYPE, PersistentDataType.STRING, "TEXT");
    }

    private void removeAllTaggedCopies() {
        Location loc = data.location.toLocation();
        if (loc == null || loc.getWorld() == null) return;

        final var world = loc.getWorld();
        final String nameKey = getName().toLowerCase(Locale.ROOT);

        for (var e : world.getNearbyEntities(loc, 5, 5, 5, ent -> ent instanceof TextDisplay)) {
            var td = (TextDisplay) e;
            var pdc = td.getPersistentDataContainer();
            if (pdc.has(K_IS_OREO, PersistentDataType.BYTE)) {
                String n = pdc.get(K_NAME, PersistentDataType.STRING);
                if (nameKey.equals(n)) {
                    try { td.remove(); } catch (Throwable ignored) {}
                }
            }
        }

        findEntity().ifPresent(e -> { try { e.remove(); } catch (Throwable ignored) {} });
    }

    @SuppressWarnings("unused")
    private Player nearestViewer(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        Player best = null;
        double bestDist = Double.MAX_VALUE;
        for (Player p : loc.getWorld().getPlayers()) {
            double d = p.getLocation().distanceSquared(loc);
            if (d < bestDist) { bestDist = d; best = p; }
        }
        return best;
    }
}