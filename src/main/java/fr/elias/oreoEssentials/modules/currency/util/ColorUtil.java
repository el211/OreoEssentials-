package fr.elias.oreoEssentials.modules.currency.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Utility class for color formatting using Kyori Adventure MiniMessage
 *
 * Supports:
 * - MiniMessage tags: <gradient>, <rainbow>, <color>, etc.
 * - Hex colors: <#RRGGBB>
 * - Legacy codes: &a, &b, etc.
 */
public class ColorUtil {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    /**
     * Parse and colorize text using MiniMessage format
     *
     * Supported formats:
     * - Gradient: <gradient:red:blue>text</gradient>
     * - Gradient (hex): <gradient:#FF0000:#0000FF>text</gradient>
     * - Rainbow: <rainbow>text</rainbow>
     * - Color: <red>text</red> or <#FF0000>text</color>
     * - Bold: <b>text</b>
     * - Italic: <i>text</i>
     * - Underline: <u>text</u>
     * - Strikethrough: <st>text</st>
     * - Legacy: &aGreen &bAqua
     *
     * @param text Text with MiniMessage tags
     * @return Formatted legacy string for Bukkit/Spigot
     */
    public static String color(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        try {
            // Parse MiniMessage format
            Component component = MINI_MESSAGE.deserialize(text);

            // Convert to legacy format for Bukkit
            return LEGACY.serialize(component);
        } catch (Exception e) {
            // If MiniMessage parsing fails, fallback to legacy codes only
            return LEGACY.serialize(LEGACY.deserialize(text));
        }
    }

    /**
     * Parse MiniMessage to Component (for Adventure API)
     * Use this if you want to send Component directly
     */
    public static Component component(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }

        try {
            return MINI_MESSAGE.deserialize(text);
        } catch (Exception e) {
            return LEGACY.deserialize(text);
        }
    }

    /**
     * Strip all color codes from text
     */
    public static String stripColor(String text) {
        if (text == null) {
            return null;
        }

        try {
            Component component = MINI_MESSAGE.deserialize(text);
            return LegacyComponentSerializer.legacySection().serialize(component)
                    .replaceAll("§[0-9a-fk-or]", "");
        } catch (Exception e) {
            return text.replaceAll("§[0-9a-fk-or]", "")
                    .replaceAll("&[0-9a-fk-or]", "");
        }
    }
}