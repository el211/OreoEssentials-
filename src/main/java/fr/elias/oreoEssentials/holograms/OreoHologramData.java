package fr.elias.oreoEssentials.holograms;

import java.util.ArrayList;
import java.util.List;

public final class OreoHologramData {
    public String name;
    public OreoHologramType type;
    public OreoHologramLocation location;

    // common props
    public double scale = 1.0;
    public int shadowStrength = 0; // 0-255 mapped to 0.0-1.0
    public float shadowRadius = 0.0f;
    public int brightnessBlock = -1; // -1 means unset
    public int brightnessSky = -1;
    public OreoHologramBillboard billboard = OreoHologramBillboard.CENTER;
    public double visibilityDistance = -1.0;
    public OreoHologramVisibility visibility = OreoHologramVisibility.ALL;
    public String viewPermission = ""; // for PERMISSION_NEEDED
    public List<String> manualViewers = new ArrayList<>(); // MANUAL mode: player names

    // text props
    public List<String> lines = new ArrayList<>();
    public String backgroundColor = "TRANSPARENT"; // or CSS names / #RRGGBB (we parse basic ones)
    public boolean textShadow = false;
    public String textAlign = "CENTER"; // CENTER|LEFT|RIGHT
    public long updateIntervalTicks = 0L; // 0=off

    // item props
    public String itemStackBase64 = ""; // we’ll store simple material if you prefer; base64 lets NBT

    // block props
    public String blockType = ""; // namespaced or vanilla like STONE
}