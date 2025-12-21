package fr.elias.oreoEssentials.holograms;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;

import java.util.ArrayList;

public final class TextOreoHologram extends OreoHologram {

    private static final LegacyComponentSerializer LEGACY_SECTION =
            LegacyComponentSerializer.legacySection();

    public TextOreoHologram(String name, OreoHologramData data) {
        super(name, data);
    }

    @Override
    public void spawnIfMissing() {
        final Location loc = data.location.toLocation();
        if (loc == null || loc.getWorld() == null) return;
        if (findEntity().isPresent()) return;

        // Ensure list exists + something visible immediately
        if (data.lines == null) data.lines = new ArrayList<>();
        if (data.lines.isEmpty()) {
            data.lines.add("<white>Edit this line with <gray>/ohologram edit " + name + "</gray>");
        }

        TextDisplay td = (TextDisplay) loc.getWorld().spawnEntity(loc, EntityType.TEXT_DISPLAY);
        entityId = td.getUniqueId();

        applyTransform();
        applyCommon();
        applyVisibility();

        setText(td, HoloText.render(data.lines));
        textTweaks(td); // <-- inherited from OreoHologram (protected final)
    }

    private static void setText(TextDisplay td, Component c) {
        try {
            // Paper API (1.19+): prefer Component directly
            td.text(c);
        } catch (NoSuchMethodError err) {
            // Spigot fallback: legacy serialize (no gradients, but no crash)
            td.setText(LEGACY_SECTION.serialize(c));
        }
    }
    private Player nearestViewer(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        Player best = null;
        double bestDist = Double.MAX_VALUE;

        for (Player p : loc.getWorld().getPlayers()) {
            double d = p.getLocation().distanceSquared(loc);
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    @Override
    public void despawn() {
        findEntity().ifPresent(e -> e.remove());
        entityId = null;
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
        // also re-apply text-specific tweaks when common changes happen
        findEntity().ifPresent(e -> textTweaks((TextDisplay) e));
    }

    @Override
    protected void applyVisibility() {
        // For TEXT_DISPLAY (real entity), visibility is global.
        // If you need per-player, use your PerPlayerTextHologram packet approach.
    }

    @Override
    protected void onTimedUpdate() {
        findEntity().ifPresent(e -> {
            TextDisplay td = (TextDisplay) e;
            setText(td, HoloText.render(data.lines));
        });
    }

    // ---------------- text editing API ----------------

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
        findEntity().ifPresent(e -> textTweaks((TextDisplay) e)); // inherited
    }

    public void setTextShadow(boolean shadow) {
        data.textShadow = shadow;
        findEntity().ifPresent(e -> textTweaks((TextDisplay) e)); // inherited
    }

    public void setAlignment(String align) {
        data.textAlign = align;
        findEntity().ifPresent(e -> textTweaks((TextDisplay) e)); // inherited
    }

    public void setUpdateIntervalTicks(long ticks) {
        data.updateIntervalTicks = Math.max(0, ticks);
    }

    private void ensureLines() {
        if (data.lines == null) data.lines = new ArrayList<>();
    }
}
