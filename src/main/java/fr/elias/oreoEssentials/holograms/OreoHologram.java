package fr.elias.oreoEssentials.holograms;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import java.util.*;
import java.util.stream.Collectors;

public abstract class OreoHologram {
    protected final String name;
    protected OreoHologramData data;
    protected UUID entityId;                 // Display entity UUID
    protected long lastUpdateMillis = 0L;    // tick scheduling in ms

    protected OreoHologram(String name, OreoHologramData data) {
        this.name = name;
        this.data = data;
    }

    public final String getName() { return name; }
    public final OreoHologramType getType() { return data.type; }
    public final OreoHologramData toData() { return data; }

    /* -------------------- lifecycle -------------------- */
    public abstract void spawnIfMissing();
    public abstract void despawn();

    /* -------------------- transforms ------------------- */
    public abstract Location currentLocation();
    protected abstract void applyTransform();
    protected abstract void applyCommon();
    protected abstract void applyVisibility();

    public void moveTo(Location l) {
        data.location = OreoHologramLocation.of(l);
        applyTransform();
    }

    public void translate(double dx, double dy, double dz) {
        Location loc = currentLocation();
        if (loc == null) return;
        loc.add(dx, dy, dz);
        moveTo(loc);
    }

    public void rotateYaw(float deg) {
        Location loc = currentLocation();
        if (loc == null) return;
        loc.setYaw(loc.getYaw() + deg);
        moveTo(loc);
    }

    public void rotatePitch(float deg) {
        Location loc = currentLocation();
        if (loc == null) return;
        loc.setPitch(loc.getPitch() + deg);
        moveTo(loc);
    }

    /* -------------------- properties ------------------- */
    public void setScale(double scale) {
        data.scale = Math.max(0.01, scale);
        applyCommon();
    }

    public void setBillboard(OreoHologramBillboard bb) {
        data.billboard = bb;
        applyCommon();
    }

    public void setShadow(int strength, float radius) {
        data.shadowStrength = strength;
        data.shadowRadius = radius;
        applyCommon();
    }

    public void setBrightness(int block, int sky) {
        data.brightnessBlock = block;
        data.brightnessSky = sky;
        applyCommon();
    }

    public void setVisibilityDistance(double d) {
        data.visibilityDistance = d;
        applyCommon();
    }

    public void setVisibilityMode(OreoHologramVisibility v) {
        data.visibility = v;
        applyVisibility();
    }

    public void setViewPermission(String perm) {
        data.viewPermission = (perm == null ? "" : perm);
        applyVisibility();
    }

    public void setManualViewers(Collection<String> names) {
        data.manualViewers = new ArrayList<>(names);
        applyVisibility();
    }

    public void tick() {
        if (data.updateIntervalTicks <= 0) return;
        long now = System.currentTimeMillis();
        long intervalMs = data.updateIntervalTicks * 50L;
        if (now - lastUpdateMillis >= intervalMs) {
            lastUpdateMillis = now;
            onTimedUpdate();
        }
    }

    protected void onTimedUpdate() {
    }

    /* ---------------------- helpers -------------------- */
    protected final Optional<Entity> findEntity() {
        if (entityId == null) return Optional.empty();
        Entity e = Bukkit.getEntity(entityId);
        if (e != null) return Optional.of(e);

        for (var w : Bukkit.getWorlds()) {
            for (var ent : w.getEntities()) {
                if (entityId.equals(ent.getUniqueId())) return Optional.of(ent);
            }
        }
        return Optional.empty();
    }


    /** Apply common Display properties that every hologram type should honor. */
    protected final void commonDisplayTweaks(Display d) {
        d.setBillboard(data.billboard.toNms());

        // shadow 0..1 from byte-like 0..255
        d.setShadowStrength(Math.max(0f, Math.min(1f, data.shadowStrength / 255f)));
        d.setShadowRadius(Math.max(0f, data.shadowRadius));

        if (data.brightnessBlock >= 0 || data.brightnessSky >= 0) {
            int b = Math.max(0, Math.min(15, Math.max(0, data.brightnessBlock)));
            int s = Math.max(0, Math.min(15, Math.max(0, data.brightnessSky)));
            d.setBrightness(new Display.Brightness(b, s));
        } else {
            d.setBrightness(null);
        }

        // Always set a view range; default to 64f when unset/<=0 to avoid culling
        float range = (data.visibilityDistance > 0) ? (float) data.visibilityDistance : 64f;
        d.setViewRange(range);

        // No Display#setScale API — use Transformation scale vector
        applyUniformScale(d, data.scale);
    }


    /** Uniformly scales a Display using its Transformation. */
    protected static void applyUniformScale(Display d, double scale) {
        Transformation tr = d.getTransformation();
        Vector3f newScale = new Vector3f((float) scale, (float) scale, (float) scale);
        d.setTransformation(new Transformation(
                tr.getTranslation(),
                tr.getLeftRotation(),
                newScale,
                tr.getRightRotation()
        ));
    }

    /** Shared visual tuning for TextDisplay. */
    protected final void textTweaks(TextDisplay t) {
        t.setLineWidth(Integer.MAX_VALUE);

        boolean transparent = "TRANSPARENT".equalsIgnoreCase(data.backgroundColor);

        if (transparent) {
            t.setDefaultBackground(false);
            t.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));  //
        } else {
            t.setDefaultBackground(false);
            t.setBackgroundColor(parseColor(data.backgroundColor));
        }

        t.setShadowed(data.textShadow);
        t.setAlignment(switch (data.textAlign.toUpperCase(Locale.ROOT)) {
            case "LEFT"  -> TextDisplay.TextAlignment.LEFT;
            case "RIGHT" -> TextDisplay.TextAlignment.RIGHT;
            default      -> TextDisplay.TextAlignment.CENTER;
        });
    }

    /** Parses #RRGGBB / #AARRGGBB or common words to a Bukkit Color. */
    private static Color parseColor(String s) {
        try {
            String v = s.trim();
            if (v.startsWith("#")) {
                v = v.substring(1);
                if (v.length() == 6) {
                    int rgb = Integer.parseInt(v, 16);
                    int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
                    return Color.fromRGB(r, g, b);
                } else if (v.length() == 8) {
                    int argb = (int) Long.parseLong(v, 16);
                    int a = (argb >> 24) & 0xFF;
                    int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
                    try { return Color.fromARGB(a, r, g, b); }
                    catch (Throwable ignored) { return Color.fromRGB(r, g, b); }
                }
            }
        } catch (Exception ignored) { }

        // Fallback named colors
        return switch (s.toUpperCase(Locale.ROOT)) {
            case "RED"    -> Color.RED;
            case "GREEN"  -> Color.GREEN;
            case "BLUE"   -> Color.BLUE;
            case "WHITE"  -> Color.WHITE;
            case "BLACK"  -> Color.BLACK;
            case "YELLOW" -> Color.YELLOW;
            case "AQUA"   -> Color.AQUA;
            default       -> Color.BLACK;
        };
    }

    protected static String joinLines(List<String> lines) {
        return lines.stream().collect(Collectors.joining("\n"));
    }
}
