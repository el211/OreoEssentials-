package fr.elias.oreoEssentials.holograms;

import org.bukkit.entity.Display;

public enum OreoHologramBillboard {
    CENTER, FIXED, VERTICAL, HORIZONTAL;

    public Display.Billboard toNms() {
        return switch (this) {
            case CENTER -> Display.Billboard.CENTER;
            case FIXED -> Display.Billboard.FIXED;
            case VERTICAL -> Display.Billboard.VERTICAL;
            case HORIZONTAL -> Display.Billboard.HORIZONTAL;
        };
    }

    public static OreoHologramBillboard from(String s) {
        try { return OreoHologramBillboard.valueOf(s.toUpperCase()); } catch (Exception e) { return CENTER; }
    }
}
