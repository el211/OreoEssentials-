// File: src/main/java/fr/elias/oreoEssentials/holograms/TextOreoHologram.java
package fr.elias.oreoEssentials.holograms;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Optional;

public final class TextOreoHologram extends OreoHologram {

    // Keep legacy fallback serialize for old APIs
    private static final LegacyComponentSerializer LEGACY_SECTION = LegacyComponentSerializer.legacySection();

    // Tag keys (match other hologram types)
    private static final NamespacedKey K_IS_OREO = NamespacedKey.fromString("oreoessentials:oreo_hologram");
    private static final NamespacedKey K_NAME    = NamespacedKey.fromString("oreoessentials:name");
    private static final NamespacedKey K_TYPE    = NamespacedKey.fromString("oreoessentials:type");

    public TextOreoHologram(String name, OreoHologramData data) {
        super(name, data);
    }

    // File: src/main/java/fr/elias/oreoEssentials/holograms/TextOreoHologram.java
// (only the two small additions; keep the rest of your regenerated class)
    @Override
    public void spawnIfMissing() {
        final Location loc = data.location.toLocation();
        if (loc == null || loc.getWorld() == null) return;
        if (findEntity().isPresent()) return;

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

        setText(td, HoloText.render(data.lines));
        textTweaks(td);

        // ⬇️ NEW: auto-wire to PerPlayer service if enabled
        if (isPerPlayerEnabled()) {
            var svc = getPerPlayerSvc();
            if (svc != null) {
                // Pass a supplier so lines are always the latest; use the hologram’s own distance & refresh
                double view = (data.visibilityDistance > 0) ? data.visibilityDistance : 64.0;
                long refresh = (data.updateIntervalTicks > 0) ? data.updateIntervalTicks : 20L;
                svc.track(td, () -> data.lines, view, refresh);
            }
        }
    }

    @Override
    public void despawn() {
        var svc = getPerPlayerSvc();
        if (svc != null && entityId != null) {
            svc.untrack(entityId);
        }

        removeAllTaggedCopies();
        entityId = null;
    }


    private boolean isPerPlayerEnabled() {
        // settings.yml: holograms.text.per-player: true|false
        try {
            var root = org.bukkit.Bukkit.getPluginManager().getPlugin("OreoEssentials");
            if (root instanceof fr.elias.oreoEssentials.OreoEssentials plug) {
                return plug.getSettingsConfig().getRoot()
                        .getBoolean("holograms.text.per-player", false);
            }
        } catch (Throwable ignored) {}
        return false;
    }
    private fr.elias.oreoEssentials.holograms.perplayer_nms.PerPlayerTextDisplayService getPerPlayerSvc() {
        try {
            var root = org.bukkit.Bukkit.getPluginManager().getPlugin("OreoEssentials");
            if (root instanceof fr.elias.oreoEssentials.OreoEssentials plug) {
                return plug.getPerPlayerTextDisplayService(); // expose a getter
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
            if (l != null) e.teleport(l);
        });
    }

    @Override
    protected void applyCommon() {
        findEntity().ifPresent(e -> commonDisplayTweaks((org.bukkit.entity.Display) e));
        // Re-apply text-specific tuning after common changes
        findEntity().ifPresent(e -> textTweaks((TextDisplay) e));
    }

    @Override
    protected void applyVisibility() {
        // NOTE: Real TextDisplay is GLOBAL. Per-player visibility/permissions require the per-player/virtual pipeline.
        // If you need MANUAL / PERMISSION_NEEDED:
        //  - Do NOT use a real TextDisplay; switch to your VirtualTextHologram / PerPlayerTextHologram systems.
        //  - Or keep a minimal global display and overlay per-player text via NMS (hybrid).
    }

    @Override
    protected void onTimedUpdate() {
        // Periodic refresh (global). For per-player PAPI, use your virtual/per-player managers.
        findEntity().ifPresent(e -> {
            TextDisplay td = (TextDisplay) e;
            setText(td, HoloText.render(data.lines));
        });
    }

    /* ---------------- text editing API ---------------- */

    public void setLine(int line, String text) {
        ensureLines();
        if (line <= 0) throw new IllegalArgumentException("Invalid line: " + line);

        int idx = line - 1;
        while (data.lines.size() <= idx) data.lines.add("");
        data.lines.set(idx, text);

        findEntity().ifPresent(e -> setText((TextDisplay) e, HoloText.render(data.lines)));
    }

    public void addLine(String text) {
        ensureLines();
        data.lines.add(text);
        findEntity().ifPresent(e -> setText((TextDisplay) e, HoloText.render(data.lines)));
    }

    public void insertBefore(int line, String text) {
        ensureLines();
        int idx = Math.max(0, Math.min(line - 1, data.lines.size()));
        data.lines.add(idx, text);
        findEntity().ifPresent(e -> setText((TextDisplay) e, HoloText.render(data.lines)));
    }

    public void insertAfter(int line, String text) {
        ensureLines();
        int idx = Math.max(0, Math.min(line, data.lines.size()));
        data.lines.add(idx, text);
        findEntity().ifPresent(e -> setText((TextDisplay) e, HoloText.render(data.lines)));
    }

    public void removeLine(int line) {
        ensureLines();
        int idx = line - 1;
        if (idx < 0 || idx >= data.lines.size()) throw new IllegalArgumentException("Invalid line: " + line);
        data.lines.remove(idx);
        findEntity().ifPresent(e -> setText((TextDisplay) e, HoloText.render(data.lines)));
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

    /* ---------------- helpers ---------------- */

    private static void setText(TextDisplay td, Component c) {
        try {
            td.text(c); // Paper API 1.19+
        } catch (NoSuchMethodError err) {
            td.setText(LEGACY_SECTION.serialize(c)); // Spigot fallback
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

        // Radius search
        for (var e : world.getNearbyEntities(loc, 3, 3, 3, ent -> ent instanceof TextDisplay)) {
            var td = (TextDisplay) e;
            var pdc = td.getPersistentDataContainer();
            if (pdc.has(K_IS_OREO, PersistentDataType.BYTE)) {
                String n = pdc.get(K_NAME, PersistentDataType.STRING);
                if (nameKey.equals(n)) return Optional.of(td);
            }
        }
        // Chunk scan
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
