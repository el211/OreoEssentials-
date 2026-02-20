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
import java.util.Locale;
import java.util.Optional;

public final class TextOreoHologram extends OreoHologram {

    private static final LegacyComponentSerializer LEGACY_SECTION = LegacyComponentSerializer.legacySection();

    private static final NamespacedKey K_IS_OREO = NamespacedKey.fromString("oreoessentials:oreo_hologram");
    private static final NamespacedKey K_NAME    = NamespacedKey.fromString("oreoessentials:name");
    private static final NamespacedKey K_TYPE    = NamespacedKey.fromString("oreoessentials:type");

    public TextOreoHologram(String name, OreoHologramData data) {
        super(name, data);
    }

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

        if (isPerPlayerEnabled()) {
            var svc = getPerPlayerSvc();
            if (svc != null) {
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
        findEntity().ifPresent(e -> textTweaks((TextDisplay) e));
    }

    @Override
    protected void applyVisibility() {
    }

    @Override
    protected void onTimedUpdate() {
        if (isPerPlayerEnabled()) return;
        findEntity().ifPresent(e -> {
            TextDisplay td = (TextDisplay) e;
            setText(td, HoloText.render(data.lines));
        });
    }

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