package fr.elias.oreoEssentials.modules.nametag;

import org.bukkit.Color;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;

/**
 * Immutable configuration for a single nametag text layer.
 */
public final class NametageLayerConfig {

    /** MiniMessage text template. Supports PlaceholderAPI placeholders. */
    public final String text;
    /** Y offset above the player's feet (e.g. 2.1 = just above head). */
    public final double yOffset;
    /** Whether text has a drop shadow. */
    public final boolean shadow;
    /** Whether text renders through blocks. */
    public final boolean seeThrough;
    /** Use the default grey background. Overridden by backgroundColor if set. */
    public final boolean defaultBackground;
    /** Background color. null = use defaultBackground setting. */
    public final Color backgroundColor;
    /** Text alignment: LEFT, CENTER, RIGHT */
    public final org.bukkit.entity.TextDisplay.TextAlignment alignment;
    /** Max line width in pixels for word-wrapping. 0 = no wrap. */
    public final int lineWidth;
    /** Conditions on the tag owner. All must pass for the tag to exist. */
    public final List<NametageCondition> ownerConditions;
    /** Conditions on the viewer. All must pass for this viewer to see the tag. */
    public final List<NametageCondition> viewerConditions;

    public NametageLayerConfig(
            String text, double yOffset, boolean shadow, boolean seeThrough,
            boolean defaultBackground, Color backgroundColor,
            org.bukkit.entity.TextDisplay.TextAlignment alignment, int lineWidth,
            List<NametageCondition> ownerConditions, List<NametageCondition> viewerConditions
    ) {
        this.text = text;
        this.yOffset = yOffset;
        this.shadow = shadow;
        this.seeThrough = seeThrough;
        this.defaultBackground = defaultBackground;
        this.backgroundColor = backgroundColor;
        this.alignment = alignment;
        this.lineWidth = lineWidth;
        this.ownerConditions = ownerConditions;
        this.viewerConditions = viewerConditions;
    }

    public static NametageLayerConfig fromSection(ConfigurationSection s) {
        String text = s.getString("text", "<white>%player_name%</white>");
        double yOffset = s.getDouble("y-offset", 2.1);
        boolean shadow = s.getBoolean("shadow", false);
        boolean seeThrough = s.getBoolean("see-through", false);

        // Background
        String bgStr = s.getString("background", "default").toLowerCase();
        boolean defaultBg = bgStr.equals("default");
        Color bgColor = null;
        if (bgStr.equals("transparent")) {
            bgColor = Color.fromARGB(0);
            defaultBg = false;
        } else if (bgStr.startsWith("#") || bgStr.startsWith("0x")) {
            try {
                long argb = Long.parseLong(bgStr.replace("#", "").replace("0x", ""), 16);
                bgColor = Color.fromARGB((int) argb);
                defaultBg = false;
            } catch (NumberFormatException ignored) {}
        }

        // Alignment
        org.bukkit.entity.TextDisplay.TextAlignment alignment;
        try {
            alignment = org.bukkit.entity.TextDisplay.TextAlignment.valueOf(
                    s.getString("alignment", "CENTER").toUpperCase());
        } catch (Exception e) {
            alignment = org.bukkit.entity.TextDisplay.TextAlignment.CENTER;
        }

        int lineWidth = s.getInt("line-width", 200);

        List<NametageCondition> ownerConds = NametageCondition.parseList(s, "owner-conditions");
        List<NametageCondition> viewerConds = NametageCondition.parseList(s, "viewer-conditions");

        return new NametageLayerConfig(text, yOffset, shadow, seeThrough,
                defaultBg, bgColor, alignment, lineWidth, ownerConds, viewerConds);
    }
}
