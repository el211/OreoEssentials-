// File: src/main/java/fr/elias/oreoEssentials/chat/FormatManager.java
package fr.elias.oreoEssentials.chat;

import net.luckperms.api.LuckPermsProvider;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * FormatManager
 *
 * Features (NO OMISSIONS vs your current class + fixes for MiniMessage mode):
 * - Loads group format from chat-format.yml (CustomConfig)
 * - Fallback to chat.default if chat.<group> is missing
 * - Injects %chat_message% early
 * - Handles %player_displayname% and %player_name% with optional strip-name-colors
 * - Supports custom legacy gradient tag:
 *      <gradient:#rrggbb:#rrggbb[:#rrggbb...]>TEXT</gradient>
 *   which is converted into legacy §x hex codes (ONLY in legacy mode)
 * - Supports <#RRGGBB> conversion into legacy §x hex codes (ONLY in legacy mode)
 * - Supports legacy & codes -> § (ONLY in legacy mode)
 *
 * New (required for your Adventure chat refactor):
 * - chat.use-minimessage: true/false
 *   When true:
 *     - DO NOT generate any legacy § codes (prevents the "black garbage" you showed)
 *     - DO NOT run the legacy gradient engine (MiniMessage handles <gradient> itself)
 *     - DO return the format string as MiniMessage-friendly text
 *     - Optional bridge: can translate legacy & codes to MiniMessage tags if enabled
 *
 * Notes:
 * - In MiniMessage mode, write your formats using MiniMessage tags:
 *     <gradient:...>, <#RRGGBB>, <hover:...>, <click:...>, <head:...>, etc.
 * - In legacy mode, keep your current formats (& + your custom <gradient:...> tag)
 */
public class FormatManager {

    private final CustomConfig customYmlManager;

    // Existing: <#RRGGBB>
    private static final Pattern HEX_PATTERN = Pattern.compile("<#([A-Fa-f0-9]{6})>");

    // Existing custom gradient: <gradient:#rrggbb:#rrggbb[:#rrggbb...]>TEXT</gradient>
    private static final Pattern GRADIENT_PATTERN =
            Pattern.compile("<gradient:([^>]+)>(.*?)</gradient>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    public FormatManager(CustomConfig customYmlManager) {
        this.customYmlManager = customYmlManager;
    }

    /**
     * Formats a chat message based on LuckPerms primary group.
     *
     * Returns:
     * - Legacy mode: String containing § codes (ready for Bukkit.broadcastMessage(String))
     * - MiniMessage mode: String containing MiniMessage tags (ready for MiniMessage.deserialize(...))
     */
    public String formatMessage(Player p, String message) {
        final FileConfiguration cfg = customYmlManager.getCustomConfig();

        // Toggle: legacy vs MiniMessage output
        final boolean useMiniMessage = cfg.getBoolean("chat.use-minimessage", false);

        // Optional: allow legacy & codes in MiniMessage mode by translating them into MiniMessage tags
        // (keeps backward compatibility for existing formats while still enabling <head>/<hover>/<click>)
        final boolean miniMessageTranslateLegacyAmp =
                cfg.getBoolean("chat.minimessage-translate-legacy-amp", true);

        String group;
        try {
            group = LuckPermsProvider.get().getUserManager()
                    .getUser(p.getUniqueId())
                    .getPrimaryGroup();
        } catch (Throwable t) {
            group = "default";
        }

        // 1) Load format
        String format = cfg.getString("chat." + group);
        if (format == null) {
            format = cfg.getString("chat.default", "&7%player_displayname% » &f%chat_message%");
        }

        // 2) Inject raw message immediately
        if (message == null) message = "";
        format = format.replace("%chat_message%", message);

        // 3) Prepare names (optionally strip existing colors so gradient/hex applies cleanly)
        boolean stripNameColors = cfg.getBoolean("chat.strip-name-colors", true);
        String displayName = p.getDisplayName();
        String plainName = p.getName();
        if (stripNameColors) {
            displayName = stripAll(displayName);
            plainName = stripAll(plainName);
        }

        // 4) Replace name placeholders BEFORE colorization/gradient
        if (format.contains("%player_displayname%")) {
            format = format.replace("%player_displayname%", displayName);
        }
        if (format.contains("%player_name%")) {
            // If both placeholders are used, keep %player_name% as plain
            String nameToUse = format.contains("%player_displayname%") ? plainName : displayName;
            format = format.replace("%player_name%", nameToUse);
        }

        // -------------------- MiniMessage mode --------------------
        if (useMiniMessage) {
            // IMPORTANT:
            // - DO NOT run applyGradients() because it produces §x codes
            // - DO NOT run colorize() because it produces §x codes and replaces '&' with '§'
            //
            // We return a MiniMessage-friendly string. MiniMessage will handle:
            // - <gradient:...>...</gradient>
            // - <#RRGGBB> (hex colors are valid in MiniMessage)
            // - <hover:...>, <click:...>, <head:...> etc.

            if (miniMessageTranslateLegacyAmp) {
                // Bridge for existing formats that still contain & codes (optional)
                format = legacyAmpToMiniMessage(format);
            }
            return format;
        }

        // -------------------- Legacy mode (original behavior) --------------------

        // 5) Apply gradients first (so they can wrap everything, including injected names)
        format = applyGradients(format);

        // 6) Then resolve <#RRGGBB> and legacy & codes
        format = colorize(format);

        return format;
    }

    /* --------------------------- Gradient engine (LEGACY) --------------------------- */

    /**
     * Legacy-only: converts your custom <gradient:...> tag to §x hex codes.
     * In MiniMessage mode we do NOT call this.
     */
    private String applyGradients(String input) {
        Matcher m = GRADIENT_PATTERN.matcher(input);
        StringBuffer out = new StringBuffer();

        while (m.find()) {
            String colorsSpec = m.group(1); // "#ff00aa:#00ffaa:#0000ff" (':' ',' '; ';' separators ok)
            String text = m.group(2);

            List<int[]> stops = parseColorStops(colorsSpec);
            if (stops.size() < 2 || text.isEmpty()) {
                // Not enough colors or empty text: just remove the tag and keep raw text
                m.appendReplacement(out, Matcher.quoteReplacement(text));
                continue;
            }

            String colored = applyGradientToText(text, stops);
            m.appendReplacement(out, Matcher.quoteReplacement(colored));
        }
        m.appendTail(out);
        return out.toString();
    }

    private List<int[]> parseColorStops(String spec) {
        String[] parts = spec.split("[:;,\\s]+");
        List<int[]> list = new ArrayList<>();
        for (String p : parts) {
            p = p.trim();
            if (p.isEmpty()) continue;
            if (p.startsWith("#")) p = p.substring(1);
            if (p.length() == 3) { // allow #abc shorthand
                p = "" + p.charAt(0) + p.charAt(0)
                        + p.charAt(1) + p.charAt(1)
                        + p.charAt(2) + p.charAt(2);
            }
            if (p.length() != 6) continue;
            try {
                int r = Integer.parseInt(p.substring(0, 2), 16);
                int g = Integer.parseInt(p.substring(2, 4), 16);
                int b = Integer.parseInt(p.substring(4, 6), 16);
                list.add(new int[]{r, g, b});
            } catch (Exception ignored) {
            }
        }
        return list;
    }

    private String applyGradientToText(String raw, List<int[]> stops) {
        // We want to color only visible characters; pass-through existing §/& color codes
        StringBuilder out = new StringBuilder(raw.length() * 10);

        // First, count visible code points
        List<Integer> visibleIndexes = new ArrayList<>();
        char[] arr = raw.toCharArray();
        for (int i = 0; i < arr.length; i++) {
            char c = arr[i];
            if (isColorCodeStart(c) && i + 1 < arr.length && isLegacyCodeChar(arr[i + 1])) {
                // keep both as-is; skip from gradient span
                i++; // skip code char
                continue;
            }
            visibleIndexes.add(i);
        }

        int n = visibleIndexes.size();
        if (n == 0) return raw;

        // Gradient over multiple segments between stops
        int segments = stops.size() - 1; // (kept for parity with your original class; not strictly needed)

        for (int v = 0, i = 0; i < arr.length; i++) {
            char c = arr[i];
            if (isColorCodeStart(c) && i + 1 < arr.length && isLegacyCodeChar(arr[i + 1])) {
                // passthrough legacy code (don't color next)
                out.append('§').append(Character.toLowerCase(arr[i + 1]));
                i++; // skip the code char
                continue;
            }

            if (v < n && i == visibleIndexes.get(v)) {
                // Compute color at this visible index
                double t = (n == 1) ? 0.0 : (double) v / (double) (n - 1);
                int[] rgb = sampleMultiStop(stops, t);
                out.append(toMcHex(rgb[0], rgb[1], rgb[2])).append(c);
                v++;
            } else {
                // Non-visible (part of something else?), just echo
                out.append(c);
            }
        }

        return out.toString();
    }

    private int[] sampleMultiStop(List<int[]> stops, double t) {
        if (t <= 0) return stops.get(0);
        if (t >= 1) return stops.get(stops.size() - 1);

        double scaled = t * (stops.size() - 1);
        int idx = (int) Math.floor(scaled);
        double localT = scaled - idx;

        int[] a = stops.get(idx);
        int[] b = stops.get(idx + 1);

        int r = (int) Math.round(a[0] + (b[0] - a[0]) * localT);
        int g = (int) Math.round(a[1] + (b[1] - a[1]) * localT);
        int bC = (int) Math.round(a[2] + (b[2] - a[2]) * localT);

        return new int[]{clamp8(r), clamp8(g), clamp8(bC)};
    }

    private int clamp8(int v) {
        return Math.min(255, Math.max(0, v));
    }

    private boolean isColorCodeStart(char c) {
        return c == '§' || c == '&';
    }

    private boolean isLegacyCodeChar(char c) {
        c = Character.toLowerCase(c);
        return (c >= '0' && c <= '9')
                || (c >= 'a' && c <= 'f')
                || (c >= 'k' && c <= 'o')
                || c == 'r'
                || c == 'x';
    }

    private String toMcHex(int r, int g, int b) {
        String hex = String.format(Locale.ROOT, "%02X%02X%02X", r, g, b);
        // §x§R§R§G§G§B§B
        return "§x§" + hex.charAt(0)
                + "§" + hex.charAt(1)
                + "§" + hex.charAt(2)
                + "§" + hex.charAt(3)
                + "§" + hex.charAt(4)
                + "§" + hex.charAt(5);
    }

    /* ----------------------------- Colorize (LEGACY) ----------------------------- */

    /**
     * Legacy-only: converts <#RRGGBB> to §x format and replaces & with §.
     * In MiniMessage mode we do NOT call this.
     */
    private String colorize(String input) {
        // Convert <#RRGGBB> then &-codes → §
        Matcher matcher = HEX_PATTERN.matcher(input);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder mc = new StringBuilder("§x");
            for (char c : hex.toCharArray()) mc.append('§').append(c);
            matcher.appendReplacement(buffer, mc.toString());
        }
        matcher.appendTail(buffer);
        return buffer.toString().replace('&', '§');
    }

    private String stripAll(String s) {
        if (s == null) return "";
        // First translate & to § (so stripColor catches both)
        String t = s.replace('&', '§');
        return ChatColor.stripColor(t);
    }

    /* ----------------------- MiniMessage compatibility bridge ----------------------- */

    /**
     * OPTIONAL bridge to keep backward compatibility when chat.use-minimessage = true
     * but your formats still contain & codes.
     *
     * Example: "&eHello &cWorld" -> "<yellow>Hello <red>World"
     *
     * NOTE:
     * - This does NOT attempt to preserve "scopes" like real MiniMessage nesting.
     * - It is intentionally simple and safe.
     * - For best results, rewrite formats to proper MiniMessage tags.
     */
    private String legacyAmpToMiniMessage(String s) {
        if (s == null || s.isEmpty()) return s;

        // colors
        s = s.replace("&0", "<black>")
                .replace("&1", "<dark_blue>")
                .replace("&2", "<dark_green>")
                .replace("&3", "<dark_aqua>")
                .replace("&4", "<dark_red>")
                .replace("&5", "<dark_purple>")
                .replace("&6", "<gold>")
                .replace("&7", "<gray>")
                .replace("&8", "<dark_gray>")
                .replace("&9", "<blue>")
                .replace("&a", "<green>")
                .replace("&b", "<aqua>")
                .replace("&c", "<red>")
                .replace("&d", "<light_purple>")
                .replace("&e", "<yellow>")
                .replace("&f", "<white>")
                .replace("&r", "<reset>");

        // decorations
        s = s.replace("&l", "<bold>")
                .replace("&o", "<italic>")
                .replace("&n", "<underlined>")
                .replace("&m", "<strikethrough>")
                .replace("&k", "<obfuscated>");

        return s;
    }
}
